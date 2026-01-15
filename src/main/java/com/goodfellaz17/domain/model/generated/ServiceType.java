package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Spotify service types offered.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum ServiceType {
    
    /** Track plays (streams) */
    PLAYS("plays"),
    
    /** Monthly listeners (unique per 28 days) */
    MONTHLY_LISTENERS("monthly_listeners"),
    
    /** Track saves to library */
    SAVES("saves"),
    
    /** Artist/profile follows */
    FOLLOWS("follows"),
    
    /** Playlist followers */
    PLAYLIST_FOLLOWERS("playlist_followers"),
    
    /** Playlist plays (streams on playlist tracks) */
    PLAYLIST_PLAYS("playlist_plays");
    
    private final String value;
    
    ServiceType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static ServiceType fromValue(String value) {
        for (ServiceType type : values()) {
            if (type.value.equals(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ServiceType: " + value);
    }
}
