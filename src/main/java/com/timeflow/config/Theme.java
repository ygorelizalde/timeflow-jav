package com.timeflow.config;

import javafx.scene.paint.Color;

/**
 * Theme — sistema centralizado de cores e espaçamentos.
 * Altere aqui para mudar o visual do app inteiro.
 */
public final class Theme {

    private Theme() {}

    // ── Sidebar ──────────────────────────────────────────────────────
    public static final String SIDEBAR_BG      = "#0F172A";
    public static final String SIDEBAR_FG      = "#94A3B8";
    public static final String SIDEBAR_HOVER   = "#1E293B";
    public static final String SIDEBAR_ACTIVE  = "#3B82F6";

    // ── Main / Cards ─────────────────────────────────────────────────
    public static final String MAIN_BG         = "#F1F5F9";
    public static final String CARD_BG         = "#FFFFFF";
    public static final String TEXT_TITLES     = "#1E293B";
    public static final String TEXT_BODY       = "#475569";
    public static final String BORDER          = "#E2E8F0";

    // ── Semânticas ────────────────────────────────────────────────────
    public static final String PRIMARY         = "#2563EB";
    public static final String SUCCESS         = "#10B981";
    public static final String DANGER          = "#EF4444";
    public static final String WARNING         = "#F59E0B";

    // ── Helpers JavaFX Color ──────────────────────────────────────────
    public static Color primary()   { return Color.web(PRIMARY); }
    public static Color success()   { return Color.web(SUCCESS); }
    public static Color danger()    { return Color.web(DANGER);  }
    public static Color warning()   { return Color.web(WARNING); }
    public static Color sidebarBg() { return Color.web(SIDEBAR_BG); }

    // ── Tipografia ────────────────────────────────────────────────────
    public static final String FONT_FAMILY  = "Segoe UI, Helvetica Neue, Arial, sans-serif";
    public static final double FONT_BASE    = 13.0;

    // ── Raios e Sombras ───────────────────────────────────────────────
    public static final double RADIUS_CARD   = 18.0;
    public static final double RADIUS_BUTTON = 14.0;
    public static final double SHADOW_BLUR   = 24.0;
}
