package com.cms.controller;

import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.model.enums.UserStatus;
import com.cms.service.ImageStorageService;
import com.cms.service.UserService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Controller for the Officer Edit Dialog (OfficerEditDialog.fxml).
 * Loaded as a modal dialog from UserAdminController.
 */
public class OfficerEditController {

    private static final Logger logger = LoggerFactory.getLogger(OfficerEditController.class);

    @FXML private ImageView photoView;
    @FXML private Label photoStatusLabel;
    @FXML private TextField badgeField;
    @FXML private TextField nameField;
    @FXML private ComboBox<Role> roleCombo;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField precinctField;
    @FXML private ComboBox<UserStatus> statusCombo;

    private final UserService userService = new UserService();
    private final ImageStorageService imageService = ImageStorageService.getInstance();
    private User officer;
    private File selectedPhotoFile;
    private boolean saved = false;

    @FXML
    public void initialize() {
        roleCombo.setItems(FXCollections.observableArrayList(Role.values()));
        statusCombo.setItems(FXCollections.observableArrayList(UserStatus.ACTIVE, UserStatus.SUSPENDED, UserStatus.INACTIVE));
    }

    /**
     * Called by the parent controller to inject the officer to edit.
     */
    public void setOfficer(User officer) {
        this.officer = officer;

        badgeField.setText(officer.getBadgeNumber());
        nameField.setText(officer.getFullName());
        roleCombo.setValue(officer.getRole());
        emailField.setText(officer.getEmail() != null ? officer.getEmail() : "");
        phoneField.setText(officer.getPhone() != null ? officer.getPhone() : "");
        precinctField.setText(officer.getPrecinct() != null ? officer.getPrecinct() : "");
        statusCombo.setValue(officer.getStatus());

        // Load existing photo
        javafx.scene.image.Image img = ImageStorageService.loadImage(officer.getProfilePhotoPath());
        photoView.setImage(img);
    }

    @FXML
    private void handleChangePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Officer Photo");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File file = fc.showOpenDialog(photoView.getScene().getWindow());
        if (file != null) {
            if (file.length() > 2 * 1024 * 1024) {
                new Alert(Alert.AlertType.WARNING, "Image must be under 2MB.").showAndWait();
                return;
            }
            selectedPhotoFile = file;
            photoView.setImage(new javafx.scene.image.Image(file.toURI().toString()));
            photoStatusLabel.setText("New photo selected ✓");
        }
    }

    @FXML
    private void handleSave() {
        // Validate
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Name cannot be empty.").showAndWait();
            return;
        }
        if (roleCombo.getValue() == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a role.").showAndWait();
            return;
        }
        if (statusCombo.getValue() == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a status.").showAndWait();
            return;
        }

        try {
            // Handle photo change
            if (selectedPhotoFile != null) {
                // Delete old photo if exists
                if (officer.getProfilePhotoPath() != null) {
                    imageService.deleteImage(officer.getProfilePhotoPath());
                }
                String newPath = imageService.saveImage(selectedPhotoFile, "officers");
                officer.setProfilePhotoPath(newPath);
            }

            // Update fields
            officer.setFullName(name);
            officer.setRole(roleCombo.getValue());
            officer.setEmail(emailField.getText().trim().isEmpty() ? null : emailField.getText().trim());
            officer.setPhone(phoneField.getText().trim().isEmpty() ? null : phoneField.getText().trim());
            officer.setPrecinct(precinctField.getText().trim().isEmpty() ? null : precinctField.getText().trim());
            officer.setStatus(statusCombo.getValue());

            // Persist
            userService.updateUser(officer);

            saved = true;
            logger.info("Officer {} updated successfully.", officer.getBadgeNumber());

            // Close dialog
            ((Stage) nameField.getScene().getWindow()).close();

        } catch (Exception e) {
            logger.error("Failed to update officer", e);
            new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void handleCancel() {
        ((Stage) nameField.getScene().getWindow()).close();
    }

    @FXML
    private void handleResetPassword() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
            "Reset password for " + officer.getFullName() + "?\nNew password will be: " + officer.getBadgeNumber() + "123!",
            ButtonType.YES, ButtonType.NO);
        alert.setTitle("Confirm Reset");
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                com.cms.service.AuthService authService = new com.cms.service.AuthService();
                officer.setPasswordHash(authService.hashPassword(officer.getBadgeNumber() + "123!"));
                officer.setMustChangePassword(true);
                userService.updateUser(officer);
                
                com.cms.model.User actor = com.cms.service.SessionManager.getInstance().getCurrentUser();
                com.cms.service.AuditService.getInstance().logAction(actor, "PASSWORD_RESET", "Reset password for officer: " + officer.getUsername());
                
                new Alert(Alert.AlertType.INFORMATION, "Password reset successful.").showAndWait();
            } catch (Exception e) {
                logger.error("Failed to reset password", e);
                new Alert(Alert.AlertType.ERROR, "Error: " + e.getMessage()).showAndWait();
            }
        }
    }

    @FXML
    private void handleDeleteOfficer() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
            "Are you absolutely sure you want to PERMANENTLY delete officer " + officer.getFullName() + "?\nThis action cannot be undone.",
            ButtonType.YES, ButtonType.NO);
        alert.setTitle("Confirm Delete");
        if (alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                com.cms.model.User actor = com.cms.service.SessionManager.getInstance().getCurrentUser();
                userService.deleteUser(officer.getId(), actor);
                saved = true; // Trigger refresh in parent
                ((Stage) nameField.getScene().getWindow()).close();
            } catch (Exception e) {
                logger.error("Failed to delete officer", e);
                new Alert(Alert.AlertType.ERROR, "Error deleting officer: " + e.getMessage()).showAndWait();
            }
        }
    }

    /**
     * Returns true if the user clicked Save and it succeeded.
     */
    public boolean isSaved() {
        return saved;
    }
}
