# Modern Java Features — Java 8 through Java 21

---

## 1. Java 8 — Lambdas

A **lambda** is an anonymous function — a  block of code you can pass around as a value.

### Basic Syntax

```java
// Traditional anonymous class
Runnable r1 = new Runnable() {
    @Override
    public void run() {
        System.out.println("Hello");
    }
};

// Lambda — same thing, shorter
Runnable r2 = () -> System.out.println("Hello");

// Single parameter (parens optional)
Consumer<String> print = s -> System.out.println(s);

// Multiple parameters
Comparator<String> byLength = (a, b) -> Integer.compare(a.length(), b.length());

// Block body with explicit return
Function<Integer, Integer> factorial = n -> {
    int result = 1;
    for (int i = 2; i <= n; i++) result *= i;
    return result;
};
```

### Variable Capture Rules

Lambdas can capture variables from the enclosing scope, but ONLY if they are **effectively final** (not reassigned after initial assignment):

```java
String prefix = "LOG:"; // effectively final — never reassigned
Consumer<String> logger = msg -> System.out.println(prefix + msg);

// This would fail:
String prefix = "LOG:";
prefix = "DEBUG:"; // now reassigned → NOT effectively final
Consumer<String> logger = msg -> System.out.println(prefix + msg); // COMPILE ERROR
```

---

## 2. Java 8 — Method References

Four types — shorthand for lambdas that just call an existing method:

```java
// 1. Static method reference: ClassName::staticMethod
Function<String, Integer> parser = Integer::parseInt;
// same as: s -> Integer.parseInt(s)

// 2. Instance method on a specific object: instance::instanceMethod
String target = "hello";
Predicate<String> check = target::equals;
// same as: s -> target.equals(s)

// 3. Instance method on an arbitrary instance: ClassName::instanceMethod
Function<String, Integer> length = String::length;
// same as: (String s) -> s.length()

// 4. Constructor reference: ClassName::new
Supplier<ArrayList<String>> createList = ArrayList::new;
// same as: () -> new ArrayList<>()

// Practical examples
List<String> names = List.of("Charlie", "Alice", "Bob");
names.stream()
     .sorted(String::compareToIgnoreCase)     // type 3
     .forEach(System.out::println);            // type 2 (out is an instance)
```

---

## 3. Java 8 — Stream API

A **Stream** is a pipeline for processing sequences of data. Unlike collections, streams do NOT store data — they process it lazily.

```
Source → [Intermediate ops] → [Intermediate ops] → Terminal op
         (lazy, return Stream)  (lazy, return Stream)  (triggers eval)
```

### Creating Streams

```java
List<String> fromList = names.stream();
Stream<String> fromArray = Arrays.stream(array);
Stream<String> from = Stream.of("a", "b", "c");
IntStream range = IntStream.range(0, 10);          // 0,1,...,9
IntStream closed = IntStream.rangeClosed(1, 5);    // 1,2,3,4,5
Stream<String> infinite = Stream.generate(() -> "x");  // infinite!
Stream<Integer> iter = Stream.iterate(0, n -> n + 2);  // 0,2,4,6,... infinite
Stream<Integer> bounded = Stream.iterate(0, n -> n < 100, n -> n + 2); // Java 9 — bounded
```

### Intermediate Operations (lazy — return Stream)

```java
List<String> names = List.of("Alice", "Bob", "Charlie", "Diana", "Ed");

names.stream()
    .filter(n -> n.length() > 3)            // keep elements matching predicate
    .map(String::toUpperCase)               // transform each element
    .sorted()                               // natural order
    .distinct()                             // removes duplicates
    .limit(3)                               // first 3 elements
    .skip(1)                                // skip first 1
    .peek(n -> System.out.println("saw: " + n)) // debug inspection (doesn't consume)
    .collect(Collectors.toList());

// flatMap — flatten nested structures
List<List<Integer>> nested = List.of(List.of(1,2), List.of(3,4));
List<Integer> flat = nested.stream()
    .flatMap(Collection::stream)            // [[1,2],[3,4]] → [1,2,3,4]
    .collect(Collectors.toList());

// mapToInt, mapToLong, mapToDouble — avoid boxing
int total = employees.stream()
    .mapToInt(Employee::getSalary)          // IntStream — no Integer boxing
    .sum(); // also: .average(), .min(), .max(), .summaryStatistics()
```

### Terminal Operations (eager — consume the stream)

