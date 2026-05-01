package com.cms.util;

import animatefx.animation.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

/**
 * CMS Elite Dark Theme — Advanced Animation Helper
 * Uses AnimateFX + JavaFX built-in animations for professional effects.
 */
public class AnimationHelper {

    // ── ENTRANCE ANIMATIONS ──────────────────────────────────────────────

    /** Fade + slide-in from bottom (most used for page loads) */
    public static void fadeSlideIn(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateY(20);
        ParallelTransition pt = new ParallelTransition(node,
            fade(node, 0, 1, 380),
            translateY(node, 20, 0, 380)
        );
        pt.play();
    }

    /** Fade + slide-in from left (for sidebar items) */
    public static void slideInFromLeft(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateX(-28);
        ParallelTransition pt = new ParallelTransition(node,
            fade(node, 0, 1, 350),
            translateX(node, -28, 0, 350)
        );
        pt.play();
    }

    /** Fade + slide-in from right */
    public static void slideInFromRight(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        node.setTranslateX(28);
        ParallelTransition pt = new ParallelTransition(node,
            fade(node, 0, 1, 350),
            translateX(node, 28, 0, 350)
        );
        pt.play();
    }

    /** Staggered entrance for a list of nodes (sidebar menu items) */
    public static void staggeredEntrance(List<Node> nodes, int delayBetweenMs) {
        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            final int delayMs = i * delayBetweenMs;
            node.setOpacity(0);
            node.setTranslateX(-20);
            PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
            pause.setOnFinished(e -> {
                ParallelTransition pt = new ParallelTransition(node,
                    fade(node, 0, 1, 300),
                    translateX(node, -20, 0, 300)
                );
                pt.play();
            });
            pause.play();
        }
    }

    /** Staggered entry for stat cards (top-down cascade) */
    public static void cascadeCards(List<Node> cards) {
        for (int i = 0; i < cards.size(); i++) {
            final Node card = cards.get(i);
            final int delayMs = i * 80;
            card.setOpacity(0);
            card.setTranslateY(24);
            card.setScaleX(0.95);
            card.setScaleY(0.95);
            PauseTransition pause = new PauseTransition(Duration.millis(delayMs));
            pause.setOnFinished(e -> {
                ParallelTransition pt = new ParallelTransition(card,
                    fade(card, 0, 1, 420),
                    translateY(card, 24, 0, 420),
                    scale(card, 0.95, 1.0, 420)
                );
                pt.setInterpolator(Interpolator.EASE_OUT);
                pt.play();
            });
            pause.play();
        }
    }

    /** AnimateFX — ZoomIn entrance */
    public static void zoomIn(Node node) {
        if (node == null) return;
        new ZoomIn(node).play();
    }

    /** AnimateFX — FadeIn */
    public static void fadeIn(Node node) {
        if (node == null) return;
        new FadeIn(node).play();
    }

    /** AnimateFX — FadeInUp (page module load) */
    public static void fadeInUp(Node node) {
        if (node == null) return;
        new FadeInUp(node).play();
    }

    /** AnimateFX — FadeInLeft (sidebar panel) */
    public static void fadeInLeft(Node node) {
        if (node == null) return;
        new FadeInLeft(node).play();
    }

    /** AnimateFX — BounceIn (alerts / badges) */
    public static void bounceIn(Node node) {
        if (node == null) return;
        new BounceIn(node).play();
    }

    /** AnimateFX — FlipInX (table rows / data load) */
    public static void flipInX(Node node) {
        if (node == null) return;
        new FlipInX(node).play();
    }

    /** AnimateFX — Shake (login error feedback) */
    public static void shake(Node node) {
        if (node == null) return;
        new Shake(node).play();
    }

    /** AnimateFX — Pulse (attention-grabbing glow pulse) */
    public static void pulse(Node node) {
        if (node == null) return;
        new Pulse(node).play();
    }

    /** AnimateFX — RubberBand (success confirmation) */
    public static void rubberBand(Node node) {
        if (node == null) return;
        new RubberBand(node).play();
    }

    /** AnimateFX — Tada (highlight completion) */
    public static void tada(Node node) {
        if (node == null) return;
        new Tada(node).play();
    }

    /** AnimateFX — SlideInLeft */
    public static void animFxSlideInLeft(Node node) {
        if (node == null) return;
        new SlideInLeft(node).play();
    }

    /** AnimateFX — SlideInRight */
    public static void animFxSlideInRight(Node node) {
        if (node == null) return;
        new SlideInRight(node).play();
    }

    // ── EXIT ANIMATIONS ──────────────────────────────────────────────────

    /** Fade + slide-out upward (page unload) */
    public static void fadeSlideOut(Node node, Runnable onFinished) {
        if (node == null) { if (onFinished != null) onFinished.run(); return; }
        ParallelTransition pt = new ParallelTransition(node,
            fade(node, 1, 0, 220),
            translateY(node, 0, -18, 220)
        );
        if (onFinished != null) pt.setOnFinished(e -> onFinished.run());
        pt.play();
    }

    /** AnimateFX — FadeOut */
    public static void fadeOut(Node node, Runnable onFinished) {
        if (node == null) { if (onFinished != null) onFinished.run(); return; }
        FadeOut anim = new FadeOut(node);
        if (onFinished != null) anim.setOnFinished(e -> onFinished.run());
        anim.play();
    }

    // ── CONTINUOUS ANIMATIONS ────────────────────────────────────────────

    /**
     * Pulsing cyan glow — attaches to node indefinitely.
     * Good for live status indicators, active elements.
     */
    public static Timeline createPulsingGlow(Node node, Color glowColor) {
        DropShadow glow = new DropShadow(18, glowColor);
        node.setEffect(glow);
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(glow.radiusProperty(), 8, Interpolator.EASE_BOTH),
                new KeyValue(glow.spreadProperty(), 0.1, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(1000),
                new KeyValue(glow.radiusProperty(), 22, Interpolator.EASE_BOTH),
                new KeyValue(glow.spreadProperty(), 0.3, Interpolator.EASE_BOTH)
            )
        );
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * Subtle breathing scale animation (for logo / brand element)
     */
    public static Timeline createBreathingScale(Node node) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.scaleXProperty(), 1.0, Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(), 1.0, Interpolator.EASE_BOTH)
            ),
            new KeyFrame(Duration.millis(1800),
                new KeyValue(node.scaleXProperty(), 1.04, Interpolator.EASE_BOTH),
                new KeyValue(node.scaleYProperty(), 1.04, Interpolator.EASE_BOTH)
            )
        );
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * Animated counter — counts from 0 to targetValue over duration.
     * Call with a Label to show animated numbers.
     */
    public static void animateCounter(javafx.scene.control.Label label, int targetValue, int durationMs) {
        if (label == null) return;
        Timeline timeline = new Timeline();
        final int[] current = {0};
        final int steps = 40;
        final int stepDelay = durationMs / steps;
        for (int i = 0; i <= steps; i++) {
            final int step = i;
            KeyFrame kf = new KeyFrame(Duration.millis((long) step * stepDelay), e -> {
                double progress = (double) step / steps;
                // Ease-out curve
                double eased = 1 - Math.pow(1 - progress, 3);
                current[0] = (int) (targetValue * eased);
                label.setText(String.valueOf(current[0]));
            });
            timeline.getKeyFrames().add(kf);
        }
        timeline.setOnFinished(e -> label.setText(String.valueOf(targetValue)));
        timeline.play();
    }

    /**
     * Shimmer scan effect across a node — creates a moving highlight.
     * Simulates a scanning/loading shimmer.
     */
    public static Timeline createShimmerEffect(Node node) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(node.translateXProperty(), -10, Interpolator.EASE_IN)
            ),
            new KeyFrame(Duration.millis(1400),
                new KeyValue(node.translateXProperty(), 10, Interpolator.EASE_OUT)
            )
        );
        timeline.setAutoReverse(true);
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * Rotational spin (for loading spinner or icon)
     */
    public static Timeline createSpinAnimation(Node node) {
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.millis(1200),
                new KeyValue(node.rotateProperty(), 360, Interpolator.LINEAR)
            )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        return timeline;
    }

    /**
     * Hover lift effect — elevates node with scale + translate on hover.
     * Attach to buttons, cards, etc.
     */
    public static void addHoverLift(Node node) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(180), node);
        scaleUp.setToX(1.03);
        scaleUp.setToY(1.03);
        TranslateTransition up = new TranslateTransition(Duration.millis(180), node);
        up.setToY(-3);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(180), node);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);
        TranslateTransition down = new TranslateTransition(Duration.millis(180), node);
        down.setToY(0);

        node.setOnMouseEntered(e -> {
            scaleUp.play();
            up.play();
        });
        node.setOnMouseExited(e -> {
            scaleDown.play();
            down.play();
        });
    }

    /**
     * Click press effect (scale down on press)
     */
    public static void addClickPress(Node node) {
        node.setOnMousePressed(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(80), node);
            st.setToX(0.96);
            st.setToY(0.96);
            st.play();
        });
        node.setOnMouseReleased(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    /**
     * Page transition — fade out old, callback, then caller fades in new content.
     */
    public static void pageTransition(Node oldContent, Pane container, Node newContent) {
        if (oldContent != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(180), oldContent);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(e -> {
                container.getChildren().setAll(newContent);
                fadeSlideIn(newContent);
            });
            fadeOut.play();
        } else {
            container.getChildren().setAll(newContent);
            fadeSlideIn(newContent);
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────

    private static FadeTransition fade(Node node, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        return ft;
    }

    private static TranslateTransition translateY(Node node, double from, double to, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromY(from);
        tt.setToY(to);
        tt.setInterpolator(Interpolator.EASE_OUT);
        return tt;
    }

    private static TranslateTransition translateX(Node node, double from, double to, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromX(from);
        tt.setToX(to);
        tt.setInterpolator(Interpolator.EASE_OUT);
        return tt;
    }

    private static ScaleTransition scale(Node node, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), node);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        return st;
    }
}
