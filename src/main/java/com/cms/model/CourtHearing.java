package com.cms.model;

import jakarta.persistence.*;
import com.cms.model.enums.HearingStatus;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "court_hearings",
        indexes = {
                @Index(name = "idx_hearing_case", columnList = "court_case_id"),
                @Index(name = "idx_hearing_date", columnList = "hearing_date")
        })
public class CourtHearing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_case_id", nullable = false)
    private CourtCase courtCase;

    @Column(name = "hearing_date", nullable = false)
    private LocalDateTime hearingDate;

    @Column(length = 255)
    private String outcome;

    @Column(name = "next_hearing_date")
    private LocalDateTime nextHearingDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "hearing_status", nullable = false, length = 20)
    private HearingStatus hearingStatus;

    // Constructors
    public CourtHearing() {
        this.recordedAt = LocalDateTime.now();
        this.hearingStatus = HearingStatus.SCHEDULED;
    }

    public CourtHearing(CourtCase courtCase, LocalDateTime hearingDate, User recordedBy) {
        this();
        this.courtCase = Objects.requireNonNull(courtCase, "courtCase cannot be null");
        this.hearingDate = Objects.requireNonNull(hearingDate, "hearingDate cannot be null");
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy cannot be null");
    }

    // Lifecycle
    @PrePersist
    protected void onCreate() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
        if (hearingStatus == null) {
            hearingStatus = HearingStatus.SCHEDULED;
        }
    }

    // Getters
    public Long getId() { return id; }

    public CourtCase getCourtCase() { return courtCase; }
    public void setCourtCase(CourtCase courtCase) {
        this.courtCase = Objects.requireNonNull(courtCase, "courtCase cannot be null");
    }

    public LocalDateTime getHearingDate() { return hearingDate; }
    public void setHearingDate(LocalDateTime hearingDate) {
        this.hearingDate = Objects.requireNonNull(hearingDate, "hearingDate cannot be null");
    }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) {
        this.outcome = sanitize(outcome);
    }

    public LocalDateTime getNextHearingDate() { return nextHearingDate; }
    public void setNextHearingDate(LocalDateTime nextHearingDate) {
        if (nextHearingDate != null && nextHearingDate.isBefore(hearingDate)) {
            throw new IllegalArgumentException("nextHearingDate cannot be before hearingDate");
        }
        this.nextHearingDate = nextHearingDate;
    }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) {
        this.recordedBy = Objects.requireNonNull(recordedBy, "recordedBy cannot be null");
    }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public HearingStatus getHearingStatus() { return hearingStatus; }
    public void setHearingStatus(HearingStatus hearingStatus) {
        this.hearingStatus = Objects.requireNonNull(hearingStatus, "hearingStatus cannot be null");
    }

    // Domain logic
    public void concludeHearing(String outcome, LocalDateTime nextDate) {
        this.outcome = sanitize(outcome);

        if (nextDate != null && nextDate.isBefore(hearingDate)) {
            throw new IllegalArgumentException("Next hearing date cannot be before current hearing");
        }

        this.nextHearingDate = nextDate;
    }

    // Utility
    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // Equals & HashCode (JPA safe)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CourtHearing)) return false;
        CourtHearing that = (CourtHearing) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debugging
    @Override
    public String toString() {
        return "CourtHearing{" +
                "id=" + id +
                ", caseId=" + (courtCase != null ? courtCase.getId() : null) +
                ", hearingDate=" + hearingDate +
                ", nextHearingDate=" + nextHearingDate +
                '}';
    }
}