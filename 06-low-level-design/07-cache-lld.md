# Cache LLD

## Purpose
Design an in-memory cache with TTL and eviction policy in Java.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Put, get, delete keys.
- Support max capacity.
- Support TTL expiry.
- Evict using LRU initially.
- Be clear about thread-safety scope.

## Entities
Cache, CacheEntry, EvictionPolicy, LruEvictionPolicy, Clock.

## Class Diagram
```text
Cache -> Map<Key, CacheEntry>
Cache -> EvictionPolicy
LruEvictionPolicy -> access order
```

## APIs
- `Optional<V> get(K key)`
- `void put(K key, V value, Duration ttl)`
- `void delete(K key)`

## Design Decisions
Separate storage from eviction policy. Remove expired entries on access and before capacity eviction.

## Edge Cases
Expired key, null key/value policy, capacity zero, updating existing key, concurrent access.

## Extensibility
LFU, metrics, loader function, write-through, Redis-backed cache.

## Test Cases
Get existing, expire entry, LRU eviction, update refreshes value, capacity boundary.

## Interview Explanation
State whether the cache is single-process. Do not imply distributed consistency unless designed.

## Interview Questions
- What are the core entities and invariants?
- Which operation needs concurrency control?
- What extension would be easiest with your design?

## Common Mistakes
- Skipping edge cases until after coding.
- Using too many abstract classes for a small prompt.
- No test plan for state transitions.

## Self-Check
- [ ] I can draw the text class diagram quickly.
- [ ] I can state at least five edge cases.
- [ ] I can point to the Java skeleton and explain the main flow.

## Practical Example
Example: Put A and B in capacity 2, read A, put C. B is evicted because A was recently accessed.
