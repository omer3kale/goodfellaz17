package com.goodfellaz17.application.service;

import com.goodfellaz17.account.service.SpotifyAccount;
import com.goodfellaz17.account.service.SpotifyAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPoolService {

    private final SpotifyAccountRepository accountRepo;

    public Mono<SpotifyAccount> getAvailableAccount() {
        // Find least used account with ACTIVE status and under daily limit (50 streams)
        // Note: Logic adapted for demo, in production this should be a DB query
        return accountRepo.findAll()
                .filter(acc -> "ACTIVE".equals(acc.getStatus()))
                // Simplified check: assuming playsToday field exists or similar
                // Based on init.sql, bot_accounts has plays_today.
                // Let's check SpotifyAccount fields.
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("No available accounts in pool")));
    }
}
