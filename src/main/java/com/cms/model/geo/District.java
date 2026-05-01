package com.cms.model.geo;

import jakarta.persistence.*;

@Entity
@Table(name = "districts")
public class District {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Province getProvince() { return province; }
    public void setProvince(Province province) { this.province = province; }
    
    @Override
    public String toString() { return name; }
}
