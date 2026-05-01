package com.cms.model;

import com.cms.model.enums.MOTimeOfDay;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "modus_operandi",
        indexes = {
                @Index(name = "idx_mo_person", columnList = "person_id"),
                @Index(name = "idx_mo_crime_type", columnList = "crime_type_id"),
                @Index(name = "idx_mo_time", columnList = "typical_time_of_day")
        })
public class ModusOperandi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crime_type_id", nullable = false)
    private CrimeType crimeType;

    @Column(name = "method_description", columnDefinition = "TEXT", nullable = false)
    private String methodDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "typical_time_of_day")
    private MOTimeOfDay typicalTimeOfDay;

    @Column(name = "typical_location_type", length = 100)
    private String typicalLocationType;

    @Column(name = "target_type", length = 100)
    private String targetType;

    @Column(name = "tools_used", columnDefinition = "TEXT")
    private String toolsUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noted_by", nullable = false)
    private User notedBy;

    @Column(name = "tags")
    private String tags;

    @Column(name = "noted_at", nullable = false, updatable = false)
    private LocalDateTime notedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public ModusOperandi() {
        this.notedAt = LocalDateTime.now();
    }

    public ModusOperandi(Person person, CrimeType crimeType, User notedBy) {
        this();
        this.person = Objects.requireNonNull(person, "person cannot be null");
        this.crimeType = Objects.requireNonNull(crimeType, "crimeType cannot be null");
        this.notedBy = Objects.requireNonNull(notedBy, "notedBy cannot be null");
    }

    // Lifecycle hooks
    @PrePersist
    protected void onCreate() {
        if (notedAt == null) {
            notedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }

    public Person getPerson() { return person; }
    public void setPerson(Person person) {
        this.person = Objects.requireNonNull(person, "person cannot be null");
    }

    public CrimeType getCrimeType() { return crimeType; }
    public void setCrimeType(CrimeType crimeType) {
        this.crimeType = Objects.requireNonNull(crimeType, "crimeType cannot be null");
    }

    public String getMethodDescription() { return methodDescription; }
    public void setMethodDescription(String methodDescription) {
        this.methodDescription = validate(methodDescription, "methodDescription");
    }

    public MOTimeOfDay getTypicalTimeOfDay() { return typicalTimeOfDay; }
    public void setTypicalTimeOfDay(MOTimeOfDay typicalTimeOfDay) {
        this.typicalTimeOfDay = typicalTimeOfDay;
    }

    public String getTypicalLocationType() { return typicalLocationType; }
    public void setTypicalLocationType(String typicalLocationType) {
        this.typicalLocationType = sanitize(typicalLocationType);
    }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) {
        this.targetType = sanitize(targetType);
    }

    public String getToolsUsed() { return toolsUsed; }
    public void setToolsUsed(String toolsUsed) {
        this.toolsUsed = sanitize(toolsUsed);
    }

    public User getNotedBy() { return notedBy; }

    public String getTags() { return tags; }
    public void setTags(String tags) {
        this.tags = sanitize(tags);
    }

    public LocalDateTime getNotedAt() { return notedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Domain logic (IMPORTANT)
    public void updateAnalysis(String methodDescription, String toolsUsed, String tags) {
        this.methodDescription = validate(methodDescription, "methodDescription");
        this.toolsUsed = sanitize(toolsUsed);
        this.tags = sanitize(tags);
    }

    // Helpers
    private String validate(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be null or empty");
        }
        return value.trim();
    }

    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // equals & hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ModusOperandi)) return false;
        ModusOperandi that = (ModusOperandi) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ModusOperandi{" +
                "id=" + id +
                ", personId=" + (person != null ? person.getId() : null) +
                ", crimeType=" + (crimeType != null ? crimeType.getId() : null) +
                '}';
    }
}