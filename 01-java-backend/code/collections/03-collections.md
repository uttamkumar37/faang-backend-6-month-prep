# Java Collections Internals

## 1. HashMap

### Internal structure

```
HashMap<K,V>
│
├── Node<K,V>[] table  (array of buckets)
│   Each bucket is a linked list (or Red-Black Tree when size > 8)
│
├── int size           (number of key-value pairs)
├── float loadFactor   (default 0.75)
└── int threshold      (resize when size > capacity × loadFactor)
```

### put() operation — step by step

```java
map.put("key", value);

1. Compute hash: hash = key.hashCode() ^ (h >>> 16)   // spread higher bits
2. bucket index = hash & (capacity - 1)               // fast modulo (power-of-2)
3. If bucket empty: insert new Node
4. If bucket has nodes (collision):
   a. Check each node: if key equals → replace value
   b. If no match: append to linked list  (or insert in Red-Black tree)
5. If list grows > TREEIFY_THRESHOLD (8) and table.length >= 64 → treeify
6. If size > threshold → resize (double capacity, rehash everything)
```

### Resize (rehash)

- New array = 2× old capacity.
- Every existing entry gets rehashed.
- Because capacity is always a power of 2, the new bucket index for an entry is either the same index OR `old index + oldCapacity` — no full hash recalculation needed, just a bit check.
- **Expensive operation** — avoid by pre-sizing: `new HashMap<>(expectedSize / 0.75 + 1)`.

### Collision handling — treeification

- `TREEIFY_THRESHOLD = 8`: list → Red-Black Tree (O(log n) per operation).
- `UNTREEIFY_THRESHOLD = 6`: tree → list on shrink.
- `MIN_TREEIFY_CAPACITY = 64`: don't treeify if table is small — resize instead.

### Time complexity

| Operation | Average | Worst (all same bucket) |
|---|---|---|
| get / put | O(1) | O(n) list / O(log n) tree |
| remove | O(1) | O(n) / O(log n) |

**FAANG pitfall**: Custom objects as keys must implement both `hashCode()` and `equals()`. Mutable keys are dangerous — if the key's hash changes after insertion, the entry becomes unfindable.

---

## 2. ConcurrentHashMap

### Java 8 design (not segment-based anymore)

```
Internal structure same as HashMap (Node[] table + trees).

Writes: CAS for empty buckets; synchronized on the BUCKET HEAD NODE only.
Reads: Lock-free (volatile reads). No locking at all for reads.

vs. Hashtable / Collections.synchronizedMap: those lock the ENTIRE map for every operation.
```

### Key operations

```java
// Safe concurrent operations
map.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()); // atomic if-absent
map.merge(key, 1, Integer::sum);      // atomic increment
map.compute(key, (k, v) -> v == null ? 1 : v + 1); // read-modify-write atomically
map.putIfAbsent(key, value);          // atomic only if absent
```

**Warning**: These methods ARE atomic. The lambda inside `computeIfAbsent` is called at most once and under synchronization. But do not do heavy work inside the lambda — you're holding a bucket lock.

### Size accuracy

`size()` returns an approximate count (uses `LongAdder` internally — sums cell values). Use `mappingCount()` for large maps (returns `long`, avoids int overflow).

### Null prohibition

`ConcurrentHashMap` does NOT allow null keys or null values. Rationale: in a concurrent context, you can't distinguish "key not present" from "key maps to null" without a separate lock.

---

## 3. LinkedHashMap — LRU Cache

```java
// Insertion-order or access-order
Map<K, V> lruCache = new LinkedHashMap<K, V>(capacity, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return size() > capacity;
    }
};
```

- `accessOrder = true` → get() moves entry to tail (most recently used).
- Override `removeEldestEntry` → evict head (least recently used) when over capacity.
- **Not thread-safe** — wrap with `Collections.synchronizedMap()` or use `LinkedHashMap` inside a class with `synchronized` methods.
- For production, use **Caffeine** (supports concurrency, expiry, refresh, stats).

---

## 4. TreeMap

