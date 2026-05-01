package com.cms.model;

import com.cms.model.enums.BloodGroup;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_records")
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 20)
    private BloodGroup bloodGroup;

    @Column(name = "dna_profile", length = 255)
    private String dnaProfile;

    @Column(name = "fingerprint_id", length = 100)
    private String fingerprintId;

    @Column(name = "known_diseases", columnDefinition = "TEXT")
    private String knownDiseases;

    @Column(name = "injuries", columnDefinition = "TEXT")
    private String injuries;

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }

    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }

    public String getDnaProfile() { return dnaProfile; }
    public void setDnaProfile(String dnaProfile) { this.dnaProfile = dnaProfile; }

    public String getKnownDiseases() { return knownDiseases; }
    public void setKnownDiseases(String knownDiseases) { this.knownDiseases = knownDiseases; }

    public String getFingerprintId() { return fingerprintId; }
    public void setFingerprintId(String fingerprintId) { this.fingerprintId = fingerprintId; }

    public String getInjuries() { return injuries; }
    public void setInjuries(String injuries) { this.injuries = injuries; }

    public String getMedicalNotes() { return medicalNotes; }
    public void setMedicalNotes(String medicalNotes) { this.medicalNotes = medicalNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
