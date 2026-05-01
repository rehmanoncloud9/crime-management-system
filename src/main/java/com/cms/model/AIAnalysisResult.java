package com.cms.model;

import com.cms.model.enums.AnalysisType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "ai_analysis_results",
        indexes = {
                @Index(name = "idx_subject", columnList = "subject_type, subject_id"),
                @Index(name = "idx_analysis_type", columnList = "analysis_type")
        })
public class AIAnalysisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false)
    private AnalysisType analysisType;

    @Column(name = "subject_type", nullable = false, length = 50)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "computed_at", nullable = false, updatable = false)
    private LocalDateTime computedAt;

    // Constructors
    public AIAnalysisResult() {
        this.computedAt = LocalDateTime.now();
    }

    public AIAnalysisResult(AnalysisType analysisType, String subjectType, Long subjectId, String resultJson) {
        this();
        this.analysisType = analysisType;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.resultJson = resultJson;
    }

    // Lifecycle Hooks
    @PrePersist
    protected void onCreate() {
        if (computedAt == null) {
            computedAt = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() { return id; }

    public AnalysisType getAnalysisType() { return analysisType; }
    public void setAnalysisType(AnalysisType analysisType) {
        this.analysisType = Objects.requireNonNull(analysisType, "analysisType cannot be null");
    }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) {
        this.subjectType = validateString(subjectType, "subjectType");
    }

    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) {
        if (subjectId == null || subjectId <= 0) {
            throw new IllegalArgumentException("subjectId must be a positive number");
        }
        this.subjectId = subjectId;
    }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) {
        this.resultJson = validateString(resultJson, "resultJson");
    }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) {
        if (confidence != null &&
                (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        this.confidence = confidence;
    }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public LocalDateTime getComputedAt() { return computedAt; }

    // Utility
    private String validateString(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
        return value.trim();
    }

    // Equals & HashCode (important for JPA)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AIAnalysisResult)) return false;
        AIAnalysisResult that = (AIAnalysisResult) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // ToString (debugging)
    @Override
    public String toString() {
        return "AIAnalysisResult{" +
                "id=" + id +
                ", analysisType=" + analysisType +
                ", subjectType='" + subjectType + '\'' +
                ", subjectId=" + subjectId +
                ", confidence=" + confidence +
                ", modelVersion='" + modelVersion + '\'' +
                ", computedAt=" + computedAt +
                '}';
    }
}