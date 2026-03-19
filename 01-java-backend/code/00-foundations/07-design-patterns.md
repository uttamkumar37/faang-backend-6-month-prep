# Design Patterns — GoF Patterns with Java and Spring Examples

---

## 1. Overview — Why Patterns Matter

Design patterns are reusable solutions to recurring software design problems. They are a shared vocabulary: saying "use Strategy here" communicates an entire architectural decision.

| Category | Purpose | Examples |
|---|---|---|
| **Creational** | How objects are created | Singleton, Builder, Factory Method, Abstract Factory, Prototype |
| **Structural** | How objects are composed | Adapter, Decorator, Proxy, Facade, Composite, Bridge, Flyweight |
| **Behavioral** | How objects communicate | Strategy, Observer, Command, Template Method, Chain of Responsibility, Iterator, State |

---

## 2. Singleton

Only one instance exists for the lifetime of the application. All callers share the same object.

```java
// WRONG — not thread-safe
public class Config {
    private static Config instance;
    private Config() {}
    public static Config getInstance() {
        if (instance == null)          // two threads may both see null!
            instance = new Config();
        return instance;
    }
}

// CORRECT option 1: Enum singleton (best — serialization-safe, thread-safe, concise)
public enum Config {
    INSTANCE;
    private final Properties props = loadProperties();
    public String get(String key) { return props.getProperty(key); }
}
// Usage: Config.INSTANCE.get("db.url")

// CORRECT option 2: Initialization-on-demand holder (lazy, no synchronization overhead)
public class Config {
    private Config() {}
    private static class Holder {
        static final Config INSTANCE = new Config();  // initialized lazily, once, safely
    }
    public static Config getInstance() { return Holder.INSTANCE; }
}

// CORRECT option 3: Double-checked locking (verbose, rarely needed)
public class Config {
    private static volatile Config instance;  // volatile required for JMM visibility!
    private Config() {}
    public static Config getInstance() {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null)  // re-check after acquiring lock
                    instance = new Config();
            }
        }
        return instance;
    }
}
```

**Spring connection**: `@Bean` methods return singletons by default (`@Scope("singleton")`). You rarely implement Singleton manually in Spring — let the IoC container manage lifecycle.

**When to use**: configuration holders, thread pools, connection pools, loggers.  
**When NOT to use**: overuse creates hidden global state → makes testing hard. Prefer dependency injection.

---

## 3. Builder

Constructs complex objects step-by-step. Avoids telescoping constructors (constructors with many optional parameters):

```java
// Problem: telescoping constructors are unreadable
new User("Alice", "alice@example.com", null, null, 0, true, null); // what do these mean?

// Builder pattern
public class User {
    private final String name;      // required
    private final String email;     // required
    private final String phone;     // optional
    private final int age;          // optional

    private User(Builder b) {
        this.name  = b.name;
        this.email = b.email;
        this.phone = b.phone;
        this.age   = b.age;
    }

    public static class Builder {
        private final String name;
        private final String email;
        private String phone;
        private int age;

        public Builder(String name, String email) { // required params in constructor
            this.name = name; this.email = email;
        }
        public Builder phone(String phone)  { this.phone = phone; return this; }
        public Builder age(int age)         { this.age = age; return this; }
        public User build() {
            validate();   // validate here, not in constructor
            return new User(this);
        }
        private void validate() {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        }
    }
}

User user = new User.Builder("Alice", "alice@example.com")
    .phone("555-0100")
    .age(30)
    .build();

// Lombok @Builder eliminates boilerplate:
@Builder
@lombok.Value  // immutable + getters
public class User {
    String name;
    String email;
    String phone;
    int age;
}
User user = User.builder().name("Alice").email("a@b.com").age(30).build();
```

**Spring connection**: `UriComponentsBuilder`, `MockMvcRequestBuilders`, `WebClient.builder()`, `RestTemplate.builder()` all use Builder.

---

## 4. Factory Method

Defines an interface for creating an object, but lets subclasses decide which class to instantiate:

