package systemdesign.circuitbreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/**
 * Circuit Breaker Pattern — Manual State Machine
 * States: CLOSED (normal) → OPEN (failing, block calls) → HALF_OPEN (probe)
 * Also includes: Retry with exponential backoff + jitter, Bulkhead pattern.
 */
public class CircuitBreakerPatterns {

    // ─────────────────────────────────────────────
    // 1. CIRCUIT BREAKER STATE MACHINE
    // ─────────────────────────────────────────────

    enum CircuitState { CLOSED, OPEN, HALF_OPEN }

    static class CircuitBreaker {
        private final String name;
        private final int failureThreshold;        // failures before OPEN
        private final int successThreshold;        // successes in HALF_OPEN to close
        private final Duration openDuration;       // how long to stay OPEN before probing

        private volatile CircuitState state = CircuitState.CLOSED;
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private volatile Instant openedAt;

        public CircuitBreaker(String name, int failureThreshold, int successThreshold,
                              Duration openDuration) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.successThreshold = successThreshold;
            this.openDuration = openDuration;
        }

        public <T> T execute(Supplier<T> operation) {
            if (!allowRequest()) {
                throw new CircuitBreakerOpenException("Circuit " + name + " is OPEN");
            }

            try {
                T result = operation.get();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                throw e;
            }
        }

        private boolean allowRequest() {
            return switch (state) {
                case CLOSED   -> true;
                case OPEN     -> checkTransitionToHalfOpen();
                case HALF_OPEN -> true;  // allow one probe request
            };
        }

        private synchronized boolean checkTransitionToHalfOpen() {
            if (state == CircuitState.OPEN &&
                Instant.now().isAfter(openedAt.plus(openDuration))) {
                state = CircuitState.HALF_OPEN;
                successCount.set(0);
                System.out.printf("[%s] OPEN → HALF_OPEN (probing)%n", name);
                return true;
            }
            return false;
        }

        private synchronized void onSuccess() {
            if (state == CircuitState.HALF_OPEN) {
                if (successCount.incrementAndGet() >= successThreshold) {
                    state = CircuitState.CLOSED;
                    failureCount.set(0);
                    System.out.printf("[%s] HALF_OPEN → CLOSED (recovered)%n", name);
                }
            } else {
                failureCount.set(0);  // reset consecutive failures on success
            }
        }

        private synchronized void onFailure() {
            if (state == CircuitState.HALF_OPEN) {
                // Probe failed — back to OPEN
                state = CircuitState.OPEN;
                openedAt = Instant.now();
                System.out.printf("[%s] HALF_OPEN → OPEN (probe failed)%n", name);
            } else if (failureCount.incrementAndGet() >= failureThreshold) {
                state = CircuitState.OPEN;
                openedAt = Instant.now();
                System.out.printf("[%s] CLOSED → OPEN (failure threshold reached: %d)%n",
                    name, failureCount.get());
            }
        }

