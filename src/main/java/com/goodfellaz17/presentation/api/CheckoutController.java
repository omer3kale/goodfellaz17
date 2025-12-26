package com.goodfellaz17.presentation.api;

import com.goodfellaz17.infrastructure.persistence.repository.ApiKeyRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Checkout Controller - Stripe payments for balance top-up.
 * 
 * POST /api/checkout/create-session → Stripe checkout URL
 * POST /api/checkout/webhook → Stripe webhook (balance credit)
 */
@RestController
@RequestMapping("/api/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final ApiKeyRepository apiKeyRepository;

    @Value("${stripe.secret-key:sk_test_placeholder}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:whsec_placeholder}")
    private String stripeWebhookSecret;

    @Value("${app.base-url:https://goodfellaz17.onrender.com}")
    private String baseUrl;

    public CheckoutController(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
        log.info("Stripe initialized with key: {}...", 
            stripeSecretKey.length() > 10 ? stripeSecretKey.substring(0, 10) : "NOT_SET");
    }

    /**
     * POST /api/checkout/create-session
     * Create a Stripe checkout session for balance top-up.
     * 
     * @param apiKey Customer's API key
     * @param amount Amount in USD to add to balance
     * @return Stripe checkout URL
     */
    @PostMapping("/create-session")
    public Mono<ResponseEntity<Map<String, Object>>> createCheckoutSession(
            @RequestParam String apiKey,
            @RequestParam BigDecimal amount) {
        
        log.info("Creating checkout session for {} amount ${}", apiKey, amount);

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

                try {
                    // Create Stripe checkout session
                    SessionCreateParams params = SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.PAYMENT)
                        .setSuccessUrl(baseUrl + "/customer?key=" + apiKey + "&deposit=success")
                        .setCancelUrl(baseUrl + "/checkout?key=" + apiKey + "&deposit=cancelled")
                        .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                    SessionCreateParams.LineItem.PriceData.builder()
                                        .setCurrency("usd")
                                        .setUnitAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())
                                        .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                .setName("GOODFELLAZ17 Balance Top-Up")
                                                .setDescription("Add $" + amount + " to your account balance")
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .putMetadata("api_key", apiKey)
                        .putMetadata("amount", amount.toString())
                        .build();

                    Session session = Session.create(params);

                    log.info("Created Stripe session {} for {} amount ${}", 
                        session.getId(), apiKey, amount);

                    return Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "sessionId", session.getId(),
                        "url", session.getUrl()
                    )));

                } catch (StripeException e) {
                    log.error("Stripe error for {}: {}", apiKey, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(Map.<String, Object>of(
                        "success", false,
                        "error", "Payment system error: " + e.getMessage()
                    )));
                }
            });
    }

    /**
     * POST /api/checkout/webhook
     * Stripe webhook for payment completion.
     * Credits balance when payment succeeds.
     */
    @PostMapping("/webhook")
    public Mono<ResponseEntity<String>> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        log.info("Received Stripe webhook");

        try {
            // Verify webhook signature
            com.stripe.model.Event event = com.stripe.net.Webhook.constructEvent(
                payload, sigHeader, stripeWebhookSecret
            );

            if ("checkout.session.completed".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);

                if (session != null) {
                    String apiKey = session.getMetadata().get("api_key");
                    String amountStr = session.getMetadata().get("amount");
                    BigDecimal amount = new BigDecimal(amountStr);

                    log.info("Payment completed for {} amount ${}", apiKey, amount);

                    // Credit the balance
                    return apiKeyRepository.addFunds(apiKey, amount)
                        .map(updated -> {
                            log.info("Credited ${} to {}", amount, apiKey);
                            return ResponseEntity.ok("OK");
                        });
                }
            }

            return Mono.just(ResponseEntity.ok("OK"));

        } catch (Exception e) {
            log.error("Webhook error: {}", e.getMessage());
            return Mono.just(ResponseEntity.badRequest().body("Webhook error: " + e.getMessage()));
        }
    }

    /**
     * POST /api/checkout/manual-credit (Admin only - for testing)
     * Manually credit balance. In production, protect with admin auth.
     */
    @PostMapping("/manual-credit")
    public Mono<ResponseEntity<Map<String, Object>>> manualCredit(
            @RequestParam String apiKey,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "admin_secret_change_me") String adminKey) {
        
        // Simple admin key check (replace with proper auth in production)
        if (!"admin_secret_change_me".equals(adminKey)) {
            return Mono.just(ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", "Unauthorized"
            )));
        }

        log.info("Manual credit {} to {} by admin", amount, apiKey);

        return apiKeyRepository.addFunds(apiKey, amount)
            .flatMap(updated -> apiKeyRepository.findByApiKey(apiKey))
            .map(entity -> ResponseEntity.ok(Map.<String, Object>of(
                "success", true,
                "apiKey", apiKey,
                "credited", amount,
                "newBalance", entity.getBalance()
            )))
            .defaultIfEmpty(ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "API key not found"
            )));
    }
}
