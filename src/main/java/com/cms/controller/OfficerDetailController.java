package com.cms.controller;

import com.cms.model.User;
import com.cms.model.CaseFile;
import com.cms.model.enums.UserStatus;
import com.cms.model.enums.Role;
import com.cms.model.enums.IncidentStatus;
import com.cms.service.UserService;
import com.cms.service.HibernateUtil;
import com.cms.service.ImageStorageService;
import com.cms.service.SessionManager;
import com.cms.util.NexusAlert;
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
    @FXML private Label headerNameLabel;
    @FXML private Label perfEfficiencyHeader;
    @FXML private Label perfTotalCases;
    @FXML private Label perfClosedCases;
    @FXML private Label perfActiveCases;
    @FXML private Label perfEfficiency;
    
    @FXML private ProgressBar perfClosedProgress;
    @FXML private ProgressBar perfActiveProgress;
    @FXML private ProgressBar perfEfficiencyProgress;

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
                NexusAlert.showError("Failed to load officer: " + task.getException().getMessage());
            });
        });

        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    private void updateUI() {
        nameLabel.setText(currentOfficer.getFullName());
        headerNameLabel.setText(currentOfficer.getFullName());
        badgeLabel.setText("BADGE #" + (currentOfficer.getBadgeNumber() != null ? currentOfficer.getBadgeNumber() : "N/A"));
        
        roleBadge.setText(currentOfficer.getRole() != null ? currentOfficer.getRole().name() : "N/A");
        UserStatus status = currentOfficer.getStatus();
        statusBadge.setText(status != null ? status.name() : "UNKNOWN");
        
        boolean isActive = status == UserStatus.ACTIVE;
        statusCircle.setFill(isActive ? Color.web("#16a34a") : Color.web("#e11d48"));
        statusBadge.getStyleClass().setAll("badge-premium", isActive ? "badge-success" : "badge-danger");
        statusBtn.setText(isActive ? "Suspend Account" : "Activate Account");
        statusBtn.getStyleClass().setAll("btn", isActive ? "btn-outline-danger-sm" : "btn-outline-success-sm");

        rankLabel.setText(currentOfficer.getOfficerRank() != null ? currentOfficer.getOfficerRank() : "Not Specified");
        deptLabel.setText(currentOfficer.getDepartment() != null ? currentOfficer.getDepartment() : "General Duty");
        precinctLabel.setText(currentOfficer.getPrecinct() != null ? currentOfficer.getPrecinct() : "Unassigned");
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

        // RBAC: Only Admin can modify officer status or edit profiles
        User actor = SessionManager.getInstance().getCurrentUser();
        boolean isAdmin = actor != null && actor.getRole() == Role.ADMINISTRATOR;
        
        statusBtn.setVisible(isAdmin);
        statusBtn.setManaged(isAdmin);
        
        // Note: The Edit button is likely in the FXML. Let's find it.
        // Looking at the controller methods, we have handleEditProfile and handleResetPassword.
        // We should disable those actions at the button level if possible, 
        // or just let the method check handle it.
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
                    logger.error("Operation failed", ex);
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
                NexusAlert.showError("Failed to load assigned cases: " + task.getException().getMessage());
            });
        });

        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    private void updatePerformanceStats(List<CaseFile> cases) {
        int total = cases.size();
        long closed = cases.stream().filter(c -> 
            c.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_CONVICTED || 
            c.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_ACQUITTED || 
            c.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED).count();
        long active = total - closed;
        double efficiency = total > 0 ? (double) closed / total : 0;

        caseCountBadge.setText(String.valueOf(total));
        perfTotalCases.setText(String.valueOf(total));
        perfClosedCases.setText(String.valueOf(closed));
        perfActiveCases.setText(String.valueOf(active));
        
        String effText = String.format("%.1f%%", efficiency * 100);
        perfEfficiency.setText(effText);
        perfEfficiencyHeader.setText(effText);

        // Update progress bars
        perfClosedProgress.setProgress(total > 0 ? (double) closed / total : 0);
        perfActiveProgress.setProgress(total > 0 ? (double) active / total : 0);
        perfEfficiencyProgress.setProgress(efficiency);
    }

    @FXML
    private void handleToggleStatus() {
        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null || actor.getRole() != Role.ADMINISTRATOR) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Administrators can modify officer account status.");
            return;
        }

        boolean confirm = NexusAlert.confirm("Account Status Change", 
            "Are you sure you want to " + (currentOfficer.getStatus() == UserStatus.ACTIVE ? "suspend" : "activate") + 
            " this account?");
        
        if (confirm) {
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
                NexusAlert.showError("Failed to update status: " + task.getException().getMessage());
            }));
            Thread th = new Thread(task); th.setDaemon(true); th.start();
        }
    }

    @FXML
    private void handleResetPassword() {
        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null || actor.getRole() != Role.ADMINISTRATOR) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Administrators can reset officer passwords.");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Enter new password for " + currentOfficer.getFullName());
        dialog.setContentText("New password:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newPassword -> {
            if (newPassword.trim().isEmpty()) {
                NexusAlert.showWarning("Password cannot be empty.");
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
                    NexusAlert.showInfo("Password reset successful.");
                }));
                task.setOnFailed(ev -> Platform.runLater(() -> {
                    showLoading(false);
                    NexusAlert.showError("Password reset failed: " + task.getException().getMessage());
                }));
                Thread th = new Thread(task); th.setDaemon(true); th.start();
            } else {
                NexusAlert.showError("Passwords do not match.");
            }
        });
    }

    @FXML
    private void handleEditProfile() {
        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null || actor.getRole() != Role.ADMINISTRATOR) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Administrators can modify officer profile details.");
            return;
        }

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
            dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            
            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialog.setScene(scene);
            dialog.setResizable(false);
            
            // Allow dragging from the header
            final double[] xOffset = new double[1];
            final double[] yOffset = new double[1];
            root.setOnMousePressed(event -> {
                xOffset[0] = event.getSceneX();
                yOffset[0] = event.getSceneY();
            });
            root.setOnMouseDragged(event -> {
                dialog.setX(event.getScreenX() - xOffset[0]);
                dialog.setY(event.getScreenY() - yOffset[0]);
            });

            // Show dialog and wait
            dialog.showAndWait();
            
            // Reload if changes were saved
            if (controller.isSaved()) {
                loadOfficer(currentOfficer.getId());
                NexusAlert.showInfo("Profile updated successfully!");
            }
        } catch (Exception e) {
            logger.error("Failed to open edit dialog", e);
            NexusAlert.showError("Failed to open edit form: " + e.getMessage());
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
