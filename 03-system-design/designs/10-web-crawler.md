# System Design: Web Crawler

**Difficulty:** Hard | **Frequency:** Very High (Google, Amazon)

---

## 1. Requirements Clarification

### Functional
- Download and **store web pages** starting from a seed URL list
- **Parse** pages: extract links (href) to discover new URLs
- **Deduplication**: never crawl the same URL twice
- Respect **robots.txt** (politeness)
- Support **re-crawling** pages on a schedule (freshness)
- Store: URL + raw HTML + timestamp + HTTP status

### Non-Functional
- Scale: crawl 1 billion pages
- Performance: 1,000–10,000 pages/sec throughput
- Distributed: multiple crawler workers
- Fault-tolerant: worker crash should not lose discovered URLs
- Polite: max 1 request/sec per domain (crawl delay)

---

## 2. Capacity Estimation

```
Target: 1 billion pages in 30 days
Required throughput: 1B / (30 × 86,400) ≈ 385 pages/sec
Safe target with headroom: 1,000 pages/sec

Average web page: 100 KB HTML
Storage: 1B × 100 KB = 100 TB raw HTML
Compressed (gzip ~5×): ~20 TB
Extracted links: avg 100 links/page = 100B links → ~5.5 TB at 60 bytes/URL

DNS lookups: 1000/sec × (1 new domain per 10 crawls) = 100 DNS/sec
```

---

## 3. High-Level Architecture

```
                      ┌─────────────────┐
                      │   Seed URLs     │
                      └────────┬────────┘
                               │
                               ▼
                      ┌─────────────────┐
                      │  URL Frontier   │ ◄──────────────────────┐
                      │  (Priority Q)   │                        │
                      └────────┬────────┘                        │
                               │ pop URL                         │
                               ▼                                 │
                   ┌───────────────────────┐              new URLs
                   │  Crawler Workers       │                    │
                   │  (distributed fleet)   │                    │
                   └────────────┬──────────┘                     │
                                │                                │
                  ┌─────────────┤──────────────┐                │
                  ▼             ▼              ▼                │
           ┌──────────┐  ┌──────────┐  ┌──────────────┐        │
           │ robots.txt│  │   DNS    │  │  HTTP Fetch  │        │
           │  Cache   │  │  Cache   │  │   (async)    │        │
           └──────────┘  └──────────┘  └──────┬───────┘        │
                                               │ HTML            │
                                               ▼                │
                                      ┌─────────────────┐       │
                                      │  HTML Parser    │       │
                                      └────────┬────────┘       │
                                               │                │
                              ┌────────────────┤                │
                              ▼                ▼                │
                       ┌──────────┐    ┌───────────────┐       │
                       │ Content  │    │   URL Filter   │       │
                       │ Store    │    │ (dedup + norm) │ ──────┘
                       │ (S3)     │    └───────────────┘
                       └──────────┘
```

---

## 4. Core Components

### 4.1 URL Frontier (Scheduler)
The heart of the crawler — decides **what to crawl next** and **when**.

**Structure:**
- Priority queue: score = freshness × importance (PageRank seed)
- Per-domain back queue: enforce crawl delay (1 req/sec per domain)
- Persistent: backed by Redis or a DB (not in-memory — crash safety)

**URL scoring:**
```
score = (1 / days_since_last_crawl) × link_popularity_score
```

**Implementation pattern:**
1. Front queue: sorted by priority (many queues, each for a priority tier)
2. Back queue: one per domain, enforces politeness window
3. Mapping table: domain → back queue index

### 4.2 URL Filter & Deduplication
- **Normalization**: lowercase scheme+host, remove fragments, canonical path
- **Dedup**: Bloom filter (memory-efficient, allows rare false positives)
  - 1B URLs × 10 bits each ≈ 1.25 GB (fits in RAM)
  - False positive rate ~0.1% at this scale (acceptable)
- **Seen URLs**: also stored in DB for exact match after Bloom filter pass

### 4.3 DNS Resolver Cache
- DNS lookups: bottleneck at high QPS
- Local DNS cache: hostname → IP, TTL = 10 min
- Distributed DNS cache: Redis (hostname → IP), TTL matching DNS record

### 4.4 HTML Fetcher
- Async HTTP/2 client (non-blocking)
- Connection pool per domain (limit concurrent requests)
- Timeout: 30s connect, 60s read
- Follow redirects (max 5 hops)
- Decompress: handle gzip, brotli
- User-Agent: proper bot identification (respect anti-bot measures ethically)

