package com.cms.controller;

import com.cms.model.Civilian;
import com.cms.model.Person;
import com.cms.service.CivilianService;
import com.cms.service.NavigationService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.List;

public class CivilianRegistryController {
    private static final Logger logger = LoggerFactory.getLogger(CivilianRegistryController.class);
    private final CivilianService civilianService = new CivilianService();

    @FXML private TextField searchField;
    @FXML private FlowPane civilianFlowPane;

    @FXML
    public void initialize() {
        loadCivilians();
        searchField.textProperty().addListener((obs, oldVal, newVal) -> loadCivilians());
    }

    @FXML
    public void loadCivilians() {
        String keyword = searchField.getText();
        Task<List<Civilian>> task = new Task<>() {
            @Override
            protected List<Civilian> call() {
                return civilianService.searchCivilians(keyword);
            }
        };

        task.setOnSucceeded(e -> {
            List<Civilian> civilians = task.getValue();
            civilianFlowPane.getChildren().clear();
            if (civilians.isEmpty()) {
                Label placeholder = new Label("No civilian records found.");
                placeholder.getStyleClass().add("text-muted");
                civilianFlowPane.getChildren().add(placeholder);
            } else {
                for (Civilian c : civilians) {
                    civilianFlowPane.getChildren().add(buildCivilianCard(c));
                }
            }
        });

        task.setOnFailed(e -> {
            logger.error("Failed to load civilians", task.getException());
            civilianFlowPane.getChildren().setAll(new Label("Error loading civilian data."));
        });

        new Thread(task).start();
    }

    private Pane buildCivilianCard(Civilian c) {
        Person p = c.getPerson();
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
            photoView.setImage(new Image(new ByteArrayInputStream(p.getPhoto())));
        }

        Label name = new Label(p.getFirstName() + " " + p.getLastName());
        name.getStyleClass().add("heading-md");
        
        Label job = new Label(c.getOccupation() != null ? c.getOccupation() : "No Occupation");
        job.getStyleClass().add("text-sm");
        
        Label employer = new Label(c.getEmployer() != null ? "@ " + c.getEmployer() : "");
        employer.getStyleClass().add("text-muted");

        Label status = new Label(p.getPersonStatus() != null ? p.getPersonStatus().name() : "CIVILIAN");
        status.getStyleClass().addAll("badge", "badge-active");

        card.getChildren().addAll(photoView, name, job, employer, status);
        card.setOnMouseClicked(e -> navigateToPersonDetail(p));

        return card;
    }

    private void navigateToPersonDetail(Person p) {
        NavigationService.getInstance().navigateTo(p.getFirstName() + "'s Profile", "/fxml/modules/PersonDetailView.fxml", (controller) -> {
            if (controller instanceof PersonDetailController pdc) {
                pdc.loadPerson(p.getId());
            }
        });
    }

    @FXML
    public void handleAddCivilian() {
        // For now, redirect to Person Registration which can handle the specialisation
        NavigationService.getInstance().navigateTo("Register Civilian", "/fxml/modules/PersonRegistration.fxml");
    }
}
