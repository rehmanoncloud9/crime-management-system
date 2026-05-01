package com.cms.controller;

import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.model.enums.UserStatus;
import com.cms.service.ImageStorageService;
import com.cms.service.UserService;
import com.cms.service.AuthService;
import com.cms.service.NavigationService;
import com.cms.service.SessionManager;
import com.cms.service.CaseService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class UserAdminController {
    private static final Logger logger = LoggerFactory.getLogger(UserAdminController.class);

    @FXML private TextField searchField;
    @FXML private FlowPane userFlowPane;

    @FXML private VBox formPanel;
    @FXML private ImageView newUserPhotoView;
    @FXML private TextField newBadgeField;
    @FXML private TextField newNameField;
    @FXML private ComboBox<Role> newRoleCombo;
    @FXML private TextField newPrecinctField;
    @FXML private TextField newEmailField;
    @FXML private TextField newPhoneField;

    private File selectedPhotoFile; // local file reference instead of byte[]

    private final UserService userService = new UserService();
    private final AuthService authService = new AuthService();
    private final CaseService caseService = new CaseService();
    private final ImageStorageService imageService = ImageStorageService.getInstance();

    @FXML
    public void initialize() {
        loadUsers();
        setupSearch();
        newRoleCombo.setItems(FXCollections.observableArrayList(Role.values()));
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadUsers());
    }

    private void loadUsers() {
        String keyword = searchField.getText();
        javafx.concurrent.Task<List<User>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<User> call() {
                return userService.searchUsers(keyword);
            }
        };

        task.setOnSucceeded(e -> {
            List<User> users = task.getValue();
            userFlowPane.getChildren().clear();
            if (users.isEmpty()) {
                Label placeholder = new Label("No officers found matching \"" + (keyword != null ? keyword : "") + "\"");
                placeholder.getStyleClass().add("text-muted");
                userFlowPane.getChildren().add(placeholder);
            } else {
                for (User u : users) {
                    userFlowPane.getChildren().add(buildUserCard(u));
                }
            }
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load users", task.getException());
            System.err.println(">>> [CMS-ERROR] Failed to load users!");
            task.getException().printStackTrace();
            userFlowPane.getChildren().clear();
            userFlowPane.getChildren().add(new Label("Error loading data. Check database connection. See logs for details."));
        });

        new Thread(task, "user-loader").start();
    }

    // ==================== CREATE NEW USER ====================

    @FXML
    private void handleCreateUser() {
        formPanel.setVisible(true);
        formPanel.setManaged(true);
    }

    @FXML
    private void handleCancelNewUser() {
        formPanel.setVisible(false);
        formPanel.setManaged(false);
        clearNewUserForm();
    }

    @FXML
    private void handleUploadNewPhoto() {
        File file = chooseImageFile();
        if (file != null) {
            selectedPhotoFile = file;
            newUserPhotoView.setImage(new Image(file.toURI().toString(), 80, 80, true, true));
        }
    }

    @FXML
    private void handleSaveNewUser() {
        if (!validateNewUserInputs()) return;

        User newUser = new User();
        newUser.setBadgeNumber(newBadgeField.getText().trim());
        newUser.setFullName(newNameField.getText().trim());
        newUser.setUsername(newBadgeField.getText().trim().toLowerCase());
        newUser.setRole(newRoleCombo.getValue());
        newUser.setPrecinct(newPrecinctField.getText().trim());
        newUser.setEmail(newEmailField.getText().trim());
        newUser.setPhone(newPhoneField.getText().trim());
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setMustChangePassword(true);
        newUser.setPasswordHash(authService.hashPassword(newUser.getBadgeNumber() + "123!"));

        // Save image to local storage
        if (selectedPhotoFile != null) {
            String savedPath = imageService.saveImage(selectedPhotoFile, "officers");
            newUser.setProfilePhotoPath(savedPath);
        }

        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                userService.saveUser(newUser);
                return null;
            }
        };
        task.setOnSucceeded(event -> {
            loadUsers();
            handleCancelNewUser();
            new Alert(Alert.AlertType.INFORMATION, "User created successfully.\nUsername: " + newUser.getUsername() + "\nDefault Password: " + newUser.getBadgeNumber() + "123!").showAndWait();
        });
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            logger.error("Failed to create user", ex);
            String msg = ex.getMessage() != null && (ex.getMessage().contains("Constraint") || (ex.getCause() != null && ex.getCause().getMessage().contains("Constraint")))
                ? "Duplicate Data: Badge Number or Email already exists."
                : "Failed to create user: " + ex.getMessage();
            new Alert(Alert.AlertType.ERROR, msg).showAndWait();
        });
        new Thread(task).start();
    }

    private boolean validateNewUserInputs() {
        String badge = newBadgeField.getText().trim();
        String name = newNameField.getText().trim();
        if (badge.isEmpty() || name.isEmpty() || !name.matches("^[a-zA-Z\\s]+$")) {
            new Alert(Alert.AlertType.WARNING, "Badge and Name are required. Name can only contain letters.").showAndWait();
            return false;
        }
        if (newRoleCombo.getValue() == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a Role.").showAndWait();
            return false;
        }
        String email = newEmailField.getText().trim();
        if (email.isEmpty() || !email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$")) {
            new Alert(Alert.AlertType.WARNING, "A valid Email is required.").showAndWait();
            return false;
        }
        return true;
    }

    private void clearNewUserForm() {
        newBadgeField.clear();
        newNameField.clear();
        newEmailField.clear();
        newPhoneField.clear();
        newPrecinctField.clear();
        newRoleCombo.setValue(null);
        selectedPhotoFile = null;
        newUserPhotoView.setImage(null);
    }

    // ==================== OFFICER CARD ====================

    private Pane buildUserCard(User user) {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/modules/OfficerCard.fxml"));
            Pane card = loader.load();

            // Get elements from FXML (they don't have a dedicated controller for the card itself to keep it simple, 
            // or we could create one. For now, lookup by fx:id is fine or we can use the root's lookup)
            ImageView photoView = (ImageView) card.lookup("#photoView");
            Label statusBadge = (Label) card.lookup("#statusBadge");
            Label nameLabel = (Label) card.lookup("#nameLabel");
            Label badgeLabel = (Label) card.lookup("#badgeLabel");
            Label rankLabel = (Label) card.lookup("#rankLabel");
            Label precinctLabel = (Label) card.lookup("#precinctLabel");
            Label contactLabel = (Label) card.lookup("#contactLabel");
            Button viewBtn = (Button) card.lookup("#viewBtn");
            Button editBtn = (Button) card.lookup("#editBtn");
            Button statusBtn = (Button) card.lookup("#statusBtn");
            Label casesLabel = (Label) card.lookup("#casesLabel");

            // Populate data
            Image img = ImageStorageService.loadImage(user.getProfilePhotoPath());
            if (img != null) photoView.setImage(img);

            nameLabel.setText(user.getFullName());
            badgeLabel.setText("BADGE #" + (user.getBadgeNumber() != null ? user.getBadgeNumber() : "N/A"));
            rankLabel.setText(user.getRole() != null ? user.getRole().name() : "N/A");
            precinctLabel.setText(user.getPrecinct() != null ? user.getPrecinct() : "N/A");
            contactLabel.setText(user.getPhone() != null ? user.getPhone() : "N/A");

            if (casesLabel != null) {
                long count = caseService.countCasesByInvestigator(user.getId());
                casesLabel.setText(String.valueOf(count));
            }

            if (user.getStatus() == UserStatus.ACTIVE) {
                statusBadge.setText("ACTIVE");
                statusBadge.getStyleClass().setAll("status-badge", "status-active");
            } else {
                statusBadge.setText("INACTIVE");
                statusBadge.getStyleClass().setAll("status-badge", "status-inactive");
            }

            // Events
            viewBtn.setOnAction(ev -> showOfficerDetail(user));
            editBtn.setOnAction(ev -> showOfficerEdit(user));
            statusBtn.setOnAction(ev -> handleToggleStatus(user));
            
            // Set status button style
            if (user.getStatus() != UserStatus.ACTIVE) {
                statusBtn.setText("Activate");
                statusBtn.getStyleClass().add("btn-success-sm");
            } else {
                statusBtn.setText("Suspend");
                statusBtn.getStyleClass().add("btn-danger-sm");
            }

            return card;
        } catch (Exception e) {
            logger.error("Error creating officer card for {}", user.getFullName(), e);
            return new VBox(new Label("Error loading card"));
        }
    }

    private void showOfficerDetail(User user) {
        NavigationService.getInstance().navigateTo("Officer: " + user.getBadgeNumber(), "/fxml/modules/OfficerDetailView.fxml", (controller) -> {
            if (controller instanceof OfficerDetailController detailCtrl) {
                detailCtrl.loadOfficer(user.getId());
            }
        });
    }

    private void showOfficerEdit(User user) {
        // We can reuse the Detail view but in edit mode, or a separate view.
        // For now, let's open the Detail view as it has an "Edit Profile" button too.
        showOfficerDetail(user);
    }

    private void handleToggleStatus(User user) {
        User actor = SessionManager.getInstance().getCurrentUser();
        if (actor == null) return;

        userService.toggleUserStatus(user.getId(), actor);
        loadUsers(); // Refresh list
    }

    // ==================== UTILITIES ====================

    private File chooseImageFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(userFlowPane.getScene().getWindow());
        if (file != null && file.length() > 2 * 1024 * 1024) {
            new Alert(Alert.AlertType.WARNING, "Image size must be less than 2MB.").showAndWait();
            return null;
        }
        return file;
    }
}