```java
TreeMap<Integer, String> tree = new TreeMap<>();
tree.put(5, "five"); tree.put(2, "two"); tree.put(8, "eight");

tree.firstKey();            // 2
tree.lastKey();             // 8
tree.floorKey(6);           // 5 (largest key <= 6)
tree.ceilingKey(6);         // 8 (smallest key >= 6)
tree.headMap(5);            // {2=two} (exclusive)
tree.subMap(2, true, 8, false); // {2, 5}
```

- Red-Black Tree internally: O(log n) put/get/remove.
- **Use case**: range queries, leaderboards, sliding-window maximum, Dijkstra's (simulated priority queue), interval scheduling.

---

## 5. PriorityQueue

```
Min-heap by default. Binary heap stored in an array.

parent(i) = (i-1)/2,  leftChild(i) = 2i+1,  rightChild(i) = 2i+2
```

```java
// Min-heap (natural order)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max-heap
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());

// Custom: by frequency
PriorityQueue<int[]> freqHeap = new PriorityQueue<>(
    (a, b) -> Integer.compare(b[1], a[1])  // higher freq first
);

// Top-K pattern
PriorityQueue<Integer> topK = new PriorityQueue<>(k); // min-heap of size k
for (int num : nums) {
    topK.offer(num);
    if (topK.size() > k) topK.poll(); // remove smallest
}
// topK now holds k largest
```

**O(n) heapify** when built from collection: `new PriorityQueue<>(list)` is O(n), not O(n log n).

---

## 6. ArrayDeque

The best general-purpose `Stack` and `Queue` replacement:

```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(1); stack.push(2);
int top = stack.peek(); // 2
stack.pop();            // removes 2

Deque<Integer> queue = new ArrayDeque<>();
queue.offer(1); queue.offer(2);
queue.poll();   // removes 1 (FIFO)
```

- Backed by a resizable array (circular).
- Faster than `LinkedList` due to cache locality.
- Not thread-safe. For concurrent use: `LinkedBlockingDeque`.

---

## 7. Collections Selection Decision Tree

```
Need a map?
├── Concurrent access? → ConcurrentHashMap
├── Insertion or access order needed? → LinkedHashMap
├── Sorted/range queries? → TreeMap
└── General purpose single-thread? → HashMap

Need a list?
├── Frequent random-access reads? → ArrayList
├── Frequent insertions/deletions at arbitrary position? → LinkedList (rare in practice)
└── Thread-safe, read-heavy? → CopyOnWriteArrayList

Need a set?
├── Concurrent? → ConcurrentHashMap.newKeySet()
├── Sorted? → TreeSet
└── General? → HashSet

Need a queue?
├── Priority ordering? → PriorityQueue
├── Concurrent producer–consumer? → LinkedBlockingQueue (bounded)
├── Single-threaded stack/queue? → ArrayDeque
└── Delayed tasks? → DelayQueue
```

---

## 8. Interview Q&A

**Q: Why does HashMap use power-of-2 capacity?**  
It allows replacing an expensive modulo operation (`hash % capacity`) with a fast bitwise AND (`hash & (capacity - 1)`), which only works when capacity is a power of 2. Also, during resize, entries only move to one of two possible new positions (same index or index + oldCapacity), which Java's resize implementation exploits.

**Q: Why can't ConcurrentHashMap have null keys?**  
In a single-threaded HashMap, `containsKey` vs a null value is unambiguous. In a concurrent context, by the time you call `containsKey` after a null `get`, another thread could have removed the key. This ambiguity is a source of bugs, so `ConcurrentHashMap` forbids nulls entirely.

**Q: What is the internal structure change that made Java 8's HashMap faster under hash collisions?**  
Prior to Java 8, all buckets used linked lists, giving O(n) worst-case lookup when many keys hash to the same bucket. Java 8 introduced treeification: when a bucket's linked list grows beyond 8 nodes and the table capacity is at least 64, the list is converted to a Red-Black Tree giving O(log n) worst-case. This protects against hash flooding attacks and bad hash functions.
