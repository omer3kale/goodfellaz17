package com.spotifybot.domain.model;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Domain Entity - Premium Spotify account.
 * 
 * Aged premium accounts for royalty-eligible streaming.
 * Daily play limit prevents account flagging.
 */
public class PremiumAccount {
    
    private static final int MAX_DAILY_PLAYS = 1000;
    
    private final UUID id;
    private final String email;
    private final String password;
    private final String cookies;
    private final LocalDate premiumExpiry;
    private final GeoTarget region;
    private final AtomicInteger playsToday;
    private LocalDate lastPlayDate;

    public PremiumAccount(UUID id, String email, String password, String cookies,
                          LocalDate premiumExpiry, GeoTarget region) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.cookies = cookies;
        this.premiumExpiry = premiumExpiry;
        this.region = region;
        this.playsToday = new AtomicInteger(0);
        this.lastPlayDate = LocalDate.now();
    }

    public boolean isPremiumActive() {
        return premiumExpiry.isAfter(LocalDate.now());
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
    public GeoTarget getRegion() { return region; }
    public int getPlaysToday() { return playsToday.get(); }
}
