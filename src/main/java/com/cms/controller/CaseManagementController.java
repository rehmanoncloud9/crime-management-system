package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.Person;
import com.cms.model.enums.Role;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import com.cms.util.NexusAlert;
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
        task.setOnFailed(e -> { /* silently log */ });
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    private void showCaseDetails(CaseFile selected) {
        // Fetch full detail with all relationships within a Hibernate session
        javafx.concurrent.Task<CaseFile> detailTask = new javafx.concurrent.Task<>() {
            @Override
            protected CaseFile call() {
                return caseService.findCaseDetailById(selected.getId());
            }
        };
        detailTask.setOnSucceeded(e -> {
            CaseFile detailed = detailTask.getValue();
            if (detailed == null) {
                detailed = selected; // fallback
            }
            renderCaseDetails(detailed);
        });
        detailTask.setOnFailed(e -> {
            // Fallback: render whatever we have
            renderCaseDetails(selected);
        });
        Thread th = new Thread(detailTask);
        th.setDaemon(true);
        th.start();
    }

    private void renderCaseDetails(CaseFile cf) {
        detailView.setDisable(false);
        this.selectedCase = cf;

        detailCaseNumber.setText(cf.getCaseNumber());
        try { detailIncidentTitle.setText(cf.getIncident() != null ? cf.getIncident().getTitle() : "N/A"); } catch (Exception ex) { detailIncidentTitle.setText("N/A"); }
        try { labelStatus.setText(cf.getStatus().name()); } catch (Exception ex) { labelStatus.setText("UNKNOWN"); }
        try { labelInvestigator.setText(cf.getPrimaryInvestigator() != null ? cf.getPrimaryInvestigator().getFullName() : "UNASSIGNED"); } catch (Exception ex) { labelInvestigator.setText("UNASSIGNED"); }
        try { labelOpened.setText(cf.getOpenedAt() != null ? cf.getOpenedAt().format(formatter) : "N/A"); } catch (Exception ex) { labelOpened.setText("N/A"); }
        try { labelCrimeType.setText(cf.getIncident() != null && cf.getIncident().getCrimeType() != null ? cf.getIncident().getCrimeType().getName() : "N/A"); } catch (Exception ex) { labelCrimeType.setText("N/A"); }
        try { labelDescription.setText(cf.getIncident() != null ? cf.getIncident().getDescription() : ""); } catch (Exception ex) { labelDescription.setText(""); }

        com.cms.model.User currentUser = com.cms.service.SessionManager.getInstance().getCurrentUser();
        com.cms.model.enums.Role currentRole = currentUser != null ? currentUser.getRole() : null;
        
        // RBAC: Officers can start investigations, but only Investigators/Admins can CLOSE them.
        boolean canStart = currentRole == Role.ADMINISTRATOR || currentRole == Role.DETECTIVE || currentRole == Role.OFFICER;
        boolean canClose = currentRole == Role.ADMINISTRATOR || currentRole == Role.DETECTIVE;
        
        startInvestigateBtn.setDisable(!canStart || cf.getStatus() != com.cms.model.enums.CaseStatus.OPEN);
        closeCaseBtn.setDisable(!canClose);
        
        if (canClose) {
            boolean isClosed = cf.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_CONVICTED ||
                               cf.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_ACQUITTED ||
                               cf.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED;
            closeCaseBtn.setDisable(isClosed);
        }

        buildTimeline(cf);
        loadEvidenceAndSuspects(cf);
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
            boolean isClosed = selected.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_CONVICTED ||
                               selected.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_ACQUITTED ||
                               selected.getStatus() == com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED;
            if (isClosed) {
                addTimelineItem("Case Closed", selected.getClosedAt(), "✅");
            }

            // Load all involved persons
            populatePersonCards(selected);
        });
        Thread th = new Thread(task); th.setDaemon(true); th.start();
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
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    @FXML
    private void handleNewCase() {
        com.cms.model.User currentUser = com.cms.service.SessionManager.getInstance().getCurrentUser();
        if (currentUser.getRole() == Role.ANALYST) {
            NexusAlert.showWarning("ACCESS DENIED\n\nAnalysts cannot initiate new criminal cases.");
            return;
        }

        com.cms.service.IncidentService incidentService = new com.cms.service.IncidentService();
        List<com.cms.model.CrimeIncident> unlinked = incidentService.getUnlinkedIncidents();
        
        if (unlinked.isEmpty()) {
            NexusAlert.showWarning("CLEAR STATUS\n\nNo unlinked field reports found. All incidents have been assigned to active cases.");
            return;
        }

        // --- CUSTOM PREMIUM DIALOG ---
        Dialog<com.cms.model.CrimeIncident> dialog = new Dialog<>();
        dialog.setTitle("Nexus Command | Case Initiation");
        dialog.setHeaderText("INTELLIGENCE UPGRADE\nSelect a field incident to promote to a formal investigation.");
        
        // Style the dialog pane
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("dialog-pane");
        dialogPane.setPrefWidth(500);

        ButtonType initiateBtnType = new ButtonType("Initiate Case", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(initiateBtnType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));
        
        Label lbl = new Label("Select Active Field Incident:");
        lbl.getStyleClass().add("form-label-premium");
        
        ComboBox<com.cms.model.CrimeIncident> incidentCombo = new ComboBox<>(FXCollections.observableArrayList(unlinked));
        incidentCombo.setMaxWidth(Double.MAX_VALUE);
        incidentCombo.setPrefHeight(45);
        incidentCombo.getStyleClass().add("combo-box");

        // Premium Cell Factory for the Dropdown
        incidentCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.cms.model.CrimeIncident item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    VBox cell = new VBox(2);
                    Label id = new Label(item.getIncidentNumber());
                    id.setStyle("-fx-font-weight: 900; -fx-text-fill: -cms-teal; -fx-font-size: 13px;");
                    
                    String title = item.getTitle() != null ? item.getTitle() : "Untitled Incident";
                    String date = item.getOccurredAt() != null ? item.getOccurredAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "N/A";
                    Label info = new Label(title + " — " + date);
                    info.setStyle("-fx-font-size: 11px; -fx-text-fill: #5A7A78;");
                    
                    cell.getChildren().addAll(id, info);
                    setGraphic(cell);
                }
            }
        });

        // String Converter for the selection area
        incidentCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(com.cms.model.CrimeIncident ci) {
                return ci == null ? "" : ci.getIncidentNumber() + " — " + (ci.getTitle() != null ? ci.getTitle() : "Unassigned");
            }
            @Override public com.cms.model.CrimeIncident fromString(String s) { return null; }
        });

        incidentCombo.getSelectionModel().selectFirst();
        content.getChildren().addAll(lbl, incidentCombo);
        dialogPane.setContent(content);

        dialog.setResultConverter(btn -> btn == initiateBtnType ? incidentCombo.getValue() : null);

        dialog.showAndWait().ifPresent(selectedInc -> {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    caseService.createNewCaseFromIncident(selectedInc.getId(), currentUser);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                loadCasesAsync();
                NexusAlert.showInfo("INVESTIGATION INITIATED\n\nCase assigned to: " + selectedInc.getIncidentNumber());
            });
            task.setOnFailed(e -> NexusAlert.showError("REGISTRY FAILURE\n\nFailed to link case: " + task.getException().getMessage()));
            Thread th = new Thread(task); th.setDaemon(true); th.start();
        });
    }

    @FXML
    private void handleStartInvestigation() {
        Role role = com.cms.service.SessionManager.getInstance().getCurrentUser().getRole();
        if (role == Role.ANALYST) {
            NexusAlert.showWarning("READ-ONLY\n\nAnalysts cannot assign investigators.");
            return;
        }

        CaseFile selected = caseListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        List<com.cms.model.User> freeOfficers;
        try {
            freeOfficers = caseService.findAvailableInvestigators(200);
        } catch (Exception e) {
            NexusAlert.showError("Failed to load officers: " + e.getMessage());
            return;
        }

        if (freeOfficers.isEmpty()) {
            NexusAlert.showWarning("No free officers available. All are on active investigations.");
            return;
        }

        // --- CUSTOM PREMIUM ASSIGNMENT DIALOG ---
        Dialog<com.cms.model.User> dialog = new Dialog<>();
        dialog.setTitle("Nexus Command | Assignment");
        dialog.setHeaderText("PERSONNEL DEPLOYMENT\nSelect a certified officer for primary case investigation.");
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStyleClass().add("dialog-pane");
        dialogPane.setPrefWidth(450);

        ButtonType assignBtnType = new ButtonType("Assign Officer", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(assignBtnType, ButtonType.CANCEL);

        VBox content = new VBox(15);
        content.setPadding(new javafx.geometry.Insets(20));
        
        Label lbl = new Label("Available Field Investigators:");
        lbl.getStyleClass().add("form-label-premium");
        
        ComboBox<com.cms.model.User> officerCombo = new ComboBox<>(FXCollections.observableArrayList(freeOfficers));
        officerCombo.setMaxWidth(Double.MAX_VALUE);
        officerCombo.setPrefHeight(45);
        officerCombo.getStyleClass().add("combo-box");

        // Premium Cell Factory
        officerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(com.cms.model.User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    VBox cell = new VBox(2);
                    Label name = new Label(item.getFullName());
                    name.setStyle("-fx-font-weight: 800; -fx-text-fill: -cms-teal;");
                    
                    Label rank = new Label(item.getRole().name() + " — Badge #" + (item.getId() + 1000));
                    rank.setStyle("-fx-font-size: 10px; -fx-text-fill: -cms-t3;");
                    
                    cell.getChildren().addAll(name, rank);
                    setGraphic(cell);
                }
            }
        });

        officerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(com.cms.model.User u) { return u == null ? "" : u.getFullName(); }
            @Override public com.cms.model.User fromString(String s) { return null; }
        });

        officerCombo.getSelectionModel().selectFirst();
        content.getChildren().addAll(lbl, officerCombo);
        dialogPane.setContent(content);

        dialog.setResultConverter(btn -> btn == assignBtnType ? officerCombo.getValue() : null);

        dialog.showAndWait().ifPresent(officer -> {
            Task<Void> task = new Task<>() {
                @Override protected Void call() {
                    caseService.assignInvestigatorAndStart(selected.getId(), officer.getId());
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                NexusAlert.showInfo("INVESTIGATION DEPLOYED\n\nPrimary Investigator: " + officer.getFullName());
                loadCasesAsync();
                showCaseDetails(selected); 
            });
            task.setOnFailed(e ->
                NexusAlert.showError("DEPLOYMENT FAILURE\n\n" + task.getException().getMessage()));
            Thread th = new Thread(task); th.setDaemon(true); th.start();
        });
    }

    @FXML
    private void handleLinkSuspect() {
        if (selectedCase == null) return;
        
        // Step 1: Pick Role (Simple Choice is okay for enums usually, but let's be consistent)
        List<com.cms.model.enums.PersonStatus> roles = List.of(
            com.cms.model.enums.PersonStatus.SUSPECT, 
            com.cms.model.enums.PersonStatus.VICTIM, 
            com.cms.model.enums.PersonStatus.WITNESS
        );
        ChoiceDialog<com.cms.model.enums.PersonStatus> roleDialog = new ChoiceDialog<>(com.cms.model.enums.PersonStatus.SUSPECT, roles);
        roleDialog.setTitle("Role Selection");
        roleDialog.setHeaderText("Specify Legal Status");
        roleDialog.setContentText("Link person as:");
        
        roleDialog.showAndWait().ifPresent(role -> {
            // Step 2: Pick Person (High Fidelity)
            List<Person> allPeople = personService.findAll(200, 0);
            
            Dialog<Person> personDialog = new Dialog<>();
            personDialog.setTitle("Link Person to Case");
            personDialog.setHeaderText("REGISTRY SEARCH\nSelect a civilian to link as " + role);
            
            DialogPane dp = personDialog.getDialogPane();
            dp.getStyleClass().add("dialog-pane");
            dp.setPrefWidth(450);

            ButtonType linkBtn = new ButtonType("Link to Case", ButtonBar.ButtonData.OK_DONE);
            dp.getButtonTypes().addAll(linkBtn, ButtonType.CANCEL);

            ComboBox<Person> personCombo = new ComboBox<>(FXCollections.observableArrayList(allPeople));
            personCombo.setMaxWidth(Double.MAX_VALUE);
            personCombo.setPrefHeight(45);
            personCombo.getStyleClass().add("combo-box");

            personCombo.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Person p, boolean empty) {
                    super.updateItem(p, empty);
                    if (empty || p == null) setGraphic(null);
                    else {
                        VBox cell = new VBox(2);
                        Label name = new Label(p.getFullName());
                        name.setStyle("-fx-font-weight: 800; -fx-text-fill: -cms-teal;");
                        Label cnic = new Label("CNIC: " + (p.getCnic() != null ? p.getCnic() : "Unknown"));
                        cnic.setStyle("-fx-font-size: 10px; -fx-text-fill: -cms-t3;");
                        cell.getChildren().addAll(name, cnic);
                        setGraphic(cell);
                    }
                }
            });
            personCombo.setConverter(new javafx.util.StringConverter<>() {
                @Override public String toString(Person p) { return p == null ? "" : p.getFullName(); }
                @Override public Person fromString(String s) { return null; }
            });

            VBox box = new VBox(10, new Label("Select Person:"), personCombo);
            box.setPadding(new javafx.geometry.Insets(20));
            dp.setContent(box);

            personDialog.setResultConverter(b -> b == linkBtn ? personCombo.getValue() : null);

            personDialog.showAndWait().ifPresent(person -> {
                javafx.concurrent.Task<Void> linkTask = new javafx.concurrent.Task<>() {
                    @Override protected Void call() {
                        caseService.addPersonToCase(selectedCase.getId(), person.getId(), role);
                        return null;
                    }
                };
                linkTask.setOnSucceeded(ev -> showCaseDetails(selectedCase));
                linkTask.setOnFailed(ev -> NexusAlert.showError("REGISTRY FAILURE\n\nLink Failed: " + linkTask.getException().getMessage()));
                Thread th = new Thread(linkTask); th.setDaemon(true); th.start();
            });
        });
    }

    @FXML
    private void handleCloseCase() {
        Role role = com.cms.service.SessionManager.getInstance().getCurrentUser().getRole();
        if (role != Role.ADMINISTRATOR && role != Role.DETECTIVE) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Investigators and Administrators can close cases.");
            return;
        }

        CaseFile selected = caseListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Close Case");
        dialog.setContentText("Reason for closure:");
        dialog.showAndWait().ifPresent(reason -> {
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    caseService.updateCaseStatus(selected.getId(), com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED, reason);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                loadCasesAsync();
                showCaseDetails(selected);
                NexusAlert.showInfo("Case has been successfully closed.");
            });
            Thread th = new Thread(task); th.setDaemon(true); th.start();
        });
    }
}