### 4.5 robots.txt Respect
```
robots.txt rules (per domain):
  User-agent: *
  Disallow: /admin/
  Crawl-delay: 2

Implementation:
  - Fetch robots.txt on first visit to a domain
  - Cache it for 24 hours
  - Before crawling any URL: check if path is disallowed
  - Honor Crawl-delay directive
```

### 4.6 HTML Parser & Link Extractor
- Extract all `<a href="...">` tags
- Resolve relative URLs to absolute
- Filter: only http/https, skip mailto/tel/javascript schemes
- Content type check: only parse `text/html` (skip PDF, images)
- Checksums: `SHA-256(content)` stored — skip re-store if unchanged (delta)

### 4.7 Content Store
- Raw HTML: S3 / blob store (keyed by URL hash)
- Metadata: PostgreSQL (url, checksum, crawled_at, http_status, content_size)
- Elasticsearch: full-text index of extracted text (for search use cases)

---

## 5. Database Design

### Crawl Records (PostgreSQL)
```sql
CREATE TABLE crawl_records (
    url_hash    CHAR(64)  PRIMARY KEY,   -- SHA-256 of normalized URL
    url         TEXT      NOT NULL,
    domain      VARCHAR(255),
    http_status SMALLINT,
    content_sha CHAR(64),                -- detect unchanged content
    crawled_at  TIMESTAMP,
    next_crawl  TIMESTAMP,               -- for recrawl scheduling
    INDEX idx_next_crawl (next_crawl),
    INDEX idx_domain    (domain)
);
```

### URL Frontier (Redis Sorted Set)
```
ZADD frontier <score> <normalized_url>
ZPOPMAX frontier  → get highest priority URL

Per-domain back queues:
LPUSH domain:queue:<domain_hash> <url>
BLPOP domain:queue:<domain_hash> (with rate limiting via token bucket)
```

---

## 6. Distributed Design

### Worker Coordination
- Workers poll URL Frontier (Redis ZPOPMAX — atomic, no double-crawl)
- Worker claims a URL → sets `status=IN_PROGRESS` with TTL in Redis
- If worker crashes: TTL expires → URL re-queued automatically
- Shard by domain hash → each worker owns a set of domains (locality for DNS cache)

### Politeness Enforcement
```
For each domain:
  - token_bucket[domain] = 1 token/sec
  - Worker checks: tryAcquire(domain) before fetching
  - If dry: re-queue URL with delay timestamp
```

### Scale-out
- URL Frontier: Redis Cluster with multiple shards (partition by domain hash)
- Workers: horizontally scaled containers (each independently polls)
- Content store: S3 (unlimited scale)
- Metadata DB: PostgreSQL with hash partitioning on `url_hash`

---

## 7. Project Structure

```
web-crawler/
├── frontier/
│   ├── UrlFrontier.java         (priority queue + per-domain back queues)
│   └── DomainScheduler.java     (politeness + crawl delay)
├── fetcher/
│   ├── HttpFetcher.java         (async HTTP + robots.txt)
│   └── DnsCache.java
├── parser/
│   ├── HtmlParser.java          (link extraction)
│   └── UrlNormalizer.java
├── dedup/
│   └── BloomFilterDedup.java
├── storage/
│   ├── ContentStore.java        (S3 client)
│   └── CrawlRecordRepository.java
└── CrawlerWorker.java           (main loop)
```

---

## 8. Core Implementation

