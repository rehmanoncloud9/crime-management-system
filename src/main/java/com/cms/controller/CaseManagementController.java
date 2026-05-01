package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.Person;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Pos;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CaseManagementController {
    @FXML private TextField searchField;
    @FXML private ListView<CaseFile> caseListView;
    @FXML private VBox detailView;
    @FXML private Label detailCaseNumber;
    @FXML private Label detailIncidentTitle;
    @FXML private Label labelStatus;
    @FXML private Label labelInvestigator;
    @FXML private Label labelOpened;
    @FXML private Label labelCrimeType;
    @FXML private Label labelDescription;
    @FXML private VBox timelineContainer;
    @FXML private FlowPane suspectFlowPane;
    @FXML private TableView<com.cms.model.Evidence> evidenceTable;
    @FXML private TableColumn<com.cms.model.Evidence, String> evidenceNumCol;
    @FXML private TableColumn<com.cms.model.Evidence, String> evidenceTypeCol;
    @FXML private TableColumn<com.cms.model.Evidence, String> evidenceDescCol;
    @FXML private TableColumn<com.cms.model.Evidence, String> evidenceStatusCol;
    @FXML private Button startInvestigateBtn;
    @FXML private Button closeCaseBtn;



    private final com.cms.service.CaseService caseService = new com.cms.service.CaseService();
    private final com.cms.service.EvidenceService evidenceService = new com.cms.service.EvidenceService();
    private final com.cms.service.PersonService personService = new com.cms.service.PersonService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    private CaseFile selectedCase;

    @FXML
    public void initialize() {
        setupListView();
        setupEvidenceTable();
        loadCasesAsync();
    }

    private void setupListView() {
        caseListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(CaseFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    VBox box = new VBox(4);
                    Label num = new Label(item.getCaseNumber());
                    num.setStyle("-fx-font-weight: bold;");
                    Label status = new Label(item.getStatus().toString());
                    status.setStyle("-fx-font-size: 10px; -fx-text-fill: #666;");
                    box.getChildren().addAll(num, status);
                    setGraphic(box);
                }
            }
        });

        caseListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showCaseDetails(newVal);
            }
        });
    }

    private void setupEvidenceTable() {
        evidenceNumCol.setCellValueFactory(new PropertyValueFactory<>("evidenceNumber"));
        evidenceTypeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        evidenceDescCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        evidenceStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void loadCasesAsync() {
        Task<List<CaseFile>> task = new Task<>() {
            @Override
            protected List<CaseFile> call() {
                return caseService.findAllCases();
            }
        };
        task.setOnSucceeded(e -> caseListView.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    private void showCaseDetails(CaseFile selected) {
        detailView.setDisable(false);
        detailCaseNumber.setText(selected.getCaseNumber());
        detailIncidentTitle.setText(selected.getIncident() != null ? selected.getIncident().getTitle() : "N/A");
        
        labelStatus.setText(selected.getStatus().name());
        labelInvestigator.setText(selected.getPrimaryInvestigator() != null ? selected.getPrimaryInvestigator().getFullName() : "UNASSIGNED");
        labelOpened.setText(selected.getOpenedAt().format(formatter));
        labelCrimeType.setText(selected.getIncident() != null && selected.getIncident().getCrimeType() != null ? selected.getIncident().getCrimeType().getName() : "N/A");
        labelDescription.setText(selected.getIncident() != null ? selected.getIncident().getDescription() : "");

        com.cms.model.enums.Role currentRole = com.cms.service.SessionManager.getInstance().getCurrentUser().getRole();
        if (currentRole == com.cms.model.enums.Role.ANALYST) {
            startInvestigateBtn.setDisable(true);
            closeCaseBtn.setDisable(true);
        } else {
            startInvestigateBtn.setDisable(selected.getStatus() != com.cms.model.enums.IncidentStatus.OPEN);
            closeCaseBtn.setDisable(selected.getStatus() == com.cms.model.enums.IncidentStatus.CLOSED);
        }

        this.selectedCase = selected;
        buildTimeline(selected);
        loadEvidenceAndSuspects(selected);
    }

    private void buildTimeline(CaseFile selected) {
        timelineContainer.getChildren().clear();
        var incident = selected.getIncident();
        if (incident == null) return;

        addTimelineItem("Incident Occurred", incident.getOccurredAt(), "🚨");
        addTimelineItem("Incident Reported", incident.getReportedAt(), "📞");
        addTimelineItem("Case Opened", selected.getOpenedAt(), "📁");
        
        // We'll add evidence items later after fetch
    }

    private void addTimelineItem(String title, java.time.LocalDateTime dt, String icon) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        
        VBox dotLine = new VBox();
        dotLine.setAlignment(Pos.CENTER);
        Circle dot = new Circle(6, Color.web("#3f51b5"));
        Line line = new Line(0, 0, 0, 40);
        line.setStroke(Color.LIGHTGRAY);
        dotLine.getChildren().addAll(dot, line);

        VBox content = new VBox(2);
        Label lblTitle = new Label(icon + " " + title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblTime = new Label(dt != null ? dt.format(formatter) : "N/A");
        lblTime.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        content.getChildren().addAll(lblTitle, lblTime);

        item.getChildren().addAll(dotLine, content);
        timelineContainer.getChildren().add(item);
    }

    private void loadEvidenceAndSuspects(CaseFile selected) {
        Task<List<com.cms.model.Evidence>> task = new Task<>() {
            @Override
            protected List<com.cms.model.Evidence> call() {
                return evidenceService.findByCase(selected.getId(), 100, 0);
            }
        };
        task.setOnSucceeded(e -> {
            List<com.cms.model.Evidence> evidenceList = task.getValue();
            evidenceTable.setItems(FXCollections.observableArrayList(evidenceList));
            
            // Update timeline with evidence
            for (var ev : evidenceList) {
                addTimelineItem("Evidence Collected: " + ev.getType(), ev.getCollectedAt(), "🔍");
            }
            if (selected.getStatus() == com.cms.model.enums.IncidentStatus.CLOSED) {
                addTimelineItem("Case Closed", selected.getClosedAt(), "✅");
            }

            // Load all involved persons
            populatePersonCards(selected);
        });
        new Thread(task).start();
    }

    private void populatePersonCards(CaseFile cf) {
        suspectFlowPane.getChildren().clear();
        
        // Add Suspects
        for (var p : cf.getSuspects()) {
            suspectFlowPane.getChildren().add(buildPersonCard(p, "SUSPECT", "#fee2e2", "#991b1b"));
        }
        // Add Victims
        for (var p : cf.getVictims()) {
            suspectFlowPane.getChildren().add(buildPersonCard(p, "VICTIM", "#dcfce7", "#166534"));
        }
        // Add Witnesses
        for (var p : cf.getWitnesses()) {
            suspectFlowPane.getChildren().add(buildPersonCard(p, "WITNESS", "#fef9c3", "#854d0e"));
        }
    }

    private VBox buildPersonCard(Person person, String role, String bgColor, String textColor) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-padding: 12; -fx-alignment: center;");
        card.setPrefWidth(160);

        ImageView iv = new ImageView();
        iv.setFitWidth(60);
        iv.setFitHeight(60);
        if (person.getPhoto() != null) {
            iv.setImage(new Image(new ByteArrayInputStream(person.getPhoto())));
        } else {
            // Default avatar placeholder could be added here
        }
        iv.setClip(new Circle(30, 30, 30));

        Label name = new Label(person.getFirstName() + " " + person.getLastName());
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        Label roleLbl = new Label(role);
        roleLbl.setStyle("-fx-font-size: 9px; -fx-padding: 2 6; -fx-background-radius: 4; " + 
                         "-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + ";");

        card.getChildren().addAll(iv, name, roleLbl);
        return card;
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText();
        Task<List<CaseFile>> task = new Task<>() {
            @Override
            protected List<CaseFile> call() {
                return caseService.searchCases(keyword);
            }
        };
        task.setOnSucceeded(e -> caseListView.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML
    private void handleNewCase() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New Case");
        dialog.setHeaderText("Link to Incident");
        dialog.setContentText("Enter the Incident ID to upgrade to a Case:");
        dialog.showAndWait().ifPresent(incidentIdStr -> {
            try {
                Long incidentId = Long.parseLong(incidentIdStr);
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        caseService.createNewCaseFromIncident(incidentId,
                            com.cms.service.SessionManager.getInstance().getCurrentUser());
                        return null;
                    }
                };
                task.setOnSucceeded(e -> loadCasesAsync());
                new Thread(task).start();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR, "Invalid Input").showAndWait();
            }
        });
    }

    @FXML
    private void handleStartInvestigation() {
        CaseFile selected = caseListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        List<com.cms.model.User> freeOfficers;
        try {
            freeOfficers = caseService.findAvailableInvestigators(200);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Failed to load officers: " + e.getMessage()).showAndWait();
            return;
        }

        if (freeOfficers.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No free officers available. All are on active investigations.").showAndWait();
            return;
        }

        // Show officer choice dialog
        ChoiceDialog<com.cms.model.User> dialog = new ChoiceDialog<>(freeOfficers.get(0), freeOfficers);
        dialog.setTitle("Assign Investigator");
        dialog.setHeaderText("Select a free officer to assign as Primary Investigator");
        dialog.setContentText("Officer:");

        dialog.showAndWait().ifPresent(officer -> {
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    caseService.assignInvestigatorAndStart(selected.getId(), officer.getId());
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                new Alert(Alert.AlertType.INFORMATION,
                    "Investigation started!\nAssigned: " + officer.getFullName()).showAndWait();
                loadCasesAsync();
            });
            task.setOnFailed(e ->
                new Alert(Alert.AlertType.ERROR, "Failed: " + task.getException().getMessage()).showAndWait());
            Thread th = new Thread(task); th.setDaemon(true); th.start();
        });
    }

    @FXML
    private void handleLinkSuspect() {
        if (selectedCase == null) return;
        
        // Step 1: Pick Role
        List<com.cms.model.enums.PersonStatus> roles = List.of(
            com.cms.model.enums.PersonStatus.SUSPECT, 
            com.cms.model.enums.PersonStatus.VICTIM, 
            com.cms.model.enums.PersonStatus.WITNESS
        );
        ChoiceDialog<com.cms.model.enums.PersonStatus> roleDialog = new ChoiceDialog<>(com.cms.model.enums.PersonStatus.SUSPECT, roles);
        roleDialog.setTitle("Select Role");
        roleDialog.setContentText("Link person as:");
        
        roleDialog.showAndWait().ifPresent(role -> {
            // Step 2: Pick Person
            List<Person> allPeople = personService.findAll(100, 0);
            ChoiceDialog<Person> personDialog = new ChoiceDialog<>(null, allPeople);
            personDialog.setTitle("Link Person to Case");
            personDialog.setHeaderText("Select a person to link as " + role);
            personDialog.setContentText("Person:");

            personDialog.showAndWait().ifPresent(person -> {
                caseService.addPersonToCase(selectedCase.getId(), person.getId(), role);
                // Refresh
                caseService.findAllCases().stream()
                    .filter(c -> c.getId().equals(selectedCase.getId()))
                    .findFirst().ifPresent(this::showCaseDetails);
            });
        });
    }

    @FXML
    private void handleCloseCase() {
        CaseFile selected = caseListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Close Case");
        dialog.setContentText("Reason for closure:");
        dialog.showAndWait().ifPresent(reason -> {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    caseService.updateCaseStatus(selected.getId(), com.cms.model.enums.IncidentStatus.CLOSED, reason);
                    return null;
                }
            };
            task.setOnSucceeded(e -> loadCasesAsync());
            new Thread(task).start();
        });
    }
}