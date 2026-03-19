# Java Stream API — Internals, Collectors, Parallel Streams

---

## 1. What is a Stream — Internal Model

A `Stream<T>` is a **lazy pipeline** of operations over a data source. Nothing runs until a terminal operation is called.

```
Source → [Stage 1] → [Stage 2] → [Stage N] → Terminal
                                              ↑ triggers the pipeline to execute
```

**Internally**, each stream operation is a `ReferencePipeline.StatelessOp` or `StatefulOp` node. The pipeline is a linked list of stages:

```java
List<String> result = names.stream()        // Head stage (source)
    .filter(s -> s.length() > 3)             // StatelessOp node
    .map(String::toUpperCase)                // StatelessOp node
    .sorted()                                // StatefulOp node (must see ALL elements)
    .collect(Collectors.toList());           // Terminal — triggers traversal

// Execution: source emits element → pushed through each stage → collected
// Key insight: filter + map are fused into a single traversal pass!
// sorted() forces buffering of all elements before passing to collect
```

**Lazy evaluation means**:
```java
// This builds a pipeline BUT DOES NOT EXECUTE
Stream<String> pipeline = names.stream()
    .filter(s -> { System.out.println("filtering " + s); return true; })
    .map(s -> { System.out.println("mapping " + s); return s.toUpperCase(); });
// No output yet!

// Terminal op triggers execution:
pipeline.findFirst(); // only prints ONE filter + ONE map — stops after first match!
```

---

## 2. Intermediate Operations — Complete Reference

### Stateless (can process one element at a time):
```java
stream.filter(Predicate<T>)         // keep elements matching predicate
stream.map(Function<T,R>)           // 1:1 transform, changes type
stream.mapToInt/Long/Double(...)    // avoids boxing: Stream<Integer> → IntStream
stream.flatMap(Function<T,Stream<R>>) // 1:many, flattens nested streams
stream.flatMapToInt(...)
stream.peek(Consumer<T>)            // side-effect for debugging (doesn't consume)
stream.distinct()                   // removes duplicates (uses equals + hashCode)
// Note: distinct() is Stateful even through it appears "simple"
```

### Stateful (must buffer or sort — can't be fused):
```java
stream.sorted()                     // natural order — buffers ALL elements
stream.sorted(Comparator<T>)        // custom order — buffers ALL elements
stream.limit(long n)                // take first n (short-circuits)
stream.skip(long n)                 // skip first n
stream.takeWhile(Predicate<T>)      // Java 9 — take while predicate true, then stop
stream.dropWhile(Predicate<T>)      // Java 9 — drop while predicate true, then take rest
```

### `map` vs `flatMap` — the most common source of confusion:
```java
// map: one element → one element
Stream<List<String>> nested = Stream.of(List.of("a","b"), List.of("c","d"));
nested.map(List::size)           // Stream<Integer>: [2, 2]
nested.map(l -> l.get(0))        // Stream<String>: ["a", "c"]

// flatMap: one element → zero-or-more elements (flattens one level)
nested.flatMap(Collection::stream) // Stream<String>: ["a", "b", "c", "d"]

// Real-world example: orders → all line items
List<String> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream()) // each Order has List<Item>
    .map(Item::getName)
    .collect(Collectors.toList());

// Optional + flatMap (unwrap nested Optional)
Optional<String> name = findUser(id)
    .flatMap(user -> Optional.ofNullable(user.getName())); // avoids Optional<Optional<String>>
```

---

## 3. Terminal Operations — Complete Reference

```java
// Collection
.collect(Collector<T,A,R>)         // most powerful — see Section 4
.toList()                           // Java 16+ — unmodifiable list (simpler than collect)
.toArray()                          // Object[]
.toArray(String[]::new)            // typed array

// Finding
.findFirst()                        // Optional<T> — first in encounter order
.findAny()                          // Optional<T> — any element (faster in parallel)
.min(Comparator)                    // Optional<T>
.max(Comparator)                    // Optional<T>
.count()                            // long

// Matching (short-circuit)
.anyMatch(Predicate)                // true if ANY element matches — stops at first match
.allMatch(Predicate)                // true if ALL elements match — stops at first miss
.noneMatch(Predicate)              // true if NO elements match

// Reduction
.reduce(identity, BinaryOperator)  // folds all elements: T result = identity ⊕ e1 ⊕ e2 ⊕ ...
.reduce(BinaryOperator)            // Optional<T> overload (no identity)
.reduce(identity, BiFunction, BinaryOperator) // for type-changing reduction

// Side effects
.forEach(Consumer)                  // no defined order in parallel
.forEachOrdered(Consumer)          // encounter order guaranteed (even parallel)

// Primitive stream terminal ops (IntStream/LongStream/DoubleStream)
.sum()   .average()   .summaryStatistics()  // IntSummaryStatistics
```

