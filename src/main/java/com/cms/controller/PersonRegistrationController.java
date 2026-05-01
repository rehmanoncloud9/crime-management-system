package com.cms.controller;

import com.cms.model.MedicalRecord;
import com.cms.model.Person;
import com.cms.model.enums.BloodGroup;
import com.cms.model.enums.Gender;
import com.cms.model.enums.PersonStatus;
import com.cms.model.geo.*;
import com.cms.service.GeographyService;
import com.cms.util.UIUtils;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class PersonRegistrationController {
    private static final Logger logger = LoggerFactory.getLogger(PersonRegistrationController.class);

    @FXML private TextField  firstNameField;
    @FXML private TextField  lastNameField;
    @FXML private ComboBox<Gender>        genderCombo;
    @FXML private DatePicker              dobPicker;
    @FXML private ComboBox<Country>       nationalityCombo;
    @FXML private ComboBox<District>      districtCombo;
    @FXML private ComboBox<City>          cityCombo;
    @FXML private ComboBox<Area>          areaCombo;
    @FXML private TextField               nationalIdField;
    @FXML private CheckBox                unknownPersonCheck;
    @FXML private TextField               heightField;
    @FXML private TextField               weightField;
    @FXML private TextArea                marksArea;
    @FXML private TextArea                addressArea;
    @FXML private TextField               aliasesField;
    @FXML private TextField               gangField;
    @FXML private CheckBox                warrantCheck;
    @FXML private ComboBox<BloodGroup>    bloodGroupCombo;
    @FXML private TextField               dnaProfileField;
    @FXML private TextArea                diseasesArea;
    @FXML private TextArea                injuriesArea;
    @FXML private ComboBox<PersonStatus>  personStatusCombo;
    @FXML private TextArea                medicalNotesArea;
    @FXML private Button                  saveBtn;
    @FXML private Label                   formTitle;
    @FXML private ImageView               photoView;

    private File   selectedPhotoFile;
    private Long   existingPersonId;

    private final com.cms.service.PersonService personService = new com.cms.service.PersonService();

    @FXML
    public void initialize() {
        genderCombo.setItems(FXCollections.observableArrayList(Gender.values()));
        personStatusCombo.setItems(FXCollections.observableArrayList(PersonStatus.values()));
        bloodGroupCombo.setItems(FXCollections.observableArrayList(BloodGroup.values()));

        GeographyService geo = new GeographyService();
        UIUtils.makeAutoSuggest(nationalityCombo, geo::searchCountries, Country::getName);
        UIUtils.makeAutoSuggest(districtCombo,    geo::searchDistricts, District::getName);
        UIUtils.makeAutoSuggest(cityCombo,        geo::searchCities,    City::getName);
        UIUtils.makeAutoSuggest(areaCombo,        geo::searchAreas,     Area::getName);

        // Cascade district -> city -> area
        districtCombo.valueProperty().addListener((obs, old, d) -> {
            cityCombo.setValue(null);
            areaCombo.setValue(null);
            if (d != null) {
                UIUtils.makeAutoSuggest(cityCombo,
                    kw -> geo.searchCities(kw).stream()
                              .filter(c -> c.getDistrict() != null && c.getDistrict().getId().equals(d.getId()))
                              .toList(),
                    City::getName);
            }
        });
        cityCombo.valueProperty().addListener((obs, old, c) -> {
            areaCombo.setValue(null);
            if (c != null) {
                UIUtils.makeAutoSuggest(areaCombo,
                    kw -> geo.searchAreas(kw).stream()
                              .filter(a -> a.getCity() != null && a.getCity().getId().equals(c.getId()))
                              .toList(),
                    Area::getName);
            }
        });

        setupUnknownPersonListener();
    }

    private void setupUnknownPersonListener() {
        unknownPersonCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            boolean unk = Boolean.TRUE.equals(newVal);
            nationalIdField.setDisable(unk);
            dobPicker.setDisable(unk);
            nationalityCombo.setDisable(unk);
            if (unk) {
                nationalIdField.clear();
                dobPicker.setValue(null);
                nationalityCombo.setValue(null);
                if (firstNameField.getText().isEmpty()) firstNameField.setText("UNIDENTIFIED");
                if (lastNameField.getText().isEmpty())  lastNameField.setText("PERSON");
                genderCombo.setValue(Gender.UNKNOWN);
            } else {
                if ("UNIDENTIFIED".equals(firstNameField.getText())) firstNameField.clear();
                if ("PERSON".equals(lastNameField.getText()))        lastNameField.clear();
            }
        });
    }

    @FXML
    private void handleUploadPhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Photo");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png","*.jpg","*.jpeg"));
        File file = fc.showOpenDialog(firstNameField.getScene().getWindow());
        if (file != null) {
            if (file.length() > 2 * 1024 * 1024) {
                alert(Alert.AlertType.WARNING, "Image must be smaller than 2 MB.");
                return;
            }
            selectedPhotoFile = file;
            photoView.setImage(new Image(file.toURI().toString()));
        }
    }

    public void loadPersonRecord(Long id) {
        this.existingPersonId = id;
        personService.findById(id).ifPresent(p -> {
            firstNameField.setText(p.getFirstName());
            lastNameField.setText(p.getLastName());
            genderCombo.setValue(p.getGender());
            dobPicker.setValue(p.getDateOfBirth());
            nationalIdField.setText(p.getNationalId() != null ? p.getNationalId() : "");
            if (p.getPhoto() != null)
                photoView.setImage(new Image(new java.io.ByteArrayInputStream(p.getPhoto())));
            if (p.getHeightCm() != null) heightField.setText(String.valueOf(p.getHeightCm()));
            if (p.getWeightKg() != null) weightField.setText(String.valueOf(p.getWeightKg()));
            marksArea.setText(p.getDistinguishingMarks() != null ? p.getDistinguishingMarks() : "");
            districtCombo.setValue(p.getDistrict());
            cityCombo.setValue(p.getCity());
            areaCombo.setValue(p.getArea());
            addressArea.setText(p.getAddress() != null ? p.getAddress() : "");
            aliasesField.setText(p.getAliases() != null ? p.getAliases() : "");
            gangField.setText(p.getGangAffiliation() != null ? p.getGangAffiliation() : "");
            warrantCheck.setSelected(p.isHasActiveWarrant());
            personStatusCombo.setValue(p.getPersonStatus());
            if (p.getMedicalRecord() != null) {
                bloodGroupCombo.setValue(p.getMedicalRecord().getBloodGroup());
                dnaProfileField.setText(p.getMedicalRecord().getDnaProfile() != null ? p.getMedicalRecord().getDnaProfile() : "");
                diseasesArea.setText(p.getMedicalRecord().getKnownDiseases() != null ? p.getMedicalRecord().getKnownDiseases() : "");
                injuriesArea.setText(p.getMedicalRecord().getInjuries() != null ? p.getMedicalRecord().getInjuries() : "");
                medicalNotesArea.setText(p.getMedicalRecord().getMedicalNotes() != null ? p.getMedicalRecord().getMedicalNotes() : "");
            }
            if (formTitle != null) formTitle.setText("Edit Profile: " + p.getFirstName() + " " + p.getLastName());
            if (saveBtn   != null) saveBtn.setText("UPDATE PROFILE");
        });
    }

    @FXML
    private void handleSave() {
        if (!validateInputs()) return;

        if (com.cms.service.SessionManager.getInstance().getCurrentUser().getRole()
                == com.cms.model.enums.Role.ANALYST) {
            alert(Alert.AlertType.ERROR, "Analysts have read-only access and cannot modify records.");
            return;
        }

        try {
            Person person = (existingPersonId == null)
                ? new Person()
                : personService.findById(existingPersonId).orElse(new Person());

            person.setFirstName(firstNameField.getText().trim());
            person.setLastName(lastNameField.getText().trim());
            person.setGender(genderCombo.getValue() != null ? genderCombo.getValue() : Gender.UNKNOWN);
            person.setDateOfBirth(dobPicker.getValue());
            person.setNationalId(nationalIdField.getText().trim().isEmpty() ? null : nationalIdField.getText().trim());
            person.setNationality(nationalityCombo.getValue());
            person.setDistrict(districtCombo.getValue());
            person.setCity(cityCombo.getValue());
            person.setArea(areaCombo.getValue());
            person.setAddress(addressArea.getText().trim());
            person.setDistinguishingMarks(marksArea.getText().trim());
            person.setAliases(aliasesField.getText().trim());
            person.setGangAffiliation(gangField.getText().trim());
            person.setHasActiveWarrant(warrantCheck.isSelected());
            person.setPersonStatus(personStatusCombo.getValue() != null ? personStatusCombo.getValue() : PersonStatus.UNKNOWN);

            if (!heightField.getText().trim().isEmpty()) {
                try { person.setHeightCm(Short.valueOf(heightField.getText().trim())); }
                catch (NumberFormatException ignore) {}
            }
            if (!weightField.getText().trim().isEmpty()) {
                try { person.setWeightKg(Short.valueOf(weightField.getText().trim())); }
                catch (NumberFormatException ignore) {}
            }

            if (selectedPhotoFile != null) {
                try { person.setPhoto(java.nio.file.Files.readAllBytes(selectedPhotoFile.toPath())); }
                catch (Exception ignore) {}
            }

            // Medical record — always set person to maintain bidirectional link
            MedicalRecord mr = person.getMedicalRecord();
            if (mr == null) mr = new MedicalRecord();
            mr.setBloodGroup(bloodGroupCombo.getValue() != null ? bloodGroupCombo.getValue() : BloodGroup.UNKNOWN);
            mr.setDnaProfile(dnaProfileField.getText().trim());
            mr.setKnownDiseases(diseasesArea.getText().trim());
            mr.setInjuries(injuriesArea.getText().trim());
            mr.setMedicalNotes(medicalNotesArea.getText().trim());
            person.setMedicalRecord(mr); // this sets mr.setPerson(person) via setter

            final Person finalPerson = person;
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<>() {
                @Override protected Void call() {
                    personService.save(finalPerson);
                    return null;
                }
            };
            task.setOnSucceeded(e -> {
                logger.info("Person saved: {} {}", finalPerson.getFirstName(), finalPerson.getLastName());
                alert(Alert.AlertType.INFORMATION,
                    "✅ Profile for " + finalPerson.getFirstName() + " " + finalPerson.getLastName() + " saved successfully!");
                handleClear();
            });
            task.setOnFailed(e -> {
                Throwable ex = task.getException();
                logger.error("Failed to save person", ex);
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                if (msg.toLowerCase().contains("constraint") || msg.toLowerCase().contains("duplicate"))
                    alert(Alert.AlertType.ERROR, "A person with this CNIC is already registered. Please check and try again.");
                else
                    alert(Alert.AlertType.ERROR, "Save failed: " + msg);
            });
            new Thread(task).start();

        } catch (Exception e) {
            logger.error("Failed mapping person data", e);
            alert(Alert.AlertType.ERROR, "Error: " + e.getMessage());
        }
    }

    private boolean validateInputs() {
        String fn = firstNameField.getText().trim();
        String ln = lastNameField.getText().trim();

        if (fn.isEmpty()) {
            alert(Alert.AlertType.WARNING, "First name is required.");
            return false;
        }
        if (ln.isEmpty()) {
            alert(Alert.AlertType.WARNING, "Last name is required.");
            return false;
        }
        // Allow letters, spaces, hyphens, apostrophes, and dots
        if (!fn.matches("^[a-zA-Z\\s'\\-.]+$") || !ln.matches("^[a-zA-Z\\s'\\-.]+$")) {
            alert(Alert.AlertType.WARNING, "Names can only contain letters, spaces, hyphens, apostrophes and dots.");
            return false;
        }
        if (!unknownPersonCheck.isSelected()) {
            if (dobPicker.getValue() != null && dobPicker.getValue().isAfter(java.time.LocalDate.now())) {
                alert(Alert.AlertType.WARNING, "Date of birth cannot be in the future.");
                return false;
            }
            String cnic = nationalIdField.getText().trim();
            if (!cnic.isEmpty() && !cnic.matches("^\\d{5}-\\d{7}-\\d{1}$")) {
                alert(Alert.AlertType.WARNING, "CNIC format must be: XXXXX-XXXXXXX-X  (e.g. 35201-1234567-1)\nLeave blank if unknown.");
                return false;
            }
        }
        return true;
    }

    @FXML
    private void handleBack() {
        com.cms.service.NavigationService.getInstance()
            .navigateTo("Person Registry", "/fxml/modules/PersonRegistry.fxml", null);
    }

    @FXML
    private void handleClear() {
        unknownPersonCheck.setSelected(false);
        firstNameField.clear(); lastNameField.clear();
        genderCombo.setValue(null); dobPicker.setValue(null);
        nationalityCombo.setValue(null); nationalIdField.clear();
        districtCombo.setValue(null); cityCombo.setValue(null); areaCombo.setValue(null);
        heightField.clear(); weightField.clear();
        marksArea.clear(); addressArea.clear();
        aliasesField.clear(); gangField.clear();
        warrantCheck.setSelected(false); personStatusCombo.setValue(null);
        bloodGroupCombo.setValue(null); dnaProfileField.clear();
        diseasesArea.clear(); injuriesArea.clear(); medicalNotesArea.clear();
        selectedPhotoFile = null;
        if (photoView != null) photoView.setImage(null);
        existingPersonId = null;
        if (formTitle != null) formTitle.setText("Register Person Profile");
        if (saveBtn   != null) saveBtn.setText("SAVE PROFILE");
    }

    private void alert(Alert.AlertType type, String msg) {
        javafx.application.Platform.runLater(() -> new Alert(type, msg).showAndWait());
    }
}
