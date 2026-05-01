package com.cms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Weak entity: CASE_WITNESSES — ERD Specialisation 2 (Disjoint ⊕, Partial).
 * Composite PK (case_id, person_id). Depends on both CASE_FILES and PERSONS.
 */
@Entity
@Table(name = "case_witnesses")
@IdClass(CasePersonId.class)
public class CaseWitness {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private CaseFile caseFile;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    // ── Weak entity descriptive attributes ──────────────────────────────────

    @Column(columnDefinition = "TEXT")
    private String statement;

    @Column(name = "reliability_rating")
    private Integer reliabilityRating;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    // ── Constructors ────────────────────────────────────────────────────────

    public CaseWitness() {
        this.addedAt = LocalDateTime.now();
    }

    public CaseWitness(CaseFile caseFile, Person person) {
        this();
        this.caseFile = caseFile;
        this.person = person;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) { this.caseFile = caseFile; }

    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }

    public Integer getReliabilityRating() { return reliabilityRating; }
    public void setReliabilityRating(Integer reliabilityRating) {
        if (reliabilityRating != null && (reliabilityRating < 1 || reliabilityRating > 5)) {
            throw new IllegalArgumentException("reliabilityRating must be between 1 and 5");
        }
        this.reliabilityRating = reliabilityRating;
    }

    public User getAddedBy() { return addedBy; }
    public void setAddedBy(User addedBy) { this.addedBy = addedBy; }

    public LocalDateTime getAddedAt() { return addedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaseWitness that)) return false;
        return caseFile != null && person != null
                && caseFile.getId() != null && person.getId() != null
                && caseFile.getId().equals(that.caseFile != null ? that.caseFile.getId() : null)
                && person.getId().equals(that.person != null ? that.person.getId() : null);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "CaseWitness{caseId=" + (caseFile != null ? caseFile.getId() : null) +
                ", personId=" + (person != null ? person.getId() : null) + '}';
    }
}
