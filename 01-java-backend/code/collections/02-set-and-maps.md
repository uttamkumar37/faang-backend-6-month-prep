# Java Collections — Sets and Maps Deep Dive

---

## 1. HashSet — The Set Backed by HashMap

`HashSet<E>` is literally a `HashMap<E, PRESENT>` where `PRESENT` is a dummy `Object` value. All Set semantics come from `HashMap`:

```java
HashSet<String> set = new HashSet<>();   // initial capacity 16, load factor 0.75
HashSet<String> set = new HashSet<>(64); // pre-size for known cardinality

set.add("apple");    // O(1) avg — hashCode() → bucket, check equals(), store
set.contains("x");   // O(1) avg
set.remove("x");     // O(1) avg
set.size();          // O(1)

// Iteration order: undefined (depends on hash values)
for (String s : set) { ... } // unpredictable order — do NOT rely on it

// Set operations
Set<String> a = new HashSet<>(List.of("a", "b", "c", "d"));
Set<String> b = new HashSet<>(List.of("c", "d", "e", "f"));

Set<String> union        = new HashSet<>(a); union.addAll(b);       // {a,b,c,d,e,f}
Set<String> intersection = new HashSet<>(a); intersection.retainAll(b); // {c,d}
Set<String> difference   = new HashSet<>(a); difference.removeAll(b);   // {a,b}
```

**The hashCode/equals contract for custom objects**:
```java
// Rule: if a.equals(b) then a.hashCode() == b.hashCode() (but not vice versa)
// Breaking this contract causes elements to "disappear" in a HashSet

@Override
public boolean equals(Object o) { ... }

@Override
public int hashCode() { return Objects.hash(field1, field2); } // always override both!
```

---

## 2. LinkedHashSet — Insertion-Order Set

`LinkedHashSet` is a `HashSet` backed by a `LinkedHashMap` — maintains a doubly-linked list through its entries in insertion order:

```java
LinkedHashSet<String> set = new LinkedHashSet<>();
set.add("banana");
set.add("apple");
set.add("cherry");
set.add("apple"); // duplicate — ignored, order NOT updated

for (String s : set) System.out.print(s + " ");
// Output: banana apple cherry  (insertion order preserved)

// Use cases:
// - Deduplication while preserving order
// - LRU cache key tracking (access-order variant → use LinkedHashMap instead)
```

---

## 3. TreeSet — Sorted NavigableSet

`TreeSet<E>` is backed by a `TreeMap<E, PRESENT>` — a Red-Black tree. Elements are always in sorted (natural or comparator) order:

```java
// Natural ordering (Comparable required)
TreeSet<Integer> ts = new TreeSet<>(Set.of(5, 1, 3, 7, 2));
System.out.println(ts); // [1, 2, 3, 5, 7] — always sorted

// Custom ordering
TreeSet<String> byLength = new TreeSet<>(Comparator.comparingInt(String::length)
                                                    .thenComparing(Comparator.naturalOrder()));
// Important: comparator must be consistent with equals or TreeSet violates Set contract!

// O(log n) for: add, remove, contains
ts.add(4);
ts.first();          // 1 — minimum element
ts.last();           // 7 — maximum element

// NavigableSet operations — all O(log n)
ts.floor(4);         // 3 — greatest element ≤ 4
ts.ceiling(4);       // 5 — smallest element ≥ 4
ts.lower(5);         // 3 — greatest element strictly < 5
ts.higher(5);        // 7 — smallest element strictly > 5

ts.headSet(5);       // [1, 2, 3] — elements < 5 (exclusive)
ts.headSet(5, true); // [1, 2, 3, 5] — elements ≤ 5 (inclusive)
ts.tailSet(3);       // [3, 5, 7] — elements ≥ 3 (inclusive)
ts.subSet(2, 6);     // [2, 3, 5] — elements 2 ≤ x < 6

ts.pollFirst();      // removes and returns minimum
ts.pollLast();       // removes and returns maximum
ts.descendingSet();  // reverse-ordered view (not a copy)
```

---

