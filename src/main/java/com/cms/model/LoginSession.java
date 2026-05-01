package com.cms.model;

import com.cms.model.enums.SessionStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "login_sessions",
        indexes = {
                @Index(name = "idx_session_user", columnList = "user_id"),
                @Index(name = "idx_session_status", columnList = "session_status")
        })
public class LoginSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "login_at", nullable = false, updatable = false)
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;

    @Column(name = "workstation_id", length = 100)
    private String workstationId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false)
    private SessionStatus sessionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Constructors
    public LoginSession() {
        this.loginAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.sessionStatus = SessionStatus.ACTIVE;
    }

    public LoginSession(User user, String ipAddress, String workstationId) {
        this();
        this.user = Objects.requireNonNull(user, "user cannot be null");
        this.ipAddress = sanitize(ipAddress);
        this.workstationId = sanitize(workstationId);
    }

    // Lifecycle hooks
    @PrePersist
    protected void onCreate() {
        if (loginAt == null) {
            loginAt = LocalDateTime.now();
        }
        if (sessionStatus == null) {
            sessionStatus = SessionStatus.ACTIVE;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // Getters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public LocalDateTime getLoginAt() { return loginAt; }
    public void setLoginAt(LocalDateTime loginAt) { this.loginAt = loginAt; }

    public LocalDateTime getLogoutAt() { return logoutAt; }

    public String getWorkstationId() { return workstationId; }
    public void setWorkstationId(String workstationId) { this.workstationId = sanitize(workstationId); }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = sanitize(ipAddress); }

    public SessionStatus getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(SessionStatus sessionStatus) { this.sessionStatus = sessionStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    // Domain logic (IMPORTANT)
    public void logout() {
        if (this.sessionStatus == SessionStatus.ENDED) {
            throw new IllegalStateException("Session already ended");
        }

        this.logoutAt = LocalDateTime.now();
        this.sessionStatus = SessionStatus.ENDED;
    }

    public void expire() {
        if (this.sessionStatus == SessionStatus.ENDED) {
            return;
        }

        this.sessionStatus = SessionStatus.EXPIRED;
        this.logoutAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.sessionStatus == SessionStatus.ACTIVE;
    }

    // Helpers
    private String sanitize(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }

    // equals & hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginSession)) return false;
        LoginSession that = (LoginSession) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "LoginSession{" +
                "id=" + id +
                ", userId=" + (user != null ? user.getId() : null) +
                ", status=" + sessionStatus +
                '}';
    }
}