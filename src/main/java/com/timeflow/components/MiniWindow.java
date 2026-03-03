package com.timeflow.components;

import com.timeflow.config.Theme;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * MiniWindow — janela flutuante sempre no topo, equivalente ao JanelaMiniatura do Python.
 * Mostra timer, tarefa atual e produtividade em tempo real.
 */
public class MiniWindow {

    private final Stage stage;
    private Label lblTimer;
    private Label lblTask;
    private Label lblProd;
    private Label lblApp;

    private double dragX, dragY;

    public MiniWindow() {
        stage = new Stage(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle("Time Flow Mini");
        stage.setResizable(false);
        buildUI();
    }

    private void buildUI() {
        VBox root = new VBox(0);
        root.getStyleClass().add("mini-root");
        root.setPrefWidth(240);
        root.setPadding(new Insets(0));

        // ── Drag bar ──────────────────────────────────────────────────
        HBox dragBar = new HBox();
        dragBar.getStyleClass().add("mini-drag-bar");
        dragBar.setPadding(new Insets(8, 12, 6, 14));
        dragBar.setAlignment(Pos.CENTER_LEFT);

        Label dot = new Label("◆");
        dot.setStyle("-fx-text-fill: #2563EB; -fx-font-size: 9px;");
        Label title = new Label("TIME FLOW");
        title.setStyle("-fx-text-fill: #475569; -fx-font-size: 9px; -fx-font-weight: 700; -fx-padding: 0 0 0 6;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnClose = new Button("×");
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #475569; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 2 0 2;");
        btnClose.setOnAction(e -> hide());

        dragBar.getChildren().addAll(dot, title, spacer, btnClose);

        // Drag logic
        dragBar.setOnMousePressed(e -> { dragX = e.getScreenX() - stage.getX(); dragY = e.getScreenY() - stage.getY(); });
        dragBar.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - dragX); stage.setY(e.getScreenY() - dragY); });

        // ── Timer ─────────────────────────────────────────────────────
        VBox body = new VBox(6);
        body.setPadding(new Insets(4, 16, 14, 16));

        lblTimer = new Label("00:00:00");
        lblTimer.getStyleClass().add("mini-timer");
        lblTimer.setMaxWidth(Double.MAX_VALUE);
        lblTimer.setAlignment(Pos.CENTER);

        // ── Task label ────────────────────────────────────────────────
        lblTask = new Label("Nenhuma sessão ativa");
        lblTask.getStyleClass().add("mini-task");
        lblTask.setMaxWidth(Double.MAX_VALUE);
        lblTask.setAlignment(Pos.CENTER);
        lblTask.setWrapText(true);

        // ── App atual + prod ──────────────────────────────────────────
        HBox statsRow = new HBox(8);
        statsRow.setAlignment(Pos.CENTER);
        statsRow.setPadding(new Insets(6, 0, 0, 0));

        lblApp = new Label("—");
        lblApp.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px; -fx-font-weight: 600;");
        lblApp.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblApp, Priority.ALWAYS);

        lblProd = new Label("0%");
        lblProd.setStyle("-fx-text-fill: #10B981; -fx-font-size: 11px; -fx-font-weight: 800;");

        statsRow.getChildren().addAll(lblApp, lblProd);

        // ── Divider ───────────────────────────────────────────────────
        Region divider = new Region();
        divider.setPrefHeight(1);
        divider.setMaxWidth(Double.MAX_VALUE);
        divider.setStyle("-fx-background-color: #1E293B;");

        body.getChildren().addAll(lblTimer, lblTask, divider, statsRow);
        root.getChildren().addAll(dragBar, body);

        Scene scene = new Scene(root, Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        stage.setScene(scene);
    }

    public void show() { stage.show(); }
    public void hide() { stage.hide(); }
    public boolean isShowing() { return stage.isShowing(); }

    public void updateTimer(String time) {
        lblTimer.setText(time);
    }

    public void updateTask(String task) {
        lblTask.setText(task == null || task.isBlank() ? "Sessão ativa" : task);
    }

    public void updateStats(String app, double prod) {
        lblApp.setText(app);
        String color = prod >= 70 ? "#10B981" : prod >= 40 ? "#F59E0B" : "#EF4444";
        lblProd.setText(String.format("%.0f%%", prod));
        lblProd.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; -fx-font-weight: 800;");
    }

    public void setIdle() {
        lblTask.setText("Sessão pausada (idle)");
        lblTask.setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px; -fx-font-weight: 600;");
    }
}
