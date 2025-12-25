package com.goodfellaz17.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for hybrid proxy routing.
 * Loaded from application-hybrid.yml when hybrid profile is active.
 */
@Component
@ConfigurationProperties(prefix = "proxy.hybrid")
public class HybridProxyProperties {
    
    private boolean enabled = false;
    private SourcesConfig sources = new SourcesConfig();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public SourcesConfig getSources() {
        return sources;
    }
    
    public void setSources(SourcesConfig sources) {
        this.sources = sources;
    }
    
    // =========================================================================
    // Nested config classes
    // =========================================================================
    
    public static class SourcesConfig {
        private AwsSourceConfig aws = new AwsSourceConfig();
        private TorSourceConfig tor = new TorSourceConfig();
        private MobileSourceConfig mobile = new MobileSourceConfig();
        private P2pSourceConfig p2p = new P2pSourceConfig();
        
        public AwsSourceConfig getAws() { return aws; }
        public void setAws(AwsSourceConfig aws) { this.aws = aws; }
        
        public TorSourceConfig getTor() { return tor; }
        public void setTor(TorSourceConfig tor) { this.tor = tor; }
        
        public MobileSourceConfig getMobile() { return mobile; }
        public void setMobile(MobileSourceConfig mobile) { this.mobile = mobile; }
        
        public P2pSourceConfig getP2p() { return p2p; }
        public void setP2p(P2pSourceConfig p2p) { this.p2p = p2p; }
    }
    
    // -------------------------------------------------------------------------
    // AWS Source Config
    // -------------------------------------------------------------------------
    public static class AwsSourceConfig {
        private boolean enabled = false;
        private int capacityPerDay = 0;
        private double costPer1k = 0.10;
        private boolean premium = true;
        private List<String> geos = new ArrayList<>();
        private List<AwsEndpoint> endpoints = new ArrayList<>();
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getCapacityPerDay() { return capacityPerDay; }
        public void setCapacityPerDay(int capacityPerDay) { this.capacityPerDay = capacityPerDay; }
        
        public double getCostPer1k() { return costPer1k; }
        public void setCostPer1k(double costPer1k) { this.costPer1k = costPer1k; }
        
        public boolean isPremium() { return premium; }
        public void setPremium(boolean premium) { this.premium = premium; }
        
        public List<String> getGeos() { return geos; }
        public void setGeos(List<String> geos) { this.geos = geos; }
        
        public List<AwsEndpoint> getEndpoints() { return endpoints; }
        public void setEndpoints(List<AwsEndpoint> endpoints) { this.endpoints = endpoints; }
    }
    
    public static class AwsEndpoint {
        private String host;
        private int port = 1080;
        private String auth;
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getAuth() { return auth; }
        public void setAuth(String auth) { this.auth = auth; }
    }
    
    // -------------------------------------------------------------------------
    // Tor Source Config - MULTI-PORT ROTATION
    // -------------------------------------------------------------------------
    public static class TorSourceConfig {
        private boolean enabled = false;
        private int capacityPerDay = 500000; // FREE - high capacity!
        private double costPer1k = 0.00;     // FREE!
        private boolean premium = false;
        private List<String> geos = List.of("GLOBAL", "US", "DE", "GB", "CA");
        private String socksHost = "127.0.0.1";
        private int socksPort = 9050;        // Legacy single port
        private List<Integer> socksPorts = List.of(9050, 9051, 9052, 9060); // Multi-port rotation!
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getCapacityPerDay() { return capacityPerDay; }
        public void setCapacityPerDay(int capacityPerDay) { this.capacityPerDay = capacityPerDay; }
        
        public double getCostPer1k() { return costPer1k; }
        public void setCostPer1k(double costPer1k) { this.costPer1k = costPer1k; }
        
        public boolean isPremium() { return premium; }
        public void setPremium(boolean premium) { this.premium = premium; }
        
        public List<String> getGeos() { return geos; }
        public void setGeos(List<String> geos) { this.geos = geos; }
        
        public String getSocksHost() { return socksHost; }
        public void setSocksHost(String socksHost) { this.socksHost = socksHost; }
        
        public int getSocksPort() { return socksPort; }
        public void setSocksPort(int socksPort) { this.socksPort = socksPort; }
        
        /**
         * Multi-port list for IP rotation.
         * Each port = different Tor circuit = different IP!
         */
        public List<Integer> getSocksPorts() { return socksPorts; }
        public void setSocksPorts(List<Integer> socksPorts) { this.socksPorts = socksPorts; }
    }
    
    // -------------------------------------------------------------------------
    // Mobile Source Config
    // -------------------------------------------------------------------------
    public static class MobileSourceConfig {
        private boolean enabled = false;
        private int capacityPerDay = 0;
        private double costPer1k = 0.30;
        private boolean premium = true;
        private List<String> geos = new ArrayList<>();
        private List<MobileGateway> gatewayEndpoints = new ArrayList<>();
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getCapacityPerDay() { return capacityPerDay; }
        public void setCapacityPerDay(int capacityPerDay) { this.capacityPerDay = capacityPerDay; }
        
        public double getCostPer1k() { return costPer1k; }
        public void setCostPer1k(double costPer1k) { this.costPer1k = costPer1k; }
        
        public boolean isPremium() { return premium; }
        public void setPremium(boolean premium) { this.premium = premium; }
        
        public List<String> getGeos() { return geos; }
        public void setGeos(List<String> geos) { this.geos = geos; }
        
        public List<MobileGateway> getGatewayEndpoints() { return gatewayEndpoints; }
        public void setGatewayEndpoints(List<MobileGateway> gatewayEndpoints) { this.gatewayEndpoints = gatewayEndpoints; }
    }
    
    public static class MobileGateway {
        private String host;
        private int port = 1080;
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }
    
    // -------------------------------------------------------------------------
    // P2P Source Config
    // -------------------------------------------------------------------------
    public static class P2pSourceConfig {
        private boolean enabled = false;
        private int capacityPerDay = 0;
        private double costPer1k = 0.05;
        private boolean premium = false;
        private List<String> geos = new ArrayList<>();
        private List<P2pEndpoint> endpoints = new ArrayList<>();
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getCapacityPerDay() { return capacityPerDay; }
        public void setCapacityPerDay(int capacityPerDay) { this.capacityPerDay = capacityPerDay; }
        
        public double getCostPer1k() { return costPer1k; }
        public void setCostPer1k(double costPer1k) { this.costPer1k = costPer1k; }
        
        public boolean isPremium() { return premium; }
        public void setPremium(boolean premium) { this.premium = premium; }
        
        public List<String> getGeos() { return geos; }
        public void setGeos(List<String> geos) { this.geos = geos; }
        
        public List<P2pEndpoint> getEndpoints() { return endpoints; }
        public void setEndpoints(List<P2pEndpoint> endpoints) { this.endpoints = endpoints; }
    }
    
    public static class P2pEndpoint {
        private String host;
        private int port = 7000;
        private String username;
        private String password;
        
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
