package com.goodfellaz17.presentation.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Root callback handler for OAuth redirects.
 * 
 * Spotify sends callbacks to /callback, this redirects
 * to the actual handler at /api/auth/spotify/callback
 */
@RestController
public class OAuthCallbackController {

    /**
     * Handle OAuth callback at root /callback path.
     * Forwards all query params to the API handler.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> handleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "state", required = false) String state) {

        // Build redirect URL with all query params
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath("/api/auth/spotify/callback");

        if (code != null) builder.queryParam("code", code);
        if (error != null) builder.queryParam("error", error);
        if (state != null) builder.queryParam("state", state);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, builder.toUriString())
                .build();
    }
}