```java
package systemdesign.webcrawler;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

public class WebCrawlerExamples {

    // --- URL Normalizer ---

    static class UrlNormalizer {
        public String normalize(String raw, String baseUrl) {
            try {
                URI base = new URI(baseUrl);
                URI resolved = base.resolve(raw.trim());

                // lowercase scheme + host, strip fragment
                String normalized = new URI(
                    resolved.getScheme().toLowerCase(),
                    resolved.getUserInfo(),
                    resolved.getHost() != null ? resolved.getHost().toLowerCase() : null,
                    resolved.getPort(),
                    resolved.getPath(),
                    resolved.getQuery(),
                    null   // strip fragment
                ).toString();

                // Remove trailing slash for consistency (except root)
                if (normalized.endsWith("/") && normalized.split("/").length > 3) {
                    normalized = normalized.substring(0, normalized.length() - 1);
                }
                return normalized;
            } catch (URISyntaxException e) {
                return null;
            }
        }

        public String extractDomain(String url) {
            try {
                return new URI(url).getHost();
            } catch (URISyntaxException e) {
                return null;
            }
        }
    }

    // --- Bloom Filter Deduplication ---

    static class BloomFilterDedup {
        private final long[] bits;
        private final int numHashFunctions;
        private final long size;

        // ~1.25 GB for 1B URLs at 10 bits each, here we use small size for demo
        BloomFilterDedup(long numExpectedItems, double falsePositiveRate) {
            // Optimal size formula: m = -n*ln(p) / (ln(2)^2)
            this.size = (long) (-numExpectedItems * Math.log(falsePositiveRate) / (Math.log(2) * Math.log(2)));
            this.bits = new long[(int) (size / 64) + 1];
            this.numHashFunctions = (int) Math.round((size / numExpectedItems) * Math.log(2));
            System.out.printf("BloomFilter: size=%d bits (%.1f MB), k=%d hash functions%n",
                size, size / 8.0 / 1024 / 1024, numHashFunctions);
        }

        public boolean mightContain(String url) {
            long h1 = url.hashCode();
            long h2 = url.chars().asLongStream().reduce(31L, (a, b) -> a * 31 + b);
            for (int i = 0; i < numHashFunctions; i++) {
                long pos = Math.abs((h1 + i * h2) % size);
                if ((bits[(int)(pos / 64)] & (1L << (pos % 64))) == 0) return false;
            }
            return true;
        }

        public void add(String url) {
            long h1 = url.hashCode();
            long h2 = url.chars().asLongStream().reduce(31L, (a, b) -> a * 31 + b);
            for (int i = 0; i < numHashFunctions; i++) {
                long pos = Math.abs((h1 + i * h2) % size);
                bits[(int)(pos / 64)] |= (1L << (pos % 64));
            }
        }
    }

    // --- Per-Domain Rate Limiter (Token Bucket) ---

    static class DomainRateLimiter {
        private final Map<String, Long> lastCrawledMs = new ConcurrentHashMap<>();
        private final long minIntervalMs; // e.g. 1000ms = 1 req/sec

        DomainRateLimiter(long minIntervalMs) {
            this.minIntervalMs = minIntervalMs;
        }

        public boolean tryAcquire(String domain) {
            long now = System.currentTimeMillis();
            long lastMs = lastCrawledMs.getOrDefault(domain, 0L);
            if (now - lastMs >= minIntervalMs) {
                lastCrawledMs.put(domain, now);
                return true;
            }
            return false;
        }

        public long msUntilAllowed(String domain) {
            long lastMs = lastCrawledMs.getOrDefault(domain, 0L);
            long elapsed = System.currentTimeMillis() - lastMs;
            return Math.max(0, minIntervalMs - elapsed);
        }
    }

    // --- HTML Link Extractor ---

    static class LinkExtractor {
        private static final Pattern HREF_PATTERN =
            Pattern.compile("<a[^>]+href=[\"']([^\"'#]+)[\"']", Pattern.CASE_INSENSITIVE);
        private final UrlNormalizer normalizer = new UrlNormalizer();

        public Set<String> extractLinks(String html, String pageUrl) {
            Set<String> links = new LinkedHashSet<>();
            Matcher m = HREF_PATTERN.matcher(html);
            while (m.find()) {
                String raw = m.group(1).trim();
                // Skip non-http schemes
                if (raw.startsWith("mailto:") || raw.startsWith("tel:") ||
                    raw.startsWith("javascript:")) continue;

                String normalized = normalizer.normalize(raw, pageUrl);
                if (normalized != null &&
                    (normalized.startsWith("http://") || normalized.startsWith("https://"))) {
                    links.add(normalized);
                }
            }
            return links;
        }
    }

    // --- URL Priority Queue (Frontier) ---

    static class UrlFrontier {
        // Priority queue: (score, url) — higher score = crawl sooner
        private final PriorityQueue<UrlEntry> queue =
            new PriorityQueue<>(Comparator.comparingDouble(UrlEntry::score).reversed());
        private final BloomFilterDedup dedup;
        private final DomainRateLimiter rateLimiter;

        UrlFrontier(int expectedUrls) {
            this.dedup = new BloomFilterDedup(expectedUrls, 0.001);
            this.rateLimiter = new DomainRateLimiter(1000); // 1 req/sec per domain
        }

        public void add(String url, double score) {
            if (dedup.mightContain(url)) return; // already seen (or false positive)
            dedup.add(url);
            synchronized (queue) { queue.add(new UrlEntry(url, score)); }
        }

        public Optional<UrlEntry> poll(String preferDomain) {
            synchronized (queue) {
                // Try to get a URL from a domain we're allowed to crawl
                List<UrlEntry> deferred = new ArrayList<>();
                UrlEntry chosen = null;

                while (!queue.isEmpty()) {
                    UrlEntry candidate = queue.poll();
                    String domain = new UrlNormalizer().extractDomain(candidate.url());
                    if (domain != null && rateLimiter.tryAcquire(domain)) {
                        chosen = candidate;
                        break;
                    }
                    deferred.add(candidate);
                }

                queue.addAll(deferred); // put back deferred entries
                return Optional.ofNullable(chosen);
            }
        }

        public int size() { synchronized (queue) { return queue.size(); } }

        record UrlEntry(String url, double score) {}
    }

    // --- Crawler Worker ---

    static class CrawlerWorker implements Runnable {
        private final UrlFrontier frontier;
        private final LinkExtractor extractor = new LinkExtractor();
        private final AtomicLong crawledCount;

        CrawlerWorker(UrlFrontier frontier, AtomicLong crawledCount) {
            this.frontier = frontier;
            this.crawledCount = crawledCount;
        }

        @Override
        public void run() {
            while (frontier.size() > 0) {
                Optional<UrlFrontier.UrlEntry> entry = frontier.poll(null);
                if (entry.isEmpty()) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    continue;
                }

                String url = entry.get().url();
                String html = fetch(url); // in production: async HTTP
                if (html == null) continue;

                // Store content (skip for demo)
                crawledCount.incrementAndGet();
                System.out.printf("[Worker-%s] Crawled: %s (total: %d)%n",
                    Thread.currentThread().getName(), url, crawledCount.get());

                // Extract and enqueue new links
                Set<String> links = extractor.extractLinks(html, url);
                for (String link : links) {
                    frontier.add(link, 0.5); // default score
                }
            }
        }

        private String fetch(String url) {
            // Simulated fetch — in production: async HttpClient
            return "<html><body><a href='/page1'>Link 1</a><a href='https://other.com/p2'>Link 2</a></body></html>";
        }
    }

    // --- Demo ---

    public static void main(String[] args) throws Exception {
        System.out.println("=== WEB CRAWLER DEMO ===\n");

        UrlNormalizer normalizer = new UrlNormalizer();
        System.out.println("Normalized: " + normalizer.normalize("/about", "https://example.com/page"));
        System.out.println("Normalized: " + normalizer.normalize("../news", "https://example.com/blog/post"));

        System.out.println("\n--- Bloom Filter ---");
        BloomFilterDedup dedup = new BloomFilterDedup(1_000_000, 0.001);
        dedup.add("https://example.com");
        System.out.println("Contains example.com: " + dedup.mightContain("https://example.com"));
        System.out.println("Contains other.com:   " + dedup.mightContain("https://other.com"));

        System.out.println("\n--- Link Extraction ---");
        LinkExtractor extractor = new LinkExtractor();
        String html = "<html><a href='/page1'>P1</a><a href='https://other.com/x'>Ex</a><a href='mailto:a@b.com'>Mail</a></html>";
        System.out.println("Extracted: " + extractor.extractLinks(html, "https://mysite.com/home"));

        System.out.println("\n--- Crawl Frontier ---");
        UrlFrontier frontier = new UrlFrontier(1_000_000);
        frontier.add("https://example.com", 1.0);
        frontier.add("https://example.com/page1", 0.8);
        frontier.add("https://other.com/news", 0.9);
        frontier.add("https://example.com", 1.0); // duplicate — should be filtered
        System.out.println("Frontier size (should be 3): " + frontier.size());

        AtomicLong crawledCount = new AtomicLong(0);
        CrawlerWorker worker = new CrawlerWorker(frontier, crawledCount);
        Thread workerThread = new Thread(worker, "w-1");
        workerThread.start();
        workerThread.join(3000);

        System.out.printf("%nTotal crawled: %d%n", crawledCount.get());
    }
}
```

---

## 9. Interview Tips

| Area | Key Points |
|------|-----------|
| URL dedup | Bloom filter for scale (1.25 GB for 1B URLs); exact DB check as fallback |
| Politeness | Per-domain rate limit + robots.txt; essential for not getting IP-banned |
| Fault tolerance | Redis-backed frontier + TTL claim; crash recovery is automatic |
| Scale | DNS cache, connection pooling, async I/O are the three main performance levers |
| Freshness | Score = recency × importance; high-traffic pages recrawled more often |
| Traps | Detect crawler traps (infinite URL generation): max URL length, max crawl depth |
| Distributed | Partition URL space by domain hash across workers for DNS cache locality |
