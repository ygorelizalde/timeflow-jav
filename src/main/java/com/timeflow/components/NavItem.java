package com.timeflow.components;

import com.timeflow.config.Theme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * NavItem — botão de navegação da sidebar.
 * Equivalente ao NavItem(QPushButton) do Python.
 *
 * Uso:
 *   NavItem item = new NavItem("⏱", "Tracker", true);
 *   item.setOnNavClicked(() -> stackPane.getSelectionModel()...);
 */
public class NavItem extends HBox {

    private boolean ativo;
    private final String icone;
    private final String texto;
    private Runnable onClickCallback;

    private static final Color BG_ACTIVE  = Color.web(Theme.PRIMARY, 0.13);
    private static final Color FG_ACTIVE  = Color.WHITE;
    private static final Color FG_INACTIVE = Color.web(Theme.SIDEBAR_FG);
    private static final Color BORDER_ACTIVE   = Color.web(Theme.PRIMARY);
    private static final Color BORDER_INACTIVE = Color.TRANSPARENT;
    private static final Color BORDER_HOVER    = Color.web(Theme.SIDEBAR_FG);

    public NavItem(String icone, String texto, boolean ativo) {
        this.icone = icone;
        this.texto = texto;
        this.ativo = ativo;

        setMinHeight(52);
        setMaxHeight(52);
        setMaxWidth(Double.MAX_VALUE);
        setAlignment(Pos.CENTER_LEFT);
        setCursor(Cursor.HAND);
        setPadding(new Insets(0, 0, 0, 28));

        render();

        // Hover
        setOnMouseEntered(e -> { if (!this.ativo) applyHover(true); });
        setOnMouseExited(e  -> { if (!this.ativo) applyHover(false); });
        setOnMouseClicked(e -> { if (onClickCallback != null) onClickCallback.run(); });
    }

    public NavItem(String icone, String texto) {
        this(icone, texto, false);
    }

    public void setAtivo(boolean v) {
        this.ativo = v;
        render();
    }

    public boolean isAtivo() { return ativo; }

    public void setOnNavClicked(Runnable r) { this.onClickCallback = r; }

    // ── Visual ────────────────────────────────────────────────────────

    private void render() {
        getChildren().clear();

        Label lbl = new Label(icone + "   " + texto);
        lbl.setFont(Font.font("Segoe UI", ativo ? FontWeight.BOLD : FontWeight.MEDIUM, 14));
        lbl.setTextFill(ativo ? FG_ACTIVE : FG_INACTIVE);
        lbl.setMouseTransparent(true);
        getChildren().add(lbl);

        if (ativo) {
            // Fundo com gradiente (esq→dir: azul suave → transparente)
            setBackground(new Background(new BackgroundFill(
                BG_ACTIVE, CornerRadii.EMPTY, Insets.EMPTY
            )));
            // Borda esquerda azul
            setBorder(new Border(new BorderStroke(
                BORDER_ACTIVE, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 0, 3), Insets.EMPTY
            )));
        } else {
            setBackground(Background.EMPTY);
            setBorder(new Border(new BorderStroke(
                BORDER_INACTIVE, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 0, 3), Insets.EMPTY
            )));
        }
    }

    private void applyHover(boolean hover) {
        if (hover) {
            setBackground(new Background(new BackgroundFill(
                Color.web("#FFFFFF", 0.05), CornerRadii.EMPTY, Insets.EMPTY
            )));
            setBorder(new Border(new BorderStroke(
                BORDER_HOVER, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT,
                BorderStrokeStyle.SOLID, BorderStrokeStyle.NONE,
                BorderStrokeStyle.NONE, BorderStrokeStyle.NONE,
                CornerRadii.EMPTY, new BorderWidths(0, 0, 0, 3), Insets.EMPTY
            )));
        } else {
            render();
        }
    }
}
