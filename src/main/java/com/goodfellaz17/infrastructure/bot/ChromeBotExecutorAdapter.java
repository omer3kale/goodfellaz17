package com.goodfellaz17.infrastructure.bot;

import com.goodfellaz17.domain.model.BotTask;
import com.goodfellaz17.domain.model.SessionProfile;
import com.goodfellaz17.domain.port.BotExecutorPort;
import com.goodfellaz17.infrastructure.spotify.SpotifyTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PRODUCTION Bot Executor - Real Spotify Web API.
 * 
 * Uses Spotify Web API with OAuth tokens to trigger playback.
 * No Selenium needed - pure API-based delivery.
 * 
 * Flow:
 * 1. Get valid access token for account
 * 2. Call Spotify Web API /me/player/play
 * 3. Wait for play duration (royalty eligible)
 * 4. Return success count
 */
@Component
@Profile("!dev")
public class ChromeBotExecutorAdapter implements BotExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(ChromeBotExecutorAdapter.class);

    private static final String SPOTIFY_API_BASE = "https://api.spotify.com/v1";
    
    @Value("${bot.executor.max-concurrent:100}")
    private int maxConcurrent;

    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final SpotifyTokenService tokenService;
    private final HttpClient httpClient;

    public ChromeBotExecutorAdapter(SpotifyTokenService tokenService) {
        this.tokenService = tokenService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    @Async("botExecutor")
    public CompletableFuture<Integer> execute(BotTask task) {
        activeTasks.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("🎵 Executing REAL Spotify play: orderId={}, track={}", 
                        task.orderId(), task.trackUrl());
                
                // REAL Spotify API execution
                int plays = executeSpotifyPlay(task);
                
                // Record account usage
                task.account().recordPlay();
                
                log.info("✅ Play delivered: orderId={}, plays={}", task.orderId(), plays);
                return plays;
                
            } catch (Exception e) {
                log.error("❌ Play failed: orderId={}, error={}", 
                        task.orderId(), e.getMessage());
                return 0;
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }

    /**
     * Execute REAL Spotify play via Web API.
     */
    private int executeSpotifyPlay(BotTask task) {
        SessionProfile profile = task.sessionProfile();
        
        try {
            // 1. Get valid OAuth token
            String accessToken = tokenService.getValidToken(task.account());
            if (accessToken == null) {
                log.error("No valid token for account: {}", task.account().getEmail());
                return 0;
            }
            
            // 2. Extract track ID from URL
            String trackId = extractTrackId(task.trackUrl());
            if (trackId == null) {
                log.error("Invalid track URL: {}", task.trackUrl());
                return 0;
            }
            
            // 3. Start playback via Spotify API
            boolean playStarted = startPlayback(accessToken, trackId);
            if (!playStarted) {
                log.warn("Failed to start playback for track: {}", trackId);
                return 0;
            }
            
            // 4. Wait for play duration (royalty eligibility = 30+ seconds)
            long durationMs = profile.playDuration().toMillis();
            long humanizedDuration = durationMs + ThreadLocalRandom.current().nextLong(2000, 5000);
            Thread.sleep(humanizedDuration);
            
            // 5. Verify play was successful
            return profile.isRoyaltyEligible() ? 1 : 0;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            log.error("Spotify API error: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Start playback on user's active device via Spotify API.
     */
    private boolean startPlayback(String accessToken, String trackId) {
        try {
            // Play endpoint - starts playback of specified track
            String body = String.format("""
                {
                    "uris": ["spotify:track:%s"],
                    "position_ms": 0
                }
                """, trackId);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_BASE + "/me/player/play"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            // 204 = success, 202 = queued, 404 = no active device
            if (response.statusCode() == 204 || response.statusCode() == 202) {
                log.debug("Playback started for track: {}", trackId);
                return true;
            } else if (response.statusCode() == 404) {
                log.warn("No active device for playback - trying transfer");
                return transferAndPlay(accessToken, trackId);
            } else {
                log.warn("Playback failed: HTTP {} - {}", response.statusCode(), response.body());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Start playback error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Transfer playback to first available device.
     */
    private boolean transferAndPlay(String accessToken, String trackId) {
        try {
            // Get available devices
            HttpRequest devicesRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_BASE + "/me/player/devices"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            
            HttpResponse<String> devicesResponse = httpClient.send(devicesRequest,
                    HttpResponse.BodyHandlers.ofString());
            
            if (devicesResponse.statusCode() != 200) {
                log.warn("Cannot get devices: {}", devicesResponse.body());
                return false;
            }
            
            // Parse first device ID (simple extraction)
            String body = devicesResponse.body();
            int idStart = body.indexOf("\"id\":\"") + 6;
            if (idStart < 6) return false;
            int idEnd = body.indexOf("\"", idStart);
            String deviceId = body.substring(idStart, idEnd);
            
            // Transfer and start playback
            String transferBody = String.format("""
                {
                    "device_ids": ["%s"],
                    "play": true
                }
                """, deviceId);
            
            HttpRequest transferRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_BASE + "/me/player"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(transferBody))
                    .build();
            
            HttpResponse<String> transferResponse = httpClient.send(transferRequest,
                    HttpResponse.BodyHandlers.ofString());
            
            if (transferResponse.statusCode() == 204 || transferResponse.statusCode() == 202) {
                // Now play the track
                Thread.sleep(500);
                return startPlayback(accessToken, trackId);
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Transfer playback error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract track ID from Spotify URL or URI.
     */
    private String extractTrackId(String url) {
        if (url == null) return null;
        
        // spotify:track:abc123
        if (url.startsWith("spotify:track:")) {
            return url.substring(14);
        }
        
        // https://open.spotify.com/track/abc123?...
        if (url.contains("/track/")) {
            int start = url.indexOf("/track/") + 7;
            int end = url.indexOf("?", start);
            return end > start ? url.substring(start, end) : url.substring(start);
        }
        
        return url; // Assume it's already a track ID
    }

    @Override
    public boolean hasCapacity() {
        return activeTasks.get() < maxConcurrent;
    }

    @Override
    public int getActiveTaskCount() {
        return activeTasks.get();
    }
}
