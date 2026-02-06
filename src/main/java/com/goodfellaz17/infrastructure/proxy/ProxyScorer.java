package com.goodfellaz17.infrastructure.proxy;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Weighted scoring algorithm for proxy selection.
 *
 * Calculates composite score based on:
 * - Health metrics (success rate, ban rate, latency)
 * - Cost efficiency
 * - Geo-matching accuracy
 * - Operation compatibility
 * - Recency and freshness
 *
 * Weights are tunable per operation type.
 */
@Component
public class ProxyScorer {

    /**
     * Scoring weights - configurable per operation type.
     */
    public record ScoringWeights(
        double healthWeight,      // 0.0-1.0 importance of health score
        double costWeight,        // 0.0-1.0 importance of cost efficiency
        double geoWeight,         // 0.0-1.0 importance of geo match
        double freshnessWeight,   // 0.0-1.0 importance of recent success
        double tierWeight         // 0.0-1.0 importance of tier quality
    ) {
        public static ScoringWeights forOperation(OperationType op) {
            return switch (op) {
                // High-risk: prioritize health and tier over cost
                case ACCOUNT_CREATION -> new ScoringWeights(0.35, 0.05, 0.25, 0.15, 0.20);
                case EMAIL_VERIFICATION -> new ScoringWeights(0.35, 0.10, 0.20, 0.15, 0.20);

                // Medium-risk: balanced
                case STREAM_OPERATION -> new ScoringWeights(0.30, 0.20, 0.15, 0.15, 0.20);
                case PLAYLIST_OPERATION -> new ScoringWeights(0.30, 0.20, 0.15, 0.15, 0.20);
                case FOLLOW_OPERATION -> new ScoringWeights(0.30, 0.20, 0.15, 0.15, 0.20);

                // Low-risk: prioritize cost
                case INITIAL_QUERY -> new ScoringWeights(0.20, 0.40, 0.10, 0.10, 0.20);
                case HEALTH_CHECK -> new ScoringWeights(0.15, 0.50, 0.05, 0.10, 0.20);
            };
        }

        public double sum() {
            return healthWeight + costWeight + geoWeight + freshnessWeight + tierWeight;
        }

        public ScoringWeights normalize() {
            double total = sum();
            if (total == 0) return this;
            return new ScoringWeights(
                healthWeight / total,
                costWeight / total,
                geoWeight / total,
                freshnessWeight / total,
                tierWeight / total
            );
        }
    }

    /**
     * Context for scoring a proxy selection.
     */
    public record ScoringContext(
        OperationType operation,
        String targetCountry,
        int remainingQuantity,
        double budgetPerRequest  // Max cost per request in USD
    ) {
        public static ScoringContext forOperation(OperationType op, String country) {
            return new ScoringContext(op, country, 1, 0.10); // Default 10 cents/request max
        }
    }

    /**
     * Calculate composite score for a proxy given operation context.
     *
     * @param metrics Proxy metrics
     * @param context Scoring context (operation, geo, budget)
     * @return Score 0.0-1.0, higher is better
     */
    public double calculateScore(ProxyMetrics metrics, ScoringContext context) {
        if (!metrics.isAvailable()) {
            return 0.0; // Circuit open or unhealthy
        }

        ScoringWeights weights = ScoringWeights.forOperation(context.operation()).normalize();

        // 1. Health Score (0-1)
        double healthScore = metrics.getHealthScore();

        // 2. Cost Efficiency Score (0-1)
        double costScore = calculateCostScore(metrics.getTier(), context.budgetPerRequest());

        // 3. Geo Match Score (0-1)
        double geoScore = calculateGeoScore(metrics, context.targetCountry());

        // 4. Freshness Score (0-1) - recently successful is better
        double freshnessScore = calculateFreshnessScore(metrics);

        // 5. Tier Quality Score (0-1)
        double tierScore = calculateTierScore(metrics.getTier(), context.operation());

        // Weighted composite
        double compositeScore =
            (healthScore * weights.healthWeight()) +
            (costScore * weights.costWeight()) +
            (geoScore * weights.geoWeight()) +
            (freshnessScore * weights.freshnessWeight()) +
            (tierScore * weights.tierWeight());

        // Apply minimum tier requirement penalty
        ProxyTier minTier = context.operation().getMinimumTier();
        if (!metrics.getTier().meetsRequirement(minTier)) {
            compositeScore *= 0.5; // Heavy penalty for below-minimum tier
        }

        return Math.min(1.0, Math.max(0.0, compositeScore));
    }

