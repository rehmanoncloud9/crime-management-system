package com.cms.controller;

import com.cms.model.CrimeIncident;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class OfficerDashboardController {
    @FXML private ListView<String> assignedCasesList;
    @FXML private ListView<String> warrantsList;
    @FXML private TableView<CrimeIncident> incidentsTable;
    @FXML private TableColumn<CrimeIncident, String> incidentNumCol;
    @FXML private TableColumn<CrimeIncident, String> typeCol;
    @FXML private TableColumn<CrimeIncident, String> timeCol;
    @FXML private TableColumn<CrimeIncident, String> statusCol;

    private final com.cms.service.IncidentService incidentService = new com.cms.service.IncidentService();
    private final com.cms.service.CaseService caseService = new com.cms.service.CaseService();
    private final com.cms.service.WarrantService warrantService = new com.cms.service.WarrantService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTable();
        loadData();
    }

    private void setupTable() {
        incidentNumCol.setCellValueFactory(new PropertyValueFactory<>("incidentNumber"));
        typeCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(
                cellData.getValue().getCrimeType() != null ? cellData.getValue().getCrimeType().getName() : "N/A"
            ));
        timeCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(
                cellData.getValue().getReportedAt() != null ? cellData.getValue().getReportedAt().format(formatter) : "N/A"
            ));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void loadData() {
        javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                List<CrimeIncident> incidents = incidentService.findAll(1000, 0);
                
                com.cms.model.User currentUser = com.cms.service.SessionManager.getInstance().getCurrentUser();
                List<String> caseStrings = java.util.Collections.emptyList();
                if (currentUser != null) {
                    List<com.cms.model.CaseFile> cases = caseService.getCasesByInvestigator(currentUser.getId());
                    caseStrings = cases.stream()
                        .map(c -> c.getCaseNumber() + ": " + c.getIncident().getTitle())
                        .collect(java.util.stream.Collectors.toList());
                }

                List<com.cms.model.Warrant> warrants = warrantService.findAll(1000, 0);
                List<String> warrantStrings = warrants.stream()
                    .filter(w -> w.getStatus() == com.cms.model.enums.WarrantStatus.ISSUED)
                    .map(w -> w.getSuspect().getFirstName() + " " + w.getSuspect().getLastName() + " - " + w.getWarrantNumber())
                    .collect(java.util.stream.Collectors.toList());

                final List<String> fCaseStrings = caseStrings;
                javafx.application.Platform.runLater(() -> {
                    incidentsTable.setItems(FXCollections.observableArrayList(incidents));
                    assignedCasesList.setItems(FXCollections.observableArrayList(fCaseStrings));
                    warrantsList.setItems(FXCollections.observableArrayList(warrantStrings));
                });
                return null;
            }
        };
        task.setOnFailed(e -> e.getSource().getException().printStackTrace());
        Thread t = new Thread(task, "officer-dashboard-loader");
        t.setDaemon(true);
        t.start();
    }
}
