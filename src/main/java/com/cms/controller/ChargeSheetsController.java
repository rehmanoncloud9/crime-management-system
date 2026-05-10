package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.service.ReportingService;
import com.cms.service.SessionManager;
import com.cms.util.NexusAlert;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChargeSheetsController {

    private static final Logger logger =
            LoggerFactory.getLogger(ChargeSheetsController.class);

    @FXML private ComboBox<CaseFile> caseCombo;
    @FXML private Label defendantNameLabel;
    @FXML private Label incidentRefLabel;
    @FXML private TextArea chargesArea;

    private final com.cms.service.CaseService caseService = new com.cms.service.CaseService();
    private final ReportingService reportingService = new ReportingService();

    @FXML
    public void initialize() {
        setupCaseCombo();
        loadCasesAsync();
    }

    private void loadCasesAsync() {

        Task<java.util.List<CaseFile>> task = new Task<>() {
            @Override
            protected java.util.List<CaseFile> call() {
                return caseService.findAllCases();
            }
        };

        task.setOnSucceeded(e -> {
            caseCombo.setItems(FXCollections.observableArrayList(task.getValue()));
        });

        task.setOnFailed(e ->
                logger.error("Failed to load cases", task.getException())
        );

        new Thread(task, "case-loader").start();
    }

    private void setupCaseCombo() {

        caseCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(CaseFile cf) {
                if (cf == null) return "";
                return cf.getCaseNumber() + " - " +
                        (cf.getIncident() != null ? cf.getIncident().getTitle() : "N/A");
            }

            @Override
            public CaseFile fromString(String string) {
                return null;
            }
        });

        caseCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                defendantNameLabel.setText("TO BE DETERMINED");
                incidentRefLabel.setText(
                        newVal.getIncident() != null
                                ? newVal.getIncident().getIncidentNumber()
                                : "N/A"
                );
            }
        });
    }

    @FXML
    private void handleGenerate() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || (currentUser.getRole() != Role.ADMINISTRATOR && currentUser.getRole() != Role.DETECTIVE)) {
            NexusAlert.showWarning("ACCESS DENIED\n\nOnly Lead Investigators and Administrators can sign off on Charge Sheets.");
            return;
        }

        CaseFile selected = caseCombo.getValue();
        if (selected == null) {
            NexusAlert.showWarning("Please select a case first.");
            return;
        }

        try {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save Charge Sheet");
            chooser.setInitialFileName("Charge_Sheet_" + selected.getCaseNumber() + ".pdf");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF Documents", "*.pdf"));
            java.io.File selectedFile = chooser.showSaveDialog(caseCombo.getScene().getWindow());

            if (selectedFile == null) return;
            String outputPath = selectedFile.getAbsolutePath();

            Map<String, Object> params = new HashMap<>();
            params.put("CaseNumber", selected.getCaseNumber());
            params.put("DefendantName", defendantNameLabel.getText());
            params.put("IncidentRef", incidentRefLabel.getText());
            params.put("Charges", chargesArea.getText());

            reportingService.generateReport(
                    "/reports/charge_sheet_template.jrxml",
                    params,
                    Collections.singletonList(selected),
                    outputPath
            );

            NexusAlert.showInfo("Charge sheet exported:\n" + outputPath);

        } catch (Exception e) {
            logger.error("Charge sheet generation failed", e);
            NexusAlert.showError("Failed: " + e.getMessage());
        }
    }

    @FXML
    private void handlePrint() {
        handleGenerate();
    }
}