```java
// Abstract creator
public abstract class NotificationService {
    // Factory method: subclasses override this
    protected abstract Notification createNotification(String message);

    // Template method using the factory method
    public void send(String message, String recipient) {
        Notification n = createNotification(message);  // delegates creation to subclass
        n.setRecipient(recipient);
        n.deliver();
    }
}

// Concrete creators
public class EmailNotificationService extends NotificationService {
    @Override
    protected Notification createNotification(String message) {
        return new EmailNotification(message); // decides the concrete type
    }
}
public class SmsNotificationService extends NotificationService {
    @Override
    protected Notification createNotification(String message) {
        return new SmsNotification(message);
    }
}
```

**vs Abstract Factory**: Factory Method creates ONE product. Abstract Factory creates a FAMILY of related products:

```java
// Abstract Factory — family of related UI components
interface UIFactory {
    Button createButton();
    TextField createTextField();
    Dialog createDialog();
}

class DarkThemeFactory implements UIFactory {
    public Button createButton()      { return new DarkButton(); }
    public TextField createTextField(){ return new DarkTextField(); }
    public Dialog createDialog()      { return new DarkDialog(); }
}

class LightThemeFactory implements UIFactory {
    // ... all light variants
}
```

**Spring connection**: `BeanFactory` is the foundational Spring factory pattern. `ApplicationContext.getBean(Class)` applies Factory Method. `DriverManagerDataSource`, `HikariDataSource` implement a data source factory.

---

## 5. Strategy

Encapsulates a family of algorithms and makes them interchangeable. Eliminates `if/else` or `switch` chains:

```java
// Strategy interface
public interface SortStrategy {
    <T extends Comparable<T>> void sort(List<T> list);
}

// Concrete strategies
public class QuickSort implements SortStrategy {
    @Override
    public <T extends Comparable<T>> void sort(List<T> list) { /* ... */ }
}
public class MergeSort implements SortStrategy {
    @Override
    public <T extends Comparable<T>> void sort(List<T> list) { /* ... */ }
}

// Context: uses a strategy, doesn't know which one
public class Sorter {
    private SortStrategy strategy;
    public Sorter(SortStrategy strategy) { this.strategy = strategy; }
    public void setStrategy(SortStrategy s) { this.strategy = s; }  // swap at runtime!
    public <T extends Comparable<T>> void sort(List<T> list) { strategy.sort(list); }
}

// Java 8+: strategies as lambdas (when interface has one method)
Comparator<User> byName  = Comparator.comparing(User::getName);
Comparator<User> byAge   = Comparator.comparing(User::getAge).reversed();
list.sort(byName.thenComparing(byAge));
```

**Spring connection**: `PlatformTransactionManager` is a Strategy (chosen at config time: JDBC, JPA, Kafka). `AuthenticationProvider` is a Strategy in Spring Security. `PaymentStrategy` injection pattern: inject the right implementation via DI.

---

## 6. Observer (Publish-Subscribe)

One object (Subject/Publisher) notifies many objects (Observers/Subscribers) of state changes, without tight coupling:

```java
// Classic Java-style observer
public interface EventListener<T> {
    void onEvent(T event);
}
public class OrderService {
    private final List<EventListener<OrderCreatedEvent>> listeners = new ArrayList<>();

    public void subscribe(EventListener<OrderCreatedEvent> listener) {
        listeners.add(listener);
    }

    public void createOrder(Order order) {
        orderRepository.save(order);
        var event = new OrderCreatedEvent(order.getId());
        listeners.forEach(l -> l.onEvent(event)); // notify all
    }
}

// Spring approach — decouple via ApplicationEventPublisher (no list management)
@Service
public class OrderService {
    @Autowired
    ApplicationEventPublisher publisher;

    public void createOrder(Order order) {
        orderRepository.save(order);
        publisher.publishEvent(new OrderCreatedEvent(order.getId())); // fire and... forget?
    }
}

@Component
public class EmailNotifier {
    @EventListener
    public void on(OrderCreatedEvent event) {
        emailService.sendConfirmation(event.getOrderId()); // synchronous by default
    }
}

@Component
public class InventoryReserver {
    @Async                  // make this listener async (runs in a thread pool)
    @EventListener
    public void on(OrderCreatedEvent event) {
        inventoryService.reserve(event.getOrderId());
    }
}
```

**Key benefit**: `OrderService` doesn't know about `EmailNotifier` or `InventoryReserver`. New observers can be added without modifying `OrderService`.

---

## 7. Decorator

Add behavior to an object at runtime by wrapping it, without modifying its class:

