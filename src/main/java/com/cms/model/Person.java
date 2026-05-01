package com.cms.model;

import com.cms.model.enums.Gender;
import com.cms.model.enums.PersonStatus;
import com.cms.model.enums.RiskScore;
import com.cms.model.geo.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "persons",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "national_id"),
                @UniqueConstraint(columnNames = "email")
        }
)
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "aliases", columnDefinition = "TEXT")
    private String aliases;

    @Column(nullable = true)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nationality_country_id")
    private Country nationality;

    @Column(name = "national_id", unique = true, length = 20, nullable = true)
    private String nationalId;

    @Positive
    @Column(name = "height_cm")
    private Short heightCm;

    @Positive
    @Column(name = "weight_kg")
    private Short weightKg;

    @Size(max = 50)
    @Column(name = "eye_color")
    private String eyeColor;

    @Size(max = 50)
    @Column(name = "hair_color")
    private String hairColor;

    @Size(max = 50)
    private String build;

    @Column(name = "distinguishing_marks", columnDefinition = "TEXT")
    private String distinguishingMarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "is_identified", nullable = false)
    private boolean isIdentified = true;

    @Email
    private String email;

    @Lob
    @Column(name = "photo", columnDefinition = "MEDIUMBLOB")
    private byte[] photo;

    @Column(name = "has_active_warrant")
    private boolean hasActiveWarrant = false;

    @Column(name = "is_high_risk")
    private boolean highRisk = false;

    @Column(name = "gang_affiliation")
    private String gangAffiliation;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_score")
    private RiskScore riskScore;

    @Column(name = "risk_score_updated_at")
    private LocalDateTime riskScoreUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_status", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private PersonStatus personStatus = PersonStatus.UNKNOWN;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "person", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MedicalRecord medicalRecord;

    // ================= LIFECYCLE =================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        validateCoreFields();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        validateCoreFields();
    }

    // ================= DOMAIN VALIDATION =================

    private void validateCoreFields() {
        if (!isIdentified) {
            // For unidentified bodies/suspects, skip full validation but ensure a placeholder name
            if (firstName == null || firstName.isBlank()) {
                this.firstName = "Unknown";
            }
            if (lastName == null || lastName.isBlank()) {
                this.lastName = "Unknown";
            }
            return;
        }

        if (firstName == null || firstName.isBlank()) {
            throw new IllegalStateException("First name cannot be empty");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalStateException("Last name cannot be empty");
        }
    }

    // ================= DOMAIN METHODS =================

    public void markHighRisk(RiskScore score) {
        this.riskScore = score;
        this.highRisk = (score != null && score.ordinal() >= RiskScore.HIGH.ordinal());
        this.riskScoreUpdatedAt = LocalDateTime.now();
    }

    public void flagWarrant() {
        this.hasActiveWarrant = true;
    }

    public void clearWarrant() {
        this.hasActiveWarrant = false;
    }

    // ================= GETTERS (KEEP CLEAN) =================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public PersonStatus getPersonStatus() { return personStatus; }
    public void setPersonStatus(PersonStatus personStatus) { this.personStatus = personStatus; }

    public byte[] getPhoto() { return photo; }
    public void setPhoto(byte[] photo) { this.photo = photo; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public Country getNationality() { return nationality; }
    public void setNationality(Country nationality) { this.nationality = nationality; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public Short getHeightCm() { return heightCm; }
    public void setHeightCm(Short heightCm) { this.heightCm = heightCm; }

    public Short getWeightKg() { return weightKg; }
    public void setWeightKg(Short weightKg) { this.weightKg = weightKg; }

    public String getDistinguishingMarks() { return distinguishingMarks; }
    public void setDistinguishingMarks(String distinguishingMarks) { this.distinguishingMarks = distinguishingMarks; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public boolean isIdentified() { return isIdentified; }
    public void setIdentified(boolean identified) { this.isIdentified = identified; }

    public District getDistrict() { return district; }
    public void setDistrict(District district) { this.district = district; }

    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }

    public Area getArea() { return area; }
    public void setArea(Area area) { this.area = area; }

    public boolean isHighRisk() { return highRisk; }
    public void setHighRisk(boolean highRisk) { this.highRisk = highRisk; }

    public boolean isHasActiveWarrant() { return hasActiveWarrant; }
    public void setHasActiveWarrant(boolean hasActiveWarrant) { this.hasActiveWarrant = hasActiveWarrant; }

    public String getGangAffiliation() { return gangAffiliation; }
    public void setGangAffiliation(String gangAffiliation) { this.gangAffiliation = gangAffiliation; }

    public RiskScore getRiskScore() { return riskScore; }
    public void setRiskScore(RiskScore riskScore) { this.riskScore = riskScore; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public void setMedicalRecord(MedicalRecord medicalRecord) {
        this.medicalRecord = medicalRecord;
        if (medicalRecord != null) {
            medicalRecord.setPerson(this);
        }
    }
}