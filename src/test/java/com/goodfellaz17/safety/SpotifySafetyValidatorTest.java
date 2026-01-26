package com.goodfellaz17.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SpotifySafetyValidator
 * Tests the 12-factor safety guardrails in isolation
 */
class SpotifySafetyValidatorTest {

    private SpotifySafetyValidator validator;
    private SpotifySafetyPolicy policy;

    @BeforeEach
    void setup() {
        // Create a fresh validator and policy for each test
        policy = new SpotifySafetyPolicy();
        validator = new SpotifySafetyValidator();

        // Inject policy into validator using reflection
        try {
            var field = SpotifySafetyValidator.class.getDeclaredField("policy");
            field.setAccessible(true);
            field.set(validator, policy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Configure policy for tests
        policy.setMaxStreamsPerTrackPerDay(500);  // High limit for testing
        policy.setMaxStreamsPerTrackPerHour(500); // High limit for testing
        policy.setMinPlayIntervalSeconds(1);  // 1 second for testing
        policy.setBlockThirdPartyServices(true);
        policy.setDevModeBypassAllChecks(false);
        policy.setEnforceRandomJitter(false); // Disable for unit tests
    }

    @Test
    void testFactorViolation_DailyVolumeLimit() {
        String trackId = "test-track-daily-" + System.nanoTime();
        String accountId = "account-1-" + System.nanoTime();
        String ipAddress = "192.168.1.1";
        String country = "DE";

        // First 10 plays should pass (below hourly limit of 500)
        for (int i = 0; i < 10; i++) {
            SpotifySafetyValidator.PlayAttemptRequest request =
                new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);

            SpotifySafetyValidator.ValidationResult result = validator.validatePlayAttempt(request);
            assertTrue(result.approved, "Play " + i + " should be approved");
            validator.recordPlay(request);
        }

        // 11th play should fail (exceeds daily limit of 50)
        // Override daily limit to 10 for this test
        policy.setMaxStreamsPerTrackPerDay(10);
        SpotifySafetyValidator.PlayAttemptRequest request =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);
        SpotifySafetyValidator.ValidationResult result = validator.validatePlayAttempt(request);
        assertFalse(result.approved, "Play 11 should be rejected");
        assertTrue(result.violations.stream().anyMatch(v -> v.contains("daily limit")));
    }

    @Test
    void testTemporalJitterEnabled() {
        String trackId = "test-track-jitter-" + System.nanoTime();
        String accountId = "account-jitter-" + System.nanoTime();
        String ipAddress = "192.168.1.2";
        String country = "DE";

        // Enable jitter for this test
        policy.setEnforceRandomJitter(true);
        policy.setMinPlayIntervalSeconds(2);

        SpotifySafetyValidator.PlayAttemptRequest request1 =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);

        SpotifySafetyValidator.ValidationResult result1 = validator.validatePlayAttempt(request1);
        assertTrue(result1.approved, "First play should be approved");
        validator.recordPlay(request1);

        // Immediate second play should fail due to jitter
        SpotifySafetyValidator.PlayAttemptRequest request2 =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);

        SpotifySafetyValidator.ValidationResult result2 = validator.validatePlayAttempt(request2);
        assertFalse(result2.approved, "Second play too soon should fail");
        assertTrue(result2.violations.stream().anyMatch(v -> v.contains("Play interval too short")));
    }

    @Test
    void testThirdPartyBlocked() {
        String trackId = "test-track-third-party-" + System.nanoTime();
        String accountId = "account-3-" + System.nanoTime();
        String ipAddress = "192.168.1.3";
        String country = "DE";

        SpotifySafetyValidator.PlayAttemptRequest request =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);
        request.source = "spotify-boost-service";

        SpotifySafetyValidator.ValidationResult result = validator.validatePlayAttempt(request);
        assertFalse(result.approved, "Third-party service should be blocked");
        assertTrue(result.violations.stream().anyMatch(v -> v.contains("Third-party")));
    }

    @Test
    void testDurationValidation() {
        String trackId = "test-track-duration-" + System.nanoTime();
        String accountId = "account-4-" + System.nanoTime();
        String ipAddress = "192.168.1.4";
        String country = "DE";

        // Too short duration (< 15s)
        SpotifySafetyValidator.PlayAttemptRequest shortRequest =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 5);

        SpotifySafetyValidator.ValidationResult shortResult = validator.validatePlayAttempt(shortRequest);
        assertFalse(shortResult.approved, "Too short duration should be rejected");

        // Valid duration (15s - 300s)
        SpotifySafetyValidator.PlayAttemptRequest validRequest =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);

        SpotifySafetyValidator.ValidationResult validResult = validator.validatePlayAttempt(validRequest);
        assertTrue(validResult.approved, "Valid duration should be approved");
    }

    @Test
    void testListenerDiversity() {
        String trackId = "test-track-diversity-" + System.nanoTime();
        String ipAddress = "192.168.1.5";
        String country = "DE";

        // Same account plays same track 20 times (max allowed)
        String accountId = "account-diverse-1-" + System.nanoTime();
        for (int i = 0; i < 20; i++) {
            SpotifySafetyValidator.PlayAttemptRequest request =
                new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);

            SpotifySafetyValidator.ValidationResult result = validator.validatePlayAttempt(request);
            assertTrue(result.approved, "Play " + i + " should be approved (within limit)");
            validator.recordPlay(request);
        }

        // 21st play from same account should fail
        SpotifySafetyValidator.PlayAttemptRequest request =
            new SpotifySafetyValidator.PlayAttemptRequest(trackId, accountId, ipAddress, country, 180);

        SpotifySafetyValidator.ValidationResult result = validator.validatePlayAttempt(request);
        assertFalse(result.approved, "21st play from same account should fail");
        assertTrue(result.violations.stream().anyMatch(v -> v.contains("plays-per-track limit")));
    }
}
