# springboot — Spring Boot, Microservices & Production Patterns

> **Learning path (7 of 8):** `1. 00-foundations` → `2. collections` → `3. concurrency` → `4. jvm` → `5. performance` → `6. linux-networking` → **`7. springboot`** → `8. testing-delivery`

Apply all the Java fundamentals in real service construction: REST design, JPA, security, resilience, messaging, observability, and caching.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [05-spring-boot.md](05-spring-boot.md) | Auto-configuration, conditional beans, starters, profiles, externalized config, actuator endpoints |
| 2 | [06-microservices-patterns.md](06-microservices-patterns.md) | Service-to-service communication, Saga, Circuit Breaker, Bulkhead, Sidecar, service mesh basics |
| 3 | [07-rest-api-design.md](07-rest-api-design.md) | REST constraints, versioning strategies, idempotency, error response format, OpenAPI/Swagger |
| 4 | [08-database-jpa.md](08-database-jpa.md) | JPA, Hibernate session, transaction management, N+1 problem, query tuning, Flyway migrations |
| 5 | [09-security.md](09-security.md) | Spring Security filter chain, OAuth2/OIDC, JWT validation, CORS, CSRF, method-level security |
| 6 | [10-testing.md](10-testing.md) | @SpringBootTest, slice annotations (@WebMvcTest, @DataJpaTest), Testcontainers, WireMock |
| 7 | [11-kafka-messaging.md](11-kafka-messaging.md) | Kafka producer/consumer with Spring, consumer groups, offset management, dead-letter topics |
| 8 | [12-observability.md](12-observability.md) | Micrometer metrics, distributed tracing (OpenTelemetry), structured logging, alerting patterns |
| 9 | [13-caching-redis.md](13-caching-redis.md) | Spring Cache abstraction, Redis data structures, TTL strategy, cache-aside vs write-through |
| 10 | [ResiliencePatterns.java](ResiliencePatterns.java) | Circuit breaker, bulkhead, retry, and timeout implemented with Resilience4j |
| 11 | [SecurityConfig.java](SecurityConfig.java) | Full Spring Security config: JWT filter chain, CORS, method security |
| 12 | [ObservabilityConfig.java](ObservabilityConfig.java) | Micrometer + OpenTelemetry wiring, custom meters, trace propagation |
| 13 | [CachingRedisExamples.java](CachingRedisExamples.java) | Redis template operations, Spring Cache annotations, TTL and eviction examples |

## How this fits in the bigger picture

```
00-foundations/  concurrency/  jvm/  performance/
                  ↓
springboot/      ← YOU ARE HERE — everything comes together here
testing-delivery/← write tests and ship safely after building here
```

## Study method

1. Read files 1–3 (Spring Boot + microservices + REST) before writing any Spring code.
2. Stand up a Spring Boot project with Docker Compose (Postgres + Redis + Kafka) while reading files 4–9.
3. Implement each Java example class yourself from scratch — start with `ResiliencePatterns.java`.
4. Add a failing integration test before every new feature; use Testcontainers (file 6) so tests are self-contained.
5. Explain the Spring Security filter chain order and where JWT validation should live, out loud.
