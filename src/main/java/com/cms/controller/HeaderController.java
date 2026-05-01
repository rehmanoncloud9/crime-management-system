package com.cms.controller;

import com.cms.model.User;
import com.cms.service.SessionManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cms.service.NavigationService;
import com.cms.util.AnimationHelper;
import javafx.scene.layout.FlowPane;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HeaderController implements NavigationService.NavigationObserver {
    private static final Logger logger = LoggerFactory.getLogger(HeaderController.class);

    @FXML private Circle    profileCircle;
    @FXML private Label     initialsLabel;
    @FXML private Label     userNameLabel;
    @FXML private Label     officerRankLabel;
    @FXML private Label     roleBadge;
    @FXML private Label     precinctLabel;
    @FXML private Label     dateLabel;
    @FXML private Label     timeLabel;
    @FXML private Button    notificationBtn;
    @FXML private Button    backBtn;
    @FXML private Button    menuToggleBtn;   // ← hamburger in header bar
    @FXML private FlowPane  breadcrumbPane;

    private Popup notificationPopup;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            userNameLabel.setText(user.getFullName());
            String rank = (user.getOfficerRank() != null ? user.getOfficerRank() : "") +
                          " #" + (user.getBadgeNumber() != null ? user.getBadgeNumber() : "");
            officerRankLabel.setText(rank.trim());
            roleBadge.setText(user.getRole().name());
            precinctLabel.setText(user.getPrecinct() != null ? user.getPrecinct() : "");

            if (user.getProfilePhoto() != null && user.getProfilePhoto().length > 0) {
                try {
                    profileCircle.setFill(new ImagePattern(
                        new Image(new ByteArrayInputStream(user.getProfilePhoto()))));
                    initialsLabel.setVisible(false);
                } catch (Exception e) {
                    initialsLabel.setText(getInitials(user.getFullName()));
                }
            } else {
                initialsLabel.setText(getInitials(user.getFullName()));
            }

            roleBadge.getStyleClass().removeAll("role-badge-admin","role-badge-detective",
                                                 "role-badge-officer","role-badge-default");
            switch (user.getRole()) {
                case ADMINISTRATOR, SUPERVISOR -> roleBadge.getStyleClass().add("role-badge-admin");
                case OFFICER, ANALYST         -> roleBadge.getStyleClass().add("role-badge-officer");
                default                       -> roleBadge.getStyleClass().add("role-badge-default");
            }
        }

        // Live clock
        Timeline clock = new Timeline(
            new KeyFrame(Duration.ZERO, e -> {
                LocalDateTime now = LocalDateTime.now();
                dateLabel.setText(now.format(dateFormatter));
                timeLabel.setText(now.format(timeFormatter));
            }),
            new KeyFrame(Duration.seconds(1))
        );
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();

        if (notificationBtn != null) AnimationHelper.addHoverLift(notificationBtn);
        if (menuToggleBtn   != null) AnimationHelper.addHoverLift(menuToggleBtn);

        NavigationService.getInstance().addObserver(this);
        updateNavigationUI();
    }

    /** Called by the hamburger button in the header — delegates to MainController */
    @FXML
    private void handleMenuToggle(ActionEvent event) {
        MainController mc = MainController.getInstance();
        if (mc != null) mc.handleToggleSidebar();
    }

    @Override
    public void onNavigationChanged(NavigationService.NavigationState currentState) {
        updateNavigationUI();
    }

    private void updateNavigationUI() {
        NavigationService nav = NavigationService.getInstance();
        boolean canGoBack = nav.canGoBack();
        backBtn.setVisible(canGoBack);
        backBtn.setManaged(canGoBack);

        breadcrumbPane.getChildren().clear();
        List<String> crumbs = nav.getBreadcrumbs();
        for (int i = 0; i < crumbs.size(); i++) {
            Label leaf = new Label(crumbs.get(i).toUpperCase());
            leaf.getStyleClass().add("breadcrumb-item");
            if (i == crumbs.size() - 1) leaf.getStyleClass().add("breadcrumb-active");
            breadcrumbPane.getChildren().add(leaf);
            if (i < crumbs.size() - 1) {
                Label sep = new Label(" / ");
                sep.getStyleClass().add("breadcrumb-separator");
                breadcrumbPane.getChildren().add(sep);
            }
        }
    }

    @FXML private void handleBack() { NavigationService.getInstance().goBack(); }

    private String getInitials(String fullName) {
        if (fullName == null || fullName.isEmpty()) return "?";
        String[] parts = fullName.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(parts.length, 2); i++)
            if (!parts[i].isEmpty()) sb.append(parts[i].charAt(0));
        return sb.toString().toUpperCase();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        SessionManager.getInstance().logout();
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            Stage stage = (Stage) userNameLabel.getScene().getWindow();
            Scene scene = new Scene(root, 900, 600);
            scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());
            stage.setScene(scene);
            stage.setTitle("Crime Management System v5.0");
            stage.setResizable(false);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to navigate to login", e);
        }
    }

    private void setupNotificationPopup() {
        if (notificationPopup == null) {
            try {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/modules/NotificationPopover.fxml"));
                Parent root = loader.load();
                NotificationPopoverController ctrl = loader.getController();
                notificationPopup = new Popup();
                notificationPopup.setAutoHide(true);
                notificationPopup.getContent().add(root);
                ctrl.setPopup(notificationPopup);
            } catch (IOException e) {
                logger.error("Failed to load notification popover", e);
            }
        }
    }

    @FXML
    private void handleNotificationClick(ActionEvent event) {
        if (notificationPopup == null) setupNotificationPopup();
        if (notificationPopup != null) {
            if (!notificationPopup.isShowing()) {
                var bounds = notificationBtn.localToScreen(notificationBtn.getBoundsInLocal());
                if (bounds != null)
                    notificationPopup.show(notificationBtn, bounds.getMinX() - 310, bounds.getMaxY() + 5);
            } else {
                notificationPopup.hide();
            }
        }
    }
}
