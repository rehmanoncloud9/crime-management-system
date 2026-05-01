package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.User;

import com.cms.service.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class MyCasesController {
    @FXML private TableView<CaseFile> caseTable;
    @FXML private TableColumn<CaseFile, String> numberCol;
    @FXML private TableColumn<CaseFile, String> titleCol;
    @FXML private TableColumn<CaseFile, String> statusCol;
    @FXML private TableColumn<CaseFile, String> dateCol;
    @FXML private TextField searchField;

    private final com.cms.service.CaseService caseService = new com.cms.service.CaseService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTable();
        loadData();
        setupSearch();
    }

    private void setupTable() {
        numberCol.setCellValueFactory(new PropertyValueFactory<>("caseNumber"));
        titleCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(
                cellData.getValue().getIncident() != null ? cellData.getValue().getIncident().getTitle() : "N/A"
            ));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        dateCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(
                cellData.getValue().getOpenedAt() != null ? cellData.getValue().getOpenedAt().format(formatter) : "N/A"
            ));
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterData(newVal));
    }

    private void loadData() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            javafx.concurrent.Task<List<CaseFile>> task = new javafx.concurrent.Task<>() {
                @Override
                protected List<CaseFile> call() {
                    return caseService.getCasesByInvestigator(currentUser.getId());
                }
            };
            task.setOnSucceeded(e -> caseTable.setItems(FXCollections.observableArrayList(task.getValue())));
            task.setOnFailed(e -> task.getException().printStackTrace());
            new Thread(task).start();
        }
    }

    private void filterData(String keyword) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser != null) {
            javafx.concurrent.Task<List<CaseFile>> task = new javafx.concurrent.Task<>() {
                @Override
                protected List<CaseFile> call() {
                    List<CaseFile> allCases = caseService.getCasesByInvestigator(currentUser.getId());
                    if (keyword == null || keyword.isEmpty()) {
                        return allCases;
                    }
                    String lowKey = keyword.toLowerCase();
                    return allCases.stream()
                        .filter(c -> c.getCaseNumber().toLowerCase().contains(lowKey) || 
                                     (c.getIncident() != null && c.getIncident().getTitle().toLowerCase().contains(lowKey)))
                        .collect(Collectors.toList());
                }
            };
            task.setOnSucceeded(e -> caseTable.setItems(FXCollections.observableArrayList(task.getValue())));
            new Thread(task).start();
        }
    }
}
