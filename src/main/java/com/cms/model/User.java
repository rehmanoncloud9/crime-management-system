package com.cms.model;

import com.cms.model.enums.Role;
import com.cms.model.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "username"),
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "badge_number")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "badge_number", nullable = false, unique = true)
    private String badgeNumber;

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotBlank
    @Size(max = 75)
    @Column(name = "first_name", nullable = false, length = 75)
    private String firstName;

    @NotBlank
    @Size(max = 75)
    @Column(name = "last_name", nullable = false, length = 75)
    private String lastName;

    /**
     * ERD Specialisation 1 — Overlapping (○), Partial: PERSONS → USERS.
     * Joined-table strategy: a User IS-A Person. person_id links to the
     * base PERSONS record. UNIQUE ensures one-to-one. Nullable = partial.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", unique = true)
    private Person person;

    @Column(name = "officer_rank")
    private String officerRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "VARCHAR(30)")
    private Role role;

    @Column(name = "department")
    private String department;
    @Column(name = "precinct")
    private String precinct;

    @Email
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{7,15}$")
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    @Column(name = "date_joined")
    private LocalDate dateOfJoining;

    @Lob
    @Column(name = "profile_photo", columnDefinition = "LONGBLOB")
    private byte[] profilePhoto;

    @Column(name = "profile_photo_path")
    private String profilePhotoPath;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, columnDefinition = "VARCHAR(30)")
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "must_change_password")
    private boolean mustChangePassword = false;

    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_active")
    private LocalDateTime lastActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // ================= LIFECYCLE =================

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        validateCoreFields();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        validateCoreFields();
    }

    // ================= VALIDATION =================

    private void validateCoreFields() {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Username cannot be empty");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalStateException("Password hash cannot be empty");
        }
    }

    // ================= SECURITY DOMAIN METHODS =================

    public void recordFailedLogin(int maxAttempts) {
        this.failedLoginAttempts++;

        if (this.failedLoginAttempts >= maxAttempts) {
            lockAccount(15); // default lock duration (minutes)
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void lockAccount(int minutes) {
        this.lockedUntil = LocalDateTime.now().plusMinutes(minutes);
        this.status = UserStatus.LOCKED;
    }

    public void unlockAccount() {
        this.lockedUntil = null;
        this.status = UserStatus.ACTIVE;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public void requirePasswordChange() {
        this.mustChangePassword = true;
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash cannot be empty");
        }
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
    }

    // ================= GETTERS =================

    public Long getId() { return id; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }

    @Transient
    public String getFullName() {
        return (firstName != null ? firstName : "") + (lastName != null ? " " + lastName : "");
    }

    public Person getPerson() { return person; }

    public String getOfficerRank() { return officerRank; }

    public String getBadgeNumber() { return badgeNumber; }

    public String getPrecinct() { return precinct; }

    public Role getRole() { return role; }

    public UserStatus getStatus() { return status; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public User getCreatedBy() { return createdBy; }

    public byte[] getProfilePhoto() { return profilePhoto; }

    public String getDepartment() { return department; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public String getProfilePhotoPath() { return profilePhotoPath; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDateTime getLastActive() { return lastActive; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    // ================= SETTERS =================

    public void setId(Long id) { this.id = id; }
    public void setBadgeNumber(String badgeNumber) { this.badgeNumber = badgeNumber; }
    public void setUsername(String username) { this.username = username; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public void setLastName(String lastName) { this.lastName = lastName; }

    public void setFullName(String fullName) {
        if (fullName != null) {
            String[] parts = fullName.split(" ", 2);
            this.firstName = parts[0];
            this.lastName = parts.length > 1 ? parts[1] : "";
        }
    }
    public void setOfficerRank(String officerRank) { this.officerRank = officerRank; }
    public void setRole(Role role) { this.role = role; }
    public void setDepartment(String department) { this.department = department; }
    public void setPrecinct(String precinct) { this.precinct = precinct; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }
    public void setProfilePhoto(byte[] profilePhoto) { this.profilePhoto = profilePhoto; }
    public void setProfilePhotoPath(String profilePhotoPath) { this.profilePhotoPath = profilePhotoPath; }
    public void setStatus(UserStatus status) { this.status = status; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public void setLastActive(LocalDateTime lastActive) { this.lastActive = lastActive; }
    public void setPerson(Person person) { this.person = person; }

    @Override
    public String toString() {
        String name = getFullName();
        return !name.isBlank() ? name : (username != null ? username : "User#" + id);
    }
}