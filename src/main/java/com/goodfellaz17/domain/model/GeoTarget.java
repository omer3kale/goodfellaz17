package com.goodfellaz17.domain.model;

/**
 * Domain Value Object - Geographic targeting for streams.
 * 
 * Determines proxy pool and account selection for Spotify compliance.
 */
public enum GeoTarget {
    USA("United States", "US"),
    EU("European Union", "EU"),
    WORLDWIDE("Worldwide", "WW");

    private final String displayName;
    private final String code;

    GeoTarget(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }
}
