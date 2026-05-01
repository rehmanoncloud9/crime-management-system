package com.cms.controller;

import com.cms.model.User;
import com.cms.model.CaseFile;
import com.cms.model.enums.UserStatus;
import com.cms.model.enums.IncidentStatus;
import com.cms.service.UserService;
import com.cms.service.HibernateUtil;
import com.cms.service.ImageStorageService;
import com.cms.service.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class OfficerDetailController {
    
    private static final Logger logger = LoggerFactory.getLogger(OfficerDetailController.class);

    @FXML private ImageView photoView;
    @FXML private Circle statusCircle;
    @FXML private Label nameLabel;
    @FXML private Label badgeLabel;
    @FXML private Label roleBadge;
    @FXML private Label statusBadge;

    @FXML private Label rankLabel;
    @FXML private Label deptLabel;
    @FXML private Label precinctLabel;
    @FXML private Label joinedLabel;
    @FXML private Label emailLabel;
    @FXML private Label phoneLabel;
    @FXML private Label dobLabel;
    @FXML private Label lastActiveLabel;
    @FXML private Label createdAtLabel;

    @FXML private TableView<CaseFile> casesTable;
    @FXML private TableColumn<CaseFile, String> caseIdCol;
    @FXML private TableColumn<CaseFile, String> caseTitleCol;
    @FXML private TableColumn<CaseFile, String> caseStatusCol;
    @FXML private TableColumn<CaseFile, String> caseDateCol;

    @FXML private Label caseCountBadge;
    @FXML private Label perfTotalCases;
    @FXML private Label perfClosedCases;
    @FXML private Label perfActiveCases;
    @FXML private Label perfEfficiency;

    @FXML private Button statusBtn;
    @FXML private StackPane loadingOverlay;

    private final UserService userService = new UserService();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private User currentOfficer;

    @FXML
    public void initialize() {
        setupTable();
        setupTooltips();
    }

    private void setupTable() {
        caseIdCol.setCellValueFactory(new PropertyValueFactory<>("caseNumber"));
        caseTitleCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getIncident() != null ? 
                cellData.getValue().getIncident().getTitle() : "N/A"));
        caseStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        caseDateCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getOpenedAt() != null ? 
                cellData.getValue().getOpenedAt().format(dateFormatter) : "N/A"));
    }

    private void setupTooltips() {
        Tooltip.install(statusBtn, new Tooltip("Suspend or activate this officer account"));
        Tooltip.install(photoView, new Tooltip("Officer profile photo"));
    }

    public void loadOfficer(Long officerId) {
        showLoading(true);
        Task<User> task = new Task<>() {
            @Override
            protected User call() throws Exception {
                return userService.findById(officerId).orElseThrow(() -> new Exception("Officer not found"));
            }
        };

        task.setOnSucceeded(e -> {
            this.currentOfficer = task.getValue();
            Platform.runLater(() -> {
                updateUI();
                loadAssignedCases();
                showLoading(false);
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                showLoading(false);
                new Alert(Alert.AlertType.ERROR, "Failed to load officer: " + task.getException().getMessage()).showAndWait();
            });
        });

        new Thread(task).start();
    }

    private void updateUI() {
        nameLabel.setText(currentOfficer.getFullName());
        badgeLabel.setText("BADGE #" + (currentOfficer.getBadgeNumber() != null ? currentOfficer.getBadgeNumber() : "N/A"));
        
        roleBadge.setText(currentOfficer.getRole() != null ? currentOfficer.getRole().name() : "N/A");
        statusBadge.setText(currentOfficer.getStatus().name());
        
        boolean isActive = currentOfficer.getStatus() == UserStatus.ACTIVE;
        statusCircle.setFill(isActive ? Color.web("#16a34a") : Color.web("#e11d48"));
        statusBadge.getStyleClass().setAll("badge", isActive ? "badge-active" : "badge-closed");
        statusBtn.setText(isActive ? "Suspend Account" : "Activate Account");

        rankLabel.setText(currentOfficer.getOfficerRank() != null ? currentOfficer.getOfficerRank() : "N/A");
        deptLabel.setText(currentOfficer.getDepartment() != null ? currentOfficer.getDepartment() : "N/A");
        precinctLabel.setText(currentOfficer.getPrecinct() != null ? currentOfficer.getPrecinct() : "N/A");
        joinedLabel.setText(currentOfficer.getDateOfJoining() != null ? currentOfficer.getDateOfJoining().format(dateFormatter) : "N/A");
        
        emailLabel.setText(currentOfficer.getEmail() != null ? currentOfficer.getEmail() : "N/A");
        phoneLabel.setText(currentOfficer.getPhone() != null ? currentOfficer.getPhone() : "N/A");
        dobLabel.setText(currentOfficer.getDateOfBirth() != null ? currentOfficer.getDateOfBirth().format(dateFormatter) : "N/A");
        
        lastActiveLabel.setText(currentOfficer.getLastActive() != null ? currentOfficer.getLastActive().format(dateTimeFormatter) : "Never");
        createdAtLabel.setText(currentOfficer.getCreatedAt() != null ? currentOfficer.getCreatedAt().format(dateTimeFormatter) : "N/A");

        if (currentOfficer.getProfilePhotoPath() != null) {
            Image img = ImageStorageService.loadImage(currentOfficer.getProfilePhotoPath());
            if (img != null) photoView.setImage(img);
            else photoView.setImage(getDefaultAvatar());
        } else {
            photoView.setImage(getDefaultAvatar());
        }
    }

    private Image getDefaultAvatar() {
        try {
            return new Image(getClass().getResourceAsStream("/images/default-avatar.png"));
        } catch (Exception e) {
            return null; // Handle missing resource gracefully
        }
    }

    private void loadAssignedCases() {
        Task<List<CaseFile>> task = new Task<>() {
            @Override
            protected List<CaseFile> call() throws Exception {
                try {
                    return HibernateUtil.executeTransaction(session -> {
                        return session.createQuery(
                            "SELECT DISTINCT c FROM CaseFile c " +
                            "LEFT JOIN FETCH c.incident i " +
                            "LEFT JOIN FETCH i.crimeType " +
                            "WHERE c.primaryInvestigator.id = :id", CaseFile.class)
                            .setParameter("id", currentOfficer.getId())
                            .list();
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                    throw ex;
                }
            }
        };

        task.setOnSucceeded(e -> {
            List<CaseFile> cases = task.getValue();
            Platform.runLater(() -> {
                casesTable.setItems(FXCollections.observableArrayList(cases));
                updatePerformanceStats(cases);
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "Failed to load assigned cases: " + task.getException().getMessage()).show();
            });
        });

        new Thread(task).start();
    }

    private void updatePerformanceStats(List<CaseFile> cases) {
        int total = cases.size();
        long closed = cases.stream().filter(c -> c.getStatus() == IncidentStatus.CLOSED).count();
        long active = total - closed;
        double efficiency = total > 0 ? (double) closed / total * 100 : 0;

        caseCountBadge.setText(total + " CASES");
        perfTotalCases.setText(String.valueOf(total));
        perfClosedCases.setText(String.valueOf(closed));
        perfActiveCases.setText(String.valueOf(active));
        perfEfficiency.setText(String.format("%.1f%%", efficiency));
    }

    @FXML
    private void handleToggleStatus() {
        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) {
            new Alert(Alert.AlertType.ERROR, "No active session found.").show();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            "Are you sure you want to " + (currentOfficer.getStatus() == UserStatus.ACTIVE ? "suspend" : "activate") + 
            " this account?");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                showLoading(true);
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() {
                        userService.toggleUserStatus(currentOfficer.getId(), actor);
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> Platform.runLater(() -> {
                    loadOfficer(currentOfficer.getId()); // Reload UI
                    showLoading(false);
                }));
                task.setOnFailed(ev -> Platform.runLater(() -> {
                    showLoading(false);
                    new Alert(Alert.AlertType.ERROR, "Failed to update status: " + task.getException().getMessage()).show();
                }));
                new Thread(task).start();
            }
        });
    }

    @FXML
    private void handleResetPassword() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Enter new password for " + currentOfficer.getFullName());
        dialog.setContentText("New password:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            if (newPassword.trim().isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Password cannot be empty.").show();
                return;
            }
            // Confirm password
            TextInputDialog confirmDialog = new TextInputDialog();
            confirmDialog.setTitle("Confirm Password");
            confirmDialog.setHeaderText("Confirm new password");
            confirmDialog.setContentText("Re-enter password:");
            Optional<String> confirmResult = confirmDialog.showAndWait();
            if (confirmResult.isPresent() && confirmResult.get().equals(newPassword)) {
                showLoading(true);
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() {
                        userService.resetPassword(currentOfficer.getId(), newPassword);
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> Platform.runLater(() -> {
                    showLoading(false);
                    new Alert(Alert.AlertType.INFORMATION, "Password reset successful.").show();
                }));
                task.setOnFailed(ev -> Platform.runLater(() -> {
                    showLoading(false);
                    new Alert(Alert.AlertType.ERROR, "Password reset failed: " + task.getException().getMessage()).show();
                }));
                new Thread(task).start();
            } else {
                new Alert(Alert.AlertType.ERROR, "Passwords do not match.").show();
            }
        });
    }

    @FXML
    private void handleEditProfile() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/fxml/modules/OfficerEditDialog.fxml"));
            javafx.scene.Parent root = loader.load();
            
            OfficerEditController controller = loader.getController();
            controller.setOfficer(currentOfficer);
            
            javafx.stage.Stage dialog = new javafx.stage.Stage();
            dialog.setTitle("Edit Officer Profile - " + currentOfficer.getFullName());
            dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            dialog.initOwner(nameLabel.getScene().getWindow());
            dialog.setScene(new javafx.scene.Scene(root));
            dialog.setResizable(false);
            
            // Show dialog and wait
            dialog.showAndWait();
            
            // Reload if changes were saved
            if (controller.isSaved()) {
                loadOfficer(currentOfficer.getId());
                new Alert(Alert.AlertType.INFORMATION, "Profile updated successfully!").show();
            }
        } catch (Exception e) {
            logger.error("Failed to open edit dialog", e);
            new Alert(Alert.AlertType.ERROR, "Failed to open edit form: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void handleBack() {
        com.cms.service.NavigationService.getInstance().navigateTo("Officers Management", "/fxml/modules/UserAdmin.fxml", null);
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
    }
}
