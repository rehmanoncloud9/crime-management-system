package com.cms.model;

import com.cms.model.enums.IncidentStatus;
import com.cms.model.geo.*;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "crime_incidents",
        indexes = {
                @Index(name = "idx_incident_number", columnList = "incident_number"),
                @Index(name = "idx_incident_status", columnList = "status"),
                @Index(name = "idx_incident_occurred", columnList = "occurred_at")
        })
public class CrimeIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_number", unique = true, nullable = false, length = 50, updatable = false)
    private String incidentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "crime_type_id", nullable = false)
    private CrimeType crimeType;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private LocalDateTime reportedAt;

    @Column(name = "location_address", length = 255)
    private String locationAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private Area area;

    @Column(length = 100)
    private String precinct;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_officer_id")
    private User reportingOfficer;

    @Column(name = "reporter_name", length = 200)
    private String reporterName;

    @Column(name = "reporter_contact", length = 100)
    private String reporterContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_person_id")
    private Person reporterPerson;

    @Column(name = "severity_level", length = 30)
    private String severityLevel = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IncidentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public CrimeIncident() {
        this.createdAt = LocalDateTime.now();
        this.reportedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = IncidentStatus.REPORTED;
    }

    public CrimeIncident(String incidentNumber, CrimeType crimeType,
                         String title, String description,
                         LocalDateTime occurredAt, User reportingOfficer) {
        this();
        this.incidentNumber = validate(incidentNumber, "incidentNumber");
        this.crimeType = Objects.requireNonNull(crimeType, "crimeType cannot be null");
        this.title = validate(title, "title");
        this.description = validate(description, "description");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        this.reportingOfficer = Objects.requireNonNull(reportingOfficer, "reportingOfficer cannot be null");

        validateTimeline();
    }

    // Lifecycle
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (reportedAt == null) reportedAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = IncidentStatus.REPORTED;

        validateTimeline();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters
    public Long getId() { return id; }

    public String getIncidentNumber() { return incidentNumber; }
    public void setIncidentNumber(String incidentNumber) {
        if (this.incidentNumber != null) {
            throw new IllegalStateException("incidentNumber cannot be changed once set");
        }
        this.incidentNumber = validate(incidentNumber, "incidentNumber");
    }

    public CrimeType getCrimeType() { return crimeType; }
    public void setCrimeType(CrimeType crimeType) {
        this.crimeType = Objects.requireNonNull(crimeType, "crimeType cannot be null");
    }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        this.title = validate(title, "title");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = validate(description, "description");
    }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt cannot be null");
        validateTimeline();
    }

    public LocalDateTime getReportedAt() { return reportedAt; }

    public String getLocationAddress() { return locationAddress; }
    public void setLocationAddress(String locationAddress) {
        this.locationAddress = sanitize(locationAddress);
    }

    public District getDistrict() { return district; }
    public void setDistrict(District district) {
        this.district = district;
    }

    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }

    public Area getArea() { return area; }
    public void setArea(Area area) { this.area = area; }

    public String getPrecinct() { return precinct; }
    public void setPrecinct(String precinct) {
        this.precinct = sanitize(precinct);
    }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) {
        if (latitude != null && (latitude.doubleValue() < -90 || latitude.doubleValue() > 90)) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) {
        if (longitude != null && (longitude.doubleValue() < -180 || longitude.doubleValue() > 180)) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        this.longitude = longitude;
    }

    public User getReportingOfficer() { return reportingOfficer; }
    public void setReportingOfficer(User reportingOfficer) {
        this.reportingOfficer = reportingOfficer;
    }

    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = sanitize(reporterName); }

    public String getReporterContact() { return reporterContact; }
    public void setReporterContact(String reporterContact) { this.reporterContact = reporterContact; }

    public Person getReporterPerson() { return reporterPerson; }
    public void setReporterPerson(Person reporterPerson) { this.reporterPerson = reporterPerson; }

    public String getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(String severityLevel) { this.severityLevel = severityLevel; }

    public IncidentStatus getStatus() { return status; }

    // Controlled state transitions
    public void setStatus(IncidentStatus status) {
        Objects.requireNonNull(status, "status cannot be null");

        if (this.status == IncidentStatus.CLOSED && status != IncidentStatus.CLOSED) {
            throw new IllegalStateException("Cannot reopen a closed incident");
        }

        this.status = status;
    }

    public void closeIncident() {
        if (this.status == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Incident already closed");
        }
        this.status = IncidentStatus.CLOSED;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // Utility
    private void validateTimeline() {
        if (occurredAt != null && reportedAt != null && occurredAt.isAfter(reportedAt)) {
            throw new IllegalStateException("occurredAt cannot be after reportedAt");
        }
    }

    private String validate(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be null or empty");
        }
        return value.trim();
    }

    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // Equals & HashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrimeIncident)) return false;
        CrimeIncident that = (CrimeIncident) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debug
    @Override
    public String toString() {
        return "CrimeIncident{" +
                "id=" + id +
                ", incidentNumber='" + incidentNumber + '\'' +
                ", status=" + status +
                ", occurredAt=" + occurredAt +
                '}';
    }
}
