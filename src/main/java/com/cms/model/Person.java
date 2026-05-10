package com.cms.model;

import com.cms.model.enums.Gender;
import com.cms.model.enums.PersonStatus;
import com.cms.model.enums.RiskScore;
import com.cms.model.geo.*;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "persons",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "national_id"),
                @UniqueConstraint(columnNames = "email")
        },
        indexes = {
                @Index(name = "idx_person_national_id", columnList = "national_id"),
                @Index(name = "idx_person_names", columnList = "first_name, last_name")
        }
)
public class Person {

    @Embeddable
    public static class DistinguishingMark {
        @Column(name = "description", nullable = false)
        private String description;
        @Column(name = "body_location")
        private String bodyLocation;

        public DistinguishingMark() {}
        public DistinguishingMark(String description, String bodyLocation) {
            this.description = description;
            this.bodyLocation = bodyLocation;
        }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getBodyLocation() { return bodyLocation; }
        public void setBodyLocation(String bodyLocation) { this.bodyLocation = bodyLocation; }
    }

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

    @Column(nullable = true)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nationality_country_id")
    private Country nationality;

    @Column(name = "national_id", unique = true, length = 20, nullable = true)
    // 🛡️ SECURITY WARNING (C-11): This field contains PII and is stored in plaintext. 
    // Recommended: Use @Convert(converter = AESConverter.class) for production.
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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "person_marks", joinColumns = @JoinColumn(name = "person_id"))
    private Set<DistinguishingMark> distinguishingMarks = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "person_aliases", joinColumns = @JoinColumn(name = "person_id"))
    @Column(name = "alias")
    private Set<String> aliases = new HashSet<>();

    // ── UI Synchronization Helpers (C-14) ──

    /** Returns aliases as a comma-separated string for TableView binding */
    public String getAliasesDisplay() {
        return aliases == null || aliases.isEmpty() ? "None" : String.join(", ", aliases);
    }

    /** Returns marks as a comma-separated string for TableView binding */
    public String getMarksDisplay() {
        if (distinguishingMarks == null || distinguishingMarks.isEmpty()) return "None";
        return distinguishingMarks.stream()
                .map(m -> m.getDescription() + (m.getBodyLocation() != null ? " (" + m.getBodyLocation() + ")" : ""))
                .reduce((a, b) -> a + "; " + b).orElse("None");
    }

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

    @Pattern(regexp = "^\\+?[0-9]{7,15}$")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_score", columnDefinition = "VARCHAR(20)")
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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "person", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MedicalRecord medicalRecord;

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

    private void validateCoreFields() {
        if (!isIdentified) {
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

    public void markHighRisk(RiskScore score) {
        this.riskScore = score;
        this.highRisk = (score != null && score.ordinal() >= RiskScore.HIGH.ordinal());
        this.riskScoreUpdatedAt = LocalDateTime.now();
    }

    // ── CONVENIENCE HELPERS ──

    /** Returns first and last name combined */
    public String getFullName() {
        return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
    }

    /** Alias for getNationalId() used in local context */
    public String getCnic() {
        return nationalId;
    }

    @Transient
    public Integer getAge() {
        if (dateOfBirth == null) return null;
        return java.time.Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    public void flagWarrant() {
        this.hasActiveWarrant = true;
    }

    public void clearWarrant() {
        this.hasActiveWarrant = false;
    }

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

    public Set<String> getAliases() { return aliases; }
    public void setAliases(Set<String> aliases) { this.aliases = aliases; }

    public void addAlias(String alias) {
        if (alias != null && !alias.isBlank()) this.aliases.add(alias.trim());
    }

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

    public Set<DistinguishingMark> getDistinguishingMarks() { return distinguishingMarks; }
    public void setDistinguishingMarks(Set<DistinguishingMark> marks) { this.distinguishingMarks = marks; }

    public void addDistinguishingMark(String desc) {
        this.addDistinguishingMark(desc, "N/A");
    }

    public void addDistinguishingMark(String desc, String location) {
        this.distinguishingMarks.add(new DistinguishingMark(desc, location));
    }

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

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public boolean isDeleted() { return deletedAt != null; }


    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public void setMedicalRecord(MedicalRecord medicalRecord) {
        this.medicalRecord = medicalRecord;
        if (medicalRecord != null) {
            medicalRecord.setPerson(this);
        }
    }
}
