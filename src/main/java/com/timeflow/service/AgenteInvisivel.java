package com.timeflow.service;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.win32.StdCallLibrary;
import com.timeflow.config.AppConfig;
import com.timeflow.model.AppUsage;
import javafx.application.Platform;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * AgenteInvisivel — coração do rastreamento.
 *
 * Detecta a janela ativa via JNA (User32.dll),
 * conta interações de teclado/mouse (hooks nativos),
 * classifica apps por categoria,
 * e dispara sync periódico ao InfluxDB + Supabase.
 */
public class AgenteInvisivel {

    // ── JNA — User32 estendido ────────────────────────────────────────

    public interface ExtUser32 extends StdCallLibrary {
        ExtUser32 INSTANCE = Native.load("user32", ExtUser32.class);
        HWND GetForegroundWindow();
        int  GetWindowTextW(HWND hWnd, char[] lpString, int nMaxCount);
        int  GetWindowTextLengthW(HWND hWnd);
    }

    // ── Estado interno ────────────────────────────────────────────────

    private final DatabaseHandler  dbHandler;
    private final SupabaseHandler  supabase;
    private final SlackNotifier    slack;

    private final AtomicBoolean running  = new AtomicBoolean(false);
    private final AtomicBoolean tracking = new AtomicBoolean(false);

    private final ConcurrentHashMap<String, AppUsage> appsUsados = new ConcurrentHashMap<>();

    private volatile String  janelaAtual      = null;
    private volatile Instant inicioJanelaAtual = null;
    private volatile Instant timestampInicio   = null;
    private volatile Instant ultimoInput       = Instant.now();
    private volatile String  tarefaAtual       = "";
    private volatile String  projetoAtual      = "";
    private volatile String  sessionId         = null;

    private final Object mutex = new Object();

    private volatile int teclasTotais  = 0;
    private volatile int cliquesTotais = 0;

    // ── Callbacks → JavaFX Thread ─────────────────────────────────────
    private Consumer<Map<String, Object>> onStatsUpdated;
    private Consumer<String>             onStatusChanged;
    private Consumer<Boolean>            onSyncCompleted;

    // ── Scheduler ─────────────────────────────────────────────────────
    private ScheduledExecutorService scheduler;

    // ─────────────────────────────────────────────────────────────────

    /**
     * Construtor principal — recebe SupabaseHandler já configurado
     * com perfilId e empresaId do usuário logado.
     */
    public AgenteInvisivel(DatabaseHandler dbHandler, SupabaseHandler supabase) {
        this.dbHandler = dbHandler;
        this.supabase  = supabase;
        this.slack = new SlackNotifier(
            AppConfig.SLACK_WEBHOOK_URL,
            AppConfig.SLACK_ENABLED,
            "TimeFlow"
        );
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    public void start() {
        if (running.get()) return;
        running.set(true);

        scheduler = Executors.newScheduledThreadPool(3,
            r -> { Thread t = new Thread(r, "TimeFlow-Agent"); t.setDaemon(true); return t; });

        // Loop principal: 1x por segundo
        scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);

        // Sync periódico
        int interval = AppConfig.SYNC_INTERVAL_SECONDS;
        scheduler.scheduleAtFixedRate(this::sincronizarDados,
            interval, interval, TimeUnit.SECONDS);
    }

    public void stop() {
        running.set(false);
        tracking.set(false);
        if (scheduler != null) scheduler.shutdownNow();
    }

    // ── Controle de tracking ──────────────────────────────────────────

    public void iniciarTracking(String tarefa, String projeto) {
        synchronized (mutex) {
            tarefaAtual     = tarefa;
            projetoAtual    = projeto;
            timestampInicio = Instant.now();
            sessionId       = gerarSessionId();
            appsUsados.clear();
            teclasTotais      = 0;
            cliquesTotais     = 0;
            janelaAtual       = null;
            inicioJanelaAtual = null;
        }
        tracking.set(true);
        fireStatus("tracking");
        slack.notificarInicio(tarefa, projeto);

        // ── Supabase: cria sessão e marca perfil ativo ────────────────
        supabase.onIniciarTracking(sessionId, tarefa, projeto);
    }

