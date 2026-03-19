# URL Shortener

## Requirements

**Functional:**
- Create a short URL from a long URL.
- Redirect short URL to original long URL.
- Custom aliases (optional).
- Link expiry (optional).

**Non-Functional:**
- 100M new URLs/day = ~1200 writes/sec.
- 10:1 read:write ratio = 12,000 reads/sec (redirect).
- Storage: 500 bytes/URL × 100M = 50 GB/day → 18 TB/year.
- Low latency redirects (< 50ms p99).
- High availability (99.99%).

---

## API Design

```
POST /api/v1/urls
  Body: { "longUrl": "https://...", "expiresIn": 365, "alias": "mylink" }
  Response: { "shortCode": "abc123", "shortUrl": "https://short.ly/abc123" }

GET /{shortCode}
  Response: 301 Redirect to longUrl (or 302 for analytics tracking)

DELETE /api/v1/urls/{shortCode}  [authenticated]
```

**301 vs 302**:
- 301 Permanent: browser caches, no future requests hit server → less load, no analytics.
- 302 Temporary: every click hits server → analytics possible.

---

## Short Code Generation

### Option 1: Base62 Encoding

```
CharSet = [0-9, a-z, A-Z]  → 62 characters
6 characters → 62^6 = 56.8 billion combinations
7 characters → 62^7 = 3.5 trillion

Algorithm:
1. Insert long URL into DB, get autoincrement ID (e.g. 12345678).
2. Encode ID to base62: 12345678 → "dnh75u"
3. Store mapping. Collision-free (deterministic).
```

```java
static final String CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

String toBase62(long id) {
    StringBuilder sb = new StringBuilder();
    while (id > 0) {
        sb.insert(0, CHARS.charAt((int)(id % 62)));
        id /= 62;
    }
    return sb.length() < 6 ? "0".repeat(6 - sb.length()) + sb : sb.toString();
}
```

### Option 2: Random hash + collision check

```
short = first 7 chars of MD5(longUrl + salt)
If collision: retry with different salt
```

### Option 3: Distributed ID (Snowflake)

For multi-DC setup: 64-bit ID with timestamp + datacenter + machine + sequence.

---

## High-Level Design

```
Client → Load Balancer → URL Shortener Service
                              ↓              ↓
                        Write Path       Read Path
                              ↓              ↓
                           DB Write    Cache (Redis)
                      (MySQL/Postgres)       ↓
                                         DB Read
                                    (if cache miss)
```

### Database Schema

```sql
CREATE TABLE urls (
    id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
    short_code VARCHAR(10)  UNIQUE NOT NULL,
    long_url   TEXT         NOT NULL,
    user_id    BIGINT,
    created_at TIMESTAMP    DEFAULT NOW(),
    expires_at TIMESTAMP,
    INDEX idx_short_code (short_code)
);
```

---

## Caching Strategy

- Cache: Redis, TTL-based (e.g., 24h).
- Key: `url:{shortCode}` → value: `longUrl`.
- Eviction: LRU.
- On redirect: check Redis first, fallback to DB.
- **Cache hit ratio**: ~80% (hot URLs).
- Estimated Redis size: 12K reads/sec × 50% cache miss = 6K DB reads/sec → with good cache, DB can handle this.

---

## Redirect Flow

```
GET /abc123
1. Check Redis: if hit → 302 Redirect + async log click event to Kafka
2. If miss → Query DB → 302 Redirect + store in Redis
3. If not found → 404
```

---

## Scale Considerations

| Problem | Solution |
|---|---|
| ID generation at scale | Snowflake IDs / distributed counter |
| DB write hotspot | Hash sharding on short_code |
| Read hotspot (viral link) | Redis cluster, CDN |
| Analytics | Kafka consumer writes click events to ClickHouse/BigQuery |
| Global latency | Multi-region with geo-DNS routing |

---

## Interview Tips

- Start with the simplest design (single DB + cache) then evolve.
- Explicitly discuss 301 vs 302 tradeoff — interviewers love this.
- Mention that base62 with autoincrement can expose business data (sequential IDs reveal volume) → use random generation in production.
- Security: rate limit POST endpoint, prevent malicious URLs (safe-browsing API check).

---

## Project Structure

```
url-shortener/
├── src/main/java/com/urlshortener/
│   ├── UrlShortenerApplication.java
│   ├── controller/
│   │   └── UrlController.java        # POST /api/v1/urls, GET /{code}
│   ├── service/
│   │   ├── UrlShortenerService.java  # core logic: encode, store, lookup
│   │   └── Base62Encoder.java        # 0-9 a-z A-Z = 62 chars
│   ├── repository/
│   │   └── UrlRepository.java        # Spring Data JPA → PostgreSQL
│   ├── cache/
│   │   └── UrlCacheService.java      # Redis cache: shortCode → longUrl
│   └── model/
│       └── UrlMapping.java           # id, shortCode, longUrl, expiresAt
├── src/main/resources/
│   └── application.yml
└── pom.xml
```

## Core Implementation

```java
// Base62 encoder — turns numeric ID into short code
public class Base62Encoder {
    private static final String CHARS =
        "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long id) {
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(CHARS.charAt((int)(id % 62)));
            id /= 62;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long id = 0;
        for (char c : code.toCharArray())
            id = id * 62 + CHARS.indexOf(c);
        return id;
    }
}

// Service — shorten + redirect
@Service
public class UrlShortenerService {
    @Autowired UrlRepository repo;
    @Autowired RedisTemplate<String, String> redis;

    public String shorten(String longUrl, Integer expiryDays) {
        // 1. Check for existing mapping (dedup)
        Optional<UrlMapping> existing = repo.findByLongUrl(longUrl);
        if (existing.isPresent()) return existing.get().getShortCode();

        // 2. Save to get DB-generated ID, then encode
        UrlMapping m = new UrlMapping();
        m.setLongUrl(longUrl);
        m.setExpiresAt(expiryDays != null
            ? Instant.now().plus(expiryDays, ChronoUnit.DAYS) : null);
        m = repo.save(m);                         // gets auto-increment id
        String code = Base62Encoder.encode(m.getId());
        m.setShortCode(code);
        repo.save(m);

        // 3. Cache: code → longUrl (TTL = expiry or 7 days)
        redis.opsForValue().set(code, longUrl,
            expiryDays != null ? Duration.ofDays(expiryDays) : Duration.ofDays(7));
        return code;
    }

    public String resolve(String code) {
        // Cache-first lookup
        String cached = redis.opsForValue().get(code);
        if (cached != null) return cached;

        // DB fallback
        return repo.findByShortCode(code)
            .filter(m -> m.getExpiresAt() == null || m.getExpiresAt().isAfter(Instant.now()))
            .map(UrlMapping::getLongUrl)
            .orElseThrow(() -> new NotFoundException(code));
    }
}

// Controller — 302 redirect
@RestController
public class UrlController {
    @Autowired UrlShortenerService svc;

    @PostMapping("/api/v1/urls")
    public ResponseEntity<Map<String,String>> create(@RequestBody CreateUrlRequest req) {
        String code = svc.shorten(req.getLongUrl(), req.getExpiresIn());
        return ResponseEntity.ok(Map.of(
            "shortCode", code,
            "shortUrl", "https://short.ly/" + code));
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        String longUrl = svc.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)   // 302 — preserves analytics
            .header(HttpHeaders.LOCATION, longUrl)
            .build();
    }
}
```
