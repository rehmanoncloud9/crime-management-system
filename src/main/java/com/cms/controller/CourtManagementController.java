package com.cms.controller;

import com.cms.model.CourtCase;
import com.cms.model.CourtHearing;
import com.cms.util.NexusAlert;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CourtManagementController {
    @FXML private TableView<CourtCase> courtCasesTable;
    @FXML private TableColumn<CourtCase, String> courtCaseNumCol;
    @FXML private TableColumn<CourtCase, String> internalCaseNumCol;
    @FXML private TableColumn<CourtCase, String> prosecutorCol;
    @FXML private TableColumn<CourtCase, String> statusCol;
    @FXML private TableColumn<CourtCase, String> filedDateCol;

    @FXML private TableView<CourtHearing> hearingsTable;
    @FXML private TableColumn<CourtHearing, String> hearingDateCol;
    @FXML private TableColumn<CourtHearing, String> hCourtCaseNumCol;
    @FXML private TableColumn<CourtHearing, String> outcomeCol;

    private final com.cms.service.CourtService courtService = new com.cms.service.CourtService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    public void initialize() {
        setupTables();
        loadData();
    }

    private void setupTables() {
        // Court Cases Table
        courtCaseNumCol.setCellValueFactory(new PropertyValueFactory<>("courtCaseNumber"));
        internalCaseNumCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getCaseFile() != null ? cellData.getValue().getCaseFile().getCaseNumber() : "N/A"));
        prosecutorCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getProsecutor() != null ? cellData.getValue().getProsecutor().getFullName() : "N/A"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        filedDateCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getFiledAt() != null ? cellData.getValue().getFiledAt().format(dateFormatter) : "N/A"));

        // Hearings Table
        hearingDateCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getHearingDate() != null ? cellData.getValue().getHearingDate().format(formatter) : "N/A"));
        hCourtCaseNumCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getCourtCase() != null ? cellData.getValue().getCourtCase().getCourtCaseNumber() : "N/A"));
        outcomeCol.setCellValueFactory(new PropertyValueFactory<>("outcome"));
    }

    @FXML
    private void handleAddCase() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Court Case");
        dialog.setHeaderText("Link CMS Case to Court");
        dialog.setContentText("Enter internal CMS Case Number:");
        
        dialog.showAndWait().ifPresent(cmsNum -> {
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() {
                    java.util.Optional<com.cms.model.CaseFile> cmsCaseOpt = courtService.getCaseByNumber(cmsNum);
                    if (cmsCaseOpt.isEmpty()) {
                        javafx.application.Platform.runLater(() -> 
                            NexusAlert.showError("CMS Case not found: " + cmsNum)
                        );
                        return null;
                    }

                    CourtCase courtCase = new CourtCase();
                    courtCase.setCourtCaseNumber("CRT-" + System.currentTimeMillis() % 100000);
                    courtCase.setCaseFile(cmsCaseOpt.get());
                    courtCase.setStatus(com.cms.model.enums.CourtStatus.FILED);
                    courtCase.setFiledAt(java.time.LocalDate.now());

                    courtService.saveCourtCase(courtCase);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                loadData();
                NexusAlert.showInfo("Court case linked successfully.");
            });
            task.setOnFailed(e -> NexusAlert.showError("Error: " + task.getException().getMessage()));
            new Thread(task).start();
        });
    }

    @FXML
    private void handleScheduleHearing() {
        CourtCase selected = courtCasesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NexusAlert.showWarning("Please select a court case first.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("Tomorrow 10:00");
        dialog.setTitle("Schedule Hearing");
        dialog.setHeaderText("Schedule new hearing for: " + selected.getCourtCaseNumber());
        dialog.setContentText("Enter Date & Time (YYYY-MM-DD HH:mm):");
        
        dialog.showAndWait().ifPresent(dateTimeStr -> {
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() throws Exception {
                    LocalDateTime hearingDate = LocalDateTime.parse(dateTimeStr, formatter);
                    CourtHearing hearing = new CourtHearing();
                    hearing.setCourtCase(selected);
                    hearing.setHearingDate(hearingDate);
                    hearing.setOutcome("SCHEDULED");
                    courtService.saveHearing(hearing);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                loadData();
                NexusAlert.showInfo("Hearing scheduled successfully.");
            });
            task.setOnFailed(e -> NexusAlert.showError("Invalid date format or error executing."));
            new Thread(task).start();
        });
    }

    @FXML
    public void loadData() {
        javafx.concurrent.Task<List<CourtCase>> casesTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<CourtCase> call() {
                return courtService.getAllCases();
            }
        };
        casesTask.setOnSucceeded(e -> courtCasesTable.setItems(FXCollections.observableArrayList(casesTask.getValue())));

        javafx.concurrent.Task<List<CourtHearing>> hearingsTask = new javafx.concurrent.Task<>() {
            @Override
            protected List<CourtHearing> call() {
                return courtService.getUpcomingHearings();
            }
        };
        hearingsTask.setOnSucceeded(e -> hearingsTable.setItems(FXCollections.observableArrayList(hearingsTask.getValue())));

        new Thread(casesTask).start();
        new Thread(hearingsTask).start();
    }
}