    public void pararTracking() {
        if (tracking.get()) sincronizarDados();

        long duracaoSeg = timestampInicio != null
            ? Duration.between(timestampInicio, Instant.now()).toSeconds() : 0;

        // ── Supabase: finaliza sessão e marca perfil offline ──────────
        supabase.onPararTracking(sessionId, duracaoSeg, calcularScore(),
            teclasTotais + cliquesTotais);

        slack.notificarFim(tarefaAtual, projetoAtual, duracaoSeg, calcularScore());

        tracking.set(false);
        fireStatus("stopped");
    }

    public void pausarTracking() {
        tracking.set(false);
        fireStatus("paused");
    }

    public void retomarTracking() {
        tracking.set(true);
        fireStatus("tracking");
    }

    // ── Callbacks ─────────────────────────────────────────────────────

    public void setOnStatsUpdated(Consumer<Map<String, Object>> c)  { onStatsUpdated  = c; }
    public void setOnStatusChanged(Consumer<String> c)              { onStatusChanged = c; }
    public void setOnSyncCompleted(Consumer<Boolean> c)             { onSyncCompleted = c; }

    // ── Input events ──────────────────────────────────────────────────

    public void registrarClique() {
        if (!tracking.get()) return;
        cliquesTotais++;
        ultimoInput = Instant.now();
        if (janelaAtual != null)
            appsUsados.computeIfAbsent(janelaAtual, AppUsage::new).incrementCliques();
    }

    public void registrarTecla() {
        if (!tracking.get()) return;
        teclasTotais++;
        ultimoInput = Instant.now();
        if (janelaAtual != null)
            appsUsados.computeIfAbsent(janelaAtual, AppUsage::new).incrementTeclas();
    }

    // ─────────────────────────────────────────────────────────────────
    //  Loop principal (1 Hz)
    // ─────────────────────────────────────────────────────────────────

    private void tick() {
        if (!running.get()) return;
        if (tracking.get()) {
            atualizarJanelaAtiva();
            fireStats();
        }
    }

    // ── Detecção de janela ativa ──────────────────────────────────────

