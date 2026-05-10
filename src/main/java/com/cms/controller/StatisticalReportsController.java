package com.cms.controller;

import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.service.HibernateUtil;
import com.cms.service.IncidentService;
import com.cms.service.SessionManager;
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
import com.cms.util.NexusAlert;
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
        // RBAC: Restricted Analytics Access
        User user = SessionManager.getInstance().getCurrentUser();
        Role role = user != null ? user.getRole() : null;
        boolean hasAccess = role == Role.ADMINISTRATOR || role == Role.DETECTIVE || 
                           role == Role.OFFICER || role == Role.ANALYST || role == Role.SUPERVISOR;

        if (!hasAccess) {
            Platform.runLater(() -> {
                NexusAlert.showWarning("ACCESS RESTRICTED\n\nStatistical analytics and performance reports require Operational Clearance.");
            });
            // We'll let the load finish but the user will be alerted and we could potentially 
            // hide components here if we wanted to be stricter.
            return;
        }

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
            crimeTrendChart.setCreateSymbols(false);
            crimeTrendChart.setLegendVisible(false);
            crimeTrendChart.setAnimated(false);
        }
        if (districtChart != null) {
            districtChart.setStyle("-fx-background-color: transparent;");
            districtChart.setLegendVisible(false);
            districtChart.setAnimated(false);
            districtChart.setBarGap(6);
            districtChart.setCategoryGap(18);
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
                            "SELECT COALESCE(d.name, 'Unknown'), COUNT(i) FROM CrimeIncident i " +
                            "LEFT JOIN i.district d " +
                            "WHERE i.occurredAt >= :start AND i.occurredAt <= :end " +
                            "GROUP BY COALESCE(d.name, 'Unknown') ORDER BY COUNT(i) DESC", Object[].class)
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
                            "SELECT COALESCE(d.name, 'Unknown'), COUNT(a) FROM ArrestRecord a " +
                            "JOIN a.caseFile cf JOIN cf.incident i LEFT JOIN i.district d " +
                            "WHERE a.arrestedAt >= :start AND a.arrestedAt <= :end " +
                            "GROUP BY COALESCE(d.name, 'Unknown') ORDER BY COUNT(a) DESC", Object[].class)
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
                            "WHERE cf.status IN (com.cms.model.enums.CaseStatus.CLOSED_CONVICTED, " +
                            "com.cms.model.enums.CaseStatus.CLOSED_ACQUITTED, " +
                            "com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED) " +
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
        params.setFill(Color.web("#07090F")); // Match Nexus Dark

        if (crimeTrendChart != null) lineSnap = crimeTrendChart.snapshot(params, null);
        if (districtChart != null) barSnap = districtChart.snapshot(params, null);

        final WritableImage finalLineSnap = lineSnap;
        final WritableImage finalBarSnap = barSnap;

        // Choose destination
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save Executive Intelligence Briefing");
        chooser.setInitialFileName("Nexus_Report_" + rt.replace(" ", "_") + ".pdf");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Documents", "*.pdf"));
        java.io.File selectedFile = chooser.showSaveDialog(reportTypeCombo.getScene().getWindow());

        if (selectedFile == null) return;
        final String outPath = selectedFile.getAbsolutePath();

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                // Use OpenPDF to create high-end briefing
                com.lowagie.text.Document doc = new com.lowagie.text.Document(
                    com.lowagie.text.PageSize.A4, 36, 36, 54, 54); 
                com.lowagie.text.pdf.PdfWriter writer = com.lowagie.text.pdf.PdfWriter.getInstance(doc, new java.io.FileOutputStream(outPath));
                doc.open();

                // Header Section with Logo
                com.lowagie.text.pdf.PdfPTable headerTable = new com.lowagie.text.pdf.PdfPTable(2);
                headerTable.setWidthPercentage(100);
                headerTable.setWidths(new float[]{3, 1});

                // Title Cell
                com.lowagie.text.Font titleFont = new com.lowagie.text.Font(
                    com.lowagie.text.Font.HELVETICA, 24, com.lowagie.text.Font.BOLD,
                    new java.awt.Color(15, 23, 42)); // Slate 900
                com.lowagie.text.pdf.PdfPCell titleCell = new com.lowagie.text.pdf.PdfPCell(
                    new com.lowagie.text.Phrase("NEXUS COMMAND\nIntelligence Briefing", titleFont));
                titleCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                titleCell.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
                headerTable.addCell(titleCell);

                // Logo Cell
                try {
                    // We'll look for the generated logo in the brain/artifacts dir
                    String logoPath = "C:\\Users\\HP\\.gemini\\antigravity\\brain\\e015d7e3-e4c5-42a1-921d-848e499699a1\\nexus_command_logo_1778393505976.png";
                    com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(logoPath);
                    logo.scaleToFit(80, 80);
                    com.lowagie.text.pdf.PdfPCell logoCell = new com.lowagie.text.pdf.PdfPCell(logo);
                    logoCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                    logoCell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_RIGHT);
                    headerTable.addCell(logoCell);
                } catch (Exception e) {
                    headerTable.addCell(""); // Fallback
                }

                doc.add(headerTable);
                doc.add(new com.lowagie.text.Paragraph(" "));

                // Separator Line
                com.lowagie.text.pdf.draw.LineSeparator ls = new com.lowagie.text.pdf.draw.LineSeparator();
                ls.setLineColor(new java.awt.Color(59, 130, 246)); // Blue 500
                ls.setLineWidth(2);
                doc.add(new com.lowagie.text.pdf.draw.VerticalPositionMark(ls, 0));

                // Report Type & Meta
                com.lowagie.text.Font subTitleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 16, com.lowagie.text.Font.BOLD, new java.awt.Color(37, 99, 235));
                doc.add(new com.lowagie.text.Paragraph("REPORT: " + rt.toUpperCase(), subTitleFont));
                
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy");
                com.lowagie.text.Font metaFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.ITALIC, new java.awt.Color(100, 116, 139));
                doc.add(new com.lowagie.text.Paragraph("Classification: LAW ENFORCEMENT SENSITIVE", metaFont));
                doc.add(new com.lowagie.text.Paragraph("Period: " + getEffectiveStart().format(fmt) + " to " + getEffectiveEnd().format(fmt), metaFont));
                doc.add(new com.lowagie.text.Paragraph("Generation Date: " + LocalDate.now().format(fmt), metaFont));
                doc.add(new com.lowagie.text.Paragraph(" "));

                // Charts Section
                if (finalLineSnap != null) {
                    doc.add(new com.lowagie.text.Paragraph("1.0 Temporal Analysis (Monthly Trends)", 
                        new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD)));
                    doc.add(new com.lowagie.text.Paragraph(" "));
                    
                    BufferedImage bImg = SwingFXUtils.fromFXImage(finalLineSnap, null);
                    File tmpLine = File.createTempFile("cms_line_chart", ".png");
                    ImageIO.write(bImg, "png", tmpLine);
                    com.lowagie.text.Image chartImg = com.lowagie.text.Image.getInstance(tmpLine.getAbsolutePath());
                    chartImg.scaleToFit(500, 250);
                    chartImg.setAlignment(com.lowagie.text.Image.MIDDLE);
                    doc.add(chartImg);
                    doc.add(new com.lowagie.text.Paragraph(" "));
                    tmpLine.deleteOnExit();
                }

                if (finalBarSnap != null) {
                    if (finalLineSnap != null) doc.newPage(); // New page if multi-chart
                    
                    doc.add(new com.lowagie.text.Paragraph("2.0 Geospatial Distribution", 
                        new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 12, com.lowagie.text.Font.BOLD)));
                    doc.add(new com.lowagie.text.Paragraph(" "));
                    
                    BufferedImage bImg = SwingFXUtils.fromFXImage(finalBarSnap, null);
                    File tmpBar = File.createTempFile("cms_bar_chart", ".png");
                    ImageIO.write(bImg, "png", tmpBar);
                    com.lowagie.text.Image chartImg = com.lowagie.text.Image.getInstance(tmpBar.getAbsolutePath());
                    chartImg.scaleToFit(500, 250);
                    chartImg.setAlignment(com.lowagie.text.Image.MIDDLE);
                    doc.add(chartImg);
                    tmpBar.deleteOnExit();
                }

                // Security Footer
                com.lowagie.text.Font footerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, new java.awt.Color(148, 163, 184));
                com.lowagie.text.Paragraph footer = new com.lowagie.text.Paragraph(
                    "\n\n\n\nCONFIDENTIAL — PROPERTY OF NEXUS COMMAND\nUnauthorized reproduction or distribution is strictly prohibited under Digital Evidence Act.", 
                    footerFont);
                footer.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                doc.add(footer);

                doc.close();

                Platform.runLater(() ->
                    NexusAlert.showInfo("Executive Intelligence Briefing saved to:\n" + outPath)
                );
                return null;
            }
        };
        task.setOnFailed(e -> Platform.runLater(() ->
            NexusAlert.showError("Briefing generation failed: " + task.getException().getMessage())));
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    // ═══════════════════════════════════════════════════
    //  Excel Export — Data tables with correct HQL
    // ═══════════════════════════════════════════════════
    @FXML private void handleExportExcel() {
        String reportType = reportTypeCombo.getValue();
        if (reportType == null) reportType = "Incident Summary";
        final String rt = reportType;

        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save Excel Report");
        chooser.setInitialFileName("CMS_" + rt.replace(" ", "_") + "_" + System.currentTimeMillis() + ".xlsx");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Excel Workbooks", "*.xlsx"));
        java.io.File selectedFile = chooser.showSaveDialog(reportTypeCombo.getScene().getWindow());

        if (selectedFile == null) return;
        final String outputPath = selectedFile.getAbsolutePath();

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                
                org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet(rt);
                
                // Header style
                org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
                org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerFont.setFontHeightInPoints((short) 12);
                headerStyle.setFont(headerFont);
                headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.LIGHT_BLUE.getIndex());
                headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
                
                switch (rt) {
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
                    NexusAlert.showInfo("Excel report saved to:\n" + outputPath)
                );
                return null;
            }
        };
        task.setOnFailed(e -> Platform.runLater(() ->
            NexusAlert.showError("Export failed: " + task.getException().getMessage())));
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
                row.createCell(4).setCellValue(ar.getChargesDisplay());
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
