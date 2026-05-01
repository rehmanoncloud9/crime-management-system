package com.cms.controller;

import com.cms.model.*;
import com.cms.model.enums.*;
import com.cms.service.PersonService;
import com.cms.service.HibernateUtil;
import com.cms.service.NavigationService;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.geometry.Pos;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

public class PersonDetailController {

    @FXML private ImageView photoView;
    @FXML private Circle statusCircle;
    @FXML private Label nameLabel;
    @FXML private Label nationalIdLabel;
    @FXML private Label classificationLabel;
    @FXML private Label riskLabel;

    @FXML private Label genderLabel;
    @FXML private Label dobLabel;
    @FXML private Label nationalityLabel;
    @FXML private Label warrantLabel;

    @FXML private Label heightLabel;
    @FXML private Label weightLabel;
    @FXML private Label marksLabel;
    @FXML private Label fullAddressLabel;
    @FXML private Label bloodGroupLabel;
    @FXML private Label dnaLabel;

    @FXML private TableView<CaseHistoryItem> caseTable;
    @FXML private TableColumn<CaseHistoryItem, String> caseNumCol;
    @FXML private TableColumn<CaseHistoryItem, String> caseRoleCol;
    @FXML private TableColumn<CaseHistoryItem, String> caseStatusCol;
    @FXML private TableColumn<CaseHistoryItem, String> caseDateCol;

    @FXML private TableView<ArrestRecord> arrestTable;
    @FXML private TableColumn<ArrestRecord, String> arrestRefCol;
    @FXML private TableColumn<ArrestRecord, String> arrestDateCol;
    @FXML private TableColumn<ArrestRecord, String> arrestLocationCol;

    @FXML private VBox timelineContainer;

    private final PersonService personService = new PersonService();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final DateTimeFormatter dateFormater = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Person currentPerson;

    @FXML
    public void initialize() {
        setupTables();
    }

