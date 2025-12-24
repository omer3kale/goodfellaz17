package com.spotifybot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spotify Bot Provider - Spring Boot 3.5 Application.
 * 
 * SMM Panel API v2 implementation for Spotify stream automation.
 * 
 * Architecture: Hexagonal / Ports & Adapters
 * - Presentation → REST API (Perfect Panel v2 spec)
 * - Application → Use Cases + Services
 * - Domain → Entities + Business Rules
 * - Infrastructure → Chrome Bots + Proxies + Persistence
 * 
 * CyyBot 3.0 Features:
 * - Stealth Chrome automation
 * - Residential proxy rotation
 * - Premium account farm management
 * - Spotify compliance (35s royalty, 5% spike limit)
 * 
 * @author RWTH MATSE Research Project
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SpotifyBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpotifyBotApplication.class, args);
    }
}
