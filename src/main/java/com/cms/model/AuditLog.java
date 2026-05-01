package com.cms.model;

import com.cms.model.enums.AuditAction;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_user", columnList = "user_id"),
                @Index(name = "idx_audit_action", columnList = "action"),
                @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
                @Index(name = "idx_audit_timestamp", columnList = "timestamp")
        })
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who performed action
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // Snapshot (VERY important: survives even if user is deleted/changed)
    @Column(name = "user_name", nullable = false, length = 100, updatable = false)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Column(name = "entity_type", length = 100, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Use TEXT for portability unless strictly on MySQL JSON
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address", length = 45, updatable = false) // supports IPv6
    private String ipAddress;

    @Column(name = "workstation_id", length = 100, updatable = false)
    private String workstationId;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    // Constructors
    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(User user, String userName, AuditAction action) {
        this();
        this.user = Objects.requireNonNull(user, "user cannot be null");
        this.userName = validate(userName, "userName");
        this.action = Objects.requireNonNull(action, "action cannot be null");
    }

    // Lifecycle Hook
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    // Getters (NO setters for critical immutable fields)
    public Long getId() { return id; }

    public User getUser() { return user; }

    public String getUserName() { return userName; }

    public AuditAction getAction() { return action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) {
        this.entityType = sanitize(entityType);
    }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = sanitize(description);
    }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) {
        this.oldValue = sanitize(oldValue);
    }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) {
        this.newValue = sanitize(newValue);
    }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) {
        this.ipAddress = sanitize(ipAddress);
    }

    public String getWorkstationId() { return workstationId; }
    public void setWorkstationId(String workstationId) {
        this.workstationId = sanitize(workstationId);
    }

    public LocalDateTime getTimestamp() { return timestamp; }

    // Utility
    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    private String validate(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be null or empty");
        }
        return value.trim();
    }

    // Equals & HashCode (JPA-safe)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog)) return false;
        AuditLog that = (AuditLog) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debugging
    @Override
    public String toString() {
        return "AuditLog{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", action=" + action +
                ", entityType='" + entityType + '\'' +
                ", entityId=" + entityId +
                ", timestamp=" + timestamp +
                '}';
    }
}