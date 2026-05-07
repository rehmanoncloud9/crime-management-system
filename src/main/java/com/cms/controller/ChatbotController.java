package com.cms.controller;

import com.cms.service.EliteAIService;
import com.cms.service.SessionManager;
import com.cms.util.AnimationHelper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CMS v5.0 — Elite AI Chatbot Controller
 * Powered by EliteAIService (Groq API + Smart Local Engine)
 * Full DB integration + animated responses
 */
public class ChatbotController {
    private static final Logger logger = LoggerFactory.getLogger(ChatbotController.class);

    private final EliteAIService aiService = new EliteAIService();
    private String sessionId = "1";
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AI-Worker");
        t.setDaemon(true);
        return t;
    });

    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatContainer;
    @FXML private TextField inputField;
    @FXML private Button sendButton;
    @FXML private Label statusLabel;
    @FXML private Button clearChatButton;
    @FXML private HBox inputBar;

    // Typing indicator node
    private HBox typingIndicator;
    private Timeline typingDots;

    @FXML
    public void initialize() {
        var user = SessionManager.getInstance().getCurrentUser();
        if (user != null) sessionId = String.valueOf(user.getId());

        setupInputField();
        setupChatContainer();

        // Welcome message is now handled by the FXML overlay

        updateStatus("AI Under Training — Local Datasets");
        if (sendButton != null) {
            AnimationHelper.addHoverLift(sendButton);
            AnimationHelper.addClickPress(sendButton);
        }

        logger.info("Elite chatbot initialized in preview mode for session: {}", sessionId);
    }

    private void setupInputField() {
        if (inputField == null) return;
        inputField.setOnAction(e -> handleSend());
        inputField.textProperty().addListener((obs, ov, nv) -> {
            if (sendButton != null) sendButton.setDisable(nv == null || nv.isBlank());
        });
        if (sendButton != null) sendButton.setDisable(true);
        inputField.requestFocus();
    }

    private void setupChatContainer() {
        if (chatContainer == null) return;
        chatContainer.setStyle(
            "-fx-background-color: #07090F; " +
            "-fx-padding: 16;"
        );
        chatContainer.setSpacing(8);
        chatContainer.setFillWidth(true);
    }

    @FXML
    public void handleSend() {
        if (inputField == null) return;
        String text = inputField.getText().trim();
        if (text.isBlank()) return;

        // Show user message
        addUserMessage(text);
        inputField.clear();
        if (sendButton != null) sendButton.setDisable(true);

        // Show typing
        showTypingIndicator();
        updateStatus("AI is thinking...");

        // Process async
        executor.submit(() -> {
            try {
                String response = aiService.processMessage(text, sessionId);
                Platform.runLater(() -> {
                    hideTypingIndicator();
                    addAIMessage(response, true);
                    updateStatus("AI Under Training — Local Datasets");
                    if (sendButton != null) sendButton.setDisable(false);
                    inputField.requestFocus();
                });
            } catch (Exception e) {
                logger.error("Chat error", e);
                Platform.runLater(() -> {
                    hideTypingIndicator();
                    addAIMessage("⚠️ Connection issue. Please check your database connection.", false);
                    updateStatus("Error — Check connection");
                    if (sendButton != null) sendButton.setDisable(false);
                });
            }
        });
    }

    @FXML
    public void handleClearChat() {
        if (chatContainer == null) return;
        aiService.clearSession(sessionId);

        FadeTransition ft = new FadeTransition(Duration.millis(200), chatContainer);
        ft.setFromValue(1); ft.setToValue(0);
        ft.setOnFinished(e -> {
            chatContainer.getChildren().clear();
            chatContainer.setOpacity(1);
            addAIMessage("Session cleared. How can I assist your investigation?", false);
        });
        ft.play();
        updateStatus("Session cleared");
    }

    // ── Message builders ─────────────────────────────────────────────────────

    private void addUserMessage(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(4, 0, 4, 60));

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(480);

        Label msg = new Label(text);
        msg.setWrapText(true);
        msg.setStyle("-fx-text-fill: #07090F; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label time = new Label(LocalDateTime.now().format(timeFmt));
        time.setStyle("-fx-text-fill: rgba(7,9,15,0.5); -fx-font-size: 9px;");
        time.setAlignment(Pos.CENTER_RIGHT);

        bubble.getChildren().addAll(msg, time);
        bubble.setStyle(
            "-fx-background-color: linear-gradient(to right, #00AACC, #00D4FF);" +
            "-fx-background-radius: 14 14 2 14;" +
            "-fx-padding: 12 16;"
        );
        bubble.setEffect(new javafx.scene.effect.DropShadow(8, Color.web("#00D4FF40")));

        row.getChildren().add(bubble);
        addToChat(row);
    }

    private void addAIMessage(String text, boolean animate) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 60, 4, 0));

        // Avatar
        Label avatar = new Label("AI");
        avatar.setMinSize(34, 34); avatar.setMaxSize(34, 34);
        avatar.setAlignment(Pos.CENTER);
        avatar.setStyle(
            "-fx-background-color: rgba(0,212,255,0.12);" +
            "-fx-border-color: rgba(0,212,255,0.3); -fx-border-width: 1;" +
            "-fx-border-radius: 17; -fx-background-radius: 17;" +
            "-fx-text-fill: #00D4FF; -fx-font-size: 10px; -fx-font-weight: bold;"
        );

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(500);

        // Parse markdown-like text into styled content
        TextArea msgArea = new TextArea(parseMarkdown(text));
        msgArea.setEditable(false);
        msgArea.setWrapText(true);
        msgArea.setStyle(
            "-fx-background-color: transparent; -fx-control-inner-background: transparent;" +
            "-fx-text-fill: #00D4FF; -fx-font-size: 13px;" +
            "-fx-border-color: transparent; -fx-background-insets: 0; -fx-padding: 0;"
        );
        msgArea.setPrefRowCount(Math.min(12, text.split("\n").length + 1));

        Label time = new Label(LocalDateTime.now().format(timeFmt));
        time.setStyle("-fx-text-fill: #2A4A5A; -fx-font-size: 9px;");

        bubble.getChildren().addAll(msgArea, time);
        bubble.setStyle(
            "-fx-background-color: #0A1020;" +
            "-fx-border-color: rgba(0,212,255,0.2); -fx-border-width: 1;" +
            "-fx-background-radius: 14 14 14 2; -fx-border-radius: 14 14 14 2;" +
            "-fx-padding: 12 16;"
        );

        row.getChildren().addAll(avatar, bubble);

        if (animate) {
            row.setOpacity(0);
            row.setTranslateY(12);
            addToChat(row);
            ParallelTransition pt = new ParallelTransition(row,
                createFade(row, 0, 1, 350),
                createTranslateY(row, 12, 0, 350)
            );
            pt.play();
        } else {
            addToChat(row);
        }
    }

    private void showTypingIndicator() {
        typingIndicator = new HBox(10);
        typingIndicator.setAlignment(Pos.CENTER_LEFT);
        typingIndicator.setPadding(new Insets(4, 60, 4, 0));

        Label avatar = new Label("AI");
        avatar.setMinSize(34, 34); avatar.setMaxSize(34, 34);
        avatar.setAlignment(Pos.CENTER);
        avatar.setStyle(
            "-fx-background-color: rgba(0,212,255,0.12);" +
            "-fx-border-color: rgba(0,212,255,0.3); -fx-border-width: 1;" +
            "-fx-border-radius: 17; -fx-background-radius: 17;" +
            "-fx-text-fill: #00D4FF; -fx-font-size: 10px; -fx-font-weight: bold;"
        );

        Label dots = new Label("● ● ●");
        dots.setStyle(
            "-fx-text-fill: rgba(0,212,255,0.5); -fx-font-size: 16px;" +
            "-fx-background-color: #0A1020;" +
            "-fx-border-color: rgba(0,212,255,0.15); -fx-border-width: 1;" +
            "-fx-background-radius: 14; -fx-border-radius: 14;" +
            "-fx-padding: 10 18;"
        );

        // Animate dots opacity
        typingDots = new Timeline(
            new KeyFrame(Duration.ZERO,       new KeyValue(dots.opacityProperty(), 0.3)),
            new KeyFrame(Duration.millis(400), new KeyValue(dots.opacityProperty(), 1.0)),
            new KeyFrame(Duration.millis(800), new KeyValue(dots.opacityProperty(), 0.3))
        );
        typingDots.setCycleCount(Timeline.INDEFINITE);
        typingDots.play();

        typingIndicator.getChildren().addAll(avatar, dots);
        addToChat(typingIndicator);
    }

    private void hideTypingIndicator() {
        if (typingDots != null) { typingDots.stop(); typingDots = null; }
        if (typingIndicator != null && chatContainer != null) {
            chatContainer.getChildren().remove(typingIndicator);
            typingIndicator = null;
        }
    }

    private void addToChat(HBox row) {
        if (chatContainer == null) return;
        chatContainer.getChildren().add(row);
        // Auto-scroll
        Platform.runLater(() -> {
            if (chatScrollPane != null) chatScrollPane.setVvalue(1.0);
        });
    }

    private void updateStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String parseMarkdown(String text) {
        // Remove ** bold markers for display in TextArea (plain text)
        return text.replace("**", "").replace("_", "");
    }

    private FadeTransition createFade(javafx.scene.Node node, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), node);
        ft.setFromValue(from); ft.setToValue(to); return ft;
    }

    private TranslateTransition createTranslateY(javafx.scene.Node node, double from, double to, int ms) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(ms), node);
        tt.setFromY(from); tt.setToY(to);
        tt.setInterpolator(Interpolator.EASE_OUT); return tt;
    }
}
