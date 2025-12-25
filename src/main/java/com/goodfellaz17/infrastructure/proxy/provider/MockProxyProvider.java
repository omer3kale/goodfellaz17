package com.goodfellaz17.infrastructure.proxy.provider;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.Proxy;
import com.goodfellaz17.domain.port.ProxyProviderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MOCK proxy provider for development/testing ONLY.
 * 
 * NEVER LOADS IN PROD - protected by @Profile("dev").
 * Generates fake proxies for local testing without real provider accounts.
 */
@Component
@Profile("dev")  // ONLY loads in dev profile
public class MockProxyProvider implements ProxyProviderPort {
    
    private static final Logger log = LoggerFactory.getLogger(MockProxyProvider.class);
    
    public MockProxyProvider() {
        log.warn("⚠️  MockProxyProvider loaded - THIS IS DEV MODE ONLY");
    }
    
    @Override
    public String getProviderName() {
        return "mock";
    }
    
    @Override
    public boolean isAvailable() {
        return true;  // Always available in dev
    }
    
    @Override
    public List<Proxy> fetchProxies(GeoTarget geo) {
        log.warn("Using MOCK proxies for geo={} - NOT FOR PRODUCTION", geo);
        
        List<Proxy> proxies = new ArrayList<>();
        String country = switch (geo) {
            case USA -> "US";
            case EU -> "DE";
            case WORLDWIDE -> "WW";
        };
        
        for (int i = 0; i < 20; i++) {
            String sessionId = UUID.randomUUID().toString().substring(0, 8);
            
            Proxy proxy = Proxy.builder()
                .id("mock-" + country.toLowerCase() + "-" + sessionId)
                .host("127.0.0.1")
                .port(8080 + i)
                .username("mock-user")
                .password("mock-pass")
                .country(country)
                .type(Proxy.ProxyType.HTTP)
                .provider("mock")
                .build();
            
            proxies.add(proxy);
        }
        
        log.info("Generated {} MOCK proxies for geo={}", proxies.size(), geo);
        return proxies;
    }
    
    @Override
    public Proxy refreshProxy(String proxyId) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        return Proxy.builder()
            .id("mock-refresh-" + sessionId)
            .host("127.0.0.1")
            .port(8080)
            .username("mock-user")
            .password("mock-pass")
            .country("US")
            .type(Proxy.ProxyType.HTTP)
            .provider("mock")
            .build();
    }
    
    @Override
    public ProviderQuota getQuota() {
        return ProviderQuota.unlimited();
    }
}
