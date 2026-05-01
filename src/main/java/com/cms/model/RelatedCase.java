package com.cms.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Entity to represent relationships between case files.
 * Fixes 1NF violation: replaces comma-separated related_case_ids with proper M:M relationship.
 * 
 * This allows recording which cases are related and the nature of their relationship.
 */
@Entity
@Table(name = "related_cases",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"case_id", "related_id"},
                        name = "uk_case_pair"
                )
        },
        indexes = {
                @Index(name = "idx_related_case_id", columnList = "case_id"),
                @Index(name = "idx_related_id", columnList = "related_id"),
                @Index(name = "idx_relation_type", columnList = "relation_type")
        })
public class RelatedCase {

    @EmbeddedId
    private RelatedCaseId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", insertable = false, updatable = false)
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_id", insertable = false, updatable = false)
    private CaseFile relatedCaseFile;

    @Column(name = "relation_type", length = 50)
    private String relationType; // e.g., "LINKED", "PREDECESSOR", "SIMILAR_MODUS", "GANG_RELATED"

    // Constructors
    public RelatedCase() {}

    public RelatedCase(CaseFile caseFile, CaseFile relatedCaseFile, String relationType) {
        this.caseFile = Objects.requireNonNull(caseFile, "caseFile cannot be null");
        this.relatedCaseFile = Objects.requireNonNull(relatedCaseFile, "relatedCaseFile cannot be null");
        this.relationType = relationType;
        
        // Create the composite key
        this.id = new RelatedCaseId(caseFile.getId(), relatedCaseFile.getId());
        
        // Ensure case_id < related_id to prevent duplicates (A->B and B->A)
        if (caseFile.getId() > relatedCaseFile.getId()) {
            Long temp = this.id.getCaseId();
            this.id.setCaseId(this.id.getRelatedId());
            this.id.setRelatedId(temp);
            CaseFile tempCase = this.caseFile;
            this.caseFile = this.relatedCaseFile;
            this.relatedCaseFile = tempCase;
        }
    }

    // Getters & Setters
    public RelatedCaseId getId() { return id; }
    public void setId(RelatedCaseId id) { this.id = id; }

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) {
        this.caseFile = Objects.requireNonNull(caseFile, "caseFile cannot be null");
    }

    public CaseFile getRelatedCaseFile() { return relatedCaseFile; }
    public void setRelatedCaseFile(CaseFile relatedCaseFile) {
        this.relatedCaseFile = Objects.requireNonNull(relatedCaseFile, "relatedCaseFile cannot be null");
    }

    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }

    // equals & hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelatedCase)) return false;
        RelatedCase that = (RelatedCase) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RelatedCase{" +
                "caseId=" + (id != null ? id.getCaseId() : null) +
                ", relatedId=" + (id != null ? id.getRelatedId() : null) +
                ", relationType='" + relationType + '\'' +
                '}';
    }

    // ===== COMPOSITE KEY CLASS =====
    @Embeddable
    public static class RelatedCaseId implements Serializable {
        @Column(name = "case_id")
        private Long caseId;

        @Column(name = "related_id")
        private Long relatedId;

        public RelatedCaseId() {}

        public RelatedCaseId(Long caseId, Long relatedId) {
            this.caseId = caseId;
            this.relatedId = relatedId;
        }

        public Long getCaseId() { return caseId; }
        public void setCaseId(Long caseId) { this.caseId = caseId; }

        public Long getRelatedId() { return relatedId; }
        public void setRelatedId(Long relatedId) { this.relatedId = relatedId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelatedCaseId)) return false;
            RelatedCaseId that = (RelatedCaseId) o;
            return Objects.equals(caseId, that.caseId) &&
                   Objects.equals(relatedId, that.relatedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(caseId, relatedId);
        }

        @Override
        public String toString() {
            return "RelatedCaseId{" +
                    "caseId=" + caseId +
                    ", relatedId=" + relatedId +
                    '}';
        }
    }
}