    /**
     * Calculate cost efficiency score.
     * Cheaper proxies score higher when cost matters.
     */
    private double calculateCostScore(ProxyTier tier, double budgetPerRequest) {
        double costPerGb = tier.getCostPerGb();

        // Free tiers get perfect cost score
        if (costPerGb == 0) return 1.0;

        // Estimate cost per request (assume 50KB average)
        double costPerRequest = costPerGb * 50.0 / 1_000_000.0;

        // Score based on budget
        if (costPerRequest <= budgetPerRequest * 0.1) return 1.0;  // Very cheap
        if (costPerRequest <= budgetPerRequest * 0.3) return 0.85;
        if (costPerRequest <= budgetPerRequest * 0.5) return 0.7;
        if (costPerRequest <= budgetPerRequest * 0.8) return 0.5;
        if (costPerRequest <= budgetPerRequest) return 0.3;
        return 0.1; // Over budget but still usable
    }

    /**
     * Calculate geo-matching score.
     *
     * Phase 1: Tier-based estimation (current implementation)
     * Phase 2: Query actual proxy geo from provider via ProxyNodeEntity.country field
     * Phase 3: Real-time geo verification via IP geolocation API
     */
    private double calculateGeoScore(ProxyMetrics metrics, String targetCountry) {
        if (targetCountry == null || targetCountry.isEmpty()) {
            return 1.0; // No geo requirement
        }

        // Handle WORLDWIDE variants - any geo works
        String normalizedTarget = normalizeGeoTarget(targetCountry);
        if ("WW".equals(normalizedTarget)) {
            return 1.0;
        }

        // Phase 2: Use actual proxy country if available
        if (metrics.getCountry() != null && !metrics.getCountry().isBlank()) {
            boolean exactMatch = metrics.getCountry().equalsIgnoreCase(normalizedTarget);
            // Continental match scores (e.g., DE matches EU_FOCUSED)
            boolean continentalMatch = isContinentalMatch(metrics.getCountry(), normalizedTarget);
            // Same region match (e.g., US and CA both in NA)
            boolean regionMatch = isSameRegion(metrics.getCountry(), normalizedTarget);

            if (exactMatch) return 1.0;
            if (continentalMatch) return 0.85;
            if (regionMatch) return 0.75;
            return 0.4; // Different region
        }

        // Phase 1 fallback: Tier-based estimation (proxy geo unknown)
        ProxyTier tier = metrics.getTier();
        return switch (tier) {
            case MOBILE -> 0.95;       // Mobile usually accurate
            case RESIDENTIAL -> 0.90;  // Residential good accuracy
            case ISP -> 0.85;          // ISP decent accuracy
            case DATACENTER -> 0.70;   // Datacenter sometimes wrong
            case TOR -> 0.30;          // TOR exit nodes are worldwide
            case USER_ARBITRAGE -> 0.80; // User location usually known
        };
    }

    /**
     * Normalize geo target strings (WORLDWIDE, WW, US, USA -> normalized codes).
     */
    private String normalizeGeoTarget(String target) {
        if (target == null) return null;
        String upper = target.trim().toUpperCase();

        return switch (upper) {
            case "WORLDWIDE", "WW", "GLOBAL", "ANY", "" -> "WW";
            case "USA", "UNITED STATES", "UNITED STATES OF AMERICA" -> "US";
            case "UK", "UNITED KINGDOM", "GREAT BRITAIN" -> "GB";
            default -> upper.length() > 2 ? upper.substring(0, 2) : upper;
        };
    }

    /**
     * Check for continental/focused geo matching.
     * Supports EU_FOCUSED, US_FOCUSED targeting for broader regional orders.
     */
    private boolean isContinentalMatch(String proxyCountry, String targetGeo) {
        // EU countries
        Set<String> EU = Set.of("DE", "FR", "IT", "ES", "NL", "BE", "AT", "PL", "SE", "DK",
                                "FI", "NO", "IE", "PT", "GR", "CZ", "RO", "HU", "CH");
        // North America
        Set<String> US = Set.of("US", "CA"); // Include Canada for NA targeting

        String upperProxy = proxyCountry.toUpperCase();

        if ("EU_FOCUSED".equals(targetGeo) && EU.contains(upperProxy)) return true;
        if ("US_FOCUSED".equals(targetGeo) && US.contains(upperProxy)) return true;
        if ("NA".equals(targetGeo) && US.contains(upperProxy)) return true;

        return false;
    }

