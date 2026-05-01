package com.cms.controller;

import com.cms.service.AIService;
import com.cms.service.DashboardService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class AdminDashboardController {
    private static final Logger logger = LoggerFactory.getLogger(AdminDashboardController.class);

    @FXML private Label totalCasesLabel;
    @FXML private Label activeCasesLabel;
    @FXML private Label closedCasesLabel;
    @FXML private Label totalOfficersLabel;
    @FXML private Label totalCriminalsLabel;
    @FXML private Label incidentsSummaryLabel;
    @FXML private Label aiAlertsLabel;

    private final DashboardService dashboardService = new DashboardService();
    private final AIService aiService = new AIService();

    @FXML
    public void initialize() {
        loadStatsAsync();
        loadAIAlertsAsync();
    }

    private void loadStatsAsync() {
        Task<Map<String, Long>> task = new Task<>() {
            @Override
            protected Map<String, Long> call() {
                return dashboardService.getStatistics();
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, Long> stats = task.getValue();
            totalCasesLabel.setText(String.valueOf(stats.getOrDefault("totalCases", 0L)));
            activeCasesLabel.setText(String.valueOf(stats.getOrDefault("activeCases", 0L)));
            closedCasesLabel.setText(String.valueOf(stats.getOrDefault("closedCases", 0L)));
            totalOfficersLabel.setText(String.valueOf(stats.getOrDefault("totalOfficers", 0L)));
            totalCriminalsLabel.setText(String.valueOf(stats.getOrDefault("totalCriminals", 0L)));
            incidentsSummaryLabel.setText(stats.getOrDefault("totalIncidents", 0L) + " total incidents recorded in the system.");
        });
        task.setOnFailed(e -> {
            logger.error("Failed to load dashboard stats", task.getException());
            totalCasesLabel.setText("?");
        });
        Thread t = new Thread(task, "dashboard-stats-loader");
        t.setDaemon(true);
        t.start();
    }

    private void loadAIAlertsAsync() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() {
                return aiService.checkForAnomalies();
            }
        };
        task.setOnSucceeded(e -> {
            List<String> alerts = task.getValue();
            if (alerts.isEmpty()) {
                aiAlertsLabel.setText("✅ No anomalies detected. System is operating normally.");
            } else {
                aiAlertsLabel.setText("⚠️ " + String.join("\n⚠️ ", alerts));
            }
        });
        task.setOnFailed(e -> {
            aiAlertsLabel.setText("Could not load AI alerts.");
        });
        Thread t = new Thread(task, "ai-alerts-loader");
        t.setDaemon(true);
        t.start();
    }

    // ─── QUICK ACTION HANDLERS (now actually navigate!) ───

    @FXML private void onAddCase(MouseEvent event) {
        navigateToModule("CaseManagement");
    }
    @FXML private void onAddOfficer(MouseEvent event) {
        navigateToModule("UserAdmin");
    }
    @FXML private void onSearchCriminal(MouseEvent event) {
        navigateToModule("CriminalSearch");
    }
    @FXML private void onNewIncident(MouseEvent event) {
        navigateToModule("IncidentRegistration");
    }
    @FXML private void onAIAnalytics(MouseEvent event) {
        navigateToModule("ChatbotView");
    }

    /**
     * FIX: Actually navigate via MainController.getInstance().loadModule()
     * Previously this was a no-op that only logged.
     */
    private void navigateToModule(String moduleName) {
        MainController main = MainController.getInstance();
        if (main != null) {
            main.loadModule(moduleName);
        } else {
            logger.warn("MainController instance not available for navigation to: {}", moduleName);
        }
    }
}
