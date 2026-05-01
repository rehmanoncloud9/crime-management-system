package com.cms.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key class for weak entities: CaseSuspect, CaseVictim, CaseWitness.
 * ERD: composite PK (case_id, person_id) — each weak entity depends on both CASE_FILES and PERSONS.
 */
public class CasePersonId implements Serializable {

    private Long caseFile;  // matches the @Id field name in the entity
    private Long person;    // matches the @Id field name in the entity

    public CasePersonId() {}

    public CasePersonId(Long caseFile, Long person) {
        this.caseFile = caseFile;
        this.person = person;
    }

    public Long getCaseFile() { return caseFile; }
    public void setCaseFile(Long caseFile) { this.caseFile = caseFile; }

    public Long getPerson() { return person; }
    public void setPerson(Long person) { this.person = person; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CasePersonId that)) return false;
        return Objects.equals(caseFile, that.caseFile) && Objects.equals(person, that.person);
    }

    @Override
    public int hashCode() {
        return Objects.hash(caseFile, person);
    }
}
