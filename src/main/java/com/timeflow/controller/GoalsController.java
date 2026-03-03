package com.timeflow.controller;

import com.timeflow.config.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GoalsController — página de metas diárias de produtividade.
 * Nova funcionalidade: define metas de horas por projeto e acompanha progresso.
 */
public class GoalsController {

    private final VBox view;
    private VBox goalsContainer;
    private final List<Goal> goals = new ArrayList<>();
    private Label lblDataHoje;

    // ── Modelo de meta ────────────────────────────────────────────────
    static class Goal {
        String nome;
        String projeto;
        int metaMinutos;
        int realizadoMinutos;
        String cor;

        Goal(String nome, String projeto, int metaMinutos, String cor) {
            this.nome = nome;
            this.projeto = projeto;
            this.metaMinutos = metaMinutos;
            this.cor = cor;
        }

        double getPct() { return metaMinutos == 0 ? 0 : Math.min(1.0, (double) realizadoMinutos / metaMinutos); }
        boolean concluida() { return realizadoMinutos >= metaMinutos; }
    }

    public GoalsController() {
        // Metas default
        goals.add(new Goal("Deep Work", "💻 Desenvolvimento", 240, Theme.PRIMARY));
        goals.add(new Goal("Revisões & Reuniões", "📞 Reunião", 60, Theme.WARNING));
        goals.add(new Goal("Aprendizado", "📚 Estudo", 30, Theme.SUCCESS));

        view = buildView();
    }

    public VBox getView() { return view; }

    // ── Atualizado pelo AgenteInvisivel ──────────────────────────────
    public void atualizarProgresso(String projeto, int minutosAdicionados) {
        goals.stream()
            .filter(g -> g.projeto.equals(projeto))
            .findFirst()
            .ifPresent(g -> {
                g.realizadoMinutos = Math.min(g.metaMinutos, g.realizadoMinutos + minutosAdicionados);
                rebuildGoals();
            });
    }

    // ── UI ────────────────────────────────────────────────────────────

    private VBox buildView() {
        VBox page = new VBox(0);
        page.getStyleClass().add("page-bg");
        page.getChildren().add(buildTopbar());

        ScrollPane scroll = new ScrollPane(buildContent());
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        page.getChildren().add(scroll);
        return page;
    }

    private HBox buildTopbar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label("Metas  /  Hoje");
        lbl.getStyleClass().add("topbar-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblDataHoje = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        lblDataHoje.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 13px; -fx-font-weight: 600;");

        bar.getChildren().addAll(lbl, spacer, lblDataHoje);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(28);
        content.setPadding(new Insets(40, 48, 40, 48));

        // ── Resumo do dia ──────────────────────────────────────────────
        content.getChildren().add(buildDaySummary());

        // ── Metas ─────────────────────────────────────────────────────
        Label lblTitulo = new Label("Metas do Dia");
        lblTitulo.getStyleClass().add("section-title");
        content.getChildren().add(lblTitulo);

        goalsContainer = new VBox(14);
        content.getChildren().add(goalsContainer);
        rebuildGoals();

        // ── Adicionar nova meta ────────────────────────────────────────
        content.getChildren().add(buildAddGoalCard());

