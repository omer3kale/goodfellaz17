package com.goodfellaz17.infrastructure.config;

import com.goodfellaz17.domain.port.ProxySource;
import com.goodfellaz17.domain.port.ProxyStrategy;
import com.goodfellaz17.infrastructure.proxy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring configuration for the hybrid proxy routing layer.
 * 
 * Reads proxy.hybrid.* config and instantiates the active ProxySource implementations.
 * Exposes a single ProxyStrategy bean that the rest of the system uses.
 * 
 * Activated when: proxy.hybrid.enabled=true
 */
@Configuration
@EnableConfigurationProperties(HybridProxyProperties.class)
@ConditionalOnProperty(prefix = "proxy.hybrid", name = "enabled", havingValue = "true", matchIfMissing = false)
public class HybridProxyConfig {
    
    private static final Logger log = LoggerFactory.getLogger(HybridProxyConfig.class);
    
    private final HybridProxyProperties properties;
    
    public HybridProxyConfig(HybridProxyProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    public ProxyStrategy hybridProxyStrategy() {
        List<ProxySource> sources = new ArrayList<>();
        
        // Build AWS source
        ProxySource aws = buildAwsSource();
        if (aws != null) {
            sources.add(aws);
        }
        
        // Build Tor source
        ProxySource tor = buildTorSource();
        if (tor != null) {
            sources.add(tor);
        }
        
        // Build Mobile source (works without phones - graceful no-capacity)
        ProxySource mobile = buildMobileSource();
        if (mobile != null) {
            sources.add(mobile);
        }
        
        // Build P2P source
        ProxySource p2p = buildP2pSource();
        if (p2p != null) {
            sources.add(p2p);
        }
        
        log.info("HybridProxyStrategy initialized with {} sources: {}",
            sources.size(),
            sources.stream().map(s -> s.getName() + "(" + (s.isEnabled() ? "enabled" : "disabled") + ")").collect(Collectors.joining(", ")));
        
        return new HybridProxyStrategy(sources);
    }
    
    private ProxySource buildAwsSource() {
        HybridProxyProperties.AwsSourceConfig cfg = properties.getSources().getAws();
        
        List<AwsProxySource.ProxyEndpoint> endpoints = cfg.getEndpoints().stream()
            .map(e -> new AwsProxySource.ProxyEndpoint(e.getHost(), e.getPort(), e.getAuth()))
            .collect(Collectors.toList());
        
        AwsProxySource source = new AwsProxySource(
            cfg.isEnabled(),
            cfg.getCapacityPerDay(),
            cfg.getCostPer1k(),
            cfg.getGeos(),
            endpoints
        );
        
        log.info("AWS proxy source: enabled={}, capacity={}/day, endpoints={}",
            cfg.isEnabled(), cfg.getCapacityPerDay(), endpoints.size());
        
        return source;
    }
    
    private ProxySource buildTorSource() {
        HybridProxyProperties.TorSourceConfig cfg = properties.getSources().getTor();
        
        // Convert List<Integer> to int[] for multi-port rotation
        List<Integer> portList = cfg.getSocksPorts();
        int[] ports;
        if (portList != null && !portList.isEmpty()) {
            ports = portList.stream().mapToInt(Integer::intValue).toArray();
        } else {
            ports = new int[]{cfg.getSocksPort()}; // Fallback to single port
        }
        
        TorProxySource source = new TorProxySource(
            cfg.isEnabled(),
            cfg.getCapacityPerDay(),
            cfg.getCostPer1k(),
            cfg.getGeos(),
            cfg.getSocksHost(),
            ports
        );
        
        log.info("🧅 Tor proxy source: enabled={}, capacity={}/day @ ${}/1k (FREE!), ports={}",
            cfg.isEnabled(), cfg.getCapacityPerDay(), cfg.getCostPer1k(),
            java.util.Arrays.toString(ports));
        
        return source;
    }
    
    private ProxySource buildMobileSource() {
        HybridProxyProperties.MobileSourceConfig cfg = properties.getSources().getMobile();
        
        List<MobileProxySource.GatewayEndpoint> gateways = cfg.getGatewayEndpoints().stream()
            .map(g -> new MobileProxySource.GatewayEndpoint(g.getHost(), g.getPort()))
            .collect(Collectors.toList());
        
        MobileProxySource source = new MobileProxySource(
            cfg.isEnabled(),
            cfg.getCapacityPerDay(),
            cfg.getCostPer1k(),
            cfg.getGeos(),
            gateways
        );
        
        log.info("Mobile proxy source: enabled={}, capacity={}/day, gateways={} (ready={})",
            cfg.isEnabled(), cfg.getCapacityPerDay(), gateways.size(), source.isReady());
        
        return source;
    }
    
    private ProxySource buildP2pSource() {
        HybridProxyProperties.P2pSourceConfig cfg = properties.getSources().getP2p();
        
        List<P2pProxySource.GatewayEndpoint> endpoints = cfg.getEndpoints().stream()
            .map(e -> new P2pProxySource.GatewayEndpoint(e.getHost(), e.getPort(), e.getUsername(), e.getPassword()))
            .collect(Collectors.toList());
        
        P2pProxySource source = new P2pProxySource(
            cfg.isEnabled(),
            cfg.getCapacityPerDay(),
            cfg.getCostPer1k(),
            cfg.getGeos(),
            endpoints
        );
        
        log.info("P2P proxy source: enabled={}, capacity={}/day, endpoints={}",
            cfg.isEnabled(), cfg.getCapacityPerDay(), endpoints.size());
        
        return source;
    }
    
    /**
     * Bean to expose all sources for monitoring/admin endpoints.
     */
    @Bean
    public List<ProxySource> proxySources(ProxyStrategy strategy) {
        return strategy.getSources();
    }
}
