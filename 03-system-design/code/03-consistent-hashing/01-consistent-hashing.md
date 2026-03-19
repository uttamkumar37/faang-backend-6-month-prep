# Consistent Hashing & Probabilistic Data Structures

## Why Consistent Hashing?

Naive modular hashing: `node = hash(key) % N`

Problem when a node is added or removed:
- Almost every key gets remapped to a different node.
- With 10 nodes, adding 1 node remaps ~91% of keys → cache storm.

Consistent hashing remaps only `K/N` keys on average.

---

## Consistent Hashing — The Ring

### Concept

```
                  0
             330      30
          300              60
       270                    90
          240              120
             210      150
                  180

Nodes mapped by hash of their ID:
  NodeA → 45°
  NodeB → 165°
  NodeC → 285°

Key mapping: hash(key) → angle → walk clockwise → first node
  key1 → 20°  → NodeA (at 45°)
  key2 → 80°  → NodeB (at 165°)
  key3 → 200° → NodeC (at 285°)
```

### Adding/Removing a Node

```
Add NodeD at 120°:
  Before: key2 (80°) → NodeB (165°)
  After:  key2 (80°) → NodeD (120°) ← only keys between old predecessor and NodeD remapped

Only ~1/N of keys move — instead of (N-1)/N with modular hashing.
```

---

## Virtual Nodes (VNodes)

Problem with basic ring: uneven load distribution when node count is small.

Solution: each physical node maps to **multiple points** on the ring.

```
NodeA → [45°, 135°, 225°, 315°]   (4 virtual nodes)
NodeB → [90°, 180°, 270°, 360°]
NodeC → [67°, 157°, 247°, 337°]

Load variance reduces from O(log N / N) to O(sqrt(log N / N)) with 100+ vnodes.
```

Benefits:
1. Even load distribution across heterogeneous nodes.
2. When a node fails, its keys spread evenly across ALL remaining nodes (not one neighbor).
3. Can assign more vnodes to more powerful nodes.

**Used by:** Cassandra (256 vnodes/node default), DynamoDB, Riak, chord DHT.

---

## Bloom Filter

A space-efficient probabilistic set. "Might be in set" or "definitely not in set."

### Structure

```
Bit array of m bits, k hash functions:

Insert("apple"):
  h1("apple") % m → bit 3
  h2("apple") % m → bit 7
  h3("apple") % m → bit 12
  Set bits 3, 7, 12 = 1

Check("cherry"):
  h1("cherry") % m → bit 3  ✓
  h2("cherry") % m → bit 9  ✗ → "definitely NOT in set"

Check("mango"):
  h1("mango") → bit 3  ✓
  h2("mango") → bit 7  ✓
  h3("mango") → bit 12 ✓ → "might be in set" (false positive possible!)
```

### False Positive Rate

$$P_{fp} = \left(1 - e^{-kn/m}\right)^k$$

Where: n = items inserted, m = bit array size, k = hash functions

Optimal k = (m/n) × ln(2) ≈ 0.693 × (m/n)

| m/n ratio | Optimal k | False positive rate |
|-----------|-----------|---------------------|
| 10        | 7         | 0.8%                |
| 20        | 14        | 0.04%               |
| 30        | 21        | 0.002%              |

### Real-World Uses

- **Chrome Safe Browsing**: Bloom filter for malicious URLs (avoids server lookup for benign URLs).
- **Cassandra**: Per-SSTable bloom filter to skip disk reads for missing keys.
- **HBase / BigTable**: avoid disk access for non-existent rows.
- **Akamai CDN**: "one-hit wonder" detection — don't cache items requested only once.
- **Databases**: Query optimizer checks bloom filter before join probe.

### Limitations

- No deletion (use Counting Bloom Filter for that).
- No enumeration or lookup of stored items.
- False positives possible; false negatives are impossible.

---

## Count-Min Sketch (Frequency Estimation)

Approximate frequency counter — like a bloom filter but stores counts.

```
d hash functions, each mapping to one of w counters:

Increment("GET /api/users"):
  h1(...) → row 0, col 3 → counter++
  h2(...) → row 1, col 7 → counter++
  h3(...) → row 2, col 1 → counter++

Estimate frequency of "GET /api/users":
  → min(row0[3], row1[7], row2[1])

Error bound: estimate ≤ true_count + ε * N
  with probability ≥ 1 - δ
  where w = ⌈e/ε⌉, d = ⌈ln(1/δ)⌉
```

**Used for:** real-time top-K heavy hitters, per-IP rate limiting, traffic analysis.

---

## HyperLogLog (Cardinality Estimation)

Estimate unique count (cardinality) using ~1.5 KB instead of storing all elements.

```
Algorithm idea:
  Hash each element → uniform bit string
  Track longest run of leading zeros: p = position of first 1
  If you see a string starting with 0...01 (p zeros), there are probably 2^p unique items.
  Use m registers and take harmonic mean for accuracy.

Error: ±1.04 / sqrt(m)   → with m=2048 registers: ±2.3% error

vs. HashSet: O(n) memory  vs. HyperLogLog: O(log log n) ≈ 12 KB for billions of items
```

**Used by:** Redis PFADD/PFCOUNT, BigQuery COUNT DISTINCT, Flink unique visitor counting.

---

## Rendezvous Hashing (HRW)

Alternative to consistent hashing. Each key picks node via max `hash(key, nodeId)`.

```
For key K and node list [A, B, C, D]:
  score_A = hash(K, "A") = 0.73
  score_B = hash(K, "B") = 0.21
  score_C = hash(K, "C") = 0.89  ← winner — K goes to C
  score_D = hash(K, "D") = 0.44

When C is removed: K recalculated → A wins (0.73)
```

**Advantage over CHR:** simpler, no virtual nodes needed, but O(n) lookup vs O(log n).

---

## Interview Quick Reference

| Problem | Solution |
|---------|----------|
| Distribute cache across changing nodes | Consistent hashing with vnodes |
| Check if URL was seen (avoid DB hit) | Bloom filter |
| Top-K most frequent requests | Count-Min Sketch |
| Count unique visitors per day | HyperLogLog |
| Shard assignment for n nodes | Rendezvous or consistent hashing |
| Avoid remapping all keys on resize | Consistent hashing (only K/N remapped) |
