package com.cms.controller;

import com.cms.model.CrimeIncident;
import com.cms.model.CrimeType;
import com.cms.model.User;
import com.cms.model.enums.IncidentStatus;
import com.cms.model.geo.*;
import com.cms.service.GeographyService;
import com.cms.service.IncidentService;
import com.cms.service.SessionManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class IncidentRegistrationController {
    private static final Logger logger = LoggerFactory.getLogger(IncidentRegistrationController.class);

    @FXML private TextField        titleField;
    @FXML private ComboBox<CrimeType>  typeCombo;
    @FXML private DatePicker           datePicker;
    @FXML private ComboBox<District>   districtCombo;
    @FXML private ComboBox<City>       cityCombo;
    @FXML private ComboBox<Area>       areaCombo;
    @FXML private TextArea             locationArea;
    @FXML private TextArea             descriptionArea;
    @FXML private Button               exportButton;

    private CrimeIncident lastSavedIncident;
    private final IncidentService  incidentService  = new IncidentService();
    private final GeographyService geoService       = new GeographyService();

    // Full lists cached after first load
    private List<District> allDistricts;
    private List<City>     allCities;
    private List<Area>     allAreas;

    @FXML
    public void initialize() {
        datePicker.setValue(LocalDate.now());
        setupTypeCombo();
        setupDistrictCombo();
        setupCityCombo();
        setupAreaCombo();
        loadInitialData();
    }

    // ── Setup converters ──────────────────────────────────────────────────────

    private void setupTypeCombo() {
        typeCombo.setConverter(new StringConverter<>() {
            @Override public String toString(CrimeType t) { return t == null ? "" : t.getName(); }
            @Override public CrimeType fromString(String s) { return null; }
        });
    }

    private void setupDistrictCombo() {
        districtCombo.setEditable(true);
        districtCombo.setConverter(new StringConverter<>() {
            @Override public String toString(District d) { return d == null ? "" : d.getName(); }
            @Override public District fromString(String s) { return null; }
        });
        
        // Filter list as user types
        districtCombo.getEditor().textProperty().addListener((obs, ov, nv) -> {
            if (allDistricts == null) return;
            
            // Don't filter if a selection was just made programmatically
            if (districtCombo.getValue() != null && 
                nv.equals(districtCombo.getValue().getName())) {
                return;
            }
            
            String kw = (nv == null ? "" : nv).toLowerCase().trim();
            List<District> filtered = kw.isEmpty() ? allDistricts
                : allDistricts.stream().filter(d -> d.getName().toLowerCase().contains(kw)).toList();
            districtCombo.setItems(FXCollections.observableArrayList(filtered));
            if (!districtCombo.isShowing() && !kw.isEmpty()) districtCombo.show();
        });
        
        // When district selected, clear editor and reload cities
        districtCombo.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                // Set editor text to match selection
                Platform.runLater(() -> districtCombo.getEditor().setText(nv.getName()));
                
                // Clear dependent fields
                cityCombo.setValue(null);
                cityCombo.getEditor().clear();
                areaCombo.setValue(null);
                areaCombo.getEditor().clear();
                
                loadCitiesForDistrict(nv);
            }
        });
        
        // Validate on focus lost
        districtCombo.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                validateAndSelectComboBox(districtCombo, allDistricts);
            }
        });
        
        // Show all on click
        districtCombo.setOnMouseClicked(e -> {
            if (allDistricts != null) {
                districtCombo.setItems(FXCollections.observableArrayList(allDistricts));
                if (!districtCombo.isShowing()) districtCombo.show();
            }
        });
    }

    private void setupCityCombo() {
        cityCombo.setEditable(true);
        cityCombo.setConverter(new StringConverter<>() {
            @Override public String toString(City c) { return c == null ? "" : c.getName(); }
            @Override public City fromString(String s) { return null; }
        });
        
        cityCombo.getEditor().textProperty().addListener((obs, ov, nv) -> {
            if (allCities == null) return;
            
            // Don't filter if a selection was just made programmatically
            if (cityCombo.getValue() != null && 
                nv.equals(cityCombo.getValue().getName())) {
                return;
            }
            
            String kw = (nv == null ? "" : nv).toLowerCase().trim();
            List<City> filtered = kw.isEmpty() ? allCities
                : allCities.stream().filter(c -> c.getName().toLowerCase().contains(kw)).toList();
            cityCombo.setItems(FXCollections.observableArrayList(filtered));
            if (!cityCombo.isShowing() && !kw.isEmpty()) cityCombo.show();
        });
        
        cityCombo.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                // Set editor text to match selection
                Platform.runLater(() -> cityCombo.getEditor().setText(nv.getName()));
                
                // Clear dependent field
                areaCombo.setValue(null);
                areaCombo.getEditor().clear();
                
                loadAreasForCity(nv);
            }
        });
        
        // Validate on focus lost
        cityCombo.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                validateAndSelectComboBox(cityCombo, allCities);
            }
        });
        
        cityCombo.setOnMouseClicked(e -> {
            if (allCities != null) {
                cityCombo.setItems(FXCollections.observableArrayList(allCities));
                if (!cityCombo.isShowing()) cityCombo.show();
            }
        });
    }

    private void setupAreaCombo() {
        areaCombo.setEditable(true);
        areaCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Area a) { return a == null ? "" : a.getName(); }
            @Override public Area fromString(String s) { return null; }
        });
        
        areaCombo.getEditor().textProperty().addListener((obs, ov, nv) -> {
            if (allAreas == null) return;
            
            // Don't filter if a selection was just made programmatically
            if (areaCombo.getValue() != null && 
                nv.equals(areaCombo.getValue().getName())) {
                return;
            }
            
            String kw = (nv == null ? "" : nv).toLowerCase().trim();
            List<Area> filtered = kw.isEmpty() ? allAreas
                : allAreas.stream().filter(a -> a.getName().toLowerCase().contains(kw)).toList();
            areaCombo.setItems(FXCollections.observableArrayList(filtered));
            if (!areaCombo.isShowing() && !kw.isEmpty()) areaCombo.show();
        });
        
        areaCombo.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
            if (nv != null) {
                // Set editor text to match selection
                Platform.runLater(() -> areaCombo.getEditor().setText(nv.getName()));
            }
        });
        
        // Validate on focus lost
        areaCombo.getEditor().focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                validateAndSelectComboBox(areaCombo, allAreas);
            }
        });
        
        areaCombo.setOnMouseClicked(e -> {
            if (allAreas != null) {
                areaCombo.setItems(FXCollections.observableArrayList(allAreas));
                if (!areaCombo.isShowing()) areaCombo.show();
            }
        });
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    /**
     * Validates and auto-selects ComboBox value based on editor text.
     * If text matches an item, selects it. Otherwise, clears the selection.
     */
    private <T> void validateAndSelectComboBox(ComboBox<T> combo, List<T> allItems) {
        if (allItems == null || allItems.isEmpty()) return;
        
        String editorText = combo.getEditor().getText();
        if (editorText == null || editorText.trim().isEmpty()) {
            combo.setValue(null);
            return;
        }
        
        // Check if current value matches editor text
        T currentValue = combo.getValue();
        if (currentValue != null) {
            String currentName = getItemName(currentValue);
            if (currentName.equalsIgnoreCase(editorText.trim())) {
                return; // Already correctly selected
            }
        }
        
        // Try to find exact match
        String searchText = editorText.trim();
        for (T item : allItems) {
            String itemName = getItemName(item);
            if (itemName.equalsIgnoreCase(searchText)) {
                combo.setValue(item);
                combo.getEditor().setText(itemName);
                return;
            }
        }
        
        // No match found - clear the invalid input
        combo.setValue(null);
        combo.getEditor().clear();
    }
    
    /**
     * Helper to get display name from District/City/Area objects
     */
    private <T> String getItemName(T item) {
        if (item instanceof District) return ((District) item).getName();
        if (item instanceof City) return ((City) item).getName();
        if (item instanceof Area) return ((Area) item).getName();
        return item.toString();
    }

    private void loadInitialData() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                List<CrimeType> types = incidentService.getAllCrimeTypes();
                List<District>  dists = geoService.searchDistricts("");
                return null;
            }
        };

        // Load crime types
        Task<List<CrimeType>> typeTask = new Task<>() {
            @Override protected List<CrimeType> call() { return incidentService.getAllCrimeTypes(); }
        };
        typeTask.setOnSucceeded(e -> typeCombo.setItems(FXCollections.observableArrayList(typeTask.getValue())));

        // Load all districts (all Punjab + others from DB)
        Task<List<District>> distTask = new Task<>() {
            @Override protected List<District> call() { return geoService.searchDistricts(""); }
        };
        distTask.setOnSucceeded(e -> {
            allDistricts = distTask.getValue();
            districtCombo.setItems(FXCollections.observableArrayList(allDistricts));
        });
        distTask.setOnFailed(e -> logger.error("Failed loading districts", distTask.getException()));

        new Thread(typeTask).start();
        new Thread(distTask).start();
    }

    private void loadCitiesForDistrict(District d) {
        Task<List<City>> t = new Task<>() {
            @Override protected List<City> call() { return geoService.getCitiesByDistrict(d.getId()); }
        };
        t.setOnSucceeded(e -> {
            allCities = t.getValue();
            cityCombo.setItems(FXCollections.observableArrayList(allCities));
            allAreas  = null;
            areaCombo.setItems(FXCollections.observableArrayList());
        });
        new Thread(t).start();
    }

    private void loadAreasForCity(City c) {
        Task<List<Area>> t = new Task<>() {
            @Override protected List<Area> call() { return geoService.getAreasByCity(c.getId()); }
        };
        t.setOnSucceeded(e -> {
            allAreas = t.getValue();
            areaCombo.setItems(FXCollections.observableArrayList(allAreas));
        });
        new Thread(t).start();
    }

    // ── Submit ─────────────────────────────────────────────────────────────────

    @FXML
    private void handleSubmit() {
        if (!validateForm()) return;

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            alert(Alert.AlertType.ERROR, "Session expired. Please log in again."); return;
        }
        if (currentUser.getRole() == com.cms.model.enums.Role.ANALYST) {
            alert(Alert.AlertType.ERROR, "Analysts have read-only access."); return;
        }

        // Build incident
        CrimeIncident incident = new CrimeIncident();
        incident.setIncidentNumber("INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        incident.setTitle(titleField.getText().trim());
        incident.setCrimeType(typeCombo.getValue());
        incident.setOccurredAt(datePicker.getValue().atStartOfDay());
        incident.setDistrict(districtCombo.getValue());
        incident.setCity(cityCombo.getValue());
        incident.setArea(areaCombo.getValue());
        incident.setLocationAddress(locationArea.getText().trim());
        incident.setDescription(descriptionArea.getText().trim());
        incident.setReportingOfficer(currentUser);
        incident.setStatus(IncidentStatus.OPEN);
        // Build a precinct label from district
        if (districtCombo.getValue() != null)
            incident.setPrecinct(districtCombo.getValue().getName() + " Police Station");

        Task<CrimeIncident> saveTask = new Task<>() {
            @Override protected CrimeIncident call() {
                return incidentService.registerIncident(incident);
            }
        };
        saveTask.setOnSucceeded(e -> {
            lastSavedIncident = saveTask.getValue();
            if (exportButton != null) exportButton.setDisable(false);
            logger.info("Incident registered: {}", lastSavedIncident.getIncidentNumber());
            Platform.runLater(() ->
                alert(Alert.AlertType.INFORMATION,
                    "✅ Incident " + lastSavedIncident.getIncidentNumber() + " registered successfully!\n\n" +
                    "Database ID: " + lastSavedIncident.getId() + "\n" +
                    "Title: " + lastSavedIncident.getTitle() + "\n" +
                    "Status: OPEN\n\n" +
                    "The dashboard and charts will reflect this incident.")
            );
            clearForm();
        });
        saveTask.setOnFailed(e -> {
            Throwable ex = saveTask.getException();
            logger.error("Failed to register incident", ex);
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            Platform.runLater(() -> alert(Alert.AlertType.ERROR, "Failed to register incident:\n" + msg));
        });
        new Thread(saveTask).start();
    }

    @FXML private void handleCancel() { clearForm(); }

    private boolean validateForm() {
        if (titleField.getText().trim().isEmpty()) {
            alert(Alert.AlertType.WARNING, "Please enter an incident title."); return false;
        }
        if (typeCombo.getValue() == null) {
            alert(Alert.AlertType.WARNING, "Please select a crime type."); return false;
        }
        if (datePicker.getValue() == null) {
            alert(Alert.AlertType.WARNING, "Please select the date."); return false;
        }
        if (districtCombo.getValue() == null) {
            alert(Alert.AlertType.WARNING, "Please select a district."); return false;
        }
        if (descriptionArea.getText().trim().isEmpty()) {
            alert(Alert.AlertType.WARNING, "Please enter a description."); return false;
        }
        return true;
    }

    private void clearForm() {
        titleField.clear();
        typeCombo.setValue(null);
        datePicker.setValue(LocalDate.now());
        districtCombo.setValue(null);
        districtCombo.getEditor().clear();
        cityCombo.setValue(null);
        cityCombo.getEditor().clear();
        areaCombo.setValue(null);
        areaCombo.getEditor().clear();
        locationArea.clear();
        descriptionArea.clear();
        if (exportButton != null) exportButton.setDisable(true);
        lastSavedIncident = null;
        allCities = null;
        allAreas  = null;
        // Reload fresh district list
        loadInitialData();
    }

    @FXML
    private void handleExportFIR() {
        if (lastSavedIncident == null) return;
        try {
            String out = System.getProperty("user.home") + "/Desktop/FIR_"
                         + lastSavedIncident.getIncidentNumber() + ".pdf";
            new com.cms.service.ReportingService().generateReport(
                "/reports/fir_template.jrxml",
                new java.util.HashMap<>(),
                java.util.Collections.singletonList(new FIRWrapper(lastSavedIncident)),
                out);
            alert(Alert.AlertType.INFORMATION, "FIR exported to: " + out);
        } catch (Exception e) {
            logger.error("Failed to export FIR", e);
            alert(Alert.AlertType.ERROR, "Failed to export FIR: " + e.getMessage());
        }
    }

    public static class FIRWrapper {
        private final CrimeIncident incident;
        public FIRWrapper(CrimeIncident i) { this.incident = i; }
        public String getIncidentNumber() { return incident.getIncidentNumber(); }
        public java.time.LocalDateTime getIncidentDate() { return incident.getOccurredAt(); }
        public String getLocation() { return incident.getLocationAddress(); }
        public String getDescription() { return incident.getDescription(); }
        public String getStatus() { return "REGISTERED"; }
    }

    private void alert(Alert.AlertType type, String msg) {
        Platform.runLater(() -> new Alert(type, msg).showAndWait());
    }
}
