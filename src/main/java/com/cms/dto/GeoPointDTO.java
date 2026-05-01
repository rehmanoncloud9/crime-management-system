package com.cms.dto;

/**
 * Data Transfer Object for geospatial crime heatmaps (S-10).
 */
public class GeoPointDTO {
    private double latitude;
    private double longitude;
    private long intensity; // Number of crimes at this location
    private String areaName;

    public GeoPointDTO(double latitude, double longitude, long intensity, String areaName) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.intensity = intensity;
        this.areaName = areaName;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getIntensity() { return intensity; }
    public String getAreaName() { return areaName; }
}
