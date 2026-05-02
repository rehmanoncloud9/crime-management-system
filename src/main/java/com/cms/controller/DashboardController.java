package com.cms.controller;

import com.cms.model.AuditLog;
import com.cms.service.DashboardService;
import com.cms.service.HibernateUtil;
import com.cms.service.NavigationService;
import com.cms.service.SessionManager;
import com.cms.model.enums.Role;
import com.cms.util.AnimationHelper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardController {

    @FXML private Label criminalCount;
    @FXML private Label caseCount;
    @FXML private Label officerCount;
    @FXML private Label closedCases;
    @FXML private Label userGreeting;
    @FXML private VBox  recentActivityList;

    private final DashboardService dashboardService = new DashboardService();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    @FXML
    public void initialize() {
        if (SessionManager.getInstance().getCurrentUser() != null) {
            userGreeting.setText("Welcome back, "
                + SessionManager.getInstance().getCurrentUser().getFullName());
            AnimationHelper.fadeInUp(userGreeting);
        }
        loadStatistics();
        loadRecentActivity();
    }

    private void loadStatistics() {
        Task<Map<String,Long>> task = new Task<>() {
            @Override protected Map<String,Long> call() {
                return dashboardService.getStatistics();
            }
        };
        task.setOnSucceeded(e -> {
            Map<String,Long> stats = task.getValue();
            long criminals = stats.getOrDefault("totalCriminals", 0L);
            long cases     = stats.getOrDefault("activeCases",    0L);
            long officers  = stats.getOrDefault("totalOfficers",  0L);
            long closed    = stats.getOrDefault("closedCases",    0L);

            AnimationHelper.animateCounter(criminalCount, (int)criminals, 900);
            AnimationHelper.animateCounter(caseCount,     (int)cases,     900);
            AnimationHelper.animateCounter(officerCount,  (int)officers,  900);
            AnimationHelper.animateCounter(closedCases,   (int)closed,    900);

            List<Node> statCards = new ArrayList<>();
            addParent(statCards, criminalCount);
            addParent(statCards, caseCount);
            addParent(statCards, officerCount);
            addParent(statCards, closedCases);
            if (!statCards.isEmpty()) AnimationHelper.cascadeCards(statCards);
        });
        new Thread(task).start();
    }

    private void addParent(List<Node> list, Node n) {
        if (n != null && n.getParent() != null && n.getParent().getParent() != null)
            list.add(n.getParent().getParent());
    }

    private void loadRecentActivity() {
        if (recentActivityList == null) return;

        Task<List<AuditLog>> task = new Task<>() {
            @Override protected List<AuditLog> call() {
                return HibernateUtil.executeTransaction(session ->
                    session.createQuery(
                            "FROM AuditLog a ORDER BY a.timestamp DESC", AuditLog.class)
                        .setMaxResults(8).list()
                );
            }
        };
        task.setOnSucceeded(e -> {
            List<AuditLog> logs = task.getValue();
            Platform.runLater(() -> {
                recentActivityList.getChildren().clear();
                if (logs.isEmpty()) {
                    Label empty = new Label("No recent activity yet.");
                    empty.setStyle("-fx-text-fill: rgba(255,255,255,0.4); -fx-font-size:12px;");
                    recentActivityList.getChildren().add(empty);
                } else {
                    for (AuditLog log : logs) {
                        HBox row = buildActivityRow(log);
                        recentActivityList.getChildren().add(row);
                    }
                }
            });
        });
        task.setOnFailed(e -> {
            // Silently ignore if audit table is empty
        });
        new Thread(task).start();
    }

    private HBox buildActivityRow(AuditLog log) {
        HBox row = new HBox(10);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("activity-item");

        String color = switch (log.getAction()) {
            case CREATE -> "#00D4FF";
            case UPDATE -> "#FFA502";
            case DELETE -> "#FF4757";
            default     -> "#2ECC71";
        };

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + color + ";");

        Label text = new Label(log.getDescription() != null ? log.getDescription() : log.getAction().name());
        text.getStyleClass().add("activity-text");
        text.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        String timeStr = log.getTimestamp() != null
                ? log.getTimestamp().format(FMT) : "";
        Label time = new Label(timeStr);
        time.getStyleClass().add("text-muted");
        time.setStyle("-fx-font-size:10px;");

        row.getChildren().addAll(dot, text, spacer, time);
        return row;
    }

    @FXML private void handleAddCriminal() {
        NavigationService.getInstance().navigateTo("Add Person", "/fxml/modules/PersonRegistration.fxml");
    }
    @FXML private void handleAddCase() {
        NavigationService.getInstance().navigateTo("Register Incident", "/fxml/modules/IncidentRegistration.fxml");
    }
    @FXML private void handleAddOfficer() {
        if (SessionManager.getInstance().getCurrentUser().getRole() == Role.ADMINISTRATOR)
            NavigationService.getInstance().navigateTo("Officer Management", "/fxml/modules/UserAdmin.fxml");
    }

    @FXML private void handleReports() {
        NavigationService.getInstance().navigateTo("Statistical Reports", "/fxml/modules/StatisticalReports.fxml");
    }

    @FXML private void handleSettings() {
        if (SessionManager.getInstance().getCurrentUser().getRole() == Role.ADMINISTRATOR)
            NavigationService.getInstance().navigateTo("System Config", "/fxml/modules/Config.fxml");
    }

    @FXML private void handleAudit() {
        if (SessionManager.getInstance().getCurrentUser().getRole() == Role.ADMINISTRATOR)
            NavigationService.getInstance().navigateTo("Audit Logs", "/fxml/modules/AuditDashboard.fxml");
    }

    @FXML private void handleViewAlert() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION, "Opening full alert details...");
        alert.setHeaderText("Alert System");
        alert.show();
    }

    @FXML private void handleResolveAlert() {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.CONFIRMATION, "Mark this alert as resolved?");
        alert.setHeaderText("Resolve Alert");
        alert.show();
    }
}
