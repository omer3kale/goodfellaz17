package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.persistence.entity.ApiKeyEntity;
import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * API Key Controller - Self-service key creation.
 * 
 * POST /api/key/create?email=user@example.com → botzzz_abcXYZ
 */
@RestController
@RequestMapping("/api/key")
@CrossOrigin(origins = "*")
@Tag(name = "API Keys", description = "Self-service API key management")
public class KeyController {

    private static final Logger log = LoggerFactory.getLogger(KeyController.class);

    private final ApiKeyRepository apiKeyRepository;

    public KeyController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * POST /api/key/create
     * Create a new API key for a user.
     * 
     * @param email User's email (used as username)
     * @return New API key
     */
    @PostMapping("/create")
    @Operation(
        summary = "Create a new API key",
        description = "Generates a unique API key (botzzz_xxx) for the given email. Free to create."
    )
    public Mono<ResponseEntity<Map<String, Object>>> createKey(
            @Parameter(description = "User email address", required = true, example = "user@example.com")
            @RequestParam String email) {
        
        log.info("Creating API key for email: {}", email);

        // Validate email
        if (email == null || email.isBlank() || !email.contains("@")) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Valid email is required"
            )));
        }

        // Generate unique key: botzzz_<20 chars>
        String apiKey = "botzzz_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        // Create entity with 0 balance
        ApiKeyEntity entity = new ApiKeyEntity(apiKey, email, BigDecimal.ZERO);

        return apiKeyRepository.save(entity)
            .map(saved -> {
                log.info("Created API key {} for {}", apiKey, email);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "success", true,
                    "apiKey", saved.getApiKey(),
                    "email", email,
                    "balance", 0.0,
                    "message", "API key created successfully"
                ));
            })
            .onErrorResume(e -> {
                log.error("Failed to create API key for {}: {}", email, e.getMessage());
                return Mono.just(ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "error", "Failed to create API key: " + e.getMessage()
                )));
            });
    }

    /**
     * GET /api/key/validate
     * Validate an API key exists and is active.
     */
    @GetMapping("/validate")
    public Mono<ResponseEntity<Map<String, Object>>> validateKey(
            @RequestParam String key) {
        
        return apiKeyRepository.findByApiKey(key)
            .map(entity -> ResponseEntity.ok(Map.<String, Object>of(
                "valid", true,
                "apiKey", entity.getApiKey(),
                "balance", entity.getBalance(),
                "active", entity.getIsActive() != null && entity.getIsActive()
            )))
            .defaultIfEmpty(ResponseEntity.ok(Map.of(
                "valid", false,
                "error", "API key not found"
            )));
    }

    /**
     * GET /api/key/balance
     * Get balance for an API key.
     */
    @GetMapping("/balance")
    public Mono<ResponseEntity<Map<String, Object>>> getBalance(
            @RequestParam String key) {
        
        return apiKeyRepository.findBalanceByApiKey(key)
            .map(balance -> ResponseEntity.ok(Map.<String, Object>of(
                "success", true,
                "apiKey", key,
                "balance", balance
            )))
            .defaultIfEmpty(ResponseEntity.ok(Map.of(
                "success", false,
                "error", "API key not found"
            )));
    }
}
