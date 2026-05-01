package com.cms.model;

import com.cms.model.enums.CaseStatus;
import com.cms.model.enums.CasePriority;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "case_files",
        indexes = {
                @Index(name = "idx_case_number", columnList = "case_number"),
                @Index(name = "idx_case_status", columnList = "status"),
                @Index(name = "idx_case_investigator", columnList = "primary_investigator_id")
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
    @Column(name = "priority", nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    private CasePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private CaseStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closure_reason", columnDefinition = "TEXT")
    private String closureReason;

    /**
     * ERD Specialisation 2 — Weak entities with composite PK (case_id, person_id).
     * Disjoint (⊕): a person can only hold ONE role per case.
     * Partial: not every person needs to be in any of these.
     */
    @OneToMany(mappedBy = "caseFile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CaseSuspect> caseSuspects = new HashSet<>();

    @OneToMany(mappedBy = "caseFile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CaseVictim> caseVictims = new HashSet<>();

    @OneToMany(mappedBy = "caseFile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CaseWitness> caseWitnesses = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "caseFile", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RelatedCase> relatedCases = new HashSet<>();

    // Constructors
    public CaseFile() {
        this.openedAt = LocalDateTime.now();
        this.status = CaseStatus.OPEN;
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
            status = CaseStatus.OPEN;
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

    public CaseStatus getStatus() { return status; }

    // Controlled state transition
    public void setStatus(CaseStatus status) {
        Objects.requireNonNull(status, "status cannot be null");

        // Example rule: cannot reopen closed case without explicit logic
        if (isClosed() && status != CaseStatus.CLOSED_CONVICTED && status != CaseStatus.CLOSED_ACQUITTED && status != CaseStatus.CLOSED_UNSOLVED) {
            throw new IllegalStateException("Cannot change status of a closed case");
        }

        this.status = status;
    }

    public boolean isClosed() {
        return status == CaseStatus.CLOSED_CONVICTED || status == CaseStatus.CLOSED_ACQUITTED || status == CaseStatus.CLOSED_UNSOLVED;
    }

    public LocalDateTime getOpenedAt() { return openedAt; }

    public LocalDateTime getClosedAt() { return closedAt; }

    public String getClosureReason() { return closureReason; }

    public void closeCase(CaseStatus closureState, String reason) {
        if (isClosed()) {
            throw new IllegalStateException("Case is already closed");
        }

        this.status = closureState;
        this.closedAt = LocalDateTime.now();
        this.closureReason = reason;
    }



    // ── Direct weak-entity collection accessors ────────────────────────────

    public Set<CaseSuspect> getCaseSuspects() { return caseSuspects; }
    public void setCaseSuspects(Set<CaseSuspect> caseSuspects) { this.caseSuspects = caseSuspects; }

    public Set<CaseVictim> getCaseVictims() { return caseVictims; }
    public void setCaseVictims(Set<CaseVictim> caseVictims) { this.caseVictims = caseVictims; }

    public Set<CaseWitness> getCaseWitnesses() { return caseWitnesses; }
    public void setCaseWitnesses(Set<CaseWitness> caseWitnesses) { this.caseWitnesses = caseWitnesses; }

    // ── Convenience accessors returning Set<Person> (backward-compatible) ─

    @Transient
    public Set<Person> getSuspects() {
        return caseSuspects.stream().map(CaseSuspect::getPerson).collect(Collectors.toSet());
    }

    @Transient
    public Set<Person> getVictims() {
        return caseVictims.stream().map(CaseVictim::getPerson).collect(Collectors.toSet());
    }

    @Transient
    public Set<Person> getWitnesses() {
        return caseWitnesses.stream().map(CaseWitness::getPerson).collect(Collectors.toSet());
    }

    // ── Convenience mutators (create weak entity internally) ─────────────

    public void addSuspect(Person person) {
        if (person != null) {
            this.caseSuspects.add(new CaseSuspect(this, person));
        }
    }

    public void addVictim(Person person) {
        if (person != null) {
            this.caseVictims.add(new CaseVictim(this, person));
        }
    }

    public void addWitness(Person person) {
        if (person != null) {
            this.caseWitnesses.add(new CaseWitness(this, person));
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