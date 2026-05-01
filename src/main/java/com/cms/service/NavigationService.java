package com.cms.service;

import com.cms.util.AnimationHelper;
import javafx.animation.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class NavigationService {
    private static final Logger logger = LoggerFactory.getLogger(NavigationService.class);
    private static NavigationService instance;

    private StackPane contentArea;
    private final Stack<NavigationState> history = new Stack<>();
    private final List<NavigationObserver> observers = new ArrayList<>();

    public interface NavigationObserver {
        void onNavigationChanged(NavigationState currentState);
    }

    public static NavigationService getInstance() {
        if (instance == null) instance = new NavigationService();
        return instance;
    }

    private NavigationService() {}

    public void setContentArea(StackPane contentArea) {
        this.contentArea = contentArea;
    }

    public void addObserver(NavigationObserver observer) {
        observers.add(observer);
    }

    public void navigateToRoot(String title, String fxmlPath) {
        history.clear();
        pushView(title, fxmlPath);
    }

    public void navigateTo(String title, String fxmlPath) {
        pushView(title, fxmlPath);
    }

    public void navigateTo(String title, String fxmlPath, ControllerInitializer initializer) {
        pushView(title, fxmlPath, initializer);
    }

    public void goBack() {
        if (history.size() > 1) {
            history.pop();
            renderState(history.peek(), false);
        }
    }

    private void pushView(String title, String fxmlPath) {
        pushView(title, fxmlPath, null);
    }

    private void pushView(String title, String fxmlPath, ControllerInitializer initializer) {
        NavigationState state = new NavigationState(title, fxmlPath, initializer);
        history.push(state);
        renderState(state, true);
    }

    private void renderState(NavigationState state, boolean isForward) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(state.fxmlPath));
            Parent view = loader.load();

            if (state.initializer != null) {
                state.initializer.init(loader.getController());
            }

            if (!contentArea.getChildren().isEmpty()) {
                // Fade out old, then slide in new
                Parent old = (Parent) contentArea.getChildren().get(0);
                FadeTransition fadeOut = new FadeTransition(Duration.millis(150), old);
                fadeOut.setFromValue(1); fadeOut.setToValue(0);
                fadeOut.setOnFinished(e -> {
                    contentArea.getChildren().setAll(view);
                    if (isForward) {
                        AnimationHelper.fadeSlideIn(view);
                    } else {
                        AnimationHelper.slideInFromLeft(view);
                    }
                });
                fadeOut.play();
            } else {
                contentArea.getChildren().setAll(view);
                AnimationHelper.fadeSlideIn(view);
            }

            notifyObservers(state);
        } catch (IOException e) {
            logger.error("Navigation failed to: {}", state.fxmlPath, e);
        }
    }

    private void notifyObservers(NavigationState state) {
        for (NavigationObserver observer : observers) observer.onNavigationChanged(state);
    }

    public List<String> getBreadcrumbs() {
        List<String> crumbs = new ArrayList<>();
        for (NavigationState state : history) crumbs.add(state.title);
        return crumbs;
    }

    public boolean canGoBack() { return history.size() > 1; }
    public NavigationState getCurrentState() { return history.isEmpty() ? null : history.peek(); }

    public static class NavigationState {
        public final String title;
        public final String fxmlPath;
        public final ControllerInitializer initializer;

        public NavigationState(String title, String fxmlPath, ControllerInitializer initializer) {
            this.title = title; this.fxmlPath = fxmlPath; this.initializer = initializer;
        }
    }

    @FunctionalInterface
    public interface ControllerInitializer {
        void init(Object controller);
    }
}
