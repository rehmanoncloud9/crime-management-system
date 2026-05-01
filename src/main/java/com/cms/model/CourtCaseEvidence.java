package com.cms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Junction table between CourtCase and Evidence as per ERD requirements (W-16).
 * Tracks when evidence was presented in court.
 */
@Entity
@Table(name = "court_case_evidence")
public class CourtCaseEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_case_id", nullable = false)
    private CourtCase courtCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = false)
    private Evidence evidence;

    @Column(name = "presented_at")
    private LocalDateTime presentedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist
    protected void onCreate() {
        if (presentedAt == null) presentedAt = LocalDateTime.now();
    }

    public CourtCaseEvidence() {}

    public CourtCaseEvidence(CourtCase courtCase, Evidence evidence) {
        this.courtCase = courtCase;
        this.evidence = evidence;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CourtCase getCourtCase() { return courtCase; }
    public void setCourtCase(CourtCase courtCase) { this.courtCase = courtCase; }

    public Evidence getEvidence() { return evidence; }
    public void setEvidence(Evidence evidence) { this.evidence = evidence; }

    public LocalDateTime getPresentedAt() { return presentedAt; }
    public void setPresentedAt(LocalDateTime presentedAt) { this.presentedAt = presentedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
