package com.cms.model;

import jakarta.persistence.*;
import com.cms.model.enums.ArrestStatus;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "arrest_records",
        indexes = {
                @Index(name = "idx_arrest_case", columnList = "case_id"),
                @Index(name = "idx_arrest_suspect", columnList = "suspect_id"),
                @Index(name = "idx_arrest_officer", columnList = "arresting_officer_id")
        })
public class ArrestRecord {

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

    @Column(name = "booking_reference", length = 100, unique = true)
    private String bookingReference;

    @Column(columnDefinition = "TEXT")
    private String charges;

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
    public void setCustodyLocation(String custodyLocation) {
        this.custodyLocation = sanitize(custodyLocation);
    }

    public String getBookingReference() { return bookingReference; }
    public void setBookingReference(String bookingReference) {
        this.bookingReference = sanitize(bookingReference);
    }

    public String getCharges() { return charges; }
    public void setCharges(String charges) {
        this.charges = sanitize(charges);
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