package com.spotifybot.infrastructure.bot;

import com.spotifybot.domain.model.GeoTarget;
import com.spotifybot.domain.model.PremiumAccount;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Infrastructure Component - Premium Account Farm.
 * 
 * Manages 1000+ aged Spotify Premium accounts:
 * - Cookie-based login (no password prompts)
 * - Daily play limits (1000/account)
 * - Premium expiry tracking
 * - Geo-matched account selection
 */
@Component
public class PremiumAccountFarm {

    private static final Logger log = LoggerFactory.getLogger(PremiumAccountFarm.class);

    @Value("${bot.accounts.farm-size:100}")
    private int farmSize;

    private final List<PremiumAccount> accounts = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing account farm: targetSize={}", farmSize);
        
        // Load from database or create mock accounts
        loadAccounts();
        
        log.info("Account farm initialized: size={}, healthy={}", 
                accounts.size(), healthyCount());
    }

    /**
     * Get next healthy account for geo target.
     */
    public Optional<PremiumAccount> nextHealthyAccount(GeoTarget geo) {
        return accounts.stream()
                .filter(PremiumAccount::canPlay)
                .filter(a -> a.getRegion() == geo || geo == GeoTarget.WORLDWIDE)
                .findFirst();
    }

    /**
     * Get any healthy account (fallback).
     */
    public Optional<PremiumAccount> nextHealthyAccount() {
        return accounts.stream()
                .filter(PremiumAccount::canPlay)
                .findFirst();
    }

    /**
     * Count accounts that can still play today.
     */
    public int healthyCount() {
        return (int) accounts.stream()
                .filter(PremiumAccount::canPlay)
                .count();
    }

    /**
     * Get total daily capacity remaining.
     */
    public int remainingDailyCapacity() {
        return accounts.stream()
                .filter(PremiumAccount::isPremiumActive)
                .mapToInt(a -> 1000 - a.getPlaysToday())
                .sum();
    }

    @Scheduled(cron = "0 0 0 * * *") // Midnight reset
    public void dailyReset() {
        log.info("Daily account reset triggered");
        // Play counters auto-reset via PremiumAccount.resetDailyCounterIfNeeded()
    }

    @Scheduled(fixedRate = 3600000) // 1 hour
    public void checkPremiumExpiry() {
        long expired = accounts.stream()
                .filter(a -> !a.isPremiumActive())
                .count();
        
        if (expired > 0) {
            log.warn("Expired premium accounts: {}", expired);
        }
    }

    /**
     * Load accounts from database or create mock for development.
     */
    private void loadAccounts() {
        // TODO: Load from Supabase in production
        // For development: create mock accounts
        
        for (int i = 0; i < farmSize; i++) {
            GeoTarget region = switch (i % 3) {
                case 0 -> GeoTarget.USA;
                case 1 -> GeoTarget.EU;
                default -> GeoTarget.WORLDWIDE;
            };
            
            accounts.add(new PremiumAccount(
                    UUID.randomUUID(),
                    "bot" + i + "@spotify.mock",
                    "mock_password",
                    "mock_cookies_" + i,
                    LocalDate.now().plusMonths(6), // 6 months premium
                    region
            ));
        }
    }
}
