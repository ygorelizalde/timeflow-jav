package com.timeflow.controller;

import com.timeflow.components.MiniWindow;
import com.timeflow.components.PillBadge;
import com.timeflow.components.RingProgress;
import com.timeflow.config.AppConfig;
import com.timeflow.config.PerfilSession;
import com.timeflow.config.Theme;
import com.timeflow.service.AgenteInvisivel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;

public class TrackerController {

    private final AgenteInvisivel agente;
    private final ScrollPane view;

    private Label        lblTimer;
    private Label        lblAppAtual;
    private Label        lblDuracao;
    private Label        lblInicioHora;
    private RingProgress ringProd;
    private PillBadge    badgeTracking;
    private Button       btnAction;
    private Button       btnPause;
    private Button       btnMini;
    private TextField    inputTask;
    private ComboBox<String> comboProject;
    private VBox         appsRecentesBox;

    private boolean  trackingAtivo   = false;
    private boolean  pausado         = false;
    private Instant  tempoInicio     = null;
    private long     pausadoSegundos = 0;
    private Instant  pausaInicio     = null;
    private Timeline timerTick;
    private MiniWindow miniWindow;

    private final LinkedList<String[]> appsRecentes = new LinkedList<>();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public TrackerController(AgenteInvisivel agente) {
        this.agente = agente;
        miniWindow  = new MiniWindow();

        agente.setOnStatsUpdated(stats -> {
            String app   = truncate((String) stats.getOrDefault("janela_atual", "—"), 28);
            double prod  = (double) stats.getOrDefault("produtividade", 0.0);
            String tempo = (String) stats.getOrDefault("tempo_formatado", "0m");
            lblAppAtual.setText(app);
            ringProd.setValue(prod);
            if (miniWindow.isShowing()) {
                miniWindow.updateStats(app, prod);
                if (trackingAtivo && !pausado) miniWindow.updateTask(inputTask.getText());
            }
            if (!app.equals("—") && trackingAtivo && !pausado) adicionarAppRecente(app, tempo);
        });

        view = buildView();
        setupTimer();
        carregarProjetos();
    }

    public ScrollPane getView() { return view; }

    // ── UI ────────────────────────────────────────────────────────────

    private ScrollPane buildView() {
        VBox page = new VBox(24);
        page.getStyleClass().add("page-content");
        page.setPadding(new Insets(40, 48, 40, 48));
        page.getChildren().addAll(buildTopbar(), buildHeroCard(), buildMetricsRow(), buildAppsRecentesCard());
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge-scroll");
        return scroll;
    }

