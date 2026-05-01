package com.cms.controller;

import com.cms.model.CaseFile;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class InvestigatorDashboardController {
    @FXML private TableView<CaseFile> investigationsTable;
    @FXML private TableColumn<CaseFile, String> caseNumberCol;
    @FXML private TableColumn<CaseFile, String> incidentTitleCol;
    @FXML private TableColumn<CaseFile, String> statusCol;
    @FXML private TableColumn<CaseFile, String> openedAtCol;

    private final com.cms.service.CaseService caseService = new com.cms.service.CaseService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTable();
        loadInvestigations();
    }

    private void setupTable() {
        caseNumberCol.setCellValueFactory(new PropertyValueFactory<>("caseNumber"));
        incidentTitleCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(
                cellData.getValue().getIncident() != null ? cellData.getValue().getIncident().getTitle() : "N/A"
            ));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        openedAtCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(
                cellData.getValue().getOpenedAt() != null ? cellData.getValue().getOpenedAt().format(formatter) : "N/A"
            ));
    }

    private void loadInvestigations() {
        javafx.concurrent.Task<List<CaseFile>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<CaseFile> call() {
                return caseService.findAllCases();
            }
        };
        task.setOnSucceeded(e -> investigationsTable.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }
}