---

## 4. Collectors — The Core Machinery

`Collector<T, A, R>` has three type parameters:
- `T` — input element type
- `A` — mutable accumulation container type (internal)
- `R` — result type

```java
// Grouping — most powerful collector
Map<Department, List<Employee>> byDept =
    employees.stream().collect(Collectors.groupingBy(Employee::getDepartment));

// Grouping with downstream collector
Map<Department, Long> countByDept =
    employees.stream().collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.counting()             // downstream: count per group
    ));

Map<Department, IntSummaryStatistics> statsByDept =
    employees.stream().collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.summarizingInt(Employee::getSalary)
    ));

Map<Department, Optional<Employee>> highestPaid =
    employees.stream().collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.maxBy(Comparator.comparing(Employee::getSalary))
    ));

// Multi-level grouping
Map<Department, Map<City, List<Employee>>> byDeptThenCity =
    employees.stream().collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.groupingBy(Employee::getCity)
    ));

// Partitioning — special case of grouping: two groups (true/false)
Map<Boolean, List<Employee>> partitioned =
    employees.stream().collect(Collectors.partitioningBy(
        emp -> emp.getSalary() > 100_000
    ));
List<Employee> highEarners = partitioned.get(true);
List<Employee> others      = partitioned.get(false);

// Joining
String names = employees.stream()
    .map(Employee::getName)
    .collect(Collectors.joining(", ", "[", "]")); // [Alice, Bob, Charlie]

// toMap
Map<String, Employee> byName =
    employees.stream().collect(Collectors.toMap(
        Employee::getName,     // key mapper
        Function.identity(),   // value mapper
        (e1, e2) -> e1         // merge function (required if duplicate keys possible!)
    ));

// Counting, summing, averaging
long count  = stream.collect(Collectors.counting());
long total  = stream.collect(Collectors.summingLong(Employee::getSalary));
double avg  = stream.collect(Collectors.averagingDouble(Employee::getSalary));
IntSummaryStatistics stats = stream.collect(Collectors.summarizingInt(Employee::getAge));

// Downstream: mapping, filtering (Java 9), flatMapping (Java 9)
Map<Department, List<String>> namesByDept =
    employees.stream().collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.mapping(Employee::getName, Collectors.toList())
    ));

Map<Department, List<String>> seniorNamesByDept =
    employees.stream().collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.filtering(                           // Java 9
            emp -> emp.getYearsExperience() > 5,
            Collectors.mapping(Employee::getName, Collectors.toList())
        )
    ));

// Collectors.teeing — split stream into two collectors, merge results (Java 12)
Map.Entry<Long, Double> countAndAvg =
    employees.stream().collect(Collectors.teeing(
        Collectors.counting(),                          // downstream 1
        Collectors.averagingDouble(Employee::getSalary), // downstream 2
        Map::entry                                      // merger
    ));
```

---

## 5. Custom Collector

Implement `Collector<T, A, R>` for domain-specific aggregation:

```java
// Custom collector: split list into sublists of size N
public static <T> Collector<T, ?, List<List<T>>> partitioningBySize(int size) {
    return Collector.of(
        ArrayList::new,                              // supplier: create accumulator
        (acc, element) -> {                          // accumulator: add element to container
            if (acc.isEmpty() || acc.get(acc.size()-1).size() == size) {
                acc.add(new ArrayList<>());
            }
            acc.get(acc.size()-1).add(element);
        },
        (left, right) -> { left.addAll(right); return left; }, // combiner: merge containers (parallel)
        Function.identity(),                         // finisher: transform accumulator to result
        Collector.Characteristics.IDENTITY_FINISH    // skip finisher call when accumulator == result
    );
}

List<List<Integer>> batches = IntStream.range(0, 10).boxed()
    .collect(partitioningBySize(3));
// [[0,1,2], [3,4,5], [6,7,8], [9]]
```

