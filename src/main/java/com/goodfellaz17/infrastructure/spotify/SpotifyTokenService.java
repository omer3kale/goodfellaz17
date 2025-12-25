package com.goodfellaz17.infrastructure.spotify;

import com.goodfellaz17.domain.model.PremiumAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spotify Token Service - Manages OAuth tokens for farm accounts.
 * 
 * Features:
 * - Auto-refresh expired access tokens (1h expiry)
 * - Token caching to minimize API calls
 * - Thread-safe for concurrent bot execution
 */
@Service
public class SpotifyTokenService {

    private static final Logger log = LoggerFactory.getLogger(SpotifyTokenService.class);

    private static final String SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final int TOKEN_BUFFER_SECONDS = 300; // Refresh 5 min before expiry

    @Value("${spotify.client-id:}")
    private String clientId;

    @Value("${spotify.client-secret:}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    // Cache: accountId → (accessToken, expiresAt)
    private final Map<String, TokenCache> tokenCache = new ConcurrentHashMap<>();

    /**
     * Get a valid access token for an account.
     * Auto-refreshes if expired or about to expire.
     */
    public String getValidToken(PremiumAccount account) {
        String accountId = account.getId().toString();

        // Check cache first
        TokenCache cached = tokenCache.get(accountId);
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken;
        }

        // Need to refresh
        String refreshToken = account.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            log.warn("No refresh token for account {}", account.getEmail());
            return null;
        }

        try {
            Map<String, Object> tokens = refreshAccessToken(refreshToken);
            String accessToken = (String) tokens.get("access_token");
            Integer expiresIn = (Integer) tokens.get("expires_in");

            // Cache the new token
            Instant expiresAt = Instant.now().plusSeconds(expiresIn - TOKEN_BUFFER_SECONDS);
            tokenCache.put(accountId, new TokenCache(accessToken, expiresAt));

            log.info("Token refreshed for {} (expires in {}s)", account.getEmail(), expiresIn);
            return accessToken;

        } catch (Exception e) {
            log.error("Failed to refresh token for {}: {}", account.getEmail(), e.getMessage());
            return null;
        }
    }

    /**
     * Pre-warm token cache for all accounts.
     * Call at startup to avoid delays on first request.
     */
    public void warmCache(Iterable<PremiumAccount> accounts) {
        int refreshed = 0;
        for (PremiumAccount account : accounts) {
            if (account.getRefreshToken() != null) {
                String token = getValidToken(account);
                if (token != null) refreshed++;
            }
        }
        log.info("Token cache warmed: {}/{} accounts", refreshed, tokenCache.size());
    }

    /**
     * Invalidate cached token for an account.
     */
    public void invalidate(String accountId) {
        tokenCache.remove(accountId);
    }

    /**
     * Invalidate all cached tokens.
     */
    public void invalidateAll() {
        tokenCache.clear();
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getStats() {
        long validCount = tokenCache.values().stream()
                .filter(t -> !t.isExpired())
                .count();

        return Map.of(
                "total_cached", tokenCache.size(),
                "valid_tokens", validCount,
                "expired_tokens", tokenCache.size() - validCount
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> refreshAccessToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encodeCredentials());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                SPOTIFY_TOKEN_URL, request, Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Token refresh failed: " + response.getStatusCode());
        }

        return response.getBody();
    }

    private String encodeCredentials() {
        String credentials = clientId + ":" + clientSecret;
        return Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Token cache entry.
     */
    private record TokenCache(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