    /**
     * Check if two countries are in the same geographic region.
     * Used for partial geo matching (e.g., US order can use CA proxy at lower score).
     */
    private boolean isSameRegion(String country1, String country2) {
        String region1 = getRegion(country1);
        String region2 = getRegion(country2);
        return region1 != null && region1.equals(region2);
    }

    /**
     * Map country to geographic region for partial geo matching.
     */
    private String getRegion(String country) {
        if (country == null) return null;

        return switch (country.toUpperCase()) {
            // North America
            case "US", "CA", "MX" -> "NA";
            // Western Europe
            case "GB", "DE", "FR", "NL", "BE", "IE", "AT", "CH" -> "EU_WEST";
            // Northern Europe
            case "SE", "NO", "DK", "FI" -> "EU_NORTH";
            // Southern Europe
            case "ES", "IT", "PT", "GR" -> "EU_SOUTH";
            // Eastern Europe
            case "PL", "CZ", "RO", "HU" -> "EU_EAST";
            // Asia Pacific
            case "AU", "NZ", "SG", "JP", "KR" -> "APAC";
            // South America
            case "BR", "AR", "CL", "CO" -> "LATAM";
            default -> null; // Unknown region
        };
    }

    /**
     * Calculate freshness score based on recent activity.
     */
    private double calculateFreshnessScore(ProxyMetrics metrics) {
        if (metrics.getLastSuccess() == null) {
            // Never used - slight penalty (unknown)
            return 0.7;
        }

        long minutesSinceSuccess = java.time.Duration.between(
            metrics.getLastSuccess(),
            java.time.Instant.now()
        ).toMinutes();

        if (minutesSinceSuccess < 5) return 1.0;   // Very fresh
        if (minutesSinceSuccess < 30) return 0.9;  // Recent
        if (minutesSinceSuccess < 60) return 0.8;  // Within hour
        if (minutesSinceSuccess < 360) return 0.6; // Within 6 hours
        if (minutesSinceSuccess < 1440) return 0.4; // Within day
        return 0.2; // Stale
    }

    /**
     * Calculate tier quality score relative to operation requirements.
     */
    private double calculateTierScore(ProxyTier tier, OperationType operation) {
        double tierQuality = tier.getQualityScore();
        double requiredQuality = operation.getMinimumTier().getQualityScore();

        if (tierQuality >= requiredQuality) {
            // Meets or exceeds requirements
            // Slight bonus for exact match (avoid overkill)
            double overQuality = tierQuality - requiredQuality;
            return 1.0 - (overQuality * 0.1); // Small penalty for overkill
        } else {
            // Below requirements - scale penalty
            double deficit = requiredQuality - tierQuality;
            return Math.max(0.1, 1.0 - (deficit * 2));
        }
    }

    /**
     * Score breakdown for debugging/logging.
     */
    public record ScoreBreakdown(
        double healthScore,
        double costScore,
        double geoScore,
        double freshnessScore,
        double tierScore,
        double compositeScore,
        ScoringWeights weights
    ) {
        @Override
        public String toString() {
            return String.format(
                "Score[health=%.2f×%.1f + cost=%.2f×%.1f + geo=%.2f×%.1f + fresh=%.2f×%.1f + tier=%.2f×%.1f = %.3f]",
                healthScore, weights.healthWeight(),
                costScore, weights.costWeight(),
                geoScore, weights.geoWeight(),
                freshnessScore, weights.freshnessWeight(),
                tierScore, weights.tierWeight(),
                compositeScore
            );
        }
    }

    /**
     * Calculate score with full breakdown.
     */
    public ScoreBreakdown calculateScoreWithBreakdown(ProxyMetrics metrics, ScoringContext context) {
        if (!metrics.isAvailable()) {
            ScoringWeights weights = ScoringWeights.forOperation(context.operation()).normalize();
            return new ScoreBreakdown(0, 0, 0, 0, 0, 0, weights);
        }

        ScoringWeights weights = ScoringWeights.forOperation(context.operation()).normalize();

        double healthScore = metrics.getHealthScore();
        double costScore = calculateCostScore(metrics.getTier(), context.budgetPerRequest());
        double geoScore = calculateGeoScore(metrics, context.targetCountry());
        double freshnessScore = calculateFreshnessScore(metrics);
        double tierScore = calculateTierScore(metrics.getTier(), context.operation());

        double compositeScore = calculateScore(metrics, context);

        return new ScoreBreakdown(
            healthScore, costScore, geoScore, freshnessScore, tierScore,
            compositeScore, weights
        );
    }
}
