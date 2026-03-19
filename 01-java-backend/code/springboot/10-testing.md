# Spring Boot Testing — From Unit to Integration

---

## 1. The Testing Pyramid

```
         ╔══════╗
         ║  E2E ║  ← few, slow, expensive (full environment)
        ╔╦══════╦╗
        ║ Contract ║  ← consumer-driven contracts (Pact)
       ╔╬══════════╬╗
       ║ Integration ║  ← @SpringBootTest, Testcontainers
      ╔╬════════════╬╗
      ║   Slice Tests ║  ← @WebMvcTest, @DataJpaTest
     ╔╬══════════════╬╗
     ║   Unit Tests   ║  ← fast, isolated, many
    ╚╩════════════════╩╝
```

| Layer | Tool | Speed | Confidence |
|---|---|---|---|
| Unit | JUnit 5 + Mockito | Very fast | Logic only |
| Slice | @WebMvcTest, @DataJpaTest | Fast | Single layer |
| Integration | @SpringBootTest + Testcontainers | Slow | Real components |
| Contract | Pact | Medium | API compatibility |
| E2E | Selenium / Playwright | Very slow | Full user flows |

---

## 2. JUnit 5 Fundamentals

```java
// Core annotations
@Test                  // marks a test method
@DisplayName("...")    // human-readable test name (shown in IDE + reports)
@BeforeEach            // runs before EACH test method in the class
@AfterEach             // runs after EACH test method
@BeforeAll             // runs ONCE before all tests — static method (or @TestInstance(Lifecycle.PER_CLASS))
@AfterAll              // runs ONCE after all tests — static method
@Disabled("reason")    // skip this test
@Tag("slow")           // for filtering tests in CI

// Assertions
assertThat(result).isEqualTo(expected);          // AssertJ (preferred over JUnit assertions)
assertThat(list).hasSize(3).contains("apple");
assertThat(ex).isInstanceOf(BadRequestException.class)
              .hasMessage("Invalid order id");

// JUnit 5 assertAll — group multiple assertions, all run even if first fails
assertAll("order fields",
    () -> assertThat(order.getId()).isNotNull(),
    () -> assertThat(order.getStatus()).isEqualTo(Status.PENDING),
    () -> assertThat(order.getItems()).hasSize(3)
);

// assertThrows — verify exception type and message
Exception ex = assertThrows(IllegalArgumentException.class,
    () -> service.createOrder(invalidRequest)
);
assertThat(ex.getMessage()).contains("quantity must be positive");

// @ParameterizedTest — same test logic, multiple inputs
@ParameterizedTest
@ValueSource(strings = {"", " ", "  "})  // blank strings
void shouldRejectBlankName(String name) {
    assertThrows(ValidationException.class, () -> service.validate(name));
}

@ParameterizedTest
@CsvSource({"1,apple,2.5", "2,banana,1.0", "3,cherry,5.0"})
void shouldCalculatePrice(int qty, String item, double expectedPrice) {
    assertThat(service.price(qty, item)).isEqualTo(expectedPrice);
}

@ParameterizedTest
@MethodSource("invalidOrders")
void shouldRejectInvalidOrder(Order order) { ... }
static Stream<Arguments> invalidOrders() {
    return Stream.of(Arguments.of(nullIdOrder), Arguments.of(zeroQuantityOrder));
}

// @Nested — group related tests (improves readability and @BeforeEach scoping)
class OrderServiceTest {
    @Nested
    class WhenOrderIsValid {
        @BeforeEach void setup() { /* ... */ }
        @Test void shouldCreateOrder() { ... }
        @Test void shouldSendConfirmation() { ... }
    }
    @Nested
    class WhenOrderIsInvalid {
        @Test void shouldRejectNullUserId() { ... }
    }
}
```

---

## 3. Mockito — Isolating Dependencies

```java
@ExtendWith(MockitoExtension.class) // inject @Mock and @InjectMocks automatically
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepo;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private OrderService orderService; // dependencies injected from @Mock fields

    @Test
    void shouldCreateOrder() {
        // Arrange
        Order order = new Order("user-1", List.of(new Item("apple", 2)));
        when(orderRepo.save(any(Order.class))).thenReturn(order.withId(1L));

        // Act
        Order created = orderService.createOrder(order);

        // Assert
        assertThat(created.getId()).isEqualTo(1L);
        verify(orderRepo).save(order);                      // was save() called once?
        verify(notificationService).sendConfirmation(1L);   // was notification sent?
        verifyNoMoreInteractions(notificationService);       // no other calls
    }

    @Test
    void shouldHandleRepositoryFailure() {
        when(orderRepo.save(any())).thenThrow(new DataAccessException("DB down") {});

        assertThrows(ServiceException.class,
            () -> orderService.createOrder(new Order("user-1", List.of())));
    }

    // Capture what was passed to a mock
    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    @Test
    void shouldSetTimestampBeforeSave() {
        orderService.createOrder(new Order("user-1", List.of()));
        verify(orderRepo).save(orderCaptor.capture());

        Order saved = orderCaptor.getValue();
        assertThat(saved.getCreatedAt()).isNotNull();  // verify side effect
    }

    // @Spy — partial mock (real object, spy on specific methods)
    @Spy
    private PricingEngine pricingEngine = new PricingEngine(config);

    @Test
    void shouldUsePricingEngine() {
        doReturn(BigDecimal.TEN).when(pricingEngine).calculatePrice(any()); // override specific method
        // All other PricingEngine methods run real logic
    }
}
```

