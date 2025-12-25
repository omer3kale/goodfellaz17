package com.goodfellaz17.infrastructure.spotify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Spotify OAuth2 Controller for Farm Account Authentication.
 * 
 * Flow:
 * 1. /api/auth/spotify/login → Redirects to Spotify OAuth
 * 2. User logs in with farm account
 * 3. Spotify redirects to /callback with code
 * 4. Exchange code for access_token + refresh_token
 * 5. Save refresh_token to PremiumAccount for auto-refresh
 */
@RestController
@RequestMapping("/api/auth/spotify")
public class SpotifyAuthController {

    private static final Logger log = LoggerFactory.getLogger(SpotifyAuthController.class);

    private static final String SPOTIFY_AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";
    
    // Scopes needed for playback control
    private static final String SCOPES = String.join(" ",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing",
            "streaming",
            "user-library-read",
            "user-library-modify",
            "playlist-read-private",
            "playlist-modify-public",
            "playlist-modify-private"
    );

    @Value("${spotify.client-id:}")
    private String clientId;

    @Value("${spotify.client-secret:}")
    private String clientSecret;

    @Value("${spotify.redirect-uri:https://goodfellaz17.onrender.com/callback}")
    private String redirectUri;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Initiate Spotify OAuth login flow.
     * Redirects user to Spotify authorization page.
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        if (clientId == null || clientId.isEmpty()) {
            log.error("SPOTIFY_CLIENT_ID not configured");
            return ResponseEntity.status(500).build();
        }

        String authUrl = String.format(
                "%s?client_id=%s&response_type=code&redirect_uri=%s&scope=%s&show_dialog=true",
                SPOTIFY_AUTH_URL,
                clientId,
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                URLEncoder.encode(SCOPES, StandardCharsets.UTF_8)
        );

        log.info("Redirecting to Spotify OAuth: {}", authUrl);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authUrl)
                .build();
    }

    /**
     * Handle OAuth callback from Spotify.
     * Exchanges authorization code for access + refresh tokens.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state) {

        if (error != null) {
            log.error("Spotify OAuth error: {}", error);
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "error", error,
                    "message", "Spotify authorization failed"
            ));
        }

        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "error", "missing_code",
                    "message", "No authorization code received"
            ));
        }

        try {
            // Exchange code for tokens
            Map<String, Object> tokens = exchangeCodeForTokens(code);

            String accessToken = (String) tokens.get("access_token");
            String refreshToken = (String) tokens.get("refresh_token");
            Integer expiresIn = (Integer) tokens.get("expires_in");

            log.info("Spotify tokens obtained: expires_in={}s, refresh_token_length={}", 
                    expiresIn, refreshToken != null ? refreshToken.length() : 0);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "access_token", accessToken.substring(0, Math.min(20, accessToken.length())) + "...",
                    "refresh_token", refreshToken,
                    "expires_in", expiresIn,
                    "next_step", "Save refresh_token to premium_accounts table",
                    "sql", String.format(
                            "UPDATE premium_accounts SET spotify_refresh_token = '%s' WHERE id = 'your-account-id';",
                            refreshToken
                    )
            ));

        } catch (Exception e) {
            log.error("Failed to exchange Spotify code: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "error", e.getMessage(),
                    "message", "Failed to exchange authorization code"
            ));
        }
    }

    /**
     * Refresh an existing access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refresh_token");

        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "refresh_token required"
            ));
        }

        try {
            Map<String, Object> tokens = refreshAccessToken(refreshToken);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Exchange authorization code for tokens.
     */
    private Map<String, Object> exchangeCodeForTokens(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encodeCredentials());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                SPOTIFY_TOKEN_URL, request, (Class<Map<String, Object>>)(Class<?>)Map.class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Token exchange failed: " + response.getStatusCode());
        }

        return response.getBody();
    }

    /**
     * Refresh access token using refresh token.
     */
    public Map<String, Object> refreshAccessToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", "Basic " + encodeCredentials());

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                SPOTIFY_TOKEN_URL, request, (Class<Map<String, Object>>)(Class<?>)Map.class
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
}
