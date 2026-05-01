package com.cms.controller;

import com.cms.model.ArrestRecord;
import com.cms.model.CaseFile;
import com.cms.model.Person;
import com.cms.model.User;
import com.cms.service.ArrestService;
import com.cms.service.CaseService;
import com.cms.service.PersonService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class ArrestRegistrationController {
    private static final Logger logger = LoggerFactory.getLogger(ArrestRegistrationController.class);

    @FXML private TableView<ArrestRecord> arrestTable;
    @FXML private TableColumn<ArrestRecord, String> colBooking;
    @FXML private TableColumn<ArrestRecord, String> colSuspect;
    @FXML private TableColumn<ArrestRecord, String> colCase;
    @FXML private TableColumn<ArrestRecord, String> colOfficer;
    @FXML private TableColumn<ArrestRecord, String> colCharges;
    @FXML private TableColumn<ArrestRecord, String> colDate;
    @FXML private TableColumn<ArrestRecord, String> colLocation;
    @FXML private Label totalArrestsLabel;
    @FXML private Label monthArrestsLabel;
    @FXML private Label autoClosedLabel;

    private final ArrestService arrestService = new ArrestService();
    private final CaseService caseService = new CaseService();
    private final PersonService personService = new PersonService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTable();
        loadArrestsAsync();
        loadStatsAsync();
    }

    private void setupTable() {
        colBooking.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                ar.getBookingReference() != null ? ar.getBookingReference() : "N/A");
        });
        colSuspect.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            String name = "Unknown";
            try {
                if (ar.getSuspect() != null)
                    name = ar.getSuspect().getFirstName() + " " + ar.getSuspect().getLastName();
            } catch (Exception ignore) {}
            return new javafx.beans.property.SimpleStringProperty(name);
        });
        colCase.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            String cn = "";
            try {
                if (ar.getCaseFile() != null) cn = ar.getCaseFile().getCaseNumber();
            } catch (Exception ignore) {}
            return new javafx.beans.property.SimpleStringProperty(cn);
        });
        colOfficer.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            String on = "";
            try {
                if (ar.getArrestingOfficer() != null) on = ar.getArrestingOfficer().getFullName();
            } catch (Exception ignore) {}
            return new javafx.beans.property.SimpleStringProperty(on);
        });
        colCharges.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                ar.getCharges() != null ? ar.getCharges() : "");
        });
        colDate.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                ar.getArrestedAt() != null ? ar.getArrestedAt().format(formatter) : "");
        });
        colLocation.setCellValueFactory(cd -> {
            ArrestRecord ar = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(
                ar.getArrestLocation() != null ? ar.getArrestLocation() : "");
        });
    }

    private void loadArrestsAsync() {
        Task<List<ArrestRecord>> task = new Task<>() {
            @Override protected List<ArrestRecord> call() {
                return arrestService.findAll(500, 0);
            }
        };
        task.setOnSucceeded(e ->
            arrestTable.setItems(FXCollections.observableArrayList(task.getValue())));
        task.setOnFailed(e ->
            logger.error("Failed to load arrests", task.getException()));
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    private void loadStatsAsync() {
        Task<long[]> task = new Task<>() {
            @Override protected long[] call() {
                return new long[]{
                    arrestService.countAll(),
                    arrestService.countForCurrentMonth(),
                    arrestService.countAutoClosedCases()
                };
            }
        };
        task.setOnSucceeded(e -> {
            long[] stats = task.getValue();
            totalArrestsLabel.setText(String.valueOf(stats[0]));
            monthArrestsLabel.setText(String.valueOf(stats[1]));
            autoClosedLabel.setText(String.valueOf(stats[2]));
        });
        Thread th = new Thread(task); th.setDaemon(true); th.start();
    }

    @FXML
    private void handleNewArrest() {
        // Build a dialog to register a new arrest
        Dialog<ArrestRecord> dialog = new Dialog<>();
        dialog.setTitle("Register New Arrest");
        dialog.setHeaderText("Enter arrest details");

        ButtonType registerBtn = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(registerBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 10));

        // Case selection
        ComboBox<CaseFile> caseCombo = new ComboBox<>();
        List<CaseFile> cases = caseService.findAllCases();
        caseCombo.setItems(FXCollections.observableArrayList(cases));
        caseCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CaseFile cf) { return cf != null ? cf.getCaseNumber() : ""; }
            @Override public CaseFile fromString(String s) { return null; }
        });

        // Suspect selection (from persons in DB)
        ComboBox<Person> suspectCombo = new ComboBox<>();
        List<Person> persons = personService.findAll(200, 0);
        suspectCombo.setItems(FXCollections.observableArrayList(persons));
        suspectCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Person p) {
                return p != null ? p.getFirstName() + " " + p.getLastName() + " (ID: " + p.getId() + ")" : "";
            }
            @Override public Person fromString(String s) { return null; }
        });

        // Officer (current user by default)
        User currentUser = com.cms.service.SessionManager.getInstance().getCurrentUser();
        Label officerLabel = new Label(currentUser.getFullName());

        TextField chargesField = new TextField();
        chargesField.setPromptText("Enter charges (e.g., Murder, Robbery)");

        TextField locationField = new TextField();
        locationField.setPromptText("Arrest location");

        TextField custodyField = new TextField();
        custodyField.setPromptText("Custody location (e.g., Central Jail)");

        TextField bookingField = new TextField();
        bookingField.setPromptText("Booking reference (auto-generated if empty)");

        grid.add(new Label("Case:"), 0, 0);           grid.add(caseCombo, 1, 0);
        grid.add(new Label("Suspect:"), 0, 1);         grid.add(suspectCombo, 1, 1);
        grid.add(new Label("Arresting Officer:"), 0, 2); grid.add(officerLabel, 1, 2);
        grid.add(new Label("Charges:"), 0, 3);         grid.add(chargesField, 1, 3);
        grid.add(new Label("Arrest Location:"), 0, 4); grid.add(locationField, 1, 4);
        grid.add(new Label("Custody Location:"), 0, 5); grid.add(custodyField, 1, 5);
        grid.add(new Label("Booking Ref:"), 0, 6);     grid.add(bookingField, 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(550);

        // Disable register until case & suspect selected
        dialog.getDialogPane().lookupButton(registerBtn).setDisable(true);
        caseCombo.valueProperty().addListener((o, ov, nv) ->
            dialog.getDialogPane().lookupButton(registerBtn)
                  .setDisable(nv == null || suspectCombo.getValue() == null));
        suspectCombo.valueProperty().addListener((o, ov, nv) ->
            dialog.getDialogPane().lookupButton(registerBtn)
                  .setDisable(nv == null || caseCombo.getValue() == null));

        dialog.setResultConverter(button -> {
            if (button == registerBtn) {
                CaseFile selectedCase = caseCombo.getValue();
                Person selectedSuspect = suspectCombo.getValue();
                if (selectedCase == null || selectedSuspect == null) return null;

                String booking = bookingField.getText().isBlank() ?
                    "ARR-" + System.currentTimeMillis() : bookingField.getText();

                try {
                    return arrestService.registerArrest(
                        selectedCase.getId(),
                        selectedSuspect.getId(),
                        currentUser.getId(),
                        chargesField.getText(),
                        locationField.getText(),
                        custodyField.getText(),
                        booking
                    );
                } catch (Exception ex) {
                    Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR, "Arrest registration failed:\n" + ex.getMessage()).showAndWait());
                    return null;
                }
            }
            return null;
        });

        Optional<ArrestRecord> result = dialog.showAndWait();
        result.ifPresent(ar -> {
            new Alert(Alert.AlertType.INFORMATION,
                "Arrest registered successfully!\nBooking: " + ar.getBookingReference()).showAndWait();
            loadArrestsAsync();
            loadStatsAsync();
        });
    }
}
