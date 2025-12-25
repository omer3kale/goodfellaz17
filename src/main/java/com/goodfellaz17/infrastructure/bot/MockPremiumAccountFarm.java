package com.goodfellaz17.infrastructure.bot;

import com.goodfellaz17.domain.model.GeoTarget;
import com.goodfellaz17.domain.model.PremiumAccount;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * DEV ONLY: Mock Premium Account Farm for testing.
 * 
 * Only active when spring.profiles.active=dev
 */
@Component
@Profile("dev")
public class MockPremiumAccountFarm {

    private static final Logger log = LoggerFactory.getLogger(MockPremiumAccountFarm.class);

    @Value("${bot.accounts.farm-size:10}")
    private int farmSize;

    private final List<PremiumAccount> accounts = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void initialize() {
        log.info("🧪 DEV MODE: Creating mock account farm");
        
        for (int i = 0; i < farmSize; i++) {
            GeoTarget region = switch (i % 3) {
                case 0 -> GeoTarget.USA;
                case 1 -> GeoTarget.EU;
                default -> GeoTarget.WORLDWIDE;
            };
            
            accounts.add(new PremiumAccount(
                    UUID.randomUUID(),
                    "dev-bot" + i + "@mock.test",
                    null, // No password
                    "dev_refresh_token_" + i,
                    LocalDate.now().plusMonths(6),
                    region
            ));
        }
        
        log.info("🧪 DEV: Mock farm created with {} accounts", accounts.size());
    }

    public Optional<PremiumAccount> nextHealthyAccount(GeoTarget geo) {
        return accounts.stream()
                .filter(PremiumAccount::canPlay)
                .filter(a -> a.getRegion() == geo || geo == GeoTarget.WORLDWIDE)
                .findFirst();
    }

    public Optional<PremiumAccount> nextHealthyAccount() {
        return accounts.stream()
                .filter(PremiumAccount::canPlay)
                .findFirst();
    }

    public int healthyCount() {
        return (int) accounts.stream().filter(PremiumAccount::canPlay).count();
    }

    public int remainingDailyCapacity() {
        return accounts.stream()
                .filter(PremiumAccount::isPremiumActive)
                .mapToInt(a -> 1000 - a.getPlaysToday())
                .sum();
    }
    
    public List<PremiumAccount> getAllAccounts() {
        return List.copyOf(accounts);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void dailyReset() {
        log.info("DEV: Daily account reset");
    }
}
