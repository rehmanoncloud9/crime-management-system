package com.cms.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "crime_types",
        indexes = {
                @Index(name = "idx_crime_type_code", columnList = "code"),
                @Index(name = "idx_crime_type_parent", columnList = "parent_id")
        })
public class CrimeType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Self-referencing hierarchy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CrimeType parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 50)
    private String code;

    @Column(length = 255)
    private String description;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String examples;

    @Column(name = "investigation_tips", columnDefinition = "TEXT")
    private String investigationTips;

    @Column(name = "legal_reference", length = 200)
    private String legalReference;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CrimeType> subTypes = new ArrayList<>();

    // Constructors
    public CrimeType() {}

    public CrimeType(String name, String code) {
        this.name = validate(name, "name");
        this.code = sanitize(code);
    }

    // Getters
    public Integer getId() { return id; }

    public CrimeType getParent() { return parent; }

    public void setParent(CrimeType parent) {
        if (parent == this) {
            throw new IllegalArgumentException("CrimeType cannot be its own parent");
        }
        this.parent = parent;
    }

    public String getName() { return name; }
    public void setName(String name) {
        this.name = validate(name, "name");
    }

    public String getCode() { return code; }
    public void setCode(String code) {
        this.code = sanitize(code);
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = sanitize(description);
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getExamples() { return examples; }
    public void setExamples(String examples) { this.examples = examples; }

    public String getInvestigationTips() { return investigationTips; }
    public void setInvestigationTips(String investigationTips) { this.investigationTips = investigationTips; }

    public String getLegalReference() { return legalReference; }
    public void setLegalReference(String legalReference) { this.legalReference = legalReference; }

    public List<CrimeType> getSubTypes() { return subTypes; }

    // Controlled relationship management
    public void addSubType(CrimeType child) {
        Objects.requireNonNull(child, "child cannot be null");

        if (child == this) {
            throw new IllegalArgumentException("Cannot add itself as subtype");
        }

        // prevent cycles (basic check)
        if (isAncestorOf(child)) {
            throw new IllegalStateException("Cycle detected in CrimeType hierarchy");
        }

        child.setParent(this);
        this.subTypes.add(child);
    }

    public void removeSubType(CrimeType child) {
        if (child != null) {
            child.setParent(null);
            this.subTypes.remove(child);
        }
    }

    // Utility: check hierarchy cycle
    private boolean isAncestorOf(CrimeType node) {
        CrimeType current = this;
        while (current != null) {
            if (current == node) return true;
            current = current.parent;
        }
        return false;
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

    // Equals & HashCode (JPA safe)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CrimeType)) return false;
        CrimeType that = (CrimeType) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Debug
    @Override
    public String toString() {
        return "CrimeType{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", code='" + code + '\'' +
                '}';
    }
}