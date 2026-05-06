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
import com.cms.service.HibernateUtil;
import com.cms.repository.UserRepository;
import com.cms.util.NexusAlert;
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
    @FXML private TextField newUsernameField;
    @FXML private PasswordField newPasswordField;

    private File selectedPhotoFile; // local file reference instead of byte[]

    private final UserService userService = new UserService();
    private final AuthService authService = new AuthService();
    private final CaseService caseService = new CaseService();
    private final ImageStorageService imageService = ImageStorageService.getInstance();

    @FXML
    public void initialize() {
        loadUsers();
        setupSearch();
        setupFormListeners();
        newRoleCombo.setItems(FXCollections.observableArrayList(Role.values()));
    }

    private void setupFormListeners() {
        newBadgeField.textProperty().addListener((obs, old, newVal) -> {
            if (newUsernameField.getText().isEmpty() || newUsernameField.getText().equals(old != null ? old.toLowerCase() : "")) {
                newUsernameField.setText(newVal.toLowerCase());
            }
        });
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
            logger.error("Failed to load users", task.getException());
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

        final String badge = newBadgeField.getText().trim();
        final String username = newUsernameField.getText().trim().toLowerCase();
        final String email = newEmailField.getText().trim();

        // Check for duplicates before attempting save to provide better error messages
        try {
            User existing = HibernateUtil.executeTransaction(session -> {
                UserRepository repo = new UserRepository(session);
                return repo.findByUsername(username)
                    .or(() -> repo.findByBadgeNumber(badge))
                    .or(() -> repo.findByEmail(email))
                    .orElse(null);
            });

            if (existing != null) {
                String conflict = "Username";
                if (existing.getBadgeNumber().equalsIgnoreCase(badge)) conflict = "Badge ID";
                else if (existing.getEmail() != null && existing.getEmail().equalsIgnoreCase(email)) conflict = "Email Address";

                NexusAlert.showError("ACCOUNT CLASH: An officer with this " + conflict + " already exists.\n\n" +
                        "Please verify the credentials or search for the existing user.");
                return;
            }
        } catch (Exception e) {
            logger.warn("Pre-save check failed: {}", e.getMessage());
        }

        User newUser = new User();
        newUser.setBadgeNumber(badge);
        newUser.setUsername(username);
        newUser.setRole(newRoleCombo.getValue());
        newUser.setPrecinct(newPrecinctField.getText().trim());
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setMustChangePassword(true);
        newUser.setDateOfJoining(java.time.LocalDate.now());

        String fullName = newNameField.getText().trim();
        
        // REUSE PERSON if email exists, otherwise create new
        com.cms.model.Person person = HibernateUtil.executeTransaction(session -> {
            return session.createQuery("from Person where email = :email", com.cms.model.Person.class)
                    .setParameter("email", email)
                    .uniqueResult();
        });

        if (person == null) {
            person = new com.cms.model.Person();
            String[] parts = fullName.trim().split("\\s+", 2);
            person.setFirstName(parts[0]);
            // Ensure lastName is never blank to satisfy @NotBlank constraint
            person.setLastName(parts.length > 1 ? parts[1] : "Officer"); 
            person.setEmail(email);
            String phone = newPhoneField.getText().trim();
            person.setPhone(phone.isEmpty() ? null : phone);
            person.setPersonStatus(com.cms.model.enums.PersonStatus.OFFICER);
        } else {
            // Update the existing person to be an officer if they were something else
            person.setPersonStatus(com.cms.model.enums.PersonStatus.OFFICER);
        }

        newUser.setPerson(person); 

        String tempPassword = newPasswordField.getText().isEmpty() ? 
            newUser.getBadgeNumber() + String.format("%04d", (int)(Math.random() * 10000)) : 
            newPasswordField.getText();
        newUser.setPasswordHash(authService.hashPassword(tempPassword));

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
            NexusAlert.showInfo(
                "User created successfully.\n\n" +
                "Username : " + newUser.getUsername() + "\n" +
                "Temp Password: " + tempPassword + "\n\n" +
                "The user will be required to change their\n" +
                "password on first login.");
        });
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            logger.error("Failed to create user", ex);
            
            String errMsg = ex.getMessage();
            if (errMsg == null) errMsg = "Unknown database error";

            // Better error categorization
            if (errMsg.contains("Constraint") || (ex.getCause() != null && ex.getCause().getMessage().contains("Constraint"))) {
                if (errMsg.contains("badge_number") || errMsg.contains("badge")) {
                    errMsg = "BADGE CLASH: An officer with Badge ID '" + badge + "' already exists.";
                } else if (errMsg.contains("username")) {
                    errMsg = "USERNAME CLASH: The username '" + username + "' is already taken.";
                } else if (errMsg.contains("email")) {
                    errMsg = "EMAIL CLASH: A person with the email '" + email + "' is already registered in the system.";
                } else {
                    errMsg = "ACCOUNT CLASH: A duplicate record was detected for this Badge ID or Email.\n\n" +
                             "Please verify the credentials or search for existing records.";
                }
            } else {
                errMsg = "System Error: " + errMsg;
            }
            
            NexusAlert.showError(errMsg);
        });
        new Thread(task).start();
    }

    private boolean validateNewUserInputs() {
        String badge = newBadgeField.getText().trim();
        String name = newNameField.getText().trim();
        if (badge.isEmpty() || name.isEmpty() || !name.matches("^[a-zA-Z\\s]+$")) {
            NexusAlert.showWarning("Badge and Name are required. Name can only contain letters.");
            return false;
        }
        if (newRoleCombo.getValue() == null) {
            NexusAlert.showWarning("Please select a Role.");
            return false;
        }
        String email = newEmailField.getText().trim();
        if (email.isEmpty() || !email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$")) {
            NexusAlert.showWarning("A valid Email is required.");
            return false;
        }
        return true;
    }

    private void clearNewUserForm() {
        newBadgeField.clear();
        newNameField.clear();
        newEmailField.clear();
        newPrecinctField.clear();
        newUsernameField.clear();
        newPasswordField.clear();
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

            Button deleteBtn = (Button) card.lookup("#deleteBtn");
            if (deleteBtn != null) {
                deleteBtn.setOnAction(ev -> handleDeleteOfficer(user));
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

    private void handleDeleteOfficer(User user) {
        boolean confirm = NexusAlert.confirm("Confirm Deletion", 
            "Are you sure you want to PERMANENTLY delete officer " + user.getBadgeNumber() + "?\nThis will also remove their linked Person profile.");
        
        if (confirm) {
            User actor = SessionManager.getInstance().getCurrentUser();
            userService.deleteUser(user.getId(), actor);
            loadUsers();
        }
    }

    private File chooseImageFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Profile Photo");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(userFlowPane.getScene().getWindow());
        if (file != null && file.length() > 2 * 1024 * 1024) {
            NexusAlert.showWarning("Image size must be less than 2MB.");
            return null;
        }
        return file;
    }
}
