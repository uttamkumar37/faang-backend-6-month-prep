import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncidentDebuggingPlaybook {
    enum Symptom {
        HIGH_CPU, MEMORY_PRESSURE, SLOW_API, DB_LATENCY, KAFKA_LAG, REDIS_LATENCY, THREAD_POOL_EXHAUSTION
    }

    record Metric(String name, String queryOrCommand, String healthySignal) {}

    record DebugStep(String phase, String action, List<Metric> evidence) {}

    record IncidentPlan(Symptom symptom, List<DebugStep> steps, List<String> mitigations, List<String> prevention) {}

    public IncidentPlan planFor(Symptom symptom) {
        return switch (symptom) {
            case HIGH_CPU -> highCpuPlan();
            case MEMORY_PRESSURE -> memoryPlan();
            case SLOW_API -> slowApiPlan();
            case DB_LATENCY -> dbLatencyPlan();
            case KAFKA_LAG -> kafkaLagPlan();
            case REDIS_LATENCY -> redisPlan();
            case THREAD_POOL_EXHAUSTION -> threadPoolPlan();
        };
    }

    private IncidentPlan highCpuPlan() {
        return new IncidentPlan(
                Symptom.HIGH_CPU,
                List.of(
                        new DebugStep("scope", "Confirm host, container, and route-level impact", List.of(
                                new Metric("process_cpu", "top -H -p <pid>", "No single thread is permanently hot"),
                                new Metric("gc_cpu", "jstat -gcutil <pid> 1s 10", "GC is not running continuously"))),
                        new DebugStep("evidence", "Capture thread dump and map hot native thread id to Java stack", List.of(
                                new Metric("thread_dump", "jcmd <pid> Thread.print", "Hot stack points to bounded work")))),
                List.of("rollback recent deploy", "disable hot feature flag", "rate-limit expensive route"),
                List.of("add CPU by route dashboard", "load test large payloads", "add retry budgets"));
    }

    private IncidentPlan memoryPlan() {
        return new IncidentPlan(
                Symptom.MEMORY_PRESSURE,
                List.of(new DebugStep("evidence", "Check after-GC heap and class histogram before restart", List.of(
                        new Metric("heap", "jcmd <pid> GC.heap_info", "Old gen falls after GC"),
                        new Metric("histogram", "jcmd <pid> GC.class_histogram", "No unexpected dominant retained type")))),
                List.of("restart one replica after heap evidence", "reduce traffic", "disable large batch job"),
                List.of("bound caches", "soak test", "alert on after-GC heap growth"));
    }

    private IncidentPlan slowApiPlan() {
        return new IncidentPlan(
                Symptom.SLOW_API,
                List.of(new DebugStep("trace", "Split latency into app, DB, cache, and downstream spans", List.of(
                        new Metric("route_p99", "APM route latency", "p99 within SLO"),
                        new Metric("pool_wait", "Hikari acquisition timer", "near zero under normal load")))),
                List.of("shed load", "lower timeout", "rollback", "serve stale cache for non-critical reads"),
                List.of("distributed tracing", "timeout budgets", "pool saturation alerts"));
    }

    private IncidentPlan dbLatencyPlan() {
        return new IncidentPlan(
                Symptom.DB_LATENCY,
                List.of(new DebugStep("query", "Compare query execution time with connection acquisition time", List.of(
                        new Metric("explain", "EXPLAIN ANALYZE <query>", "Index scan matches selectivity"),
                        new Metric("connections", "pool active/idle/pending", "pending acquisitions stay low")))),
                List.of("kill runaway query", "rollback migration", "temporarily disable expensive filter"),
                List.of("query-plan review", "slow query alerts", "N+1 integration tests"));
    }

    private IncidentPlan kafkaLagPlan() {
        return new IncidentPlan(
                Symptom.KAFKA_LAG,
                List.of(new DebugStep("lag", "Check lag by partition and handler processing time", List.of(
                        new Metric("consumer_lag", "kafka-consumer-groups --describe", "lag drains after spike"),
                        new Metric("handler_time", "consumer processing timer", "processing time below poll interval budget")))),
                List.of("scale consumers up to partition count", "pause poison partition", "route bad records to DLQ"),
                List.of("idempotent consumers", "partition-key review", "DLQ replay runbook"));
    }

    private IncidentPlan redisPlan() {
        return new IncidentPlan(
                Symptom.REDIS_LATENCY,
                List.of(new DebugStep("cache", "Check latency, hot keys, evictions, and miss amplification", List.of(
                        new Metric("slowlog", "redis-cli SLOWLOG GET 20", "No blocking big-key commands"),
                        new Metric("info", "redis-cli INFO", "No unexpected evictions or memory pressure")))),
                List.of("bypass corrupted key", "warm critical keys", "rate-limit miss path"),
                List.of("TTL jitter", "single-flight loading", "hot-key alerting"));
    }

    private IncidentPlan threadPoolPlan() {
        return new IncidentPlan(
                Symptom.THREAD_POOL_EXHAUSTION,
                List.of(new DebugStep("saturation", "Check active threads, queue depth, and blocked stacks", List.of(
                        new Metric("thread_dump", "jcmd <pid> Thread.print", "Threads are not blocked on one dependency"),
                        new Metric("executor", "executor active/queue/rejected metrics", "queue drains and rejections are zero")))),
                List.of("shed traffic", "pause non-critical jobs", "increase replicas"),
                List.of("bounded queues", "bulkheads", "timeouts on every external call"));
    }

    public static void main(String[] args) {
        var playbook = new IncidentDebuggingPlaybook();
        var plan = playbook.planFor(Symptom.SLOW_API);
        System.out.println("Symptom: " + plan.symptom());
        plan.steps().forEach(step -> System.out.println(step.phase() + ": " + step.action()));
    }

    static final class IncidentTimeline {
        private final List<Map<String, String>> events = new ArrayList<>();

        void add(String event, String evidence) {
            events.add(Map.of(
                    "time", Instant.now().toString(),
                    "event", event,
                    "evidence", evidence));
        }

        boolean hasMinimumPostmortemData(Duration incidentDuration) {
            return incidentDuration.toMinutes() >= 0 && events.size() >= 3;
        }
    }

    // Test ideas:
    // 1. Verify every Symptom returns at least one evidence step, mitigation, and prevention item.
    // 2. Verify postmortem timeline rejects incomplete incidents in your own wrapper logic.
    // 3. Verify slow API plan includes both route latency and connection pool evidence.
}