---

## 6. `reduce` — Folding a Stream

```java
// Sum with reduce
int sum = IntStream.rangeClosed(1, 100).reduce(0, Integer::sum); // 5050

// String concatenation (don't do this in production — use joining collector)
String concat = Stream.of("a", "b", "c").reduce("", String::concat);

// Most complex form: transforming type
// reduce(identity, accumulator, combiner)
// Used when: result type R differs from input type T
//            REQUIRED in parallel (combiner merges partial results)
int totalLength = Stream.of("hello", "world", "!")
    .reduce(
        0,                                               // identity: R (int)
        (acc, str) -> acc + str.length(),               // accumulator: (R, T) → R
        Integer::sum                                    // combiner: (R, R) → R (parallel merging)
    );

// collect vs reduce:
// reduce: for immutable folding (produces a scalar or immutable result)
// collect: for mutable container building (produces List, Map, String, etc.)
// Rule: avoid reduce for String building or collection building — use collect!
```

---

## 7. Primitive Streams — Avoiding Boxing

`IntStream`, `LongStream`, `DoubleStream` store primitives directly in arrays — no `Integer`/`Long`/`Double` boxing:

```java
// Performance-critical loops
IntStream.range(0, 1_000_000)
    .filter(n -> n % 2 == 0)
    .map(n -> n * n)
    .sum(); // no boxing at any stage

// mapToInt / mapToLong / mapToDouble — convert Stream<T> to primitive stream
employees.stream()
    .mapToInt(Employee::getAge)     // IntStream (no Integer boxing)
    .average()                      // OptionalDouble
    .orElse(0.0);

// boxed() — convert primitive stream back to reference stream
IntStream.range(0, 5)
    .boxed()                         // Stream<Integer>
    .collect(Collectors.toList());

// Range streams
IntStream.range(0, n)             // [0, n) — exclusive end
IntStream.rangeClosed(1, n)       // [1, n] — inclusive end
LongStream.range(0L, 1_000_000_000L) // use LongStream for large ranges!
```

---

## 8. Parallel Streams — When They Help and When They Don't

Parallel streams split work across the **ForkJoin common pool**:

```java
// Enable parallel processing
list.parallelStream()             // parallel stream from collection
stream.parallel()                  // convert any stream to parallel
stream.sequential()               // convert back to sequential

// When parallel streams help:
// 1. Large data (> 10,000 elements — overhead must be worth it)
// 2. CPU-intensive operations (not I/O-bound)
// 3. No shared mutable state
// 4. Operations are associative/commutative (for correct reduce)

// Good candidate:
long count = largeList.parallelStream()
    .filter(this::expensivePredicate)        // CPU-intensive filter
    .count();

// BAD — never do this in parallel:
List<String> result = new ArrayList<>();
stream.parallel().forEach(e -> result.add(e.getName())); // RACE CONDITION!
// Fix: use collect
List<String> result = stream.parallel().map(Employee::getName).collect(Collectors.toList());

// Spliterator — the key to parallel efficiency
// Collections split their data using Spliterators:
// ArrayList: splits in half (excellent parallel performance)
// LinkedList: cannot split efficiently (poor parallel performance)
// HashSet: can split (good parallel performance)
// Stream.iterate(): cannot split — always sequential!
```

**Performance rule of thumb** for parallel streams:
```
N × Q > 10,000 (approx)
where N = number of elements
      Q = cost per element in operations

Below threshold: overhead of splitting/merging costs more than parallelism saves.
```

---

## 9. Spliterator — Source of Parallelism

`Spliterator<T>` splits a source for parallel processing:

