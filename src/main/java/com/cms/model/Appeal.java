package com.cms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "appeals")
public class Appeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_case_id", nullable = false)
    private CourtCase courtCase;

    @Column(name = "appeal_number", unique = true, nullable = false)
    private String appealNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by")
    private User filedBy;

    @Column(name = "filed_at", nullable = false)
    private LocalDate filedAt;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String grounds;

    @Column(nullable = false)
    private String status = "FILED"; // FILED, UNDER_REVIEW, HEARING, GRANTED, DENIED, WITHDRAWN

    @Column(columnDefinition = "TEXT")
    private String outcome;

    @Column(name = "decided_at")
    private LocalDate decidedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (filedAt == null) filedAt = LocalDate.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CourtCase getCourtCase() { return courtCase; }
    public void setCourtCase(CourtCase courtCase) { this.courtCase = courtCase; }

    public String getAppealNumber() { return appealNumber; }
    public void setAppealNumber(String appealNumber) { this.appealNumber = appealNumber; }

    public User getFiledBy() { return filedBy; }
    public void setFiledBy(User filedBy) { this.filedBy = filedBy; }

    public LocalDate getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDate filedAt) { this.filedAt = filedAt; }

    public String getGrounds() { return grounds; }
    public void setGrounds(String grounds) { this.grounds = grounds; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public LocalDate getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDate decidedAt) { this.decidedAt = decidedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
