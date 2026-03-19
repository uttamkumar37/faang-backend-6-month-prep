package com.faangprep.javabackend.springboot;

import io.micrometer.core.instrument.*;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

/**
 * Observability configuration:
 *
 * - Custom metrics (Micrometer → Prometheus → Grafana)
 * - Structured logging with MDC (trace correlation)
 * - Distributed tracing via Micrometer Observation API
 *
 * application.yml:
 * management:
 *   endpoints:
 *     web:
 *       exposure:
 *         include: health,metrics,prometheus,info
 *   metrics:
 *     export:
 *       prometheus:
 *         enabled: true
 *     tags:
 *       application: ${spring.application.name}
 *       environment: ${ENVIRONMENT:local}
 *   tracing:
 *     sampling:
 *       probability: 0.1   # 10% in prod; 1.0 in dev
 */
public class ObservabilityConfig {

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM BUSINESS METRICS
    // ─────────────────────────────────────────────────────────────────────────

    @Component
    public static class OrderMetrics {
        private final Counter ordersCreated;
        private final Counter ordersFailed;
        private final Timer orderProcessingTime;
        private final DistributionSummary orderValueDistribution;
        private final Gauge activeOrdersGauge;

        private volatile int activeOrders = 0;

        public OrderMetrics(MeterRegistry registry) {
            // Counter — monotonically increasing
            this.ordersCreated = Counter.builder("orders.created")
                    .description("Total orders successfully created")
                    .tag("source", "api")
                    .register(registry);

            this.ordersFailed = Counter.builder("orders.failed")
                    .description("Total order creation failures")
                    .register(registry);

            // Timer — tracks count, sum, max, percentiles
            this.orderProcessingTime = Timer.builder("orders.processing.duration")
                    .description("Time to process an order end-to-end")
                    .publishPercentileHistogram()    // enables Prometheus quantile queries
                    .percentilePrecision(2)
                    .register(registry);

            // Distribution summary — for non-time values
            this.orderValueDistribution = DistributionSummary.builder("orders.value.dollars")
                    .description("Distribution of order value in dollars")
                    .baseUnit("dollars")
                    .publishPercentileHistogram()
                    .scale(0.01)  // pennies → dollars
                    .register(registry);

            // Gauge — reflects current state
            this.activeOrdersGauge = Gauge.builder("orders.active", this, m -> m.activeOrders)
                    .description("Number of orders currently being processed")
                    .register(registry);
        }

        public void recordOrderCreated(long valueInCents) {
            ordersCreated.increment();
            orderValueDistribution.record(valueInCents);
        }

        public void recordOrderFailed() {
            ordersFailed.increment();
        }

        public void setActiveOrders(int count) {
            this.activeOrders = count;
        }

        // Wrap a block and record its duration
        public <T> T timeOrder(java.util.function.Supplier<T> supplier) {
            activeOrders++;
            try {
                return orderProcessingTime.record(supplier);
            } finally {
                activeOrders--;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STRUCTURED LOGGING WITH TRACE CORRELATION
    // ─────────────────────────────────────────────────────────────────────────

    @Service
    public static class OrderService {
        private static final Logger log = LoggerFactory.getLogger(OrderService.class);
        private final ObservationRegistry observationRegistry;
        private final OrderMetrics metrics;

        public OrderService(ObservationRegistry observationRegistry, OrderMetrics metrics) {
            this.observationRegistry = observationRegistry;
            this.metrics = metrics;
        }

        public String createOrder(CreateOrderCommand cmd) {
            // Observation API: automatically adds span + trace IDs to logs and exports traces
            return Observation.createNotStarted("order.creation", observationRegistry)
                    .lowCardinalityKeyValue("order.source", cmd.source())
                    .highCardinalityKeyValue("customer.id", cmd.customerId())
                    .observe(() -> {
                        log.info("Creating order for customer={} items={}",
                                cmd.customerId(), cmd.items());
                        // traceId + spanId are automatically injected by Micrometer Tracing:
                        // {"timestamp":"...","level":"INFO","message":"Creating order...",
                        //  "traceId":"abc123","spanId":"def456","customerId":"..."}

                        String orderId = metrics.timeOrder(() -> {
                            // Simulate order processing
                            sleep(50);
                            return "order-" + System.currentTimeMillis();
                        });

                        metrics.recordOrderCreated(cmd.totalCents());
                        log.info("Order created orderId={}", orderId);
                        return orderId;
                    });
        }

        private void sleep(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    record CreateOrderCommand(String customerId, java.util.List<String> items, long totalCents, String source) {}

    // ─────────────────────────────────────────────────────────────────────────
    // CUSTOM HEALTH INDICATOR
    // ─────────────────────────────────────────────────────────────────────────

    @Component
    public static class KafkaHealthIndicator
            implements org.springframework.boot.actuate.health.HealthIndicator {

        @Override
        public org.springframework.boot.actuate.health.Health health() {
            try {
                // In a real implementation: check Kafka connectivity
                boolean kafkaReachable = checkKafka();
                if (kafkaReachable) {
                    return org.springframework.boot.actuate.health.Health.up()
                            .withDetail("brokers", "kafka:9092")
                            .withDetail("topic", "orders")
                            .build();
                } else {
                    return org.springframework.boot.actuate.health.Health.down()
                            .withDetail("error", "Cannot reach Kafka broker")
                            .build();
                }
            } catch (Exception e) {
                return org.springframework.boot.actuate.health.Health.down(e).build();
            }
        }

        private boolean checkKafka() {
            return true; // demo stub: replace with an AdminClient or broker metadata check
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ALERT RULES (for documentation — these go in Prometheus/Alertmanager)
    // ─────────────────────────────────────────────────────────────────────────

    /*
     * Prometheus alert rules (alerts.yml):
     *
     * groups:
     * - name: order-service
     *   rules:
     *   - alert: HighOrderErrorRate
     *     expr: rate(orders_failed_total[5m]) / rate(orders_created_total[5m]) > 0.05
     *     for: 2m
     *     labels:
     *       severity: critical
     *     annotations:
     *       summary: "Order error rate > 5% for 2 minutes"
     *
     *   - alert: HighP99Latency
     *     expr: histogram_quantile(0.99, rate(orders_processing_duration_seconds_bucket[5m])) > 2
     *     for: 5m
     *     labels:
     *       severity: warning
     *     annotations:
     *       summary: "P99 order processing latency > 2s"
     */
}