        public CircuitState getState() { return state; }
        public int getFailureCount() { return failureCount.get(); }
    }

    static class CircuitBreakerOpenException extends RuntimeException {
        CircuitBreakerOpenException(String msg) { super(msg); }
    }

    // ─────────────────────────────────────────────
    // 2. RETRY WITH EXPONENTIAL BACKOFF + JITTER
    // Prevents thundering herd when many clients retry simultaneously.
    // ─────────────────────────────────────────────

    static class RetryPolicy {
        private final int maxAttempts;
        private final long baseDelayMs;
        private final long maxDelayMs;
        private final double jitterFactor;

        RetryPolicy(int maxAttempts, long baseDelayMs, long maxDelayMs, double jitterFactor) {
            this.maxAttempts = maxAttempts;
            this.baseDelayMs = baseDelayMs;
            this.maxDelayMs = maxDelayMs;
            this.jitterFactor = jitterFactor;
        }

        public <T> T execute(Supplier<T> operation, String operationName) {
            Exception lastException = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    T result = operation.get();
                    if (attempt > 1) {
                        System.out.printf("[Retry] %s succeeded on attempt %d%n", operationName, attempt);
                    }
                    return result;
                } catch (Exception e) {
                    lastException = e;
                    if (attempt == maxAttempts) break;

                    long delay = calculateDelay(attempt);
                    System.out.printf("[Retry] %s failed (attempt %d/%d): %s. Retrying in %dms...%n",
                        operationName, attempt, maxAttempts, e.getMessage(), delay);
                    try { Thread.sleep(delay); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
            throw new RuntimeException("Max retries exceeded for: " + operationName, lastException);
        }

        private long calculateDelay(int attempt) {
            // Exponential: base * 2^(attempt-1)
            long exponential = (long)(baseDelayMs * Math.pow(2, attempt - 1));
            long capped = Math.min(exponential, maxDelayMs);
            // Jitter: ±jitterFactor% random variation
            long jitter = (long)(capped * jitterFactor * (Math.random() * 2 - 1));
            return Math.max(0, capped + jitter);
        }
    }

    // ─────────────────────────────────────────────
    // 3. BULKHEAD PATTERN
    // Isolate resource pools per downstream service.
    // One slow service can't exhaust threads for other services.
    // ─────────────────────────────────────────────

    static class Bulkhead {
        private final String name;
        private final Semaphore semaphore;

        Bulkhead(String name, int maxConcurrentCalls) {
            this.name = name;
            this.semaphore = new Semaphore(maxConcurrentCalls);
        }

        public <T> T execute(Supplier<T> operation) {
            if (!semaphore.tryAcquire()) {
                throw new RuntimeException("Bulkhead [" + name + "] full — rejecting request");
            }
            try {
                return operation.get();
            } finally {
                semaphore.release();
            }
        }

        public int availableCapacity() { return semaphore.availablePermits(); }
    }

    // ─────────────────────────────────────────────
    // 4. COMBINING: CircuitBreaker + Retry + Bulkhead
    // This is what Resilience4j does — compose decorators.
    // ─────────────────────────────────────────────

    static class ResilientServiceClient {
        private final CircuitBreaker circuitBreaker;
        private final RetryPolicy retryPolicy;
        private final Bulkhead bulkhead;

        ResilientServiceClient(CircuitBreaker cb, RetryPolicy retry, Bulkhead bulkhead) {
            this.circuitBreaker = cb;
            this.retryPolicy = retry;
            this.bulkhead = bulkhead;
        }

        public String callExternalApi(String request) {
            // Order: Bulkhead (limit concurrent) → CircuitBreaker → Retry
            return bulkhead.execute(() ->
                circuitBreaker.execute(() ->
                    retryPolicy.execute(() -> doHttpCall(request), "callExternalApi")
                )
            );
        }

        private String doHttpCall(String request) {
            // Simulates unreliable HTTP call
            if (Math.random() < 0.5) throw new RuntimeException("Service unavailable");
            return "Response for: " + request;
        }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Circuit Breaker State Machine ===");
        CircuitBreaker cb = new CircuitBreaker("payment-service",
            3, 2, Duration.ofSeconds(2));

        AtomicInteger callCount = new AtomicInteger(0);
        // Simulate 5 failures to open circuit
        for (int i = 0; i < 10; i++) {
            int call = i;
            try {
                cb.execute(() -> {
                    callCount.incrementAndGet();
                    if (call < 4) throw new RuntimeException("connection refused");
                    return "OK";
                });
                System.out.println("Call " + (i+1) + ": SUCCESS");
            } catch (CircuitBreakerOpenException e) {
                System.out.println("Call " + (i+1) + ": CIRCUIT OPEN — fast fail");
            } catch (RuntimeException e) {
                System.out.println("Call " + (i+1) + ": FAILED (" + e.getMessage() + ") state=" + cb.getState());
            }
        }

        System.out.println("\nWaiting 2.1s for circuit to transition to HALF_OPEN...");
        Thread.sleep(2100);

        // Probe — should go HALF_OPEN and then CLOSED
        for (int i = 0; i < 3; i++) {
            try {
                String result = cb.execute(() -> "OK from recovered service");
                System.out.println("Probe " + (i+1) + ": " + result + " (state=" + cb.getState() + ")");
            } catch (Exception e) {
                System.out.println("Probe " + (i+1) + ": " + e.getMessage());
            }
        }

        System.out.println("\n=== Retry with Exponential Backoff ===");
        RetryPolicy retry = new RetryPolicy(4, 50, 500, 0.3);
        AtomicInteger attempts = new AtomicInteger();
        try {
            String result = retry.execute(() -> {
                int a = attempts.incrementAndGet();
                if (a < 3) throw new RuntimeException("transient error");
                return "success on attempt " + a;
            }, "fetchData");
            System.out.println("Result: " + result);
        } catch (RuntimeException e) {
            System.out.println("Final failure: " + e.getMessage());
        }

        System.out.println("\n=== Bulkhead ===");
        Bulkhead bulkhead = new Bulkhead("payment-api", 2);
        Semaphore start = new Semaphore(0);
        // Simulate 4 concurrent calls to a bulkhead of size 2
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            final int id = i;
            executor.submit(() -> {
                try {
                    start.acquire();
                    String result = bulkhead.execute(() -> {
                        Thread.sleep(100);
                        return "result-" + id;
                    });
                    System.out.println("Request " + id + ": " + result);
                } catch (Exception e) {
                    System.out.println("Request " + id + ": REJECTED (bulkhead full)");
                }
            });
        }
        start.release(4);  // all start simultaneously
        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }
}
