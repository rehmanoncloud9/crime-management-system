package com.cms.controller;

import com.cms.model.AuditLog;
import com.cms.model.enums.CaseStatus;
import com.cms.service.DashboardService;
import com.cms.service.HibernateUtil;
import com.cms.service.NavigationService;
import com.cms.service.SessionManager;
import com.cms.model.enums.Role;
import com.cms.util.AnimationHelper;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardController {

    @FXML private Label criminalCount;
    @FXML private Label caseCount;
    @FXML private Label officerCount;
    @FXML private Label closedCases;
    @FXML private Label incidentCount;
    @FXML private Label userGreeting;
    @FXML private Label lastSyncLabel;
    @FXML private Label alertCountLabel;
    @FXML private VBox  recentActivityList;
    @FXML private VBox  alertsContainer;
    @FXML private VBox  caseBreakdownContainer;

    private final DashboardService dashboardService = new DashboardService();
    private static final DateTimeFormatter FMT     = DateTimeFormatter.ofPattern("dd MMM HH:mm");
    private static final DateTimeFormatter SYNC_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    public void initialize() {
        if (SessionManager.getInstance().getCurrentUser() != null) {
            userGreeting.setText("Welcome back, "
                + SessionManager.getInstance().getCurrentUser().getFullName());
            AnimationHelper.fadeInUp(userGreeting);
        }
        // Update sync label with current time
        if (lastSyncLabel != null) {
            lastSyncLabel.setText("Last Sync: " + LocalDateTime.now().format(SYNC_FMT));
        }
        loadStatistics();
        loadRecentActivity();
        loadAlerts();
        loadCaseBreakdown();
    }

    /* ── Statistics ── */
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
            long incidents = stats.getOrDefault("totalIncidents", 0L);

            AnimationHelper.animateCounter(criminalCount, (int)criminals, 900);
            AnimationHelper.animateCounter(caseCount,     (int)cases,     900);
            AnimationHelper.animateCounter(officerCount,  (int)officers,  900);
            AnimationHelper.animateCounter(closedCases,   (int)closed,    900);
            if (incidentCount != null)
                AnimationHelper.animateCounter(incidentCount, (int)incidents, 900);

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

    /* ── Recent Activity (real DB audit logs) ── */
    private void loadRecentActivity() {
        if (recentActivityList == null) return;
        Task<List<AuditLog>> task = new Task<>() {
            @Override protected List<AuditLog> call() {
                return HibernateUtil.executeTransaction(session ->
                    session.createQuery(
                            "FROM AuditLog a ORDER BY a.timestamp DESC", AuditLog.class)
                        .setMaxResults(10).list()
                );
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<AuditLog> logs = task.getValue();
            recentActivityList.getChildren().clear();
            if (logs == null || logs.isEmpty()) {
                Label empty = new Label("No recent activity recorded.");
                empty.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size:12px;");
                recentActivityList.getChildren().add(empty);
            } else {
                for (AuditLog log : logs) {
                    recentActivityList.getChildren().add(buildActivityRow(log));
                }
            }
        }));
        task.setOnFailed(e -> {});
        new Thread(task).start();
    }

    private HBox buildActivityRow(AuditLog log) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("activity-item");

        String color = switch (log.getAction()) {
            case CREATE -> "#00E5FF";
            case UPDATE -> "#F59E0B";
            case DELETE -> "#F43F5E";
            default     -> "#10B981";
        };

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10px;"
            + " -fx-effect: dropshadow(gaussian, " + color + ", 6, 0.5, 0, 0);");

        Label text = new Label(log.getDescription() != null
            ? log.getDescription() : log.getAction().name());
        text.getStyleClass().add("activity-text");
        text.setWrapText(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.SOMETIMES);

        String timeStr = log.getTimestamp() != null ? log.getTimestamp().format(FMT) : "";
        Label time = new Label(timeStr);
        time.setStyle("-fx-font-size:10px; -fx-text-fill: rgba(255,255,255,0.28);");

        row.getChildren().addAll(dot, text, spacer, time);
        return row;
    }

    /* ── Alerts (real DB queries) ── */
    private void loadAlerts() {
        if (alertsContainer == null) return;
        Task<List<String[]>> task = new Task<>() {
            @Override protected List<String[]> call() {
                List<String[]> alerts = new ArrayList<>();
                return HibernateUtil.executeTransaction(session -> {
                    // High priority: cases with HIGH priority that are OPEN
                    try {
                        Long highPriority = session.createQuery(
                            "SELECT COUNT(c) FROM CaseFile c WHERE c.priority = 'HIGH' " +
                            "AND c.status NOT IN ('CLOSED_CONVICTED','CLOSED_ACQUITTED','CLOSED_UNSOLVED')",
                            Long.class).getSingleResult();
                        if (highPriority > 0)
                            alerts.add(new String[]{"HIGH",
                                highPriority + " high-priority case(s) require immediate attention"});
                    } catch (Exception ignored) {}

                    // Warrants: active warrants pending execution
                    try {
                        Long pendingWarrants = session.createQuery(
                            "SELECT COUNT(w) FROM Warrant w WHERE w.status = 'PENDING'",
                            Long.class).getSingleResult();
                        if (pendingWarrants > 0)
                            alerts.add(new String[]{"MED",
                                pendingWarrants + " warrant(s) pending execution"});
                    } catch (Exception ignored) {}

                    // Evidence untagged (no case associated)
                    try {
                        Long untaggedEvidence = session.createQuery(
                            "SELECT COUNT(e) FROM Evidence e WHERE e.caseFile IS NULL",
                            Long.class).getSingleResult();
                        if (untaggedEvidence > 0)
                            alerts.add(new String[]{"MED",
                                untaggedEvidence + " evidence record(s) not linked to a case"});
                    } catch (Exception ignored) {}

                    // Court hearings today
                    try {
                        java.time.LocalDate today = java.time.LocalDate.now();
                        Long hearingsToday = session.createQuery(
                            "SELECT COUNT(h) FROM CourtHearing h " +
                            "WHERE FUNCTION('DATE', h.scheduledAt) = :today",
                            Long.class)
                            .setParameter("today", today)
                            .getSingleResult();
                        if (hearingsToday > 0)
                            alerts.add(new String[]{"INFO",
                                hearingsToday + " court hearing(s) scheduled today"});
                    } catch (Exception ignored) {}

                    return alerts;
                });
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            List<String[]> alerts = task.getValue();
            alertsContainer.getChildren().clear();
            if (alertCountLabel != null)
                alertCountLabel.setText(alerts.isEmpty() ? "No active alerts" : alerts.size() + " active");

            if (alerts.isEmpty()) {
                VBox noAlerts = new VBox(8);
                noAlerts.setStyle("-fx-background-color: rgba(16,185,129,0.07);" +
                    "-fx-border-color: rgba(16,185,129,0.2); -fx-border-width: 1;" +
                    "-fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 14;");
                Label ok = new Label("✔  All systems normal");
                ok.setStyle("-fx-text-fill: #10B981; -fx-font-weight: 700; -fx-font-size: 12px;");
                noAlerts.getChildren().add(ok);
                alertsContainer.getChildren().add(noAlerts);
            } else {
                for (String[] alert : alerts) {
                    alertsContainer.getChildren().add(buildAlertCard(alert[0], alert[1]));
                }
            }
        }));
        task.setOnFailed(e -> {});
        new Thread(task).start();
    }

    private VBox buildAlertCard(String severity, String message) {
        VBox card = new VBox(8);
        String icon, bgColor, borderColor, textColor;
        switch (severity) {
            case "HIGH" -> {
                icon = "🛑"; bgColor = "rgba(244,63,94,0.08)";
                borderColor = "rgba(244,63,94,0.3)"; textColor = "#F43F5E";
            }
            case "MED" -> {
                icon = "⚠"; bgColor = "rgba(245,158,11,0.08)";
                borderColor = "rgba(245,158,11,0.3)"; textColor = "#F59E0B";
            }
            default -> {
                icon = "ℹ"; bgColor = "rgba(0,229,255,0.06)";
                borderColor = "rgba(0,229,255,0.2)"; textColor = "#00E5FF";
            }
        }
        card.setStyle(String.format(
            "-fx-background-color: %s; -fx-border-color: %s;" +
            "-fx-border-width: 1; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 13;",
            bgColor, borderColor));

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 13px;");
        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill: " + textColor + "; -fx-font-weight: 700; -fx-font-size: 11px;");
        msg.setWrapText(true);
        HBox.setHgrow(msg, Priority.ALWAYS);
        header.getChildren().addAll(iconLabel, msg);
        card.getChildren().add(header);
        return card;
    }

    /* ── Case Breakdown (real DB) ── */
    private void loadCaseBreakdown() {
        if (caseBreakdownContainer == null) return;
        Task<Map<String, Long>> task = new Task<>() {
            @Override protected Map<String, Long> call() {
                return HibernateUtil.executeTransaction(session -> {
                    Map<String, Long> result = new java.util.LinkedHashMap<>();
                    try {
                        List<Object[]> rows = session.createQuery(
                            "SELECT c.status, COUNT(c) FROM CaseFile c GROUP BY c.status", Object[].class).list();
                        for (Object[] row : rows) {
                            result.put(row[0].toString(), (Long) row[1]);
                        }
                    } catch (Exception ignored) {}
                    return result;
                });
            }
        };
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            Map<String, Long> breakdown = task.getValue();
            caseBreakdownContainer.getChildren().clear();
            if (breakdown.isEmpty()) {
                Label empty = new Label("No case data available.");
                empty.setStyle("-fx-text-fill: rgba(255,255,255,0.3); -fx-font-size:11px;");
                caseBreakdownContainer.getChildren().add(empty);
                return;
            }
            long total = breakdown.values().stream().mapToLong(Long::longValue).sum();
            for (Map.Entry<String, Long> entry : breakdown.entrySet()) {
                caseBreakdownContainer.getChildren().add(
                    buildBreakdownRow(entry.getKey(), entry.getValue(), total));
            }
        }));
        task.setOnFailed(e -> {});
        new Thread(task).start();
    }

    private HBox buildBreakdownRow(String status, long count, long total) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        // Friendly label
        String label = status.replace("_", " ").replace("CLOSED", "Closed").replace("OPEN", "Open")
            .replace("UNDER INVESTIGATION", "Investigating").replace("PENDING", "Pending");
        label = label.substring(0, 1).toUpperCase() + label.substring(1).toLowerCase();

        String color;
        if (status.startsWith("CLOSED")) color = "#8B5CF6";
        else if (status.contains("INVESTIGATION")) color = "#00E5FF";
        else if (status.equals("PENDING")) color = "#F59E0B";
        else color = "#10B981";

        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10px;");

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 12px;");
        HBox.setHgrow(lbl, Priority.ALWAYS);

        double pct = total > 0 ? (count * 100.0 / total) : 0;
        Label cnt = new Label(count + " (" + String.format("%.0f", pct) + "%)");
        cnt.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; -fx-font-weight: 700;");

        row.getChildren().addAll(dot, lbl, cnt);
        return row;
    }

    /* ── Navigation ── */
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
    @FXML private void handleViewAlert() {}
    @FXML private void handleResolveAlert() {}
}
