package com.cms.model;

import com.cms.model.enums.WarrantStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "warrants",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "warrant_number")
        }
)
public class Warrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "warrant_number", nullable = false, unique = true)
    private String warrantNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private CaseFile caseFile;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suspect_id", nullable = false)
    private Person suspect;

    @NotBlank
    @Column(name = "issued_by", nullable = false)
    private String issuedBy;

    @NotNull
    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Future
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @NotBlank
    @Column(nullable = false, columnDefinition = "TEXT")
    private String charges;

    @Enumerated(EnumType.STRING)
    private WarrantStatus status = WarrantStatus.ISSUED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ================= LIFECYCLE =================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        validate();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        validate();
    }

    // ================= VALIDATION =================

    private void validate() {
        if (issuedAt != null && expiresAt != null) {
            if (expiresAt.isBefore(issuedAt)) {
                throw new IllegalStateException("Expiry date cannot be before issue date");
            }
        }
    }

    // ================= DOMAIN METHODS =================

    public boolean isExpired() {
        return expiresAt != null && LocalDate.now().isAfter(expiresAt);
    }

    public void revoke() {
        if (this.status == WarrantStatus.EXECUTED) {
            throw new IllegalStateException("Executed warrant cannot be revoked");
        }
        this.status = WarrantStatus.REVOKED;
    }

    public void markExecuted() {
        if (this.status != WarrantStatus.ISSUED) {
            throw new IllegalStateException("Only issued warrants can be executed");
        }
        this.status = WarrantStatus.EXECUTED;
    }

    public void validateIssuance() {
        if (warrantNumber == null || warrantNumber.isBlank()) {
            throw new IllegalStateException("Warrant number required");
        }
        if (suspect == null) {
            throw new IllegalStateException("Suspect is required");
        }
        if (charges == null || charges.isBlank()) {
            throw new IllegalStateException("Charges must be specified");
        }
    }

    // ================= GETTERS =================

    public Long getId() { return id; }

    public String getWarrantNumber() { return warrantNumber; }

    public Person getSuspect() { return suspect; }

    public WarrantStatus getStatus() { return status; }

    public LocalDate getIssuedAt() { return issuedAt; }

    public LocalDate getExpiresAt() { return expiresAt; }

    public String getCharges() { return charges; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}