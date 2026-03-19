
package com.faangprep.javabackend.concurrency;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * CompletableFuture Patterns — production-grade async composition
 *
 * Patterns:
 *  1. Sequential chain (thenApply / thenCompose)
 *  2. Fan-out / fan-in (allOf, thenCombine)
 *  3. First-to-succeed (anyOf)
 *  4. Timeout + fallback
 *  5. Retry with backoff
 *  6. Batched parallel processing
 *  7. Error handling (exceptionally, handle, whenComplete)
 */
public class CompletableFuturePatterns {

    private static final ExecutorService IO_POOL = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(2);

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SEQUENTIAL CHAIN
    // ─────────────────────────────────────────────────────────────────────────

    record User(String id, String name, String email) {}
    record EnrichedUser(User user, String tier, double creditScore) {}

    static CompletableFuture<EnrichedUser> fetchAndEnrichUser(String userId) {
        return CompletableFuture
                .supplyAsync(() -> fetchUser(userId), IO_POOL)          // async fetch
                .thenApplyAsync(user -> enrichWithCrm(user), IO_POOL)   // async transform
                .thenApply(enriched -> {                                  // sync transform (cheap)
                    System.out.println("Fetched: " + enriched.user().name());
                    return enriched;
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. FAN-OUT / FAN-IN
    // ─────────────────────────────────────────────────────────────────────────

    record OrderSummary(User user, List<String> orders, double creditScore) {}

    static CompletableFuture<OrderSummary> buildOrderSummary(String userId) {
        // Start all three calls in parallel
        CompletableFuture<User> userFuture =
                CompletableFuture.supplyAsync(() -> fetchUser(userId), IO_POOL);

        CompletableFuture<List<String>> ordersFuture =
                CompletableFuture.supplyAsync(() -> fetchOrders(userId), IO_POOL);

        CompletableFuture<Double> creditFuture =
                CompletableFuture.supplyAsync(() -> fetchCreditScore(userId), IO_POOL);

        // Combine user + credit first
        CompletableFuture<EnrichedUser> enrichedFuture = userFuture.thenCombine(
                creditFuture, (user, score) -> new EnrichedUser(user, "GOLD", score));

        // Then combine with orders
        return enrichedFuture.thenCombine(
                ordersFuture,
                (enriched, orders) -> new OrderSummary(enriched.user(), orders, enriched.creditScore()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. FIRST-TO-SUCCEED (PRIMARY + FALLBACK)
    // ─────────────────────────────────────────────────────────────────────────

    static CompletableFuture<String> fetchWithFallback(String key) {
        CompletableFuture<String> primary = CompletableFuture.supplyAsync(
                () -> fetchFromPrimary(key), IO_POOL);
        CompletableFuture<String> fallback = CompletableFuture.supplyAsync(
                () -> fetchFromFallback(key), IO_POOL);

        // anyOf returns the first to complete
        return CompletableFuture.anyOf(primary, fallback)
                .thenApply(result -> (String) result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. TIMEOUT + FALLBACK
    // ─────────────────────────────────────────────────────────────────────────

    static CompletableFuture<String> fetchWithTimeout(String key, long timeoutMs) {
        CompletableFuture<String> task = CompletableFuture.supplyAsync(
                () -> slowFetch(key), IO_POOL);

        // Java 9+: orTimeout throws TimeoutException; completeOnTimeout provides default
        return task.completeOnTimeout("DEFAULT_VALUE", timeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    System.out.println("Fetch failed/timed out: " + ex.getMessage());
                    return "FALLBACK_VALUE";
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. RETRY WITH EXPONENTIAL BACKOFF
    // ─────────────────────────────────────────────────────────────────────────

    static <T> CompletableFuture<T> retry(Supplier<T> task, int maxAttempts, long baseDelayMs) {
        return attemptWithRetry(task, maxAttempts, baseDelayMs, 1, new CompletableFuture<>());
    }

    private static <T> CompletableFuture<T> attemptWithRetry(
            Supplier<T> task, int maxAttempts, long baseDelayMs, int attempt,
            CompletableFuture<T> promise) {

        CompletableFuture.supplyAsync(task, IO_POOL)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        promise.complete(result);
                    } else if (attempt >= maxAttempts) {
                        promise.completeExceptionally(ex); // exhausted retries
                    } else {
                        long delay = baseDelayMs * (1L << (attempt - 1)); // 2^(attempt-1) * base
                        long jitter = ThreadLocalRandom.current().nextLong(0, delay / 3 + 1);
                        long waitMs = delay + jitter;
                        System.out.printf("Retry %d/%d after %dms%n", attempt, maxAttempts, waitMs);
                        SCHEDULER.schedule(
                                () -> attemptWithRetry(task, maxAttempts, baseDelayMs, attempt + 1, promise),
                                waitMs, TimeUnit.MILLISECONDS);
                    }
                });

        return promise;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. BATCHED PARALLEL PROCESSING
    // ─────────────────────────────────────────────────────────────────────────

    static List<String> processBatch(List<String> items) throws ExecutionException, InterruptedException {
        List<CompletableFuture<String>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> process(item), IO_POOL))
                .toList();

        // Wait for all — if one fails, the returned future also fails
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. ERROR HANDLING PATTERNS
    // ─────────────────────────────────────────────────────────────────────────

    static void errorHandlingDemo() {
        // exceptionally — recover from failure
        CompletableFuture<String> withRecovery = CompletableFuture
                .<String>failedFuture(new RuntimeException("DB timeout"))
                .exceptionally(ex -> {
                    System.out.println("Recovering from: " + ex.getMessage());
                    return "CACHED_FALLBACK";
                });

        // handle — always called, can inspect both result and exception
        CompletableFuture<String> withHandle = CompletableFuture
                .<String>failedFuture(new RuntimeException("network error"))
                .handle((result, ex) -> {
                    if (ex != null) return "handle fallback";
                    return result.toUpperCase();
                });

        // whenComplete — side effects, does NOT change the result
        CompletableFuture<String> withSideEffect = CompletableFuture.supplyAsync(() -> "data")
                .whenComplete((result, ex) -> {
                    if (ex != null) metricsCounter.recordRequest(0, true);
                    else System.out.println("Success: " + result);
                });

        System.out.println("Recovery result: " + withRecovery.join());
        System.out.println("Handle result: " + withHandle.join());
        System.out.println("Side-effect result: " + withSideEffect.join());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIMULATED DEPENDENCIES FOR DEMO PURPOSES
    // These helpers keep the examples runnable without external services.
    // Re-implement them against real clients, storage, or APIs as practice.
    // ─────────────────────────────────────────────────────────────────────────

    private static final MetricsCounter metricsCounter = new MetricsCounter();

    private static User fetchUser(String id) {
        sleep(20); return new User(id, "Alice-" + id, "alice@example.com");
    }
    private static EnrichedUser enrichWithCrm(User user) {
        sleep(15); return new EnrichedUser(user, "GOLD", 750.0);
    }
    private static List<String> fetchOrders(String userId) {
        sleep(30); return List.of("order-1", "order-2", "order-3");
    }
    private static double fetchCreditScore(String userId) {
        sleep(25); return 780.5;
    }
    private static String fetchFromPrimary(String key) {
        sleep(50); return "primary:" + key;
    }
    private static String fetchFromFallback(String key) {
        sleep(5); return "fallback:" + key;
    }
    private static String slowFetch(String key) {
        sleep(500); return "slow:" + key;
    }
    private static String process(String item) {
        sleep(10); return "processed-" + item;
    }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static class MetricsCounter {
        private final AtomicLong errors = new AtomicLong();
        void recordRequest(long latency, boolean error) { if (error) errors.incrementAndGet(); }
        long errors() { return errors.get(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("═══ 1. Sequential Chain ══════════════════════");
        EnrichedUser enriched = fetchAndEnrichUser("u-1").join();
        System.out.println("Enriched: " + enriched);

        System.out.println("\n═══ 2. Fan-out Fan-in ════════════════════════");
        OrderSummary summary = buildOrderSummary("u-2").join();
        System.out.println("Summary: " + summary);

        System.out.println("\n═══ 3. First-to-succeed ══════════════════════");
        String winner = fetchWithFallback("item-key").join();
        System.out.println("Winner: " + winner);

        System.out.println("\n═══ 4. Timeout + Fallback ════════════════════");
        String result = fetchWithTimeout("slow-key", 100).join(); // will timeout
        System.out.println("Result: " + result);

        System.out.println("\n═══ 5. Retry with Backoff ════════════════════");
        AtomicInteger attempts = new AtomicInteger(0);
        String retryResult = retry(() -> {
            if (attempts.incrementAndGet() < 3) throw new RuntimeException("transient failure");
            return "success-after-" + attempts.get() + "-attempts";
        }, 5, 100).join();
        System.out.println("Retry result: " + retryResult);

        System.out.println("\n═══ 6. Batched Processing ════════════════════");
        List<String> batchResults = processBatch(List.of("a", "b", "c", "d", "e"));
        System.out.println("Batch: " + batchResults);

        System.out.println("\n═══ 7. Error Handling ════════════════════════");
        errorHandlingDemo();

        IO_POOL.shutdown();
        SCHEDULER.shutdown();
    }
}
