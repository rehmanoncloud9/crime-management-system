package com.cms.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

public class NexusAlertController {

    @FXML private VBox root;
    @FXML private Circle iconBg;
    @FXML private Label iconLabel;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button cancelBtn;
    @FXML private Button okBtn;

    private boolean confirmed = false;
    
    @FXML
    public void initialize() {
        root.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(250), root);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.play();
    }


    public void setType(String type) {
        switch (type.toUpperCase()) {
            case "ERROR":
                iconBg.getStyleClass().add("alert-error-bg");
                iconLabel.getStyleClass().add("alert-error-icon");
                iconLabel.setText("✕");
                titleLabel.setText("Error");
                break;
            case "WARNING":
                iconBg.getStyleClass().add("alert-warning-bg");
                iconLabel.getStyleClass().add("alert-warning-icon");
                iconLabel.setText("⚠");
                titleLabel.setText("Warning");
                break;
            case "CONFIRM":
                iconBg.getStyleClass().add("alert-success-bg");
                iconLabel.getStyleClass().add("alert-success-icon");
                iconLabel.setText("?");
                titleLabel.setText("Confirmation");
                cancelBtn.setVisible(true);
                cancelBtn.setManaged(true);
                break;
            default: // SUCCESS / INFO
                iconBg.getStyleClass().add("alert-success-bg");
                iconLabel.getStyleClass().add("alert-success-icon");
                iconLabel.setText("✓");
                titleLabel.setText("Success");
                break;
        }
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
    }

    public void setOkButtonText(String text) {
        okBtn.setText(text);
    }

    @FXML
    private void handleOk() {
        confirmed = true;
        close();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        close();
    }

    private void close() {
        ((Stage) root.getScene().getWindow()).close();
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
