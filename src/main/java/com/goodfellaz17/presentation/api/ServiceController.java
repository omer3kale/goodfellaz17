package com.goodfellaz17.presentation.api;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service Catalog Controller - PWA dropdown data.
 * 
 * Provides all available Spotify services with pricing.
 * Frontend uses this for order form population.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ServiceController {

    /**
     * Get all available services for PWA dropdown.
     */
    @GetMapping("/services")
    public List<ServiceResponse> getServices() {
        return List.of(
            // === PLAYS ===
            new ServiceResponse("spotify_plays_ww", "Spotify Plays Worldwide", "plays", 
                new BigDecimal("2.50"), 100, 1000000, "Standard Route"),
            new ServiceResponse("spotify_plays_usa", "Spotify Plays USA", "plays", 
                new BigDecimal("4.00"), 100, 500000, "Premium Route"),
            new ServiceResponse("spotify_plays_uk", "Spotify Plays UK", "plays", 
                new BigDecimal("4.00"), 100, 500000, "Premium Route"),
            new ServiceResponse("spotify_plays_de", "Spotify Plays Germany", "plays", 
                new BigDecimal("4.00"), 100, 500000, "Premium Route"),
            new ServiceResponse("spotify_plays_premium", "Spotify Plays Premium", "plays", 
                new BigDecimal("6.00"), 100, 250000, "Elite Route"),
            
            // === LISTENERS ===
            new ServiceResponse("spotify_listeners_ww", "Spotify Monthly Listeners WW", "listeners", 
                new BigDecimal("3.00"), 100, 500000, "Standard Route"),
            new ServiceResponse("spotify_listeners_usa", "Spotify Monthly Listeners USA", "listeners", 
                new BigDecimal("5.00"), 100, 250000, "Premium Route"),
            
            // === FOLLOWERS ===
            new ServiceResponse("spotify_followers", "Spotify Followers", "followers", 
                new BigDecimal("3.50"), 50, 100000, "Standard Route"),
            new ServiceResponse("spotify_followers_premium", "Spotify Followers Premium", "followers", 
                new BigDecimal("5.50"), 50, 50000, "Premium Route"),
            
            // === SAVES ===
            new ServiceResponse("spotify_saves", "Spotify Track Saves", "saves", 
                new BigDecimal("4.00"), 50, 100000, "Standard Route"),
            
            // === PLAYLIST ===
            new ServiceResponse("spotify_playlist_plays", "Spotify Playlist Plays", "playlist", 
                new BigDecimal("3.00"), 100, 500000, "Standard Route"),
            new ServiceResponse("spotify_playlist_followers", "Spotify Playlist Followers", "playlist", 
                new BigDecimal("4.50"), 50, 50000, "Standard Route")
        );
    }

    /**
     * Get single service details.
     */
    @GetMapping("/services/{serviceId}")
    public ServiceResponse getService(@PathVariable String serviceId) {
        return getServices().stream()
            .filter(s -> s.serviceId().equals(serviceId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));
    }

    /**
     * Service response DTO.
     */
    public record ServiceResponse(
        String serviceId,
        String serviceName,
        String category,
        BigDecimal rate,       // $ per 1000
        int minOrder,
        int maxOrder,
        String routeHint       // "Premium Route", "Elite Route", etc.
    ) {}
}
