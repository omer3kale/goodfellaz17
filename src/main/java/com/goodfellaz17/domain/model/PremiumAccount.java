package com.goodfellaz17.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Domain Entity - Premium Spotify account.
 * 
 * Aged premium accounts for royalty-eligible streaming.
 * Daily play limit prevents account flagging.
 * 
 * OAuth tokens for Spotify Web API integration:
 * - refreshToken: Long-lived (~1 year), used to get new access tokens
 * - accessToken: Short-lived (1h), used for API calls
 * 
 * R2DBC mapped to Neon PostgreSQL table: premium_accounts
 */
@Table("premium_accounts")
public class PremiumAccount {
    
    private static final int MAX_DAILY_PLAYS = 1000;
    
    @Id
    private UUID id;
    
    private String email;
    private String password;
    private String cookies;
    
    @Column("premium_expiry")
    private LocalDate premiumExpiry;
    
    private String region;
    
    @Column("spotify_refresh_token")
    private String refreshToken;
    
    @Column("spotify_access_token")
    private String accessToken;
    
    // Transient - not persisted
    private transient AtomicInteger playsToday = new AtomicInteger(0);
    private transient LocalDate lastPlayDate = LocalDate.now();

    // Default constructor for R2DBC
    public PremiumAccount() {
        this.playsToday = new AtomicInteger(0);
        this.lastPlayDate = LocalDate.now();
    }

    public PremiumAccount(UUID id, String email, String password, String refreshToken,
                          LocalDate premiumExpiry, GeoTarget region) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.refreshToken = refreshToken;
        this.premiumExpiry = premiumExpiry;
        this.region = region != null ? region.name() : "WORLDWIDE";
        this.playsToday = new AtomicInteger(0);
        this.lastPlayDate = LocalDate.now();
    }

    public boolean isPremiumActive() {
        return premiumExpiry != null && premiumExpiry.isAfter(LocalDate.now());
    }

    public boolean canPlay() {
        resetDailyCounterIfNeeded();
        return isPremiumActive() && playsToday.get() < MAX_DAILY_PLAYS;
    }

    public void recordPlay() {
        resetDailyCounterIfNeeded();
        playsToday.incrementAndGet();
    }

    private void resetDailyCounterIfNeeded() {
        if (playsToday == null) playsToday = new AtomicInteger(0);
        LocalDate today = LocalDate.now();
        if (!today.equals(lastPlayDate)) {
            playsToday.set(0);
            lastPlayDate = today;
        }
    }

    // Getters
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getCookies() { return cookies; }
    
    public GeoTarget getRegion() { 
        if (region == null) return GeoTarget.WORLDWIDE;
        try {
            return GeoTarget.valueOf(region);
        } catch (Exception e) {
            return GeoTarget.WORLDWIDE;
        }
    }
    
    public int getPlaysToday() { 
        if (playsToday == null) playsToday = new AtomicInteger(0);
        return playsToday.get(); 
    }
    
    // OAuth token getters/setters
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    
    public boolean hasValidOAuth() {
        return refreshToken != null && !refreshToken.isEmpty();
    }
    
    // Setters for R2DBC
    public void setId(UUID id) { this.id = id; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setCookies(String cookies) { this.cookies = cookies; }
    public void setPremiumExpiry(LocalDate premiumExpiry) { this.premiumExpiry = premiumExpiry; }
    public void setRegion(String region) { this.region = region; }
}