    private void atualizarJanelaAtiva() {
        try {
            HWND hwnd = ExtUser32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) return;

            int len = ExtUser32.INSTANCE.GetWindowTextLengthW(hwnd);
            if (len == 0) return;

            char[] buf = new char[len + 1];
            ExtUser32.INSTANCE.GetWindowTextW(hwnd, buf, buf.length);
            String titulo = new String(buf).trim();
            if (titulo.isEmpty()) return;

            String nomeApp = extrairNomeApp(titulo);
            Instant agora  = Instant.now();

            synchronized (mutex) {
                if (!nomeApp.equals(janelaAtual)) {
                    if (janelaAtual != null && inicioJanelaAtual != null) {
                        double seg = Duration.between(inicioJanelaAtual, agora).toMillis() / 1000.0;
                        appsUsados.computeIfAbsent(janelaAtual, AppUsage::new).addTempo(seg);
                        appsUsados.get(janelaAtual).incrementSwitches();
                    }
                    janelaAtual       = nomeApp;
                    inicioJanelaAtual = agora;
                    AppUsage novo = appsUsados.computeIfAbsent(nomeApp, AppUsage::new);
                    novo.setCategoria(categorizarApp(nomeApp));
                }

                boolean idle = Duration.between(ultimoInput, agora).toSeconds()
                               > AppConfig.IDLE_THRESHOLD_SECONDS;
                if (janelaAtual != null) {
                    AppUsage uso = appsUsados.computeIfAbsent(janelaAtual, AppUsage::new);
                    if (idle) uso.incrementIdle(); else uso.incrementAtivo();
                }
            }
        } catch (Exception e) {
            // Silencia — janela pode não ter título em transições rápidas
        }
    }

    // ── Sincronização com InfluxDB + Supabase ─────────────────────────

    void sincronizarDados() {
        if (!tracking.get() && sessionId == null) return;

        try {
            Instant agora = Instant.now();
            Map<String, Object> doc;
            long   duracaoSegFinal;
            double scoreFinal;

            synchronized (mutex) {
                if (janelaAtual != null && inicioJanelaAtual != null) {
                    double seg = Duration.between(inicioJanelaAtual, agora).toMillis() / 1000.0;
                    appsUsados.computeIfAbsent(janelaAtual, AppUsage::new).addTempo(seg);
                    inicioJanelaAtual = agora;
                }

                Map<String, Object> appsFormatado = new LinkedHashMap<>();
                double tempoProdutivo = 0, tempoTotal = 0;

                for (Map.Entry<String, AppUsage> e : appsUsados.entrySet()) {
                    AppUsage u = e.getValue();
                    if (u.getTempoSegundos() <= 0) continue;
                    Map<String, Object> appData = new LinkedHashMap<>();
                    appData.put("tempo_segundos", (int) u.getTempoSegundos());
                    appData.put("categoria",      u.getCategoria());
                    appData.put("interacoes", Map.of(
                        "teclas",  u.getTeclas(),
                        "cliques", u.getCliques()
                    ));
                    appsFormatado.put(e.getKey(), appData);
                    tempoTotal += u.getTempoSegundos();
                    if (List.of("produtividade", "design", "desenvolvimento")
                            .contains(u.getCategoria()))
                        tempoProdutivo += u.getTempoSegundos();
                }

                long   duracaoSeg = timestampInicio != null
                    ? Duration.between(timestampInicio, agora).toSeconds() : 0;
                double score      = tempoTotal > 0 ? (tempoProdutivo / tempoTotal * 100) : 0;

                duracaoSegFinal = duracaoSeg;
                scoreFinal      = score;

                doc = new LinkedHashMap<>();
                doc.put("session_id",        sessionId);
                doc.put("descricao",         tarefaAtual.isEmpty()  ? "sem_descricao" : tarefaAtual);
                doc.put("projeto",           projetoAtual.isEmpty() ? "Sem Projeto"   : projetoAtual);
                doc.put("inicio",            timestampInicio);
                doc.put("fim",               agora);
                doc.put("duracao_segundos",  (int) duracaoSeg);
                doc.put("duracao_formatada", (duracaoSeg / 60) + "m");
                doc.put("aplicativos",       appsFormatado);
                doc.put("produtividade", Map.of(
                    "score",            Math.round(score * 10.0) / 10.0,
                    "total_interacoes", teclasTotais + cliquesTotais
                ));
            }

            // ── InfluxDB (mantido intacto) ────────────────────────────
            boolean ok = dbHandler.salvarSessao(sessionId, doc);

            // ── Supabase: sync ao vivo ────────────────────────────────
            if (ok) {
                slack.notificarSync(tarefaAtual, duracaoSegFinal, scoreFinal);
                supabase.onSync(sessionId, duracaoSegFinal, scoreFinal,
                    teclasTotais + cliquesTotais);
            }

            if (onSyncCompleted != null)
                Platform.runLater(() -> onSyncCompleted.accept(ok));

        } catch (Exception ex) {
            System.err.println("[Agente] Erro no sync: " + ex.getMessage());
            if (onSyncCompleted != null)
                Platform.runLater(() -> onSyncCompleted.accept(false));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void fireStats() {
        if (onStatsUpdated == null) return;
        Map<String, Object> stats = new HashMap<>();
        synchronized (mutex) {
            stats.put("janela_atual",   janelaAtual != null ? janelaAtual : "—");
            stats.put("produtividade",  calcularScore());
            stats.put("tempo_decorrido",
                timestampInicio != null
                    ? Duration.between(timestampInicio, Instant.now()).toSeconds()
                    : 0L);
        }
        Platform.runLater(() -> { if (onStatsUpdated != null) onStatsUpdated.accept(stats); });
    }

    private void fireStatus(String status) {
        if (onStatusChanged != null)
            Platform.runLater(() -> onStatusChanged.accept(status));
    }

    private double calcularScore() {
        double total = 0, produtivo = 0;
        for (AppUsage u : appsUsados.values()) {
            total += u.getTempoSegundos();
            if (List.of("produtividade", "design", "desenvolvimento")
                    .contains(u.getCategoria()))
                produtivo += u.getTempoSegundos();
        }
        return total > 0 ? Math.round((produtivo / total * 100) * 10.0) / 10.0 : 0;
    }

    private static String extrairNomeApp(String titulo) {
        int idx = titulo.lastIndexOf(" - ");
        String nome = idx >= 0 ? titulo.substring(idx + 3).trim() : titulo;
        return nome.length() > 50 ? nome.substring(0, 50) : nome;
    }

    private static String categorizarApp(String nome) {
        String lower = nome.toLowerCase();
        for (Map.Entry<String, List<String>> e : AppConfig.APP_CATEGORIES.entrySet()) {
            for (String app : e.getValue()) {
                if (lower.contains(app)) return e.getKey();
            }
        }
        return "outros";
    }

    private static String gerarSessionId() {
        return java.time.LocalDateTime.now()
               .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}