```java
// Component interface
public interface TextProcessor {
    String process(String text);
}

// Concrete component
public class PlainTextProcessor implements TextProcessor {
    public String process(String text) { return text; }
}

// Decorators — each wraps another TextProcessor
public class UpperCaseDecorator implements TextProcessor {
    private final TextProcessor wrapped;
    public UpperCaseDecorator(TextProcessor w) { this.wrapped = w; }
    public String process(String text) { return wrapped.process(text).toUpperCase(); }
}

public class TrimDecorator implements TextProcessor {
    private final TextProcessor wrapped;
    public TrimDecorator(TextProcessor w) { this.wrapped = w; }
    public String process(String text) { return wrapped.process(text.trim()); }
}

// Chain decorators at runtime
TextProcessor processor =
    new UpperCaseDecorator(
        new TrimDecorator(
            new PlainTextProcessor()
        )
    );
processor.process("  hello world  "); // "HELLO WORLD"
```

**Java/Spring examples of Decorator in the wild**:
- `BufferedInputStream` wraps `FileInputStream` — adds buffering
- `GZIPOutputStream` wraps `FileOutputStream` — adds compression
- Spring's `@Cacheable`, `@Transactional`, `@Async` — all decorate method behavior via AOP proxy
- `HttpServletRequestWrapper` in Servlet API

---

## 8. Proxy

Controls access to another object. The proxy and the real object share the same interface:

```java
// Subject interface
public interface UserService {
    User findById(Long id);
}

// Real subject
@Service
public class UserServiceImpl implements UserService {
    public User findById(Long id) { return userRepository.findById(id).orElseThrow(); }
}

// Caching proxy (manually)
public class CachingUserServiceProxy implements UserService {
    private final UserService delegate;
    private final Map<Long, User> cache = new HashMap<>();

    public CachingUserServiceProxy(UserService delegate) { this.delegate = delegate; }

    public User findById(Long id) {
        return cache.computeIfAbsent(id, delegate::findById);
    }
}
```

**Java dynamic proxy** (interface-based, used by Spring AOP for `@Transactional` etc.):
```java
UserService proxy = (UserService) Proxy.newProxyInstance(
    UserService.class.getClassLoader(),
    new Class[]{UserService.class},
    (proxyObj, method, args) -> {
        System.out.println("Before: " + method.getName());
        Object result = method.invoke(realService, args);  // delegate to real object
        System.out.println("After: " + method.getName());
        return result;
    }
);
```

**CGLIB proxy** (class-based, Spring uses when no interface): subclasses the target class and overrides methods to inject advice.

**Spring connection**: Every `@Transactional`, `@Cacheable`, `@Async`, and AOP `@Around` is implemented via a proxy. The bean you inject is NOT the real object — it's a proxy that intercepts your calls.

---

## 9. Adapter

Makes a class with an incompatible interface work with code expecting a different interface:

```java
// Target interface (what your code expects)
public interface ModernLogger {
    void logInfo(String message);
    void logError(String message, Throwable cause);
}

// Adaptee (existing class with incompatible interface)
public class LegacyLogger {                   // can't change this
    public void writeLog(int level, String msg) { ... }
    public static final int INFO = 1, ERROR = 2;
}

// Adapter: implements Target, wraps Adaptee
public class LegacyLoggerAdapter implements ModernLogger {
    private final LegacyLogger adaptee;
    public LegacyLoggerAdapter(LegacyLogger l) { this.adaptee = l; }

    public void logInfo(String message) {
        adaptee.writeLog(LegacyLogger.INFO, message);
    }
    public void logError(String message, Throwable cause) {
        adaptee.writeLog(LegacyLogger.ERROR, message + ": " + cause.getMessage());
    }
}

// Now your code works with legacy logger through the modern interface
ModernLogger logger = new LegacyLoggerAdapter(new LegacyLogger());
logger.logInfo("Application started");
```

**Java examples**:
- `Arrays.asList(int[])` — adapts array to `List`
- `InputStreamReader` — adapts byte `InputStream` to character `Reader`
- `Collections.enumeration(list)` — adapts `Iterator` to `Enumeration`
- Spring's `HandlerAdapter` — adapts various controller types to the dispatcher servlet's interface

---

## 10. Template Method

Defines the skeleton of an algorithm in a base class and lets subclasses override specific steps:

