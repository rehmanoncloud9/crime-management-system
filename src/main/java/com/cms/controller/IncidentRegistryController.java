package com.cms.controller;

import com.cms.model.CrimeIncident;
import com.cms.service.IncidentService;
import com.cms.service.NavigationService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class IncidentRegistryController {
    private static final Logger logger = LoggerFactory.getLogger(IncidentRegistryController.class);

    @FXML private TextField searchField;
    @FXML private FlowPane incidentFlowPane;

    private final IncidentService incidentService = new IncidentService();

    @FXML
    public void initialize() {
        loadIncidents();
        searchField.textProperty().addListener((obs, old, newVal) -> loadIncidents());
    }

    @FXML
    public void loadIncidents() {
        String keyword = searchField.getText().trim();
        javafx.concurrent.Task<List<CrimeIncident>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<CrimeIncident> call() {
                return incidentService.searchIncidents(keyword);
            }
        };

        task.setOnSucceeded(e -> {
            List<CrimeIncident> incidents = task.getValue();
            incidentFlowPane.getChildren().clear();
            if (incidents.isEmpty()) {
                Label placeholder = new Label("No incidents found matching search criteria.");
                placeholder.getStyleClass().add("text-muted");
                incidentFlowPane.getChildren().add(placeholder);
            } else {
                for (CrimeIncident inc : incidents) {
                    incidentFlowPane.getChildren().add(buildIncidentCard(inc));
                }
            }
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load incidents", task.getException());
            incidentFlowPane.getChildren().setAll(new Label("Error loading incidents."));
        });

        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    private Pane buildIncidentCard(CrimeIncident inc) {
        VBox card = new VBox(12);
        card.getStyleClass().add("form-section-card");
        card.setPrefWidth(300);
        card.setPadding(new javafx.geometry.Insets(20));

        Label numLabel = new Label(inc.getIncidentNumber());
        numLabel.getStyleClass().add("form-label-premium");
        
        Label titleLabel = new Label(inc.getTitle());
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 900; -fx-text-fill: -cms-t1;");
        titleLabel.setWrapText(true);

        Label dateLabel = new Label("📅 " + (inc.getOccurredAt() != null ? inc.getOccurredAt().toString() : "N/A"));
        dateLabel.getStyleClass().add("text-muted-sm");

        Label statusBadge = new Label(inc.getStatus() != null ? inc.getStatus().name() : "NEW");
        statusBadge.getStyleClass().setAll("status-badge", "status-active");
        
        javafx.scene.control.Button viewBtn = new javafx.scene.control.Button("OPEN DOSSIER");
        viewBtn.getStyleClass().add("btn-premium-teal-sm");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        viewBtn.setOnAction(e -> {
            NavigationService.getInstance().navigateTo(
                "Incident Dossier", 
                "/fxml/modules/IncidentDetailView.fxml", 
                controller -> ((IncidentDetailController) controller).init(inc.getId())
            );
        });

        card.getChildren().addAll(numLabel, titleLabel, dateLabel, statusBadge, viewBtn);
        return card;
    }

    @FXML
    private void handleNewIncident() {
        NavigationService.getInstance().navigateTo("Register Incident", "/fxml/modules/IncidentRegistration.fxml", null);
    }
}
