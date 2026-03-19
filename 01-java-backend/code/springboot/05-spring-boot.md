# Spring Boot Deep Dive

## 1. Application Context & Bean Lifecycle

### Startup sequence

```
1. SpringApplication.run()
2. Load ApplicationContext (AnnotationConfigApplicationContext / 
   AnnotationConfigServletWebServerApplicationContext)
3. Scan @Component, @Service, @Repository, @Controller, @Configuration
4. Process @Bean factory methods
5. Resolve and inject dependencies
6. PostProcessor hooks (BeanPostProcessor, BeanFactoryPostProcessor)
7. @PostConstruct methods run
8. ApplicationReadyEvent fired
9. Embedded server listening
```

### Bean lifecycle hooks

```java
@Component
public class DatabasePool {

    @PostConstruct
    public void init() {
        // runs AFTER injection, BEFORE serving requests
        // use for: connection validation, cache warm-up
    }

    @PreDestroy
    public void cleanup() {
        // runs BEFORE context shutdown
        // use for: close connections, flush buffers
    }
}
```

### Bean scopes

| Scope | Lifetime | Suitable for |
|---|---|---|
| `singleton` (default) | One per ApplicationContext | Stateless services, repos |
| `prototype` | New instance per injection | Stateful objects, heavy init |
| `request` | One per HTTP request | Request-scoped state (web) |
| `session` | One per HTTP session | User session data |

---

## 2. Dependency Injection

### Preferred: constructor injection

```java
@Service
public class OrderService {
    private final OrderRepository repo;
    private final PaymentGateway gateway;

    // @Autowired is optional when single constructor (Spring 4.3+)
    public OrderService(OrderRepository repo, PaymentGateway gateway) {
        this.repo = repo;
        this.gateway = gateway;
    }
}
```

Why constructor injection:
- Fields are `final` — immutable, safe.
- Works without Spring (unit tests with `new OrderService(mockRepo, mockGateway)`).
- Fails fast at startup if beans missing (no `NullPointerException` at runtime).

### Qualifier and Primary

```java
@Bean @Primary
public PaymentGateway stripeGateway() { return new StripeGateway(); }

@Bean
public PaymentGateway paypalGateway() { return new PaypalGateway(); }

// In consumer:
@Autowired @Qualifier("paypalGateway")
private PaymentGateway gateway;
```

---

## 3. Auto-Configuration

Spring Boot's magic: `@EnableAutoConfiguration` reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3.x) and registers conditional beans.

```java
// How it works internally:
@Configuration
@ConditionalOnClass(DataSource.class)       // only if JPA is on classpath
@ConditionalOnMissingBean(DataSource.class) // only if user didn't define one
@EnableConfigurationProperties(DataSourceProperties.class)
public class DataSourceAutoConfiguration { ... }
```

### Debugging auto-config

```bash
# Run with debug flag
java -jar app.jar --debug

# Or in application.properties:
debug=true
```

Spring Boot prints "Positive Matches" (applied), "Negative Matches" (skipped), and "Exclusions".

---

## 4. Configuration & Profiles

### application.yml hierarchy

```yaml
# application.yml (base)
server:
  port: 8080
spring:
  datasource:
    url: jdbc:h2:mem:testdb

---
# application-prod.yml (overrides for prod profile)
spring:
  datasource:
    url: jdbc:postgresql://prod-db:5432/orders
    username: ${DB_USER}          # from env var
    password: ${DB_PASS}
  config:
    activate:
      on-profile: prod
```

### @ConfigurationProperties (type-safe binding)

```java
@ConfigurationProperties(prefix = "app.rate-limit")
@Validated
public record RateLimitConfig(
    @Min(1) int requestsPerSecond,
    @Min(0) int burstCapacity,
    Duration windowDuration
) {}

// application.yml
// app:
//   rate-limit:
//     requests-per-second: 100
//     burst-capacity: 200
//     window-duration: PT1S
```

---

## 5. REST Controllers & Validation

```java
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.create(request);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return orderService.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(OrderNotFoundException ex) {
        return new ErrorResponse("ORDER_NOT_FOUND", ex.getMessage());
    }
}
```

