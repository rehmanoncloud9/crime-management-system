package com.cms.controller;

import com.cms.model.User;
import com.cms.service.AuthService;
import com.cms.util.AnimationHelper;
import com.cms.util.NexusAlert;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final AuthService authService = new AuthService();
    private static final Properties APP_CONFIG = loadAppConfig();

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordTextField; // For show/hide toggle
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    @FXML
    private Hyperlink supportLink;

    @FXML
    private Region orb1;
    @FXML
    private Region orb2;
    @FXML
    private Region orb3;

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

            // Animate background orbs
            if (orb1 != null && orb2 != null && orb3 != null) {
                animateOrb(orb1, 400, 300, 15000);
                animateOrb(orb2, -300, -200, 20000);
                animateOrb(orb3, -200, 400, 18000);
            }
        });

        updateSupportLink();
    }

    private void animateOrb(Node orb, double dx, double dy, double durationMs) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(durationMs), orb);
        tt.setByX(dx);
        tt.setByY(dy);
        tt.setCycleCount(Animation.INDEFINITE);
        tt.setAutoReverse(true);
        tt.play();
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText();

        // Retrieve password from whichever field is currently active/visible
        String password = passwordField.isVisible() ? passwordField.getText() : passwordTextField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter both username and password.");
            AnimationHelper.shake(errorLabel);
            return;
        }

        errorLabel.setText("Authenticating...");
        errorLabel.getStyleClass().removeAll("error-text");
        errorLabel.getStyleClass().add("text-secondary");
        if (loginButton != null)
            loginButton.setDisable(true);

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
                logger.info("User '{}' must change password — redirecting to change-password screen.",
                        user.getUsername());
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
            if (loginButton != null)
                loginButton.setDisable(false);
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
                    createScale(root, 0.96, 1.0, 450));
            entrance.setInterpolator(Interpolator.EASE_OUT);
            entrance.play();

        } catch (IOException e) {
            logger.error("Failed to load main dashboard", e);
            errorLabel.setText("Navigation failed. Check logs.");
        }
    }

    private FadeTransition createFade(Node node, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(from);
        ft.setToValue(to);
        return ft;
    }

    @FXML
    private void togglePasswordVisibility() {
        if (passwordField.isVisible()) {
            passwordTextField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordTextField.setVisible(true);
            passwordTextField.setManaged(true);
            passwordTextField.requestFocus();
            passwordTextField.positionCaret(passwordTextField.getText().length());
        } else {
            passwordField.setText(passwordTextField.getText());
            passwordTextField.setVisible(false);
            passwordTextField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    private ScaleTransition createScale(Node node, double from, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), node);
        st.setFromX(from);
        st.setFromY(from);
        st.setToX(to);
        st.setToY(to);
        return st;
    }

    @FXML
    private void handleContactAdmin(ActionEvent event) {
        try {
            String supportUrl = APP_CONFIG.getProperty("app.support.url", "").trim();
            String supportEmail = APP_CONFIG.getProperty("app.support.email", "").trim();

            if (!supportUrl.isBlank()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(supportUrl));
            } else if (!supportEmail.isBlank()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("mailto:" + supportEmail));
            } else {
                NexusAlert.showError("Support contact is not configured. Please contact your system administrator.");
            }
        } catch (Exception e) {
            logger.error("Failed to open support link", e);
            String fallback = APP_CONFIG.getProperty("app.support.email", "administrator");
            NexusAlert.show("Technical Support", "Could not open support link. Contact: " + fallback, NexusAlert.Type.SUCCESS);
        }
    }

    private void updateSupportLink() {
        if (supportLink == null) return;
        String supportEmail = APP_CONFIG.getProperty("app.support.email", "").trim();
        String supportUrl = APP_CONFIG.getProperty("app.support.url", "").trim();

        if (!supportEmail.isBlank()) {
            supportLink.setText(supportEmail);
        } else if (!supportUrl.isBlank()) {
            supportLink.setText("Contact Support");
        } else {
            supportLink.setText("Contact Administrator");
        }
    }

    private static Properties loadAppConfig() {
        Properties props = new Properties();
        try (InputStream in = LoginController.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            logger.warn("Could not load config.properties for support contact: {}", e.getMessage());
        }
        return props;
    }
}
