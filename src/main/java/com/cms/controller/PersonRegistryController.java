package com.cms.controller;

import com.cms.model.Person;
import com.cms.service.NavigationService;
import com.cms.service.PersonService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import com.cms.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PersonRegistryController {
    private static final Logger logger = LoggerFactory.getLogger(PersonRegistryController.class);
    private final PersonService personService = new PersonService();

    @FXML private TextField searchField;
    @FXML private FlowPane personFlowPane;

    @FXML
    public void initialize() {
        loadPersons();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadPersons());
    }

    @FXML
    public void loadPersons() {
        String keyword = searchField.getText();
        javafx.concurrent.Task<List<Person>> task = new javafx.concurrent.Task<>() {
            @Override
            protected List<Person> call() {
                return personService.searchPersons(keyword);
            }
        };

        task.setOnSucceeded(e -> {
            List<Person> persons = task.getValue();
            personFlowPane.getChildren().clear();
            if (persons.isEmpty()) {
                Label placeholder = new Label("No profiles found matching search.");
                placeholder.getStyleClass().add("text-muted");
                personFlowPane.getChildren().add(placeholder);
            } else {
                for (Person p : persons) {
                    personFlowPane.getChildren().add(buildPersonCard(p));
                }
            }
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load persons", task.getException());
            personFlowPane.getChildren().setAll(new Label("Error loading data."));
        });

        new Thread(task).start();
    }

    private Pane buildPersonCard(Person p) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPrefWidth(220);
        card.setCursor(javafx.scene.Cursor.HAND);

        // Photo
        ImageView photoView = new ImageView();
        photoView.setFitWidth(180);
        photoView.setFitHeight(150);
        photoView.setPreserveRatio(true);
        
        if (p.getPhoto() != null) {
            photoView.setImage(new Image(new java.io.ByteArrayInputStream(p.getPhoto())));
        }

        Label name = new Label(p.getFirstName() + " " + p.getLastName());
        name.getStyleClass().add("heading-md");
        
        Label cnic = new Label(p.getNationalId() != null ? p.getNationalId() : "Internal ID: " + p.getId());
        cnic.getStyleClass().add("text-muted");

        Label status = new Label(p.getPersonStatus() != null ? p.getPersonStatus().name() : "UNKNOWN");
        status.getStyleClass().addAll("badge", getStatusClass(p.getPersonStatus()));

        card.getChildren().addAll(photoView, name, cnic, status);
        card.setOnMouseClicked(e -> navigateToDetail(p));

        return card;
    }

    private String getStatusClass(com.cms.model.enums.PersonStatus status) {
        if (status == null) return "badge-pending";
        return switch (status) {
            case CRIMINAL -> "badge-closed"; // Red for criminal
            case SUSPECT -> "badge-pending"; // Orange for suspect
            case VICTIM, WITNESS -> "badge-active"; // Green for victim/witness
            default -> "badge-pending";
        };
    }

    private void navigateToDetail(Person p) {
        NavigationService.getInstance().navigateTo(p.getFirstName() + "'s Profile", "/fxml/modules/PersonDetailView.fxml", (controller) -> {
            if (controller instanceof PersonDetailController pdc) {
                pdc.loadPerson(p.getId());
            }
        });
    }

    @FXML
    public void handleAddPerson() {
        NavigationService.getInstance().navigateTo("Add New Person", "/fxml/modules/PersonRegistration.fxml");
    }
}