```java
// Collecting to collections
List<String> list   = stream.collect(Collectors.toList());
Set<String> set     = stream.collect(Collectors.toSet());
List<String> unmod  = stream.collect(Collectors.toUnmodifiableList());

Map<Department, List<Employee>> byDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDepartment));

Map<Boolean, List<Employee>> seniorJunior = employees.stream()
    .collect(Collectors.partitioningBy(e -> e.getSalary() > 80_000));

String csv = names.stream()
    .collect(Collectors.joining(", ", "[", "]")); // "[Alice, Bob, Charlie]"

// Counting, summing
long count = stream.count();
int totalSalary = employees.stream().collect(Collectors.summingInt(Employee::getSalary));

// Finding
Optional<String> first = stream.findFirst();       // first element
Optional<String> any   = stream.findAny();         // any element (useful with parallel)
boolean anyMatch   = stream.anyMatch(Predicate);   // short-circuits
boolean allMatch   = stream.allMatch(Predicate);
boolean noneMatch  = stream.noneMatch(Predicate);

// Reduction
Optional<Integer> max = IntStream.of(1,2,3).boxed().max(Integer::compareTo);
int sum = IntStream.range(1, 6).reduce(0, Integer::sum); // 0+1+2+3+4+5 = 15
```

### Parallel Streams

```java
// Just add .parallel() — split/fork-join pool processes subsets concurrently
long count = bigList.parallelStream()
    .filter(n -> isPrime(n))
    .count();

// WARNING: parallel is NOT always faster
// - Overhead of thread coordination: only worth it for CPU-intensive work on large data (>10,000 elements)
// - MUST use stateless, side-effect-free operations (no shared mutable state)
// - Ordering: parallel streams may change encounter order (use .forEachOrdered() if order matters)
```

---

## 4. Java 8 — Optional

`Optional<T>` is a container that may or may not hold a value — a null-safe wrapper:

```java
// Creating
Optional<String> name = Optional.of("Alice");         // throws NPE if null
Optional<String> maybe = Optional.ofNullable(possiblyNull);
Optional<String> empty = Optional.empty();

// Checking and getting
name.isPresent()          // true
name.isEmpty()            // false (Java 11+)
name.get()                // "Alice" — throws NoSuchElementException if empty!

// Safe access patterns
name.orElse("default")                            // value or default
name.orElseGet(() -> computeDefault())            // lazy default (computed only if needed)
name.orElseThrow(NotFoundException::new)          // value or throw

// Transforming
Optional<Integer> length = name.map(String::length);    // Optional[5]
Optional<String> upper = name.map(String::toUpperCase); // transform if present

// flatMap — prevents Optional<Optional<T>>
Optional<String> found = userRepository.findById(id)    // Optional<User>
    .flatMap(user -> addressRepo.findByUser(user))       // Optional<Address>
    .map(Address::getCity);                              // Optional<String>

// ifPresent
name.ifPresent(n -> System.out.println("Hello " + n));
name.ifPresentOrElse(                                    // Java 9
    n -> System.out.println("Hello " + n),
    () -> System.out.println("No name")
);

// Filtering
Optional<String> longName = name.filter(n -> n.length() > 3); // empty if length <=3

// BAD: using Optional like null
if (optional.isPresent()) { optional.get(); }  // just use orElse / map instead
// RULE: Optional is for METHOD RETURN TYPES, not for fields or parameters
```

---

## 5. Java 8 — Default and Static Interface Methods

```java
public interface Logger {
    void log(String message);

    // Default method — existing implementations inherit this for free
    default void logError(String message) {
        log("ERROR: " + message);
    }

    default void logInfo(String message) {
        log("INFO: " + message);
    }

    // Static method — utility on the interface itself
    static Logger consoleLogger() {
        return message -> System.out.println("[" + LocalTime.now() + "] " + message);
    }
}
// Libraries can add default methods to interfaces without breaking all implementing classes
```

---

## 6. Java 8 — `java.time` API

The old `java.util.Date` and `Calendar` were broken (not thread-safe, confusing). `java.time` (JSR-310) replaces them:

