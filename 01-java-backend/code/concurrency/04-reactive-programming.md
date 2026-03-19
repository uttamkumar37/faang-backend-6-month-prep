# Reactive Programming — Project Reactor and Spring WebFlux

---

## 1. Why Reactive? The Thread-Per-Request Problem

In a traditional Spring MVC app, each HTTP request occupies a thread from start to finish:

```
Request → Thread-1 acquired
            │── DB query      (Thread-1 BLOCKED, waiting for DB — 20ms)
            │── External API  (Thread-1 BLOCKED, waiting for network — 50ms)
            └── JSON serialize
          Thread-1 released
```

With 200 concurrent requests, you need 200 threads. Default Tomcat has ~200 threads. At 201 requests: queue latency spikes. Each thread requires ~1MB of stack.

**Reactive = Non-blocking I/O + Event-loop:**
```
Request → EventLoop
            │── initiate DB query    (returns immediately, callback registered)
            │── EventLoop handles other requests (thread is FREE)
            │── DB result arrives → EventLoop resumes this request
            └── JSON serialize → write response
```

Same event-loop thread handles thousands of requests. No blocking = no idle threads.

---

## 2. Reactive Streams Specification

Four interfaces form the contract (same in Java 9's `java.util.concurrent.Flow`):

```java
// 1. Publisher: produces data
public interface Publisher<T> {
    void subscribe(Subscriber<? super T> subscriber);
}

// 2. Subscriber: consumes data
public interface Subscriber<T> {
    void onSubscribe(Subscription s);   // called first — allows requesting items
    void onNext(T item);                // called for each item
    void onError(Throwable t);          // terminal: error
    void onComplete();                  // terminal: no more items
}

// 3. Subscription: link between publisher and subscriber (backpressure!)
public interface Subscription {
    void request(long n);   // subscriber requests n items (backpressure signal)
    void cancel();          // subscriber cancels the subscription
}

// 4. Processor: both Publisher and Subscriber (transformation stage)
public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {}
```

**Backpressure** = subscriber controls flow rate. If subscriber calls `request(1)`, publisher sends only 1 item. This prevents fast publishers from overwhelming slow subscribers.

---

## 3. Project Reactor — Mono and Flux

Reactor is the reactive library used by Spring WebFlux. It provides two publishers:

```java
// Mono<T>: 0 or 1 item, then complete or error
// Like: Optional + Future combined
Mono<User> user = userRepository.findById(42L); // async, non-blocking
Mono<Void> result = service.sendEmail("x@y.com"); // completes with no item

// Flux<T>: 0 to N items, then complete or error
// Like: Stream + Future combined
Flux<User> users = userRepository.findAll();
Flux<ServerSentEvent<String>> events = Flux.interval(Duration.ofSeconds(1))
    .map(i -> ServerSentEvent.builder("ping " + i).build());
```

### Creating Publishers

```java
// Mono factories
Mono.just("hello")              // immediate value
Mono.empty()                    // completes immediately with no item
Mono.error(new RuntimeException("failed"))  // immediate error
Mono.never()                    // never completes (for testing)
Mono.fromCallable(() -> expensiveComputation())     // lazy: executes on subscribe
Mono.fromFuture(completableFuture)                  // wraps CompletableFuture
Mono.fromSupplier(() -> value)
Mono.defer(() -> Mono.just(nowTime()))  // evaluated LAZILY on each subscribe

// Flux factories
Flux.just(1, 2, 3)
Flux.fromIterable(List.of("a", "b", "c"))
Flux.fromStream(Stream.of("x", "y"))
Flux.fromArray(new String[]{"a","b"})
Flux.range(1, 10)                        // Flux<Integer>: 1..10
Flux.interval(Duration.ofSeconds(1))     // Flux<Long>: 0,1,2,... every second
Flux.empty()

// Programmatic creation:
Flux<String> flux = Flux.generate(
    () -> 0,                             // initial state
    (state, sink) -> {
        sink.next("value " + state);     // emit one item
        if (state == 9) sink.complete(); // signal done
        return state + 1;               // next state
    }
);

// Flux.create — for async multi-threaded emission
Flux<String> fromCallback = Flux.create(sink -> {
    eventBus.register(event -> sink.next(event.getData()));
    eventBus.register(EndEvent.class, e -> sink.complete());
});
```

---

## 4. Nothing Happens Until You Subscribe

```java
Mono<User> mono = userRepo.findById(1L)    // NO DB CALL YET
    .map(User::getName)                     // NO EXECUTION YET
    .doOnNext(name -> log.info("Found: {}", name)); // not called yet

// Only when subscribe() is called does execution begin:
mono.subscribe(
    name -> System.out.println("Result: " + name),  // onNext
    error -> System.err.println("Error: " + error), // onError
    () -> System.out.println("Done!")               // onComplete
);

// Missing subscribe = missing data. This is the #1 reactive bug.
// Spring WebFlux handles subscribe automatically when you return Mono/Flux from a controller.
```

### Cold vs Hot Publishers

```java
// Cold: each subscriber gets an independent execution from the start
Mono<User> cold = Mono.fromCallable(() -> repo.findById(1L));
cold.subscribe(u -> log.info("A: {}", u.getName())); // one DB call
cold.subscribe(u -> log.info("B: {}", u.getName())); // ANOTHER DB call

// Hot: shared single execution; late subscribers miss past events
Flux<String> hotSource = Flux.interval(Duration.ofMillis(500))
    .map(i -> "event-" + i)
    .share(); // share() makes it hot: multicasts to all current subscribers
hotSource.subscribe(e -> log.info("sub1: {}", e));
Thread.sleep(1000);
hotSource.subscribe(e -> log.info("sub2: {}", e)); // misses first 2 events
```

---

## 5. Operators — Transforming Reactive Pipelines

### Transforming Values
```java
Flux.range(1, 5)
    .map(n -> n * 2)                    // sync transform: 2,4,6,8,10
    .filter(n -> n > 4)                 // keep: 6,8,10
    .take(2)                            // take first 2: 6,8
    .subscribe(System.out::println);

// flatMap: async transform → returns Publisher per element (concurrent!)
Flux<Order> orders = orderService.getAllOrders();
Flux<OrderDetails> details = orders.flatMap(order ->      // starts ALL concurrently
    enrichmentService.enrich(order)      // returns Mono<OrderDetails>
);

// concatMap: like flatMap but sequential — waits for each inner to complete
Flux<OrderDetails> sequential = orders.concatMap(order ->
    enrichmentService.enrich(order)      // one at a time, order preserved
);

// flatMap vs concatMap:
// flatMap: higher throughput (concurrent), may reorder results
// concatMap: ordered results, lower throughput (sequential)
// switchMap: cancels in-flight inner when new outer emits (good for autocomplete)
```

### Combining Multiple Publishers
```java
// zip: combine items by position (shortest wins)
Flux<String> names = Flux.just("Alice", "Bob");
Flux<Integer> ages  = Flux.just(30, 25);
Flux<String> combined = Flux.zip(names, ages, (n, a) -> n + "/" + a);
// combined: "Alice/30", "Bob/25"

// merge: interleave emissions as they arrive (concurrent)
Flux<Integer> merged = Flux.merge(
    Flux.interval(Duration.ofMillis(10)).take(3),
    Flux.interval(Duration.ofMillis(15)).take(3)
); // order depends on timing

// concat: sequential — waits for first to complete, then subscribes to second
Flux<Integer> sequential = Flux.concat(flux1, flux2);

// Mono.zip: combine results of multiple monos
Mono<Result> result = Mono.zip(
    userService.getUser(id),
    orderService.getOrders(id),
    notificationService.getPending(id)
).map(tuple -> new Result(tuple.getT1(), tuple.getT2(), tuple.getT3()));
```

### Aggregating
```java
Flux.range(1, 5)
    .reduce(0, Integer::sum)           // Mono<Integer>: 15
    .subscribe(System.out::println);

Flux.range(1, 10)
    .buffer(3)                         // Flux<List<Integer>>: [1,2,3],[4,5,6],[7,8,9],[10]
    .subscribe(System.out::println);

Flux.range(1, 10)
    .window(3)                         // Flux<Flux<Integer>>: sub-fluxes of 3
    .flatMap(window -> window.reduce(0, Integer::sum)) // Flux<Integer>: sums of windows
    .subscribe(System.out::println);

Flux.range(1, 10)
    .collectList()                     // Mono<List<Integer>>: [1..10]
    .subscribe(System.out::println);

Flux.just("a","b","a","c","b","a")
    .groupBy(Function.identity())      // Flux<GroupedFlux<String, String>>
    .flatMap(group -> group.count().map(c -> group.key() + "=" + c))
    .subscribe(System.out::println);   // "a=3", "b=2", "c=1" (in some order)
```

---

## 6. Error Handling

```java
Mono<User> user = userService.findById(userId)
    .onErrorReturn(User.guest())                   // fallback value on any error
    .onErrorResume(NotFoundException.class,        // fallback publisher (type-specific)
        ex -> Mono.just(User.anonymous()))
    .onErrorMap(DatabaseException.class,           // transform error type
        ex -> new ServiceException("DB unavailable", ex))
    .doOnError(ex -> log.error("Error fetching user", ex)); // side effect, doesn't handle

// Retry
Flux<String> withRetry = externalApiFlux
    .retry(3)                                      // retry up to 3 times on any error
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100)) // exponential backoff
        .filter(ex -> ex instanceof TransientException) // only retry on transient errors
        .maxBackoff(Duration.ofSeconds(5))
    );

// Timeout
Mono<User> withTimeout = userService.findById(id)
    .timeout(Duration.ofSeconds(2))                // error if not completed in 2s
    .onErrorResume(TimeoutException.class, e ->
        Mono.just(User.fromCache(id)));            // fallback to cache on timeout
```

---

## 7. Schedulers — Thread Assignment

By default, reactive code runs on the calling thread (or the event loop). Schedulers control which thread pool runs each stage:

```java
// Scheduler types:
Schedulers.boundedElastic()   // for BLOCKING I/O — expandable thread pool (10× CPU), bounded max
Schedulers.parallel()          // for CPU-ONLY work — exactly Runtime.availableProcessors() threads
Schedulers.single()            // single reusable thread
Schedulers.immediate()         // current thread (no switch)
Schedulers.fromExecutor(exec)  // wrap any existing Executor
```

### `subscribeOn` vs `publishOn` — the crucial difference

```java
Flux.just("a", "b", "c")
    .doOnNext(s -> log.info("Source on: {}", Thread.currentThread().getName()))
    .subscribeOn(Schedulers.boundedElastic())  // affects: subscription + source execution
    .map(s -> s.toUpperCase())
    .doOnNext(s -> log.info("After map on: {}", Thread.currentThread().getName()))
    .publishOn(Schedulers.parallel())          // affects: everything DOWNSTREAM of this point
    .map(s -> s + "!")
    .subscribe(s -> log.info("Subscriber on: {}", Thread.currentThread().getName()));

// subscribeOn: "where should the subscribe() call happen (and thus the source emission)"
//   → affects the entire upstream chain (goes backwards up the chain)
//   → only the FIRST subscribeOn in a chain wins

// publishOn: "switch the thread for everything downstream"
//   → affects everything after it in the pipeline
//   → can appear multiple times to switch threads at different stages
```

### Dispatching Blocking Calls:
```java
// NEVER call blocking code on a parallel() scheduler — it starves the event loop!
// Wrap blocking calls in subscribeOn(Schedulers.boundedElastic()):
Mono<User> fromDb = Mono.fromCallable(() -> jdbcTemplate.queryForObject(...)) // BLOCKING call!
    .subscribeOn(Schedulers.boundedElastic()); // offload to elastic pool — safe
```

---

## 8. Context — Replacing ThreadLocal in Reactive

`ThreadLocal` doesn't work in reactive (same logical operation switches threads). Use `Context`:

```java
// Write to context upstream:
Mono<String> result = Mono.deferContextual(ctx -> {
    String userId = ctx.get("userId");           // read from context
    return userService.findByName(userId);
})
.contextWrite(Context.of("userId", "alice"));   // write to context (downstream won't see it)

// Practical: propagate security principal
Mono<User> current = ReactiveSecurityContextHolder.getContext()
    .map(SecurityContext::getAuthentication)
    .map(auth -> (User) auth.getPrincipal());
```

---

## 9. Backpressure

Backpressure is the mechanism a slow subscriber uses to resist a fast publisher:

```java
// Strategy 1: Buffer — store excess in memory (risk: OOM on very fast producers)
Flux.interval(Duration.ofMillis(1))     // emits very fast
    .onBackpressureBuffer(1000)          // buffer up to 1000 items
    .subscribe(i -> Thread.sleep(10));   // slow subscriber

// Strategy 2: Drop — silently discard items subscriber can't keep up with
Flux.interval(Duration.ofMillis(1))
    .onBackpressureDrop(dropped -> log.warn("Dropped: {}", dropped))
    .sample(Duration.ofMillis(10));     // sample once every 10ms

// Strategy 3: Latest — keep only most recent item
Flux.interval(Duration.ofMillis(1))
    .onBackpressureLatest()             // always process the most recent value

// Strategy 4: Error — throw error if subscriber falls behind
Flux.interval(Duration.ofMillis(1))
    .onBackpressureError();            // OverflowException if buffer fills
```

---

## 10. Spring WebFlux

WebFlux is the reactive web framework — it replaces Spring MVC for non-blocking scenarios:

```java
// Annotated controllers (same annotations as MVC, different return types)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository repository;  // R2DBC or MongoDB reactive repo

    @GetMapping("/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable Long id) {
        return repository.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Flux<User> listUsers() {
        return repository.findAll();           // streams as NDJSON or SSE
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> createUser(@RequestBody @Valid Mono<UserRequest> request) {
        return request
            .map(req -> new User(req.getName(), req.getEmail()))
            .flatMap(repository::save);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<User> streamUsers() {    // Server-Sent Events
        return repository.findAll()
            .delayElements(Duration.ofMillis(100));
    }
}

// Functional routing (alternative to annotations)
@Bean
public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
    return RouterFunctions.route()
        .GET("/api/users", handler::listAll)
        .GET("/api/users/{id}", handler::getById)
        .POST("/api/users", handler::create)
        .build();
}

@Component
public class UserHandler {
    public Mono<ServerResponse> getById(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return repository.findById(id)
            .flatMap(user -> ServerResponse.ok().bodyValue(user))
            .switchIfEmpty(ServerResponse.notFound().build());
    }
}
```

---

## 11. WebClient — Reactive HTTP Client

`WebClient` replaces `RestTemplate` for non-blocking HTTP calls:

```java
@Bean
public WebClient webClient() {
    return WebClient.builder()
        .baseUrl("https://api.example.com")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
}

// Making requests
Mono<User> user = webClient.get()
    .uri("/users/{id}", userId)
    .retrieve()
    .bodyToMono(User.class);

Flux<User> users = webClient.get()
    .uri("/users")
    .retrieve()
    .bodyToFlux(User.class);

// POST with body
Mono<Order> created = webClient.post()
    .uri("/orders")
    .bodyValue(orderRequest)
    .retrieve()
    .onStatus(HttpStatus::is4xxClientError, resp ->
        resp.bodyToMono(String.class).map(body -> new ClientException(body)))
    .bodyToMono(Order.class);

// Fan-out: call two services in parallel (Mono.zip)
Mono<DashboardData> dashboard = Mono.zip(
    webClient.get().uri("/users/{id}", id).retrieve().bodyToMono(UserProfile.class),
    webClient.get().uri("/orders?userId={id}", id).retrieve().bodyToFlux(Order.class).collectList()
).map(t -> new DashboardData(t.getT1(), t.getT2()));
```

---

## 12. R2DBC — Reactive Database Access

```java
// application.yml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: user
    password: pass

// Reactive repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Flux<User> findByDepartment(String department);
    Mono<Long> countByActive(boolean active);

    @Query("SELECT * FROM users WHERE salary > :minSalary ORDER BY salary DESC")
    Flux<User> findHighEarners(@Param("minSalary") int minSalary);
}

// R2DBC entity
@Table("users")
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
}

// Transactional reactive (must use ReactiveTransactionManager)
@Service
public class UserService {
    @Transactional
    public Mono<Void> transferCredit(Long fromId, Long toId, int amount) {
        return userRepository.findById(fromId)
            .flatMap(from -> {
                from.setCredit(from.getCredit() - amount);
                return userRepository.save(from);
            })
            .then(userRepository.findById(toId))
            .flatMap(to -> {
                to.setCredit(to.getCredit() + amount);
                return userRepository.save(to);
            })
            .then();
    }
}
```

---

## 13. Spring WebFlux vs Spring MVC

| Aspect | Spring MVC | Spring WebFlux |
|---|---|---|
| Thread model | Thread-per-request (blocking) | Event loop (non-blocking) |
| Server | Tomcat, Jetty | Netty (default), Tomcat |
| I/O model | Synchronous blocking | Non-blocking async |
| Return types | `String`, `ResponseEntity`, POJO | `Mono<T>`, `Flux<T>` |
| DB drivers | JDBC (blocking) | R2DBC (reactive) |
| Client | `RestTemplate` | `WebClient` |
| Thread count | High (~200 Tomcat threads) | Low (~N CPU event-loop threads + bounded elastic) |
| Overhead per request | High (stack per thread) | Low (no waiting threads) |
| **Best for** | Traditional CRUD, blocking libraries | High-concurrency, I/O-intensive, streaming |
| **NOT good for** | N/A | Heavy CPU computation, teams new to reactive |

**When to choose WebFlux:**
- Extremely high concurrency (10,000+ concurrent connections)
- Real-time streaming (SSE, WebSockets)
- Microservice fan-out (calls many downstream services in parallel)
- All dependencies have reactive drivers (R2DBC, reactive Redis, reactive Mongo)

**When to stick with MVC:**
- Team unfamiliar with reactive (reactive stack has steep learning curve)
- Using JDBC/JPA (no reactive alternative; must offload to `boundedElastic`)
- Simple CRUD applications where concurrency isn't a bottleneck
- Using libraries without reactive support

---

## 14. Testing Reactive Code — StepVerifier

```java
// StepVerifier is the standard tool for testing Mono/Flux
@Test
void testUserService() {
    Mono<User> result = userService.findById(1L);

    StepVerifier.create(result)
        .assertNext(user -> {
            assertThat(user.getName()).isEqualTo("Alice");
            assertThat(user.getAge()).isEqualTo(30);
        })
        .verifyComplete(); // asserts onComplete was called

    // Test Flux
    StepVerifier.create(Flux.just(1, 2, 3))
        .expectNext(1)
        .expectNext(2)
        .expectNext(3)
        .verifyComplete();

    // Test error
    StepVerifier.create(Mono.error(new RuntimeException("fail")))
        .expectErrorMatches(ex ->
            ex instanceof RuntimeException && ex.getMessage().equals("fail"))
        .verify();

    // Test with virtual time (for intervals/delays without actually waiting)
    StepVerifier.withVirtualTime(() -> Flux.interval(Duration.ofHours(1)).take(3))
        .expectSubscription()
        .thenAwait(Duration.ofHours(3))  // advance virtual clock
        .expectNextCount(3)
        .verifyComplete();
}

// WebTestClient for WebFlux endpoints
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerTest {
    @Autowired WebTestClient webTestClient;

    @Test
    void shouldReturnUser() {
        webTestClient.get()
            .uri("/api/users/1")
            .exchange()
            .expectStatus().isOk()
            .expectBody(User.class)
            .consumeWith(result -> {
                assertThat(result.getResponseBody().getName()).isEqualTo("Alice");
            });
    }
}
```

---

## 15. Interview Q&A

**Q: What is the difference between `subscribeOn` and `publishOn`?**  
`subscribeOn(Scheduler)` affects where the **subscription** happens — specifically, which thread the upstream source emits on. It applies retroactively to the entire upstream chain no matter where it appears (only the first one wins). `publishOn(Scheduler)` switches the thread for all **downstream** operations — everything after `publishOn` runs on the new scheduler. Use `subscribeOn(boundedElastic())` to offload a blocking source to a safe thread pool; use `publishOn(parallel())` to switch subsequent non-blocking transformations to CPU threads.

**Q: How does backpressure solve the overwhelming-publisher problem?**  
The Reactive Streams specification requires that a subscriber controls flow by calling `Subscription.request(n)` — a publisher may only emit at most `n` elements before the subscriber requests more. This makes the subscriber the flow controller. If a subscriber calls `request(1)`, the publisher emits one item and waits. Reactor provides strategies for when demand signals are difficult to express: `onBackpressureBuffer` (buffer excess), `onBackpressureDrop` (silently discard), `onBackpressureLatest` (keep only newest), or `onBackpressureError` (fail fast).

**Q: What is the difference between cold and hot publishers?**  
A cold publisher is lazy — each new subscriber triggers an independent pipeline execution from the beginning (like calling `Service.findUser()` multiple times). A hot publisher is eager and shared — it emits regardless of subscribers, and late subscribers miss past events (like a Kafka topic or a websocket feed). In Reactor, `share()` / `publish().connect()` converts a cold Flux to hot. Most publishers from reactive repositories and `Mono.fromCallable()` are cold.

**Q: Why is `Thread.sleep()` dangerous in a reactive pipeline?**  
`Thread.sleep()` is a blocking call — it holds the current thread and does nothing while waiting. In a reactive event loop (like Netty's), there are typically only `N CPU` event-loop threads handling all requests. Blocking one starves all other concurrent requests using that thread. The correct approach is `Mono.delay(Duration)` or `Flux.delayElements(Duration)` — these schedule a callback on a timer without blocking any thread. If you must do a blocking operation (like calling a legacy SDK), wrap it with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.
