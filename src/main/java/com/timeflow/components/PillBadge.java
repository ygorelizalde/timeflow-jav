package com.timeflow.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * PillBadge — label arredondado de status (ex: "● Sincronizado", "● Offline").
 * Equivalente ao PillBadge do Python.
 */
public class PillBadge extends Label {

    public PillBadge(String texto, String hexColor) {
        super(texto);
        update(texto, hexColor);
    }

    /**
     * Atualiza texto e cor do badge com efeito visual.
     * @param texto    Texto a exibir (ex: "● Rastreando")
     * @param hexColor Cor hex da borda/texto (ex: "#10B981")
     */
    public void update(String texto, String hexColor) {
        setText(texto);

        Color cor      = Color.web(hexColor);
        Color bgColor  = Color.web(hexColor, 0.13);  // fundo 13% opaco
        Color bdColor  = Color.web(hexColor, 0.33);  // borda 33% opaca

        setBackground(new Background(new BackgroundFill(
            bgColor, new CornerRadii(10), Insets.EMPTY
        )));
        setBorder(new Border(new BorderStroke(
            bdColor, BorderStrokeStyle.SOLID,
            new CornerRadii(10), new BorderWidths(1)
        )));
        setTextFill(cor);
        setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        setPadding(new Insets(3, 12, 3, 12));
    }

    // ── Factory helpers ───────────────────────────────────────────────

    public static PillBadge offline() {
        return new PillBadge("● Offline", "#64748B");
    }

    public static PillBadge rastreando() {
        return new PillBadge("● Rastreando", "#10B981");
    }

    public static PillBadge parado() {
        return new PillBadge("● Parado", "#64748B");
    }
}
