# JMH — Java Microbenchmark Harness

---

## 1. Why Naive Benchmarks Lie

Measuring Java performance manually is deceptively hard. The JVM's runtime environment makes naive timing produce meaningless results:

```java
// WRONG — this measures almost nothing useful
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    result += count(str);
}
long elapsed = System.nanoTime() - start;
```

**Problems with the above**:

| Problem | What happens | Effect on measurement |
|---|---|---|
| JIT warm-up bias | First N iterations interpreted, then compiled | First run is 10-100x slower — skews results |
| Dead code elimination | If result isn't used, JIT removes the entire loop | Measures nothing — loop runs in 0ns |
| Constant folding | `"abc".length()` replaced by `3` at compile time | Measures constant, not method |
| Loop unrolling | JIT unrolls short loops | Appears faster than real workload |
| Cache effects | L1/L2 cache warm after many iterations | Benchmark measures cache-hot performance |
| GC pressure | Long loop builds garbage → GC pause mid-measurement | Random spikes in numbers |

---

## 2. JMH Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>provided</scope>
</dependency>

<!-- Build as uber-jar for easy execution -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <configuration>
        <finalName>benchmarks</finalName>
        <transformers>
            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                <mainClass>org.openjdk.jmh.Main</mainClass>
            </transformer>
        </transformers>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
        </execution>
    </executions>
</plugin>
```

---

## 3. Your First Benchmark

```java
@BenchmarkMode(Mode.AverageTime)         // what to measure
@OutputTimeUnit(TimeUnit.MICROSECONDS)   // output unit
@State(Scope.Thread)                     // one state object per thread
@Warmup(iterations = 5, time = 1)       // 5 warm-up iterations, 1s each
@Measurement(iterations = 10, time = 1) // 10 measurement iterations, 1s each
@Fork(3)                                 // run in 3 separate JVM processes
public class StringConcatBenchmark {

    @Param({"10", "100", "1000"}) // run benchmark for each value of n
    private int n;

    private List<String> data;

    @Setup(Level.Trial) // run once per entire benchmark run
    public void setup() {
        data = IntStream.range(0, n)
                        .mapToObj(i -> "item" + i)
                        .collect(Collectors.toList());
    }

    @Benchmark
    public String concatWithPlus() {
        String result = "";
        for (String s : data) result += s; // O(n^2) — StringBuilder warning
        return result; // returning prevents DCE
    }

    @Benchmark
    public String concatWithStringBuilder() {
        StringBuilder sb = new StringBuilder();
        for (String s : data) sb.append(s);
        return sb.toString();
    }

    @Benchmark
    public String concatWithJoining() {
        return String.join("", data);
    }
}
```

---

## 4. Benchmark Modes (`@BenchmarkMode`)

| Mode | Measures | Output | Use when |
|---|---|---|---|
| `Mode.Throughput` | Operations per unit time | ops/s | Maximize throughput (requests/sec) |
| `Mode.AverageTime` | Average time per operation | time/op | Minimize latency |
| `Mode.SampleTime` | Samples every operation (histogram) | percentiles | See p99, p999 tail latency |
| `Mode.SingleShotTime` | One single invocation (cold) | time | Cold start, startup cost |
| `Mode.All` | All of the above | all | Complete picture |

```java
@BenchmarkMode({Mode.Throughput, Mode.AverageTime}) // multiple modes at once
```

---

## 5. State Management (`@State` and `@Setup`)

`@State` marks a class that provides data for benchmarks. The `Scope` controls sharing:

```java
@State(Scope.Thread)     // each thread gets its own instance — no contention
@State(Scope.Benchmark)  // one shared instance for all threads — measures contention behavior
@State(Scope.Group)      // shared within a thread group (for asymmetric benchmarks)
```

`@Setup` and `@TearDown` control lifecycle:

```java
@Setup(Level.Trial)      // once per entire benchmark (all forks + warmups + measurements)
@Setup(Level.Iteration)  // once per iteration (each 1-second measurement window)
@Setup(Level.Invocation) // before every @Benchmark call — expensive, may distort results

@TearDown(Level.Trial)   // after all iterations — good for resource cleanup
```

**Example — database connection pool benchmark**:
```java
@State(Scope.Thread)
public class DaoState {
    private DataSource dataSource;
    private UserDao dao;

    @Setup(Level.Trial)
    public void setup() {
        dataSource = createHikariDataSource();
        dao = new UserDao(dataSource);
    }

    @TearDown(Level.Trial)
    public void teardown() {
        ((HikariDataSource) dataSource).close();
    }
}

public class DaoBenchmark {
    @Benchmark
    public User findUser(DaoState state) {
        return state.dao.findById(42L);
    }
}
```

---

## 6. `@Fork` — Why Multiple JVM Forks

Each `@Fork` runs the benchmark in a fresh JVM process:

```java
@Fork(3)       // three separate JVM processes; result is averaged across forks
@Fork(value = 3, warmups = 1) // one additional fork just for JVM warm-up (discarded)
@Fork(value = 1, jvmArgs = {"-Xmx2g", "-XX:+UseZGC"}) // custom JVM flags for this benchmark
```

**Why multiple forks?**  
JIT compilation is non-deterministic — the same code may be compiled differently between runs. Multiple forks reduce this variance and catch "lucky JIT" effects. Never use `@Fork(0)` outside quick debugging — results are unreliable.

---

## 7. Blackhole — Preventing Dead Code Elimination

JIT aggressively eliminates computations whose results are never used. Use `Blackhole` to consume computed values:

```java
@Benchmark
public void badBenchmark() {
    // JIT sees result never used → eliminates this method body!
    int[] arr = new int[1000];
    Arrays.fill(arr, 42);
}