---

## 4. `@SpringBootTest` — Full Application Context

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiIntegrationTest {

    @LocalServerPort
    private int port; // the random port the embedded server started on

    @Autowired
    private TestRestTemplate restTemplate; // auto-configured for this port

    @Test
    void shouldCreateOrder() {
        OrderRequest req = new OrderRequest("user-1", List.of(new Item("apple", 2)));

        ResponseEntity<Order> response = restTemplate.postForEntity(
            "/api/v1/orders", req, Order.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isNotNull();
    }
}

// WebEnvironment options:
// RANDOM_PORT   — starts real embedded server on random port
// DEFINED_PORT  — starts on server.port (default 8080)
// MOCK          — no real HTTP server, use MockMvc
// NONE          — no web server (useful for service layer testing)

// Replacing beans in test context
@SpringBootTest
class ServiceTest {
    @MockBean
    private PaymentGateway paymentGateway; // replaces the real bean in the context with a Mockito mock

    @Autowired
    private OrderService orderService; // gets the real OrderService with mocked PaymentGateway
}
```

---

## 5. `@WebMvcTest` — Controller Slice

Only loads the web layer (controllers, filters, `@ControllerAdvice`) — no services or repositories in context:

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean                         // must mock all @Autowired service dependencies
    private OrderService orderService;

    @MockBean
    private AuthenticationManager authManager;

    @Test
    void shouldReturn201WhenOrderCreated() throws Exception {
        Order saved = new Order(1L, "user-1", Status.PENDING);
        when(orderService.create(any())).thenReturn(saved);

        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"userId": "user-1", "items": [{"name": "apple", "qty": 2}]}
                    """)
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturn400WhenBodyInvalid() throws Exception {
        mockMvc.perform(
            post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": null}")  // fails @NotNull validation
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation failed"));
    }
}
```

---

## 6. `@DataJpaTest` — JPA Slice

Only loads JPA repositories, entity classes, and an in-memory H2 database (no full application context):

```java
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager em;     // direct entity manager for test setup

    @Autowired
    private OrderRepository orderRepo;

    @Test
    void shouldFindPendingOrdersByUser() {
        // Arrange: insert test data
        User user = em.persist(new User("user-1", "alice@example.com"));
        em.persist(new Order(user, Status.PENDING, "2024-01-01"));
        em.persist(new Order(user, Status.COMPLETED, "2024-01-02"));
        em.flush();  // flush to DB, clear cache
        em.clear();

        // Act
        List<Order> result = orderRepo.findByUserIdAndStatus("user-1", Status.PENDING);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    @Sql("/test-data/orders.sql")  // run SQL script before this test
    void shouldReturnTopOrdersByValue() {
        List<Order> orders = orderRepo.findTopByOrderByValueDesc(PageRequest.of(0, 5));
        assertThat(orders).hasSize(5);
        assertThat(orders.get(0).getValue()).isGreaterThanOrEqualTo(orders.get(1).getValue());
    }
}

// To use real DB instead of H2:
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class OrderRepositoryRealDbTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    // ...
}
```

---

## 7. Testcontainers — Real Dependencies in Tests

```java
@SpringBootTest
@Testcontainers
class FullIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         postgres::getJdbcUrl);
        registry.add("spring.datasource.username",    postgres::getUsername);
        registry.add("spring.datasource.password",    postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host",        redis::getHost);
        registry.add("spring.data.redis.port",        () -> redis.getMappedPort(6379));
    }

    @Test
    void shouldProcessOrderEndToEnd() {
        // Real PostgreSQL, real Kafka, real Redis — no mocking!
    }
}
```

**Performance tip**: declare containers `static` and they are shared across all tests in the class (started once). Use `@SpringBootTest` context sharing to avoid starting Spring context multiple times.

---

## 8. WireMock — Mock External HTTP APIs

```java
// pom.xml: com.github.tomakehurst:wiremock-jre8:2.35.2
@SpringBootTest
class PaymentServiceTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().port(8090))
        .build();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("payment.gateway.url", () -> "http://localhost:8090");
    }

    @Test
    void shouldHandlePaymentSuccess() {
        wireMock.stubFor(
            post(urlEqualTo("/payments"))
                .withRequestBody(matchingJsonPath("$.amount"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"transactionId": "txn-123", "status": "APPROVED"}"""))
        );

        PaymentResult result = paymentService.processPayment(new PaymentRequest(100.0, "user-1"));

        assertThat(result.getTransactionId()).isEqualTo("txn-123");
        wireMock.verify(postRequestedFor(urlEqualTo("/payments")));
    }

    @Test
    void shouldRetryOnServiceUnavailable() {
        wireMock.stubFor(
            post(urlEqualTo("/payments"))
                .inScenario("retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second-attempt")
        );
        wireMock.stubFor(
            post(urlEqualTo("/payments"))
                .inScenario("retry")
                .whenScenarioStateIs("second-attempt")
                .willReturn(aResponse().withStatus(200).withBody("""{"status":"APPROVED"}"""))
        );

        PaymentResult result = paymentService.processPayment(new PaymentRequest(100.0, "user-1"));
        assertThat(result.getStatus()).isEqualTo("APPROVED");
        wireMock.verify(2, postRequestedFor(urlEqualTo("/payments"))); // called twice (retry worked)
    }
}
```

---

## 9. Shared Context and `@DirtiesContext`

Spring caches application contexts between tests — starting the full context is slow (1-5s). Tests in the same JVM that use the same configuration share the same context:

```java
// Best practice: share a base test class
@SpringBootTest
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {
    // Common @MockBean, @Autowired go here — shared across all subclasses
}

class OrderServiceTest extends BaseIntegrationTest { ... }
class UserServiceTest extends BaseIntegrationTest { ... }
// SAME Spring context is reused — fast!

// @DirtiesContext — marks context as dirty (needs restart after this test)
// Use ONLY when a test modifies global state that can't be restored otherwise
@Test
@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD) // restart after this test
void shouldModifyGlobalConfiguration() { ... }

// Context restart is expensive — avoid @DirtiesContext by:
// - Using @Transactional (test-level transaction rolls back after each test)
// - Resetting mock state in @AfterEach
// - Using @Sql + @Sql(executionPhase=AFTER_TEST_METHOD) cleanup scripts
```

---

## 10. Test Strategy Best Practices

```java
// @Transactional in test — rolls back after each test method (DB is clean for next test)
@DataJpaTest  // implies @Transactional by default
@SpringBootTest
@Transactional // add this for service layer tests touching DB
class OrderServiceIntegrationTest {
    @Test
    void shouldCreateAndRetrieveOrder() {
        Order created = orderService.createOrder(request);
        Order found   = orderService.findById(created.getId());
        assertThat(found).isEqualTo(created);
    }
    // After test: @Transactional rolls back — DB is pristine for next test
}
```

**Test naming conventions**:
```java
// Pattern: should<ExpectedBehavior>When<Condition>
@Test void shouldReturn404WhenOrderNotFound() { ... }
@Test void shouldThrowWhenQuantityIsZero() { ... }
@Test void shouldSendEmailAfterOrderCreated() { ... }
```

---

## 11. Interview Q&A

**Q: What is the difference between `@MockBean` and `@Mock` in Spring tests?**  
`@Mock` (Mockito) creates a mock object in a plain Java context — it has no knowledge of Spring. Use it with `@ExtendWith(MockitoExtension.class)` for pure unit tests where you wire dependencies manually. `@MockBean` (Spring) creates a Mockito mock AND registers it in the Spring application context, replacing the real bean. Use it in `@SpringBootTest` or `@WebMvcTest` when a bean in the context has a dependency that should be mocked.

**Q: Why does `@WebMvcTest` start up faster than `@SpringBootTest`?**  
`@WebMvcTest` is a slice test — it only loads web-layer beans: controllers, `ControllerAdvice`, `Filter`, `HandlerInterceptor`, `Jackson` converters, and Spring MVC configuration. It does NOT load `@Service`, `@Repository`, `@Component`, or any infrastructure beans (JPA, security full config, Kafka, etc.). This makes the context much smaller and faster to start. All dependencies of controllers must be provided via `@MockBean`. Use it when you want to test controller logic and HTTP behavior in isolation.

**Q: What are Testcontainers and when should you use them over H2?**  
Testcontainers starts real third-party services (PostgreSQL, Kafka, Redis, Elasticsearch) in Docker containers for tests. Use them when: (1) your SQL uses PostgreSQL-specific features not supported by H2 (JSON columns, arrays, window functions, SKIP LOCKED), (2) you need to test Kafka consumer/producer logic, (3) you need Redis with Lua scripts or specific data structures. Use H2 when: tests are database-agnostic, speed is critical, and you want to avoid Docker overhead. The rule of thumb: H2 for `@DataJpaTest` CRUD queries, Testcontainers for anything PostgreSQL-specific or involving infrastructure.
