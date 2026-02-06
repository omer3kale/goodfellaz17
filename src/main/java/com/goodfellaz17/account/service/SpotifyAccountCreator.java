package com.goodfellaz17.account.service;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import io.github.bonigarcia.wdm.WebDriverManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
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

@Service
@Profile("accounts")
public class SpotifyAccountCreator {
    private static final Logger log = LoggerFactory.getLogger(SpotifyAccountCreator.class);

    private final AtomicInteger totalSignedUp = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private static final String CSV_INPUT_PATH = "/data/gmx_accounts.csv";
    private static final String CSV_PROCESSED_PATH = "/data/gmx_accounts_processed.csv";
    private static final long SCHEDULE_INTERVAL = 900000; // 15 minutes

    @Autowired
    private SpotifyAccountRepository spotifyAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Scheduled(fixedRate = SCHEDULE_INTERVAL)
    public void scheduleSpotifyAccountCreation() {
        log.info("Starting scheduled Spotify account creation task");
        try {
            processNextPendingGmxAccount().get();
        } catch (Exception e) {
            log.error("Scheduled Spotify account creation failed", e);
            totalFailed.incrementAndGet();
        }
    }

    public CompletableFuture<SpotifyAccount> processNextPendingGmxAccount() {
        return CompletableFuture.supplyAsync(() -> {
            WebDriver driver = null;
            try {
                // Read next unprocessed email from CSV
                String[] accountData = readNextUnprocessedAccount();
                if (accountData == null) {
                    log.warn("No unprocessed GMX accounts available");
                    return null;
                }

                String email = accountData[0];
                String password = accountData[1];

                log.info("Processing Spotify account creation for: {}", email);

                // Initialize WebDriver
                WebDriverManager.chromedriver().setup();
                ChromeOptions options = new ChromeOptions();
                options.addArguments("--headless");
                options.addArguments("--no-sandbox");
                options.addArguments("--disable-dev-shm-usage");
                options.addArguments("--disable-blink-features=AutomationControlled");
                options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
                driver = new ChromeDriver(options);

                driver.get("https://www.spotify.com/de/signup");
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

                // Fill email
                try {
                    WebElement emailField = wait.until(ExpectedConditions.presenceOfElementLocated(By.name("email")));
                    emailField.clear();
                    emailField.sendKeys(email);
                    log.info("Spotify email field filled");
                } catch (TimeoutException e) {
                    log.error("Spotify email field not found", e);
                    totalFailed.incrementAndGet();
                    return null;
                }

                // Fill password
                try {
                    WebElement passwordField = driver.findElement(By.name("password"));
                    passwordField.clear();
                    passwordField.sendKeys(password);
                    log.info("Spotify password field filled");
                } catch (NoSuchElementException e) {
                    log.error("Spotify password field not found", e);
                    totalFailed.incrementAndGet();
                    return null;
                }

                // Fill display name
                String displayName = "User_" + System.currentTimeMillis();
                try {
                    WebElement displayNameField = driver.findElement(By.name("displayname"));
                    displayNameField.clear();
                    displayNameField.sendKeys(displayName);
                    log.info("Spotify display name filled");
                } catch (NoSuchElementException e) {
                    log.warn("Display name field not found, continuing...");
                }

                // Fill birth date (random)
                int day = new Random().nextInt(28) + 1;
                int month = new Random().nextInt(12) + 1;
                int year = new Random().nextInt(26) + 1980; // 1980-2006

                try {
                    WebElement dayField = driver.findElement(By.name("day"));
                    dayField.clear();
                    dayField.sendKeys(String.format("%02d", day));

                    WebElement monthField = driver.findElement(By.name("month"));
                    monthField.clear();
                    monthField.sendKeys(String.format("%02d", month));

                    WebElement yearField = driver.findElement(By.name("year"));
                    yearField.clear();
                    yearField.sendKeys(String.valueOf(year));

                    log.info("Birth date filled: {}/{}/{}", day, month, year);
                } catch (NoSuchElementException e) {
                    log.warn("Birth date fields not found, continuing...");
                }

                // Fill gender
                String gender = new Random().nextBoolean() ? "male" : "female";
                try {
                    WebElement genderField = driver.findElement(By.name("gender"));
                    genderField.sendKeys(gender);
                    log.info("Gender filled: {}", gender);
                } catch (NoSuchElementException e) {
                    log.warn("Gender field not found, continuing...");
                }

                // Accept terms
                try {
                    WebElement termsCheckbox = driver.findElement(By.id("terms"));
                    if (!termsCheckbox.isSelected()) {
                        termsCheckbox.click();
                        log.info("Spotify terms accepted");
                    }
                } catch (NoSuchElementException e) {
                    log.warn("Terms checkbox not found");
                }

                // Submit form
                try {
                    WebElement submitButton = driver.findElement(By.id("submit"));
                    submitButton.click();
                    log.info("Spotify signup form submitted");
                } catch (NoSuchElementException e) {
                    log.error("Spotify submit button not found", e);
                    totalFailed.incrementAndGet();
                    return null;
                }

                // Wait for redirect to open.spotify.com
                try {
                    wait.until(ExpectedConditions.urlContains("open.spotify.com"));
                    log.info("Spotify signup redirect successful");
                } catch (TimeoutException e) {
                    log.warn("Spotify redirect timeout, attempting to continue...");
                }

                // Extract Spotify User ID from URL or localStorage
                String spotifyUserId = extractSpotifyUserId(driver);
                if (spotifyUserId == null) {
                    spotifyUserId = generateSpotifyUserId(email);
                }

                // Create and save SpotifyAccount
                SpotifyAccount account = new SpotifyAccount();
                account.setEmail(email);
                account.setPassword(password);
                account.setSpotifyUserId(spotifyUserId);
                account.setStatus("PENDING_EMAIL_VERIFICATION");
                account.setCreatedAt(LocalDateTime.now());

                SpotifyAccount savedAccount = spotifyAccountRepository.save(account).block();
                log.info("Spotify account saved to database: {} (ID: {})", email, spotifyUserId);

                // Mark as processed
                markAsProcessed(email, password);
                totalSignedUp.incrementAndGet();
                log.info("Spotify account creation completed. Total signed up: {}", totalSignedUp.get());

                return savedAccount;

            } catch (Exception e) {
                log.error("Unexpected error during Spotify account creation", e);
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

    private String[] readNextUnprocessedAccount() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(CSV_INPUT_PATH));
            BufferedWriter processedWriter = new BufferedWriter(new FileWriter(CSV_PROCESSED_PATH, true));

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 2) {
                    String email = parts[0];
                    String password = parts[1];

                    // Check if already processed
                    if (!isAlreadyProcessed(email)) {
                        reader.close();
                        return new String[]{email, password};
                    }
                }
            }
            reader.close();
            processedWriter.close();
            return null;
        } catch (IOException e) {
            log.error("Error reading unprocessed accounts from CSV", e);
            return null;
        }
    }

    private boolean isAlreadyProcessed(String email) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(CSV_PROCESSED_PATH));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(email)) {
                    reader.close();
                    return true;
                }
            }
            reader.close();
        } catch (IOException e) {
            log.debug("Processed CSV file not found or error reading it");
        }
        return false;
    }

    private String extractSpotifyUserId(WebDriver driver) {
        try {
            String currentUrl = driver.getCurrentUrl();
            if (currentUrl.contains("open.spotify.com")) {
                String[] parts = currentUrl.split("/");
                if (parts.length > 0) {
                    return parts[parts.length - 1];
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract Spotify user ID from URL", e);
        }
        return null;
    }

    private String generateSpotifyUserId(String email) {
        return "spotify_" + email.replaceAll("[@.]", "_") + "_" + System.currentTimeMillis();
    }

    private void markAsProcessed(String email, String password) {
        try {
            Files.createDirectories(Paths.get("/data"));
            String csvLine = String.format("%s,%s,%s%n", email, password, LocalDateTime.now());

            FileWriter writer = new FileWriter(CSV_PROCESSED_PATH, true);
            writer.append(csvLine);
            writer.close();
            log.info("Account marked as processed: {}", email);
        } catch (IOException e) {
            log.error("Failed to mark account as processed", e);
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        int total = totalSignedUp.get() + totalFailed.get();
        double successRate = total > 0 ? (totalSignedUp.get() * 100.0) / total : 0.0;

        metrics.put("totalSignedUp", totalSignedUp.get());
        metrics.put("totalFailed", totalFailed.get());
        metrics.put("successRate", String.format("%.2f%%", successRate));
        metrics.put("totalAttempts", total);

        return metrics;
    }
}
