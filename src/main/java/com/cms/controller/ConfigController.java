package com.cms.controller;

import com.cms.model.CrimeType;
import com.cms.service.CrimeTypeService;
import com.cms.util.NexusAlert;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class ConfigController {
    private static final Logger logger = LoggerFactory.getLogger(ConfigController.class);

    // Resolve config path relative to the JAR/classpath root so it works
    // regardless of the working directory the app is launched from.
    private static final String CONFIG_FILE = resolveConfigPath();

    private static String resolveConfigPath() {
        // First try: next to the running JAR (production layout)
        java.net.URL url = ConfigController.class.getResource("/config.properties");
        if (url != null) {
            try {
                return new java.io.File(url.toURI()).getAbsolutePath();
            } catch (Exception ignored) {}
        }
        // Second try: Maven source layout (dev mode)
        java.io.File devPath = new java.io.File("src/main/resources/config.properties");
        if (devPath.exists()) return devPath.getAbsolutePath();
        // Fallback: write next to working directory
        return "config.properties";
    }

    @FXML private TextField systemNameField;
    @FXML private TextField defaultPrecinctField;
    @FXML private CheckBox autoBackupCheck;
    @FXML private Slider aiSensitivitySlider;

    @FXML private TableView<CrimeType> crimeTypeTable;
    @FXML private TableColumn<CrimeType, String> crimeNameCol;
    @FXML private TableColumn<CrimeType, String> crimeCodeCol;
    @FXML private TableColumn<CrimeType, String> crimeDescCol;

    private final CrimeTypeService crimeTypeService = new CrimeTypeService();

    @FXML
    public void initialize() {
        loadConfig();
        setupCrimeTypeTable();
        loadCrimeTypes();
    }

    private void setupCrimeTypeTable() {
        crimeNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        crimeCodeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        crimeDescCol.setCellValueFactory(new PropertyValueFactory<>("description"));

        // Context menu for deletion
        ContextMenu menu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Delete Crime Type");
        deleteItem.setOnAction(e -> handleDeleteCrimeType());
        menu.getItems().add(deleteItem);
        crimeTypeTable.setContextMenu(menu);
    }

    private void loadCrimeTypes() {
        crimeTypeTable.setItems(FXCollections.observableArrayList(crimeTypeService.findAll()));
    }

    private void loadConfig() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
            systemNameField.setText(props.getProperty("app.name", "CMS v2.0"));
            defaultPrecinctField.setText(props.getProperty("app.default.precinct", "Central"));
            autoBackupCheck.setSelected(Boolean.parseBoolean(props.getProperty("app.auto.backup", "true")));
            aiSensitivitySlider.setValue(Double.parseDouble(props.getProperty("ai.sensitivity", "75")));
        } catch (Exception e) {
            logger.error("Failed to load config", e);
        }
    }

    @FXML
    private void handleSave() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (Exception e) { /* New file */ }

        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.setProperty("app.name", systemNameField.getText());
            props.setProperty("app.default.precinct", defaultPrecinctField.getText());
            props.setProperty("app.auto.backup", String.valueOf(autoBackupCheck.isSelected()));
            props.setProperty("ai.sensitivity", String.valueOf((int)aiSensitivitySlider.getValue()));
            props.store(out, "Updated by CMS UI");
            
            NexusAlert.showInfo("Settings Saved Successfully");
        } catch (Exception e) {
            logger.error("Failed to save config", e);
            NexusAlert.showError("Save Failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddCrimeType() {
        // Simple dialog for adding crime type
        Dialog<CrimeType> dialog = new Dialog<>();
        dialog.setTitle("Add Crime Type");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10);
        TextField name = new TextField(); name.setPromptText("Name");
        TextField code = new TextField(); code.setPromptText("Code (e.g. THEFT-01)");
        TextField desc = new TextField(); desc.setPromptText("Description");

        grid.add(new Label("Name:"), 0, 0); grid.add(name, 1, 0);
        grid.add(new Label("Code:"), 0, 1); grid.add(code, 1, 1);
        grid.add(new Label("Desc:"), 0, 2); grid.add(desc, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == addButtonType) return new CrimeType(name.getText(), code.getText());
            return null;
        });

        dialog.showAndWait().ifPresent(ct -> {
            try {
                crimeTypeService.save(ct);
                loadCrimeTypes();
            } catch (Exception e) {
                NexusAlert.showError(e.getMessage());
            }
        });
    }

    private void handleDeleteCrimeType() {
        CrimeType selected = crimeTypeTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        boolean confirm = NexusAlert.confirm("Confirm Deletion", "Delete " + selected.getName() + "?");
        if (confirm) {
            crimeTypeService.delete(selected);
            loadCrimeTypes();
        }
    }
}
