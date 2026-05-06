package com.cms.controller;

import com.cms.model.CaseFile;
import com.cms.model.Person;
import com.cms.model.enums.PersonStatus;
import com.cms.service.CaseService;
import com.cms.service.NavigationService;
import com.cms.util.NexusAlert;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class LinkCaseController {

    @FXML private TextField searchField;
    @FXML private TableView<CaseFile> caseTable;
    @FXML private TableColumn<CaseFile, String> numberCol;
    @FXML private TableColumn<CaseFile, String> incidentCol;
    @FXML private TableColumn<CaseFile, String> statusCol;
    @FXML private TableColumn<CaseFile, String> dateCol;
    @FXML private ComboBox<PersonStatus> roleCombo;
    @FXML private Button linkBtn;

    private final CaseService caseService = new CaseService();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    
    private Person personToLink;
    private Runnable onLinkSucceeded;

    @FXML
    public void initialize() {
        setupTable();
        roleCombo.setItems(FXCollections.observableArrayList(
            PersonStatus.SUSPECT, PersonStatus.VICTIM, PersonStatus.WITNESS
        ));
        roleCombo.setValue(PersonStatus.SUSPECT);
        
        loadAll();
    }

    private void setupTable() {
        numberCol.setCellValueFactory(new PropertyValueFactory<>("caseNumber"));
        incidentCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getIncident().getTitle()));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        dateCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getOpenedAt().format(formatter)));
            
        caseTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            linkBtn.setDisable(newVal == null);
        });
        linkBtn.setDisable(true);
    }

    private void loadAll() {
        Task<List<CaseFile>> task = new Task<>() {
            @Override
            protected List<CaseFile> call() {
                return caseService.findAllCases();
            }
        };
        task.setOnSucceeded(e -> caseTable.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML
    private void handleSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) {
            loadAll();
            return;
        }

        Task<List<CaseFile>> task = new Task<>() {
            @Override
            protected List<CaseFile> call() {
                return caseService.searchCases(keyword);
            }
        };
        task.setOnSucceeded(e -> caseTable.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML
    private void handleLink() {
        CaseFile selected = caseTable.getSelectionModel().getSelectedItem();
        PersonStatus role = roleCombo.getValue();

        if (selected == null || personToLink == null) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                caseService.addPersonToCase(selected.getId(), personToLink.getId(), role);
                return null;
            }
        };
        
        task.setOnSucceeded(e -> {
            NexusAlert.showInfo("Person linked to case successfully.");
            if (onLinkSucceeded != null) onLinkSucceeded.run();
            handleCancel(); // Close current view
        });
        
        task.setOnFailed(e -> {
            NexusAlert.showError("Link failed: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void handleCancel() {
        NavigationService.getInstance().goBack();
    }

    public void setPerson(Person person) {
        this.personToLink = person;
    }

    public void setOnLinkSucceeded(Runnable onLinkSucceeded) {
        this.onLinkSucceeded = onLinkSucceeded;
    }
}
