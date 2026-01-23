package com.goodfellaz.proxy.service;

import com.goodfellaz.proxy.model.ProxyNode;
import com.goodfellaz.proxy.model.ProxyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Test Cluster 1: ProxySelector
 * 
 * 7 tests covering:
 * - Test 1.1: Happy path – pick least loaded healthy
 * - Test 1.2: Prefer healthy over degraded
 * - Test 1.3: Fall back to degraded when no healthy
 * - Test 1.4: No healthy proxies → throws exception
 * - Test 1.5: Empty proxy list → throws exception
 * - Test 1.6: Tie-break by stable sort (same load)
 * - Test 1.7: Region filter before selection
 */
@DisplayName("Cluster 1: ProxySelector Tests")
public class ProxySelectorTest {

    // ========== TEST 1.1: Happy Path – Pick Least Loaded Healthy ==========
    @Test
    @DisplayName("Test 1.1: Happy Path – Pick Least Loaded Healthy Proxy")
    void test_happy_path_pick_least_loaded_healthy() {
        ProxyNode node1 = new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.HEALTHY, 45);
        ProxyNode node2 = new ProxyNode("192.168.1.2", 8080, "DE", ProxyStatus.HEALTHY, 10);
        ProxyNode node3 = new ProxyNode("192.168.1.3", 8080, "DE", ProxyStatus.HEALTHY, 80);

        List<ProxyNode> candidates = Arrays.asList(node1, node2, node3);
        ProxySelector selector = new LeastLoadedHealthySelector();

        ProxyNode selected = selector.select(candidates);

        assertEquals("192.168.1.2", selected.getIp());
        assertEquals(10, selected.getCurrentLoad());
        assertEquals(ProxyStatus.HEALTHY, selected.getStatus());
    }

    // ========== TEST 1.2: Prefer Healthy Over Degraded ==========
    @Test
    @DisplayName("Test 1.2: Prefer Healthy Over Degraded")
    void test_prefer_healthy_over_degraded() {
        ProxyNode healthy = new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.HEALTHY, 5);
        ProxyNode degraded = new ProxyNode("192.168.1.2", 8080, "DE", ProxyStatus.DEGRADED, 1);
        List<ProxyNode> candidates = Arrays.asList(healthy, degraded);

        ProxySelector selector = new LeastLoadedHealthySelector();
        ProxyNode selected = selector.select(candidates);

        assertEquals("192.168.1.1", selected.getIp());
        assertEquals(ProxyStatus.HEALTHY, selected.getStatus());
    }

    // ========== TEST 1.3: Fall Back to Degraded When No Healthy ==========
    @Test
    @DisplayName("Test 1.3: Fall Back to Degraded When No Healthy Proxies")
    void test_fall_back_to_degraded_only() {
        ProxyNode degraded1 = new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.DEGRADED, 60);
        ProxyNode degraded2 = new ProxyNode("192.168.1.2", 8080, "DE", ProxyStatus.DEGRADED, 20);
        List<ProxyNode> candidates = Arrays.asList(degraded1, degraded2);

        ProxySelector selector = new LeastLoadedHealthySelector();
        ProxyNode selected = selector.select(candidates);

        assertEquals("192.168.1.2", selected.getIp());
        assertEquals(ProxyStatus.DEGRADED, selected.getStatus());
    }

    // ========== TEST 1.4: No Healthy Proxies – Throws Exception ==========
    @Test
    @DisplayName("Test 1.4: No Healthy Proxies – Throws Exception")
    void test_no_healthy_proxies_throws_exception() {
        ProxyNode offline1 = new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.OFFLINE, 0);
        ProxyNode offline2 = new ProxyNode("192.168.1.2", 8080, "DE", ProxyStatus.OFFLINE, 0);
        List<ProxyNode> candidates = Arrays.asList(offline1, offline2);

        ProxySelector selector = new LeastLoadedHealthySelector();

        assertThrows(NoAvailableProxyException.class, () -> selector.select(candidates));
    }

    // ========== TEST 1.5: Empty Proxy List – Throws Exception ==========
    @Test
    @DisplayName("Test 1.5: Empty Proxy List – Throws Exception")
    void test_empty_proxy_list_throws_exception() {
        List<ProxyNode> candidates = Collections.emptyList();
        ProxySelector selector = new LeastLoadedHealthySelector();

        assertThrows(NoAvailableProxyException.class, () -> selector.select(candidates));
    }

    // ========== TEST 1.6: Tie-Break by Stable Sort (Same Load) ==========
    @Test
    @DisplayName("Test 1.6: Tie-Break by Stable Sort (Same Load)")
    void test_tie_break_by_stable_sort() {
        ProxyNode nodeA = new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.HEALTHY, 50);
        ProxyNode nodeB = new ProxyNode("192.168.1.2", 8080, "DE", ProxyStatus.HEALTHY, 50);
        ProxyNode nodeC = new ProxyNode("192.168.1.3", 8080, "DE", ProxyStatus.HEALTHY, 50);
        List<ProxyNode> candidates = Arrays.asList(nodeA, nodeB, nodeC);

        ProxySelector selector = new LeastLoadedHealthySelector();

        ProxyNode selected1 = selector.select(candidates);
        ProxyNode selected2 = selector.select(candidates);
        ProxyNode selected3 = selector.select(candidates);

        assertEquals("192.168.1.1", selected1.getIp());
        assertEquals("192.168.1.1", selected2.getIp());
        assertEquals("192.168.1.1", selected3.getIp());
    }

    // ========== TEST 1.7: Region Filter Before Selection ==========
    @Test
    @DisplayName("Test 1.7: Region Filter Before Selection")
    void test_region_filter_before_selection() {
        ProxyNode deProxy = new ProxyNode("192.168.1.1", 8080, "DE", ProxyStatus.HEALTHY, 5);
        ProxyNode usProxy = new ProxyNode("192.168.1.2", 8080, "US", ProxyStatus.HEALTHY, 1);
        ProxyNode gbProxy = new ProxyNode("192.168.1.3", 8080, "GB", ProxyStatus.HEALTHY, 2);
        List<ProxyNode> allCandidates = Arrays.asList(deProxy, usProxy, gbProxy);

        List<ProxyNode> deCandidates = allCandidates.stream()
                .filter(p -> p.getRegion().equals("DE"))
                .toList();

        ProxySelector selector = new LeastLoadedHealthySelector();
        ProxyNode selected = selector.select(deCandidates);

        assertEquals("192.168.1.1", selected.getIp());
        assertEquals("DE", selected.getRegion());
    }
}
