package com.faangprep.javabackend.springboot;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

/**
 * Resilience4j patterns in a Spring Boot service
 *
 * application.yml config for these annotations:
 *
 * resilience4j:
 *   circuitbreaker:
 *     instances:
 *       inventoryService:
 *         slidingWindowType: COUNT_BASED
 *         slidingWindowSize: 10
 *         failureRateThreshold: 50
 *         waitDurationInOpenState: 30s
 *         permittedNumberOfCallsInHalfOpenState: 3
 *   retry:
 *     instances:
 *       inventoryService:
 *         maxAttempts: 3
 *         waitDuration: 500ms
 *         enableExponentialBackoff: true
 *         exponentialBackoffMultiplier: 2
 *         retryExceptions:
 *           - java.io.IOException
 *           - java.net.ConnectException
 *   bulkhead:
 *     instances:
 *       inventoryService:
 *         maxConcurrentCalls: 25
 *         maxWaitDuration: 100ms
 *   timelimiter:
 *     instances:
 *       inventoryService:
 *         timeoutDuration: 3s
 */
@Service
public class ResiliencePatterns {

    private static final Logger log = LoggerFactory.getLogger(ResiliencePatterns.class);

    private final InventoryClient inventoryClient;

    public ResiliencePatterns(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CIRCUIT BREAKER + RETRY + BULKHEAD (stacked decorators)
    // Order matters: CircuitBreaker → Retry → Bulkhead → TimeLimiter
    // ─────────────────────────────────────────────────────────────────────────

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "inventoryFallback")
    @Retry(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public InventoryResponse checkInventory(String productId) {
        log.info("Checking inventory for product: {}", productId);
        return inventoryClient.check(productId);
    }

    // Fallback — called when circuit is OPEN or all retries exhausted
    InventoryResponse inventoryFallback(String productId, Exception e) {
        log.warn("Inventory fallback for product={} reason={}", productId, e.getMessage());
        // Return a safe default — either cached value, or "assume available" strategy
        return InventoryResponse.unavailableWithMessage(
                "Inventory temporarily unavailable, please try again shortly");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ASYNC WITH TIMEOUT (TimeLimiter requires CompletableFuture)
    // ─────────────────────────────────────────────────────────────────────────

    @TimeLimiter(name = "inventoryService", fallbackMethod = "asyncFallback")
    @CircuitBreaker(name = "inventoryService")
    public CompletableFuture<InventoryResponse> checkInventoryAsync(String productId) {
        return CompletableFuture.supplyAsync(() -> inventoryClient.check(productId));
    }

    CompletableFuture<InventoryResponse> asyncFallback(String productId, Exception e) {
        log.warn("Async timeout fallback for product={}", productId);
        return CompletableFuture.completedFuture(InventoryResponse.defaultAvailable());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEMO SEAM (replace with a real HTTP or SDK client in a production service)
    // ─────────────────────────────────────────────────────────────────────────

    interface InventoryClient {
        InventoryResponse check(String productId);
    }

    record InventoryResponse(boolean available, int quantity, String message) {
        static InventoryResponse unavailableWithMessage(String msg) {
            return new InventoryResponse(false, 0, msg);
        }
        static InventoryResponse defaultAvailable() {
            return new InventoryResponse(true, 10, "default");
        }
    }
}
