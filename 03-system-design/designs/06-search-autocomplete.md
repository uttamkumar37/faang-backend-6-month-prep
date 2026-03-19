# Design a Search Autocomplete System

## Requirements

**Functional:**
- Return top-5 suggestions matching the typed prefix.
- Ranked by search frequency.
- Suggestions update daily (no real-time).

**Non-Functional:**
- 10M DAU × 10 searches/day = 100M queries/day ≈ 1,200/sec.
- With prefix streaming: 5 chars per search = 6,000 req/sec.
- Latency: < 50ms.
- High availability.

---

## Core Data Structure: Trie

```
Corpus: ["car", "card", "care", "cat", "cats"]

          root
          / \
         c   ...
         |
         a
        /|\
       r  t
      /\   \
     (/) d  e  s
         |  |  |
        (card)(care)(cats)

Each node stores: top-5 popular queries containing this prefix.
```

Storing top-N at each node: O(1) prefix query, O(prefix_len) lookup.  
Space tradeoff: each node stores a small list.

---

## Building the Trie (Offline)

```
Daily aggregation pipeline:
1. Stream all searches from Kafka to Spark job.
2. Count frequency per query (last 7 days, weighted: today > yesterday).
3. Build trie: for each (query, freq) pair, insert into all prefix nodes.
4. Serialize trie to disk (using Kryo or protobuf).
5. Upload to S3.
6. Deploy: API servers reload from S3 snapshot (blue-green).
```

---

## API & Serving

```
GET /autocomplete?q=car

Response:
{
  "suggestions": [
    {"query": "car insurance", "frequency": 1200000},
    {"query": "car rental",    "frequency": 980000},
    {"query": "car wash",      "frequency": 850000},
    {"query": "car accident",  "frequency": 720000},
    {"query": "car dealership","frequency": 610000}
  ]
}
```

### Serving Architecture

```
Client → CDN (cache prefix queries for 5 min)
             ↓
         API Gateway
             ↓
      Autocomplete Service
      (trie loaded in-memory, multiple instances)
             ↓
         Redis (prefix → top5, for fallback if trie not warmed)
```

### Response Caching

```
Cache key: "ac:{prefix}" → sorted list of top-5
TTL: 5 minutes (suggestions change daily, but fresher cache = better)
Popular prefixes: "a", "th" — pre-warm on startup.
```

---

## Scale

| Problem | Solution |
|---|---|
| Trie too large for single machine | Shard by first character of prefix |
| High read QPS | Read-only instances behind LB, in-memory trie |
| Trie update frequency | Rebuild once/day, atomic hot-swap |
| Typo tolerance | Optional: fuzzy matching with edit distance ≤ 2 |
| Personalization | Blend global top-5 with user history (re-rank at query time) |

---

## Filter & Safety

- Block blocklisted terms (hate speech, malicious queries) — filter_list in Redis.
- Before insertion into trie: normalize (lowercase, trim, deduplicate).
- Review pipeline: human review queue for flagged queries.

---

## Interview Tips

- Trie with cached top-N per node is the core insight — state it early.
- Explain the offline build pipeline vs real-time updates (daily is fine for this volume).
- CDN caching for popular prefixes is a great optimization to mention.
- Briefly mention safe search (blocked terms) — shows product thinking.

---

## Project Structure

```
search-autocomplete/
├── src/main/java/com/autocomplete/
│   ├── AutocompleteApplication.java
│   ├── controller/
│   │   └── SuggestController.java      # GET /v1/suggest?q=pre
│   ├── service/
│   │   ├── AutocompleteService.java    # trie lookup + prefix cache
│   │   └── TrieBuildService.java       # offline builder from query logs
│   ├── trie/
│   │   ├── TrieNode.java               # node with topK cache
│   │   └── Trie.java                   # insert + topSuggestions
│   └── cache/
│       └── PrefixCacheService.java     # Redis: prefix → JSON array
└── pom.xml
```

## Core Implementation

```java
// Trie node — each node caches its own top-K suggestions
public class TrieNode {
    public final Map<Character, TrieNode> children = new HashMap<>();
    public boolean isEnd = false;
    public long frequency = 0;
    // Pre-computed top-5 terms reachable from this node (updated at build time)
    public final List<String> topK = new ArrayList<>();
}

// Trie — insert queries + retrieve top-N suggestions for a prefix
public class Trie {
    private final TrieNode root = new TrieNode();

    public void insert(String word, long frequency) {
        TrieNode cur = root;
        for (char c : word.toCharArray()) {
            cur.children.putIfAbsent(c, new TrieNode());
            cur = cur.children.get(c);
        }
        cur.isEnd = true;
        cur.frequency = frequency;
    }

    // Returns top-N suggestions for the given prefix
    public List<String> suggest(String prefix, int n) {
        TrieNode cur = root;
        for (char c : prefix.toCharArray()) {
            cur = cur.children.get(c);
            if (cur == null) return List.of();
        }
        // Use pre-cached topK if available (built offline)
        if (!cur.topK.isEmpty()) return cur.topK.subList(0, Math.min(n, cur.topK.size()));

        // DFS fallback (used during build, not production read path)
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        List<String> results = new ArrayList<>();
        dfs(cur, new StringBuilder(prefix), n, pq, results);
        return results;
    }

    private void dfs(TrieNode node, StringBuilder sb, int n,
                     PriorityQueue<long[]> pq, List<String> results) {
        if (node.isEnd) results.add(sb.toString()); // simplified
        for (var e : node.children.entrySet()) {
            sb.append(e.getKey());
            dfs(e.getValue(), sb, n, pq, results);
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    // Offline: pre-compute and cache topK at each prefix node
    public void buildTopK(int k) {
        buildTopKHelper(root, new StringBuilder(), k);
    }

    private List<String[]> buildTopKHelper(TrieNode node, StringBuilder sb, int k) {
        List<String[]> all = new ArrayList<>();
        if (node.isEnd) all.add(new String[]{sb.toString(), String.valueOf(node.frequency)});
        for (var e : node.children.entrySet()) {
            sb.append(e.getKey());
            all.addAll(buildTopKHelper(e.getValue(), sb, k));
            sb.deleteCharAt(sb.length() - 1);
        }
        // Sort by frequency desc, store top-k in this node
        all.sort((a, b) -> Long.compare(Long.parseLong(b[1]), Long.parseLong(a[1])));
        node.topK.clear();
        all.stream().limit(k).map(a -> a[0]).forEach(node.topK::add);
        return all;
    }
}

// API layer: Cache-first prefix lookup
@RestController
public class SuggestController {
    @Autowired AutocompleteService svc;

    @GetMapping("/v1/suggest")
    public List<String> suggest(@RequestParam String q) {
        if (q == null || q.isBlank()) return List.of();
        return svc.suggest(q.toLowerCase().trim(), 5);
    }
}

@Service
public class AutocompleteService {
    @Autowired Trie trie;
    @Autowired RedisTemplate<String, String> redis;
    @Autowired ObjectMapper mapper;

    public List<String> suggest(String prefix, int n) {
        // 1. Cache check (CDN-cacheable response for popular prefixes)
        String key = "suggest:" + prefix;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            try { return mapper.readValue(cached, new TypeReference<>() {}); }
            catch (Exception ignored) {}
        }

        // 2. Trie lookup
        List<String> results = trie.suggest(prefix, n);

        // 3. Cache result (10 min TTL)
        try { redis.opsForValue().set(key, mapper.writeValueAsString(results), Duration.ofMinutes(10)); }
        catch (Exception ignored) {}

        return results;
    }
}
```