    private void setupTables() {
        caseNumCol.setCellValueFactory(new PropertyValueFactory<>("caseNumber"));
        caseRoleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        caseStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        caseDateCol.setCellValueFactory(new PropertyValueFactory<>("date"));

        arrestRefCol.setCellValueFactory(new PropertyValueFactory<>("bookingReference"));
        arrestDateCol.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getArrestedAt().format(dateTimeFormatter)));
        arrestLocationCol.setCellValueFactory(new PropertyValueFactory<>("arrestLocation"));
    }

    public void loadPerson(Long personId) {
        Task<Person> task = new Task<>() {
            @Override
            protected Person call() throws Exception {
                return personService.findById(personId).orElseThrow(() -> new Exception("Person not found"));
            }
        };

        task.setOnSucceeded(e -> {
            this.currentPerson = task.getValue();
            updateUI();
            loadInvolvement();
        });
        
        task.setOnFailed(e -> {
            new Alert(Alert.AlertType.ERROR, "Failed to load person: " + task.getException().getMessage()).showAndWait();
        });

        new Thread(task).start();
    }

    private void updateUI() {
        nameLabel.setText(currentPerson.getFirstName() + " " + currentPerson.getLastName());
        nationalIdLabel.setText("CNIC: " + (currentPerson.getNationalId() != null ? currentPerson.getNationalId() : "N/A"));
        genderLabel.setText(currentPerson.getGender().name());
        dobLabel.setText(currentPerson.getDateOfBirth() != null ? currentPerson.getDateOfBirth().format(dateFormater) : "N/A");
        nationalityLabel.setText(currentPerson.getNationality() != null ? currentPerson.getNationality().getName() : "N/A");
        
        classificationLabel.setText(currentPerson.getPersonStatus().name());
        classificationLabel.getStyleClass().setAll("badge", getStatusBadgeClass(currentPerson.getPersonStatus()));
        
        warrantLabel.setText(currentPerson.isHasActiveWarrant() ? "ACTIVE WARRANT" : "NONE");
        warrantLabel.setStyle("-fx-text-fill: " + (currentPerson.isHasActiveWarrant() ? "#e11d48" : "#16a34a") + "; -fx-font-weight: bold;");

        heightLabel.setText(currentPerson.getHeightCm() != null ? currentPerson.getHeightCm() + " cm" : "N/A");
        weightLabel.setText(currentPerson.getWeightKg() != null ? currentPerson.getWeightKg() + " kg" : "N/A");
        marksLabel.setText(currentPerson.getMarksDisplay());

        StringBuilder addr = new StringBuilder();
        if (currentPerson.getArea() != null) addr.append(currentPerson.getArea().getName()).append(", ");
        if (currentPerson.getCity() != null) addr.append(currentPerson.getCity().getName()).append(", ");
        if (currentPerson.getDistrict() != null) addr.append(currentPerson.getDistrict().getName());
        if (currentPerson.getAddress() != null && !currentPerson.getAddress().isEmpty()) addr.append("\n").append(currentPerson.getAddress());
        fullAddressLabel.setText(addr.toString().isEmpty() ? "No address recorded" : addr.toString());

        statusCircle.setFill(currentPerson.isHasActiveWarrant() ? Color.web("#e11d48") : Color.web("#16a34a"));

        riskLabel.setText("RISK: " + (currentPerson.getRiskScore() != null ? currentPerson.getRiskScore() : "LOW"));
        riskLabel.getStyleClass().setAll("badge", getRiskBadgeClass(currentPerson.getRiskScore()));

        if (currentPerson.getPhoto() != null) {
            photoView.setImage(new Image(new ByteArrayInputStream(currentPerson.getPhoto())));
        }

        if (currentPerson.getMedicalRecord() != null) {
            bloodGroupLabel.setText(currentPerson.getMedicalRecord().getBloodGroup().toString());
            dnaLabel.setText(currentPerson.getMedicalRecord().getDnaProfile() != null && !currentPerson.getMedicalRecord().getDnaProfile().isEmpty() ? 
                currentPerson.getMedicalRecord().getDnaProfile() : "NOT ON FILE");
        }
    }

    private String getStatusBadgeClass(PersonStatus status) {
        if (status == null) return "badge-pending";
        return switch (status) {
            case CRIMINAL -> "badge-closed";
            case VICTIM, WITNESS -> "badge-active";
            default -> "badge-pending";
        };
    }

    private String getRiskBadgeClass(RiskScore risk) {
        if (risk == null) return "badge-active";
        return switch (risk) {
            case HIGH -> "badge-closed";
            case MEDIUM -> "badge-pending";
            case LOW -> "badge-active";
            default -> "badge-pending";
        };
    }

    private void loadInvolvement() {
        Task<InvolvementData> task = new Task<>() {
            @Override
            protected InvolvementData call() {
                InvolvementData data = new InvolvementData();
                HibernateUtil.executeTransaction(session -> {
                    data.arrests = session.createQuery("FROM ArrestRecord WHERE suspect.id = :id", ArrestRecord.class)
                            .setParameter("id", currentPerson.getId())
                            .list();

                    List<CaseFile> allCases = session.createQuery(
                            "FROM CaseFile c " +
                            "LEFT JOIN FETCH c.caseSuspects cs LEFT JOIN FETCH cs.person " +
                            "LEFT JOIN FETCH c.caseVictims cv LEFT JOIN FETCH cv.person " +
                            "LEFT JOIN FETCH c.caseWitnesses cw LEFT JOIN FETCH cw.person", CaseFile.class).list();
                    for (CaseFile cf : allCases) {
                        if (cf.getSuspects().contains(currentPerson)) data.cases.add(new CaseHistoryItem(cf, "SUSPECT"));
                        else if (cf.getVictims().contains(currentPerson)) data.cases.add(new CaseHistoryItem(cf, "VICTIM"));
                        else if (cf.getWitnesses().contains(currentPerson)) data.cases.add(new CaseHistoryItem(cf, "WITNESS"));
                    }
                    return null;
                });
                return data;
            }
        };

        task.setOnSucceeded(e -> {
            InvolvementData data = task.getValue();
            caseTable.setItems(FXCollections.observableArrayList(data.cases));
            arrestTable.setItems(FXCollections.observableArrayList(data.arrests));
            buildTimeline(data);
        });

        new Thread(task).start();
    }

    private void buildTimeline(InvolvementData data) {
        timelineContainer.getChildren().clear();
        List<TimelineItem> items = new ArrayList<>();

        if (currentPerson.getCreatedAt() != null) {
            items.add(new TimelineItem("Profile Created", currentPerson.getCreatedAt().format(dateTimeFormatter), "👤", "Registration record established in system."));
        }
        
        for (CaseHistoryItem ci : data.cases) {
            items.add(new TimelineItem("Linked to Case: " + ci.caseNumber, ci.date, "📁", "Involved as " + ci.role + ". Case status: " + ci.status));
        }

        for (ArrestRecord ar : data.arrests) {
            items.add(new TimelineItem("Arrested", ar.getArrestedAt().format(dateTimeFormatter), "🚔", "Taken into custody at " + ar.getArrestLocation() + ". Ref: " + ar.getBookingReference()));
        }

        items.sort(Comparator.comparing(TimelineItem::timestamp).reversed());
        for (TimelineItem ti : items) {
            addTimelineEntry(ti);
        }
    }

    private void addTimelineEntry(TimelineItem ti) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("timeline-row");

        Label icon = new Label(ti.icon);
        icon.setStyle("-fx-font-size: 20px; -fx-min-width: 30;");

        VBox content = new VBox(4);
        Label title = new Label(ti.title);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label time = new Label(ti.timestamp);
        time.getStyleClass().add("text-muted");
        time.setStyle("-fx-font-size: 11px;");
        Label desc = new Label(ti.description);
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px;");
        
        content.getChildren().addAll(title, time, desc);
        row.getChildren().addAll(icon, content);
        timelineContainer.getChildren().add(row);
    }

    @FXML
    private void handleUpdateStatus() {
        if (currentPerson == null) return;

        List<PersonStatus> statuses = List.of(PersonStatus.values());
        ChoiceDialog<PersonStatus> dialog = new ChoiceDialog<>(currentPerson.getPersonStatus(), statuses);
        dialog.setTitle("Update Status");
        dialog.setHeaderText("Update Classification for " + currentPerson.getFirstName());
        dialog.setContentText("Select new status:");

        dialog.showAndWait().ifPresent(newStatus -> {
            currentPerson.setPersonStatus(newStatus);
            
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() {
                    personService.save(currentPerson);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                updateUI();
                new Alert(Alert.AlertType.INFORMATION, "Status updated to " + newStatus).showAndWait();
            });
            new Thread(task).start();
        });
    }

    @FXML
    private void handleLinkToCase() {
        NavigationService.getInstance().navigateTo("Link to Case", "/fxml/modules/LinkCaseDialog.fxml", controller -> {
            if (controller instanceof com.cms.controller.LinkCaseController linkCtrl) {
                linkCtrl.setPerson(currentPerson);
                linkCtrl.setOnLinkSucceeded(this::loadInvolvement);
            }
        });
    }

    @FXML
    private void handleEditProfile() {
        if (currentPerson == null) return;
        NavigationService.getInstance().navigateTo("Edit Profile: " + currentPerson.getFirstName(), "/fxml/modules/PersonRegistration.fxml", (controller) -> {
            if (controller instanceof PersonRegistrationController prc) {
                prc.loadPersonRecord(currentPerson.getId());
            }
        });
    }

    public static class InvolvementData {
        List<CaseHistoryItem> cases = new ArrayList<>();
        List<ArrestRecord> arrests = new ArrayList<>();
    }

    public static class CaseHistoryItem {
        private String caseNumber;
        private String role;
        private String status;
        private String date;

        public CaseHistoryItem(CaseFile cf, String role) {
            this.caseNumber = cf.getCaseNumber();
            this.role = role;
            this.status = cf.getStatus().name();
            this.date = cf.getOpenedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        public String getCaseNumber() { return caseNumber; }
        public String getRole() { return role; }
        public String getStatus() { return status; }
        public String getDate() { return date; }
    }

    private record TimelineItem(String title, String timestamp, String icon, String description) {}
}
