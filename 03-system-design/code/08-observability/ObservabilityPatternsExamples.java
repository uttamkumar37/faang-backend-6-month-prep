package systemdesign.observability;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

/**
 * Observability Patterns — metrics, tracing, health checks, SLO tracking
 *
 * Topics:
 *  1. Metrics — Counter, Gauge, Histogram with percentile computation
 *  2. Distributed tracing — trace/span propagation through simulated services
 *  3. SLO tracking — error budget and burn rate alerting
 *  4. Structured logging — JSON log builder with correlation IDs
 *  5. Health checks — composite readiness/liveness probes
 *  6. RED method — Rate, Errors, Duration dashboard
 */
public class ObservabilityPatternsExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. METRICS — Counter, Gauge, Histogram
    // ─────────────────────────────────────────────────────────────────────────

    static class Counter {
        private final String name;
        private final AtomicLong value = new AtomicLong(0);

        Counter(String name) { this.name = name; }

        void increment()              { value.incrementAndGet(); }
        void incrementBy(long delta)  { value.addAndGet(delta); }
        long get()                    { return value.get(); }

        @Override public String toString() {
            return String.format("Counter{%s=%d}", name, value.get());
        }
    }

    static class Gauge {
        private final String name;
        private final AtomicLong value = new AtomicLong(0);

        Gauge(String name) { this.name = name; }

        void set(long v)  { value.set(v); }
        void inc()        { value.incrementAndGet(); }
        void dec()        { value.decrementAndGet(); }
        long get()        { return value.get(); }

        @Override public String toString() {
            return String.format("Gauge{%s=%d}", name, value.get());
        }
    }

    static class Histogram {
        private final String name;
        // Bucket boundaries (upper-inclusive); last bucket = +∞
        private final long[]        bucketBounds;
        private final AtomicLong[]  bucketCounts;
        private final AtomicLong    totalCount = new AtomicLong(0);
        private final AtomicLong    totalSum   = new AtomicLong(0);

        Histogram(String name, long... bounds) {
            this.name = name;
            // Add +∞ bucket at end
            this.bucketBounds  = Arrays.copyOf(bounds, bounds.length + 1);
            this.bucketBounds[bounds.length] = Long.MAX_VALUE;
            this.bucketCounts  = new AtomicLong[this.bucketBounds.length];
            Arrays.setAll(this.bucketCounts, i -> new AtomicLong(0));
        }

        void observe(long value) {
            for (int i = 0; i < bucketBounds.length; i++) {
                if (value <= bucketBounds[i]) {
                    bucketCounts[i].incrementAndGet(); // cumulative buckets
                }
            }
            totalCount.incrementAndGet();
            totalSum.addAndGet(value);
        }

        // Approximate percentile from histogram buckets
        double percentile(double p) {
            long target = (long) Math.ceil(totalCount.get() * p);
            long prev = 0;
            for (int i = 0; i < bucketBounds.length; i++) {
                long cumulative = bucketCounts[i].get();
                if (cumulative >= target) {
                    // Linear interpolation within bucket
                    long lowerBound = i == 0 ? 0 : bucketBounds[i - 1];
                    long upperBound = bucketBounds[i] == Long.MAX_VALUE ? bucketBounds[i - 1] * 2 : bucketBounds[i];
                    long inBucket   = cumulative - prev;
                    if (inBucket == 0) return upperBound;
                    double fraction = (double) (target - prev) / inBucket;
                    return lowerBound + fraction * (upperBound - lowerBound);
                }
                prev = cumulative;
            }
            return bucketBounds[bucketBounds.length - 2]; // last finite bucket
        }

        double average() {
            long count = totalCount.get();
            return count == 0 ? 0 : (double) totalSum.get() / count;
        }

        void print() {
            System.out.printf("  Histogram{%s} count=%d avg=%.1fms p50=%.0fms p95=%.0fms p99=%.0fms%n",
                name, totalCount.get(), average(),
                percentile(0.50), percentile(0.95), percentile(0.99));
        }
    }

    static void metricsDemo() {
        System.out.println("=== 1. Metrics ===");

        Counter httpRequests  = new Counter("http_requests_total");
        Counter httpErrors    = new Counter("http_errors_total");
        Gauge   activeConns   = new Gauge("active_connections");
        Gauge   heapBytes     = new Gauge("jvm_heap_used_bytes");

        // Simulate request traffic
        Random rng = new Random(42);
        Histogram latency = new Histogram("http_request_duration_ms",
            10, 25, 50, 100, 200, 500, 1000);

        for (int i = 0; i < 1000; i++) {
            httpRequests.increment();
            activeConns.inc();

            // Simulate latency: 80% fast, 15% medium, 5% slow
            long ms;
            double r = rng.nextDouble();
            if (r < 0.80)      ms = 10  + (long)(rng.nextDouble() * 40);  // 10-50ms
            else if (r < 0.95) ms = 100 + (long)(rng.nextDouble() * 100); // 100-200ms
            else               ms = 500 + (long)(rng.nextDouble() * 600); // 500-1100ms (slow!)

            latency.observe(ms);

            if (rng.nextDouble() < 0.02) httpErrors.increment(); // 2% error rate

            activeConns.dec();
        }

        heapBytes.set(128 * 1024 * 1024); // 128 MB

        System.out.println("  " + httpRequests);
        System.out.println("  " + httpErrors);
        System.out.printf("  Error rate: %.2f%%%n",
            100.0 * httpErrors.get() / httpRequests.get());
        System.out.println("  " + activeConns);
        System.out.printf("  %s: %.1f MB%n", heapBytes, heapBytes.get() / 1024.0 / 1024.0);
        latency.print();

        System.out.println("\n  RED Method summary:");
        System.out.printf("    Rate:   %d req/s (simulated 1000 over 1s)%n", httpRequests.get());
        System.out.printf("    Errors: %d (%.2f%%)%n", httpErrors.get(),
            100.0 * httpErrors.get() / httpRequests.get());
        System.out.printf("    Duration p99: %.0fms%n", latency.percentile(0.99));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. DISTRIBUTED TRACING
    // ─────────────────────────────────────────────────────────────────────────

    // Trace context propagated through headers / thread-locals
    record TraceContext(String traceId, String spanId) {
        static TraceContext root() {
            return new TraceContext(randomHex(16), randomHex(8));
        }
        TraceContext childSpan() {
            return new TraceContext(traceId, randomHex(8)); // same trace, new span
        }
    }

    static String randomHex(int bytes) {
        StringBuilder sb = new StringBuilder();
        Random rng = new Random();
        for (int i = 0; i < bytes; i++) sb.append(String.format("%02x", rng.nextInt(256)));
        return sb.toString();
    }

    record Span(String name, String traceId, String spanId, String parentSpanId,
                long startMs, long durationMs, Map<String, String> tags, String status) {}

    static class Tracer {
        private final List<Span> spans = new CopyOnWriteArrayList<>();

        Span startSpan(String name, TraceContext ctx, String parentSpanId) {
            return new Span(name, ctx.traceId(), ctx.spanId(), parentSpanId,
                System.currentTimeMillis(), 0, new HashMap<>(), "");
        }

        Span finishSpan(Span span, String status, Map<String, String> tags) {
            long duration = System.currentTimeMillis() - span.startMs();
            Span finished = new Span(
                span.name(), span.traceId(), span.spanId(), span.parentSpanId(),
                span.startMs(), duration, tags, status
            );
            spans.add(finished);
            return finished;
        }

        List<Span> getTrace(String traceId) {
            return spans.stream().filter(s -> s.traceId().equals(traceId)).toList();
        }

        void printTrace(String traceId) {
            List<Span> trace = getTrace(traceId);
            System.out.printf("  Trace %s (%d spans):%n", traceId.substring(0, 8) + "...", trace.size());
            trace.forEach(s -> {
                String parent = s.parentSpanId() != null ? "← " + s.parentSpanId().substring(0, 4) + "..." : "(root)";
                System.out.printf("    span=%-32s spanId=%s %s duration=%dms status=%s%n",
                    s.name(), s.spanId().substring(0, 4) + "...", parent, s.durationMs(), s.status());
            });
        }
    }

    // Simulate a multi-service request: API → Order Service → DB + Inventory
    static void distributedTracingDemo() throws Exception {
        System.out.println("\n=== 2. Distributed Tracing ===");

        Tracer tracer = new Tracer();

        // 1. API Gateway receives request → root span
        TraceContext rootCtx = TraceContext.root();
        Span apiSpan = tracer.startSpan("api.handle_request", rootCtx, null);
        Thread.sleep(5);

        // 2. API calls Order Service — propagate traceId, new spanId
        TraceContext orderCtx = rootCtx.childSpan();
        Span orderSpan = tracer.startSpan("order-service.create_order", orderCtx, apiSpan.spanId());
        Thread.sleep(10);

        // 3. Order Service queries DB
        TraceContext dbCtx = orderCtx.childSpan();
        Span dbSpan = tracer.startSpan("db.query", dbCtx, orderSpan.spanId());
        Thread.sleep(20); // simulate DB latency
        tracer.finishSpan(dbSpan, "OK", Map.of("db.statement", "INSERT INTO orders...", "db.type", "postgresql"));

        // 4. Order Service calls Inventory Service
        TraceContext invCtx = orderCtx.childSpan();
        Span invSpan = tracer.startSpan("inventory-service.reserve_stock", invCtx, orderSpan.spanId());
        Thread.sleep(15);
        tracer.finishSpan(invSpan, "OK", Map.of("http.status_code", "200"));

        tracer.finishSpan(orderSpan, "OK", Map.of("order.id", "ORD-001"));
        tracer.finishSpan(apiSpan,   "OK", Map.of("http.status_code", "201", "http.method", "POST"));

        tracer.printTrace(rootCtx.traceId());

        System.out.println("\n  Trace context propagated via HTTP header:");
        System.out.printf("    traceparent: 00-%s-%s-01%n", rootCtx.traceId(), orderCtx.spanId());
        System.out.println("    (W3C Trace Context standard — OpenTelemetry)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SLO TRACKING + ERROR BUDGET
    // ─────────────────────────────────────────────────────────────────────────

    static class SloTracker {
        private final String sloName;
        private final double targetP;       // e.g. 0.999 for 99.9%
        private final long windowSeconds;   // rolling window (e.g. 30 days)

        private long totalRequests  = 0;
        private long goodRequests   = 0;

        SloTracker(String sloName, double targetP, long windowSeconds) {
            this.sloName        = sloName;
            this.targetP        = targetP;
            this.windowSeconds  = windowSeconds;
        }

        void record(boolean good) {
            totalRequests++;
            if (good) goodRequests++;
        }

        double currentSli() {
            return totalRequests == 0 ? 1.0 : (double) goodRequests / totalRequests;
        }

        double errorBudgetTotal() {
            return (1.0 - targetP) * windowSeconds; // seconds of allowed failure
        }

        double errorBudgetConsumed() {
            double actualErrorRate = 1.0 - currentSli();
            return actualErrorRate * windowSeconds;
        }

        double errorBudgetRemaining() {
            return Math.max(0, errorBudgetTotal() - errorBudgetConsumed());
        }

        double errorBudgetRemainingPercent() {
            double total = errorBudgetTotal();
            return total == 0 ? 0 : 100.0 * errorBudgetRemaining() / total;
        }

        double burnRate() {
            return errorBudgetTotal() == 0 ? 0 :
                errorBudgetConsumed() / errorBudgetTotal();
        }

        void printStatus() {
            System.out.printf("  SLO[%s=%,.1f%%] SLI=%.4f%% budget_remaining=%.1f%% burn_rate=%.2f%n",
                sloName, targetP * 100, currentSli() * 100,
                errorBudgetRemainingPercent(), burnRate());

            if (burnRate() > 14.4)  System.out.println("  ⚠ PAGE immediately — budget burns in < 2 hours!");
            else if (burnRate() > 6) System.out.println("  ⚠ Page within 6 hours");
            else if (burnRate() > 1) System.out.println("  ~ Warning — burning budget > 1x rate");
            else                     System.out.println("  OK — within error budget");
        }
    }

    static void sloTrackingDemo() {
        System.out.println("\n=== 3. SLO Tracking + Error Budget ===");

        // SLO: 99.9% of requests succeed; 30-day rolling window
        long thirtyDaysSeconds = 30L * 24 * 60 * 60;
        SloTracker slo = new SloTracker("availability", 0.999, thirtyDaysSeconds);

        System.out.println("  Scenario A: healthy week (0.05% error rate)");
        for (int i = 0; i < 10_000; i++) slo.record(i % 2000 != 0); // 0.05% error
        slo.printStatus();

        // Now simulate an incident: 5% error rate for 1000 requests
        System.out.println("\n  Scenario B: after incident (5% error rate spike)");
        SloTracker slo2 = new SloTracker("availability", 0.999, thirtyDaysSeconds);
        for (int i = 0; i < 1000; i++) slo2.record(i % 20 != 0); // 5% error rate
        slo2.printStatus();

        // Budget calc
        double budgetSeconds = (1 - 0.999) * thirtyDaysSeconds;
        System.out.printf("%n  Error budget for 99.9%% SLO / 30 days: %.1f minutes = %.1f seconds%n",
            budgetSeconds / 60, budgetSeconds);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. STRUCTURED LOGGING with correlation IDs
    // ─────────────────────────────────────────────────────────────────────────

    static class StructuredLogger {
        private final String serviceName;

        // In real apps: MDC (Mapped Diagnostic Context) — ThreadLocal
        private final ThreadLocal<Map<String, String>> context =
            ThreadLocal.withInitial(HashMap::new);

        StructuredLogger(String serviceName) { this.serviceName = serviceName; }

        void setContext(String key, String value) { context.get().put(key, value); }
        void clearContext() { context.remove(); }

        private void log(String level, String message, Map<String, Object> fields) {
            // Build structured JSON-like log entry
            StringBuilder sb = new StringBuilder("{");
            sb.append(String.format("\"ts\":\"%s\"", Instant.now()));
            sb.append(String.format(",\"level\":\"%s\"", level));
            sb.append(String.format(",\"service\":\"%s\"", serviceName));
            sb.append(String.format(",\"msg\":\"%s\"", message));

            // Inject correlation context
            context.get().forEach((k, v) ->
                sb.append(String.format(",\"%s\":\"%s\"", k, v)));

            // Extra fields
            fields.forEach((k, v) ->
                sb.append(String.format(",\"%s\":\"%s\"", k, v)));

            sb.append("}");
            System.out.println("  " + sb);
        }

        void info(String msg, Object... kvPairs) { log("INFO",  msg, toMap(kvPairs)); }
        void warn(String msg, Object... kvPairs) { log("WARN",  msg, toMap(kvPairs)); }
        void error(String msg, Object... kvPairs){ log("ERROR", msg, toMap(kvPairs)); }

        @SuppressWarnings("unchecked")
        private Map<String, Object> toMap(Object[] pairs) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i + 1 < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
            return m;
        }
    }

    static void structuredLoggingDemo() {
        System.out.println("\n=== 4. Structured Logging + Correlation IDs ===");

        StructuredLogger orderLog = new StructuredLogger("order-service");
        StructuredLogger payLog   = new StructuredLogger("payment-service");

        // Request arrives; generate correlation ID
        String correlationId = "corr-" + randomHex(4);
        String traceId       = randomHex(16);

        // Set correlation context (MDC equivalent)
        orderLog.setContext("correlationId", correlationId);
        orderLog.setContext("traceId", traceId);

        orderLog.info("order request received", "userId", "u123", "amount", "99.99");
        orderLog.info("inventory check passed",  "sku", "WIDGET-01", "stock", "42");

        // Pass correlation IDs to downstream service
        payLog.setContext("correlationId", correlationId);
        payLog.setContext("traceId", traceId);

        payLog.info("payment initiated", "gateway", "stripe", "amount", "99.99");
        payLog.warn("gateway slow response", "latencyMs", "850", "threshold", "500");
        payLog.info("payment confirmed",  "transactionId", "txn-abc123");

        orderLog.info("order confirmed", "orderId", "ORD-456", "status", "PLACED");

        System.out.println("\n  → All logs queryable by correlationId=" + correlationId);
        System.out.println("  → In Kibana/Loki: filter by correlationId to see full request flow");

        orderLog.clearContext();
        payLog.clearContext();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. HEALTH CHECKS — readiness + liveness
    // ─────────────────────────────────────────────────────────────────────────

    interface HealthCheck {
        record Result(String name, boolean healthy, String details) {}
        Result check();
    }

    static class DatabaseHealthCheck implements HealthCheck {
        private final boolean reachable;
        DatabaseHealthCheck(boolean reachable) { this.reachable = reachable; }

        @Override public Result check() {
            if (reachable) return new Result("database", true, "connected, latency=5ms");
            return new Result("database", false, "connection refused: tcp://db:5432");
        }
    }

    static class CacheHealthCheck implements HealthCheck {
        private final boolean available;
        CacheHealthCheck(boolean available) { this.available = available; }

        @Override public Result check() {
            if (available) return new Result("redis-cache", true, "PONG latency=1ms");
            return new Result("redis-cache", false, "TIMEOUT after 1000ms");
        }
    }

    static class DiskHealthCheck implements HealthCheck {
        private final long freeMB;
        DiskHealthCheck(long freeMB) { this.freeMB = freeMB; }

        @Override public Result check() {
            if (freeMB > 500) return new Result("disk", true, freeMB + "MB free");
            return new Result("disk", false, "LOW DISK: only " + freeMB + "MB free");
        }
    }

    static class CompositeHealthCheck {
        private final List<HealthCheck> checks;

        CompositeHealthCheck(List<HealthCheck> checks) { this.checks = checks; }

        Map<String, Object> evaluate() {
            List<HealthCheck.Result> results = checks.stream()
                .map(HealthCheck::check).toList();

            boolean allHealthy = results.stream().allMatch(HealthCheck.Result::healthy);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", allHealthy ? "UP" : "DOWN");
            response.put("timestamp", Instant.now().toString());

            Map<String, String> details = new LinkedHashMap<>();
            results.forEach(r -> details.put(r.name(), (r.healthy() ? "UP" : "DOWN") + " — " + r.details()));
            response.put("components", details);

            return response;
        }
    }

    static void healthCheckDemo() {
        System.out.println("\n=== 5. Health Checks ===");

        // Happy path
        CompositeHealthCheck healthy = new CompositeHealthCheck(List.of(
            new DatabaseHealthCheck(true),
            new CacheHealthCheck(true),
            new DiskHealthCheck(10_000)
        ));
        Map<String, Object> h = healthy.evaluate();
        System.out.println("  Healthy service:");
        h.forEach((k, v) -> System.out.println("    " + k + ": " + v));

        // DB down
        System.out.println("\n  DB unavailable:");
        CompositeHealthCheck degraded = new CompositeHealthCheck(List.of(
            new DatabaseHealthCheck(false),
            new CacheHealthCheck(true),
            new DiskHealthCheck(5_000)
        ));
        Map<String, Object> d = degraded.evaluate();
        d.forEach((k, v) -> System.out.println("    " + k + ": " + v));
        System.out.println("\n  Kubernetes behaviour:");
        System.out.println("    readiness=DOWN → remove pod from Service load balancer");
        System.out.println("    liveness=DOWN  → restart pod");
        System.out.println("    startup probe  → skip liveness until app fully started");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. RED METHOD DASHBOARD SIMULATION
    // ─────────────────────────────────────────────────────────────────────────

    static void redMethodDemo() {
        System.out.println("\n=== 6. RED Method Dashboard ===");

        // Simulate 5 seconds of traffic
        Counter requests = new Counter("requests_total");
        Counter errors   = new Counter("errors_total");
        Histogram duration = new Histogram("duration_ms",
            5, 10, 25, 50, 100, 250, 500, 1000, 2500);

        Random rng = new Random(99);
        int windowSeconds = 5;
        int rps = 200;
        int total = windowSeconds * rps;

        for (int i = 0; i < total; i++) {
            requests.increment();
            double r = rng.nextDouble();
            long ms;
            boolean error = r < 0.015; // 1.5% error rate
            if (error) {
                errors.increment();
                ms = 500 + (long)(rng.nextDouble() * 2000); // errors are slow
            } else {
                ms = 5 + (long)(Math.pow(rng.nextDouble(), 2) * 200); // fast
            }
            duration.observe(ms);
        }

        System.out.println("  Service: order-service  |  Window: 5 seconds");
        System.out.println("  ─────────────────────────────────────────────");
        System.out.printf("  Rate:    %d req/s%n", requests.get() / windowSeconds);
        System.out.printf("  Errors:  %d (%.2f%%)  %s%n",
            errors.get(), 100.0 * errors.get() / requests.get(),
            errors.get() * 100.0 / requests.get() > 1.0 ? "<< ALERT" : "");
        System.out.printf("  p50:     %.0f ms%n", duration.percentile(0.50));
        System.out.printf("  p95:     %.0f ms%n", duration.percentile(0.95));
        System.out.printf("  p99:     %.0f ms   %s%n",
            duration.percentile(0.99),
            duration.percentile(0.99) > 500 ? "<< ALERT" : "");
        System.out.println("  ─────────────────────────────────────────────");
        System.out.println("\n  USE Method (infrastructure):");
        System.out.println("    CPU utilization:  62%  saturation: queue depth=8   errors: 0");
        System.out.println("    Memory:           74%  saturation: swap=0          errors: 0");
        System.out.println("    Network:          31%  saturation: tx_drops=0      errors: 0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        metricsDemo();
        distributedTracingDemo();
        sloTrackingDemo();
        structuredLoggingDemo();
        healthCheckDemo();
        redMethodDemo();
        System.out.println("\n=== All observability pattern demos completed ===");
    }
}
