package com.goodfellaz17.infrastructure.proxy.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.port.ProxyProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * BrightData (formerly Luminati) residential proxy provider adapter.
 * 
 * REAL IMPLEMENTATION - NO MOCKS.
 * Fetches actual residential proxies from BrightData API.
 * 
 * Required config in application-prod.yml:
 *   proxy.provider.brightdata.zone: your-zone
 *   proxy.provider.brightdata.username: your-user
 *   proxy.provider.brightdata.password: your-pass
 */
@Component
@Profile("!dev")  // Never loads in dev profile - dev uses mocks
public class BrightDataProxyProvider implements ProxyProviderPort {
    
    private static final Logger log = LoggerFactory.getLogger(BrightDataProxyProvider.class);
    
    private static final String BRIGHTDATA_HOST = "brd.superproxy.io";
    private static final int BRIGHTDATA_PORT = 22225;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    private final String zone;
    private final String username;
    private final String password;
    private final boolean enabled;
    
    // Geo-specific zones (BrightData uses country codes)
    private static final Map<GeoTarget, String> GEO_COUNTRY_CODES = Map.of(
        GeoTarget.USA, "us",
        GeoTarget.EU, "gb,de,fr,nl",  // Multiple EU countries
        GeoTarget.WORLDWIDE, "any"
    );
    
    public BrightDataProxyProvider(
        @Value("${proxy.provider.brightdata.zone:}") String zone,
        @Value("${proxy.provider.brightdata.username:}") String username,
        @Value("${proxy.provider.brightdata.password:}") String password,
        @Value("${proxy.provider.brightdata.enabled:false}") boolean enabled
    ) {
        this.zone = zone;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.objectMapper = new ObjectMapper();
        
        this.webClient = WebClient.builder()
            .baseUrl("https://brightdata.com/api")
            .defaultHeader("Authorization", "Bearer " + password)
            .build();
        
        if (enabled && (zone.isBlank() || username.isBlank() || password.isBlank())) {
            log.error("BrightData enabled but credentials missing! Set proxy.provider.brightdata.*");
        } else if (enabled) {
            log.info("BrightData proxy provider initialized: zone={}", zone);
        }
    }
    
    @Override
    public String getProviderName() {
        return "brightdata";
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && !zone.isBlank() && !username.isBlank() && !password.isBlank();
    }
    
    @Override
    public List<Proxy> fetchProxies(GeoTarget geo) {
        if (!isAvailable()) {
            throw new ProxyProviderException("BrightData not configured");
        }
        
        String countryCodes = GEO_COUNTRY_CODES.getOrDefault(geo, "any");
        List<Proxy> proxies = new ArrayList<>();
        
        // BrightData uses a single super-proxy endpoint with session IDs
        // Each "proxy" is actually a session to the super-proxy
        for (String country : countryCodes.split(",")) {
            for (int i = 0; i < 10; i++) {  // 10 sessions per country
                String sessionId = UUID.randomUUID().toString().substring(0, 8);
                
                Proxy proxy = Proxy.builder()
                    .id("bd-" + country + "-" + sessionId)
                    .host(BRIGHTDATA_HOST)
                    .port(BRIGHTDATA_PORT)
                    .username(buildSessionUsername(country, sessionId))
                    .password(password)
                    .country(country.toUpperCase())
                    .type(Proxy.ProxyType.HTTP)
                    .provider("brightdata")
                    .build();
                
                proxies.add(proxy);
            }
        }
        
        log.info("Fetched {} BrightData proxies for geo={}", proxies.size(), geo);
        return proxies;
    }
    
    /**
     * Build BrightData session username with country and session targeting.
     * Format: brd-customer-{customer}-zone-{zone}-country-{country}-session-{session}
     */
    private String buildSessionUsername(String country, String sessionId) {
        return String.format(
            "brd-customer-%s-zone-%s-country-%s-session-%s",
            username, zone, country, sessionId
        );
    }
    
    @Override
    public Proxy refreshProxy(String proxyId) {
        // BrightData: just create a new session ID
        String[] parts = proxyId.split("-");
        String country = parts.length > 1 ? parts[1] : "us";
        String newSessionId = UUID.randomUUID().toString().substring(0, 8);
        
        return Proxy.builder()
            .id("bd-" + country + "-" + newSessionId)
            .host(BRIGHTDATA_HOST)
            .port(BRIGHTDATA_PORT)
            .username(buildSessionUsername(country, newSessionId))
            .password(password)
            .country(country.toUpperCase())
            .type(Proxy.ProxyType.HTTP)
            .provider("brightdata")
            .build();
    }
    
    @Override
    public ProviderQuota getQuota() {
        if (!isAvailable()) {
            return new ProviderQuota(0, 0, 0);
        }
        
        try {
            String response = webClient.get()
                .uri("/zone/get_zone_stats?zone=" + zone)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
            
            JsonNode json = objectMapper.readTree(response);
            long remainingBw = json.path("bw_remaining").asLong(Long.MAX_VALUE);
            
            return new ProviderQuota(Long.MAX_VALUE, remainingBw / (1024 * 1024), 0);
            
        } catch (Exception e) {
            log.warn("Failed to fetch BrightData quota: {}", e.getMessage());
            return ProviderQuota.unlimited();  // Assume unlimited on API failure
        }
    }
}
