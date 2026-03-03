package com.timeflow.controller;

import com.timeflow.components.PillBadge;
import com.timeflow.config.Theme;
import com.timeflow.service.DatabaseHandler;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.*;
import java.util.*;

/**
 * AnalyticsController — página de histórico e relatórios.
 * Equivalente ao _build_analytics() + _buscar() + _renderizar() do Python.
 */
public class AnalyticsController {

    private final DatabaseHandler db;
    private final VBox view;

    private VBox resultsContainer;
    private DatePicker dpInicio;
    private DatePicker dpFim;

    public AnalyticsController(DatabaseHandler db) {
        this.db = db;
        view = buildView();
    }

    public VBox getView() { return view; }

    // ── UI ────────────────────────────────────────────────────────────

    private VBox buildView() {
        VBox page = new VBox(0);
        page.getStyleClass().add("page-bg");

        HBox topbar = buildTopbar("Analytics  /  Relatórios");
        page.getChildren().add(topbar);

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        page.getChildren().add(scroll);
        return page;
    }

    private HBox buildTopbar(String titulo) {
        HBox bar = new HBox();
        bar.getStyleClass().add("topbar");
        Label lbl = new Label(titulo);
        lbl.getStyleClass().add("topbar-title");
        bar.getChildren().add(lbl);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(28);
        content.setPadding(new Insets(40, 48, 40, 48));

        // ── Filtro de data ────────────────────────────────────────────
        HBox filtroRow = new HBox(16);
        filtroRow.setAlignment(Pos.CENTER_LEFT);
        filtroRow.getStyleClass().add("filter-card");

        Label lbDe = new Label("De:");
        lbDe.getStyleClass().add("filter-label");
        dpInicio = new DatePicker(LocalDate.now().minusDays(7));
        dpInicio.getStyleClass().add("date-picker");

        Label lbAte = new Label("Até:");
        lbAte.getStyleClass().add("filter-label");
        dpFim = new DatePicker(LocalDate.now());
        dpFim.getStyleClass().add("date-picker");

        Button btnBuscar = new Button("🔍  Buscar");
        btnBuscar.getStyleClass().add("btn-primary-sm");
        btnBuscar.setOnAction(e -> buscar());

        filtroRow.getChildren().addAll(lbDe, dpInicio, lbAte, dpFim, btnBuscar);
        content.getChildren().add(filtroRow);

        // ── Área de resultados ────────────────────────────────────────
        resultsContainer = new VBox(18);
        content.getChildren().add(resultsContainer);
        return content;
    }

    // ── Busca em background (Task = não trava UI) ─────────────────────

    private void buscar() {
        resultsContainer.getChildren().clear();
        Label loading = new Label("⏳  Buscando dados...");
        loading.getStyleClass().add("placeholder-label");
        resultsContainer.getChildren().add(loading);

        LocalDate dIni = dpInicio.getValue();
        LocalDate dFim = dpFim.getValue();
        Instant start  = dIni.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant stop   = dFim.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

        Task<List<Map<String, Object>>> task = new Task<>() {
            @Override protected List<Map<String, Object>> call() {
                return db.buscarHistorico(start, stop);
            }
        };

        task.setOnSucceeded(e  -> renderizar(task.getValue()));
        task.setOnFailed(e     -> {
            resultsContainer.getChildren().clear();
            Label err = new Label("❌  Erro ao buscar dados.");
            err.getStyleClass().add("placeholder-label");
            resultsContainer.getChildren().add(err);
        });

        Thread t = new Thread(task, "TimeFlow-AnalyticsQuery");
        t.setDaemon(true);
        t.start();
    }

    // ── Renderização ──────────────────────────────────────────────────