## 4. EnumSet — The Fastest Set for Enums

`EnumSet<E extends Enum>` uses a **bit vector** internally (a `long` for ≤64 constants, `long[]` for more):
- Each enum constant maps to one bit
- All operations are just bitwise operations — O(1) and extremely fast

```java
enum Permission { READ, WRITE, EXECUTE, DELETE, ADMIN }

// Factory methods — never use new EnumSet<>()
EnumSet<Permission> userPerms = EnumSet.of(Permission.READ, Permission.WRITE);
EnumSet<Permission> allPerms  = EnumSet.allOf(Permission.class);
EnumSet<Permission> noPerms   = EnumSet.noneOf(Permission.class);
EnumSet<Permission> range     = EnumSet.range(Permission.READ, Permission.EXECUTE); // READ, WRITE, EXECUTE

// Operations (bitwise under the hood — JVM can optimize further)
userPerms.add(Permission.EXECUTE);
userPerms.remove(Permission.DELETE);
userPerms.contains(Permission.READ); // O(1) — just a bit test

// Set operations act on bitmask
EnumSet<Permission> intersection = EnumSet.copyOf(allPerms);
intersection.retainAll(userPerms); // bitwise AND

// Replace HashSet<MyEnum> with EnumSet for ~ 4-5x speedup
```

**Rule**: whenever your `Set` element type is an `enum`, use `EnumSet`. It is always faster and uses less memory.

---

## 5. HashMap — Internals Refresher

```
Table: array of Entry/TreeNode buckets
Default initial capacity: 16
Load factor: 0.75  →  threshold = capacity × 0.75

When size > threshold: rehash (double capacity, redistribute all entries)

Bucket structure:
 - 0 to 7 entries:  singly-linked list
 - 8+ entries:      convert to Red-Black tree (treeify threshold = 8)
 - < 6 entries:     convert back to list (untreeify threshold = 6)

Java 8+ hash function:
  int h = key.hashCode();
  h = h ^ (h >>> 16);       // XOR high bits into low bits (reduces collisions for bad hashCodes)
  bucket = h & (capacity-1) // capacity is always power of 2
```

```java
Map<String, Integer> map = new HashMap<>(64, 0.75f); // initial capacity, load factor

map.put("key", 1);           // O(1) avg
map.get("key");              // O(1) avg
map.getOrDefault("key", 0); // O(1) avg, returns default if missing
map.containsKey("key");      // O(1) avg

// Atomic compute operations (essential for concurrent patterns)
map.putIfAbsent("key", 1);                         // put only if not present
map.computeIfAbsent("key", k -> new ArrayList<>()); // creates value lazily
map.computeIfPresent("key", (k, v) -> v + 1);      // update only if present
map.compute("key", (k, v) -> v == null ? 1 : v+1); // unconditional compute
map.merge("key", 1, Integer::sum);                  // merge with BiFunction

// Iteration
map.forEach((k, v) -> process(k, v));        // O(capacity + size)
for (Map.Entry<K,V> e : map.entrySet()) { }  // most efficient iteration
map.entrySet().stream().filter(...).collect(Collectors.toMap(...));
```

---

## 6. LinkedHashMap — Access-Order and LRU Cache

`LinkedHashMap` maintains a doubly-linked list through entries. Two ordering modes:
- **Insertion order** (default): entries iterated in the order they were first inserted
- **Access order** (`true` as 3rd constructor arg): entries iterated from **least recently used** to most recently used — perfect for LRU caches

```java
// LRU cache using LinkedHashMap access-order
public class LRUCache<K,V> extends LinkedHashMap<K,V> {
    private final int maxCapacity;

    public LRUCache(int capacity) {
        super(capacity, 0.75f, true); // accessOrder = true
        this.maxCapacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return size() > maxCapacity; // evict least-recently-used when over capacity
    }
}

LRUCache<String, User> cache = new LRUCache<>(100);
cache.put("user:1", user);
cache.get("user:1"); // marks user:1 as most recently used
// When size > 100, the least recently accessed entry is automatically removed
```

---

## 7. TreeMap — SortedMap and NavigableMap

