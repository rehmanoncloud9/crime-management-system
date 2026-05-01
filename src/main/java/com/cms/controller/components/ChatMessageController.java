package com.cms.controller.components;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Pos;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatMessageController {
    @FXML private HBox root;
    @FXML private Circle avatarCircle;
    @FXML private Label avatarChar;
    @FXML private Label senderName;
    @FXML private Label messageText;
    @FXML private Label timestamp;
    @FXML private VBox bubble;

    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public void setMessage(String sender, String content, LocalDateTime time, boolean isUser) {
        senderName.setText(sender.toUpperCase());
        messageText.setText(content);
        timestamp.setText(time.format(timeFormatter));

        if (isUser) {
            root.setAlignment(Pos.CENTER_RIGHT);
            avatarCircle.setFill(Color.web("#2563eb")); // Blue
            avatarChar.setText("U");
            bubble.getStyleClass().setAll("message-bubble-user");
            // Reverse order for user messages (content first, then avatar)
            root.getChildren().setAll(root.getChildren().get(1), root.getChildren().get(0));
        } else {
            root.setAlignment(Pos.CENTER_LEFT);
            avatarCircle.setFill(Color.web("#3b82f6")); // AI Blue
            avatarChar.setText("AI");
            bubble.getStyleClass().setAll("message-bubble-ai");
        }
    }
}
