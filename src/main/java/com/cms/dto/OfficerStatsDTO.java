package com.cms.dto;

import com.cms.model.User;

/**
 * Data Transfer Object for Officer Performance reporting.
 * Improves UI binding in JavaFX (W-37).
 */
public class OfficerStatsDTO {
    private User officer;
    private long totalCases;
    private long closedCases;
    private long convictions;
    private double resolutionRate;

    public OfficerStatsDTO(User officer, long totalCases, long closedCases, long convictions) {
        this.officer = officer;
        this.totalCases = totalCases;
        this.closedCases = closedCases;
        this.convictions = convictions;
        this.resolutionRate = totalCases > 0 ? (double) closedCases / totalCases * 100 : 0;
    }

    public User getOfficer() { return officer; }
    public String getOfficerName() { return officer != null ? officer.getFullName() : "Unknown"; }
    public long getTotalCases() { return totalCases; }
    public long getClosedCases() { return closedCases; }
    public long getConvictions() { return convictions; }
    public double getResolutionRate() { return resolutionRate; }
    
    public String getSuccessMetric() {
        return String.format("%.1f%% (%d/%d)", resolutionRate, closedCases, totalCases);
    }
}
