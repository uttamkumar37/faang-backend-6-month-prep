# Capacity Estimation Cheat Sheet

> Quick reference for back-of-envelope math in system design interviews.

---

## Core Units

| Unit | Value |
|------|-------|
| 1 KB | 10^3 bytes (1,024 bytes) |
| 1 MB | 10^6 bytes |
| 1 GB | 10^9 bytes |
| 1 TB | 10^12 bytes |
| 1 PB | 10^15 bytes |
| 1 million = 10^6 | 1 billion = 10^9 |

---

## Time Conversions

| Period | Seconds |
|--------|---------|
| 1 minute | 60 |
| 1 hour | 3,600 |
| 1 day | 86,400 ≈ **10^5** |
| 1 month | 2,592,000 ≈ **2.5 × 10^6** |
| 1 year | 31,536,000 ≈ **3 × 10^7** |

### Trick: DAU → QPS
```
QPS = DAU × actions_per_day / 86,400
Peak QPS ≈ QPS × 2 to 5
```

---

## Typical Object Sizes

| Object | Size |
|--------|------|
| UUID / ID (string) | 36 bytes |
| Long integer | 8 bytes |
| Char (Unicode) | 2 bytes |
| Short tweet text | ~140 bytes |
| URL | ~100 bytes |
| Thumbnail image | ~200 KB |
| Profile photo | ~200 KB–1 MB |
| Web page (HTML) | ~100 KB |
| HD video (1 min) | ~100 MB |
| 4K video (1 min) | ~400 MB |
| Audio (1 min MP3) | ~1 MB |
| Email | ~50 KB avg |
| SQL row (typical) | ~1 KB |
| Log line | ~500 bytes |

---

## Latency Numbers Every Engineer Should Know

| Operation | Latency |
|-----------|---------|
| L1 cache read | 0.5 ns |
| Branch mis-predict | 5 ns |
| L2 cache read | 7 ns |
| Mutex lock/unlock | 25 ns |
| RAM read | 100 ns |
| Compress 1 KB (Snappy) | 3 µs |
| SSD random read | 16 µs |
| Read 1 MB from RAM | 250 µs |
| Round trip in same DC | 500 µs |
| Redis GET | ~1 ms |
| HDD seek | 10 ms |
| DB read (no cache) | 10–50 ms |
| Packet CA → Netherlands | 150 ms |
| Packet CA → Australia | 200 ms |

### Ratios to remember
- Memory is ~1,000× faster than SSD
- SSD is ~100× faster than HDD
- RAM reads ~40× faster than SSD
- Network (same DC) ~10× faster than HDD

---

## Storage Estimation Formula
```
storage_per_day = DAU × writes_per_day × avg_object_size
storage_5_years = storage_per_day × 365 × 5
```

### Add replication factor (usually 3×):
```
actual_storage = storage_5_years × 3
```

---

## Common System Estimates

### URL Shortener (bit.ly scale)
```
Write: 100M new URLs/day → 1,160 writes/sec
Read:  100:1 read/write ratio → 116,000 reads/sec
Storage per URL: 500 bytes
5-year storage: 100M × 500B × 365 × 5 = 91 TB
```

### Twitter / Social Feed
```
DAU: 300M users; 5% post → 15M tweets/day → 175 tweets/sec
Read: 300M × 20 reads/day = 6B reads/day → 69,000 reads/sec
Tweet size: ~280 chars + metadata = ~1 KB
5-year storage: 175 TPS × 3,600 × 24 × 365 × 5 × 1KB ≈ 28 TB
Media storage (photos): separate, typically 100× text
```

### Notification System
```
DAU: 100M; avg 5 notifications/day → 500M/day → 5,780/sec
Peak (promo blast): 10-20× → 100,000/sec burst
Payload per notification: ~1 KB
Daily volume: 500M × 1KB = 500 GB/day
```

### Video Streaming (YouTube scale)
```
DAU: 1B users; avg watch 30 min/day
Storage: 500 hours video uploaded/min → 30,000 min video/min
One minute HD video: 100 MB → 3 TB ingested/min → 4.3 PB/day (raw)
With encoding (multiple qualities): ~10× → 43 PB/day
CDN bandwidth: 1B × 30 min × 2 Mbps = 60 Exabits/day
```

### Distributed Cache (Redis)
```
Cache hit rate target: 80–99%
Memory per server: 72–256 GB RAM
100M objects × 1 KB = 100 GB → fits in 2 Redis nodes
Throughput: single Redis node ~100K ops/sec
```

### Google Drive / File Storage
```
DAU: 500M; avg file size: 500 KB; 2 files/day
Upload: 500M × 2 × 500KB = 500 TB/day
With chunking (4 MB chunks): 500 TB / 4 MB = 125M chunks/day
5-year storage: 500 TB × 365 × 5 = 912 PB ≈ 1 EB
```

### Payment System
```
Peak TPS (global scale): 10,000–100,000 TPS (Visa handles ~65,000 TPS peak)
Typical fintech: 1,000–10,000 TPS peak
Transaction record: ~500 bytes
Ledger record: ~200 bytes (debit + credit = 2 rows)
1M TPS × 700 bytes × 86,400 = 60 TB/day
```

### Rate Limiter
```
Per-user: O(1) Redis GET/SET per request + TTL
Global: single Redis counter + sliding window log
Memory: 100M users × 20 bytes/counter = 2 GB (trivial)
```

---

## Server Capacity Rules of Thumb

| Resource | Rule |
|----------|------|
| CPU-bound server | ~1,000 req/sec per core |
| I/O-bound server | ~10,000 req/sec (async) |
| MySQL | ~1,000 writes/sec, 10,000 reads/sec |
| Redis | ~100,000 ops/sec single node |
| Kafka | ~1M msgs/sec per broker (with batching) |
| Cassandra | ~50,000 writes/sec per node |
| S3 / blob store | Effectively unlimited, ~5 GB/s bandwidth |
| CDN edge | 10–100 Gbps bandwidth |

---

## Bandwidth Estimation
```
bandwidth = QPS × avg_response_size
1 Gbps network = 125 MB/s = ~1,000 req/sec for 1 MB responses
```

---

## 3-Step Estimation Framework (Interview)

```
1. SCALE
   - How many daily active users (DAU)?
   - Read/write ratio? (90:10 typical for social apps)
   - Peak vs average: peak = 2×–5× average

2. STORAGE
   - Object size × writes per day × retention period × replication

3. THROUGHPUT
   - QPS = DAU × actions / 86,400
   - Bandwidth = QPS × payload_size
```

---

## Quick Decimal Shortcuts

| Expression | Shortcut |
|------------|----------|
| 10^3 | 1 thousand |
| 10^6 | 1 million |
| 10^9 | 1 billion |
| 10^12 | 1 trillion |
| 2^10 | 1,024 ≈ 10^3 |
| 2^20 | 1,048,576 ≈ 10^6 |
| 2^30 | 1,073,741,824 ≈ 10^9 |

---

## Interview Tips

- **Always state your assumptions out loud** — interviewers want to see your reasoning
- **Round aggressively** — 86,400 → 10^5, 1,024 → 10^3 is fine
- **Use the numbers to justify decisions** — "100K QPS means we need multiple cache nodes"
- **Don't panic on impossible numbers** — start from DAU and build up
- **Typical FAANG DAU ranges**: small=1M, medium=100M, large=1B