    private void renderizar(List<Map<String, Object>> dados) {
        resultsContainer.getChildren().clear();

        if (dados.isEmpty()) {
            Label placeholder = new Label("Nenhum dado encontrado para o período.");
            placeholder.getStyleClass().add("placeholder-label");
            resultsContainer.getChildren().add(placeholder);
            return;
        }

        // ── KPI cards ─────────────────────────────────────────────────
        long totalSec  = dados.stream().mapToLong(d -> toLong(d.get("duracao_segundos"))).sum();
        long h  = totalSec / 3600;
        long mn = (totalSec % 3600) / 60;
        double avgProd = dados.stream().mapToDouble(d -> toDouble(d.get("score_produtividade")))
                              .average().orElse(0);

        HBox kpiRow = new HBox(18);
        kpiRow.getChildren().addAll(
            kpiCard("⏱", "Horas Totais",   h + "h " + mn + "m", Theme.PRIMARY),
            kpiCard("📁", "Sessões",         String.valueOf(dados.size()), Theme.SUCCESS),
            kpiCard("⚡", "Prod. Média",     String.format("%.0f%%", avgProd), Theme.WARNING)
        );
        resultsContainer.getChildren().add(kpiRow);

        // ── Barra de projetos ─────────────────────────────────────────
        Map<String, Long> projData = new LinkedHashMap<>();
        for (Map<String, Object> d : dados) {
            String proj = (String) d.getOrDefault("projeto", "Sem Projeto");
            projData.merge(proj, toLong(d.get("duracao_segundos")), Long::sum);
        }
        resultsContainer.getChildren().add(buildProjetosCard(projData));

        // ── Lista de sessões ──────────────────────────────────────────
        Label lblHist = new Label("Últimas Atividades");
        lblHist.getStyleClass().add("section-title");
        resultsContainer.getChildren().add(lblHist);

        dados.stream().limit(15).forEach(d ->
            resultsContainer.getChildren().add(buildSessionRow(d)));
    }

    // ── Componentes de resultado ──────────────────────────────────────

    private VBox kpiCard(String icone, String titulo, String valor, String color) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(20, 24, 20, 24));
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbIco  = new Label(icone + "  " + titulo.toUpperCase());
        lbIco.getStyleClass().add("metric-title");

        Label lbVal  = new Label(valor);
        lbVal.getStyleClass().add("metric-value");

        Region bar = new Region();
        bar.setPrefHeight(4);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");

        card.getChildren().addAll(lbIco, lbVal, new Region(), bar);
        VBox.setVgrow(card.getChildren().get(2), Priority.ALWAYS);
        return card;
    }

    private VBox buildProjetosCard(Map<String, Long> projData) {
        VBox card = new VBox(14);
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(24, 28, 24, 28));

        Label titulo = new Label("Distribuição por Projeto");
        titulo.getStyleClass().add("section-title");
        card.getChildren().add(titulo);

        final long total = Math.max(1, projData.values().stream().mapToLong(Long::longValue).sum());
        String[] cores = { Theme.PRIMARY, Theme.SUCCESS, Theme.WARNING, Theme.DANGER };
        int[] idx = {0};

        projData.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(e -> {
                double pct = (double) e.getValue() / total * 100;
                String cor = cores[idx[0] % cores.length]; idx[0]++;

                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                Label nome = new Label(truncate(e.getKey(), 28));
                nome.getStyleClass().add("project-name");
                nome.setMinWidth(180);

                ProgressBar pb = new ProgressBar(pct / 100.0);
                pb.getStyleClass().add("project-bar");
                pb.setMaxWidth(Double.MAX_VALUE);
                pb.setPrefHeight(8);
                pb.setStyle("-fx-accent: " + cor + ";");
                HBox.setHgrow(pb, Priority.ALWAYS);

                Label pctLbl = new Label(String.format("%d%%", (int) pct));
                pctLbl.getStyleClass().add("project-pct");
                pctLbl.setMinWidth(40);

                row.getChildren().addAll(nome, pb, pctLbl);
                card.getChildren().add(row);
            });

        return card;
    }

    private HBox buildSessionRow(Map<String, Object> d) {
        HBox row = new HBox(12);
        row.getStyleClass().add("session-row");
        row.setPadding(new Insets(16, 20, 16, 20));
        row.setAlignment(Pos.CENTER_LEFT);

        String proj = (String) d.getOrDefault("projeto", "Sem Projeto");
        Label ic = new Label(proj.isEmpty() ? "📁" : String.valueOf(proj.charAt(0)));
        ic.getStyleClass().add("session-icon");

        VBox info = new VBox(3);
        Label desc   = new Label((String) d.getOrDefault("descricao", "Sem Descrição"));
        desc.getStyleClass().add("session-desc");
        Label projLbl = new Label(proj);
        projLbl.getStyleClass().add("session-proj");
        info.getChildren().addAll(desc, projLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label dur   = new Label((String) d.getOrDefault("duracao_formatada", "0m"));
        dur.getStyleClass().add("session-duration");

        double score = toDouble(d.get("score_produtividade"));
        String bColor = score >= 60 ? Theme.SUCCESS : score >= 30 ? Theme.WARNING : Theme.DANGER;
        PillBadge badge = new PillBadge(String.format("⚡ %.0f%%", score), bColor);

        row.getChildren().addAll(ic, info, spacer, dur, badge);
        return row;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }
}
