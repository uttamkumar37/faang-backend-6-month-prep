# collections — Java Collections & Stream API

> **Learning path (2 of 8):** `1. 00-foundations` → **`2. collections`** → `3. concurrency` → `4. jvm` → `5. performance` → `6. linux-networking` → `7. springboot` → `8. testing-delivery`

Deep dives into every standard collection, their internal mechanics, and the Stream API.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-list-and-queues.md](01-list-and-queues.md) | ArrayList vs LinkedList internals, ArrayDeque, PriorityQueue (min-heap), when to choose each |
| 2 | [02-set-and-maps.md](02-set-and-maps.md) | HashMap/LinkedHashMap/TreeMap internals, hash collision, load factor, ConcurrentHashMap |
| 3 | [03-collections.md](03-collections.md) | Collections utility class, unmodifiable views, thread-safe wrappers, CopyOnWriteArrayList |
| 4 | [04-stream-api.md](04-stream-api.md) | Stream pipeline, lazy evaluation, stateful vs stateless ops, Collectors, parallelStream pitfalls |
| 5 | [CollectionsDeepDive.java](CollectionsDeepDive.java) | Runnable code tracing all the internals above |
| 6 | [LRUCacheImpl.java](LRUCacheImpl.java) | LRU cache using LinkedHashMap — classic interview implementation |
| 7 | [LRUCacheImplTest.java](LRUCacheImplTest.java) | Tests that validate the LRU eviction behaviour |
| 8 | [StreamApiExamples.java](StreamApiExamples.java) | Grouping, partitioning, flat-mapping, and collector composition examples |

## How this fits in the bigger picture

```
00-foundations/      ← language basics (generics, syntax) — do this first
collections/         ← YOU ARE HERE — data structure internals + Stream API
concurrency/         ← thread-safe collections, atomic operations
jvm/                 ← why ArrayList resize costs O(n) amortised (memory model)
```

## Study method

1. Read each markdown in order; draw the internal data structure (array, linked-node, red-black tree) on paper.
2. Open the corresponding Java file and trace through every method.
3. Implement `LRUCacheImpl` from scratch without looking.
4. Explain the Big-O complexity of every operation out loud.
