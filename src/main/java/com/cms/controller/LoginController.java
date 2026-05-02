package com.cms.controller;

import com.cms.model.User;
import com.cms.service.AuthService;
import com.cms.util.AnimationHelper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;


public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final AuthService authService = new AuthService();

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    @FXML
    public void initialize() {
        // Animate login form entrance
        Platform.runLater(() -> {
            if (usernameField != null) {
                // Stagger field entrances
                AnimationHelper.fadeSlideIn(usernameField);
                PauseTransition p1 = new PauseTransition(Duration.millis(100));
                p1.setOnFinished(e -> AnimationHelper.fadeSlideIn(passwordField));
                p1.play();
                PauseTransition p2 = new PauseTransition(Duration.millis(200));
                p2.setOnFinished(e -> {
                    if (loginButton != null) {
                        AnimationHelper.zoomIn(loginButton);
                        AnimationHelper.addHoverLift(loginButton);
                        AnimationHelper.addClickPress(loginButton);
                    }
                });
                p2.play();
            }
        });
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter both username and password.");
            AnimationHelper.shake(errorLabel);
            return;
        }

        errorLabel.setText("Authenticating...");
        errorLabel.getStyleClass().removeAll("error-text");
        errorLabel.getStyleClass().add("text-secondary");
        if (loginButton != null) loginButton.setDisable(true);

        javafx.concurrent.Task<User> authTask = new javafx.concurrent.Task<>() {
            @Override
            protected User call() throws Exception {
                return authService.authenticate(username, password);
            }
        };

        authTask.setOnSucceeded(e -> {
            User user = authTask.getValue();
            logger.info("User logged in: {} ({})", user.getUsername(), user.getRole());
            if (user.isMustChangePassword()) {
                logger.info("User '{}' must change password — redirecting to change-password screen.", user.getUsername());
                navigateToChangePassword(event);
            } else {
                navigateToMain(event);
            }
        });

        authTask.setOnFailed(e -> {
            Throwable ex = authTask.getException();
            logger.error("Login failed: {}", ex != null ? ex.toString() : "Unknown error");
            errorLabel.getStyleClass().removeAll("text-secondary");
            errorLabel.getStyleClass().add("error-text");
            
            String errorMessage = "Authentication service unavailable.";
            if (ex != null) {
                if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                    errorMessage = ex.getMessage();
                } else if (ex.getCause() != null && ex.getCause().getMessage() != null) {
                    errorMessage = "Login error: " + ex.getCause().getMessage();
                } else {
                    errorMessage = "Critical Error: " + ex.getClass().getSimpleName();
                }
            }
            errorLabel.setText(errorMessage);
            logger.error("AUTHENTICATION FAILED", ex);
            if (loginButton != null) loginButton.setDisable(false);
            // Shake password field on error
            AnimationHelper.shake(passwordField);
            AnimationHelper.shake(errorLabel);
        });

        new Thread(authTask).start();
    }

    private void navigateToChangePassword(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/ChangePassword.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1200, 750);
            String css = getClass().getResource("/css/login.css").toExternalForm();
            scene.getStylesheets().add(css);
            stage.setScene(scene);
            stage.setTitle("Crime Management System v5.0 — Set New Password");
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to load change-password screen", e);
            errorLabel.setText("Navigation failed. Check logs.");
        }
    }

    private void navigateToMain(ActionEvent event) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Main.fxml"));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root, 1400, 850);
            String css = getClass().getResource("/css/application.css").toExternalForm();
            scene.getStylesheets().add(css);
            stage.setScene(scene);
            stage.setTitle("Crime Management System v5.0");
            stage.setResizable(true);
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            stage.centerOnScreen();

            // Elite entrance: fade + scale from 0.92
            root.setOpacity(0);
            root.setScaleX(0.96);
            root.setScaleY(0.96);
            stage.show();

            ParallelTransition entrance = new ParallelTransition(root,
                createFade(root, 0, 1, 450),
                createScale(root, 0.96, 1.0, 450)
            );
            entrance.setInterpolator(Interpolator.EASE_OUT);
            entrance.play();

        } catch (IOException e) {
            logger.error("Failed to load main dashboard", e);
            errorLabel.setText("Navigation failed. Check logs.");
        }
    }

    private FadeTransition createFade(Node node, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(from); ft.setToValue(to); return ft;
    }

    private ScaleTransition createScale(Node node, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), node);
        st.setFromX(from); st.setFromY(from); st.setToX(to); st.setToY(to); return st;
    }

    @FXML
    private void handleContactAdmin(ActionEvent event) {
        // Contact info is read from config.properties (app.support.email or app.support.url).
        // Fallback: show a plain dialog so there is no hardcoded personal number.
        Properties props = new Properties();
        try (java.io.InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}

        String supportUrl = props.getProperty("app.support.url", "");
        String supportEmail = props.getProperty("app.support.email", "");

        if (!supportUrl.isBlank()) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(supportUrl));
                return;
            } catch (Exception e) {
                logger.error("Failed to open support URL", e);
            }
        }

        String msg = supportEmail.isBlank()
            ? "Please contact your system administrator for technical support."
            : "For technical support, email: " + supportEmail;

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION, msg);
        alert.setHeaderText("Technical Support");
        alert.showAndWait();
    }
}
