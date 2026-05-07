package com.cms.model;

import com.cms.model.enums.WarrantStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "warrants",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "warrant_number")
        }
)
public class Warrant {

    @Embeddable
    public static class Charge {
        @Column(name = "description", nullable = false, length = 500)
        private String description;
        @Column(name = "legal_section", length = 100)
        private String legalSection;

        public Charge() {}
        public Charge(String description, String legalSection) {
            this.description = description;
            this.legalSection = legalSection;
        }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLegalSection() { return legalSection; }
        public void setLegalSection(String legalSection) { this.legalSection = legalSection; }
    }

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
    private String issuedBy; // Name of the Judge/Official

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issuing_court_id")
    private Court issuingCourt;

    @NotNull
    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    @Future
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "warrant_charges", joinColumns = @JoinColumn(name = "warrant_id"))
    private Set<Charge> charges = new HashSet<>();

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
        if (charges == null || charges.isEmpty()) {
            throw new IllegalStateException("Charges must be specified");
        }
    }

    // ================= GETTERS =================

    public Long getId() { return id; }

    public String getWarrantNumber() { return warrantNumber; }

    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }

    public Court getIssuingCourt() { return issuingCourt; }
    public void setIssuingCourt(Court issuingCourt) { this.issuingCourt = issuingCourt; }

    public Person getSuspect() { return suspect; }

    public WarrantStatus getStatus() { return status; }

    public LocalDate getIssuedAt() { return issuedAt; }

    public LocalDate getExpiresAt() { return expiresAt; }

    public Set<Charge> getCharges() { return charges; }
    public void setCharges(Set<Charge> charges) { this.charges = charges; }

    public void addCharge(String desc, String section) {
        this.charges.add(new Charge(desc, section));
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