    private HBox buildTopbar() {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        Label lbl = new Label("Tracker  /  Foco");
        lbl.getStyleClass().add("topbar-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label lblHora = new Label(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        lblHora.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-font-weight: 600;");
        Timeline clock = new Timeline(new KeyFrame(Duration.minutes(1), e ->
            lblHora.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))));
        clock.setCycleCount(Timeline.INDEFINITE);
        clock.play();
        bar.getChildren().addAll(lbl, spacer, lblHora);
        return bar;
    }

    private VBox buildHeroCard() {
        VBox card = new VBox(0);
        card.getStyleClass().add("hero-card");
        card.setPadding(new Insets(44, 52, 44, 52));

        // ── Header ────────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label lblSessao = new Label("SESSÃO ATUAL");
        lblSessao.getStyleClass().add("hero-label-sub");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        lblInicioHora = new Label("");
        lblInicioHora.setStyle("-fx-text-fill: #475569; -fx-font-size: 11px;");
        badgeTracking = PillBadge.parado();
        btnMini = new Button("⧉");
        btnMini.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #94A3B8; -fx-font-size: 14px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
        btnMini.setTooltip(new Tooltip("Janela flutuante"));
        btnMini.setOnAction(e -> toggleMiniWindow());
        header.getChildren().addAll(lblSessao, spacer, lblInicioHora, badgeTracking, btnMini);

        // ── Timer ─────────────────────────────────────────────────────
        lblTimer = new Label("00:00:00");
        lblTimer.getStyleClass().add("hero-timer");
        lblTimer.setMaxWidth(Double.MAX_VALUE);
        lblTimer.setAlignment(Pos.CENTER);
        VBox.setMargin(lblTimer, new Insets(28, 0, 32, 0));

        // ── Campo de atividade ────────────────────────────────────────
        Label lblAtividade = new Label("ATIVIDADE");
        lblAtividade.setStyle("-fx-text-fill: #475569; -fx-font-size: 10px; -fx-font-weight: 700; -fx-letter-spacing: 1px;");
        VBox.setMargin(lblAtividade, new Insets(0, 0, 4, 0));

        inputTask = new TextField();
        inputTask.setPromptText("No que você está trabalhando agora?");
        inputTask.getStyleClass().add("hero-input");
        VBox.setMargin(inputTask, new Insets(0, 0, 16, 0));

        // ── Projeto com botão de criar ────────────────────────────────
        Label lblProjeto = new Label("PROJETO");
        lblProjeto.setStyle("-fx-text-fill: #475569; -fx-font-size: 10px; -fx-font-weight: 700; -fx-letter-spacing: 1px;");
        VBox.setMargin(lblProjeto, new Insets(0, 0, 4, 0));

        comboProject = new ComboBox<>();
        comboProject.setPromptText("Selecione ou crie um projeto...");
        comboProject.getStyleClass().add("hero-combo");
        comboProject.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(comboProject, Priority.ALWAYS);

        Button btnNovoProjeto = new Button("+ Novo");
        btnNovoProjeto.setStyle(
            "-fx-background-color: rgba(37,99,235,0.15);" +
            "-fx-text-fill: #2563EB;" +
            "-fx-font-size: 12px;" +
            "-fx-font-weight: 700;" +
            "-fx-background-radius: 8px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 8 14 8 14;"
        );
        btnNovoProjeto.setOnAction(e -> abrirDialogNovoProjeto());

        HBox projetoRow = new HBox(10, comboProject, btnNovoProjeto);
        projetoRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(projetoRow, new Insets(0, 0, 28, 0));

        // ── Botões ────────────────────────────────────────────────────
        btnAction = new Button("▶  INICIAR SESSÃO");
        btnAction.getStyleClass().add("btn-start");
        btnAction.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnAction, Priority.ALWAYS);
        btnAction.setOnAction(e -> toggle());

        btnPause = new Button("⏸");
        btnPause.setStyle("-fx-background-color: rgba(255,255,255,0.10); -fx-text-fill: #94A3B8; -fx-font-size: 18px; -fx-background-radius: 14px; -fx-cursor: hand; -fx-min-width: 58px; -fx-pref-height: 58px;");
        btnPause.setTooltip(new Tooltip("Pausar sessão"));
        btnPause.setVisible(false);
        btnPause.setOnAction(e -> togglePause());

        HBox btnRow = new HBox(10, btnAction, btnPause);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
            header, lblTimer,
            lblAtividade, inputTask,
            lblProjeto, projetoRow,
            btnRow
        );
        return card;
    }

    private HBox buildMetricsRow() {
        lblAppAtual = new Label("—");
        lblAppAtual.getStyleClass().add("metric-value");
        lblAppAtual.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");
        lblDuracao = new Label("00:00:00");
        lblDuracao.getStyleClass().add("metric-value");
        ringProd = new RingProgress(80);
        HBox row = new HBox(16);
        row.getChildren().addAll(
            metricCard("APP ATIVO",     lblAppAtual, Theme.PRIMARY),
            metricCard("DURAÇÃO",       lblDuracao,  Theme.WARNING),
            ringCard()
        );
        return row;
    }

    private VBox buildAppsRecentesCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(22, 26, 22, 26));
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label("Apps Detectados na Sessão");
        titulo.getStyleClass().add("section-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("Histórico em tempo real");
        hint.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
        header.getChildren().addAll(titulo, sp, hint);
        appsRecentesBox = new VBox(6);
        Label placeholder = new Label("Nenhum app detectado ainda. Inicie uma sessão.");
        placeholder.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 13px; -fx-padding: 8 0 0 0;");
        appsRecentesBox.getChildren().add(placeholder);
        card.getChildren().addAll(header, appsRecentesBox);
        return card;
    }

    // ── Dialog novo projeto ───────────────────────────────────────────

    private void abrirDialogNovoProjeto() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Novo Projeto");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.setStyle("-fx-background-color: #0F1623; -fx-border-color: rgba(255,255,255,0.08); -fx-border-radius: 12px; -fx-background-radius: 12px;");

        Label lbl = new Label("Nome do projeto:");
        lbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-font-weight: 700;");

        TextField inputNome = new TextField();
        inputNome.setPromptText("Ex: Site do cliente, App mobile...");
        inputNome.setStyle(
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-border-color: rgba(255,255,255,0.10);" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: #334155;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 10 12 10 12;"
        );

        VBox content = new VBox(8, lbl, inputNome);
        content.setPadding(new Insets(20, 24, 4, 24));
        pane.setContent(content);

        ButtonType btnCriar  = new ButtonType("Criar",   ButtonBar.ButtonData.OK_DONE);
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(btnCriar, btnCancel);

        // Estiliza botões
        pane.lookupButton(btnCriar).setStyle(
            "-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: 700; " +
            "-fx-background-radius: 8px; -fx-cursor: hand;");
        pane.lookupButton(btnCancel).setStyle(
            "-fx-background-color: transparent; -fx-text-fill: #64748B; -fx-font-weight: 600; " +
            "-fx-cursor: hand;");

        dialog.setResultConverter(btn -> {
            if (btn == btnCriar) return inputNome.getText().trim();
            return null;
        });

        Platform.runLater(inputNome::requestFocus);

        dialog.showAndWait().ifPresent(nome -> {
            if (!nome.isEmpty()) criarProjeto(nome);
        });
    }

    // ── Supabase: projetos ────────────────────────────────────────────

    private void carregarProjetos() {
        Thread.ofVirtual().start(() -> {
            try {
                String url = AppConfig.SUPABASE_URL
                    + "/rest/v1/projetos?empresa_id=eq." + PerfilSession.getEmpresaId()
                    + "&select=nome&order=nome.asc";

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("apikey", AppConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer " + AppConfig.SUPABASE_ANON_KEY)
                    .GET().build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    // Parse simples do JSON array
                    String body = resp.body().trim();
                    Platform.runLater(() -> {
                        comboProject.getItems().clear();
                        comboProject.getItems().add("Sem Projeto");
                        // Extrai nomes do JSON: [{"nome":"X"},{"nome":"Y"}]
                        if (!body.equals("[]")) {
                            String[] partes = body.replaceAll("[\\[\\]{}]", "").split(",");
                            for (String p : partes) {
                                if (p.contains("\"nome\"")) {
                                    String nome = p.replace("\"nome\":", "").replace("\"", "").trim();
                                    if (!nome.isEmpty()) comboProject.getItems().add(nome);
                                }
                            }
                        }
                        comboProject.getSelectionModel().selectFirst();
                    });
                }
            } catch (Exception e) {
                // Fallback para projetos padrão se não tiver tabela
                Platform.runLater(() -> {
                    comboProject.getItems().setAll(
                        "Sem Projeto", "💻 Desenvolvimento", "🎨 Design",
                        "📞 Reunião", "📚 Estudo", "🔧 Infra / DevOps"
                    );
                    comboProject.getSelectionModel().selectFirst();
                });
            }
        });
    }

    private void criarProjeto(String nome) {
        Thread.ofVirtual().start(() -> {
            try {
                String body = String.format(
                    "{\"nome\":\"%s\",\"empresa_id\":\"%s\",\"criado_por\":\"%s\"}",
                    nome.replace("\"", "\\\""),
                    PerfilSession.getEmpresaId(),
                    PerfilSession.getId()
                );

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(AppConfig.SUPABASE_URL + "/rest/v1/projetos"))
                    .header("Content-Type", "application/json")
                    .header("apikey", AppConfig.SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer " + AppConfig.SUPABASE_ANON_KEY)
                    .header("Prefer", "return=representation")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

                HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

                Platform.runLater(() -> {
                    if (resp.statusCode() == 201 || resp.statusCode() == 200) {
                        if (!comboProject.getItems().contains(nome)) {
                            comboProject.getItems().add(nome);
                        }
                        comboProject.getSelectionModel().select(nome);
                    } else {
                        // Mesmo com erro no Supabase, adiciona localmente
                        if (!comboProject.getItems().contains(nome)) {
                            comboProject.getItems().add(nome);
                        }
                        comboProject.getSelectionModel().select(nome);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    if (!comboProject.getItems().contains(nome)) {
                        comboProject.getItems().add(nome);
                    }
                    comboProject.getSelectionModel().select(nome);
                });
            }
        });
    }

    // ── Tracking ──────────────────────────────────────────────────────

    private void toggle() {
        if (!trackingAtivo) {
            String tarefa  = inputTask.getText().trim();
            String projeto = comboProject.getSelectionModel().getSelectedItem();
            if (tarefa.isEmpty()) {
                inputTask.requestFocus();
                inputTask.setStyle(inputTask.getStyle() +
                    "-fx-border-color: #EF4444;");
                return;
            }
            if (projeto == null) projeto = "Sem Projeto";

            trackingAtivo = true; pausado = false; pausadoSegundos = 0;
            tempoInicio = Instant.now();
            agente.iniciarTracking(tarefa, projeto);
            miniWindow.updateTask(tarefa);

            btnAction.setText("⏹  PARAR SESSÃO");
            btnAction.getStyleClass().remove("btn-start");
            btnAction.getStyleClass().add("btn-stop");
            btnPause.setVisible(true);
            badgeTracking.update("● RASTREANDO", Theme.SUCCESS);
            lblInicioHora.setText("Início: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
            inputTask.setDisable(true);
            comboProject.setDisable(true);
            timerTick.play();
            appsRecentesBox.getChildren().clear();
            appsRecentes.clear();

        } else {
            trackingAtivo = false; pausado = false;
            agente.pararTracking();
            timerTick.stop();

            btnAction.setText("▶  INICIAR SESSÃO");
            btnAction.getStyleClass().remove("btn-stop");
            btnAction.getStyleClass().add("btn-start");
            btnPause.setVisible(false);
            btnPause.setText("⏸");
            badgeTracking.update("● PARADO", "#64748B");
            lblTimer.setText("00:00:00");
            lblDuracao.setText("00:00:00");
            lblInicioHora.setText("");
            inputTask.setDisable(false);
            comboProject.setDisable(false);
            miniWindow.updateTimer("00:00:00");
            miniWindow.updateTask("Nenhuma sessão ativa");
        }
    }

    private void togglePause() {
        if (!trackingAtivo) return;
        if (!pausado) {
            pausado = true; pausaInicio = Instant.now();
            agente.pausarTracking();
            btnPause.setText("▶");
            badgeTracking.update("⏸ PAUSADO", Theme.WARNING);
            miniWindow.setIdle();
        } else {
            pausado = false;
            if (pausaInicio != null) {
                pausadoSegundos += ChronoUnit.SECONDS.between(pausaInicio, Instant.now());
                pausaInicio = null;
            }
            agente.retomarTracking();
            btnPause.setText("⏸");
            badgeTracking.update("● RASTREANDO", Theme.SUCCESS);
            miniWindow.updateTask(inputTask.getText());
        }
    }

    private void toggleMiniWindow() {
        if (miniWindow.isShowing()) {
            miniWindow.hide();
            btnMini.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: #94A3B8; -fx-font-size: 14px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
        } else {
            miniWindow.show();
            btnMini.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-size: 14px; -fx-background-radius: 8px; -fx-cursor: hand; -fx-padding: 4 10 4 10;");
        }
    }

    private void setupTimer() {
        timerTick = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (tempoInicio == null || pausado) return;
            long s = ChronoUnit.SECONDS.between(tempoInicio, Instant.now()) - pausadoSegundos;
            String t = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
            lblTimer.setText(t);
            lblDuracao.setText(t);
            if (miniWindow.isShowing()) miniWindow.updateTimer(t);
        }));
        timerTick.setCycleCount(Timeline.INDEFINITE);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void adicionarAppRecente(String app, String tempo) {
        if (!appsRecentes.isEmpty() && appsRecentes.getFirst()[0].equals(app)) return;
        appsRecentes.addFirst(new String[]{app, tempo});
        if (appsRecentes.size() > 8) appsRecentes.removeLast();
        appsRecentesBox.getChildren().clear();
        appsRecentes.forEach(entry -> {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 0, 4, 0));
            Label dot = new Label("◆");
            dot.setStyle("-fx-text-fill: #2563EB; -fx-font-size: 8px;");
            Label lblApp = new Label(truncate(entry[0], 40));
            lblApp.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px; -fx-font-weight: 600;");
            HBox.setHgrow(lblApp, Priority.ALWAYS);
            Label lblTempo = new Label(entry[1]);
            lblTempo.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
            row.getChildren().addAll(dot, lblApp, lblTempo);
            appsRecentesBox.getChildren().add(row);
        });
    }

    private VBox metricCard(String titulo, Label valueLabel, String color) {
        VBox card = new VBox(6);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(22, 24, 22, 24));
        HBox.setHgrow(card, Priority.ALWAYS);
        Label lbl = new Label(titulo);
        lbl.getStyleClass().add("metric-title");
        Region bar = new Region();
        bar.setPrefHeight(3);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");
        card.getChildren().addAll(lbl, valueLabel, new Region(), bar);
        VBox.setVgrow(card.getChildren().get(2), Priority.ALWAYS);
        return card;
    }

    private VBox ringCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(16, 24, 16, 24));
        card.setAlignment(Pos.CENTER);
        HBox.setHgrow(card, Priority.ALWAYS);
        Label lbl = new Label("PRODUTIVIDADE");
        lbl.getStyleClass().add("metric-title");
        card.getChildren().addAll(lbl, ringProd);
        return card;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "—";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}