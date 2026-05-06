package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.Evidence;
import com.cms.model.Person;
import com.cms.model.enums.EvidenceStatus;
import com.cms.model.enums.EvidenceType;
import com.cms.service.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class EvidenceLogController {
    private static final Logger logger = LoggerFactory.getLogger(EvidenceLogController.class);

    @FXML private ComboBox<CaseFile>  caseCombo;
    @FXML private ComboBox<Person>    suspectCombo;
    @FXML private ComboBox<EvidenceType> typeCombo;
    @FXML private TextArea            descriptionArea;
    @FXML private TextField           locationField;
    @FXML private TextField           storageField;

    // Table columns (evidence list)
    @FXML private TableView<Evidence>          evidenceTable;
    @FXML private TableColumn<Evidence,String> colEvidNum;
    @FXML private TableColumn<Evidence,String> colCase;
    @FXML private TableColumn<Evidence,String> colType;
    @FXML private TableColumn<Evidence,String> colStatus;
    @FXML private TableColumn<Evidence,String> colLocation;

    private final CaseService     caseService     = new CaseService();
    private final EvidenceService evidenceService = new EvidenceService();
    private final PersonService   personService   = new PersonService();

    @FXML
    public void initialize() {
        // Populate type combo immediately
        typeCombo.setItems(FXCollections.observableArrayList(EvidenceType.values()));

        setupConverters();
        loadCases();
        loadAllPersons();
        setupTableColumns();
        loadEvidenceTable();

        // When case changes, filter suspects from that case's suspect list
        caseCombo.valueProperty().addListener((obs, old, cf) -> {
            if (cf != null) loadSuspectsForCase(cf);
        });
    }

    private void loadCases() {
        Task<List<CaseFile>> t = new Task<>() {
            @Override protected List<CaseFile> call() { return caseService.findAllCases(); }
        };
        t.setOnSucceeded(e -> {
            caseCombo.setItems(FXCollections.observableArrayList(t.getValue()));
            if (!t.getValue().isEmpty())
                logger.info("Evidence form: loaded {} cases", t.getValue().size());
        });
        t.setOnFailed(e -> logger.error("Failed to load cases for evidence form", t.getException()));
        new Thread(t).start();
    }

    private void loadAllPersons() {
        Task<List<Person>> t = new Task<>() {
            @Override protected List<Person> call() { return personService.findAll(1000, 0); }
        };
        t.setOnSucceeded(e -> suspectCombo.setItems(FXCollections.observableArrayList(t.getValue())));
        new Thread(t).start();
    }

    private void loadSuspectsForCase(CaseFile cf) {
        // Load suspects associated with this case
        Task<List<Person>> t = new Task<>() {
            @Override protected List<Person> call() {
                try {
                    // Prefer case suspects first, then fall back to all persons
                    CaseFile fresh = caseService.findAllCases().stream()
                        .filter(c -> c.getId().equals(cf.getId())).findFirst().orElse(cf);
                    if (fresh.getCaseSuspects() != null && !fresh.getCaseSuspects().isEmpty())
                        return fresh.getCaseSuspects().stream()
                            .map(com.cms.model.CaseSuspect::getPerson)
                            .collect(java.util.stream.Collectors.toList());
                } catch (Exception ignore) {}
                return personService.findAll(1000, 0);
            }
        };
        t.setOnSucceeded(e -> {
            suspectCombo.setItems(FXCollections.observableArrayList(t.getValue()));
        });
        new Thread(t).start();
    }

    private void setupConverters() {
        caseCombo.setConverter(new StringConverter<>() {
            @Override public String toString(CaseFile cf) {
                if (cf == null) return "";
                String title = (cf.getIncident() != null) ? " — " + cf.getIncident().getTitle() : "";
                return cf.getCaseNumber() + title;
            }
            @Override public CaseFile fromString(String s) { return null; }
        });

        suspectCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Person p) {
                if (p == null) return "";
                String cnic = (p.getNationalId() != null && !p.getNationalId().isEmpty())
                              ? " | " + p.getNationalId() : "";
                return p.getFirstName() + " " + p.getLastName() + cnic;
            }
            @Override public Person fromString(String s) { return null; }
        });
    }

    private void setupTableColumns() {
        if (evidenceTable == null) return;
        if (colEvidNum  != null) colEvidNum.setCellValueFactory(new PropertyValueFactory<>("evidenceNumber"));
        if (colType     != null) colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        if (colStatus   != null) colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        if (colLocation != null) colLocation.setCellValueFactory(new PropertyValueFactory<>("collectionLocation"));
        if (colCase     != null) colCase.setCellValueFactory(data -> {
            CaseFile cf = data.getValue().getCaseFile();
            return new javafx.beans.property.SimpleStringProperty(cf != null ? cf.getCaseNumber() : "");
        });
    }

    private void loadEvidenceTable() {
        if (evidenceTable == null) return;
        Task<List<Evidence>> t = new Task<>() {
            @Override protected List<Evidence> call() {
                return evidenceService.findAll(500, 0);
            }
        };
        t.setOnSucceeded(e -> evidenceTable.setItems(FXCollections.observableArrayList(t.getValue())));
        t.setOnFailed(e -> logger.error("Failed loading evidence table", t.getException()));
        new Thread(t).start();
    }

    @FXML
    private void handleLog() {
        if (caseCombo.getValue() == null) {
            alert(Alert.AlertType.WARNING, "Please select an associated case."); return;
        }
        if (typeCombo.getValue() == null) {
            alert(Alert.AlertType.WARNING, "Please select an evidence type."); return;
        }
        if (locationField.getText().trim().isEmpty()) {
            alert(Alert.AlertType.WARNING, "Collection location is required."); return;
        }
        if (descriptionArea.getText().trim().isEmpty()) {
            alert(Alert.AlertType.WARNING, "Description is required."); return;
        }

        // Reload fresh CaseFile reference from DB to avoid detached entity
        CaseFile selectedCase = caseCombo.getValue();

        Task<Void> task = new Task<>() {
            @Override protected Void call() {
                // Fetch a fresh/managed CaseFile
                List<CaseFile> freshCases = caseService.findAllCases();
                CaseFile managed = freshCases.stream()
                    .filter(c -> c.getId().equals(selectedCase.getId()))
                    .findFirst().orElse(selectedCase);

                Evidence evidence = new Evidence();
                evidence.setEvidenceNumber("EVD-" + UUID.randomUUID().toString().substring(0,8).toUpperCase());
                evidence.setCaseFile(managed);
                evidence.setSuspect(suspectCombo.getValue());
                evidence.setType(typeCombo.getValue());
                evidence.setDescription(descriptionArea.getText().trim());
                evidence.setCollectionLocation(locationField.getText().trim());
                evidence.setCurrentStorageLocation(storageField.getText().trim());
                evidence.setStatus(EvidenceStatus.COLLECTED);
                evidence.setCollectedBy(SessionManager.getInstance().getCurrentUser());
                evidenceService.save(evidence);
                return null;
            }
        };
        task.setOnSucceeded(e -> {
            alert(Alert.AlertType.INFORMATION, "Evidence logged successfully!");
            handleClear();
            loadEvidenceTable();
        });
        task.setOnFailed(e -> {
            logger.error("Failed to log evidence", task.getException());
            alert(Alert.AlertType.ERROR, "Failed to save evidence: " + task.getException().getMessage());
        });
        new Thread(task).start();
    }

    @FXML
    private void handleExportPDF() {
        CaseFile selected = caseCombo.getValue();
        if (selected == null) { alert(Alert.AlertType.WARNING, "Please select a case first."); return; }
        
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save Chain of Custody Report");
        chooser.setInitialFileName("Chain_of_Custody_" + selected.getCaseNumber() + ".pdf");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Documents", "*.pdf"));
        java.io.File selectedFile = chooser.showSaveDialog(caseCombo.getScene().getWindow());

        if (selectedFile == null) return;
        final String outPath = selectedFile.getAbsolutePath();

        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception {
                List<Evidence> list = evidenceService.findByCase(selected.getId(), 1000, 0);
                if (list.isEmpty()) {
                    Platform.runLater(() -> alert(Alert.AlertType.INFORMATION,"No evidence found for this case."));
                    return null;
                }
                new ReportingService().generateReport(
                    "/reports/chain_of_custody_template.jrxml",
                    java.util.Map.of("CaseNumber", selected.getCaseNumber()), list, outPath);
                Platform.runLater(() -> alert(Alert.AlertType.INFORMATION, "Chain of Custody exported to: " + outPath));
                return null;
            }
        };
        t.setOnFailed(e -> {
            logger.error("PDF export failed", t.getException());
            alert(Alert.AlertType.ERROR, "Export failed: " + t.getException().getMessage());
        });
        new Thread(t).start();
    }

    @FXML
    private void handleClear() {
        caseCombo.setValue(null);
        suspectCombo.setValue(null);
        typeCombo.setValue(null);
        descriptionArea.clear();
        locationField.clear();
        storageField.clear();
    }

    private void alert(Alert.AlertType type, String msg) {
        Platform.runLater(() -> new Alert(type, msg).showAndWait());
    }
}
