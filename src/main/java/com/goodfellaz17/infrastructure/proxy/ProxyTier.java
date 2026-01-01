package com.goodfellaz17.infrastructure.proxy;

/**
 * Proxy tiers ordered by quality/cost.
 * 
 * Selection priority follows operation risk level:
 * - High-risk ops prefer MOBILE → RESIDENTIAL → ISP → DATACENTER
 * - Low-risk ops prefer DATACENTER → ISP → RESIDENTIAL → MOBILE (cost optimize)
 */
public enum ProxyTier {
    
    /**
     * Mobile carrier IPs - HIGHEST QUALITY.
     * - Real 4G/5G carrier IPs (AT&T, Verizon, T-Mobile)
     * - Extremely hard to detect/block
     * - Best for account creation
     * - Cost: ~$20-30/GB
     */
    MOBILE(1.0, 25.0, 0.98, 100),
    
    /**
     * Residential IPs - HIGH QUALITY.
     * - Real home user IPs (ISP assigned)
     * - Excellent for most operations
     * - Good geo-targeting
     * - Cost: ~$8-15/GB
     */
    RESIDENTIAL(0.85, 12.0, 0.92, 500),
    
    /**
     * ISP proxies - MEDIUM-HIGH QUALITY.
     * - Static residential IPs
     * - Good for sticky sessions
     * - More detectable than residential
     * - Cost: ~$2-5/GB
     */
    ISP(0.7, 3.5, 0.85, 1000),
    
    /**
     * Datacenter proxies - BASIC QUALITY.
     * - Cloud/hosting provider IPs
     * - Fast but easily fingerprinted
     * - Good for low-risk, high-volume
     * - Cost: ~$0.50-1.5/GB
     */
    DATACENTER(0.4, 1.0, 0.70, 5000),
    
    /**
     * TOR network - FREE but LOWEST QUALITY.
     * - Free anonymity network
     * - Very slow, often blocked
     * - Exit nodes are well-known
     * - Cost: FREE
     */
    TOR(0.2, 0.0, 0.40, 10000),
    
    /**
     * User arbitrage - FREE, VARIABLE QUALITY.
     * - User-provided IPs (desktop app users)
     * - Quality varies by user
     * - Best value when available
     * - Cost: FREE (commission only)
     */
    USER_ARBITRAGE(0.6, 0.0, 0.75, 2000);
    
    private final double qualityScore;      // 0.0-1.0 base quality rating
    private final double costPerGb;         // USD per GB of traffic
    private final double expectedSuccessRate; // Historical success rate
    private final int dailyCapacity;        // Approximate daily request capacity
    
    ProxyTier(double qualityScore, double costPerGb, double expectedSuccessRate, int dailyCapacity) {
        this.qualityScore = qualityScore;
        this.costPerGb = costPerGb;
        this.expectedSuccessRate = expectedSuccessRate;
        this.dailyCapacity = dailyCapacity;
    }
    
    public double getQualityScore() { return qualityScore; }
    public double getCostPerGb() { return costPerGb; }
    public double getExpectedSuccessRate() { return expectedSuccessRate; }
    public int getDailyCapacity() { return dailyCapacity; }
    
    /**
     * Calculate cost efficiency score (quality per dollar).
     * Higher is better value.
     */
    public double getCostEfficiency() {
        if (costPerGb == 0) return qualityScore * 10; // Free tiers get bonus
        return qualityScore / costPerGb;
    }
    
    /**
     * Check if this tier meets minimum requirements for operation.
     */
    public boolean meetsRequirement(ProxyTier minimum) {
        return this.qualityScore >= minimum.qualityScore;
    }
}