`TreeMap<K,V>` is a Red-Black tree. Keys are always sorted. `O(log n)` for all core operations:

```java
TreeMap<String, Integer> treeMap = new TreeMap<>();
treeMap.put("banana", 2);
treeMap.put("apple", 5);
treeMap.put("cherry", 1);

// Keys always iterated in sorted order
treeMap.keySet().forEach(System.out::println); // apple, banana, cherry

// NavigableMap operations — all O(log n)
treeMap.firstKey();            // "apple"
treeMap.lastKey();             // "cherry"
treeMap.floorKey("avocado");   // "apple" — greatest key ≤ "avocado"
treeMap.ceilingKey("avocado"); // "banana" — smallest key ≥ "avocado"
treeMap.lowerKey("banana");    // "apple" — strictly less than
treeMap.higherKey("banana");   // "cherry" — strictly greater than

// Range sub-maps (all return live views — changes to view affect original!)
treeMap.headMap("cherry");            // {apple: 5, banana: 2}  key < "cherry"
treeMap.headMap("cherry", true);      // {apple: 5, banana: 2, cherry: 1}  key <= "cherry"
treeMap.tailMap("banana");            // {banana: 2, cherry: 1}  key >= "banana"
treeMap.subMap("apple", "cherry");    // {apple: 5, banana: 2}   "apple" <= key < "cherry"

treeMap.descendingMap();   // reverse view
treeMap.descendingKeySet();

// Useful for range queries: find all events in the last hour
TreeMap<Instant, Event> timeline = new TreeMap<>();
Instant hourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
SortedMap<Instant, Event> recentEvents = timeline.tailMap(hourAgo);
```

---

## 8. WeakHashMap — Cache with GC-Eligible Keys

`WeakHashMap<K,V>` stores keys as `WeakReference` objects. When a key becomes unreachable (no strong reference in application code), the GC can collect it and the entry is automatically removed:

```java
WeakHashMap<Object, Metadata> metaCache = new WeakHashMap<>();

Object obj = new BigObject();
metaCache.put(obj, new Metadata("computed-metadata"));

// As long as obj has a strong reference, the entry persists
metaCache.get(obj); // returns the metadata

obj = null;  // strong reference dropped
System.gc(); // entry ~will be removed from WeakHashMap (non-deterministic)
metaCache.size(); // 0 (probably) — entry has been GC'd

// Use cases:
// - Caches where eviction matches object lifetime (metadata, computed annotations)
// - Class-to-data mappings in frameworks (Spring, Hibernate use this internally)

// WARNING: keys must NOT be string literals or primitives (autoboxed — they're always reachable)
// WARNING: not thread-safe — use Collections.synchronizedMap(new WeakHashMap<>()) for concurrent use
```

---

## 9. IdentityHashMap — Reference Equality

`IdentityHashMap` uses `==` (reference equality) and `System.identityHashCode()` instead of `equals()`/`hashCode()`:

```java
IdentityHashMap<String, Integer> map = new IdentityHashMap<>();
String a = new String("key");
String b = new String("key");

map.put(a, 1);
map.put(b, 2);
map.size(); // 2 — a != b (different references), even though a.equals(b)

// Use cases:
// - Object identity tracking (visited set in graph traversal)
// - Proxy/wrapper frameworks that need to track object instances
// - Serialization frameworks (track already-serialized objects to handle cycles)
```

---

## 10. EnumMap — Fastest Map for Enum Keys

`EnumMap<K extends Enum<K>, V>` is backed by a plain array (indexed by enum ordinal):
- All operations are O(1) array accesses — faster than `HashMap`
- No hash collision possible
- Memory-efficient (contiguous array)

```java
enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

EnumMap<Day, Integer> schedule = new EnumMap<>(Day.class);
schedule.put(Day.MON, 9);
schedule.put(Day.FRI, 18);

schedule.get(Day.MON);  // O(1) — literally: array[DAY.MON.ordinal()]

// Iteration: always in enum declaration order
schedule.forEach((day, hour) -> System.out.println(day + "=" + hour));
// Output: MON=9 FRI=18 (in ordinal order)
```

