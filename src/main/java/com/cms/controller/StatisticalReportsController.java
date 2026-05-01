package com.cms.controller;

import com.cms.service.HibernateUtil;
import com.cms.service.IncidentService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class StatisticalReportsController {
    private static final Logger logger = LoggerFactory.getLogger(StatisticalReportsController.class);

    @FXML private DatePicker           startDatePicker;
    @FXML private DatePicker           endDatePicker;
    @FXML private ComboBox<String>     reportTypeCombo;
    @FXML private LineChart<String,Number> crimeTrendChart;
    @FXML private BarChart<String,Number>  districtChart;

    private final IncidentService  incidentService  = new IncidentService();

    private static final String[] MONTHS = {"Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"};

    @FXML
    public void initialize() {
        reportTypeCombo.setItems(FXCollections.observableArrayList(
            "Incident Summary","Arrest Success Rate","Evidence Inventory","Officer Performance"));
        
        reportTypeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) loadChartsForReportType(newVal);
        });
        
        styleCharts();
        loadCharts();
    }

    private void styleCharts() {
        if (crimeTrendChart != null) {
            crimeTrendChart.setStyle("-fx-background-color: transparent;");
            crimeTrendChart.setCreateSymbols(true);
            crimeTrendChart.setLegendVisible(false);
        }
        if (districtChart != null) {
            districtChart.setStyle("-fx-background-color: transparent;");
            districtChart.setLegendVisible(false);
            // Rotate district names on x-axis for readability
            if (districtChart.getXAxis() instanceof CategoryAxis catAxis) {
                catAxis.setTickLabelRotation(45);
            }
        }
    }

    /** Get the date range from pickers, or default to last 6 months */
    private LocalDate getEffectiveStart() {
        if (startDatePicker != null && startDatePicker.getValue() != null) {
            return startDatePicker.getValue();
        }
        return LocalDate.now().minusMonths(5).withDayOfMonth(1);
    }

    private LocalDate getEffectiveEnd() {
        if (endDatePicker != null && endDatePicker.getValue() != null) {
            return endDatePicker.getValue();
        }
        return LocalDate.now();
    }

    /** Generate list of YearMonth values between start and end */
    private List<YearMonth> getMonthRange(LocalDate start, LocalDate end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth ym = YearMonth.from(start);
        YearMonth ymEnd = YearMonth.from(end);
        while (!ym.isAfter(ymEnd)) {
            months.add(ym);
            ym = ym.plusMonths(1);
        }
        return months;
    }

    private String monthLabel(YearMonth ym) {
        return MONTHS[ym.getMonthValue() - 1] + " " + (ym.getYear() % 100);
    }

    private void loadCharts() {
        LocalDate start = getEffectiveStart();
        LocalDate end = getEffectiveEnd();
        List<YearMonth> months = getMonthRange(start, end);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                // Line chart: real monthly counts for selected range
                XYChart.Series<String,Number> trendSeries = new XYChart.Series<>();
                trendSeries.setName("Reported Incidents");
                for (YearMonth ym : months) {
                    long count = incidentService.countInMonth(ym.getYear(), ym.getMonthValue());
                    trendSeries.getData().add(new XYChart.Data<>(monthLabel(ym), count));
                }

                // Bar chart: real district breakdown
                XYChart.Series<String,Number> distSeries = new XYChart.Series<>();
                distSeries.setName("Incidents by District");
                try {
                    List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                        session.createQuery(
                            "SELECT d.name, COUNT(i) FROM CrimeIncident i JOIN i.district d " +
                            "WHERE i.occurredAt >= :start AND i.occurredAt <= :end " +
                            "GROUP BY d.name ORDER BY COUNT(i) DESC", Object[].class)
                        .setParameter("start", start.atStartOfDay())
                        .setParameter("end", end.plusDays(1).atStartOfDay())
                        .setMaxResults(10).list()
                    );
                    for (Object[] row : rows)
                        distSeries.getData().add(new XYChart.Data<>((String)row[0], (Long)row[1]));
                } catch (Exception e) { logger.warn("District chart error: {}", e.getMessage()); }

                updateChartUI(trendSeries, distSeries, "Crime Trend (Monthly)", "Incidents by District");
                return null;
            }
        };
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    @FXML private void handleGenerate() { 
        String selectedType = reportTypeCombo.getValue();
        if (selectedType != null) {
            loadChartsForReportType(selectedType);
        } else {
            loadCharts();
        }
    }
    
    private void loadChartsForReportType(String reportType) {
        switch (reportType) {
            case "Incident Summary" -> loadCharts();
            case "Arrest Success Rate" -> loadArrestSuccessCharts();
            case "Evidence Inventory" -> loadEvidenceCharts();
            case "Officer Performance" -> loadOfficerPerformanceCharts();
            default -> loadCharts();
        }
    }
    
    private void loadArrestSuccessCharts() {
        LocalDate start = getEffectiveStart();
        LocalDate end = getEffectiveEnd();
        List<YearMonth> months = getMonthRange(start, end);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                // Line chart: monthly arrest counts from arrest_records table
                XYChart.Series<String,Number> trendSeries = new XYChart.Series<>();
                trendSeries.setName("Arrests Made");
                for (YearMonth ym : months) {
                    long arrests = 0;
                    try {
                        arrests = HibernateUtil.executeTransaction(session -> {
                            Long count = session.createQuery(
                                "SELECT COUNT(a) FROM ArrestRecord a " +
                                "WHERE YEAR(a.arrestedAt) = :y AND MONTH(a.arrestedAt) = :m", Long.class)
                                .setParameter("y", ym.getYear())
                                .setParameter("m", ym.getMonthValue())
                                .getSingleResult();
                            return count != null ? count : 0L;
                        });
                    } catch (Exception ignore) {}
                    trendSeries.getData().add(new XYChart.Data<>(monthLabel(ym), arrests));
                }

                // Bar chart: Arrests by district
                XYChart.Series<String,Number> distSeries = new XYChart.Series<>();
                distSeries.setName("Arrests by District");
                try {
                    List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                        session.createQuery(
                            "SELECT d.name, COUNT(a) FROM ArrestRecord a " +
                            "JOIN a.caseFile cf JOIN cf.incident i JOIN i.district d " +
                            "WHERE a.arrestedAt >= :start AND a.arrestedAt <= :end " +
                            "GROUP BY d.name ORDER BY COUNT(a) DESC", Object[].class)
                        .setParameter("start", start.atStartOfDay())
                        .setParameter("end", end.plusDays(1).atStartOfDay())
                        .setMaxResults(10).list()
                    );
                    for (Object[] row : rows)
                        distSeries.getData().add(new XYChart.Data<>((String)row[0], (Long)row[1]));
                } catch (Exception e) { logger.warn("Arrest chart error: {}", e.getMessage()); }

                updateChartUI(trendSeries, distSeries, "Arrest Trend (Monthly)", "Arrests by District");
                return null;
            }
        };
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }
    
    private void loadEvidenceCharts() {
        LocalDate start = getEffectiveStart();
        LocalDate end = getEffectiveEnd();
        List<YearMonth> months = getMonthRange(start, end);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                // Line chart: Evidence collected per month - REAL DATA
                XYChart.Series<String,Number> trendSeries = new XYChart.Series<>();
                trendSeries.setName("Evidence Items Collected");
                for (YearMonth ym : months) {
                    long count = 0;
                    try {
                        count = HibernateUtil.executeTransaction(session -> {
                            Long c = session.createQuery(
                                "SELECT COUNT(e) FROM Evidence e " +
                                "WHERE YEAR(e.collectedAt) = :y AND MONTH(e.collectedAt) = :m", Long.class)
                                .setParameter("y", ym.getYear())
                                .setParameter("m", ym.getMonthValue())
                                .getSingleResult();
                            return c != null ? c : 0L;
                        });
                    } catch (Exception ignore) {}
                    trendSeries.getData().add(new XYChart.Data<>(monthLabel(ym), count));
                }

                // Bar chart: Evidence count by type - REAL DATA
                XYChart.Series<String,Number> typeSeries = new XYChart.Series<>();
                typeSeries.setName("Evidence by Type");
                try {
                    List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                        session.createQuery(
                            "SELECT CAST(e.type AS string), COUNT(e) FROM Evidence e " +
                            "GROUP BY e.type ORDER BY COUNT(e) DESC", Object[].class)
                        .list()
                    );
                    for (Object[] row : rows)
                        typeSeries.getData().add(new XYChart.Data<>(String.valueOf(row[0]), (Long)row[1]));
                } catch (Exception e) { 
                    logger.warn("Evidence type chart error: {}", e.getMessage());
                }

                updateChartUI(trendSeries, typeSeries, "Evidence Collection (Monthly)", "Evidence by Type");
                return null;
            }
        };
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }
    
    private void loadOfficerPerformanceCharts() {
        LocalDate start = getEffectiveStart();
        LocalDate end = getEffectiveEnd();
        List<YearMonth> months = getMonthRange(start, end);

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                // Line chart: Cases closed per month
                XYChart.Series<String,Number> trendSeries = new XYChart.Series<>();
                trendSeries.setName("Cases Closed");
                for (YearMonth ym : months) {
                    long closed = 0;
                    try {
                        closed = HibernateUtil.executeTransaction(session -> {
                            Long c = session.createQuery(
                                "SELECT COUNT(cf) FROM CaseFile cf " +
                                "WHERE cf.closedAt IS NOT NULL " +
                                "AND YEAR(cf.closedAt) = :y AND MONTH(cf.closedAt) = :m", Long.class)
                                .setParameter("y", ym.getYear())
                                .setParameter("m", ym.getMonthValue())
                                .getSingleResult();
                            return c != null ? c : 0L;
                        });
                    } catch (Exception ignore) {}
                    trendSeries.getData().add(new XYChart.Data<>(monthLabel(ym), closed));
                }

                // Bar chart: Top performing officers — REAL NAMES from DB using primaryInvestigator
                XYChart.Series<String,Number> officerSeries = new XYChart.Series<>();
                officerSeries.setName("Cases Solved by Officer");
                try {
                    List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                        session.createQuery(
                            "SELECT u.fullName, COUNT(cf) FROM CaseFile cf " +
                            "JOIN cf.primaryInvestigator u " +
                            "WHERE cf.status IN (com.cms.model.enums.IncidentStatus.CLOSED, " +
                            "com.cms.model.enums.IncidentStatus.CLOSED_CONVICTED, " +
                            "com.cms.model.enums.IncidentStatus.CLOSED_ACQUITTED, " +
                            "com.cms.model.enums.IncidentStatus.CLOSED_UNSOLVED) " +
                            "GROUP BY u.fullName ORDER BY COUNT(cf) DESC", Object[].class)
                        .setMaxResults(10).list()
                    );
                    if (rows.isEmpty()) {
                        // If no closed cases, show all officers with their total assigned cases
                        rows = HibernateUtil.executeTransaction(session ->
                            session.createQuery(
                                "SELECT u.fullName, COUNT(cf) FROM CaseFile cf " +
                                "JOIN cf.primaryInvestigator u " +
                                "GROUP BY u.fullName ORDER BY COUNT(cf) DESC", Object[].class)
                            .setMaxResults(10).list()
                        );
                    }
                    for (Object[] row : rows)
                        officerSeries.getData().add(new XYChart.Data<>((String)row[0], (Long)row[1]));
                } catch (Exception e) { 
                    logger.warn("Officer performance chart error: {}", e.getMessage()); 
                }

                if (officerSeries.getData().isEmpty()) {
                    // Show a "No Data" placeholder instead of fake Officer A/B/C
                    officerSeries.getData().add(new XYChart.Data<>("No Data", 0L));
                }

                updateChartUI(trendSeries, officerSeries, "Officer Performance (Monthly)", "Top Performers");
                return null;
            }
        };
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }
    
    private void updateChartUI(XYChart.Series<String,Number> lineSeries, XYChart.Series<String,Number> barSeries,
                                String lineTitle, String barTitle) {
        Platform.runLater(() -> {
            if (crimeTrendChart != null) {
                crimeTrendChart.setTitle(lineTitle);
                crimeTrendChart.getData().clear();
                crimeTrendChart.getData().add(lineSeries);
                javafx.animation.PauseTransition d = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
                d.setOnFinished(ev -> { if (lineSeries.getNode() != null)
                    lineSeries.getNode().setStyle("-fx-stroke: #00D4FF; -fx-stroke-width: 2.5px;"); });
                d.play();
            }
            if (districtChart != null) {
                districtChart.setTitle(barTitle);
                districtChart.getData().clear();
                districtChart.getData().add(barSeries);
                // Rotate district label axis for readability
                if (districtChart.getXAxis() instanceof CategoryAxis catAxis) {
                    catAxis.setTickLabelRotation(45);
                }
                javafx.animation.PauseTransition d = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
                d.setOnFinished(ev -> barSeries.getData().forEach(bar -> {
                    if (bar.getNode() != null) bar.getNode().setStyle("-fx-bar-fill: rgba(0,212,255,0.75);");
                }));
                d.play();
            }
        });
    }

    // ═══════════════════════════════════════════════════
    //  PDF Export — Snapshot the charts as images into PDF
    // ═══════════════════════════════════════════════════
    @FXML
    private void handleExportPDF() {
        String reportType = reportTypeCombo.getValue();
        if (reportType == null) reportType = "Incident Summary";
        final String rt = reportType;

        // Take chart snapshots on FX thread
        WritableImage lineSnap = null;
        WritableImage barSnap = null;
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.web("#07090F"));

        if (crimeTrendChart != null) lineSnap = crimeTrendChart.snapshot(params, null);
        if (districtChart != null) barSnap = districtChart.snapshot(params, null);

        final WritableImage finalLineSnap = lineSnap;
        final WritableImage finalBarSnap = barSnap;

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                String fileName = "CMS_" + rt.replace(" ", "_") + "_" +
                                  System.currentTimeMillis() + ".pdf";
                String outPath = System.getProperty("user.home") + "/Desktop/" + fileName;

                // Use OpenPDF to create PDF with chart images
                com.lowagie.text.Document doc = new com.lowagie.text.Document(
                    com.lowagie.text.PageSize.A4.rotate()); // Landscape for charts
                com.lowagie.text.pdf.PdfWriter.getInstance(doc, new java.io.FileOutputStream(outPath));
                doc.open();

                // Title
                com.lowagie.text.Font titleFont = new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 20, com.lowagie.text.Font.BOLD,
                    new java.awt.Color(0, 212, 255));
                doc.add(new com.lowagie.text.Paragraph("Crime Management System — " + rt, titleFont));
                doc.add(new com.lowagie.text.Paragraph(" "));

                // Date range info
                com.lowagie.text.Font infoFont = new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 11, com.lowagie.text.Font.NORMAL,
                    new java.awt.Color(107, 191, 204));
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
                String rangeText = "Date Range: " + getEffectiveStart().format(fmt) + " — " + getEffectiveEnd().format(fmt);
                doc.add(new com.lowagie.text.Paragraph(rangeText, infoFont));
                doc.add(new com.lowagie.text.Paragraph("Generated: " + LocalDate.now().format(fmt), infoFont));
                doc.add(new com.lowagie.text.Paragraph(" "));

                // Embed chart snapshots as images
                if (finalLineSnap != null) {
                    BufferedImage bImg = SwingFXUtils.fromFXImage(finalLineSnap, null);
                    File tmpLine = File.createTempFile("cms_line_chart", ".png");
                    ImageIO.write(bImg, "png", tmpLine);
                    com.lowagie.text.Image chartImg = com.lowagie.text.Image.getInstance(tmpLine.getAbsolutePath());
                    chartImg.scaleToFit(680, 320);
                    chartImg.setAlignment(com.lowagie.text.Image.MIDDLE);
                    doc.add(chartImg);
                    doc.add(new com.lowagie.text.Paragraph(" "));
                    tmpLine.deleteOnExit();
                }
                if (finalBarSnap != null) {
                    BufferedImage bImg = SwingFXUtils.fromFXImage(finalBarSnap, null);
                    File tmpBar = File.createTempFile("cms_bar_chart", ".png");
                    ImageIO.write(bImg, "png", tmpBar);
                    com.lowagie.text.Image chartImg = com.lowagie.text.Image.getInstance(tmpBar.getAbsolutePath());
                    chartImg.scaleToFit(680, 320);
                    chartImg.setAlignment(com.lowagie.text.Image.MIDDLE);
                    doc.add(chartImg);
                    tmpBar.deleteOnExit();
                }

                doc.close();

                Platform.runLater(() ->
                    new Alert(Alert.AlertType.INFORMATION, "PDF report saved to:\n" + outPath).showAndWait()
                );
                return null;
            }
        };
        task.setOnFailed(e -> Platform.runLater(() ->
            new Alert(Alert.AlertType.ERROR, "PDF Export failed: " + task.getException().getMessage()).showAndWait()));
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    // ═══════════════════════════════════════════════════
    //  Excel Export — Data tables with correct HQL
    // ═══════════════════════════════════════════════════
    @FXML private void handleExportExcel() {
        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                String reportType = reportTypeCombo.getValue();
                if (reportType == null) reportType = "Incident Summary";
                
                String fileName = "CMS_" + reportType.replace(" ", "_") + "_" + 
                                  System.currentTimeMillis() + ".xlsx";
                String outputPath = System.getProperty("user.home") + "/Desktop/" + fileName;
                
                org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(reportType);
                
                // Header style
                org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 12);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                
                switch (reportType) {
                    case "Incident Summary" -> exportIncidentSummary(sheet, headerStyle);
                    case "Arrest Success Rate" -> exportArrestSuccess(sheet, headerStyle);
                    case "Evidence Inventory" -> exportEvidenceInventory(sheet, headerStyle);
                    case "Officer Performance" -> exportOfficerPerformance(sheet, headerStyle);
                    default -> exportIncidentSummary(sheet, headerStyle);
                }
                
                for (int i = 0; i < 10; i++) sheet.autoSizeColumn(i);
                
                try (java.io.FileOutputStream fileOut = new java.io.FileOutputStream(outputPath)) {
                    workbook.write(fileOut);
                }
                workbook.close();
                
                Platform.runLater(() -> 
                    new Alert(Alert.AlertType.INFORMATION, "Excel report saved to:\n" + outputPath).showAndWait()
                );
                return null;
            }
        };
        task.setOnFailed(e -> Platform.runLater(() ->
            new Alert(Alert.AlertType.ERROR, "Export failed: " + task.getException().getMessage()).showAndWait()));
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }
    
    private void exportIncidentSummary(org.apache.poi.ss.usermodel.Sheet sheet, 
                                       org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"Incident Number", "Title", "Crime Type", "Date", "District", "Status"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        List<com.cms.model.CrimeIncident> incidents = incidentService.findAll(1000, 0);
        int rowNum = 1;
        for (com.cms.model.CrimeIncident inc : incidents) {
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(inc.getIncidentNumber());
            row.createCell(1).setCellValue(inc.getTitle());
            row.createCell(2).setCellValue(inc.getCrimeType() != null ? inc.getCrimeType().getName() : "");
            row.createCell(3).setCellValue(inc.getOccurredAt() != null ? inc.getOccurredAt().toLocalDate().toString() : "");
            row.createCell(4).setCellValue(inc.getDistrict() != null ? inc.getDistrict().getName() : "");
            row.createCell(5).setCellValue(inc.getStatus() != null ? inc.getStatus().toString() : "");
        }
    }
    
    private void exportArrestSuccess(org.apache.poi.ss.usermodel.Sheet sheet,
                                     org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"Suspect Name", "Case Number", "Arrest Date", "Arresting Officer", "Charges", "Location"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        try {
            List<com.cms.model.ArrestRecord> arrests = HibernateUtil.executeTransaction(session ->
                session.createQuery(
                    "SELECT a FROM ArrestRecord a " +
                    "LEFT JOIN FETCH a.suspect LEFT JOIN FETCH a.caseFile " +
                    "LEFT JOIN FETCH a.arrestingOfficer " +
                    "ORDER BY a.arrestedAt DESC", com.cms.model.ArrestRecord.class)
                .setMaxResults(500).list()
            );
            int rowNum = 1;
            for (com.cms.model.ArrestRecord ar : arrests) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(ar.getSuspect() != null ? 
                    ar.getSuspect().getFirstName() + " " + ar.getSuspect().getLastName() : "");
                row.createCell(1).setCellValue(ar.getCaseFile() != null ? ar.getCaseFile().getCaseNumber() : "");
                row.createCell(2).setCellValue(ar.getArrestedAt() != null ? ar.getArrestedAt().toLocalDate().toString() : "");
                row.createCell(3).setCellValue(ar.getArrestingOfficer() != null ? ar.getArrestingOfficer().getFullName() : "");
                row.createCell(4).setCellValue(ar.getCharges() != null ? ar.getCharges() : "");
                row.createCell(5).setCellValue(ar.getArrestLocation() != null ? ar.getArrestLocation() : "");
            }
        } catch (Exception e) {
            logger.warn("Error exporting arrest data: {}", e.getMessage());
        }
    }
    
    private void exportEvidenceInventory(org.apache.poi.ss.usermodel.Sheet sheet,
                                        org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"Evidence Number", "Case Number", "Type", "Status", "Collection Location", "Storage Location", "Description"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Real data from Evidence table
        try {
            List<com.cms.model.Evidence> evidenceList = HibernateUtil.executeTransaction(session ->
                session.createQuery(
                    "SELECT e FROM Evidence e LEFT JOIN FETCH e.caseFile ORDER BY e.id DESC",
                    com.cms.model.Evidence.class)
                .setMaxResults(500).list()
            );
            int rowNum = 1;
            for (com.cms.model.Evidence ev : evidenceList) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(ev.getEvidenceNumber() != null ? ev.getEvidenceNumber() : "");
                row.createCell(1).setCellValue(ev.getCaseFile() != null ? ev.getCaseFile().getCaseNumber() : "");
                row.createCell(2).setCellValue(ev.getType() != null ? ev.getType().toString() : "");
                row.createCell(3).setCellValue(ev.getStatus() != null ? ev.getStatus().toString() : "");
                row.createCell(4).setCellValue(ev.getCollectionLocation() != null ? ev.getCollectionLocation() : "");
                row.createCell(5).setCellValue(ev.getCurrentStorageLocation() != null ? ev.getCurrentStorageLocation() : "");
                row.createCell(6).setCellValue(ev.getDescription() != null ? ev.getDescription() : "");
            }
        } catch (Exception e) {
            logger.warn("Error exporting evidence data: {}", e.getMessage());
        }
    }
    
    private void exportOfficerPerformance(org.apache.poi.ss.usermodel.Sheet sheet,
                                         org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
        String[] headers = {"Officer Name", "Badge Number", "Cases Assigned", "Cases Closed", "Arrests Made", "Success Rate %"};
        for (int i = 0; i < headers.length; i++) {
            org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // Real data using primaryInvestigator (NOT assignedTo)
        try {
            List<Object[]> rows = HibernateUtil.executeTransaction(session ->
                session.createQuery(
                    "SELECT u.fullName, u.badgeNumber, COUNT(cf), " +
                    "SUM(CASE WHEN cf.closedAt IS NOT NULL THEN 1 ELSE 0 END) " +
                    "FROM CaseFile cf JOIN cf.primaryInvestigator u " +
                    "GROUP BY u.fullName, u.badgeNumber ORDER BY COUNT(cf) DESC", Object[].class)
                .setMaxResults(50)
                .list()
            );
            int rowNum = 1;
            for (Object[] row : rows) {
                org.apache.poi.ss.usermodel.Row excelRow = sheet.createRow(rowNum++);
                String name = (String) row[0];
                String badge = (String) row[1];
                Long assigned = (Long) row[2];
                Long closed = (Long) row[3];
                double successRate = assigned > 0 ? (closed * 100.0 / assigned) : 0;
                
                // Also get arrest count for this officer
                long arrestCount = 0;
                try {
                    final String officerName = name;
                    arrestCount = HibernateUtil.executeTransaction(session -> {
                        Long c = session.createQuery(
                            "SELECT COUNT(a) FROM ArrestRecord a WHERE a.arrestingOfficer.fullName = :name", Long.class)
                            .setParameter("name", officerName)
                            .getSingleResult();
                        return c != null ? c : 0L;
                    });
                } catch (Exception ignore) {}
                
                excelRow.createCell(0).setCellValue(name);
                excelRow.createCell(1).setCellValue(badge != null ? badge : "N/A");
                excelRow.createCell(2).setCellValue(assigned);
                excelRow.createCell(3).setCellValue(closed);
                excelRow.createCell(4).setCellValue(arrestCount);
                excelRow.createCell(5).setCellValue(String.format("%.1f%%", successRate));
            }
        } catch (Exception e) {
            logger.warn("Error exporting officer data: {}", e.getMessage());
        }
    }
}
