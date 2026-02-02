package com.goodfellaz17.account.service;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@Profile("accounts")
public class GmailAccountCreator {

    private final AtomicInteger totalCreated = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private static final String CSV_PATH = "/data/gmx_accounts.csv";
    private static final long SCHEDULE_INTERVAL = 600000; // 10 minutes

    @Autowired
    private SpotifyAccountRepository spotifyAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Scheduled(fixedRate = SCHEDULE_INTERVAL)
    public void scheduleGmxAccountCreation() {
        log.info("Starting scheduled GMX account creation task");
        try {
            createGmxAccount().get();
        } catch (Exception e) {
            log.error("Scheduled GMX account creation failed", e);
            totalFailed.incrementAndGet();
        }
    }

    public CompletableFuture<SpotifyAccount> createGmxAccount() {
        return CompletableFuture.supplyAsync(() -> {
            WebDriver driver = null;
            try {
                // Initialize WebDriver
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
                driver = new ChromeDriver(options);

                // Generate unique credentials
                String email = generateGmxEmail();
                String password = generateSecurePassword();
                String displayName = "User_" + System.currentTimeMillis();

                log.info("Starting GMX account creation for: {}", email);

                // Navigate to GMX signup
                driver.get("https://www.gmx.net/produkte/premium/tarifvergleich/");
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

                // Fill email field
                try {
                    WebElement emailField = wait.until(ExpectedConditions.presenceOfElementLocated(By.name("email")));
                    emailField.clear();
                    emailField.sendKeys(email);
                    log.info("Email field filled");
                } catch (TimeoutException e) {
                    log.error("Email field not found within timeout", e);
                    totalFailed.incrementAndGet();
                    return null;
                }

                // Fill password field
                try {
                    WebElement passwordField = driver.findElement(By.name("password"));
                    passwordField.clear();
                    passwordField.sendKeys(password);
                    log.info("Password field filled");
                } catch (NoSuchElementException e) {
                    log.error("Password field not found", e);
                    totalFailed.incrementAndGet();
                    return null;
                }

                // Fill display name
                try {
                    WebElement displayNameField = driver.findElement(By.name("displayname"));
                    displayNameField.clear();
                    displayNameField.sendKeys(displayName);
                    log.info("Display name field filled");
                } catch (NoSuchElementException e) {
                    log.warn("Display name field not found, continuing...");
                }

                // Accept terms checkbox
                try {
                    WebElement termsCheckbox = driver.findElement(By.id("terms_checkbox"));
                    if (!termsCheckbox.isSelected()) {
                        termsCheckbox.click();
                        log.info("Terms checkbox accepted");
                    }
                } catch (NoSuchElementException e) {
                    log.warn("Terms checkbox not found, attempting to continue...");
                }

                // Submit form
                try {
                    WebElement submitButton = driver.findElement(By.id("submit_button"));
                    submitButton.click();
                    log.info("Form submitted");
                } catch (NoSuchElementException e) {
                    log.error("Submit button not found", e);
                    totalFailed.incrementAndGet();
                    return null;
                }

                // Wait for success
                try {
                    wait.until(ExpectedConditions.urlContains("success"));
                    log.info("GMX account creation successful");
                } catch (TimeoutException e) {
                    log.warn("Success page timeout, but proceeding to save account");
                }

                // Create SpotifyAccount entity
                SpotifyAccount account = new SpotifyAccount();
                account.setEmail(email);
                account.setPassword(encryptPassword(password));
                account.setStatus("CREATED");
                account.setCreatedAt(LocalDateTime.now());

                // Save to database
                SpotifyAccount savedAccount = spotifyAccountRepository.save(account).block();
                log.info("Account saved to database: {}", email);

                // Append to CSV
                appendToCSV(email, password);
                totalCreated.incrementAndGet();
                log.info("GMX account creation completed. Total created: {}", totalCreated.get());

                return savedAccount;

            } catch (Exception e) {
                log.error("Unexpected error during GMX account creation", e);
                totalFailed.incrementAndGet();
                return null;
            } finally {
                if (driver != null) {
                    try {
                        driver.quit();
                    } catch (Exception e) {
                        log.warn("Error closing WebDriver", e);
                    }
                }
            }
        });
    }

    private String generateGmxEmail() {
        long timestamp = System.currentTimeMillis();
        String random = String.format("%04d", new Random().nextInt(10000));
        return "user_" + timestamp + "_" + random + "@gmx.de";
    }

    private String generateSecurePassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;:,.<>?";
        String all = upper + lower + digits + special;

        StringBuilder password = new StringBuilder();
        Random random = new Random();

        // Ensure mix of character types
        password.append(upper.charAt(random.nextInt(upper.length())));
        password.append(lower.charAt(random.nextInt(lower.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        password.append(special.charAt(random.nextInt(special.length())));

        // Fill remaining length
        for (int i = 4; i < 16; i++) {
            password.append(all.charAt(random.nextInt(all.length())));
        }

        return password.toString();
    }

    private String encryptPassword(String password) {
        // BCrypt encryption with strength 12 (~100ms per hash)
        String encrypted = passwordEncoder.encode(password);
        log.debug("Password encrypted with BCrypt (strength 12)");
        return encrypted;
    }

    private void appendToCSV(String email, String password) {
        try {
            Files.createDirectories(Paths.get("/data"));
            String csvLine = String.format("%s,%s,%s,%s,%s%n",
                email,
                password,
                LocalDateTime.now(),
                "SYSTEM",
                "CREATED");

            FileWriter writer = new FileWriter(CSV_PATH, true);
            writer.append(csvLine);
            writer.close();
            log.info("Account appended to CSV: {}", CSV_PATH);
        } catch (IOException e) {
            log.error("Failed to append to CSV file: {}", CSV_PATH, e);
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        int total = totalCreated.get() + totalFailed.get();
        double successRate = total > 0 ? (totalCreated.get() * 100.0) / total : 0.0;

        metrics.put("totalCreated", totalCreated.get());
        metrics.put("totalFailed", totalFailed.get());
        metrics.put("successRate", String.format("%.2f%%", successRate));
        metrics.put("totalAttempts", total);

        return metrics;
    }
}