```java
// Abstract base — defines the template
public abstract class ReportGenerator {
    // Template method: the algorithm skeleton — final prevents overriding the flow
    public final String generate(ReportConfig config) {
        String data    = fetchData(config);    // step 1: abstract — must override
        String cleaned = cleanData(data);     // step 2: abstract — must override
        String html    = formatReport(cleaned); // step 3: hook — optional override
        String footer  = generateFooter();     // step 4: concrete — shared logic
        return html + footer;
    }

    protected abstract String fetchData(ReportConfig config);  // must override
    protected abstract String cleanData(String data);          // must override

    // Hook: has default behavior, subclass may optionally override
    protected String formatReport(String data) { return "<html>" + data + "</html>"; }

    // Concrete step — shared by all subclasses
    private String generateFooter() { return "<footer>Generated: " + Instant.now() + "</footer>"; }
}

public class SalesReportGenerator extends ReportGenerator {
    protected String fetchData(ReportConfig config)  { return salesDb.query(config); }
    protected String cleanData(String data)          { return sanitize(data); }
    protected String formatReport(String data)       { return pdfRenderer.render(data); } // override hook
}
```

**Spring/Java examples**:
- `JdbcTemplate.execute(ConnectionCallback)` — handles connection setup/teardown
- `AbstractApplicationContext.refresh()` — defines Spring startup sequence, subclasses customize steps
- `HttpServlet.service()` → `doGet()`/`doPost()` — template method dispatches to verb-specific hooks

---

## 11. Chain of Responsibility

Passes a request along a chain of handlers; each handler decides to process it or pass it along:

```java
// Handler interface
public interface RequestHandler {
    void handle(HttpRequest request);
    void setNext(RequestHandler next);
}

// Abstract base with chain management
public abstract class AbstractHandler implements RequestHandler {
    private RequestHandler next;
    public void setNext(RequestHandler h) { this.next = h; }
    protected void passAlong(HttpRequest req) {
        if (next != null) next.handle(req);
    }
}

// Concrete handlers
public class AuthFilter extends AbstractHandler {
    public void handle(HttpRequest req) {
        if (!isAuthenticated(req)) { req.reject(401); return; }
        passAlong(req);  // authenticated — pass to next handler
    }
}
public class RateLimitFilter extends AbstractHandler {
    public void handle(HttpRequest req) {
        if (isRateLimited(req.getClientIp())) { req.reject(429); return; }
        passAlong(req);
    }
}
public class LoggingFilter extends AbstractHandler {
    public void handle(HttpRequest req) {
        log.info("Processing: {}", req.getPath());
        passAlong(req); // always passes along
    }
}

// Assemble the chain
RequestHandler chain = new LoggingFilter();
chain.setNext(new AuthFilter());
// ... add more
```

**Spring/Java connections**:
- Servlet `FilterChain` — each `Filter` calls `chain.doFilter()` to pass the request along
- Spring Security `SecurityFilterChain` — a Chain of Responsibility with ~15 built-in filters
- Spring MVC `HandlerInterceptor` — pre/post hooks around controller execution
- Jackson deserialization — chain of `JsonDeserializer` delegates

---

## 12. Command

Encapsulates a request as an object, enabling undo/redo, queuing, logging:

```java
// Command interface
public interface Command {
    void execute();
    void undo();
}

// Concrete command
public class TransferCommand implements Command {
    private final Account from, to;
    private final BigDecimal amount;

    public void execute() {
        from.debit(amount);
        to.credit(amount);
    }

    public void undo() {
        to.debit(amount);
        from.credit(amount);
    }
}

// Invoker — stores and executes commands (supports undo history)
public class CommandProcessor {
    private final Deque<Command> history = new ArrayDeque<>();

    public void submit(Command cmd) {
        cmd.execute();
        history.push(cmd);
    }

    public void undoLast() {
        if (!history.isEmpty()) history.pop().undo();
    }
}
```

**Java/Spring connections**:
- `Runnable` and `Callable` — are Commands (encapsulate an action)
- Spring's `ApplicationEvent` — events as command objects
- Database transactions — a transaction is a batch of commands that can be rolled back (undo)

---

## 13. Patterns Working Together in Spring

Spring is a masterclass in pattern composition:

