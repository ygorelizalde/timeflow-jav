package com.timeflow.controller;

import com.timeflow.components.NavItem;
import com.timeflow.components.PillBadge;
import com.timeflow.config.PerfilSession;
import com.timeflow.config.Theme;
import com.timeflow.service.AgenteInvisivel;
import com.timeflow.service.DatabaseHandler;
import com.timeflow.service.SupabaseHandler;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

public class MainController {

    private static MainController INSTANCE;

    private final DatabaseHandler      db;
    private final AgenteInvisivel      agente;
    private final SupabaseHandler      supabase;
    private final TrackerController    trackerCtrl;
    private final AnalyticsController  analyticsCtrl;
    private final GoalsController      goalsCtrl;
    private final SettingsController   settingsCtrl;

    private final BorderPane root = new BorderPane();
    private NavItem navTracker, navAnalytics, navGoals, navSettings;
    private PillBadge badgeSync;

    public MainController() {
        INSTANCE = this;
        db       = new DatabaseHandler();
        supabase = new SupabaseHandler(PerfilSession.getId(), PerfilSession.getEmpresaId());
        agente   = new AgenteInvisivel(db, supabase);

        trackerCtrl   = new TrackerController(agente);
        analyticsCtrl = new AnalyticsController(db);
        goalsCtrl     = new GoalsController();
        settingsCtrl  = new SettingsController();

        agente.setOnSyncCompleted(ok -> {
            Platform.runLater(() -> {
                if (ok) badgeSync.update("● Sincronizado", Theme.SUCCESS);
                else    badgeSync.update("● Falha Sync",   Theme.DANGER);
            });
        });

        buildUI();
        agente.start();

        Runtime.getRuntime().addShutdownHook(new Thread(supabase::onAppShutdown));
    }

    public static MainController getInstance() { return INSTANCE; }
    public BorderPane getRoot()                { return root;      }

    public void shutdown() {
        supabase.onAppShutdown();
        agente.stop();
    }

    private void buildUI() {
        root.getStyleClass().add("root-pane");
        root.setLeft(buildSidebar());
        navegar(0);
    }

    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.getStyleClass().add("sidebar");
        sb.setPrefWidth(248);
        sb.setMinWidth(248);
        sb.setMaxWidth(248);
        sb.setPadding(new Insets(36, 0, 28, 0));
        sb.setSpacing(0);

        // Logo
        HBox logoRow = new HBox(8);
        logoRow.setAlignment(Pos.CENTER_LEFT);
        logoRow.setPadding(new Insets(0, 0, 0, 28));
        Label dot  = new Label("◆");
        dot.getStyleClass().add("logo-dot");
        Label name = new Label("TIME FLOW");
        name.getStyleClass().add("logo-text");
        logoRow.getChildren().addAll(dot, name);
        sb.getChildren().add(logoRow);

        // Versão
        Label ver = new Label("v2.1");
        ver.setStyle("-fx-text-fill: #2563EB; -fx-font-size: 10px; -fx-font-weight: 700; " +
                     "-fx-background-color: rgba(37,99,235,0.12); -fx-background-radius: 4px; " +
                     "-fx-padding: 2 6 2 6;");
        HBox verRow = new HBox(ver);
        verRow.setPadding(new Insets(6, 0, 0, 28));
        sb.getChildren().add(verRow);

        // Nome do usuário logado
        String nomeUsuario = PerfilSession.getNome() != null ? PerfilSession.getNome() : "Usuário";
        Label lblUsuario = new Label("● " + nomeUsuario);
        lblUsuario.setStyle("-fx-text-fill: #10B981; -fx-font-size: 11px; -fx-font-weight: 700; " +
                            "-fx-padding: 4 0 0 28;");
        sb.getChildren().add(lblUsuario);

        sb.getChildren().add(separator());
        VBox.setMargin(sb.getChildren().get(sb.getChildren().size() - 1), new Insets(24, 0, 16, 0));

        // Label MENU
        Label lblMenu = new Label("MENU PRINCIPAL");
        lblMenu.setStyle("-fx-text-fill: #334155; -fx-font-size: 9px; -fx-font-weight: 700; -fx-letter-spacing: 1.5px;");
        sb.getChildren().add(lblMenu);
        VBox.setMargin(lblMenu, new Insets(0, 0, 8, 28));

        navTracker   = new NavItem("⏱", "Tracker",   true);
        navAnalytics = new NavItem("📊", "Analytics", false);
        navGoals     = new NavItem("🎯", "Metas",     false);

        navTracker.setOnNavClicked(()   -> navegar(0));
        navAnalytics.setOnNavClicked(() -> navegar(1));
        navGoals.setOnNavClicked(()     -> navegar(2));

        sb.getChildren().addAll(navTracker, navAnalytics, navGoals);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sb.getChildren().add(spacer);

        sb.getChildren().add(separator());
        VBox.setMargin(sb.getChildren().get(sb.getChildren().size() - 1), new Insets(0, 0, 12, 0));

        // Label SISTEMA
        Label lblConf = new Label("SISTEMA");
        lblConf.setStyle("-fx-text-fill: #334155; -fx-font-size: 9px; -fx-font-weight: 700; -fx-letter-spacing: 1.5px;");
        VBox.setMargin(lblConf, new Insets(0, 0, 8, 28));
        sb.getChildren().add(lblConf);

        navSettings = new NavItem("⚙", "Configurações", false);
        navSettings.setOnNavClicked(() -> navegar(3));
        sb.getChildren().add(navSettings);

        sb.getChildren().add(separator());
        VBox.setMargin(sb.getChildren().get(sb.getChildren().size() - 1), new Insets(12, 0, 14, 0));

        // Badge sync
        badgeSync = PillBadge.offline();
        HBox badgeRow = new HBox(badgeSync);
        badgeRow.setAlignment(Pos.CENTER);
        sb.getChildren().add(badgeRow);

        // Info DB
        Label dbInfo = new Label("InfluxDB + Supabase");
        dbInfo.getStyleClass().add("sidebar-version");
        HBox dbRow = new HBox(dbInfo);
        dbRow.setAlignment(Pos.CENTER);
        VBox.setMargin(dbRow, new Insets(4, 0, 0, 0));
        sb.getChildren().add(dbRow);

        return sb;
    }

    private void navegar(int idx) {
        switch (idx) {
            case 0 -> root.setCenter(trackerCtrl.getView());
            case 1 -> root.setCenter(analyticsCtrl.getView());
            case 2 -> root.setCenter(goalsCtrl.getView());
            case 3 -> root.setCenter(settingsCtrl.getView());
        }
        navTracker.setAtivo(idx == 0);
        navAnalytics.setAtivo(idx == 1);
        navGoals.setAtivo(idx == 2);
        navSettings.setAtivo(idx == 3);
    }

    private static Rectangle separator() {
        Rectangle r = new Rectangle();
        r.getStyleClass().add("sidebar-separator");
        r.setHeight(1);
        r.setWidth(248);
        return r;
    }
}