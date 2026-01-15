package com.goodfellaz17.infrastructure.config;

import com.goodfellaz17.domain.model.generated.*;
import com.goodfellaz17.infrastructure.persistence.generated.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Local Development Data Seeder
 * =============================
 * Runs ONLY on 'local' profile and seeds test data for development.
 * 
 * Creates:
 * - 1 test user with known API key
 * - 1 spotify_plays service with sane pricing
 * - 8 proxies (5 DATACENTER + 3 ISP) with realistic metrics
 * 
 * Idempotent: Safe to run on every startup - checks before insert.
 * 
 * Usage from IDE:
 *   Run Configuration → VM options: -Dspring.profiles.active=local
 *   Or set environment variable: SPRING_PROFILES_ACTIVE=local
 */
@Configuration
@Profile("local")
public class LocalDevSeeder {
    
    private static final Logger log = LoggerFactory.getLogger(LocalDevSeeder.class);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TEST DATA CONSTANTS - Change these to customize your local dev environment
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Fixed API key for local testing - use in curl commands */
    public static final String TEST_API_KEY = "test-api-key-local-dev-12345";
    
    /** Fixed test user email */
    public static final String TEST_USER_EMAIL = "localdev@goodfellaz17.test";
    
    /** Fixed service ID for spotify_plays - predictable for testing */
    public static final UUID SPOTIFY_PLAYS_SERVICE_ID = 
        UUID.fromString("3c1cb593-85a7-4375-8092-d39c00399a7b");
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SEEDER BEAN
    // ═══════════════════════════════════════════════════════════════════════════
    
