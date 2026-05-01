package com.cms.controller;

import com.cms.model.Person;
import com.cms.model.enums.Gender;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.cms.service.NavigationService;
import javafx.scene.layout.HBox;
import javafx.scene.control.Tooltip;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class CriminalSearchController {
    @FXML private TextField nameSearchField;
    @FXML private TextField idSearchField;
    @FXML private ComboBox<Gender> genderFilter;
    @FXML private CheckBox warrantFilter;
    @FXML private TableView<Person> resultsTable;
    @FXML private TableColumn<Person, byte[]> photoCol;
    @FXML private TableColumn<Person, String> nameCol;
    @FXML private TableColumn<Person, String> dobCol;
    @FXML private TableColumn<Person, String> genderCol;
    @FXML private TableColumn<Person, String> idCol;
    @FXML private TableColumn<Person, String> warrantCol;
    @FXML private TableColumn<Person, String> riskCol;
    @FXML private TableColumn<Person, Void> actionCol;

    private final com.cms.service.PersonService personService = new com.cms.service.PersonService();
    private final DateTimeFormatter dobFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    public void initialize() {
        genderFilter.setItems(FXCollections.observableArrayList(Gender.values()));
        setupTable();
        loadAll();
    }

    private void setupTable() {
        photoCol.setCellValueFactory(new PropertyValueFactory<>("photo"));
        photoCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            {
                imageView.setFitWidth(60);
                imageView.setFitHeight(60);
                imageView.setPreserveRatio(true);
            }
            @Override
            protected void updateItem(byte[] photo, boolean empty) {
                super.updateItem(photo, empty);
                if (empty || photo == null) {
                    setGraphic(null);
                } else {
                    imageView.setImage(new Image(new ByteArrayInputStream(photo)));
                    setGraphic(imageView);
                }
            }
        });

        nameCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getFirstName() + " " + cellData.getValue().getLastName()));
        dobCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getDateOfBirth() != null ? cellData.getValue().getDateOfBirth().format(dobFormatter) : "N/A"));
        genderCol.setCellValueFactory(new PropertyValueFactory<>("gender"));
        idCol.setCellValueFactory(new PropertyValueFactory<>("nationalId"));
        warrantCol.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().isHasActiveWarrant() ? "YES" : "NO"));
        riskCol.setCellValueFactory(new PropertyValueFactory<>("riskScore"));

        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn = new Button("👁");
            private final Button editBtn = new Button("✏");
            private final Button deleteBtn = new Button("🗑");
            private final javafx.scene.layout.HBox container = new javafx.scene.layout.HBox(5, viewBtn, editBtn, deleteBtn);
            
            {
                viewBtn.getStyleClass().addAll("btn-xs", "btn-primary");
                viewBtn.setTooltip(new Tooltip("View Profile"));
                viewBtn.setOnAction(e -> {
                    Person p = getTableView().getItems().get(getIndex());
                    if (p != null) MainController.getInstance().loadPersonDetail(p.getId());
                });

                editBtn.getStyleClass().addAll("btn-xs", "btn-outline");
                editBtn.setTooltip(new Tooltip("Edit Profile"));
                editBtn.setOnAction(e -> {
                    Person p = getTableView().getItems().get(getIndex());
                    if (p != null) {
                        NavigationService.getInstance().navigateTo("Edit Profile", "/fxml/modules/PersonRegistration.fxml", controller -> {
                            if (controller instanceof PersonRegistrationController prc) {
                                prc.loadPersonRecord(p.getId());
                            }
                        });
                    }
                });

                deleteBtn.getStyleClass().addAll("btn-xs", "btn-danger");
                deleteBtn.setTooltip(new Tooltip("Delete Record"));
                deleteBtn.setOnAction(e -> {
                    Person p = getTableView().getItems().get(getIndex());
                    if (p != null) handleDelete(p);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else setGraphic(container);
            }
        });
    }

    private void handleDelete(Person p) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete the record for " + p.getFirstName() + "? This action cannot be undone.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                personService.deletePerson(p.getId());
                loadAll();
            }
        });
    }

    private void loadAll() {
        javafx.concurrent.Task<List<Person>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Person> call() {
                return personService.findAll(1000, 0);
            }
        };
        task.setOnSucceeded(e -> resultsTable.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML
    private void handleSearch() {
        String name = nameSearchField.getText();

        javafx.concurrent.Task<List<Person>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Person> call() {
                List<Person> results = personService.findByName(name, name, 1000, 0); 
                
                // Filter by gender if selected
                if (genderFilter.getValue() != null) {
                    results = results.stream()
                        .filter(p -> p.getGender() == genderFilter.getValue())
                        .collect(Collectors.toList());
                }
                
                // Filter by active warrant
                if (warrantFilter.isSelected()) {
                    results = results.stream()
                        .filter(Person::isHasActiveWarrant)
                        .collect(Collectors.toList());
                }
                return results;
            }
        };
        task.setOnSucceeded(e -> resultsTable.setItems(FXCollections.observableArrayList(task.getValue())));
        new Thread(task).start();
    }

    @FXML
    private void handleClear() {
        nameSearchField.clear();
        idSearchField.clear();
        genderFilter.setValue(null);
        warrantFilter.setSelected(false);
        loadAll();
    }
}
