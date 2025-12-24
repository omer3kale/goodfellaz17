package com.goodfellaz17.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Order;
import com.goodfellaz17.domain.model.OrderStatus;
import com.goodfellaz17.domain.model.SpeedTier;
import com.goodfellaz17.domain.port.OrderRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure Adapter - Supabase Order Repository.
 * 
 * Production implementation connecting to botzzz773.pro Supabase tables.
 * Uses REST API with PostgREST queries.
 * 
 * Activate with: SPRING_PROFILES_ACTIVE=prod
 */
@Repository
@Primary
@Profile("prod")
public class SupabaseOrderRepositoryAdapter implements OrderRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(SupabaseOrderRepositoryAdapter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public SupabaseOrderRepositoryAdapter(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.key}") String supabaseKey) {
        
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(supabaseUrl + "/rest/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + supabaseKey)
                .defaultHeader("apikey", supabaseKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Prefer", "return=representation")
                .build();
        
        log.info("SupabaseOrderRepositoryAdapter initialized: url={}", supabaseUrl);
    }

    @Override
    public Order save(Order order) {
        try {
            String json = toSupabaseJson(order);
            
            webClient.post()
                    .uri("/orders")
                    .bodyValue(json)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.debug("Order saved to Supabase: id={}", order.getId());
            return order;
            
        } catch (Exception e) {
            log.error("Failed to save order: id={}, error={}", order.getId(), e.getMessage());
            throw new RuntimeException("Supabase save failed", e);
        }
    }

    @Override
    public Optional<Order> findById(UUID id) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("id", "eq." + id.toString())
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode array = objectMapper.readTree(response);
            if (array.isEmpty()) {
                return Optional.empty();
            }
            
            return Optional.of(fromSupabaseJson(array.get(0)));
            
        } catch (Exception e) {
            log.error("Failed to find order: id={}, error={}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Order> findPendingOrders() {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("status", "eq.pending")
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            return parseOrderList(response);
            
        } catch (Exception e) {
            log.error("Failed to find pending orders: error={}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Order> findProcessingWithRemaining() {
        try {
            // PostgREST: status=processing AND delivered < quantity
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("status", "eq.processing")
                            .queryParam("delivered", "lt.quantity")
                            .queryParam("select", "*")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            return parseOrderList(response);
            
        } catch (Exception e) {
            log.error("Failed to find processing orders: error={}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void updateDelivered(UUID orderId, int delivered) {
        try {
            String json = String.format("{\"delivered\": %d}", delivered);
            
            webClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/orders")
                            .queryParam("id", "eq." + orderId.toString())
                            .build())
                    .bodyValue(json)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            log.debug("Updated delivered count: orderId={}, delivered={}", orderId, delivered);
            
        } catch (Exception e) {
            log.error("Failed to update delivered: orderId={}, error={}", orderId, e.getMessage());
        }
    }

    // === JSON Serialization ===

    private String toSupabaseJson(Order order) {
        return String.format("""
            {
                "id": "%s",
                "service_id": %d,
                "track_url": "%s",
                "quantity": %d,
                "delivered": %d,
                "geo_target": "%s",
                "speed_tier": "%s",
                "status": "%s",
                "created_at": "%s"
            }
            """,
            order.getId(),
            order.getServiceId(),
            order.getTrackUrl(),
            order.getQuantity(),
            order.getDelivered(),
            order.getGeoTarget().name(),
            order.getSpeedTier().name(),
            order.getStatus().name().toLowerCase(),
            java.time.Instant.now()
        );
    }

    private Order fromSupabaseJson(JsonNode node) {
        return Order.builder()
                .id(UUID.fromString(node.get("id").asText()))
                .serviceId(node.get("service_id").asInt())
                .trackUrl(node.get("track_url").asText())
                .quantity(node.get("quantity").asInt())
                .delivered(node.get("delivered").asInt())
                .geoTarget(GeoTarget.valueOf(node.get("geo_target").asText()))
                .speedTier(SpeedTier.valueOf(node.get("speed_tier").asText()))
                .status(OrderStatus.valueOf(node.get("status").asText().toUpperCase()))
                .build();
    }

    private List<Order> parseOrderList(String response) {
        try {
            JsonNode array = objectMapper.readTree(response);
            return java.util.stream.StreamSupport.stream(array.spliterator(), false)
                    .map(this::fromSupabaseJson)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to parse order list: error={}", e.getMessage());
            return List.of();
        }
    }
}