```java
// Immutable date/time types
LocalDate today = LocalDate.now();                  // 2024-01-15
LocalDate birthday = LocalDate.of(1990, Month.MAY, 20); // 1990-05-20
LocalTime noon = LocalTime.of(12, 0);               // 12:00
LocalDateTime meeting = LocalDateTime.of(2024, 1, 15, 14, 30); // 2024-01-15T14:30
ZonedDateTime utcNow = ZonedDateTime.now(ZoneId.of("UTC"));
Instant timestamp = Instant.now();                  // machine time (epoch millis)

// Calculations — all operations return NEW objects (immutable)
LocalDate nextWeek = today.plusWeeks(1);
LocalDate lastYear = today.minusYears(1);
boolean isPast = birthday.isBefore(today);

// Duration (time-based) and Period (date-based)
Duration oneHour = Duration.ofHours(1);
Period threeMonths = Period.ofMonths(3);
long days = Duration.between(start, end).toDays();
Period age = Period.between(birthday, today);
System.out.println(age.getYears()); // e.g., 33

// Formatting and parsing
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
String formatted = today.format(fmt);               // "15/01/2024"
LocalDate parsed = LocalDate.parse("15/01/2024", fmt);

// Convert from legacy Date (when integrating with old APIs)
Instant instant = oldDate.toInstant();
LocalDateTime ldt = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
```

---

## 7. Java 9 — Collection Factories

```java
// Immutable collections — no add/remove/set after creation
List<String> list  = List.of("Alice", "Bob", "Charlie");
Set<Integer> set   = Set.of(1, 2, 3, 4, 5);
Map<String, Integer> map = Map.of("one", 1, "two", 2, "three", 3);
Map<String, Integer> big = Map.ofEntries(
    Map.entry("one", 1),
    Map.entry("two", 2) // use ofEntries for > 10 entries
);

list.add("Dave");    // UnsupportedOperationException!

// To create a mutable copy:
List<String> mutable = new ArrayList<>(List.of("A", "B", "C"));
```

---

## 8. Java 10 — `var`

```java
// Local variable type inference — type determined at compile time from the right side
var list = new ArrayList<String>();          // ArrayList<String>
var map = new HashMap<String, List<Integer>>(); // less typing for complex types
var entry = map.entrySet().iterator().next();  // Map.Entry<String, List<Integer>>

// Rules:
// ✅ Must have an initializer (var x; is illegal)
// ✅ Local variables ONLY — not fields, not parameters, not return types
// ✅ Works in for-each: for (var item : collection)
// ❌ Cannot be null: var x = null; is illegal (type can't be inferred)
```

---

## 9. Java 11 — String Enhancements

```java
"  hello  ".strip()          // "hello" (Unicode-aware, better than trim())
"  hello  ".stripLeading()   // "hello  "
"  hello  ".stripTrailing()  // "  hello"
"   ".isBlank()              // true (all whitespace)
"ha".repeat(3)               // "hahaha"
"line1\nline2\nline3".lines() // Stream<String> of 3 elements
```

### Java 11 — HTTP Client

```java
// Synchronous request
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Accept", "application/json")
    .timeout(Duration.ofSeconds(30))
    .GET()
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode()); // 200
System.out.println(response.body());       // JSON string

// Async request
CompletableFuture<HttpResponse<String>> async = client.sendAsync(request,
    HttpResponse.BodyHandlers.ofString());
async.thenApply(HttpResponse::body)
     .thenAccept(System.out::println);
```

---

## 10. Java 14 — Switch Expressions

```java
// Old switch statement (error-prone fall-through)
String result;
switch (day) {
    case MONDAY: case FRIDAY: result = "Busy"; break;
    case SATURDAY: case SUNDAY: result = "Free"; break;
    default: result = "Normal";
}

// New switch expression — no fall-through, can assign directly
String result = switch (day) {
    case MONDAY, FRIDAY -> "Busy";
    case SATURDAY, SUNDAY -> "Free";
    default -> "Normal";
};

// With a block (yield returns value)
int fee = switch (accountType) {
    case BASIC -> 0;
    case PREMIUM -> {
        int base = 100;
        int discount = isPremiumYear ? 20 : 0;
        yield base - discount;  // 'yield' instead of 'return'
    }
    case ENTERPRISE -> 500;
};
```

---

## 11. Java 15 — Text Blocks

```java
// OLD: escaped multiline string
String json = "{\n" +
              "    \"name\": \"Alice\",\n" +
              "    \"age\": 30\n" +
              "}";

// NEW: text block — no escaping, preserves structure
String json = """
        {
            "name": "Alice",
            "age": 30
        }
        """;

// SQL
String query = """
        SELECT u.id, u.name, o.total
        FROM users u
        JOIN orders o ON u.id = o.user_id
        WHERE o.total > :minAmount
        ORDER BY o.total DESC
        """;

// HTML
String html = """
        <html>
            <body>
                <p>Hello %s</p>
            </body>
        </html>
        """.formatted("World");

// Indentation: leading whitespace up to the indent of closing """ is stripped
// \  at end of line: line continuation (no newline inserted)
// \s: explicit trailing space (preserves trailing space that would be stripped)
```

