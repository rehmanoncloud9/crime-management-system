package com.cms.controller;

import com.cms.model.Person;
import com.cms.service.AIChatService;
import com.cms.service.AIService;
import com.cms.service.PersonService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AIAnalyticsController {
    private static final Logger logger = LoggerFactory.getLogger(AIAnalyticsController.class);

    // Chat tab
    @FXML private VBox chatMessages;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextField chatInput;
    @FXML private ProgressIndicator chatLoadingIndicator;

    // Risk tab
    @FXML private TableView<Person> riskTable;
    @FXML private TableColumn<Person, String> suspectNameCol;
    @FXML private TableColumn<Person, String> riskScoreCol;
    @FXML private TableColumn<Person, String> confidenceCol;
    @FXML private PieChart riskPieChart;

    // Pattern tab
    @FXML private BarChart<String, Number> patternChart;

    // Hotspot
    @FXML private ImageView hotspotImage;

    private final PersonService personService = new PersonService();
    private final AIService aiService = new AIService();
    private final AIChatService chatService = new AIChatService();

    @FXML
    public void initialize() {
        setupTable();
        styleCharts();
        loadDataAsync();

        // Hide loading indicator initially
        if (chatLoadingIndicator != null) {
            chatLoadingIndicator.setVisible(false);
        }
    }

    private void styleCharts() {
        if (riskPieChart != null) {
            riskPieChart.setLegendVisible(true);
            riskPieChart.setLegendSide(Side.RIGHT);
            riskPieChart.setLabelsVisible(true);
            riskPieChart.setStartAngle(90);
            riskPieChart.setAnimated(false);
        }
        if (patternChart != null) {
            patternChart.setLegendVisible(false);
            patternChart.setAnimated(false);
            patternChart.setBarGap(6);
            patternChart.setCategoryGap(18);
        }
    }

    // ─── CHAT ───

    @FXML
    private void handleChatSend() {
        String query = chatInput.getText();
        if (query == null || query.isBlank()) return;

        addChatMessage(query, true);
        chatInput.clear();
        chatInput.setDisable(true);

        // Show loading indicator
        if (chatLoadingIndicator != null) {
            chatLoadingIndicator.setVisible(true);
        }

        // Show "typing" indicator
        Label typingLabel = new Label("🤖 AI is thinking...");
        typingLabel.getStyleClass().add("text-muted");
        typingLabel.setStyle("-fx-font-style: italic;");
        HBox typingBox = new HBox(typingLabel);
        typingBox.setAlignment(Pos.CENTER_LEFT);
        typingBox.setPadding(new Insets(2, 0, 2, 0));
        chatMessages.getChildren().add(typingBox);
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));

        // Process in background
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return chatService.processQuery(query);
            }
        };
        task.setOnSucceeded(e -> {
            chatMessages.getChildren().remove(typingBox);
            addChatMessage(task.getValue(), false);
            chatInput.setDisable(false);
            chatInput.requestFocus();
            if (chatLoadingIndicator != null) {
                chatLoadingIndicator.setVisible(false);
            }
        });
        task.setOnFailed(e -> {
            chatMessages.getChildren().remove(typingBox);
            addChatMessage("❌ Error: " + task.getException().getMessage(), false);
            chatInput.setDisable(false);
            chatInput.requestFocus();
            if (chatLoadingIndicator != null) {
                chatLoadingIndicator.setVisible(false);
            }
        });

        Thread t = new Thread(task, "ai-chat-thread");
        t.setDaemon(true);
        t.start();
    }

    private void addChatMessage(String text, boolean isUser) {
        VBox bubbleContainer = new VBox(4);
        bubbleContainer.setMaxWidth(500);
        
        HBox alignBox = new HBox();
        alignBox.setPadding(new Insets(8, 12, 8, 12));

        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(400);

        Label timeLabel = new Label(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: #94a3b8;");

        if (isUser) {
            alignBox.setAlignment(Pos.CENTER_RIGHT);
            bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
            label.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-padding: 10 14; -fx-background-radius: 18 18 2 18; -fx-font-size: 13px; -fx-effect: dropshadow(gaussian, rgba(59,130,246,0.2), 5, 0, 0, 2);");
            bubbleContainer.getChildren().addAll(label, timeLabel);
        } else {
            alignBox.setAlignment(Pos.CENTER_LEFT);
            bubbleContainer.setAlignment(Pos.CENTER_LEFT);
            
            if (text != null && (text.contains("CASE ANALYSIS REPORT") || text.contains("🔍 Case Insight"))) {
                VBox card = buildStructuredAiCard(text);
                bubbleContainer.getChildren().addAll(card, timeLabel);
            } else {
                label.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #1e293b; -fx-padding: 10 14; -fx-background-radius: 18 18 18 2; -fx-font-size: 13px; -fx-border-color: #e2e8f0; -fx-border-radius: 18 18 18 2;");
                bubbleContainer.getChildren().addAll(label, timeLabel);
            }
        }

        alignBox.getChildren().add(bubbleContainer);
        chatMessages.getChildren().add(alignBox);
        Platform.runLater(() -> chatScrollPane.setVvalue(1.0));
    }

    private VBox buildStructuredAiCard(String text) {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 18; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10, 0, 0, 5);");
        card.setMaxWidth(500);

        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.contains("-----")) continue;
            
            Label label = new Label(trimmed);
            label.setWrapText(true);
            
            if (trimmed.startsWith("CASE ANALYSIS REPORT") || trimmed.contains("REPORT")) {
                label.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #0f172a; -fx-padding: 0 0 8 0;");
            } else if (trimmed.contains("Case Insight") || trimmed.contains("Analysis") || trimmed.contains("Recommendation")) {
                label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #334155; -fx-padding: 8 0 4 0;");
                if (trimmed.startsWith("🔍")) label.setStyle(label.getStyle() + " -fx-text-fill: #2563eb;");
                if (trimmed.startsWith("🧠")) label.setStyle(label.getStyle() + " -fx-text-fill: #7c3aed;");
                if (trimmed.startsWith("📌")) label.setStyle(label.getStyle() + " -fx-text-fill: #059669;");
            } else if (trimmed.startsWith("- Risk Level:") || trimmed.startsWith("- Recidivism:")) {
                String val = trimmed.substring(trimmed.indexOf(":") + 1).trim();
                label.setText("  • " + trimmed.substring(2));
                if (val.contains("HIGH")) label.setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626;");
                else if (val.contains("MEDIUM")) label.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
                else label.setStyle("-fx-text-fill: #475569;");
            } else if (trimmed.startsWith("-")) {
                label.setText("  • " + trimmed.substring(1).trim());
                label.setStyle("-fx-text-fill: #475569; -fx-font-size: 13px;");
            } else {
                label.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 13px;");
            }
            
            card.getChildren().add(label);
        }
        return card;
    }

    // ─── RISK TABLE ───

    private void setupTable() {
        if (suspectNameCol == null) return; // Safety check

        suspectNameCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()
            )
        );
        riskScoreCol.setCellValueFactory(cellData -> {
            int score = aiService.calculateRiskScore(cellData.getValue());
            return new SimpleStringProperty(aiService.mapToEnum(score) + " (" + score + ")");
        });
        confidenceCol.setCellValueFactory(cellData -> {
            double recidivism = aiService.predictRecidivism(cellData.getValue());
            return new SimpleStringProperty(String.format("%.1f%% Recidivism", recidivism * 100));
        });
    }

    @SuppressWarnings("unchecked")
    private void loadDataAsync() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                List<Person> allPersons = personService.findAll(1000, 0);

                List<Person> trackedSuspects = allPersons.stream()
                    .filter(p -> aiService.calculateRiskScore(p) > 30)
                    .collect(Collectors.toList());

                long low = allPersons.stream()
                    .filter(p -> aiService.calculateRiskScore(p) < 30).count();
                long med = allPersons.stream()
                    .filter(p -> {
                        int s = aiService.calculateRiskScore(p);
                        return s >= 30 && s < 60;
                    }).count();
                long high = allPersons.stream()
                    .filter(p -> aiService.calculateRiskScore(p) >= 60).count();

                Map<String, Long> moData = aiService.analyzeMODistribution();

                Platform.runLater(() -> {
                    if (riskTable != null) {
                        riskTable.setItems(FXCollections.observableArrayList(trackedSuspects));
                    }
                    if (riskPieChart != null) {
                        riskPieChart.setData(FXCollections.observableArrayList(
                            new PieChart.Data("Low Risk", low),
                            new PieChart.Data("Medium Risk", med),
                            new PieChart.Data("High Risk", high)
                        ));
                    }

                    if (patternChart != null) {
                        XYChart.Series<String, Number> series = new XYChart.Series<>();
                        series.setName("MO Patterns");
                        moData.forEach((tag, count) ->
                            series.getData().add(new XYChart.Data<>(tag, count))
                        );
                        patternChart.getData().setAll(series);
                    }
                });

                return null;
            }
        };
        task.setOnFailed(e -> logger.error("AI data loading failed", task.getException()));

        Thread thread = new Thread(task, "ai-analytics-loader");
        thread.setDaemon(true);
        thread.start();
    }
}
