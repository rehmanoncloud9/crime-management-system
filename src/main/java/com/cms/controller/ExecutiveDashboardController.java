package com.cms.controller;

import com.cms.model.enums.IncidentStatus;
import com.cms.service.HibernateUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ExecutiveDashboardController {

    @FXML private Label    closureRateLabel;
    @FXML private Label    totalIncidentsLabel;
    @FXML private PieChart crimeDistributionChart;
    @FXML private BarChart<String,Number> districtCrimeChart;

    // Color mapping for consistent pie chart and legend colors
    private static final java.util.Map<String, String> CRIME_COLORS = java.util.Map.ofEntries(
        java.util.Map.entry("Murder", "#FF6B4A"),           // Coral red
        java.util.Map.entry("Robbery", "#FFA500"),          // Orange  
        java.util.Map.entry("Narcotics Operations", "#2ECC71"), // Green
        java.util.Map.entry("Cyber Fraud", "#00D4FF"),      // Cyan
        java.util.Map.entry("Kidnapping", "#7B5CF6"),       // Purple
        java.util.Map.entry("Assault", "#9D7EFA"),          // Light purple
        java.util.Map.entry("Extortion", "#FF4081"),        // Pink
        java.util.Map.entry("Vehicle Theft", "#90A4AE"),    // Gray
        java.util.Map.entry("Arson", "#FF8A65"),            // Light orange
        java.util.Map.entry("Terrorism", "#FFA502"),        // Orange
        java.util.Map.entry("Dacoity", "#27AE60"),          // Dark green
        java.util.Map.entry("Forgery / Document Fraud", "#00B0E8"), // Light cyan
        java.util.Map.entry("Burglary", "#FFB74D"),         // Amber
        java.util.Map.entry("Rape", "#E91E63"),             // Deep pink
        java.util.Map.entry("Fraud", "#00ACC1")             // Teal
    );
    
    private static final String[] FALLBACK_COLORS = {
        "#00D4FF","#7B5CF6","#2ECC71","#FFA502","#FF4757",
        "#F0B429","#00B0E8","#9D7EFA","#27ae60","#cc2233","#33EEFF","#6B48FF"
    };

    @FXML
    public void initialize() {
        styleCharts();
        loadMetrics();
        loadDistribution();
        loadDistrictHotspots();
    }

    private void styleCharts() {
        if (crimeDistributionChart != null) {
            crimeDistributionChart.setStyle("-fx-background-color: transparent;");
            crimeDistributionChart.setLegendVisible(true);
            crimeDistributionChart.setLabelsVisible(true);
        }
        if (districtCrimeChart != null) {
            districtCrimeChart.setStyle("-fx-background-color: transparent;");
            districtCrimeChart.setLegendVisible(false);
            // Rotate district names on bar chart for proper readability
            if (districtCrimeChart.getXAxis() instanceof javafx.scene.chart.CategoryAxis catAxis) {
                catAxis.setTickLabelRotation(45);
            }
        }
    }

    private void loadMetrics() {
        Task<long[]> t = new Task<>() {
            @Override protected long[] call() {
                return HibernateUtil.executeTransaction(session -> {
                    long total = session.createQuery("SELECT COUNT(cf) FROM CaseFile cf", Long.class).uniqueResult();
                    long closed = session.createQuery(
                        "SELECT COUNT(cf) FROM CaseFile cf WHERE cf.status IN (:s1,:s2,:s3,:s4)", Long.class)
                        .setParameter("s1", IncidentStatus.CLOSED)
                        .setParameter("s2", IncidentStatus.CLOSED_CONVICTED)
                        .setParameter("s3", IncidentStatus.CLOSED_ACQUITTED)
                        .setParameter("s4", IncidentStatus.CLOSED_UNSOLVED)
                        .uniqueResult();
                    LocalDate now = LocalDate.now();
                    long mtd = session.createQuery(
                        "SELECT COUNT(i) FROM CrimeIncident i WHERE i.occurredAt >= :start AND i.occurredAt < :end", Long.class)
                        .setParameter("start", now.withDayOfMonth(1).atStartOfDay())
                        .setParameter("end",   now.plusMonths(1).withDayOfMonth(1).atStartOfDay())
                        .uniqueResult();
                    return new long[]{total, closed, mtd};
                });
            }
        };
        t.setOnSucceeded(e -> {
            long[] d = t.getValue();
            long total = d[0], closed = d[1], mtd = d[2];
            if (totalIncidentsLabel != null) totalIncidentsLabel.setText(String.valueOf(mtd));
            if (closureRateLabel != null)
                closureRateLabel.setText(total > 0 ? String.format("%.1f%%", (double)closed/total*100) : "0%");
        });
        Thread th = new Thread(t); th.setDaemon(true); th.start();
    }

    private void loadDistribution() {
        Task<Map<String,Long>> t = new Task<>() {
            @Override protected Map<String,Long> call() {
                try {
                    List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                        session.createQuery(
                            "SELECT ct.name, COUNT(i) FROM CrimeIncident i JOIN i.crimeType ct " +
                            "GROUP BY ct.name ORDER BY COUNT(i) DESC", Object[].class)
                        .setMaxResults(12).list()
                    );
                    if (!rows.isEmpty()) {
                        Map<String,Long> dist = new LinkedHashMap<>();
                        for (Object[] row : rows) dist.put((String)row[0], (Long)row[1]);
                        return dist;
                    }
                } catch (Exception ignore) {}
                return Map.of();
            }
        };
        t.setOnSucceeded(e -> {
            Map<String,Long> dist = t.getValue();
            if (dist.isEmpty() || crimeDistributionChart == null) return;
            List<PieChart.Data> pie = dist.entrySet().stream()
                .map(en -> new PieChart.Data(en.getKey() + " (" + en.getValue() + ")", en.getValue()))
                .collect(Collectors.toList());
            crimeDistributionChart.setData(FXCollections.observableArrayList(pie));
            Platform.runLater(() -> {
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
                delay.setOnFinished(ev -> {
                    int fallbackIdx = 0;
                    for (PieChart.Data d2 : crimeDistributionChart.getData()) {
                        if (d2.getNode() != null) {
                            // Extract crime type name (before the count in parentheses)
                            String label = d2.getName();
                            String crimeType = label.contains(" (") ? label.substring(0, label.indexOf(" (")) : label;
                            
                            // Use mapped color if available, otherwise use fallback
                            String color = CRIME_COLORS.getOrDefault(crimeType, 
                                FALLBACK_COLORS[fallbackIdx % FALLBACK_COLORS.length]);
                            d2.getNode().setStyle("-fx-pie-color: " + color + ";");
                            fallbackIdx++;
                        }
                    }
                });
                delay.play();
            });
        });
        Thread th = new Thread(t); th.setDaemon(true); th.start();
    }

    private void loadDistrictHotspots() {
        Task<Map<String,Long>> t = new Task<>() {
            @Override protected Map<String,Long> call() {
                try {
                    List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                        session.createQuery(
                            "SELECT d.name, COUNT(i) FROM CrimeIncident i JOIN i.district d " +
                            "GROUP BY d.name ORDER BY COUNT(i) DESC", Object[].class)
                        .setMaxResults(10).list()
                    );
                    if (!rows.isEmpty()) {
                        Map<String,Long> m = new LinkedHashMap<>();
                        for (Object[] r : rows) m.put((String)r[0], (Long)r[1]);
                        return m;
                    }
                } catch (Exception ignore) {}
                return Map.of();
            }
        };
        t.setOnSucceeded(e -> {
            Map<String,Long> data = t.getValue();
            if (data.isEmpty() || districtCrimeChart == null) return;
            XYChart.Series<String,Number> series = new XYChart.Series<>();
            data.forEach((d2,c) -> series.getData().add(new XYChart.Data<>(d2, c)));
            districtCrimeChart.getData().clear();
            districtCrimeChart.getData().add(series);
            Platform.runLater(() -> {
                javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));
                delay.setOnFinished(ev ->
                    series.getData().forEach(bar -> {
                        if (bar.getNode() != null) bar.getNode().setStyle("-fx-bar-fill: #00D4FF;");
                    })
                );
                delay.play();
            });
        });
        Thread th = new Thread(t); th.setDaemon(true); th.start();
    }
}
