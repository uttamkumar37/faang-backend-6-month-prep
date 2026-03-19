package com.faangprep.javabackend.foundations;

// Pure Java — no external dependencies required

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;

/**
 * Design Patterns — GoF patterns implemented in idiomatic Java 21
 *
 * Topics:
 *  1.  Singleton — enum, holder idiom, double-checked locking
 *  2.  Builder — fluent builder with validation
 *  3.  Factory Method — abstract creator, concrete creators
 *  4.  Abstract Factory — product families
 *  5.  Prototype — clone vs copy constructor
 *  6.  Strategy — interface + lambdas
 *  7.  Observer — manual + functional
 *  8.  Decorator — transparent wrapping
 *  9.  Proxy — JDK dynamic proxy
 * 10.  Adapter — legacy integration
 * 11.  Template Method — abstract steps + hooks
 * 12.  Chain of Responsibility — handler pipeline
 * 13.  Command — execute/undo with history
 */
public class DesignPatternsExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. SINGLETON — three idioms, from worst to best
    // ─────────────────────────────────────────────────────────────────────────

    // BAD: Not thread-safe. Two threads may both see null and create two instances.
    static class LazySingletonBroken {
        private static LazySingletonBroken instance;
        private LazySingletonBroken() {}
        public static LazySingletonBroken getInstance() {
            if (instance == null) instance = new LazySingletonBroken(); // RACE CONDITION
            return instance;
        }
    }

    // BETTER: Double-checked locking with volatile (Java 5+)
    // volatile prevents JIT from reordering the write of 'instance' with constructor
    static class DoubleCheckedSingleton {
        private static volatile DoubleCheckedSingleton instance;
        private final String value;
        private DoubleCheckedSingleton() { this.value = "initialized"; }
        public static DoubleCheckedSingleton getInstance() {
            if (instance == null) {                  // first check — cheap, no lock
                synchronized (DoubleCheckedSingleton.class) {
                    if (instance == null) {          // second check — after acquiring lock
                        instance = new DoubleCheckedSingleton(); // volatile write
                    }
                }
            }
            return instance;
        }
    }

    // BEST: Holder idiom — lazy, thread-safe, no synchronization overhead
    static class HolderSingleton {
        private HolderSingleton() {}
        private static class Holder { // loaded only when getInstance() is first called
            static final HolderSingleton INSTANCE = new HolderSingleton();
        }
        public static HolderSingleton getInstance() { return Holder.INSTANCE; }
    }

    // BEST for most cases: Enum singleton — serialization-safe, reflection-proof
    enum AppConfig {
        INSTANCE;
        private final Map<String, String> settings = new HashMap<>();
        public void set(String k, String v) { settings.put(k, v); }
        public String get(String k)          { return settings.getOrDefault(k, ""); }
    }

    static void singletonDemo() {
        System.out.println("=== 1. Singleton ===");
        AppConfig.INSTANCE.set("host", "localhost");
        AppConfig.INSTANCE.set("port", "8080");
        System.out.println("AppConfig same instance: " + (AppConfig.INSTANCE == AppConfig.INSTANCE));
        System.out.println("host=" + AppConfig.INSTANCE.get("host"));

        // Holder idiom
        System.out.println("Holder same: " + (HolderSingleton.getInstance() == HolderSingleton.getInstance()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. BUILDER — complex object construction with validation
    // ─────────────────────────────────────────────────────────────────────────

    static final class HttpRequest {
        private final String method;    // required
        private final String url;       // required
        private final Map<String, String> headers;
        private final String body;
        private final int    timeoutMs;
        private final boolean followRedirects;

        private HttpRequest(Builder b) {
            this.method           = b.method;
            this.url              = b.url;
            this.headers          = Map.copyOf(b.headers);
            this.body             = b.body;
            this.timeoutMs        = b.timeoutMs;
            this.followRedirects  = b.followRedirects;
        }

        public static Builder builder(String method, String url) {
            return new Builder(method, url);
        }

        @Override public String toString() {
            return method + " " + url + " (headers=" + headers.size()
                + ", bodyLen=" + (body == null ? 0 : body.length()) + ", timeout=" + timeoutMs + "ms)";
        }

        static final class Builder {
            private final String method;
            private final String url;
            private final Map<String, String> headers = new LinkedHashMap<>();
            private String body;
            private int    timeoutMs       = 5000;
            private boolean followRedirects = true;

            private Builder(String method, String url) {
                if (method == null || method.isBlank()) throw new IllegalArgumentException("method required");
                if (url    == null || url.isBlank())    throw new IllegalArgumentException("url required");
                this.method = method.toUpperCase();
                this.url    = url;
            }

            public Builder header(String key, String value) {
                headers.put(key, value);
                return this;
            }
            public Builder body(String body) {
                this.body = body;
                return this;
            }
            public Builder timeoutMs(int ms) {
                if (ms <= 0) throw new IllegalArgumentException("timeout must be positive");
                this.timeoutMs = ms;
                return this;
            }
            public Builder noRedirects() {
                this.followRedirects = false;
                return this;
            }
            public HttpRequest build() { return new HttpRequest(this); }
        }
    }

    static void builderDemo() {
        System.out.println("\n=== 2. Builder ===");
        HttpRequest req = HttpRequest.builder("POST", "https://api.example.com/users")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer token123")
            .body("{\"name\":\"Alice\"}")
            .timeoutMs(3000)
            .build();
        System.out.println("Built: " + req);

        // Immutable — no setters means thread-safe and predictable
        try {
            HttpRequest.builder("", "url").build(); // validation on construction
        } catch (IllegalArgumentException e) {
            System.out.println("Validation caught: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. FACTORY METHOD — defer instantiation to subclasses
    // ─────────────────────────────────────────────────────────────────────────

    interface Notification {
        void send(String message, String recipient);
    }

    record EmailNotification(String smtpHost) implements Notification {
        public void send(String msg, String to) {
            System.out.println("  [EMAIL→" + to + "] " + msg + " via " + smtpHost);
        }
    }

    record SmsNotification(String provider) implements Notification {
        public void send(String msg, String to) {
            System.out.println("  [SMS→" + to + "] " + msg + " via " + provider);
        }
    }

    record PushNotification(String appKey) implements Notification {
        public void send(String msg, String to) {
            System.out.println("  [PUSH→" + to + "] " + msg);
        }
    }

    // Factory method via enum or static factory
    enum NotificationType { EMAIL, SMS, PUSH }

    static Notification createNotification(NotificationType type) {
        return switch (type) {
            case EMAIL -> new EmailNotification("smtp.example.com");
            case SMS   -> new SmsNotification("twilio");
            case PUSH  -> new PushNotification("fcm-key-123");
        };
    }

    // More extensible: registry-based factory
    static class NotificationFactory {
        private final Map<String, Supplier<Notification>> registry = new HashMap<>();

        NotificationFactory() {
            registry.put("email", () -> new EmailNotification("smtp.example.com"));
            registry.put("sms",   () -> new SmsNotification("twilio"));
        }

        void register(String type, Supplier<Notification> factory) {
            registry.put(type, factory);
        }

        Notification create(String type) {
            Supplier<Notification> factory = registry.get(type);
            if (factory == null) throw new IllegalArgumentException("Unknown type: " + type);
            return factory.get();
        }
    }

    static void factoryMethodDemo() {
        System.out.println("\n=== 3. Factory Method ===");
        createNotification(NotificationType.EMAIL).send("Welcome!", "alice@example.com");
        createNotification(NotificationType.SMS).send("Your OTP: 123456", "+1555000");

        NotificationFactory factory = new NotificationFactory();
        factory.register("slack", () -> (msg, to) -> System.out.println("  [SLACK→" + to + "] " + msg));
        factory.create("slack").send("Deploy done", "#engineering");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. PROTOTYPE — clone and copy constructors
    // ─────────────────────────────────────────────────────────────────────────

    static class ReportTemplate implements Cloneable {
        String title;
        List<String> sections;
        Map<String, Object> config;

        ReportTemplate(String title, List<String> sections, Map<String, Object> config) {
            this.title    = title;
            this.sections = sections;
            this.config   = config;
        }

        // SHALLOW clone — sections list is shared (mutation affects original!)
        @Override
        public ReportTemplate clone() {
            try { return (ReportTemplate) super.clone(); }
            catch (CloneNotSupportedException e) { throw new AssertionError(); }
        }

        // DEEP copy constructor — truly independent copy
        ReportTemplate(ReportTemplate other) {
            this.title    = other.title;
            this.sections = new ArrayList<>(other.sections); // new list
            this.config   = new HashMap<>(other.config);     // new map
        }
    }

    static void prototypeDemo() {
        System.out.println("\n=== 4. Prototype ===");
        ReportTemplate base = new ReportTemplate("Q4 Report",
            new ArrayList<>(List.of("Summary", "Revenue", "Costs")),
            new HashMap<>(Map.of("format", "PDF"))
        );

        // Shallow clone danger
        ReportTemplate shallow = base.clone();
        shallow.sections.add("Forecast"); // ALSO modifies base.sections!
        System.out.println("base sections (shallow pollution): " + base.sections.size()); // 4!

        // Deep copy is safe
        ReportTemplate deep = new ReportTemplate(base);
        deep.sections.add("Deep-only section");
        System.out.println("base sections (deep copy safe): " + base.sections.size()); // still 4
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. STRATEGY — interchangeable algorithms
    // ─────────────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface SortStrategy<T extends Comparable<T>> {
        void sort(List<T> list);
        default String name() { return "anonymous"; }
    }

    // Concrete strategies (can also be lambdas)
    static final class QuickSort<T extends Comparable<T>> implements SortStrategy<T> {
        public void sort(List<T> list) { Collections.sort(list); }  // java's Timsort
        public String name() { return "QuickSort"; }
    }

    static final class ReverseSort<T extends Comparable<T>> implements SortStrategy<T> {
        public void sort(List<T> list) { list.sort(Comparator.reverseOrder()); }
        public String name() { return "ReverseSort"; }
    }

    static class Sorter<T extends Comparable<T>> {
        private SortStrategy<T> strategy;
        Sorter(SortStrategy<T> strategy) { this.strategy = strategy; }
        void setStrategy(SortStrategy<T> s) { this.strategy = s; }  // runtime swap!
        List<T> sort(List<T> input) {
            List<T> copy = new ArrayList<>(input);
            strategy.sort(copy);
            return copy;
        }
    }

    static void strategyDemo() {
        System.out.println("\n=== 5. Strategy ===");
        List<Integer> data = List.of(5, 2, 8, 1, 9, 3);

        Sorter<Integer> sorter = new Sorter<>(new QuickSort<>());
        System.out.println("QuickSort: " + sorter.sort(data));

        sorter.setStrategy(new ReverseSort<>());
        System.out.println("Reverse:   " + sorter.sort(data));

        // Lambda strategy — no need for a class
        sorter.setStrategy(list -> list.sort(Comparator.comparingInt(n -> Math.abs(n - 5))));
        System.out.println("Closest-to-5: " + sorter.sort(data));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. OBSERVER — event-driven decoupling
    // ─────────────────────────────────────────────────────────────────────────

    sealed interface AppEvent permits OrderPlaced, OrderShipped, OrderCancelled {}
    record OrderPlaced(String orderId, double amount) implements AppEvent {}
    record OrderShipped(String orderId, String trackingCode) implements AppEvent {}
    record OrderCancelled(String orderId, String reason) implements AppEvent {}

    @FunctionalInterface
    interface EventHandler<E extends AppEvent> {
        void handle(E event);
    }

    static class EventBus {
        // Type → list of handlers (type-safe via unchecked cast — intentional)
        @SuppressWarnings("rawtypes")
        private final Map<Class<?>, List<EventHandler>> handlers = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        <E extends AppEvent> void subscribe(Class<E> type, EventHandler<E> handler) {
            handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(handler);
        }

        @SuppressWarnings("unchecked")
        <E extends AppEvent> void publish(E event) {
            List<EventHandler> eventHandlers = handlers.getOrDefault(event.getClass(), List.of());
            for (EventHandler handler : eventHandlers) {
                try { handler.handle(event); }
                catch (Exception e) { System.err.println("[EventBus] handler error: " + e.getMessage()); }
            }
        }
    }

    static void observerDemo() {
        System.out.println("\n=== 6. Observer (Event Bus) ===");
        EventBus bus = new EventBus();

        // Register handlers (Observers)
        bus.subscribe(OrderPlaced.class, e ->
            System.out.println("  [InventoryService] reserving stock for order " + e.orderId()));
        bus.subscribe(OrderPlaced.class, e ->
            System.out.println("  [EmailService] sending confirmation for order " + e.orderId()));
        bus.subscribe(OrderShipped.class, e ->
            System.out.println("  [TrackingService] tracking " + e.trackingCode()));
        bus.subscribe(OrderCancelled.class, e ->
            System.out.println("  [RefundService] refunding order " + e.orderId() + ": " + e.reason()));

        // Publish events
        bus.publish(new OrderPlaced("ORD-001", 299.99));
        bus.publish(new OrderShipped("ORD-001", "TRACK-XYZ"));
        bus.publish(new OrderCancelled("ORD-002", "out of stock"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. DECORATOR — add behavior without subclassing
    // ─────────────────────────────────────────────────────────────────────────

    interface TextProcessor {
        String process(String text);
    }

    // Concrete component
    static class PlainProcessor implements TextProcessor {
        public String process(String text) { return text; }
    }

    // Abstract decorator — delegates to wrapped processor
    static abstract class TextDecorator implements TextProcessor {
        protected final TextProcessor wrapped;
        TextDecorator(TextProcessor wrapped) { this.wrapped = wrapped; }
        @Override public String process(String text) { return wrapped.process(text); }
    }

    static class TrimDecorator extends TextDecorator {
        TrimDecorator(TextProcessor w) { super(w); }
        @Override public String process(String text) { return super.process(text).strip(); }
    }

    static class UpperCaseDecorator extends TextDecorator {
        UpperCaseDecorator(TextProcessor w) { super(w); }
        @Override public String process(String text) { return super.process(text).toUpperCase(); }
    }

    static class HtmlEscapeDecorator extends TextDecorator {
        HtmlEscapeDecorator(TextProcessor w) { super(w); }
        @Override public String process(String text) {
            return super.process(text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        }
    }

    static void decoratorDemo() {
        System.out.println("\n=== 7. Decorator ===");
        // Chain: trim → uppercase → html-escape
        TextProcessor pipeline = new HtmlEscapeDecorator(
                                     new UpperCaseDecorator(
                                         new TrimDecorator(
                                             new PlainProcessor())));

        String rawInput = "  <b>hello world</b>  ";
        System.out.println("Input:  '" + rawInput + "'");
        System.out.println("Output: '" + pipeline.process(rawInput) + "'");

        // compose as Functions (modern Java alternative — simpler for simple cases)
        Function<String, String> funcPipeline =
            ((Function<String, String>) String::strip)
            .andThen(String::toUpperCase)
            .andThen(s -> s.replace("<", "&lt;").replace(">", "&gt;"));
        System.out.println("Functional: '" + funcPipeline.apply(rawInput) + "'");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. PROXY — JDK dynamic proxy (instrumentation / logging)
    // ─────────────────────────────────────────────────────────────────────────

    interface UserRepository {
        Optional<User> findById(long id);
        void save(User user);
        void delete(long id);
    }

    record User(long id, String name, String email, String department) {}

    static class InMemoryUserRepository implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();
        public Optional<User> findById(long id) { return Optional.ofNullable(store.get(id)); }
        public void save(User u)  { store.put(u.id(), u); }
        public void delete(long id) { store.remove(id); }
    }

    // Creates a logging proxy without modifying UserRepository or the implementation
    @SuppressWarnings("unchecked")
    static <T> T createLoggingProxy(T target, Class<T> iface) {
        return (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[]{ iface },
            (proxy, method, args) -> {
                System.out.println("  [LOG] >" + method.getName() + "(" + Arrays.toString(args) + ")");
                long start = System.nanoTime();
                try {
                    Object result = method.invoke(target, args);
                    System.out.printf("  [LOG] <%s returned in %.2fms%n",
                        method.getName(), (System.nanoTime() - start) / 1e6);
                    return result;
                } catch (InvocationTargetException e) {
                    System.out.println("  [LOG] !" + method.getName() + " threw " + e.getCause());
                    throw e.getCause();
                }
            }
        );
    }

    static void proxyDemo() {
        System.out.println("\n=== 8. Proxy (JDK Dynamic Proxy) ===");
        UserRepository repo  = new InMemoryUserRepository();
        UserRepository proxy = createLoggingProxy(repo, UserRepository.class);

        User alice = new User(1L, "Alice", "alice@example.com", "Engineering");
        proxy.save(alice);
        proxy.findById(1L);
        proxy.delete(1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. ADAPTER — make incompatible interfaces work together
    // ─────────────────────────────────────────────────────────────────────────

    // Modern interface our system expects
    interface Logger {
        void info(String message);
        void warn(String message);
        void error(String message, Throwable cause);
    }

    // Legacy library with different interface
    static class LegacyAuditLogger {
        void log(int level, String msg) {
            System.out.printf("  [LEGACY level=%d] %s%n", level, msg);
        }
        void logWithException(String msg, Exception e) {
            System.out.printf("  [LEGACY EX] %s -- %s%n", msg, e.getMessage());
        }
    }

    // Adapter: wraps LegacyAuditLogger, implements Logger
    static class LegacyLoggerAdapter implements Logger {
        private final LegacyAuditLogger legacy;
        LegacyLoggerAdapter(LegacyAuditLogger legacy) { this.legacy = legacy; }

        @Override public void info(String message)  { legacy.log(1, message); }
        @Override public void warn(String message)  { legacy.log(2, message); }
        @Override public void error(String message, Throwable cause) {
            legacy.logWithException(message, cause instanceof Exception e ? e : new RuntimeException(cause));
        }
    }

    static void adapterDemo() {
        System.out.println("\n=== 9. Adapter ===");
        Logger logger = new LegacyLoggerAdapter(new LegacyAuditLogger());
        logger.info("Starting payment processing");
        logger.warn("Payment provider is slow");
        logger.error("Payment failed", new RuntimeException("timeout"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. TEMPLATE METHOD — fixed algorithm skeleton with variable steps
    // ─────────────────────────────────────────────────────────────────────────

    static abstract class DataExporter {
        // Template method — defines the FIXED algorithm
        final void export(String destination) {
            connect(destination);          // step 1: always same
            List<Map<String, Object>> rows = fetchData();    // step 2: subclass-specific
            List<Map<String, Object>> processed = transform(rows); // step 3: subclass-specific
            writeOutput(processed, destination);             // step 4: subclass-specific
            cleanup();                     // step 5 (hook): optional override
            System.out.println("  Exported " + processed.size() + " rows to " + destination);
        }

        private void connect(String dest) {
            System.out.println("  [template] connecting to " + dest);
        }

        protected abstract List<Map<String, Object>> fetchData();
        protected abstract List<Map<String, Object>> transform(List<Map<String, Object>> rows);
        protected abstract void writeOutput(List<Map<String, Object>> data, String destination);

        // Hook: subclasses may override for cleanup (but don't have to)
        protected void cleanup() {}
    }

    static class CsvExporter extends DataExporter {
        @Override
        protected List<Map<String, Object>> fetchData() {
            return List.of(
                Map.of("id", 1, "name", "Alice", "dept", "Eng"),
                Map.of("id", 2, "name", "Bob",   "dept", "Sales")
            );
        }
        @Override
        protected List<Map<String, Object>> transform(List<Map<String, Object>> rows) {
            return rows; // no transformation needed for CSV
        }
        @Override
        protected void writeOutput(List<Map<String, Object>> data, String dest) {
            System.out.println("  [CSV] writing " + data.size() + " rows");
        }
        @Override protected void cleanup() { System.out.println("  [CSV] closing file handle"); }
    }

    static class JsonApiExporter extends DataExporter {
        @Override
        protected List<Map<String, Object>> fetchData() {
            return List.of(Map.of("id", 3, "name", "Carol", "dept", "Finance"));
        }
        @Override
        protected List<Map<String, Object>> transform(List<Map<String, Object>> rows) {
            // Transform: add version field for API
            rows.forEach(r -> ((Map<String, Object>)r).put("apiVersion", "v2"));
            return rows;
        }
        @Override
        protected void writeOutput(List<Map<String, Object>> data, String dest) {
            System.out.println("  [JSON API] POST to " + dest);
        }
    }

    static void templateMethodDemo() {
        System.out.println("\n=== 10. Template Method ===");
        new CsvExporter().export("/tmp/report.csv");
        new JsonApiExporter().export("https://api.example.com/import");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. CHAIN OF RESPONSIBILITY — pipeline of handlers
    // ─────────────────────────────────────────────────────────────────────────

    record HttpContext(String path, String method, Map<String, String> headers,
                       String body, Map<String, Object> attributes) {
        void setAttribute(String key, Object val) { attributes.put(key, val); }
        @SuppressWarnings("unchecked")
        <T> T getAttribute(String key) { return (T) attributes.get(key); }
    }

    @FunctionalInterface
    interface Filter {
        boolean handle(HttpContext ctx, FilterChain chain);
    }

    static class FilterChain {
        private final List<Filter> filters;
        private int index = 0;

        FilterChain(List<Filter> filters) { this.filters = List.copyOf(filters); }

        boolean next(HttpContext ctx) {
            if (index >= filters.size()) return true; // all filters passed
            return filters.get(index++).handle(ctx, this);
        }
    }

    static class LoggingFilter implements Filter {
        @Override
        public boolean handle(HttpContext ctx, FilterChain chain) {
            System.out.println("  [LOG] " + ctx.method() + " " + ctx.path());
            boolean result = chain.next(ctx);
            System.out.println("  [LOG] completed: " + result);
            return result;
        }
    }

    static class AuthFilter implements Filter {
        @Override
        public boolean handle(HttpContext ctx, FilterChain chain) {
            String authHeader = ctx.headers().get("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                System.out.println("  [AUTH] REJECTED — missing token");
                return false; // stop chain
            }
            ctx.setAttribute("userId", "alice"); // set for downstream filters
            System.out.println("  [AUTH] authorized as " + ctx.getAttribute("userId"));
            return chain.next(ctx);
        }
    }

    static class RateLimitFilter implements Filter {
        private final Set<String> blocked = new HashSet<>();
        @Override
        public boolean handle(HttpContext ctx, FilterChain chain) {
            String userId = ctx.getAttribute("userId");
            if (blocked.contains(userId)) {
                System.out.println("  [RATE] REJECTED — user " + userId + " rate limited");
                return false;
            }
            System.out.println("  [RATE] user " + userId + " allowed");
            return chain.next(ctx);
        }
    }

    static void chainOfResponsibilityDemo() {
        System.out.println("\n=== 11. Chain of Responsibility ===");
        List<Filter> filters = List.of(new LoggingFilter(), new AuthFilter(), new RateLimitFilter());

        HttpContext validReq = new HttpContext("/api/users", "GET",
            Map.of("Authorization", "Bearer valid-token-123"), null, new HashMap<>());
        System.out.println("Request 1 (valid):");
        boolean result = new FilterChain(filters).next(validReq);
        System.out.println("Passed all filters: " + result);

        HttpContext noAuth = new HttpContext("/api/users", "GET", Map.of(), null, new HashMap<>());
        System.out.println("\nRequest 2 (no auth):");
        new FilterChain(filters).next(noAuth);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. COMMAND — encapsulate operations with undo
    // ─────────────────────────────────────────────────────────────────────────

    interface Command {
        void execute();
        void undo();
        String describe();
    }

    static class BankAccount {
        private double balance;
        BankAccount(double initial) { this.balance = initial; }
        void deposit(double amount)  { balance += amount; System.out.println("  deposited " + amount + ", bal=" + balance); }
        void withdraw(double amount) {
            if (amount > balance) throw new IllegalStateException("Insufficient funds");
            balance -= amount;
            System.out.println("  withdrew " + amount + ", bal=" + balance);
        }
        double getBalance() { return balance; }
    }

    static class DepositCommand implements Command {
        private final BankAccount account;
        private final double      amount;
        DepositCommand(BankAccount acc, double amt) { this.account = acc; this.amount = amt; }
        @Override public void execute()  { account.deposit(amount); }
        @Override public void undo()     { account.withdraw(amount); System.out.println("  [UNDO] deposit reversed"); }
        @Override public String describe() { return "Deposit(" + amount + ")"; }
    }

    static class WithdrawCommand implements Command {
        private final BankAccount account;
        private final double amount;
        WithdrawCommand(BankAccount acc, double amt) { this.account = acc; this.amount = amt; }
        @Override public void execute()  { account.withdraw(amount); }
        @Override public void undo()     { account.deposit(amount); System.out.println("  [UNDO] withdrawal reversed"); }
        @Override public String describe() { return "Withdraw(" + amount + ")"; }
    }

    static class CommandProcessor {
        private final Deque<Command> history = new ArrayDeque<>();
        private final Deque<Command> redoStack = new ArrayDeque<>();

        void execute(Command cmd) {
            System.out.println("Executing: " + cmd.describe());
            cmd.execute();
            history.push(cmd);
            redoStack.clear(); // redo is invalidated after new command
        }

        void undo() {
            if (history.isEmpty()) { System.out.println("Nothing to undo"); return; }
            Command cmd = history.pop();
            System.out.println("Undoing: " + cmd.describe());
            cmd.undo();
            redoStack.push(cmd);
        }

        void redo() {
            if (redoStack.isEmpty()) { System.out.println("Nothing to redo"); return; }
            Command cmd = redoStack.pop();
            System.out.println("Redoing: " + cmd.describe());
            cmd.execute();
            history.push(cmd);
        }
    }

    static void commandDemo() {
        System.out.println("\n=== 12. Command (with undo/redo) ===");
        BankAccount account = new BankAccount(1000.0);
        CommandProcessor processor = new CommandProcessor();

        processor.execute(new DepositCommand(account, 500.0));   // bal=1500
        processor.execute(new WithdrawCommand(account, 200.0));  // bal=1300
        processor.execute(new DepositCommand(account, 100.0));   // bal=1400
        processor.undo();                                          // undo deposit: bal=1300
        processor.undo();                                          // undo withdraw: bal=1500
        processor.redo();                                          // redo withdraw: bal=1300
        System.out.println("Final balance: " + account.getBalance());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN — run all demos
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        singletonDemo();
        builderDemo();
        factoryMethodDemo();
        prototypeDemo();
        strategyDemo();
        observerDemo();
        decoratorDemo();
        proxyDemo();
        adapterDemo();
        templateMethodDemo();
        chainOfResponsibilityDemo();
        commandDemo();

        System.out.println("\n=== All design pattern demos completed ===");
    }
}
