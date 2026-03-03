package com.timeflow.controller;

import com.timeflow.config.PerfilSession;
import com.timeflow.service.SupabaseAuth;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * LoginController — tela de login com autenticação Supabase.
 * Exibida antes do MainController.
 *
 * Uso no TimeFlowApp:
 *   LoginController login = new LoginController();
 *   login.setOnLoginSucesso(() -> { ... abre MainController ... });
 */
public class LoginController {

    private final BorderPane root = new BorderPane();

    private TextField     inputEmail;
    private PasswordField inputSenha;
    private Button        btnEntrar;
    private Label         lblErro;
    private Label         lblCarregando;

    private Runnable onLoginSucesso;

    public LoginController() { buildUI(); }

    public BorderPane getRoot() { return root; }
    public void setOnLoginSucesso(Runnable cb) { this.onLoginSucesso = cb; }

    // ── UI ────────────────────────────────────────────────────────────

    private void buildUI() {
        root.getStyleClass().add("root-pane");

        VBox card = new VBox(0);
        card.setAlignment(Pos.TOP_CENTER);
        card.setMaxWidth(420);
        card.setStyle(
            "-fx-background-color: #0F1623;" +
            "-fx-background-radius: 20px;" +
            "-fx-border-color: rgba(255,255,255,0.07);" +
            "-fx-border-radius: 20px;" +
            "-fx-border-width: 1px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 40, 0, 0, 8);"
        );
        card.setPadding(new Insets(48, 48, 48, 48));

        // Logo
        HBox logoRow = new HBox(10);
        logoRow.setAlignment(Pos.CENTER);
        Label dot  = new Label("◆");
        dot.setStyle("-fx-text-fill: #2563EB; -fx-font-size: 22px; -fx-font-weight: 900;");
        Label name = new Label("TIME FLOW");
        name.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: 900; -fx-letter-spacing: 3px;");
        logoRow.getChildren().addAll(dot, name);
        VBox.setMargin(logoRow, new Insets(0, 0, 8, 0));

        Label subLogo = new Label("Tracker de Produtividade");
        subLogo.setStyle("-fx-text-fill: #475569; -fx-font-size: 12px; -fx-font-weight: 600;");
        subLogo.setAlignment(Pos.CENTER);
        subLogo.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(subLogo, new Insets(0, 0, 36, 0));

        Region sep1 = sep();
        VBox.setMargin(sep1, new Insets(0, 0, 32, 0));

        // Email
        Label lblEmailLbl = fieldLabel("EMAIL");
        inputEmail = new TextField();
        inputEmail.setPromptText("seu@email.com");
        styleInput(inputEmail);
        VBox.setMargin(inputEmail, new Insets(6, 0, 20, 0));

        // Senha
        Label lblSenhaLbl = fieldLabel("SENHA");
        inputSenha = new PasswordField();
        inputSenha.setPromptText("••••••••");
        styleInput(inputSenha);
        VBox.setMargin(inputSenha, new Insets(6, 0, 8, 0));

        // Feedback
        lblErro = new Label("");
        lblErro.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px; -fx-font-weight: 600;");
        lblErro.setWrapText(true);
        lblErro.setMaxWidth(Double.MAX_VALUE);
        lblErro.setVisible(false);

        lblCarregando = new Label("Autenticando...");
        lblCarregando.setStyle("-fx-text-fill: #2563EB; -fx-font-size: 12px; -fx-font-weight: 600;");
        lblCarregando.setVisible(false);

        // Botão
        btnEntrar = new Button("ENTRAR");
        btnEntrar.setMaxWidth(Double.MAX_VALUE);
        styleBtnPrimario(btnEntrar);
        VBox.setMargin(btnEntrar, new Insets(16, 0, 0, 0));

        // Ações
        inputEmail.setOnAction(e -> inputSenha.requestFocus());
        inputSenha.setOnAction(e -> fazerLogin());
        btnEntrar.setOnAction(e -> fazerLogin());

        // Rodapé
        Region sep2 = sep();
        VBox.setMargin(sep2, new Insets(32, 0, 20, 0));
        Label rodape = new Label("rogy.com.br — Gestão de equipe");
        rodape.setStyle("-fx-text-fill: #334155; -fx-font-size: 11px;");
        rodape.setAlignment(Pos.CENTER);
        rodape.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(
            logoRow, subLogo, sep1,
            lblEmailLbl, inputEmail,
            lblSenhaLbl, inputSenha,
            lblErro, lblCarregando,
            btnEntrar,
            sep2, rodape
        );

        StackPane center = new StackPane(card);
        center.setAlignment(Pos.CENTER);
        root.setCenter(center);

        Platform.runLater(() -> inputEmail.requestFocus());
    }

