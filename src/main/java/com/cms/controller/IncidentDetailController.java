package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.CrimeIncident;
import com.cms.service.IncidentService;
import com.cms.service.NavigationService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;

public class IncidentDetailController {
    private static final Logger logger = LoggerFactory.getLogger(IncidentDetailController.class);
    private final IncidentService incidentService = new IncidentService();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

    @FXML private Label headerIncidentNum;
    @FXML private Label titleLabel;
    @FXML private Label statusBadge;
    @FXML private Label severityBadge;
    @FXML private Label crimeTypeLabel;
    @FXML private Label dateLabel;
    @FXML private Label timeLabel;
    @FXML private Label descriptionLabel;

    @FXML private Label districtLabel;
    @FXML private Label cityLabel;
    @FXML private Label areaLabel;
    @FXML private Label addressLabel;

    @FXML private Label officerLabel;
    @FXML private Label reporterNameLabel;
    @FXML private Label reporterContactLabel;

    @FXML private VBox caseCard;
    @FXML private VBox caseInfoBox;
    @FXML private VBox noCaseBox;
    @FXML private Label caseNumLabel;
    @FXML private Label caseStatusLabel;
    
    @FXML private StackPane loadingOverlay;

    private Long incidentId;
    private CrimeIncident currentIncident;
    private CaseFile linkedCase;

    public void init(Long id) {
        this.incidentId = id;
        loadDossier();
    }

    private void loadDossier() {
        loadingOverlay.setVisible(true);
        Task<Object[]> task = new Task<>() {
            @Override
            protected Object[] call() {
                return incidentService.getIncidentDossier(incidentId);
            }
        };

        task.setOnSucceeded(e -> {
            Object[] results = task.getValue();
            this.currentIncident = (CrimeIncident) results[0];
            this.linkedCase = (CaseFile) results[1];
            renderDossier();
            loadingOverlay.setVisible(false);
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load dossier", task.getException());
            loadingOverlay.setVisible(false);
        });

        new Thread(task).start();
    }

    private void renderDossier() {
        if (currentIncident == null) return;

        headerIncidentNum.setText(currentIncident.getIncidentNumber());
        titleLabel.setText(currentIncident.getTitle());
        descriptionLabel.setText(currentIncident.getDescription());
        
        statusBadge.setText(currentIncident.getStatus() != null ? currentIncident.getStatus().name() : "NEW");
        severityBadge.setText((currentIncident.getSeverityLevel() != null ? currentIncident.getSeverityLevel() : "MEDIUM") + " SEVERITY");
        
        if (currentIncident.getCrimeType() != null) {
            crimeTypeLabel.setText(currentIncident.getCrimeType().getName());
        }

        if (currentIncident.getOccurredAt() != null) {
            dateLabel.setText(currentIncident.getOccurredAt().format(dateFormatter));
            timeLabel.setText(currentIncident.getOccurredAt().format(timeFormatter));
        }

        districtLabel.setText(currentIncident.getDistrict() != null ? currentIncident.getDistrict().getName() : "N/A");
        cityLabel.setText(currentIncident.getCity() != null ? currentIncident.getCity().getName() : "N/A");
        areaLabel.setText(currentIncident.getArea() != null ? currentIncident.getArea().getName() : "N/A");
        addressLabel.setText(currentIncident.getLocationAddress() != null ? currentIncident.getLocationAddress() : "N/A");

        if (currentIncident.getReportingOfficer() != null) {
            officerLabel.setText(currentIncident.getReportingOfficer().getFullName());
        } else {
            officerLabel.setText("N/A");
        }

        reporterNameLabel.setText(currentIncident.getReporterName() != null ? currentIncident.getReporterName() : "N/A");
        reporterContactLabel.setText(currentIncident.getReporterContact() != null ? currentIncident.getReporterContact() : "N/A");

        if (linkedCase != null) {
            caseInfoBox.setVisible(true);
            caseInfoBox.setManaged(true);
            noCaseBox.setVisible(false);
            noCaseBox.setManaged(false);
            caseNumLabel.setText(linkedCase.getCaseNumber());
            caseStatusLabel.setText(linkedCase.getStatus().name());
        } else {
            caseInfoBox.setVisible(false);
            caseInfoBox.setManaged(false);
            noCaseBox.setVisible(true);
            noCaseBox.setManaged(true);
        }
    }

    @FXML
    private void handleBack() {
        NavigationService.getInstance().goBack();
    }

    @FXML
    private void handleViewCase() {
        if (linkedCase != null) {
            NavigationService.getInstance().navigateTo("Case Management", "/fxml/modules/CaseManagement.fxml", null);
        }
    }

    @FXML
    private void handleConvertToCase() {
        // Logic to navigate to Case Creation or trigger it
        NavigationService.getInstance().navigateTo("Case Management", "/fxml/modules/CaseManagement.fxml", null);
    }
}
