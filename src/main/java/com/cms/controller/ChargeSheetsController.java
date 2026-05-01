package com.cms.controller;

import com.cms.model.CaseFile;

import com.cms.service.ReportingService;
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

        CaseFile selected = caseCombo.getValue();

        if (selected == null) {
            new Alert(Alert.AlertType.WARNING,
                    "Please select a case first.").showAndWait();
            return;
        }

        try {

            String outputPath = System.getProperty("user.home")
                    + "/Desktop/Charge_Sheet_" + selected.getCaseNumber() + ".pdf";

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

            new Alert(Alert.AlertType.INFORMATION,
                    "Charge sheet exported:\n" + outputPath).showAndWait();

        } catch (Exception e) {

            logger.error("Charge sheet generation failed", e);

            new Alert(Alert.AlertType.ERROR,
                    "Failed: " + e.getMessage()).showAndWait();
        }
    }

    @FXML
    private void handlePrint() {
        handleGenerate();
    }
}