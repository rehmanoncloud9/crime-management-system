package com.cms.model;

import com.cms.model.enums.CourtStatus;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "court_cases",
        indexes = {
                @Index(name = "idx_court_case_number", columnList = "court_case_number"),
                @Index(name = "idx_court_status", columnList = "status")
        })
public class CourtCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // One case file → one court case (enforced)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private CaseFile caseFile;

    @Column(name = "court_case_number", unique = true, length = 50)
    private String courtCaseNumber;

    @Column(name = "court_name", length = 150)
    private String courtName;

    @Column(name = "judge_name", length = 100)
    private String judgeName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prosecutor_id")
    private User prosecutor;

    @Column(name = "filed_at", nullable = false, updatable = false)
    private LocalDate filedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private CourtStatus status;

    // Constructors
    public CourtCase() {
        this.filedAt = LocalDate.now();
        this.status = CourtStatus.FILED;
    }

    public CourtCase(CaseFile caseFile) {
        this();
        this.caseFile = Objects.requireNonNull(caseFile, "caseFile cannot be null");
    }

    // Lifecycle
    @PrePersist
    protected void onCreate() {
        if (filedAt == null) {
            filedAt = LocalDate.now();
        }
        if (status == null) {
            status = CourtStatus.FILED;
        }
    }

    // Getters
    public Long getId() { return id; }

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) {
        this.caseFile = Objects.requireNonNull(caseFile, "caseFile cannot be null");
    }

    public String getCourtCaseNumber() { return courtCaseNumber; }
    public void setCourtCaseNumber(String courtCaseNumber) {
        this.courtCaseNumber = sanitize(courtCaseNumber);
    }

    public String getCourtName() { return courtName; }
    public void setCourtName(String courtName) {
        this.courtName = sanitize(courtName);
    }

    public String getJudgeName() { return judgeName; }
    public void setJudgeName(String judgeName) {
        this.judgeName = sanitize(judgeName);
    }

    public User getProsecutor() { return prosecutor; }
    public void setProsecutor(User prosecutor) {
        this.prosecutor = prosecutor;
    }

    public LocalDate getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDate filedAt) { this.filedAt = filedAt; }

    public CourtStatus getStatus() { return status; }

    // Controlled status transitions
    public void setStatus(CourtStatus status) {
        Objects.requireNonNull(status, "status cannot be null");

        // Prevent illegal transitions
        if (this.status == CourtStatus.CLOSED) {
            throw new IllegalStateException("Cannot modify a closed court case");
        }

        this.status = status;
    }

    // Domain method: close case
    public void closeCase() {
        if (this.status == CourtStatus.CLOSED) {
            throw new IllegalStateException("Court case already closed");
        }
        this.status = CourtStatus.CLOSED;
    }

    // Utility
    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // Equals & HashCode (JPA safe)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CourtCase)) return false;
        CourtCase that = (CourtCase) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debugging
    @Override
    public String toString() {
        return "CourtCase{" +
                "id=" + id +
                ", caseFileId=" + (caseFile != null ? caseFile.getId() : null) +
                ", courtCaseNumber='" + courtCaseNumber + '\'' +
                ", status=" + status +
                ", filedAt=" + filedAt +
                '}';
    }
}