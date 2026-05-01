package com.cms.model;

import com.cms.model.enums.IncidentStatus;
import com.cms.model.enums.CasePriority;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "case_files",
        indexes = {
                @Index(name = "idx_case_number", columnList = "case_number"),
                @Index(name = "idx_case_status", columnList = "status")
        })
public class CaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_number", unique = true, nullable = false, length = 50, updatable = false)
    private String caseNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", nullable = false, unique = true)
    private CrimeIncident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_investigator_id")
    private User primaryInvestigator;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private CasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IncidentStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_reason", columnDefinition = "TEXT")
    private String closureReason;

    // Keep as TEXT for portability
    @Column(name = "related_case_ids", columnDefinition = "TEXT")
    private String relatedCaseIds;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "case_suspects",
            joinColumns = @JoinColumn(name = "case_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private Set<Person> suspects = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "case_victims",
            joinColumns = @JoinColumn(name = "case_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private Set<Person> victims = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "case_witnesses",
            joinColumns = @JoinColumn(name = "case_id"),
            inverseJoinColumns = @JoinColumn(name = "person_id")
    )
    private Set<Person> witnesses = new HashSet<>();

    /**
     * One-to-many relationship with RelatedCase.
     * Maps to case_files via the related_cases table (1NF normalized).
     * This replaces the legacy comma-separated related_case_ids field.
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "caseFile", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RelatedCase> relatedCases = new HashSet<>();

    // Constructors
    public CaseFile() {
        this.openedAt = LocalDateTime.now();
        this.status = IncidentStatus.OPEN;
        this.priority = CasePriority.MEDIUM;
    }

    public CaseFile(String caseNumber, CrimeIncident incident) {
        this();
        this.caseNumber = validate(caseNumber, "caseNumber");
        this.incident = Objects.requireNonNull(incident, "incident cannot be null");
    }

    // Lifecycle Hook
    @PrePersist
    protected void onCreate() {
        if (openedAt == null) {
            openedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = IncidentStatus.OPEN;
        }
        if (priority == null) {
            priority = CasePriority.MEDIUM;
        }
    }

    // Getters
    public Long getId() { return id; }

    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) {
        if (this.caseNumber != null) {
            throw new IllegalStateException("caseNumber cannot be changed once set");
        }
        this.caseNumber = validate(caseNumber, "caseNumber");
    }

    public CrimeIncident getIncident() { return incident; }
    public void setIncident(CrimeIncident incident) {
        this.incident = Objects.requireNonNull(incident, "incident cannot be null");
    }

    public User getPrimaryInvestigator() { return primaryInvestigator; }
    public void setPrimaryInvestigator(User primaryInvestigator) {
        this.primaryInvestigator = primaryInvestigator;
    }

    public CasePriority getPriority() { return priority; }
    public void setPriority(CasePriority priority) {
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
    }

    public IncidentStatus getStatus() { return status; }

    // Controlled state transition
    public void setStatus(IncidentStatus status) {
        Objects.requireNonNull(status, "status cannot be null");

        // Example rule: cannot reopen closed case without explicit logic
        if (this.status == IncidentStatus.CLOSED && status != IncidentStatus.CLOSED) {
            throw new IllegalStateException("Cannot change status of a closed case");
        }

        this.status = status;
    }

    public LocalDateTime getOpenedAt() { return openedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }

    public String getClosureReason() { return closureReason; }

    public void closeCase(String reason) {
        if (this.status == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Case is already closed");
        }

        this.status = IncidentStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.closureReason = validate(reason, "closureReason");
    }

    public void closeCase(IncidentStatus closureStatus, String reason) {
        if (closureStatus == null || (
                closureStatus != IncidentStatus.CLOSED &&
                closureStatus != IncidentStatus.CLOSED_CONVICTED &&
                closureStatus != IncidentStatus.CLOSED_ACQUITTED &&
                closureStatus != IncidentStatus.CLOSED_UNSOLVED)) {
            throw new IllegalArgumentException("Invalid closure status");
        }
        if (this.status == IncidentStatus.CLOSED ||
                this.status == IncidentStatus.CLOSED_CONVICTED ||
                this.status == IncidentStatus.CLOSED_ACQUITTED ||
                this.status == IncidentStatus.CLOSED_UNSOLVED) {
            throw new IllegalStateException("Case is already closed");
        }
        this.status = closureStatus;
        this.closedAt = LocalDateTime.now();
        this.closureReason = validate(reason, "closureReason");
    }

    public String getRelatedCaseIds() { return relatedCaseIds; }
    public void setRelatedCaseIds(String relatedCaseIds) {
        this.relatedCaseIds = sanitize(relatedCaseIds);
    }

    public Set<Person> getSuspects() { return suspects; }
    public void setSuspects(Set<Person> suspects) { this.suspects = suspects; }
    
    public void addSuspect(Person person) {
        if (person != null) {
            this.suspects.add(person);
        }
    }

    public Set<Person> getVictims() { return victims; }
    public void setVictims(Set<Person> victims) { this.victims = victims; }

    public void addVictim(Person person) {
        if (person != null) {
            this.victims.add(person);
        }
    }

    public Set<Person> getWitnesses() { return witnesses; }
    public void setWitnesses(Set<Person> witnesses) { this.witnesses = witnesses; }

    public void addWitness(Person person) {
        if (person != null) {
            this.witnesses.add(person);
        }
    }

    public Set<RelatedCase> getRelatedCases() { return relatedCases; }
    public void setRelatedCases(Set<RelatedCase> relatedCases) { this.relatedCases = relatedCases; }

    public void addRelatedCase(RelatedCase relatedCase) {
        if (relatedCase != null) {
            this.relatedCases.add(relatedCase);
        }
    }

    // Utility
    private String validate(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be null or empty");
        }
        return value.trim();
    }

    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // Equals & HashCode (JPA safe)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaseFile)) return false;
        CaseFile that = (CaseFile) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debugging
    @Override
    public String toString() {
        return "CaseFile{" +
                "id=" + id +
                ", caseNumber='" + caseNumber + '\'' +
                ", status=" + status +
                ", openedAt=" + openedAt +
                ", closedAt=" + closedAt +
                '}';
    }
}