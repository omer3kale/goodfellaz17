package com.spotifybot.presentation.dto;

import java.util.List;

/**
 * DTO - Services Response (SMM Panel API v2).
 * 
 * Service catalog with pricing matching StreamingMafia rates.
 */
public record ServicesResponse(List<ServiceItem> services) {

    public record ServiceItem(
            int service,
            String name,
            String type,
            String category,
            double rate,
            int min,
            int max,
            String description
    ) {}

    /**
     * Default service catalog for Spotify bot provider.
     * Pricing based on StreamingMafia research ($11.90/1M premium plays).
     */
    public static ServicesResponse defaultCatalog() {
        return new ServicesResponse(List.of(
                new ServiceItem(
                        1,
                        "Spotify Plays [Worldwide]",
                        "Default",
                        "Spotify",
                        0.0089,  // $8.90 per 1000
                        100,
                        10_000_000,
                        "Worldwide plays, 2-3 day delivery"
                ),
                new ServiceItem(
                        2,
                        "Spotify Plays [USA]",
                        "Default",
                        "Spotify",
                        0.0109,  // $10.90 per 1000
                        100,
                        5_000_000,
                        "USA targeted plays, 2-3 day delivery"
                ),
                new ServiceItem(
                        3,
                        "Spotify Plays [EU]",
                        "Default",
                        "Spotify",
                        0.0109,
                        100,
                        5_000_000,
                        "EU targeted plays, 2-3 day delivery"
                ),
                new ServiceItem(
                        4,
                        "Spotify Premium Plays [USA] ⭐",
                        "Premium",
                        "Spotify",
                        0.0119,  // $11.90 per 1000 (StreamingMafia rate)
                        100,
                        1_000_000,
                        "Premium USA plays, royalty eligible, 24-48h delivery"
                ),
                new ServiceItem(
                        5,
                        "Spotify VIP Plays [USA] 👑",
                        "VIP",
                        "Spotify",
                        0.0199,  // $19.90 per 1000
                        100,
                        500_000,
                        "VIP USA plays, fastest delivery 12-24h, no drops guaranteed"
                )
        ));
    }
}
