package com.goodfellaz.proxy.service;

import com.goodfellaz.proxy.model.ProxyHealthConfig;
import com.goodfellaz.proxy.model.ProxyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Cluster 2: ProxyHealthRules
 * 
 * 7 tests covering:
 * - Test 2.1: HEALTHY + good metrics → stays HEALTHY
 * - Test 2.2: HEALTHY + bad success rate → DEGRADED
 * - Test 2.3: DEGRADED + recovered metrics → HEALTHY
 * - Test 2.4: OFFLINE never auto-recovers
 * - Test 2.5: Overloaded connections → DEGRADED
 * - Test 2.6: Threshold boundary (99.9% success rate)
 * - Test 2.7: Multiple degradation signals (both bad)
 */
@DisplayName("Cluster 2: ProxyHealthRules Tests")
public class ProxyHealthRulesTest {

    // Standard test config: 95% success rate threshold, 10 min connections
    private final ProxyHealthConfig config = new ProxyHealthConfig(95.0f, 10);

    // ========== TEST 2.1: HEALTHY + Good Metrics → Stay HEALTHY ==========
    @Test
    @DisplayName("Test 2.1: HEALTHY + Good Metrics → Stay HEALTHY")
    void test_healthy_with_good_metrics_stays_healthy() {
        ProxyStatus currentStatus = ProxyStatus.HEALTHY;
        float successRate = 98.5f;  // Good: >= 95%
        int availableConnections = 25; // Good: >= 10

        ProxyHealthRules rules = new DefaultProxyHealthRules();
        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.HEALTHY, nextStatus);
    }

    // ========== TEST 2.2: HEALTHY + Bad Success Rate → DEGRADED ==========
    @Test
    @DisplayName("Test 2.2: HEALTHY + Bad Success Rate → DEGRADED")
    void test_healthy_bad_success_rate_becomes_degraded() {
        ProxyStatus currentStatus = ProxyStatus.HEALTHY;
        float successRate = 72.0f;  // below threshold
        int availableConnections = 15; // still good

        ProxyHealthConfig config = new ProxyHealthConfig(95.0f, 10);
        ProxyHealthRules rules = new DefaultProxyHealthRules();

        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.DEGRADED, nextStatus);
    }

    // ========== TEST 2.3: DEGRADED + Recovered Metrics → HEALTHY ==========
    @Test
    @DisplayName("Test 2.3: DEGRADED + Recovered Metrics → HEALTHY")
    void test_degraded_with_recovered_metrics_becomes_healthy() {
        ProxyStatus currentStatus = ProxyStatus.DEGRADED;
        float successRate = 97.0f;      // recovered: above 95%
        int availableConnections = 20;  // recovered: above 10

        ProxyHealthRules rules = new DefaultProxyHealthRules();
        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.HEALTHY, nextStatus);
    }

    // ========== TEST 2.4: OFFLINE Never Auto-Recovers ==========
    @Test
    @DisplayName("Test 2.4: OFFLINE Never Auto-Recovers")
    void test_offline_never_auto_recovers() {
        ProxyStatus currentStatus = ProxyStatus.OFFLINE;
        float successRate = 99.5f;    // would be amazing if it were online
        int availableConnections = 50; // also great

        ProxyHealthRules rules = new DefaultProxyHealthRules();
        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.OFFLINE, nextStatus);
    }

    // ========== TEST 2.5: Overloaded Connections → DEGRADED ==========
    @Test
    @DisplayName("Test 2.5: Overloaded Connections → DEGRADED")
    void test_overloaded_connections_triggers_degraded() {
        ProxyStatus currentStatus = ProxyStatus.HEALTHY;
        float successRate = 96.0f;      // good
        int availableConnections = 2;   // below threshold (10)

        ProxyHealthRules rules = new DefaultProxyHealthRules();
        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.DEGRADED, nextStatus);
    }

    // ========== TEST 2.6: Threshold Boundary (99.9% Success Rate) ==========
    @Test
    @DisplayName("Test 2.6: Threshold Boundary (99.9% Success Rate)")
    void test_threshold_boundary_99_9_percent_success() {
        ProxyStatus currentStatus = ProxyStatus.DEGRADED;
        float successRate = 99.9f;     // far above threshold
        int availableConnections = 15; // above threshold

        ProxyHealthRules rules = new DefaultProxyHealthRules();
        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.HEALTHY, nextStatus);
    }

    // ========== TEST 2.7: Multiple Degradation Signals (Both Bad) ==========
    @Test
    @DisplayName("Test 2.7: Multiple Degradation Signals (Both Bad)")
    void test_multiple_degradation_signals() {
        ProxyStatus currentStatus = ProxyStatus.HEALTHY;
        float successRate = 70.0f;      // BAD: below 95%
        int availableConnections = 3;   // BAD: below 10

        ProxyHealthRules rules = new DefaultProxyHealthRules();
        ProxyStatus nextStatus = rules.next(currentStatus, successRate, availableConnections, config);

        assertEquals(ProxyStatus.DEGRADED, nextStatus);
    }
}
