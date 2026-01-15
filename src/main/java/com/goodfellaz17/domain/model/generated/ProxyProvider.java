package com.goodfellaz17.domain.model.generated;

/**
 * GENERATED FROM: DomainModel.mc4 / Goodfellaz17.dm
 * DO NOT EDIT MANUALLY - Changes will be overwritten on next generation.
 * 
 * Cloud/VPS providers for proxy infrastructure.
 * 
 * @generated MontiCore DomainModel Generator v1.0.0
 */
public enum ProxyProvider {
    
    VULTR("vultr"),
    HETZNER("hetzner"),
    NETCUP("netcup"),
    CONTABO("contabo"),
    OVH("ovh"),
    AWS("aws"),
    DIGITALOCEAN("digitalocean"),
    LINODE("linode");
    
    private final String value;
    
    ProxyProvider(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public static ProxyProvider fromValue(String value) {
        for (ProxyProvider provider : values()) {
            if (provider.value.equals(value) || provider.name().equalsIgnoreCase(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown ProxyProvider: " + value);
    }
}