        return content;
    }

    private HBox buildDaySummary() {
        HBox row = new HBox(16);

        int totalMeta     = goals.stream().mapToInt(g -> g.metaMinutos).sum();
        int totalFeito    = goals.stream().mapToInt(g -> g.realizadoMinutos).sum();
        long concluidas   = goals.stream().filter(Goal::concluida).count();

        row.getChildren().addAll(
            summaryCard("🎯", "Meta Total", formatMin(totalMeta), Theme.PRIMARY),
            summaryCard("✅", "Realizado",  formatMin(totalFeito), Theme.SUCCESS),
            summaryCard("🏆", "Concluídas", concluidas + "/" + goals.size(), Theme.WARNING)
        );
        return row;
    }

    private VBox summaryCard(String icon, String titulo, String valor, String color) {
        VBox card = new VBox(6);
        card.getStyleClass().add("stat-card");
        card.setPadding(new Insets(20, 24, 20, 24));
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbIco = new Label(icon + "  " + titulo.toUpperCase());
        lbIco.getStyleClass().add("metric-title");

        Label lbVal = new Label(valor);
        lbVal.getStyleClass().add("metric-value");

        Region bar = new Region();
        bar.setPrefHeight(4);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 2;");

        card.getChildren().addAll(lbIco, lbVal, new Region(), bar);
        VBox.setVgrow(card.getChildren().get(2), Priority.ALWAYS);
        return card;
    }

    private void rebuildGoals() {
        if (goalsContainer == null) return;
        goalsContainer.getChildren().clear();
        goals.forEach(g -> goalsContainer.getChildren().add(buildGoalRow(g)));
    }

    private HBox buildGoalRow(Goal g) {
        HBox row = new HBox(16);
        row.getStyleClass().add("goal-row");
        row.setPadding(new Insets(18, 22, 18, 22));
        row.setAlignment(Pos.CENTER_LEFT);

        // ── Ícone circular de progresso ───────────────────────────────
        StackPane circlePane = new StackPane();
        circlePane.setMinSize(48, 48);

        Circle bg = new Circle(24, Color.web(g.cor + "22"));
        Arc arc = new Arc(24, 24, 20, 20, 90, -g.getPct() * 360);
        arc.setType(ArcType.OPEN);
        arc.setStroke(Color.web(g.cor));
        arc.setStrokeWidth(3);
        arc.setFill(Color.TRANSPARENT);

        Label pctLbl = new Label(String.format("%.0f%%", g.getPct() * 100));
        pctLbl.setStyle("-fx-text-fill: " + g.cor + "; -fx-font-size: 10px; -fx-font-weight: 800;");

        circlePane.getChildren().addAll(bg, arc, pctLbl);

        // ── Info ──────────────────────────────────────────────────────
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label nome = new Label(g.nome);
        nome.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 14px; -fx-font-weight: 700;");
        Label proj = new Label(g.projeto);
        proj.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px;");
        if (g.concluida()) {
            Label check = new Label("✓ Concluída");
            check.setStyle("-fx-text-fill: #10B981; -fx-font-size: 11px; -fx-font-weight: 700;");
            titleRow.getChildren().addAll(nome, proj, check);
        } else {
            titleRow.getChildren().addAll(nome, proj);
        }

        // Barra de progresso
        ProgressBar pb = new ProgressBar(g.getPct());
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setPrefHeight(6);
        pb.setStyle("-fx-accent: " + g.cor + ";");
        pb.getStyleClass().add("goal-progress-bar");

        Label tempo = new Label(formatMin(g.realizadoMinutos) + " de " + formatMin(g.metaMinutos));
        tempo.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px;");

        info.getChildren().addAll(titleRow, pb, tempo);

        // ── Botão editar ──────────────────────────────────────────────
        Button btnEdit = new Button("⋯");
        btnEdit.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 16px; -fx-cursor: hand;");
        btnEdit.setOnAction(e -> showEditDialog(g));

        row.getChildren().addAll(circlePane, info, btnEdit);
        return row;
    }

    private VBox buildAddGoalCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("add-goal-card");
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setAlignment(Pos.CENTER);

        Label titulo = new Label("Adicionar Nova Meta");
        titulo.getStyleClass().add("section-title");

        HBox fields = new HBox(12);
        fields.setAlignment(Pos.CENTER_LEFT);

        TextField tfNome = new TextField();
        tfNome.setPromptText("Nome da meta");
        tfNome.getStyleClass().add("goal-input");
        tfNome.setPrefWidth(200);

        ComboBox<String> cbProjeto = new ComboBox<>();
        cbProjeto.getItems().addAll(
            "💻 Desenvolvimento", "🎨 Design", "📞 Reunião",
            "📚 Estudo", "🔧 Infra / DevOps", "Sem Projeto"
        );
        cbProjeto.getSelectionModel().selectFirst();
        cbProjeto.setPrefWidth(180);

        Spinner<Integer> spHoras = new Spinner<>(0, 12, 1);
        spHoras.setEditable(true);
        spHoras.setPrefWidth(80);
        Label lblH = new Label("h");
        lblH.setStyle("-fx-text-fill: #64748B;");

        Spinner<Integer> spMin = new Spinner<>(0, 59, 0, 15);
        spMin.setEditable(true);
        spMin.setPrefWidth(80);
        Label lblM = new Label("min");
        lblM.setStyle("-fx-text-fill: #64748B;");

        Button btnAdd = new Button("+ Adicionar");
        btnAdd.getStyleClass().add("btn-primary-sm");
        btnAdd.setOnAction(e -> {
            String nome = tfNome.getText().trim();
            if (nome.isEmpty()) { tfNome.requestFocus(); return; }
            int totalMin = spHoras.getValue() * 60 + spMin.getValue();
            if (totalMin == 0) totalMin = 30;
            String[] cores = {Theme.PRIMARY, Theme.SUCCESS, Theme.WARNING, Theme.DANGER};
            String cor = cores[goals.size() % cores.length];
            goals.add(new Goal(nome, cbProjeto.getValue(), totalMin, cor));
            tfNome.clear();
            rebuildGoals();
        });

        fields.getChildren().addAll(tfNome, cbProjeto, spHoras, lblH, spMin, lblM, btnAdd);
        card.getChildren().addAll(titulo, fields);
        return card;
    }

    private void showEditDialog(Goal g) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar Meta");
        dialog.setHeaderText(g.nome);

        Spinner<Integer> spMin = new Spinner<>(15, 480, g.metaMinutos, 15);
        spMin.setEditable(true);

        VBox content = new VBox(10, new Label("Meta em minutos:"), spMin);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Botão de excluir
        ButtonType btnExcluir = new ButtonType("Excluir", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().add(btnExcluir);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                g.metaMinutos = spMin.getValue();
                rebuildGoals();
            } else if (result == btnExcluir) {
                goals.remove(g);
                rebuildGoals();
            }
        });
    }

    private static String formatMin(int min) {
        if (min < 60) return min + "min";
        return (min / 60) + "h" + (min % 60 > 0 ? " " + (min % 60) + "min" : "");
    }
}
