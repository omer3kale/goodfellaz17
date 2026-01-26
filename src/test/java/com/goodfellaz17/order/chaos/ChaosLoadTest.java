package com.goodfellaz17.order.chaos;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chaos Load Test: Validates system stability under concurrent load.
 *
 * Run with: java -cp target/test-classes com.goodfellaz17.order.chaos.ChaosLoadTest
 * Prerequisites: App must be running on http://localhost:8080
 */
public class ChaosLoadTest {
    private static final int CONCURRENT_ORDERS = 500;
    private static final String BASE_URL = "http://localhost:8080";
    private static final int THREAD_POOL_SIZE = 50;

    public static void main(String[] args) throws Exception {
        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║        CHAOS LOAD TEST: 500 CONCURRENT ORDERS        ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        var executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        var futures = new ArrayList<CompletableFuture<Result>>();
        var startTime = System.currentTimeMillis();
        var requestCounter = new AtomicInteger(0);

        System.out.println("Spawning " + CONCURRENT_ORDERS + " concurrent POST /api/orders/create requests...\n");

        for (int i = 0; i < CONCURRENT_ORDERS; i++) {
            int idx = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                int num = requestCounter.incrementAndGet();
                if (num % 50 == 0) {
                    System.out.println("  Progress: " + num + "/" + CONCURRENT_ORDERS + " requests sent");
                }
                return createOrder(idx);
            }, executor));
        }

        System.out.println("\nWaiting for all responses...\n");
        var results = futures.stream().map(CompletableFuture::join).toList();
        var duration = (System.currentTimeMillis() - startTime) / 1000.0;

        var successful = results.stream().filter(r -> r.statusCode == 201).count();
        var failed = results.stream().filter(r -> r.statusCode >= 400).count();
        var avgLatency = results.stream().mapToLong(r -> r.latencyMs).average().orElse(0);
        var maxLatency = results.stream().mapToLong(r -> r.latencyMs).max().orElse(0);
        var minLatency = results.stream().mapToLong(r -> r.latencyMs).min().orElse(0);
        var p99 = results.stream().sorted((a,b) -> Long.compare(b.latencyMs, a.latencyMs))
            .limit(Math.max(1, results.size() / 100))
            .mapToLong(r -> r.latencyMs).max().orElse(0);

        var successRate = (double) successful / CONCURRENT_ORDERS * 100;
        var throughput = CONCURRENT_ORDERS / duration;

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║              CHAOS TEST RESULTS                      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");
        System.out.printf("  Total Requests       : %d%n", CONCURRENT_ORDERS);
        System.out.printf("  Successful (201)     : %d (%.1f%%)%n", successful, successRate);
        System.out.printf("  Failed (4xx/5xx)     : %d%n", failed);
        System.out.printf("  Unknown              : %d%n", CONCURRENT_ORDERS - successful - failed);
        System.out.println();
        System.out.printf("  Duration             : %.2f seconds%n", duration);
        System.out.printf("  Throughput           : %.0f orders/sec%n", throughput);
        System.out.println();
        System.out.printf("  Latency (avg)        : %d ms%n", (long)avgLatency);
        System.out.printf("  Latency (min)        : %d ms%n", minLatency);
        System.out.printf("  Latency (max)        : %d ms%n", maxLatency);
        System.out.printf("  Latency (P99)        : %d ms%n", p99);
        System.out.println();

        if (successRate >= 95) {
            System.out.println("  ✅ STATUS: PASS (≥95% success rate)\n");
        } else if (successRate >= 80) {
            System.out.println("  ⚠️  STATUS: WARN (" + String.format("%.1f", successRate) + "% success)\n");
        } else {
            System.out.println("  ❌ STATUS: FAIL (<80% success rate)\n");
        }

        executor.shutdown();
    }

    static Result createOrder(int id) {
        var start = System.currentTimeMillis();
        try {
            var json = String.format(
                "{\"trackId\":\"spotify:track:chaos-%s\",\"quantity\":2,\"accountIds\":[\"chaos-a%d\",\"chaos-b%d\"]}",
                UUID.randomUUID(), id, id);

            var pb = new ProcessBuilder("curl", "-s", "-w", "%{http_code}",
                "-X", "POST", BASE_URL + "/api/orders/create",
                "-H", "Content-Type: application/json",
                "-d", json);
            pb.redirectErrorStream(true);
            var proc = pb.start();
            var exitCode = proc.waitFor();

            if (exitCode != 0) {
                return new Result(500, System.currentTimeMillis() - start);
            }

            var output = new String(proc.getInputStream().readAllBytes());
            var code = 500;
            if (output.length() >= 3) {
                try {
                    code = Integer.parseInt(output.substring(Math.max(0, output.length()-3)));
                } catch (NumberFormatException e) {
                    code = 500;
                }
            }
            return new Result(code, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new Result(500, System.currentTimeMillis() - start);
        }
    }

    static class Result {
        int statusCode;
        long latencyMs;
        Result(int statusCode, long latencyMs) {
            this.statusCode = statusCode;
            this.latencyMs = latencyMs;
        }
    }
}