@Benchmark
public int[] betterBenchmark() {
    int[] arr = new int[1000];
    Arrays.fill(arr, 42);
    return arr; // returning forces JIT to produce the result
}

@Benchmark
public void bestForManyValues(Blackhole bh) {
    // When producing multiple values that can't all be returned
    for (int i = 0; i < 100; i++) {
        int[] arr = new int[100];
        Arrays.fill(arr, i);
        bh.consume(arr); // tells JIT "this value IS used" without real overhead
    }
}
```

---

## 8. Asymmetric Benchmarks (`@Group`)

Simulate reader-writer contention:

```java
@State(Scope.Group)
public class SharedState {
    public volatile int counter = 0;
}

@GroupThreads(5) // 5 reader threads
@Group("counter")
@Benchmark
public int read(SharedState state) {
    return state.counter;
}

@GroupThreads(1) // 1 writer thread
@Group("counter")
@Benchmark
public void write(SharedState state) {
    state.counter++;
}
```

---

## 9. Running and Reading Output

```bash
# Build
mvn clean package -DskipTests

# Run all benchmarks
java -jar target/benchmarks.jar

# Run specific benchmark (regex match on class/method name)
java -jar target/benchmarks.jar StringConcatBenchmark

# Override annotations on command line
java -jar target/benchmarks.jar -f 1 -wi 3 -i 5 -tu us

# Useful flags:
# -f 1       one fork
# -wi 3      3 warmup iterations
# -i 5       5 measurement iterations  
# -t 4       4 threads
# -tu ns/us/ms/s  time unit
# -bm thrpt/avgt/sample  benchmark mode
# -rff result.csv  save results to CSV

# Using the Java API (in tests or main method)
Options opts = new OptionsBuilder()
    .include(StringConcatBenchmark.class.getSimpleName())
    .forks(1)
    .warmupIterations(5)
    .measurementIterations(10)
    .build();
new Runner(opts).run();
```

**Reading the output**:
```
Benchmark                         (n)  Mode  Cnt    Score   Error  Units
StringConcatBenchmark.concatPlus   10  avgt   30    1.234 ± 0.045  us/op
StringConcatBenchmark.concatPlus  100  avgt   30   63.891 ± 1.234  us/op
StringConcatBenchmark.concatSB     10  avgt   30    0.234 ± 0.012  us/op
StringConcatBenchmark.concatSB    100  avgt   30    1.891 ± 0.034  us/op

# Score = mean score across all measurement iterations × forks
# Error = 99% confidence interval (lower is better — means stable result)
# If error is large (>5% of score), increase iterations or forks
```

---

## 10. Common Benchmarking Mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Measuring cold code (no warmup) | First run always fast/slow | Keep default warmups, never `@Fork(0)` |
| Not consuming results | Benchmark scores near 0 | Return from method or use Blackhole |
| Mutable shared state in `@State(Scope.Benchmark)` | Irreproducible results | Use `Scope.Thread` for independence |
| Testing the wrong thing (e.g., String.format vs + on literals) | JIT constant-folds the input | Use `@Param` to inject dynamic values |
| Including I/O in benchmark | Results dominated by I/O latency | Move I/O to `@Setup`, mock it, or benchmark I/O separately |
| Single fork | JIT variance skews results | Always use `@Fork(3)` minimum in real benchmarks |

---

## 11. Interview Q&A

**Q: What is JMH and why is it necessary over `System.nanoTime()` loops?**  
JMH (Java Microbenchmark Harness) is the standard tool for measuring JVM code performance. `System.nanoTime()` loops are unreliable because: (1) JIT warms up code during the loop, so early iterations are much slower; (2) JIT dead-code elimination removes computations whose results go unused, making the "benchmark" measure nothing; (3) GC pauses introduce random spikes. JMH solves all these: warm-up iterations handle JIT, `Blackhole` prevents DCE, multiple forks reduce JIT variance, and it handles GC-triggering between iterations.

**Q: What does `@Fork(3)` do and why is it important?**  
`@Fork(3)` runs the benchmark in 3 separate, fresh JVM processes. JIT compilation decisions are non-deterministic — the same code may be compiled to different native code in different JVM runs (different hotness profiles, different inlining decisions). Running in multiple processes and averaging results gives a more representative number and catches "lucky JIT" outliers. `@Fork(0)` runs in the current process — convenient for debugging but produces untrustworthy performance numbers.

**Q: What is the difference between `@Warmup` and `@Fork` in controlling JIT effects?**  
`@Warmup` handles **within-process** JIT: it lets the JIT compiler tier up code from interpreted → C1 (client compiler) → C2 (server compiler) before measurement begins. This ensures we measure the peak-optimized code, not the transitional state. `@Fork` handles **between-process** JIT variance: across different JVM runs, the JIT may make different (better or worse) compilation decisions. Multiple forks average out these decisions. Both are needed: warmup for fair within-process results, forks for statistical reliability.
