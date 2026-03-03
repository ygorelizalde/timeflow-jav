package com.timeflow.controller;

import com.timeflow.config.AppConfig;
import com.timeflow.config.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * SettingsController — página de configurações do app.
 */
public class SettingsController {

    private final VBox view;

    public SettingsController() {
        view = buildView();
    }

    public VBox getView() { return view; }

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
        Label lbl = new Label("Configurações");
        lbl.getStyleClass().add("topbar-title");
        bar.getChildren().add(lbl);
        return bar;
    }

    private VBox buildContent() {
        VBox content = new VBox(24);
        content.setPadding(new Insets(40, 48, 40, 48));

        content.getChildren().addAll(
            buildSection("⏱ Rastreamento", buildTrackingSettings()),
            buildSection("🔔 Notificações", buildNotifSettings()),
            buildSection("☁️ InfluxDB Cloud", buildInfluxSettings()),
            buildSection("ℹ️ Sobre", buildAboutCard())
        );

        return content;
    }

    private VBox buildSection(String titulo, VBox conteudo) {
        VBox section = new VBox(14);

        Label lbl = new Label(titulo);
        lbl.getStyleClass().add("section-title");

        section.getChildren().addAll(lbl, conteudo);
        return section;
    }

    private VBox buildTrackingSettings() {
        VBox card = new VBox(16);
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(22, 26, 22, 26));

        card.getChildren().addAll(
            settingRow("Intervalo de sync (segundos)",
                spinnerField(AppConfig.SYNC_INTERVAL_SECONDS, 10, 300, 10)),
            separator(),
            settingRow("Tempo idle (segundos)",
                spinnerField(AppConfig.IDLE_THRESHOLD_SECONDS, 30, 600, 30)),
            separator(),
            settingRow("Iniciar rastreamento ao abrir",
                toggleSwitch(true)),
            separator(),
            settingRow("Mostrar janela miniatura ao iniciar sessão",
                toggleSwitch(true))
        );
        return card;
    }

    private VBox buildNotifSettings() {
        VBox card = new VBox(16);
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(22, 26, 22, 26));

        card.getChildren().addAll(
            settingRow("Alerta de inatividade",         toggleSwitch(true)),
            separator(),
            settingRow("Lembrete de pausa (a cada 90min)", toggleSwitch(false)),
            separator(),
            settingRow("Resumo ao fim da sessão",        toggleSwitch(true))
        );
        return card;
    }

    private VBox buildInfluxSettings() {
        VBox card = new VBox(16);
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(22, 26, 22, 26));

        card.getChildren().addAll(
            settingRow("URL",    readonlyField(AppConfig.INFLUX_URL)),
            separator(),
            settingRow("Org",    readonlyField(AppConfig.INFLUX_ORG)),
            separator(),
            settingRow("Bucket", readonlyField(AppConfig.INFLUX_BUCKET)),
            separator(),
            settingRow("Token",  readonlyField("••••••••••••••••••••"))
        );

        Label hint = new Label("Para alterar credenciais, edite AppConfig.java e rebuilde.");
        hint.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
        card.getChildren().add(hint);

        return card;
    }

    private VBox buildAboutCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("settings-card");
        card.setPadding(new Insets(22, 26, 22, 26));

        card.getChildren().addAll(
            infoRow("Versão",       "2.0.0"),
            separator(),
            infoRow("Runtime",      "Java 21 + JavaFX 21"),
            separator(),
            infoRow("Backend",      "InfluxDB Cloud"),
            separator(),
            infoRow("Rastreamento", "JNA / User32.dll"),
            separator(),
            infoRow("GitHub",       "github.com/ygorelizalde/timeflows")
        );
        return card;
    }

    // ── Helpers de UI ─────────────────────────────────────────────────

    private HBox settingRow(String label, javafx.scene.Node control) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px; -fx-font-weight: 600;");
        lbl.setMinWidth(260);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(lbl, spacer, control);
        return row;
    }

    private HBox infoRow(String label, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12px; -fx-font-weight: 600;");
        lbl.setMinWidth(140);

        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #1E293B; -fx-font-size: 13px; -fx-font-weight: 700;");

        row.getChildren().addAll(lbl, val);
        return row;
    }

    private Spinner<Integer> spinnerField(int value, int min, int max, int step) {
        Spinner<Integer> sp = new Spinner<>(min, max, value, step);
        sp.setEditable(true);
        sp.setPrefWidth(100);
        return sp;
    }

    private CheckBox toggleSwitch(boolean value) {
        CheckBox cb = new CheckBox();
        cb.setSelected(value);
        cb.setStyle("-fx-cursor: hand;");
        return cb;
    }

    private TextField readonlyField(String value) {
        TextField tf = new TextField(value);
        tf.setEditable(false);
        tf.setPrefWidth(280);
        tf.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-text-fill: #475569;");
        return tf;
    }

    private Region separator() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        r.setStyle("-fx-background-color: #F1F5F9;");
        return r;
    }
}
