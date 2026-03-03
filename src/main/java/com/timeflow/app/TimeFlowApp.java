package com.timeflow.app;

import com.timeflow.config.PerfilSession;
import com.timeflow.config.Theme;
import com.timeflow.controller.LoginController;
import com.timeflow.controller.MainController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * TimeFlowApp — ponto de entrada JavaFX.
 *
 * Fluxo:
 *   1. Abre tela de Login (LoginController)
 *   2. Usuário autentica via Supabase → PerfilSession preenchido
 *   3. Troca a cena para o app principal (MainController)
 */
public class TimeFlowApp extends Application {

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        stage.setTitle("Time Flow");
        stage.setMinWidth(900);
        stage.setMinHeight(620);

        try {
            stage.getIcons().add(
                new Image(getClass().getResourceAsStream("/assets/icon.png")));
        } catch (Exception ignored) {}

        mostrarLogin();
        stage.show();
    }

    // ── Tela de Login ─────────────────────────────────────────────────

    private void mostrarLogin() {
        LoginController loginCtrl = new LoginController();

        loginCtrl.setOnLoginSucesso(() -> mostrarApp());

        Scene loginScene = new Scene(loginCtrl.getRoot(), 900, 620);
        String css = getClass().getResource("/css/style.css").toExternalForm();
        loginScene.getStylesheets().add(css);

        primaryStage.setResizable(false);
        primaryStage.setScene(loginScene);
    }

    // ── App Principal ─────────────────────────────────────────────────

    private void mostrarApp() {
        // MainController já usa PerfilSession para pegar perfilId e empresaId 
        MainController mainCtrl = new MainController();
        
        Scene appScene = new Scene(mainCtrl.getRoot(), 1280, 800);
        String css = getClass().getResource("/css/style.css").toExternalForm();
        appScene.getStylesheets().add(css);

        primaryStage.setResizable(true);
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.setScene(appScene);
    }

    @Override
    public void stop() {
        // Marca perfil offline no Supabase e para o agente
        if (MainController.getInstance() != null) {
            MainController.getInstance().shutdown();
        }
        // Limpa sessão local
        PerfilSession.clear();
    }
}