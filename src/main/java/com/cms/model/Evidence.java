package com.cms.model;

import com.cms.model.enums.EvidenceStatus;
import com.cms.model.enums.EvidenceType;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "evidence",
        indexes = {
                @Index(name = "idx_evidence_number", columnList = "evidence_number"),
                @Index(name = "idx_evidence_case", columnList = "case_id"),
                @Index(name = "idx_evidence_status", columnList = "status")
        })
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_number", unique = true, nullable = false, updatable = false)
    private String evidenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suspect_id")
    private Person suspect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private LocalDateTime collectedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by", nullable = false)
    private User collectedBy;

    @Column(name = "collection_location", nullable = false)
    private String collectionLocation;

    @Column(name = "current_storage_location")
    private String currentStorageLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceStatus status;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public Evidence() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.collectedAt = now; 
        this.status = EvidenceStatus.COLLECTED;
    }

    public Evidence(String evidenceNumber, CaseFile caseFile, EvidenceType type, User collectedBy) {
        this();
        this.evidenceNumber = validate(evidenceNumber, "evidenceNumber");
        this.caseFile = Objects.requireNonNull(caseFile, "caseFile cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.collectedBy = Objects.requireNonNull(collectedBy, "collectedBy cannot be null");
        this.collectedAt = LocalDateTime.now();
    }

    // Lifecycle hooks
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        // Ensure updatedAt is not null for initial save
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = EvidenceStatus.COLLECTED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }

    public String getEvidenceNumber() { return evidenceNumber; }
    public void setEvidenceNumber(String evidenceNumber) { this.evidenceNumber = validate(evidenceNumber, "evidenceNumber"); }

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) {
        this.caseFile = Objects.requireNonNull(caseFile, "caseFile cannot be null");
    }

    public Person getSuspect() { return suspect; }
    public void setSuspect(Person suspect) {
        this.suspect = suspect;
    }

    public EvidenceType getType() { return type; }
    public void setType(EvidenceType type) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = validate(description, "description");
    }

    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }

    public User getCollectedBy() { return collectedBy; }
    public void setCollectedBy(User collectedBy) { this.collectedBy = Objects.requireNonNull(collectedBy, "collectedBy cannot be null"); }

    public String getCollectionLocation() { return collectionLocation; }
    public void setCollectionLocation(String collectionLocation) {
        this.collectionLocation = validate(collectionLocation, "collectionLocation");
    }

    public String getCurrentStorageLocation() { return currentStorageLocation; }
    public void setCurrentStorageLocation(String currentStorageLocation) {
        this.currentStorageLocation = sanitize(currentStorageLocation);
    }

    public EvidenceStatus getStatus() { return status; }
    public void setStatus(EvidenceStatus status) { this.status = Objects.requireNonNull(status, "status cannot be null"); }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) {
        this.filePath = sanitize(filePath);
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Domain logic (IMPORTANT)
    public void transfer(String newLocation, User handler) {
        if (status == EvidenceStatus.DESTROYED) {
            throw new IllegalStateException("Cannot transfer destroyed evidence");
        }

        this.currentStorageLocation = validate(newLocation, "newLocation");
        this.status = EvidenceStatus.IN_TRANSFER;
    }

    public void store(String location) {
        if (status == EvidenceStatus.DESTROYED) {
            throw new IllegalStateException("Cannot store destroyed evidence");
        }

        this.currentStorageLocation = validate(location, "location");
        this.status = EvidenceStatus.STORED;
    }

    public void markAsDestroyed() {
        this.status = EvidenceStatus.DESTROYED;
        this.currentStorageLocation = null;
    }

    // Validation
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
        if (!(o instanceof Evidence)) return false;
        Evidence that = (Evidence) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Evidence{" +
                "id=" + id +
                ", evidenceNumber='" + evidenceNumber + '\'' +
                ", status=" + status +
                '}';
    }
}
