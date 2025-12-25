package com.goodfellaz17.infrastructure.bot;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.PremiumAccount;
import com.goodfellaz17.infrastructure.persistence.PremiumAccountRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Infrastructure Component - Premium Account Farm.
 * 
 * PRODUCTION: Loads REAL accounts from Neon PostgreSQL.
 * NO MOCKS - Real farm accounts with OAuth refresh tokens.
 * 
 * Manages 1000+ aged Spotify Premium accounts:
 * - OAuth-based login (refresh tokens)
 * - Daily play limits (1000/account)
 * - Premium expiry tracking
 * - Geo-matched account selection
 */
@Component
@Profile("!dev")  // PRODUCTION ONLY - No dev profile
public class PremiumAccountFarm {

    private static final Logger log = LoggerFactory.getLogger(PremiumAccountFarm.class);

    private final PremiumAccountRepository accountRepository;
    
    @Value("${bot.accounts.farm-size:100}")
    private int farmSize;

    private final List<PremiumAccount> accounts = new CopyOnWriteArrayList<>();

    public PremiumAccountFarm(PremiumAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @PostConstruct
    public void initialize() {
        log.info("🚀 Production mode: Loading REAL accounts from Neon DB");
        loadAccounts();
        
        if (accounts.isEmpty()) {
            log.error("🚨 NO PREMIUM ACCOUNTS IN DATABASE - Add to Neon!");
            log.error("Run this SQL in Neon console:");
            log.error("INSERT INTO premium_accounts (email, spotify_refresh_token, region, premium_expiry) VALUES ('farm1@spotify.com', 'YOUR_OAUTH_TOKEN', 'USA', '2026-06-25');");
            // Don't throw - allow startup but log critical warning
        }
        
        log.info("✅ Account farm initialized: size={}, healthy={}", 
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

    @Scheduled(fixedRate = 3600000) // 1 hour - reload from DB
    public void reloadFromDatabase() {
        log.info("Reloading accounts from database...");
        loadAccounts();
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
     * REAL: Load accounts from Neon PostgreSQL.
     */
    private void loadAccounts() {
        accounts.clear();
        
        List<PremiumAccount> realAccounts = accountRepository
                .findAllActive(LocalDate.now())
                .collectList()
                .block();
        
        if (realAccounts != null && !realAccounts.isEmpty()) {
            accounts.addAll(realAccounts);
            log.info("✅ Loaded {} REAL premium accounts from Neon DB", accounts.size());
        } else {
            log.warn("⚠️ No active premium accounts found in database");
        }
    }
    
    /**
     * Get all accounts (for token warming).
     */
    public List<PremiumAccount> getAllAccounts() {
        return List.copyOf(accounts);
    }
}
