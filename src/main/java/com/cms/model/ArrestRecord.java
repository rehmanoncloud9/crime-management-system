package com.cms.model;

import jakarta.persistence.*;
import com.cms.model.enums.ArrestStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "arrest_records",
        indexes = {
                @Index(name = "idx_arrest_case", columnList = "case_id"),
                @Index(name = "idx_arrest_suspect", columnList = "suspect_id"),
                @Index(name = "idx_arrest_officer", columnList = "arresting_officer_id")
        })
public class ArrestRecord {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private CaseFile caseFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warrant_id")
    private Warrant warrant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suspect_id", nullable = false)
    private Person suspect;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "arresting_officer_id", nullable = false)
    private User arrestingOfficer;

    @Column(name = "arrested_at", nullable = false, updatable = false)
    private LocalDateTime arrestedAt;

    @Column(name = "arrest_location", length = 255)
    private String arrestLocation;

    @Column(name = "custody_location", length = 255)
    private String custodyLocation;

    @Column(name = "bail_amount", precision = 12, scale = 2)
    private java.math.BigDecimal bailAmount;

    @Column(name = "bail_status", length = 20)
    private String bailStatus = "NOT_APPLICABLE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "booking_reference", length = 100, unique = true)
    private String bookingReference;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "arrest_charges", joinColumns = @JoinColumn(name = "arrest_record_id"))
    private Set<Charge> charges = new HashSet<>();

    /** Returns charges as a comma-separated string for TableView binding */
    public String getChargesDisplay() {
        if (charges == null || charges.isEmpty()) return "None";
        return charges.stream()
                .map(c -> c.getDescription() + (c.getLegalSection() != null ? " [" + c.getLegalSection() + "]" : ""))
                .reduce((a, b) -> a + "; " + b).orElse("None");
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "arrest_status", nullable = false, length = 20)
    private ArrestStatus arrestStatus;

    // Constructors
    public ArrestRecord() {
        this.arrestedAt = LocalDateTime.now();
        this.arrestStatus = ArrestStatus.INITIATED;
    }

    public ArrestRecord(Person suspect, User arrestingOfficer) {
        this();
        this.suspect = Objects.requireNonNull(suspect, "suspect cannot be null");
        this.arrestingOfficer = Objects.requireNonNull(arrestingOfficer, "arrestingOfficer cannot be null");
    }

    // Lifecycle Hook
    @PrePersist
    protected void onCreate() {
        if (arrestedAt == null) {
            arrestedAt = LocalDateTime.now();
        }
        if (arrestStatus == null) {
            arrestStatus = ArrestStatus.INITIATED;
        }
    }

    // Getters (no setter for id & arrestedAt for safety)
    public Long getId() { return id; }

    public CaseFile getCaseFile() { return caseFile; }
    public void setCaseFile(CaseFile caseFile) { this.caseFile = caseFile; }

    public Warrant getWarrant() { return warrant; }
    public void setWarrant(Warrant warrant) { this.warrant = warrant; }

    public Person getSuspect() { return suspect; }
    public void setSuspect(Person suspect) {
        this.suspect = Objects.requireNonNull(suspect, "suspect cannot be null");
    }

    public User getArrestingOfficer() { return arrestingOfficer; }
    public void setArrestingOfficer(User arrestingOfficer) {
        this.arrestingOfficer = Objects.requireNonNull(arrestingOfficer, "arrestingOfficer cannot be null");
    }

    public LocalDateTime getArrestedAt() { return arrestedAt; }

    public String getArrestLocation() { return arrestLocation; }
    public void setArrestLocation(String arrestLocation) {
        this.arrestLocation = sanitize(arrestLocation);
    }

    public String getCustodyLocation() { return custodyLocation; }
    public void setCustodyLocation(String custodyLocation) { this.custodyLocation = custodyLocation; }

    public java.math.BigDecimal getBailAmount() { return bailAmount; }
    public void setBailAmount(java.math.BigDecimal bailAmount) { this.bailAmount = bailAmount; }

    public String getBailStatus() { return bailStatus; }
    public void setBailStatus(String bailStatus) { this.bailStatus = bailStatus; }

    public User getCreatedBy() { return createdBy; }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) {
        this.bookingReference = sanitize(bookingReference);
    }

    public Set<Charge> getCharges() { return charges; }
    public void setCharges(Set<Charge> charges) { this.charges = charges; }

    public void addCharge(String desc) {
        this.addCharge(desc, "N/A");
    }

    public void addCharge(String desc, String section) {
        this.charges.add(new Charge(desc, section));
    }

    public ArrestStatus getArrestStatus() { return arrestStatus; }
    public void setArrestStatus(ArrestStatus arrestStatus) {
        this.arrestStatus = Objects.requireNonNull(arrestStatus, "arrestStatus cannot be null");
    }

    // Utility
    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // Equals & HashCode (JPA safe)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArrestRecord)) return false;
        ArrestRecord that = (ArrestRecord) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debugging
    @Override
    public String toString() {
        return "ArrestRecord{" +
                "id=" + id +
                ", suspect=" + (suspect != null ? suspect.getId() : null) +
                ", officer=" + (arrestingOfficer != null ? arrestingOfficer.getId() : null) +
                ", arrestedAt=" + arrestedAt +
                ", bookingReference='" + bookingReference + '\'' +
                '}';
    }
}