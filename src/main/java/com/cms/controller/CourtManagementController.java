package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.CourtCase;
import com.cms.model.CourtHearing;
import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.service.SessionManager;
import com.cms.util.NexusAlert;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

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
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != Role.ADMINISTRATOR && currentUser.getRole() != Role.DETECTIVE)) {
            NexusAlert.showWarning("JUDICIAL CLEARANCE REQUIRED\n\nOnly Detectives and Administrators can file court cases.");
            return;
        }

        List<CaseFile> unlinked = courtService.getUnlinkedCases();
        if (unlinked.isEmpty()) {
            NexusAlert.showInfo("ALL CASES FILED\n\nThere are no active investigations waiting for judicial linkage.");
            return;
        }

        ChoiceDialog<CaseFile> dialog = new ChoiceDialog<>(unlinked.get(0), unlinked);
        dialog.setTitle("Nexus Judicial | Link Case");
        dialog.setHeaderText("INTELLIGENCE HANDOFF\nSelect an investigation to promote to the court system.");
        dialog.setContentText("Case File:");
        
        // --- PREMIUM UI INJECTION ---
        ComboBox<CaseFile> combo = (ComboBox<CaseFile>) dialog.getDialogPane().lookup(".combo-box");
        if (combo != null) {
            combo.setPrefWidth(400);
            combo.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(CaseFile item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        VBox cell = new VBox(2);
                        Label id = new Label(item.getCaseNumber());
                        id.setStyle("-fx-font-weight: 900; -fx-text-fill: -cms-teal; -fx-font-size: 13px;");
                        
                        String title = item.getIncident() != null ? item.getIncident().getTitle() : "Untitled Investigation";
                        Label info = new Label(title + " [" + item.getStatus() + "]");
                        info.setStyle("-fx-font-size: 11px; -fx-text-fill: #5A7A78;");
                        
                        cell.getChildren().addAll(id, info);
                        setGraphic(cell);
                    }
                }
            });

            combo.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(CaseFile cf) {
                    return cf == null ? "" : cf.getCaseNumber() + " — " + (cf.getIncident() != null ? cf.getIncident().getTitle() : "Unassigned");
                }
                @Override public CaseFile fromString(String s) { return null; }
            });
        }

        dialog.showAndWait().ifPresent(cf -> {
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() {
                    CourtCase cc = new CourtCase();
                    cc.setCourtCaseNumber("CRT-" + (System.currentTimeMillis() % 1000000));
                    cc.setCaseFile(cf);
                    cc.setStatus(com.cms.model.enums.CourtStatus.FILED);
                    cc.setFiledAt(java.time.LocalDate.now());
                    cc.setProsecutor(currentUser);
                    
                    courtService.saveCourtCase(cc);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                loadData();
                NexusAlert.showInfo("CASE FILED\n\nInvestigation " + cf.getCaseNumber() + " successfully linked to court.");
            });
            task.setOnFailed(e -> NexusAlert.showError("FILING FAILED\n\n" + task.getException().getMessage()));
            new Thread(task).start();
        });
    }

    @FXML
    private void handleScheduleHearing() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != Role.ADMINISTRATOR && currentUser.getRole() != Role.DETECTIVE)) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Investigators and Administrators can schedule hearings.");
            return;
        }

        CourtCase selected = courtCasesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NexusAlert.showWarning("SELECTION REQUIRED\n\nPlease select a court case to schedule a hearing.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(LocalDateTime.now().plusDays(1).format(formatter));
        dialog.setTitle("Nexus Judicial | Schedule Hearing");
        dialog.setHeaderText("JUDICIAL CALENDAR\nSet the next hearing date for " + selected.getCourtCaseNumber());
        dialog.setContentText("Format (YYYY-MM-DD HH:mm):");
        
        dialog.showAndWait().ifPresent(dateTimeStr -> {
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() throws Exception {
                    LocalDateTime hearingDate = LocalDateTime.parse(dateTimeStr, formatter);
                    CourtHearing hearing = new CourtHearing();
                    hearing.setCourtCase(selected);
                    hearing.setHearingDate(hearingDate);
                    hearing.setHearingStatus(com.cms.model.enums.HearingStatus.SCHEDULED);
                    hearing.setRecordedBy(currentUser);
                    courtService.saveHearing(hearing);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                loadData();
                NexusAlert.showInfo("HEARING SCHEDULED\n\nJudicial record updated for " + selected.getCourtCaseNumber());
            });
            task.setOnFailed(e -> NexusAlert.showError("INVALID FORMAT\n\nPlease use YYYY-MM-DD HH:mm (e.g. 2026-05-15 10:00)"));
            new Thread(task).start();
        });
    }

    @FXML
    private void handleRecordOutcome() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != Role.ADMINISTRATOR && currentUser.getRole() != Role.DETECTIVE)) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Investigators and Administrators can record outcomes.");
            return;
        }

        CourtHearing selected = hearingsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NexusAlert.showWarning("SELECTION REQUIRED\n\nPlease select a hearing to record the outcome.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Nexus Judicial | Record Outcome");
        dialog.setHeaderText("HEARING SUMMARY\nEnter the official outcome for hearing on " + selected.getHearingDate().format(formatter));
        dialog.setContentText("Outcome Description:");

        dialog.showAndWait().ifPresent(outcome -> {
            List<com.cms.model.enums.HearingStatus> statuses = List.of(
                com.cms.model.enums.HearingStatus.COMPLETED, 
                com.cms.model.enums.HearingStatus.ADJOURNED,
                com.cms.model.enums.HearingStatus.SCHEDULED
            );
            ChoiceDialog<com.cms.model.enums.HearingStatus> statusDialog = new ChoiceDialog<>(statuses.get(0), statuses);
            statusDialog.setTitle("Judicial Status");
            statusDialog.setHeaderText("Select the new status for this hearing:");
            
            statusDialog.showAndWait().ifPresent(status -> {
                javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                    @Override protected Void call() {
                        courtService.updateHearingOutcome(selected.getId(), outcome, status, null);
                        return null;
                    }
                };
                task.setOnSucceeded(e -> {
                    loadData();
                    NexusAlert.showInfo("RECORD UPDATED\n\nHearing outcome has been successfully logged.");
                });
                new Thread(task).start();
            });
        });
    }

    @FXML
    private void handleUpdateStatus() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != Role.ADMINISTRATOR && currentUser.getRole() != Role.DETECTIVE)) {
            NexusAlert.showWarning("ACCESS DENIED\n\nJudicial verdicts require Detective or Admin clearance.");
            return;
        }

        CourtCase selected = courtCasesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            NexusAlert.showWarning("SELECTION REQUIRED\n\nPlease select a court case to update.");
            return;
        }

        List<com.cms.model.enums.CourtStatus> statuses = List.of(
            com.cms.model.enums.CourtStatus.FILED,
            com.cms.model.enums.CourtStatus.ONGOING_TRIAL,
            com.cms.model.enums.CourtStatus.CONVICTED,
            com.cms.model.enums.CourtStatus.ACQUITTED,
            com.cms.model.enums.CourtStatus.CLOSED
        );
        ChoiceDialog<com.cms.model.enums.CourtStatus> dialog = new ChoiceDialog<>(selected.getStatus(), statuses);
        dialog.setTitle("Nexus Judicial | Case Status");
        dialog.setHeaderText("UPDATE LEGAL STATUS\nChange the current phase of court case " + selected.getCourtCaseNumber());
        
        dialog.showAndWait().ifPresent(status -> {
            TextInputDialog verdictDialog = new TextInputDialog(selected.getVerdict());
            verdictDialog.setTitle("Legal Verdict");
            verdictDialog.setHeaderText("Enter official verdict (Guilty/Acquitted/etc.):");
            
            verdictDialog.showAndWait().ifPresent(verdict -> {
                TextInputDialog sentenceDialog = new TextInputDialog(selected.getSentenceDetails());
                sentenceDialog.setTitle("Sentencing Details");
                sentenceDialog.setHeaderText("Enter sentencing or closure details:");
                
                sentenceDialog.showAndWait().ifPresent(sentence -> {
                    javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                        @Override protected Void call() {
                            courtService.updateCourtCaseStatus(selected.getId(), status, verdict, sentence);
                            return null;
                        }
                    };
                    task.setOnSucceeded(e -> {
                        loadData();
                        NexusAlert.showInfo("JUDICIAL VERDICT RECORDED\n\nCase " + selected.getCourtCaseNumber() + " status updated.");
                    });
                    new Thread(task).start();
                });
            });
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
