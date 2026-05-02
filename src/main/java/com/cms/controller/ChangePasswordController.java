package com.cms.controller;

import com.cms.model.User;
import com.cms.service.AuthService;
import com.cms.service.SessionManager;
import com.cms.service.UserService;
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
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the mandatory password-change screen shown to any user whose
 * must_change_password flag is true (e.g. first login, admin-reset).
 *
 * The user cannot proceed to the dashboard until they set a new password.
 */
public class ChangePasswordController {

    private static final Logger logger = LoggerFactory.getLogger(ChangePasswordController.class);

    @FXML private Label usernameLabel;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;
    @FXML private Button submitButton;

    private final AuthService authService = new AuthService();
    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null && usernameLabel != null) {
            usernameLabel.setText("Logged in as: " + user.getUsername());
        }
    }

    @FXML
    private void handleChangePassword(ActionEvent event) {
        String current = currentPasswordField.getText();
        String newPass  = newPasswordField.getText();
        String confirm  = confirmPasswordField.getText();

        // Validate
        if (current.isBlank() || newPass.isBlank() || confirm.isBlank()) {
            showError("All fields are required.");
            return;
        }
        if (!newPass.equals(confirm)) {
            showError("New passwords do not match.");
            return;
        }
        if (newPass.length() < 8) {
            showError("Password must be at least 8 characters.");
            return;
        }
        if (newPass.equals(current)) {
            showError("New password must be different from current password.");
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            showError("Session expired. Please log in again.");
            return;
        }

        // Verify current password
        boolean currentValid = authService.verifyCurrentPassword(current, user.getPasswordHash());
        if (!currentValid) {
            showError("Current password is incorrect.");
            return;
        }

        if (submitButton != null) submitButton.setDisable(true);
        showInfo("Updating password...");

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                userService.resetPassword(user.getId(), newPass);
                userService.clearMustChangePassword(user.getId());
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            logger.info("User '{}' changed their password successfully.", user.getUsername());
            navigateToDashboard(event);
        });

        task.setOnFailed(e -> {
            logger.error("Password change failed for '{}'", user.getUsername(), task.getException());
            showError("Failed to update password. Please try again.");
            if (submitButton != null) submitButton.setDisable(false);
        });

        new Thread(task, "password-change").start();
    }

    private void navigateToDashboard(ActionEvent event) {
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
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to navigate to dashboard after password change", e);
            showError("Navigation failed. Please restart the application.");
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            if (errorLabel != null) {
                errorLabel.getStyleClass().removeAll("info-text");
                errorLabel.getStyleClass().add("error-text");
                errorLabel.setText(message);
            }
        });
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            if (errorLabel != null) {
                errorLabel.getStyleClass().removeAll("error-text");
                errorLabel.getStyleClass().add("info-text");
                errorLabel.setText(message);
            }
        });
    }
}
