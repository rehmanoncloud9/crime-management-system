package com.cms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Weak entity: CASE_SUSPECTS — ERD Specialisation 2 (Disjoint ⊕, Partial).
 * Composite PK (case_id, person_id). Depends on both CASE_FILES and PERSONS.
 * NOT a bare join table — carries descriptive attributes per ERD requirement.
 */
@Entity
@Table(name = "case_suspects")
@IdClass(CasePersonId.class)
public class CaseSuspect {

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
    private String motive;

    @Column(name = "threat_level", length = 20)
    private String threatLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private User addedBy;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    // ── Constructors ────────────────────────────────────────────────────────

    public CaseSuspect() {
        this.addedAt = LocalDateTime.now();
        this.threatLevel = "LOW";
    }

    public CaseSuspect(CaseFile caseFile, Person person) {
        this();
        this.caseFile = caseFile;
        this.person = person;
    }

    // ── Getters / Setters ───────────────────────────────────────────────────

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) { this.caseFile = caseFile; }

    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }

    public String getMotive() { return motive; }
    public void setMotive(String motive) { this.motive = motive; }

    public String getThreatLevel() { return threatLevel; }
    public void setThreatLevel(String threatLevel) { this.threatLevel = threatLevel; }

    public User getAddedBy() { return addedBy; }
    public void setAddedBy(User addedBy) { this.addedBy = addedBy; }

    public LocalDateTime getAddedAt() { return addedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaseSuspect that)) return false;
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
        return "CaseSuspect{caseId=" + (caseFile != null ? caseFile.getId() : null) +
                ", personId=" + (person != null ? person.getId() : null) +
                ", threatLevel=" + threatLevel + '}';
    }
}
