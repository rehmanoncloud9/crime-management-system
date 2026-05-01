package com.cms.controller;

import com.cms.model.Notification;
import com.cms.service.NotificationService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class NotificationPopoverController {

    @FXML private VBox notificationList;
    private Popup popup;
    private final NotificationService notificationService = NotificationService.getInstance();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm");

    @FXML
    public void initialize() {
        loadNotifications();
    }

    public void setPopup(Popup popup) {
        this.popup = popup;
        // Refresh when showing
        popup.showingProperty().addListener((obs, wasShowing, isNowShowing) -> {
            if (isNowShowing) {
                loadNotifications();
            }
        });
    }

    private void loadNotifications() {
        Task<List<Notification>> task = new Task<>() {
            @Override
            protected List<Notification> call() {
                return notificationService.getLatestNotifications();
            }
        };

        task.setOnSucceeded(e -> {
            notificationList.getChildren().clear();
            List<Notification> items = task.getValue();
            if (items.isEmpty()) {
                Label emptyLabel = new Label("No new notifications.");
                emptyLabel.setStyle("-fx-text-fill: #999; -fx-font-style: italic;");
                notificationList.getChildren().add(emptyLabel);
            } else {
                for (Notification item : items) {
                    notificationList.getChildren().add(buildItem(item));
                }
            }
        });

        new Thread(task).start();
    }

    private VBox buildItem(Notification notif) {
        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: " + (notif.isRead() ? "#ffffff" : "#f8f9fa") + 
                     "; -fx-border-color: #eee; -fx-border-width: 0 0 1 0; -fx-cursor: hand;");

        HBox header = new HBox(8);
        Circle dot = new Circle(4);
        switch (notif.getPriority()) {
            case CRITICAL: dot.setFill(Color.web("#e74c3c")); break;
            case IMPORTANT: dot.setFill(Color.web("#f39c12")); break;
            default: dot.setFill(Color.web("#2ecc71")); break;
        }

        Label title = new Label(notif.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        if (notif.getPriority() == com.cms.model.enums.NotificationPriority.CRITICAL) {
            title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #e74c3c;");
        }

        header.getChildren().addAll(dot, title);

        Label desc = new Label(notif.getDescription());
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #555;");

        Label time = new Label(notif.getTimestamp().format(timeFormatter));
        time.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");

        box.getChildren().addAll(header, desc, time);

        box.setOnMouseClicked(e -> {
            if (!notif.isRead()) {
                Task<Void> readTask = new Task<>() {
                    @Override
                    protected Void call() {
                        notificationService.markAsRead(notif);
                        return null;
                    }
                };
                readTask.setOnSucceeded(evt -> box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #eee; -fx-border-width: 0 0 1 0; -fx-cursor: hand;"));
                new Thread(readTask).start();
            }
        });

        return box;
    }

    @FXML
    private void closePopover() {
        if (popup != null) {
            popup.hide();
        }
    }
}