---

## 12. Java 16 — `instanceof` Pattern Matching

```java
// Old — verbose (test then cast)
Object obj = "hello";
if (obj instanceof String) {
    String s = (String) obj;  // redundant cast
    System.out.println(s.length());
}

// New — test and bind in one step
if (obj instanceof String s) {
    System.out.println(s.length()); // s is String in this scope
}

// Can combine with conditions
if (obj instanceof String s && s.length() > 5) {
    System.out.println("Long string: " + s);
}

// In switch (Java 21 — standard)
Object value = ...;
String formatted = switch (value) {
    case Integer i  -> "int: " + i;
    case Long l     -> "long: " + l;
    case Double d   -> "double: " + d;
    case String s   -> "string: " + s;
    case null       -> "null";
    default         -> "other: " + value;
};
```

---

## 13. Java 16 — Records (see OOP file for full details)

```java
public record Point(double x, double y) { }
```

---

## 14. Java 17 — Sealed Classes (see OOP file for full details)

```java
public sealed class Shape permits Circle, Rectangle { }
```

---

## 15. Java 21 — Virtual Threads

Traditional threads = OS threads — expensive (~1MB stack, OS context switch overhead).  
**Virtual threads** = lightweight threads managed by the JVM — can have MILLIONS concurrently.

```java
// Create a virtual thread
Thread vt = Thread.ofVirtual().start(() -> System.out.println("Running in VT"));

// Virtual thread executor — one virtual thread per task
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 100_000; i++) {
        executor.submit(() -> handleRequest());  // 100K concurrent virtual threads — no problem
    }
}

// Key difference: I/O blocking is fine with virtual threads
// Virtual thread blocks on I/O → JVM unmounts it from carrier thread (OS thread)
// Carrier thread is free to run another virtual thread
// When I/O completes → virtual thread is rescheduled
```

### Virtual Threads in Spring Boot (since Boot 3.2)

```yaml
# application.yml — enable virtual threads for all Spring async/blocking operations
spring:
  threads:
    virtual:
      enabled: true
```

### Pinning — the gotcha

