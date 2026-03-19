package com.faangprep.javabackend.collections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.stream.*;

/**
 * Stream API — Internals, Collectors, Parallel Streams
 *
 * Topics:
 *  1. Pipeline internals — lazy evaluation, short-circuit demo
 *  2. map vs flatMap — the key difference
 *  3. reduce vs collect — when to use each
 *  4. Collectors.groupingBy — single and multi-level
 *  5. Collectors.teeing — two-collector split (Java 12)
 *  6. Custom Collector — partition-by-batch-size
 *  7. Parallel stream — correct and incorrect usage
 *  8. Primitive streams — boxing-free arithmetic
 *  9. Word frequency — real-world flatMap + groupingBy
 * 10. Top-N pattern — sorted + limit vs min/max
 */
public class StreamApiExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // Domain model
    // ─────────────────────────────────────────────────────────────────────────

    record Employee(String name, String department, String city, int salary, int yearsExp) {}

    static List<Employee> employees() {
        return List.of(
            new Employee("Alice",   "Engineering", "NYC",  120_000, 7),
            new Employee("Bob",     "Engineering", "NYC",  95_000,  3),
            new Employee("Carol",   "Engineering", "SF",   140_000, 10),
            new Employee("Dave",    "Marketing",   "NYC",  80_000,  4),
            new Employee("Eve",     "Marketing",   "SF",   85_000,  6),
            new Employee("Frank",   "HR",          "NYC",  70_000,  2),
            new Employee("Grace",   "HR",          "SF",   75_000,  5),
            new Employee("Heidi",   "Engineering", "NYC",  110_000, 8)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. PIPELINE INTERNALS — lazy evaluation + short-circuit
    // ─────────────────────────────────────────────────────────────────────────

    static void lazyEvaluationDemo() {
        System.out.println("\n=== 1. Lazy Evaluation Demo ===");

        List<String> names = List.of("Alice", "Bob", "Carol", "Dave", "Eve");

        // Nothing prints until findFirst() is called
        Optional<String> result = names.stream()
            .filter(s -> {
                System.out.println("  filter: " + s);
                return s.length() > 3;
            })
            .map(s -> {
                System.out.println("  map: " + s);
                return s.toUpperCase();
            })
            .findFirst(); // short-circuit: stops after first match

        System.out.println("Result: " + result.orElse("none"));
        // Only "Alice" triggers filter + map — Bob and beyond are never processed!
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. map vs flatMap
    // ─────────────────────────────────────────────────────────────────────────

    record Order(String id, List<String> items) {}

    static void mapVsFlatMapDemo() {
        System.out.println("\n=== 2. map vs flatMap ===");

        List<Order> orders = List.of(
            new Order("O1", List.of("Apple", "Banana")),
            new Order("O2", List.of("Cherry")),
            new Order("O3", List.of("Date", "Elderberry", "Fig"))
        );

        // map: one Order → one thing (the list size)
        List<Integer> sizes = orders.stream()
            .map(o -> o.items().size())
            .toList();
        System.out.println("map (sizes): " + sizes); // [2, 1, 3]

        // map: one Order → one List<String>  ← Stream<List<String>>
        List<List<String>> nested = orders.stream()
            .map(Order::items)
            .toList();
        System.out.println("map (nested): " + nested); // [[Apple,Banana],[Cherry],[Date,...]]

        // flatMap: one Order → zero-or-more Strings ← Stream<String>
        List<String> allItems = orders.stream()
            .flatMap(o -> o.items().stream()) // flatten one level
            .sorted()
            .toList();
        System.out.println("flatMap (all items): " + allItems);

        // Practical: find orders containing "Apple"
        List<String> ordersWithApple = orders.stream()
            .filter(o -> o.items().contains("Apple"))
            .map(Order::id)
            .toList();
        System.out.println("Orders with Apple: " + ordersWithApple);

        // Optional + flatMap — avoids Optional<Optional<T>>
        Optional<String> firstItemOfFirstOrder = orders.stream()
            .findFirst()
            .flatMap(o -> o.items().stream().findFirst()); // Optional<String>, not Optional<Optional<String>>
        System.out.println("First item of first order: " + firstItemOfFirstOrder.orElse("none"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. reduce vs collect — when to use each
    // ─────────────────────────────────────────────────────────────────────────

    static void reduceVsCollectDemo() {
        System.out.println("\n=== 3. reduce vs collect ===");

        List<Integer> numbers = List.of(1, 2, 3, 4, 5);

        // reduce: immutable folding — good for scalars
        int sum = numbers.stream().reduce(0, Integer::sum);
        int product = numbers.stream().reduce(1, (a, b) -> a * b);
        System.out.println("Sum: " + sum + ", Product: " + product);

        // reduce with Optional (no identity) — handles empty stream safely
        Optional<Integer> max = numbers.stream().reduce(Integer::max);
        System.out.println("Max via reduce: " + max.orElse(0));

        // BAD: reduce to build a list — O(n²) because new list on every step
        // List<String> bad = employees().stream()
        //     .reduce(new ArrayList<>(), (acc, e) -> { acc.add(e.name()); return acc; }, (a,b)->a); // WRONG

        // GOOD: collect for mutable container building — O(n), avoids copies
        List<String> names = employees().stream()
            .map(Employee::name)
            .collect(Collectors.toList());
        System.out.println("Names via collect: " + names);

        // Three-arg reduce: identity + accumulator + combiner (required for parallel)
        int totalNameLength = employees().stream()
            .reduce(
                0,                                    // identity (int)
                (acc, emp) -> acc + emp.name().length(), // accumulator: (int, Employee) → int
                Integer::sum                          // combiner: (int, int) → int — merges parallel results
            );
        System.out.println("Total name length: " + totalNameLength);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Collectors.groupingBy — single and multi-level
    // ─────────────────────────────────────────────────────────────────────────

    static void groupingByDemo() {
        System.out.println("\n=== 4. groupingBy ===");

        List<Employee> emps = employees();

        // Simple grouping
        Map<String, List<Employee>> byDept = emps.stream()
            .collect(Collectors.groupingBy(Employee::department));
        System.out.println("Engineering count: " + byDept.get("Engineering").size());

        // Grouping with downstream — counting
        Map<String, Long> countByDept = emps.stream()
            .collect(Collectors.groupingBy(Employee::department, Collectors.counting()));
        System.out.println("Count by dept: " + countByDept);

        // Grouping with downstream — average salary
        Map<String, Double> avgSalaryByDept = emps.stream()
            .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.averagingInt(Employee::salary)
            ));
        System.out.println("Avg salary by dept: " + avgSalaryByDept);

        // Grouping with downstream — finding max salary employee per dept
        Map<String, Optional<Employee>> highestPaidByDept = emps.stream()
            .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.maxBy(Comparator.comparingInt(Employee::salary))
            ));
        highestPaidByDept.forEach((dept, emp) ->
            System.out.printf("  Highest paid in %-12s: %s (%,d)%n",
                dept, emp.map(Employee::name).orElse("none"), emp.map(Employee::salary).orElse(0)));

        // Multi-level grouping: dept → city → employees
        Map<String, Map<String, List<Employee>>> byDeptThenCity = emps.stream()
            .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.groupingBy(Employee::city)
            ));
        System.out.println("Engineering SF count: " +
            byDeptThenCity.getOrDefault("Engineering", Map.of())
                .getOrDefault("SF", List.of()).size());

        // partitioningBy — special two-group split (true/false)
        Map<Boolean, List<Employee>> seniorVsJunior = emps.stream()
            .collect(Collectors.partitioningBy(e -> e.yearsExp() >= 5));
        System.out.println("Senior employees (>=5yr): " +
            seniorVsJunior.get(true).stream().map(Employee::name).toList());

        // downstream mapping — names per department
        Map<String, List<String>> namesByDept = emps.stream()
            .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.mapping(Employee::name, Collectors.toList())
            ));
        System.out.println("HR names: " + namesByDept.get("HR"));

        // collecting into sorted, joined string
        Map<String, String> joinedNamesByDept = emps.stream()
            .collect(Collectors.groupingBy(
                Employee::department,
                Collectors.mapping(Employee::name, Collectors.joining(", "))
            ));
        System.out.println("Engineering members: " + joinedNamesByDept.get("Engineering"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Collectors.teeing — two-collector split (Java 12+)
    // ─────────────────────────────────────────────────────────────────────────

    record SalarySummary(long count, double average, int max, int min) {}

    static void teeingDemo() {
        System.out.println("\n=== 5. Collectors.teeing ===");

        List<Employee> emps = employees();

        // teeing: send the same stream to two collectors, merge results
        // Useful for computing two aggregates in a single pass
        Map.Entry<Long, Double> countAndAvg = emps.stream()
            .collect(Collectors.teeing(
                Collectors.counting(),
                Collectors.averagingDouble(Employee::salary),
                Map::entry
            ));
        System.out.printf("Count: %d, Avg salary: %,.0f%n",
            countAndAvg.getKey(), countAndAvg.getValue());

        // Combine 4 aggregates using nested teeing
        SalarySummary summary = emps.stream()
            .collect(Collectors.teeing(
                Collectors.teeing(
                    Collectors.counting(),
                    Collectors.averagingDouble(Employee::salary),
                    Map::entry            // (count, avg)
                ),
                Collectors.teeing(
                    Collectors.collectingAndThen(
                        Collectors.maxBy(Comparator.comparingInt(Employee::salary)),
                        opt -> opt.map(Employee::salary).orElse(0)
                    ),
                    Collectors.collectingAndThen(
                        Collectors.minBy(Comparator.comparingInt(Employee::salary)),
                        opt -> opt.map(Employee::salary).orElse(0)
                    ),
                    Map::entry            // (max, min)
                ),
                (countAvg, maxMin) -> new SalarySummary(
                    countAvg.getKey(), countAvg.getValue(),
                    maxMin.getKey(), maxMin.getValue()
                )
            ));
        System.out.println("Summary: " + summary);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Custom Collector — partition into fixed-size batches
    // ─────────────────────────────────────────────────────────────────────────

    static <T> Collector<T, ?, List<List<T>>> toBatches(int batchSize) {
        return Collector.of(
            // supplier: create the mutable accumulation container
            ArrayList::new,

            // accumulator: add element to the container
            (List<List<T>> batches, T element) -> {
                if (batches.isEmpty() || batches.get(batches.size() - 1).size() == batchSize) {
                    batches.add(new ArrayList<>());
                }
                batches.get(batches.size() - 1).add(element);
            },

            // combiner: merge two partial containers (called in parallel)
            (left, right) -> {
                if (!left.isEmpty() && !right.isEmpty()
                        && left.get(left.size() - 1).size() < batchSize) {
                    // last batch of left is incomplete — drain into it from right's first batch
                    List<T> incomplete = left.get(left.size() - 1);
                    List<T> firstRight = right.get(0);
                    while (incomplete.size() < batchSize && !firstRight.isEmpty()) {
                        incomplete.add(firstRight.remove(0));
                    }
                    if (firstRight.isEmpty()) right.remove(0);
                }
                left.addAll(right);
                return left;
            },

            // finisher: convert accumulator to result (identity here)
            Function.identity(),

            // characteristics: no CONCURRENT, no UNORDERED, no IDENTITY_FINISH (finisher changes type)
            Collector.Characteristics.IDENTITY_FINISH
        );
    }

    static void customCollectorDemo() {
        System.out.println("\n=== 6. Custom Collector (batch partitioner) ===");

        List<Integer> ids = IntStream.rangeClosed(1, 17).boxed().toList();
        List<List<Integer>> batches = ids.stream().collect(toBatches(5));
        System.out.println("Batches of 5 from 17 elements: " + batches);
        // [[1,2,3,4,5], [6,7,8,9,10], [11,12,13,14,15], [16,17]]

        // Practical use: DB batch inserts
        batches.forEach(batch -> System.out.println("  Would INSERT batch: " + batch));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Parallel Stream — correct and incorrect usage
    // ─────────────────────────────────────────────────────────────────────────

    static void parallelStreamDemo() {
        System.out.println("\n=== 7. Parallel Stream ===");

        // CORRECT — stateless, no shared mutable state
        int n = 1_000_000;
        long sumParallel = LongStream.rangeClosed(1, n)
            .parallel()         // switches to ForkJoin common pool
            .sum();
        System.out.println("Sum 1.." + n + " = " + sumParallel);

        // INCORRECT — race condition, DON'T DO THIS
        List<Integer> unsafeList = new ArrayList<>();
        // IntStream.range(0, 100).parallel().forEach(unsafeList::add);
        // ^ data corruption — ArrayList is not thread-safe

        // CORRECT — parallel + collect (thread-safe accumulation)
        List<String> upperNames = employees().parallelStream()
            .map(e -> e.name().toUpperCase())
            .sorted()                         // stateful but handles parallel correctly
            .collect(Collectors.toList());    // collect is thread-safe in Reactor
        System.out.println("Parallel upper names: " + upperNames);

        // Thread-safe alternative with ConcurrentHashMap
        Map<String, Long> freq = employees().parallelStream()
            .collect(Collectors.groupingByConcurrent(
                Employee::department, Collectors.counting()
            ));
        System.out.println("Dept freq (parallel): " + freq);

        // sequential() converts back — useful for debugging
        long seqCount = employees().parallelStream()
            .filter(e -> e.salary() > 100_000)
            .sequential()           // debug: force sequential for peek
            .peek(e -> System.out.println("  high earner: " + e.name()))
            .count();
        System.out.println("High earners: " + seqCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Primitive Streams — boxing-free arithmetic
    // ─────────────────────────────────────────────────────────────────────────

    static void primitiveStreamDemo() {
        System.out.println("\n=== 8. Primitive Streams ===");

        // IntStream.range — no Integer boxing anywhere
        int sumOfSquares = IntStream.rangeClosed(1, 10)
            .map(n -> n * n)
            .sum();
        System.out.println("Sum of squares 1..10: " + sumOfSquares);

        // summary statistics in one pass
        IntSummaryStatistics stats = employees().stream()
            .mapToInt(Employee::salary)   // Stream<Employee> → IntStream (no boxing)
            .summaryStatistics();
        System.out.printf("Salary stats: count=%d, sum=%,d, avg=%,.0f, min=%,d, max=%,d%n",
            stats.getCount(), (long) stats.getSum(), stats.getAverage(),
            stats.getMin(), stats.getMax());

        // average() returns OptionalDouble (handles empty stream)
        OptionalDouble avgYearsExp = employees().stream()
            .mapToInt(Employee::yearsExp)
            .average();
        System.out.println("Avg years experience: " + avgYearsExp.orElse(0));

        // boxed() — convert IntStream → Stream<Integer> to use non-primitive collectors
        List<Integer> salaries = employees().stream()
            .mapToInt(Employee::salary)
            .filter(s -> s > 100_000)
            .boxed()                    // IntStream → Stream<Integer>
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
        System.out.println("High salaries sorted: " + salaries);

        // Generate a range, filter, collect to array (all primitive)
        int[] primes = IntStream.rangeClosed(2, 50)
            .filter(StreamApiExamples::isPrime)
            .toArray(); // int[] — no boxing
        System.out.println("Primes up to 50: " + Arrays.toString(primes));

        // LongStream for large ranges (avoids int overflow)
        long triangleNumber = LongStream.rangeClosed(1L, 1_000_000L).sum();
        System.out.println("Triangle number 10^6: " + triangleNumber);
    }

    static boolean isPrime(int n) {
        if (n < 2) return false;
        return IntStream.rangeClosed(2, (int) Math.sqrt(n)).noneMatch(i -> n % i == 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Word frequency — real-world flatMap + groupingBy
    // ─────────────────────────────────────────────────────────────────────────

    static void wordFrequencyDemo() {
        System.out.println("\n=== 9. Word Frequency (flatMap + groupingBy) ===");

        List<String> sentences = List.of(
            "the quick brown fox",
            "the fox jumped over the lazy dog",
            "the dog barked at the fox"
        );

        // Flatten sentences to words → count frequency → sort by count desc
        Map<String, Long> freq = sentences.stream()
            .flatMap(line -> Arrays.stream(line.split("\\s+"))) // one line → many words
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        freq.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .forEach(e -> System.out.printf("  %-10s: %d%n", e.getKey(), e.getValue()));

        // Using Collectors.toMap with merge function (avoids duplicate key exception)
        Map<String, Long> freq2 = sentences.stream()
            .flatMap(line -> Arrays.stream(line.split("\\s+")))
            .collect(Collectors.toMap(
                Function.identity(),
                w -> 1L,
                Long::sum        // merge function: add counts for duplicate keys
            ));
        System.out.println("'the' appears " + freq2.get("the") + " times");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Top-N pattern — don't sort everything just to find top items
    // ─────────────────────────────────────────────────────────────────────────

    static void topNDemo() {
        System.out.println("\n=== 10. Top-N Pattern ===");

        List<Employee> emps = employees();

        // INEFFICIENT: sort O(n log n) just to get top 3
        List<Employee> top3Slow = emps.stream()
            .sorted(Comparator.comparingInt(Employee::salary).reversed())
            .limit(3)
            .toList();

        // BETTER for single max/min: O(n) traversal, no sort
        Optional<Employee> richest = emps.stream()
            .max(Comparator.comparingInt(Employee::salary));
        System.out.println("Richest: " + richest.map(Employee::name).orElse("none"));

        // Top 3 using a min-heap of size K — O(n log K) in practice
        // Java doesn't have built-in bounded top-K collector, but we can use PriorityQueue
        int K = 3;
        PriorityQueue<Employee> minHeap =
            new PriorityQueue<>(K, Comparator.comparingInt(Employee::salary));

        emps.forEach(e -> {
            minHeap.offer(e);
            if (minHeap.size() > K) minHeap.poll(); // evict minimum
        });

        // Drain heap (will come out in ascending order — reverse for descending)
        List<Employee> top3 = new ArrayList<>(minHeap);
        top3.sort(Comparator.comparingInt(Employee::salary).reversed());
        System.out.println("Top 3 salaries: " + top3.stream().map(Employee::name).toList());

        // sorted + limit is fine for SMALL streams — the issue is O(n log n) vs O(n log K)
        System.out.println("Top 3 via sorted+limit: " +
            top3Slow.stream().map(Employee::name).toList());

        // collectingAndThen — transform result after collecting
        String richestName = emps.stream()
            .collect(Collectors.collectingAndThen(
                Collectors.maxBy(Comparator.comparingInt(Employee::salary)),
                opt -> opt.map(Employee::name).orElse("none")
            ));
        System.out.println("Richest (collectingAndThen): " + richestName);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN — run all demos
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        lazyEvaluationDemo();
        mapVsFlatMapDemo();
        reduceVsCollectDemo();
        groupingByDemo();
        teeingDemo();
        customCollectorDemo();
        parallelStreamDemo();
        primitiveStreamDemo();
        wordFrequencyDemo();
        topNDemo();
    }
}