    // ── Login ─────────────────────────────────────────────────────────

    private void fazerLogin() {
        String email = inputEmail.getText().trim();
        String senha = inputSenha.getText();

        if (email.isEmpty() || senha.isEmpty()) {
            mostrarErro("Preencha email e senha.");
            return;
        }

        setCarregando(true);

        Thread.ofVirtual().start(() -> {
            SupabaseAuth.LoginResult result = SupabaseAuth.login(email, senha);
            Platform.runLater(() -> {
                setCarregando(false);
                if (result.sucesso()) {
                    PerfilSession.set(
                        result.perfilId(),
                        result.empresaId(),
                        result.nome(),
                        result.nivel(),
                        result.token()
                    );
                    if (onLoginSucesso != null) onLoginSucesso.run();
                } else {
                    mostrarErro(result.erro());
                    inputSenha.clear();
                    inputSenha.requestFocus();
                }
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void mostrarErro(String msg) {
        lblErro.setText("⚠  " + msg);
        lblErro.setVisible(true);
        lblCarregando.setVisible(false);
    }

    private void setCarregando(boolean v) {
        btnEntrar.setDisable(v);
        inputEmail.setDisable(v);
        inputSenha.setDisable(v);
        lblCarregando.setVisible(v);
        lblErro.setVisible(false);
        btnEntrar.setText(v ? "Entrando..." : "ENTRAR");
    }

    private static Label fieldLabel(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-text-fill: #475569; -fx-font-size: 10px; -fx-font-weight: 700; -fx-letter-spacing: 1px;");
        return l;
    }

    private static void styleInput(Control c) {
        String base =
            "-fx-background-color: rgba(255,255,255,0.05);" +
            "-fx-border-color: rgba(255,255,255,0.08);" +
            "-fx-border-radius: 10px;" +
            "-fx-background-radius: 10px;" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: #334155;" +
            "-fx-font-size: 14px;" +
            "-fx-padding: 12 14 12 14;";
        String focused =
            "-fx-background-color: rgba(37,99,235,0.08);" +
            "-fx-border-color: #2563EB;" +
            "-fx-border-radius: 10px;" +
            "-fx-background-radius: 10px;" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: #334155;" +
            "-fx-font-size: 14px;" +
            "-fx-padding: 12 14 12 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.25), 8, 0, 0, 0);";
        c.setStyle(base);
        c.setMaxWidth(Double.MAX_VALUE);
        c.focusedProperty().addListener((obs, o, f) -> c.setStyle(f ? focused : base));
    }

    private static void styleBtnPrimario(Button b) {
        String base =
            "-fx-background-color: #2563EB;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 800;" +
            "-fx-letter-spacing: 1.5px;" +
            "-fx-background-radius: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 14 0 14 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.4), 16, 0, 0, 4);";
        String hover =
            "-fx-background-color: #1D4ED8;" +
            "-fx-text-fill: white;" +
            "-fx-font-size: 13px;" +
            "-fx-font-weight: 800;" +
            "-fx-letter-spacing: 1.5px;" +
            "-fx-background-radius: 10px;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 14 0 14 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(37,99,235,0.6), 20, 0, 0, 6);";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e  -> b.setStyle(base));
    }

    private static Region sep() {
        Region r = new Region();
        r.setPrefHeight(1);
        r.setMaxWidth(Double.MAX_VALUE);
        r.setStyle("-fx-background-color: rgba(255,255,255,0.06);");
        return r;
    }
}