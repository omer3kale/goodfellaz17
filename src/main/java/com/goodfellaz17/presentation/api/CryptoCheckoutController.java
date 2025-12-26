package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Crypto Checkout Controller - Coinbase Commerce payments.
 * 
 * POST /api/crypto/create-charge → Coinbase checkout URL
 * POST /api/crypto/webhook → Coinbase webhook (balance credit)
 * 
 * Supports: BTC, ETH, USDC, USDT, SOL, DOGE, SHIB, LTC + 50 more
 */
@RestController
@RequestMapping("/api/crypto")
@CrossOrigin(origins = "*")
public class CryptoCheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CryptoCheckoutController.class);

    private final ApiKeyRepository apiKeyRepository;
    private final WebClient coinbaseClient;

    @Value("${coinbase.api-key:}")
    private String coinbaseApiKey;

    @Value("${coinbase.webhook-secret:}")
    private String coinbaseWebhookSecret;

    @Value("${app.base-url:https://goodfellaz17.onrender.com}")
    private String baseUrl;

    public CryptoCheckoutController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.coinbaseClient = WebClient.builder()
            .baseUrl("https://api.commerce.coinbase.com")
            .build();
    }

    @PostConstruct
    public void init() {
        log.info("Coinbase Commerce initialized with key: {}...", 
            coinbaseApiKey.length() > 10 ? coinbaseApiKey.substring(0, 10) : "NOT_SET");
    }

    /**
     * POST /api/crypto/create-charge
     * Create a Coinbase Commerce charge for crypto payment.
     * 
     * @param apiKey Customer's API key
     * @param amount Amount in USD to add to balance
     * @return Coinbase hosted checkout URL
     */
    @PostMapping("/create-charge")
    public Mono<ResponseEntity<Map<String, Object>>> createCharge(
            @RequestParam String apiKey,
            @RequestParam BigDecimal amount) {
        
        log.info("Creating crypto charge for {} amount ${}", apiKey, amount);

        // Validate
        if (amount.compareTo(BigDecimal.valueOf(5)) < 0) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Minimum deposit is $5.00"
            )));
        }

        if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Maximum deposit is $10,000.00"
            )));
        }

        return apiKeyRepository.existsByApiKey(apiKey)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "success", false,
                        "error", "Invalid API key"
                    )));
                }

                // Create Coinbase Commerce charge
                String chargeId = UUID.randomUUID().toString();
                
                Map<String, Object> chargeRequest = Map.of(
                    "name", "GOODFELLAZ17 Balance Top-Up",
                    "description", "Add $" + amount + " to your account balance",
                    "pricing_type", "fixed_price",
                    "local_price", Map.of(
                        "amount", amount.toString(),
                        "currency", "USD"
                    ),
                    "metadata", Map.of(
                        "api_key", apiKey,
                        "amount", amount.toString(),
                        "charge_id", chargeId
                    ),
                    "redirect_url", baseUrl + "/customer?key=" + apiKey + "&crypto=success",
                    "cancel_url", baseUrl + "/checkout?key=" + apiKey + "&crypto=cancelled"
                );

                return coinbaseClient.post()
                    .uri("/charges")
                    .header("X-CC-Api-Key", coinbaseApiKey)
                    .header("X-CC-Version", "2018-03-22")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(chargeRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .map(response -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> data = (Map<String, Object>) response.get("data");
                        String hostedUrl = (String) data.get("hosted_url");
                        String code = (String) data.get("code");

                        log.info("Created Coinbase charge {} for {} amount ${}", 
                            code, apiKey, amount);

                        return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "chargeCode", code,
                            "url", hostedUrl,
                            "method", "crypto"
                        ));
                    })
                    .onErrorResume(e -> {
                        log.error("Coinbase error for {}: {}", apiKey, e.getMessage());
                        return Mono.just(ResponseEntity.internalServerError().body(Map.<String, Object>of(
                            "success", false,
                            "error", "Crypto payment error: " + e.getMessage()
                        )));
                    });
            });
    }

    /**
     * POST /api/crypto/webhook
     * Coinbase Commerce webhook for payment confirmation.
     * Credits balance when payment is confirmed.
     */
    @PostMapping("/webhook")
    public Mono<ResponseEntity<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-CC-Webhook-Signature") String signature) {
        
        log.info("Received Coinbase webhook");

        // Verify signature
        if (!verifySignature(payload, signature)) {
            log.warn("Invalid Coinbase webhook signature");
            return Mono.just(ResponseEntity.badRequest().body("Invalid signature"));
        }

        try {
            // Parse the webhook payload
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> event = mapper.readValue(payload, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = (Map<String, Object>) event.get("event");
            String eventType = (String) eventData.get("type");

            // Only process confirmed payments
            if ("charge:confirmed".equals(eventType) || "charge:resolved".equals(eventType)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) eventData.get("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");

                String apiKey = (String) metadata.get("api_key");
                String amountStr = (String) metadata.get("amount");
                BigDecimal amount = new BigDecimal(amountStr);

                log.info("Crypto payment confirmed for {} amount ${}", apiKey, amount);

                // Credit the balance
                return apiKeyRepository.addFunds(apiKey, amount)
                    .map(updated -> {
                        log.info("Credited ${} crypto to {}", amount, apiKey);
                        return ResponseEntity.ok("OK");
                    });
            }

            // Handle underpayment/overpayment
            if ("charge:pending".equals(eventType)) {
                log.info("Crypto payment pending confirmation");
            }

            return Mono.just(ResponseEntity.ok("OK"));

        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body("Webhook error: " + e.getMessage()));
        }
    }

    /**
     * Verify Coinbase webhook signature using HMAC-SHA256.
     */
    private boolean verifySignature(String payload, String signature) {
        if (coinbaseWebhookSecret == null || coinbaseWebhookSecret.isBlank()) {
            log.warn("Coinbase webhook secret not configured - skipping verification");
            return true; // Allow in dev mode
        }

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                coinbaseWebhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            hmac.init(secretKey);
            byte[] hash = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            
            return computed.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * GET /api/crypto/supported
     * List supported cryptocurrencies.
     */
    @GetMapping("/supported")
    public ResponseEntity<Map<String, Object>> supportedCoins() {
        return ResponseEntity.ok(Map.of(
            "coins", new String[]{
                "BTC", "ETH", "USDC", "USDT", "SOL", 
                "DOGE", "SHIB", "LTC", "BCH", "MATIC",
                "APE", "DAI", "AVAX", "DOT", "LINK"
            },
            "provider", "Coinbase Commerce",
            "minDeposit", 5.00,
            "maxDeposit", 10000.00
        ));
    }
}