---

## 11. Immutable Maps

```java
// Map.of() — up to 10 entries, no nulls, implementation detail (order not guaranteed, random every JVM start)
Map<String, Integer> m = Map.of("a", 1, "b", 2, "c", 3);
m.put("d", 4); // UnsupportedOperationException

// Map.ofEntries() — unlimited entries
Map<String, Integer> m = Map.ofEntries(
    Map.entry("a", 1),
    Map.entry("b", 2)
);

// Map.copyOf() — defensive copy, immutable
Map<String, Integer> snapshot = Map.copyOf(mutableMap);

// Collections.unmodifiableMap() — view, reflects changes in backing map
Map<String, Integer> view = Collections.unmodifiableMap(mutableMap);
mutableMap.put("new", 99);
view.get("new"); // 99 — view is live!

// Collectors.toUnmodifiableMap() — terminal stream collector
Map<String, Integer> wordCounts = words.stream()
    .collect(Collectors.toUnmodifiableMap(w -> w, String::length, Integer::sum));
```

---

## 12. Collections Comparison Cheat Sheet

| Collection | Order | Null? | Thread-safe? | Time complexity |
|---|---|---|---|---|
| `HashSet` | None | Yes (one null) | No | O(1) avg |
| `LinkedHashSet` | Insertion | Yes (one null) | No | O(1) avg |
| `TreeSet` | Sorted | No | No | O(log n) |
| `EnumSet` | Enum ordinal | No | No | O(1) bitwise |
| `HashMap` | None | Yes | No | O(1) avg |
| `LinkedHashMap` | Insertion/Access | Yes | No | O(1) avg |
| `TreeMap` | Sorted | No for keys | No | O(log n) |
| `EnumMap` | Enum ordinal | No for keys | No | O(1) |
| `WeakHashMap` | None | Yes | No | O(1) avg |
| `IdentityHashMap` | None | Yes | No | O(1) avg |
| `ConcurrentHashMap` | None | No | Yes | O(1) avg |
| `ConcurrentSkipListMap` | Sorted | No | Yes | O(log n) |

---

## 13. Interview Q&A

**Q: Why does `HashSet` require overriding both `hashCode()` and `equals()`?**  
`HashSet` uses `hashCode()` to find the bucket, then `equals()` to confirm exact match. If two logically equal objects have different hash codes, they land in different buckets and `HashSet` considers them distinct — you'll have duplicates. If two objects are `equals()` but have different hash codes, the contract is broken. The rule: `a.equals(b)` ⟹ `a.hashCode() == b.hashCode()`. Always use `Objects.hash(field1, field2)` in `hashCode()` and compare all the same fields in `equals()`.

**Q: How would you implement an LRU cache in Java without external libraries?**  
Extend `LinkedHashMap` with `accessOrder = true` and override `removeEldestEntry`. The `LinkedHashMap` in access-order mode moves entries to the end of its internal linked list on every `get()` or `put()`. The eldest (head) entry is the one not accessed for the longest time — we remove it when `size() > capacity`. This gives O(1) get, O(1) put, and O(1) eviction.

**Q: When should you use `TreeMap` over `HashMap`?**  
Use `TreeMap` whenever you need: (1) sorted iteration over keys, (2) range queries (`subMap`, `headMap`, `tailMap`), (3) nearest-key lookups (`floorKey`, `ceilingKey`). Classic examples: leaderboard with rank range, time-series event lookup for a time window, scheduling by timestamp. `TreeMap` costs O(log n) vs O(1) for `HashMap`, so only use it when ordering/range features are needed.

**Q: What happens to a `WeakHashMap` entry when the key is a String literal?**  
String literals in Java are interned — they live in the constant pool and are held by strong references from the loaded class. They are never GC-eligible as long as the class is loaded. So a `WeakHashMap` entry with a string literal key behaves exactly like a regular `HashMap` entry — it is never automatically evicted. Use `new String("key")` if you actually want weak-reference semantics (though the use case would be unusual).