    @Bean
    ApplicationRunner localDevSeederRunner(
            GeneratedUserRepository userRepo,
            GeneratedServiceRepository serviceRepo,
            GeneratedProxyNodeRepository proxyRepo,
            GeneratedProxyMetricsRepository metricsRepo
    ) {
        return args -> {
            log.info("╔══════════════════════════════════════════════════════════════════╗");
            log.info("║           LOCAL DEV SEEDER - Initializing Test Data              ║");
            log.info("╚══════════════════════════════════════════════════════════════════╝");
            
            // Run all seeders in sequence
            seedTestUser(userRepo)
                .then(seedSpotifyPlaysService(serviceRepo))
                .then(seedProxyPool(proxyRepo, metricsRepo))
                .doOnSuccess(v -> {
                    log.info("╔══════════════════════════════════════════════════════════════════╗");
                    log.info("║                   LOCAL DEV READY TO TEST!                       ║");
                    log.info("╠══════════════════════════════════════════════════════════════════╣");
                    log.info("║  API Key: {}  ║", TEST_API_KEY);
                    log.info("║  Test User: {}                     ║", TEST_USER_EMAIL);
                    log.info("║  Service ID: {}   ║", SPOTIFY_PLAYS_SERVICE_ID);
                    log.info("╠══════════════════════════════════════════════════════════════════╣");
                    log.info("║  Quick Test Commands:                                            ║");
                    log.info("║  curl http://localhost:8080/api/admin/capacity                   ║");
                    log.info("║  curl -H 'X-Api-Key: {}' \\      ║", TEST_API_KEY);
                    log.info("║       http://localhost:8080/api/v2/orders/balance                ║");
                    log.info("╚══════════════════════════════════════════════════════════════════╝");
                })
                .doOnError(e -> log.error("❌ Seeder failed: {}", e.getMessage(), e))
                .block(); // Block to ensure seed completes before app starts accepting requests
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // 1. TEST USER SEEDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Mono<Void> seedTestUser(GeneratedUserRepository userRepo) {
        return userRepo.findByApiKey(TEST_API_KEY)
            .hasElement()
            .flatMap(exists -> {
                if (exists) {
                    log.info("✓ Test user already exists with API key: {}", TEST_API_KEY);
                    return Mono.empty();
                }
                
                log.info("→ Creating test user: {} with API key: {}", TEST_USER_EMAIL, TEST_API_KEY);
                
                UserEntity user = UserEntity.builder()
                    .email(TEST_USER_EMAIL)
                    .passwordHash("$2a$10$localdevhash") // BCrypt placeholder
                    .tier(UserTier.RESELLER)  // RESELLER tier for testing tier pricing
                    .balance(new BigDecimal("1000.00"))  // $1000 test balance
                    .apiKey(TEST_API_KEY)
                    .status(UserStatus.ACTIVE)
                    .emailVerified(true)
                    .companyName("Local Dev Test Co")
                    .build();
                
                return userRepo.save(user)
                    .doOnSuccess(u -> log.info("✓ Created test user: {} (id={})", u.getEmail(), u.getId()))
                    .then();
            });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // 2. SPOTIFY PLAYS SERVICE SEEDER
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Mono<Void> seedSpotifyPlaysService(GeneratedServiceRepository serviceRepo) {
        return serviceRepo.findById(SPOTIFY_PLAYS_SERVICE_ID)
            .hasElement()
            .flatMap(exists -> {
                if (exists) {
                    log.info("✓ spotify_plays service already exists (id={})", SPOTIFY_PLAYS_SERVICE_ID);
                    return Mono.empty();
                }
                
                log.info("→ Creating spotify_plays service with id: {}", SPOTIFY_PLAYS_SERVICE_ID);
                
                ServiceEntity service = ServiceEntity.builder()
                    .id(SPOTIFY_PLAYS_SERVICE_ID)
                    .name("spotify_plays")
                    .displayName("Spotify Track Plays")
                    .serviceType("SPOTIFY")
                    .description("High-quality Spotify plays from premium accounts. Safe drip-feed delivery.")
                    .costPer1k(new BigDecimal("2.50"))       // $2.50/1k for CONSUMER
                    .resellerCostPer1k(new BigDecimal("2.00")) // $2.00/1k for RESELLER
                    .agencyCostPer1k(new BigDecimal("1.50"))   // $1.50/1k for AGENCY
                    .minQuantity(100)
                    .maxQuantity(100000)  // Allow up to 100k per order
                    .estimatedDaysMin(1)
                    .estimatedDaysMax(7)
                    .geoProfiles("[\"US\", \"UK\", \"DE\", \"WORLDWIDE\"]")
                    .isActive(true)
                    .sortOrder(1)
                    .build();
                
                return serviceRepo.save(service)
                    .doOnSuccess(s -> log.info("✓ Created service: {} (id={})", s.getName(), s.getId()))
                    .then();
            });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // 3. PROXY POOL SEEDER (5 DATACENTER + 3 ISP)
    // ═══════════════════════════════════════════════════════════════════════════
    
    private Mono<Void> seedProxyPool(
            GeneratedProxyNodeRepository proxyRepo,
            GeneratedProxyMetricsRepository metricsRepo
    ) {
        // Define our test proxy pool
        List<ProxySpec> proxySpecs = List.of(
            // 5 DATACENTER proxies (1.0x multiplier = 20 plays/hr each = 100 plays/hr total)
            new ProxySpec("dc-us-1", "DATACENTER", "192.168.1.101", "US", "us-east", "Ashburn", 0.91),
            new ProxySpec("dc-us-2", "DATACENTER", "192.168.1.102", "US", "us-west", "Los Angeles", 0.94),
            new ProxySpec("dc-de-1", "DATACENTER", "192.168.1.103", "DE", "eu-central", "Frankfurt", 0.89),
            new ProxySpec("dc-uk-1", "DATACENTER", "192.168.1.104", "GB", "eu-west", "London", 0.92),
            new ProxySpec("dc-nl-1", "DATACENTER", "192.168.1.105", "NL", "eu-west", "Amsterdam", 0.95),
            
            // 3 ISP proxies (1.5x multiplier = 30 plays/hr each = 90 plays/hr total)
            new ProxySpec("isp-us-1", "ISP", "192.168.2.101", "US", "us-east", "New York", 0.96),
            new ProxySpec("isp-de-1", "ISP", "192.168.2.102", "DE", "eu-central", "Berlin", 0.93),
            new ProxySpec("isp-uk-1", "ISP", "192.168.2.103", "GB", "eu-west", "Manchester", 0.97)
        );
        
        // Total capacity: 100 + 90 = 190 plays/hr base
        // With 0.7 safety factor: 133 safe plays/hr
        // In 72h: ~9,576 plays (enough for multiple 1k orders)
        // To support 15k, we'd need more - but this shows the math works
        
        // Actually let's bump up capacity per proxy to make 15k work
        // 15k / 72h / 0.7 safety = 297 plays/hr needed
        // Let's give each proxy higher capacity (200 instead of 100)
        
        return proxyRepo.count()
            .flatMap(count -> {
                if (count >= 8) {
                    log.info("✓ Proxy pool already seeded ({} proxies exist)", count);
                    return Mono.empty();
                }
                
                log.info("→ Seeding proxy pool with {} proxies...", proxySpecs.size());
                
                return Flux.fromIterable(proxySpecs)
                    .flatMap(spec -> createProxyWithMetrics(spec, proxyRepo, metricsRepo))
                    .then()
                    .doOnSuccess(v -> {
                        log.info("✓ Proxy pool seeded:");
                        log.info("  - 5 DATACENTER proxies (1.0x multiplier)");
                        log.info("  - 3 ISP proxies (1.5x multiplier)");
                        log.info("  - Total base capacity: ~190 plays/hr");
                        log.info("  - Safe capacity (0.7x): ~133 plays/hr");
                        log.info("  - 72h max capacity: ~9,576 plays");
                    });
            });
    }
    
    /**
     * Create a proxy node with associated metrics in a single transaction-like flow.
     */
    private Mono<Void> createProxyWithMetrics(
            ProxySpec spec,
            GeneratedProxyNodeRepository proxyRepo,
            GeneratedProxyMetricsRepository metricsRepo
    ) {
        // Check if this proxy already exists by IP
        return proxyRepo.findByPublicIp(spec.ip)
            .hasElement()
            .flatMap(exists -> {
                if (exists) {
                    log.debug("  → Proxy {} already exists, skipping", spec.name);
                    return Mono.empty();
                }
                
                // Create the proxy node
                ProxyNodeEntity proxy = ProxyNodeEntity.builder()
                    .provider(ProxyProvider.HETZNER)  // Simulated provider
                    .providerInstanceId(spec.name)
                    .publicIp(spec.ip)
                    .port(8080)
                    .region(spec.region)
                    .country(spec.country)
                    .city(spec.city)
                    .tier(ProxyTier.valueOf(spec.tier))
                    .capacity(200)  // High capacity for local testing
                    .currentLoad(0)
                    .costPerHour(new BigDecimal("0.01"))  // Minimal cost for tracking
                    .status(ProxyStatus.ONLINE)
                    .build();
                
                return proxyRepo.save(proxy)
                    .flatMap(savedProxy -> {
                        // Create metrics for this proxy with realistic values
                        ProxyMetricsEntity metrics = new ProxyMetricsEntity(savedProxy.getId());
                        metrics.setTotalRequests(1000L + (long)(Math.random() * 5000));
                        metrics.setSuccessfulRequests((long)(metrics.getTotalRequests() * spec.successRate));
                        metrics.setFailedRequests(metrics.getTotalRequests() - metrics.getSuccessfulRequests());
                        metrics.setSuccessRate(spec.successRate);
                        metrics.setBanRate(0.02 + Math.random() * 0.03);  // 2-5% ban rate
                        metrics.setLatencyP50(50 + (int)(Math.random() * 100));
                        metrics.setLatencyP95(150 + (int)(Math.random() * 200));
                        metrics.setLatencyP99(300 + (int)(Math.random() * 400));
                        metrics.setActiveConnections((int)(Math.random() * 10));
                        metrics.setPeakConnections(20 + (int)(Math.random() * 30));
                        metrics.setBytesTransferred(1_000_000L + (long)(Math.random() * 10_000_000));
                        metrics.setLastUpdated(Instant.now());
                        metrics.setWindowStart(Instant.now().minusSeconds(3600));  // 1 hour window
                        
                        return metricsRepo.save(metrics);
                    })
                    .doOnSuccess(m -> log.debug("  ✓ Created proxy: {} ({}) - success rate: {}%", 
                        spec.name, spec.tier, (int)(spec.successRate * 100)))
                    .then();
            });
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER RECORD
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Specification for a test proxy node.
     */
    private record ProxySpec(
        String name,
        String tier,
        String ip,
        String country,
        String region,
        String city,
        double successRate
    ) {}
}