Virtual threads are "pinned" (can't unmount) when:
1. Inside a `synchronized` block or method
2. Calling native code

```java
// BAD — synchronized pins the virtual thread to a carrier thread
synchronized (lock) {
    jdbcCall(); // blocking call while pinned — wastes carrier thread
}

// GOOD — use ReentrantLock instead
private final ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    jdbcCall(); // virtual thread can unmount here
} finally {
    lock.unlock();
}
```

---

## 16. Java 21 — Sequenced Collections

Adds first/last access to the existing collection hierarchy:

```java
interface SequencedCollection<E> extends Collection<E> {
    E getFirst();
    E getLast();
    void addFirst(E e);
    void addLast(E e);
    E removeFirst();
    E removeLast();
    SequencedCollection<E> reversed();
}

// All List, Deque, LinkedHashSet, LinkedHashMap now implement this
List<String> list = new ArrayList<>(List.of("A", "B", "C"));
list.getFirst();     // "A"
list.getLast();      // "C"
list.reversed();     // live reversed view: ["C", "B", "A"]

LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
map.put("one", 1); map.put("two", 2);
map.firstEntry();   // {one=1}
map.lastEntry();    // {two=2}
map.reversed();     // reversed view
```

---

## 17. Java 21 — Pattern Matching in Switch (Standard)

```java
// Works with ANY type (not just sealed classes)
static String format(Object obj) {
    return switch (obj) {
        case Integer i when i < 0  -> "negative int: " + i;   // guarded pattern
        case Integer i             -> "int: " + i;
        case Long l                -> "long: " + l;
        case Double d              -> "double: " + d;
        case String s when s.isBlank() -> "blank string";
        case String s              -> "string: " + s;
        case int[] arr             -> "int array of length " + arr.length;
        case null                  -> "null";
        default                    -> "unknown: " + obj;
    };
}
```

### Record Patterns (Java 21)

```java
record Point(int x, int y) {}
record Rectangle(Point topLeft, Point bottomRight) {}

// Deconstruct records in patterns
Object shape = new Rectangle(new Point(0, 0), new Point(10, 20));
if (shape instanceof Rectangle(Point(int x1, int y1), Point(int x2, int y2))) {
    System.out.println("Area: " + (x2 - x1) * (y2 - y1)); // directly use x1,y1,x2,y2
}

// In switch
static double area(Object shape) {
    return switch (shape) {
        case Circle(double r)             -> Math.PI * r * r;
        case Rectangle(Point(int x1,int y1), Point(int x2,int y2)) ->
            (double)(x2-x1) * (y2-y1);
        default -> throw new IllegalArgumentException();
    };
}
```

---

## 18. Complete Feature Timeline Cheat Sheet

| Version | LTS? | Key Features |
|---|---|---|
| **Java 8** (2014) | ✅ | Lambdas, Streams, `Optional`, `java.time`, `default` methods, `forEach` |
| Java 9 (2017) | | Module system (JPMS), `List.of()`, `Map.of()`, `Stream.takeWhile/dropWhile` |
| Java 10 (2018) | | `var` (local type inference) |
| **Java 11** (2018) | ✅ | `String.isBlank/strip/lines/repeat`, `Files.readString`, new HTTP Client |
| Java 12 | | Switch expression (preview) |
| Java 13 | | Text blocks (preview) |
| Java 14 | | Switch expression (standard), `NullPointerException` messages |
| Java 15 | | Text blocks (standard), `hidden classes` |
| Java 16 | | Records (standard), `instanceof` pattern matching (standard) |
| **Java 17** (2021) | ✅ | Sealed classes (standard), enhanced `switch`, removal of experimental AOT |
| Java 18 | | `@snippet` in Javadoc, simple web server |
| Java 19 | | Virtual threads (preview), Structured concurrency (incubator) |
| Java 20 | | Record patterns (preview), Virtual threads (preview 2) |
| **Java 21** (2023) | ✅ | Virtual threads (standard), Sequenced collections, Pattern matching switch (standard), Record patterns (standard), String templates (preview) |

---

## 19. Interview Q&A

**Q: What is a functional interface and can you name some built-in ones?**  
A functional interface has exactly one abstract method. `@FunctionalInterface` annotation enforces this. They can be implemented by lambdas or method references. Built-in examples: `Runnable` (`void run()`), `Callable<V>` (`V call()`), `Comparator<T>` (`int compare(T,T)`), and all of `java.util.function`: `Supplier<T>`, `Consumer<T>`, `Function<T,R>`, `Predicate<T>`, plus bi-variants and primitive specializations.

**Q: What is the difference between `map` and `flatMap` in streams?**  
`map` transforms each element 1:1 — it applies a function that produces ONE output per input. `flatMap` transforms each element to ZERO or MORE outputs (a Stream) and then flattens all those streams into one. Example: `List<List<Integer>> → flatMap(Collection::stream) → List<Integer>`. Also used for Optional: `Optional<Optional<T>> → flatMap → Optional<T>`, avoiding nested Optionals.

**Q: What are virtual threads and when do you use them?**  
Virtual threads (Java 21) are lightweight JVM-managed threads. Unlike platform threads (backed by OS threads, ~1MB each), virtual threads take ~few hundred bytes and can be blocked without blocking an OS thread — when a virtual thread does I/O, the JVM unmounts it from its carrier (OS) thread, which can then run other virtual threads. This enables the thread-per-request model even at massive scale. Use them for I/O-bound workloads (HTTP, database, file). For CPU-bound work, they offer no advantage over platform threads. Avoid in pinning situations (synchronized blocks with I/O inside).

**Q: Why is `Optional` not ideal as a method parameter or field?**  
Optional as a parameter forces callers to always wrap values, adding noise: `findUser(Optional.of(id))` vs `findUser(id)`. It's also verbose to check inside methods. As a field, Optional is not `Serializable`, adding complexity for JPA entities or DTOs. As a return type it's idiomatic — callers are prompted to handle the empty case explicitly. Use overloaded methods instead of Optional parameters.

**Q: What is the difference between `orElse` and `orElseGet` in Optional?**  
`orElse(defaultValue)` always evaluates the argument — even if Optional has a value, the default expression runs. `orElseGet(Supplier)` only evaluates the supplier lazily if Optional is empty. For cheap defaults (a constant or simple value) they're equivalent. For expensive computations (database call, object construction), always use `orElseGet` to avoid wasted work.