| Pattern | Where Spring Uses It |
|---|---|
| **Singleton** | All beans default to singleton scope |
| **Factory Method** | `BeanFactory`, `ApplicationContext.getBean()` |
| **Abstract Factory** | `AutowireCapableBeanFactory` creating beans |
| **Builder** | `UriComponentsBuilder`, `WebClient.builder()`, `MockMvcRequestBuilders` |
| **Prototype** | `@Scope("prototype")` — new instance per injection |
| **Proxy** | `@Transactional`, `@Cacheable`, `@Async`, AOP `@Around` |
| **Decorator** | `HttpRequestWrapper`, Servlet filter wrapping, Security filter chain |
| **Adapter** | `HandlerAdapter` (adapts controllers), `JpaVendorAdapter` |
| **Template Method** | `JdbcTemplate`, `RestTemplate`, `AbstractApplicationContext.refresh()` |
| **Strategy** | `PlatformTransactionManager`, `AuthenticationProvider`, `ResourceLoader` |
| **Observer** | `ApplicationEventPublisher` + `@EventListener` |
| **Chain of Responsibility** | `SecurityFilterChain`, `HandlerInterceptor` chain |
| **Command** | Spring Batch steps, Spring Integration message handlers |

---

## 14. Anti-Patterns to Avoid

| Anti-Pattern | Problem | Solution |
|---|---|---|
| **God Class** | One class does everything (10+ responsibilities) | Apply Single Responsibility Principle, decompose |
| **Anemic Domain Model** | Domain objects are pure data bags; all logic in Services | Move business logic into domain entities |
| **Singleton Overuse** | Using Singleton for everything → global state, hard to test | Use DI containers; only truly global state (config) needs Singleton |
| **Premature Abstraction** | Creating Strategy/Factory/Abstract Factory for one use case | YAGNI — abstract when the second case arrives |
| **Magic Numbers** | `if (status == 3)` | Use enums: `if (status == OrderStatus.SHIPPED)` |
| **Service Locator** | `ServiceLocator.get(UserService.class)` inside classes | Use constructor injection — dependencies are explicit |

---

## 15. Interview Q&A

**Q: What is the difference between Decorator and Proxy?**  
Both wrap another object and share its interface, but their intent differs. A **Decorator** adds new responsibilities/behavior to an object (buffering, compression, caching logic itself). The caller typically assembles decorators explicitly. A **Proxy** controls *access* to an object — it can add authentication checks, lazy loading, logging, or transaction management without the caller knowing a proxy is involved. In Spring, `@Transactional` is a proxy (intercepts method calls to manage transactions); `BufferedInputStream` is a decorator (adds buffering to any `InputStream`). They look structurally identical but serve different design purposes.

**Q: When would you use Strategy vs Template Method?**  
Use **Strategy** when the entire algorithm is interchangeable and you want to swap algorithms at runtime, or inject them via dependency injection (favors composition). Use **Template Method** when you have a fixed algorithm skeleton with a few variable steps, and the variation is handled by subclasses (uses inheritance). Strategy is more flexible and testable; Template Method is simpler when the overall structure never changes. In modern Java, prefer Strategy + lambdas over Template Method when possible.

**Q: What design patterns does `@Transactional` use internally?**  
`@Transactional` primarily uses the **Proxy** pattern: Spring generates a CGLIB or JDK dynamic proxy around your bean. When you call a `@Transactional` method, the proxy intercepts the call, begins a transaction (using a **Strategy** — `PlatformTransactionManager`), delegates to the real method, then commits or rolls back. The `PlatformTransactionManager` is a Strategy because Spring can swap JDBC, JPA, or Kafka transaction managers transparently. The proxy structure itself resembles a **Decorator** (same interface, wraps the real bean), but the intent is access control (transaction lifecycle management), which classifies it as Proxy.

**Q: How is Observer different from an event queue like Kafka?**  
The Observer pattern is synchronous and in-process — the subject directly calls each observer's callback within the same thread and JVM. If an observer throws, it can break the notification chain. `ApplicationEventPublisher` in Spring defaults to synchronous dispatch. Kafka (and message queues generally) provide asynchronous, durable, distributed publish-subscribe: producers and consumers are decoupled across services and machines, messages are persisted to disk (survivors crashes), and consumers can replay events. Use Observer for in-process, lightweight coordination. Use Kafka for cross-service event streaming with durability guarantees.
