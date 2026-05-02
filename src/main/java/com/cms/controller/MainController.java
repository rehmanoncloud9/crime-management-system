package com.cms.controller;

import com.cms.model.User;
import com.cms.service.NavigationService;
import com.cms.service.SessionManager;
import com.cms.util.AnimationHelper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private VBox sideMenu;
    @FXML
    private ScrollPane sidebarScroll;
    @FXML
    private StackPane contentArea;

    private Button activeButton;
    private static MainController instance;
    private boolean sidebarExpanded = true;

    private static final double EXPANDED = 240.0;
    private static final double COLLAPSED = 0.0;

    public static MainController getInstance() {
        return instance;
    }

    @FXML
    public void initialize() {
        instance = this;
        User user = SessionManager.getInstance().getCurrentUser();
        if (user != null) {
            NavigationService.getInstance().setContentArea(contentArea);
            buildSidebar(user);

            Platform.runLater(() -> {
                sidebarScroll.setPrefWidth(0);
                sidebarScroll.setMinWidth(0);
                sidebarScroll.setOpacity(0);
                Timeline slideIn = new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(sidebarScroll.prefWidthProperty(), 0),
                                new KeyValue(sidebarScroll.opacityProperty(), 0)),
                        new KeyFrame(Duration.millis(420),
                                new KeyValue(sidebarScroll.prefWidthProperty(), EXPANDED, Interpolator.EASE_OUT),
                                new KeyValue(sidebarScroll.opacityProperty(), 1, Interpolator.EASE_OUT)));
                slideIn.setOnFinished(e -> {
                    sidebarScroll.setMinWidth(EXPANDED);
                    sidebarExpanded = true;
                    List<Node> btns = new ArrayList<>();
                    for (Node n : sideMenu.getChildren())
                        if (n instanceof Button)
                            btns.add(n);
                    AnimationHelper.staggeredEntrance(btns, 45);
                });
                slideIn.play();
            });

            NavigationService.getInstance().navigateToRoot("Dashboard", "/fxml/modules/Dashboard.fxml");
        }
    }

    /**
     * Called from HeaderController's hamburger button (and formerly from toggleBtn)
     */
    @FXML
    public void handleToggleSidebar() {
        if (sidebarExpanded)
            collapseSidebar();
        else
            expandSidebar();
        sidebarExpanded = !sidebarExpanded;
    }

    private void expandSidebar() {
        sidebarScroll.setVisible(true);
        sidebarScroll.setManaged(true);
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(sidebarScroll.prefWidthProperty(), 0),
                        new KeyValue(sidebarScroll.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(300),
                        new KeyValue(sidebarScroll.prefWidthProperty(), EXPANDED, Interpolator.EASE_OUT),
                        new KeyValue(sidebarScroll.opacityProperty(), 1, Interpolator.EASE_OUT)));
        t.setOnFinished(e -> sidebarScroll.setMinWidth(EXPANDED));
        t.play();
    }

    private void collapseSidebar() {
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(sidebarScroll.prefWidthProperty(), EXPANDED),
                        new KeyValue(sidebarScroll.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(280),
                        new KeyValue(sidebarScroll.prefWidthProperty(), 0, Interpolator.EASE_OUT),
                        new KeyValue(sidebarScroll.opacityProperty(), 0, Interpolator.EASE_IN)));
        t.setOnFinished(e -> sidebarScroll.setMinWidth(0));
        t.play();
    }

    private void buildSidebar(User user) {
        com.cms.model.enums.Role role = user.getRole();

        addSection("MAIN");
        addMenuButton("📊", "Dashboard", "Dashboard");
        addMenuButton("📁", "Cases", "CaseManagement");
        if (role != com.cms.model.enums.Role.ANALYST)
            addMenuButton("🚨", "Incidents", "IncidentRegistration");

        addSection("PEOPLE");
        if (role == com.cms.model.enums.Role.ADMINISTRATOR)
            addMenuButton("👮", "Officers", "UserAdmin");
        addMenuButton("🔍", "Criminals", "CriminalSearch");
        addMenuButton("📋", "Person Registry", "PersonRegistry");

        addSection("INTELLIGENCE");
        addMenuButton("🤖", "AI Assistant", "ChatbotView");
        if (role != com.cms.model.enums.Role.OFFICER) {
            addMenuButton("📈", "Reports", "StatisticalReports");
            if (role == com.cms.model.enums.Role.ADMINISTRATOR
                    || role == com.cms.model.enums.Role.SUPERVISOR
                    || role == com.cms.model.enums.Role.ANALYST)
                addMenuButton("📊", "Executive KPIs", "ExecutiveDashboard");
        }

        addSection("SYSTEM");
        if (role == com.cms.model.enums.Role.ADMINISTRATOR)
            addMenuButton("⚖️", "Court", "CourtManagement");
        if (role != com.cms.model.enums.Role.ANALYST)
            addMenuButton("📦", "Evidence", "EvidenceLog");
        if (role != com.cms.model.enums.Role.ANALYST)
            addMenuButton("🚔", "Arrested Persons", "ArrestRegistration");
        if (role == com.cms.model.enums.Role.ADMINISTRATOR) {
            addMenuButton("📝", "Audit Logs", "AuditDashboard");
            addMenuButton("⚙️", "Settings", "Config");
        }
    }

    private void addSection(String title) {
        Label s = new Label(title);
        s.setStyle("-fx-padding: 14 20 2 20; -fx-font-size: 9px; -fx-font-weight: bold;" +
                "-fx-text-fill: rgba(0,212,255,0.38);");
        sideMenu.getChildren().add(s);
    }

    private void addMenuButton(String icon, String text, String mod) {
        Button btn = new Button(icon + "  " + text);
        btn.getStyleClass().add("sidebar-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(44);
        btn.setMinHeight(44);
        btn.setMaxHeight(44);
        btn.setOnAction(e -> {
            setActiveButton(btn);
            NavigationService.getInstance().navigateToRoot(text, "/fxml/modules/" + mod + ".fxml");
        });
        AnimationHelper.addHoverLift(btn);
        AnimationHelper.addClickPress(btn);
        sideMenu.getChildren().add(btn);
        if (activeButton == null && "Dashboard".equals(mod))
            setActiveButton(btn);
    }

    private void setActiveButton(Button btn) {
        if (activeButton != null)
            activeButton.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        activeButton = btn;
    }

    public void loadModule(String mod) {
        NavigationService.getInstance().navigateTo(mod, "/fxml/modules/" + mod + ".fxml");
    }

    public void loadPersonDetail(Long personId) {
        NavigationService.getInstance().navigateTo("Person Details", "/fxml/modules/PersonDetailView.fxml",
                c -> {
                    if (c instanceof PersonDetailController p)
                        p.loadPerson(personId);
                });
    }
}
