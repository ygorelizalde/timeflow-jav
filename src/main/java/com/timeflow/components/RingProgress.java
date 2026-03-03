package com.timeflow.components;

import com.timeflow.config.Theme;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

/**
 * RingProgress — componente circular de progresso com gradiente e animação.
 * Equivalente ao RingProgress do Python (QPainter).
 *
 * Uso:
 *   RingProgress ring = new RingProgress(160);
 *   ring.setValue(72.5);   // 0.0 – 100.0
 */
public class RingProgress extends StackPane {

    // ── Propriedade animável ───────────────────────────────────────────
    private final DoubleProperty value = new SimpleDoubleProperty(0) {
        @Override protected void invalidated() { redraw(); }
    };

    private final Canvas canvas;
    private final double size;
    private static final double STROKE  = 10.0;
    private static final double MARGIN  = 12.0;

    // ── Timeline para animar transição de valor ───────────────────────
    private Timeline animation;

    public RingProgress(double size) {
        this.size   = size;
        this.canvas = new Canvas(size, size);
        getChildren().add(canvas);
        setMaxSize(size, size);
        setMinSize(size, size);
        redraw();
    }

    // ── API pública ───────────────────────────────────────────────────

    public void setValue(double newValue) {
        newValue = Math.max(0, Math.min(100, newValue));
        if (animation != null) animation.stop();
        animation = new Timeline(
            new KeyFrame(Duration.millis(600),
                new KeyValue(value, newValue, Interpolator.EASE_BOTH))
        );
        animation.play();
    }

    public double getValue() { return value.get(); }
    public DoubleProperty valueProperty() { return value; }

    // ── Renderização ──────────────────────────────────────────────────

    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, size, size);

        double x      = MARGIN;
        double y      = MARGIN;
        double w      = size - 2 * MARGIN;
        double h      = size - 2 * MARGIN;
        double v      = value.get();

        // ── Trilha (círculo completo cinza escuro) ────────────────────
        gc.setStroke(Color.web("#1E293B"));
        gc.setLineWidth(STROKE);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.strokeOval(x, y, w, h);

        // ── Arco de progresso com gradiente ──────────────────────────
        if (v > 0) {
            gc.save();

            // Gradiente conforme score (igual ao Python)
            Color c1, c2;
            if (v >= 70) {
                c1 = Color.web(Theme.SUCCESS);
                c2 = Color.web("#34D399");
            } else if (v >= 40) {
                c1 = Color.web(Theme.WARNING);
                c2 = Color.web("#FCD34D");
            } else {
                c1 = Color.web(Theme.DANGER);
                c2 = Color.web("#F87171");
            }

            LinearGradient grad = new LinearGradient(
                0, 0, size, size, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, c1), new Stop(1.0, c2)
            );

            // JavaFX Canvas não suporta stroke com gradiente diretamente.
            // Estratégia: rotacionar e desenhar usando stroke com cor sólida
            // misturada, então reaplicar clip para o arco correto.
            // Implementação robusta com loop de segmentos finos:
            double angleStart = -90.0; // topo
            double sweep      = -(v / 100.0) * 360.0;
            int    segments   = Math.max(1, (int) Math.abs(sweep));

            for (int i = 0; i < segments; i++) {
                double t     = (double) i / segments;
                double angle = Math.toRadians(angleStart + sweep * t);
                double cx    = x + w / 2 + (w / 2) * Math.cos(angle);
                double cy    = y + h / 2 + (h / 2) * Math.sin(angle);

                // Interpola cor ao longo do gradiente
                double r = c1.getRed()   + (c2.getRed()   - c1.getRed())   * t;
                double g = c1.getGreen() + (c2.getGreen() - c1.getGreen()) * t;
                double b = c1.getBlue()  + (c2.getBlue()  - c1.getBlue())  * t;

                gc.setStroke(new Color(
                    Math.max(0, Math.min(1, r)),
                    Math.max(0, Math.min(1, g)),
                    Math.max(0, Math.min(1, b)), 1.0));
                gc.setLineWidth(STROKE + 0.5);
                gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

                double nextAngle = Math.toRadians(angleStart + sweep * ((double)(i + 1) / segments));
                double nx = x + w / 2 + (w / 2) * Math.cos(nextAngle);
                double ny = y + h / 2 + (h / 2) * Math.sin(nextAngle);
                gc.strokeLine(cx, cy, nx, ny);
            }
            gc.restore();
        }

        // ── Texto central ─────────────────────────────────────────────
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, size * 0.155));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(
            String.format("%d%%", (int) v),
            size / 2,
            size / 2 + size * 0.06
        );
    }
}