### Validation annotations

```java
public record CreateOrderRequest(
    @NotNull UUID customerId,
    @NotEmpty List<@Valid OrderItem> items,
    @NotBlank String shippingAddress,
    @PositiveOrZero BigDecimal discount
) {}
```

### Global exception handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(toList());
        return new ErrorResponse("VALIDATION_FAILED", errors);
    }
}
```

---

## 6. Spring Data JPA

```java
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    // Derived query (JPA generates SQL)
    List<Order> findByCustomerIdAndStatusOrderByCreatedAtDesc(
        UUID customerId, OrderStatus status
    );

    // JPQL — explicit
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :since AND o.total > :minAmount")
    List<Order> findRecentLargeOrders(
        @Param("since") Instant since,
        @Param("minAmount") BigDecimal minAmount
    );

    // Pagination
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
}
```

```java
// Usage
orderService.findByCustomer(customerId, PageRequest.of(0, 20, Sort.by("createdAt").descending()));
```

---

## 7. Transactions

```java
@Transactional
public Order createOrder(CreateOrderRequest req) {
    Order order = orderRepository.save(new Order(req));
    inventoryService.reserve(req.items()); // within same transaction
    eventOutbox.publish(new OrderCreatedEvent(order.getId())); // same transaction
    return order;
}
```

### Propagation types

| Propagation | Behavior |
|---|---|
| `REQUIRED` (default) | Join existing transaction or start new |
| `REQUIRES_NEW` | Always start new, suspend outer |
| `SUPPORTS` | Join if exists, no transaction if not |
| `NOT_SUPPORTED` | Suspend any existing, run without |
| `NEVER` | Throw exception if transaction exists |
| `MANDATORY` | Throw exception if NO transaction |
| `NESTED` | Savepoint within existing |

### Transaction pitfalls

- `@Transactional` on `private` methods has no effect (Spring wraps with a proxy — can't proxy what it can't override).
- `@Transactional` on a method called from within the same class bypasses the proxy — no transaction. Extract to a separate bean or use `ApplicationContext.getBean()` to self-inject.
- Lazy-loading outside transaction → `LazyInitializationException`. Fix: use `@Transactional` on the service or use DTOs.

---

## 8. Actuator & Observability

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

```java
// Custom metric
@Component
public class OrderMetrics {
    private final Counter orderCounter;
    private final Timer orderTimer;

    public OrderMetrics(MeterRegistry registry) {
        this.orderCounter = registry.counter("orders.created",
            "status", "success");
        this.orderTimer = registry.timer("orders.processing.duration");
    }

    public void recordOrder(Runnable action) {
        orderTimer.record(action);
        orderCounter.increment();
    }
}
```

---

## 9. Interview Q&A

**Q: What is the difference between @Component, @Service, @Repository, @Controller?**  
All are specializations of `@Component` and trigger component scanning. `@Repository` adds automatic exception translation (converting JPA/JDBC exceptions to Spring's `DataAccessException` hierarchy). `@Service` has no extra behavior — it's a semantic marker for business logic. `@Controller` marks an HTTP handler. The distinction matters for readability and for `BeanPostProcessors` that look for specific annotations (like `@Repository`'s exception translator).

**Q: How does @Transactional work internally?**  
Spring wraps the annotated bean in a CGLIB proxy. Before the method executes, the proxy starts a transaction (or joins an existing one per propagation rules). If the method completes normally, the proxy commits. If an unchecked exception (or any exception listed in `rollbackFor`) is thrown, it rolls back. This proxy mechanism is why `@Transactional` has no effect on private methods or self-invocations.

**Q: What is the N+1 query problem and how do you fix it?**  
When fetching a list of N entities with a lazily-loaded relationship, accessing that relationship triggers one additional query per entity — N+1 total queries. Fix: use a JOIN FETCH in JPQL (`SELECT o FROM Order o JOIN FETCH o.items WHERE o.customerId = :id`), or `@EntityGraph`, or annotate the relationship with `FetchType.EAGER` (with care — can cause over-fetching). In Spring Data, for projections use DTO queries with native SQL or `@Query` with constructor expressions.
