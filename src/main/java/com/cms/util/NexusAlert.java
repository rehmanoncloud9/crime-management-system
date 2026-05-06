package com.cms.util;

import com.cms.controller.NexusAlertController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NexusAlert {
    private static final Logger logger = LoggerFactory.getLogger(NexusAlert.class);

    public enum Type { SUCCESS, ERROR, WARNING, CONFIRM }

    public static void show(String title, String message, Type type) {
        display(title, message, type);
    }

    public static void showInfo(String message) {
        display("Information", message, Type.SUCCESS);
    }

    public static void showError(String message) {
        display("System Error", message, Type.ERROR);
    }

    public static void showWarning(String message) {
        display("Attention", message, Type.WARNING);
    }

    public static boolean confirm(String title, String message) {
        return display(title, message, Type.CONFIRM);
    }

    private static boolean display(String title, String message, Type type) {
        try {
            FXMLLoader loader = new FXMLLoader(NexusAlert.class.getResource("/fxml/components/NexusAlert.fxml"));
            Parent root = loader.load();
            NexusAlertController controller = loader.getController();

            controller.setType(type.name());
            if (title != null) controller.setTitle(title);
            controller.setMessage(message);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            
            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(NexusAlert.class.getResource("/css/application.css").toExternalForm());
            
            stage.setScene(scene);
            
            // Add slight fade in animation
            root.setOpacity(0);
            // Show and block until closed
            stage.showAndWait();
            
            return controller.isConfirmed();
        } catch (Exception e) {
            logger.error("Failed to show NexusAlert", e);
            // Fallback to standard alert if custom fails
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(message);
            alert.showAndWait();
            return true;
        }
    }
}
