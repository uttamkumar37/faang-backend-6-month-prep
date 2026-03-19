package com.faangprep.javabackend.concurrency;

// Dependencies required (add to pom.xml):
//   io.projectreactor:reactor-core:3.6.x
//   io.projectreactor:reactor-test:3.6.x (for StepVerifier)
//   org.springframework.boot:spring-boot-starter-webflux (includes reactor-core + netty)

import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.context.Context;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Reactive Programming — Project Reactor and Spring WebFlux
 *
 * Topics:
 *  1. Mono factories — just, empty, error, fromCallable, defer
 *  2. Flux factories — just, range, generate, create
 *  3. Cold vs hot — share(), ConnectableFlux
 *  4. Transforming operators — map, flatMap, concatMap, switchMap
 *  5. Combining operators — zip, merge, concat, Mono.zip
 *  6. Aggregating operators — reduce, collectList, groupBy, buffer
 *  7. Error handling — onErrorReturn, onErrorResume, retry, timeout
 *  8. Schedulers — subscribeOn vs publishOn
 *  9. Backpressure — buffer, drop, latest strategies
 * 10. Context — replacing ThreadLocal in reactive
 * 11. StepVerifier — testing Mono and Flux
 */
public class ReactiveProgrammingExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. MONO FACTORIES — different ways to create a Mono
    // ─────────────────────────────────────────────────────────────────────────

    static void monoFactoriesDemo() {
        System.out.println("=== 1. Mono Factories ===");

        // just: immediate value — evaluated NOW (not lazy)
        Mono<String> hello = Mono.just("Hello, World!");
        hello.subscribe(s -> System.out.println("just: " + s));

        // empty: completes immediately with no item (like Optional.empty())
        Mono<String> empty = Mono.empty();
        empty.subscribe(
            item  -> System.out.println("item: " + item),
            error -> System.err.println("error: " + error),
            ()    -> System.out.println("empty: completed with no item")
        );

        // error: immediately signals an error
        Mono<String> failed = Mono.error(new RuntimeException("something went wrong"));
        failed.subscribe(
            item  -> System.out.println("item: " + item),
            error -> System.out.println("error handled: " + error.getMessage())
        );

        // fromCallable: LAZY — called only when subscribed; for blocking operations
        AtomicInteger callCount = new AtomicInteger(0);
        Mono<Integer> lazy = Mono.fromCallable(() -> {
            callCount.incrementAndGet();
            return expensiveComputation(); // would hit DB, file, etc.
        });
        System.out.println("Before subscribe, callCount=" + callCount.get()); // 0 — NOT called yet
        lazy.subscribe(v -> System.out.println("fromCallable result: " + v));
        System.out.println("After subscribe, callCount=" + callCount.get());  // 1

        // defer: re-evaluates supplier on each subscription (useful for time-based values)
        Mono<Long> timestamp = Mono.defer(() -> Mono.just(System.currentTimeMillis()));
        timestamp.subscribe(t -> System.out.println("defer t1: " + t));
        timestamp.subscribe(t -> System.out.println("defer t2: " + t)); // different timestamp!

        // fromFuture: bridge from CompletableFuture
        CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> "async result");
        Mono.fromFuture(cf).subscribe(s -> System.out.println("fromFuture: " + s));
    }

    static int expensiveComputation() {
        return 42; // simulate DB lookup
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. FLUX FACTORIES — creating multi-element publishers
    // ─────────────────────────────────────────────────────────────────────────

    static void fluxFactoriesDemo() {
        System.out.println("\n=== 2. Flux Factories ===");

        // just / fromIterable / range
        Flux.just(1, 2, 3, 4, 5)
            .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        Flux.fromIterable(List.of("a", "b", "c"))
            .subscribe(s -> System.out.print(s + " "));
        System.out.println();

        Flux.range(1, 5) // [1, 2, 3, 4, 5]
            .subscribe(n -> System.out.print(n + " "));
        System.out.println();

        // generate: stateful synchronous source (one-at-a-time emission)
        Flux<String> fibonacci = Flux.generate(
            () -> new long[]{0, 1},    // initial state
            (state, sink) -> {
                sink.next(state[0] + "");  // emit current
                long next = state[0] + state[1];
                state[0] = state[1];
                state[1] = next;
                return state;
            }
        );
        System.out.println("Fibonacci: " + fibonacci.take(10).collectList().block());

        // create: for async multi-threaded emission with FluxSink
        Flux<Integer> fromCallback = Flux.create(sink -> {
            // Simulates events arriving from a callback (e.g., event listener)
            for (int i = 1; i <= 5; i++) {
                if (sink.isCancelled()) break;
                sink.next(i);
            }
            sink.complete();
        });
        fromCallback.subscribe(n -> System.out.print("create:" + n + " "));
        System.out.println();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. COLD VS HOT PUBLISHERS
    // ─────────────────────────────────────────────────────────────────────────

    static void coldVsHotDemo() throws InterruptedException {
        System.out.println("\n=== 3. Cold vs Hot ===");

        // COLD: each subscriber starts fresh
        AtomicInteger emissions = new AtomicInteger(0);
        Flux<Integer> cold = Flux.defer(() -> {
            System.out.println("  [cold] subscribed — starting emission");
            emissions.incrementAndGet();
            return Flux.range(1, 3);
        });

        cold.subscribe(n -> System.out.print("sub1:" + n + " "));
        System.out.println();
        cold.subscribe(n -> System.out.print("sub2:" + n + " ")); // independent run
        System.out.println();
        System.out.println("Cold emissions triggered: " + emissions.get()); // 2

        // HOT: publish().autoConnect() — single emission shared among subscribers
        Sinks.Many<Integer> sink = Sinks.many().multicast().onBackpressureBuffer();
        Flux<Integer> hot = sink.asFlux().share(); // share() = publish().autoConnect()

        hot.subscribe(n -> System.out.print("hotSub1:" + n + " "));
        sink.tryEmitNext(10);
        sink.tryEmitNext(20);

        hot.subscribe(n -> System.out.print("hotSub2:" + n + " ")); // misses 10 and 20
        sink.tryEmitNext(30);  // both subs see this
        sink.tryEmitNext(40);  // both subs see this
        sink.tryEmitComplete();
        System.out.println("\n(hotSub2 only sees 30, 40 — missed 10, 20)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. TRANSFORMING OPERATORS — map, flatMap, concatMap, switchMap
    // ─────────────────────────────────────────────────────────────────────────

    static Mono<String> fetchUser(int id) {
        // Simulates an async I/O call (e.g., HTTP or DB)
        return Mono.just("User-" + id)
            .delayElement(Duration.ofMillis(10));
    }

    static void transformingOperatorsDemo() {
        System.out.println("\n=== 4. Transforming Operators ===");

        // map: sync 1:1 transform
        List<Integer> doubled = Flux.range(1, 5)
            .map(n -> n * 2)
            .collectList()
            .block();
        System.out.println("map (doubled): " + doubled);

        // flatMap: async 1:many — START ALL CONCURRENTLY; order not guaranteed
        List<String> flatMapped = Flux.range(1, 5)
            .flatMap(id -> fetchUser(id))  // all 5 requests start simultaneously
            .collectList()
            .block();
        System.out.println("flatMap (concurrent, may reorder): " + flatMapped);

        // concatMap: async 1:many — SEQUENTIAL; order guaranteed (waits for each)
        List<String> concatMapped = Flux.range(1, 5)
            .concatMap(id -> fetchUser(id))  // one at a time: fetch 1, then 2, then 3...
            .collectList()
            .block();
        System.out.println("concatMap (sequential, ordered): " + concatMapped);

        // flatMap with concurrency limit — at most 2 concurrent inner subscriptions
        List<String> limitedFlat = Flux.range(1, 10)
            .flatMap(id -> fetchUser(id), 2)  // concurrency=2
            .collectList()
            .block();
        System.out.println("flatMap concurrency=2: " + limitedFlat.size() + " items");

        // switchMap: cancels in-flight inner when new outer emits (autocomplete pattern)
        //   useful for user search input — only care about latest request
        List<String> switched = Flux.just("a", "ab", "abc")
            .switchMap(query ->
                Mono.just("results for: " + query)
                    .delayElement(Duration.ofMillis(5))
            )
            .collectList()
            .block();
        System.out.println("switchMap (only last survives): " + switched);
        // Usually only "results for: abc" because "a" and "ab" are cancelled
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. COMBINING OPERATORS — zip, merge, concat
    // ─────────────────────────────────────────────────────────────────────────

    static void combiningOperatorsDemo() {
        System.out.println("\n=== 5. Combining Operators ===");

        // zip: combine by position (like Java streams' zip)
        Flux<String> names = Flux.just("Alice", "Bob", "Carol");
        Flux<Integer> ages  = Flux.just(30, 25, 35);
        List<String> zipped = Flux.zip(names, ages, (n, a) -> n + "/" + a)
            .collectList()
            .block();
        System.out.println("zip: " + zipped); // [Alice/30, Bob/25, Carol/35]

        // merge: interleave as they arrive (concurrent)
        List<Integer> merged = Flux.merge(
            Flux.just(1, 3, 5).delayElements(Duration.ofMillis(10)),
            Flux.just(2, 4, 6).delayElements(Duration.ofMillis(15))
        ).collectList().block();
        System.out.println("merge (order by arrival): " + merged);

        // concat: sequential (first to completion, then second)
        List<Integer> concatenated = Flux.concat(
            Flux.just(1, 2, 3),
            Flux.just(4, 5, 6)
        ).collectList().block();
        System.out.println("concat (ordered): " + concatenated);

        // Mono.zip: fan-out and combine (e.g., call 3 services in parallel, merge results)
        Mono<String> serviceA = Mono.just("UserProfile{name=Alice}").delayElement(Duration.ofMillis(20));
        Mono<String> serviceB = Mono.just("Orders[o1,o2]").delayElement(Duration.ofMillis(30));
        Mono<String> serviceC = Mono.just("Notifications[n1]").delayElement(Duration.ofMillis(10));

        String dashboard = Mono.zip(serviceA, serviceB, serviceC)
            .map(tuple -> String.format("Profile=%s | %s | %s",
                tuple.getT1(), tuple.getT2(), tuple.getT3()))
            .block();
        System.out.println("Fan-out result: " + dashboard);

        // zipWhen: chain where second depends on first result
        String enriched = Mono.just(1)
            .zipWhen(id -> Mono.just("Details for " + id))
            .map(t -> "id=" + t.getT1() + ", " + t.getT2())
            .block();
        System.out.println("zipWhen: " + enriched);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. AGGREGATING — reduce, collectList, groupBy, buffer
    // ─────────────────────────────────────────────────────────────────────────

    static void aggregatingDemo() {
        System.out.println("\n=== 6. Aggregating ===");

        // reduce (like Stream.reduce)
        Mono<Integer> sum = Flux.range(1, 10).reduce(0, Integer::sum);
        System.out.println("Sum 1..10: " + sum.block()); // 55

        // scan: running total (emits all intermediate values)
        List<Integer> running = Flux.range(1, 5)
            .scan(0, Integer::sum)
            .collectList().block();
        System.out.println("Running total: " + running); // [0, 1, 3, 6, 10, 15]

        // collectList: accumulate all into one list (completes before emitting)
        Mono<List<String>> all = Flux.just("a","b","c","d").collectList();
        System.out.println("collectList: " + all.block());

        // buffer: group into fixed-size lists
        List<List<Integer>> buffers = Flux.range(1, 10)
            .buffer(3)  // [1,2,3], [4,5,6], [7,8,9], [10]
            .collectList().block();
        System.out.println("buffer(3): " + buffers);

        // window: each window is a Flux (process lazily)
        Flux.range(1, 9).window(3)
            .flatMap(window -> window.reduce(0, Integer::sum))
            .subscribe(s -> System.out.print("window sum: " + s + " ")); // 6 15 24
        System.out.println();

        // groupBy: split into sub-fluxes by key
        Flux.range(1, 6)
            .groupBy(n -> n % 2 == 0 ? "even" : "odd")
            .flatMap(group -> group.collectList()
                .map(list -> group.key() + "=" + list))
            .subscribe(System.out::println);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. ERROR HANDLING
    // ─────────────────────────────────────────────────────────────────────────

    static Mono<String> unstableService(int attempt) {
        if (attempt < 3) return Mono.error(new RuntimeException("transient failure #" + attempt));
        return Mono.just("success on attempt " + attempt);
    }

    static void errorHandlingDemo() {
        System.out.println("\n=== 7. Error Handling ===");

        // onErrorReturn: fallback value
        String result1 = Mono.<String>error(new RuntimeException("oops"))
            .onErrorReturn("fallback-value")
            .block();
        System.out.println("onErrorReturn: " + result1);

        // onErrorResume: fallback publisher (most flexible)
        String result2 = Mono.<String>error(new RuntimeException("db down"))
            .onErrorResume(e -> {
                System.out.println("  caught: " + e.getMessage() + " → using cache");
                return Mono.just("cached-response");
            })
            .block();
        System.out.println("onErrorResume: " + result2);

        // onErrorResume filtering by exception type
        String result3 = Mono.<String>error(new IllegalArgumentException("bad input"))
            .onErrorResume(IllegalArgumentException.class, e -> Mono.just("bad-request-handled"))
            .onErrorResume(RuntimeException.class,         e -> Mono.just("generic-error-handled"))
            .block();
        System.out.println("onErrorResume (typed): " + result3);

        // onErrorMap: transform exception type
        String result4 = Mono.<String>error(new RuntimeException("timeout"))
            .onErrorMap(RuntimeException.class,
                e -> new IllegalStateException("wrapped: " + e.getMessage()))
            .onErrorReturn("after-map-fallback")
            .block();
        System.out.println("onErrorMap: " + result4);

        // retry with exponential backoff
        AtomicInteger attemptCounter = new AtomicInteger(0);
        String retried = Mono.defer(() -> unstableService(attemptCounter.incrementAndGet()))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(50))
                .maxBackoff(Duration.ofMillis(500))
            )
            .block();
        System.out.println("retry result: " + retried + " (attempts: " + attemptCounter.get() + ")");

        // timeout + fallback
        String timedOut = Mono.just("slow")
            .delayElement(Duration.ofMillis(100))
            .timeout(Duration.ofMillis(50))
            .onErrorReturn("timeout-fallback")
            .block();
        System.out.println("timeout fallback: " + timedOut);

        // doOnError: side effect (logging) without handling
        Mono.error(new RuntimeException("need to log"))
            .doOnError(e -> System.out.println("  [LOG] error: " + e.getMessage()))
            .onErrorReturn("handled")
            .block();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. SCHEDULERS — subscribeOn vs publishOn
    // ─────────────────────────────────────────────────────────────────────────

    static void schedulersDemo() throws InterruptedException {
        System.out.println("\n=== 8. Schedulers ===");

        // subscribeOn: affects WHERE the subscription happens (source thread)
        // Goes backwards up the chain — all upstream runs on specified scheduler
        Flux.range(1, 3)
            .doOnNext(n -> System.out.println("  source on: " + Thread.currentThread().getName()))
            .subscribeOn(Schedulers.boundedElastic())  // ALL upstream uses boundedElastic
            .doOnNext(n -> System.out.println("  after subscribeOn: " + Thread.currentThread().getName()))
            .blockLast();

        // publishOn: switches thread for everything DOWNSTREAM
        Flux.range(1, 3)
            .doOnNext(n -> System.out.println("  before publishOn: " + Thread.currentThread().getName()))
            .publishOn(Schedulers.parallel())   // downstream switches to parallel
            .doOnNext(n -> System.out.println("  after publishOn: " + Thread.currentThread().getName()))
            .blockLast();

        // Real-world pattern: blocking I/O on boundedElastic, CPU work on parallel
        String result = Mono.fromCallable(() -> {
                System.out.println("  [blocking DB] on: " + Thread.currentThread().getName());
                Thread.sleep(10); // simulate blocking DB call
                return "db-result";
            })
            .subscribeOn(Schedulers.boundedElastic())   // offload blocking operation
            .publishOn(Schedulers.parallel())            // CPU processing on parallel
            .map(data -> {
                System.out.println("  [CPU process] on: " + Thread.currentThread().getName());
                return data.toUpperCase();
            })
            .block();
        System.out.println("Scheduler pipeline result: " + result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. BACKPRESSURE STRATEGIES
    // ─────────────────────────────────────────────────────────────────────────

    static void backpressureDemo() {
        System.out.println("\n=== 9. Backpressure ===");

        // onBackpressureBuffer: buffer items a slow subscriber can't consume yet
        List<Integer> buffered = Flux.range(1, 20)
            .onBackpressureBuffer(5)   // accept up to 5 ahead of subscriber demand
            .take(10)                  // subscriber only takes 10
            .collectList()
            .block();
        System.out.println("Buffered (first 10): " + buffered);

        // onBackpressureDrop: silently discard items subscriber isn't ready for
        List<Integer> dropped = new ArrayList<>();
        Flux.range(1, 20)
            .onBackpressureDrop(item -> dropped.add(item))
            .take(5)
            .blockLast();
        System.out.println("Dropped items: " + dropped.size() + " items dropped");

        // onBackpressureLatest: always keep only the most recent item
        long latest = Flux.range(1, 1000)
            .onBackpressureLatest()    // subscriber always gets freshest data
            .take(1)
            .single()
            .block();
        System.out.println("Latest under backpressure: " + latest); // likely 1000

        // Controlled demand with limitRate (request n at a time from source)
        Flux.range(1, 100)
            .limitRate(10)   // request 10 at a time (prefetch=10)
            .take(25)
            .subscribe(n -> {}); // just consume
        System.out.println("limitRate: requested in chunks of 10");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. CONTEXT — replacing ThreadLocal in reactive
    // ─────────────────────────────────────────────────────────────────────────

    static void contextDemo() {
        System.out.println("\n=== 10. Context (reactive ThreadLocal) ===");

        // Context flows UPSTREAM from subscribe to source (opposite to data flow)
        // Write context downstream (near subscribe), read it upstream (near source)
        String result = Mono.deferContextual(ctx -> {
            String userId = ctx.getOrDefault("userId", "anonymous");
            System.out.println("  Reading userId from context: " + userId);
            return Mono.just("Hello, " + userId + "!");
        })
        .contextWrite(Context.of("userId", "alice123"))  // written closer to subscribe
        .block();
        System.out.println("Context result: " + result);

        // Multiple entries
        String greeting = Mono.deferContextual(ctx ->
            Mono.just(String.format("User=%s, Role=%s",
                ctx.getOrDefault("user", "guest"),
                ctx.getOrDefault("role", "VIEWER")))
        )
        .contextWrite(ctx -> ctx
            .put("user", "bob")
            .put("role", "ADMIN")
        )
        .block();
        System.out.println("Multi-context: " + greeting);

        // Context in operator chain — reading at each stage
        Flux.range(1, 3)
            .transformDeferredContextual((flux, ctx) -> {
                String prefix = ctx.getOrDefault("prefix", "item");
                return flux.map(n -> prefix + "-" + n);
            })
            .contextWrite(Context.of("prefix", "order"))
            .collectList()
            .subscribe(list -> System.out.println("Context in Flux: " + list));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. StepVerifier — testing reactive code
    // ─────────────────────────────────────────────────────────────────────────

    static void stepVerifierDemo() {
        System.out.println("\n=== 11. StepVerifier Tests ===");

        // Test Mono value
        StepVerifier.create(Mono.just("hello"))
            .expectNext("hello")
            .verifyComplete();
        System.out.println("✓ Mono.just test passed");

        // Test Flux sequence
        StepVerifier.create(Flux.just(1, 2, 3))
            .expectNext(1)
            .expectNext(2)
            .expectNext(3)
            .verifyComplete();
        System.out.println("✓ Flux sequence test passed");

        // Test empty Mono
        StepVerifier.create(Mono.empty())
            .verifyComplete(); // no items expected
        System.out.println("✓ Empty Mono test passed");

        // Test error
        StepVerifier.create(Mono.error(new IllegalArgumentException("bad id")))
            .expectErrorMessage("bad id")
            .verify();
        System.out.println("✓ Error Mono test passed");

        // Test error type
        StepVerifier.create(Mono.error(new RuntimeException("fail")))
            .expectErrorMatches(e ->
                e instanceof RuntimeException && "fail".equals(e.getMessage()))
            .verify();
        System.out.println("✓ Error type test passed");

        // Test with assertNext (complex object)
        record User(String name, int age) {}
        StepVerifier.create(Mono.just(new User("Alice", 30)))
            .assertNext(user -> {
                assert "Alice".equals(user.name())  : "wrong name";
                assert user.age() == 30             : "wrong age";
            })
            .verifyComplete();
        System.out.println("✓ assertNext test passed");

        // Test count
        StepVerifier.create(Flux.range(1, 100))
            .expectNextCount(100)
            .verifyComplete();
        System.out.println("✓ Count test passed");

        // Test with virtual time (avoids actually waiting for delays)
        StepVerifier.withVirtualTime(() ->
            Flux.interval(Duration.ofSeconds(1)).take(3)
        )
        .expectSubscription()
        .thenAwait(Duration.ofSeconds(3))  // advance virtual clock — no real waiting
        .expectNextCount(3)
        .verifyComplete();
        System.out.println("✓ Virtual time test passed (3 seconds simulated instantly)");

        // Test backpressure / onError with buffer overflow
        StepVerifier.create(
            Flux.range(1, 5)
                .onErrorReturn(-1)
                .take(3)
        )
        .expectNext(1, 2, 3)
        .verifyComplete();
        System.out.println("✓ Backpressure + take test passed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN — run all demos
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        monoFactoriesDemo();
        fluxFactoriesDemo();
        coldVsHotDemo();
        transformingOperatorsDemo();
        combiningOperatorsDemo();
        aggregatingDemo();
        errorHandlingDemo();
        schedulersDemo();
        backpressureDemo();
        contextDemo();
        stepVerifierDemo();

        // Give async operations time to complete
        Thread.sleep(500);
        System.out.println("\n=== All reactive demos completed ===");
    }
}
