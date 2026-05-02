package com.cms;

import com.cms.service.HibernateUtil;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void start(Stage primaryStage) {
        System.out.println("###################################################");
        System.out.println("### CMS APPLICATION STARTING - DIAGNOSTIC MODE  ###");
        System.out.println("###################################################");
        
        // 1) System Initialization (Migrations & Lookups)
        try {
            System.out.println(">>> [CMS-BOOT] Starting System Initialization...");
            com.cms.service.DatabaseInitializer.initialize();
            System.out.println(">>> [CMS-BOOT] System initialized.");
        } catch (Throwable e) {
            System.err.println(">>> [CMS-BOOT-ERROR] Initialization CRITICAL FAILURE!");
            logger.error("Application error", e);
            logger.error("Database initialization failed", e);
        }

        // 2) Always try to load UI
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"));
            Scene scene = new Scene(root, 1024, 680);
            // application.css is intentionally omitted here to prevent conflicts with the Login theme
            // It will be added in the Main dashboard navigation.

            primaryStage.setTitle("Crime Management System v5.0");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);
            primaryStage.setResizable(true);

            // Respect the taskbar — use visual bounds, NOT setMaximized
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            primaryStage.setX(bounds.getMinX());
            primaryStage.setY(bounds.getMinY());
            primaryStage.setWidth(bounds.getWidth());
            primaryStage.setHeight(bounds.getHeight());

            primaryStage.show();

        } catch (Exception e) {
            logger.error("Failed to start application UI", e);
            logger.error("Application error", e);
        }
    }

    @Override
    public void stop() { HibernateUtil.shutdown(); }

    public static void main(String[] args) { launch(args); }
}