```java
// Key characteristics (bitfield) that affect parallel behavior:
// ORDERED    — elements have defined encounter order
// DISTINCT   — all elements are unique (distinct())
// SORTED     — elements are sorted by a comparator
// SIZED      — knows total number of elements (enables better work-stealing splits)
// SUBSIZED   — split halves also know their sizes
// NONNULL    — no null elements
// IMMUTABLE  — source cannot be modified during traversal
// CONCURRENT — safe for concurrent modification

// Custom Spliterator example (splitting a range):
class RangeSpliterator implements Spliterator<Integer> {
    private int current, end;
    RangeSpliterator(int start, int end) { this.current = start; this.end = end; }

    public boolean tryAdvance(Consumer<? super Integer> action) {
        if (current < end) { action.accept(current++); return true; }
        return false;
    }

    public Spliterator<Integer> trySplit() {
        int mid = (current + end) / 2;
        if (mid == current) return null; // too small to split
        int lo = current;
        current = mid;
        return new RangeSpliterator(lo, mid);
    }

    public long estimateSize() { return end - current; }
    public int characteristics() { return ORDERED | SIZED | SUBSIZED | IMMUTABLE | NONNULL; }
}

Stream<Integer> stream = StreamSupport.stream(new RangeSpliterator(0, 1000), true); // parallel!
```

---

## 10. Common Patterns and Anti-Patterns

```java
// PATTERN: Chaining Optional with streams
Optional<String> result = employees.stream()
    .filter(emp -> emp.getId().equals(targetId))
    .findFirst()
    .map(Employee::getName);

// PATTERN: Flattening + grouping
Map<String, Long> wordFreq = text.lines()
    .flatMap(line -> Arrays.stream(line.split("\\s+")))
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

// PATTERN: Stream.ofNullable (Java 9) — avoid null check
Stream.ofNullable(maybeNull)          // empty if null, single-element if not
    .flatMap(list -> list.stream())   // works even if list is null

// ANTI-PATTERN: Reusing a stream (throws IllegalStateException)
Stream<String> s = list.stream();
s.forEach(System.out::println);
s.forEach(System.out::println); // IllegalStateException: stream has already been operated upon

// ANTI-PATTERN: forEach for mutation (use collect instead)
// BAD:
Map<String, Integer> map = new HashMap<>();
stream.forEach(e -> map.put(e.getName(), e.getAge())); // side effects → not parallelizable, not composable
// GOOD:
Map<String, Integer> map = stream.collect(
    Collectors.toMap(Employee::getName, Employee::getAge)
);

// ANTI-PATTERN: sorted on huge stream without a terminal short-circuit
stream.sorted().findFirst(); // sorts EVERYTHING just to find the minimum!
// GOOD:
stream.min(Comparator.comparing(...)); // O(n) traversal, not O(n log n) sort
```

---

## 11. Interview Q&A

**Q: Why is `Stream.iterate()` bad for parallel streams?**  
`Stream.iterate(seed, f)` generates each element by applying `f` to the previous element — it's inherently sequential (element N depends on element N-1). Its `Spliterator` has characteristic `ORDERED` but NOT `SIZED` and cannot split (`trySplit()` returns null). This means the parallel framework has nothing to split — all work stays on one thread. If you need a parallel numerical stream, use `IntStream.range()` or `LongStream.range()` instead — those have `SIZED + SUBSIZED` characteristics and split efficiently in half.

**Q: What is the difference between `reduce` and `collect`?**  
`reduce` is for **immutable folding** — it produces a single value by repeatedly applying a combining function. Each step creates a new value; no mutable container. `collect` is for **mutable reduction** — it builds a mutable container (e.g., `ArrayList`, `HashMap`, `StringBuilder`) by calling `add()`/`put()` on it per element, then optionally transforming the container to a final result. For building collections, maps, or strings, always use `collect` — `reduce` into a collection is O(n²) because it creates a new list on every step.

**Q: How does `groupingBy` work internally?**  
`groupingBy(classifier, downstream)` is a `Collector` whose accumulation container is a `HashMap<K, A>` where `K` is the key type and `A` is the downstream collector's accumulation type. For each element, it calls `classifier` to get the key, then delegates to the downstream collector for that key's container. In parallel, each thread builds its own partial `HashMap`, and the combiner merges them with `Map.merge()`. The finisher then transforms each value from `A` to the downstream's result type `R`.

**Q: When does `findFirst()` have different behavior than `findAny()` in parallel streams?**  
`findFirst()` returns the first element in **encounter order** — for a parallel stream from a list, this is strictly the element at the lowest index. To guarantee this, terminated parallel threads discard their results if a lower-encountered element has already been found. `findAny()` returns any element that is found first — in parallel, this is typically the element found first by the fastest thread, regardless of position. `findAny()` is therefore faster in parallel because threads don't coordinate to preserve order. In sequential streams, both behave identically.
