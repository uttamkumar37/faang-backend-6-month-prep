# linux-networking — Linux OS & Backend Networking

> **Learning path (6 of 8):** `1. 00-foundations` → `2. collections` → `3. concurrency` → `4. jvm` → `5. performance` → **`6. linux-networking`** → `7. springboot` → `8. testing-delivery`

OS and network fundamentals that separate senior backend engineers from mid-level: process model, file descriptors, TCP lifecycle, and latency debugging.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-linux-and-process-basics.md](01-linux-and-process-basics.md) | Process vs thread model, file descriptors, `/proc` filesystem, signals, ulimits, context-switch cost |
| 2 | [02-networking-for-backend.md](02-networking-for-backend.md) | TCP three-way handshake, TLS 1.3 handshake, TIME_WAIT, keep-alive, HTTP/2 multiplexing, socket options, connection pooling |
| 3 | [LinuxAndNetworkingExamples.java](LinuxAndNetworkingExamples.java) | ThreadPoolSizing, DeadlineBudget, ConnectionPoolGuard, RetryPolicy — translating OS knowledge to Java service patterns |

## How this fits in the bigger picture

```
jvm/                 ← JVM memory and thread model
performance/         ← CPU profiling, GC tuning
linux-networking/    ← YOU ARE HERE — OS and network layer
springboot/          ← Spring's HTTP clients, RestTemplate, WebClient in practice
```

## Study method

1. Read `01-linux-and-process-basics.md` — run `cat /proc/self/status` and `lsof -p $$` in your terminal while reading.
2. Use `ss -s` and `netstat -an | grep TIME_WAIT` on your machine to see TCP state distribution live.
3. Read `02-networking-for-backend.md` — use Wireshark or `tcpdump` to capture a TLS handshake once.
4. Work through `LinuxAndNetworkingExamples.java` — verify your thread pool and connection pool sizing against the formulas.
5. Explain the consequence of TIME_WAIT accumulation in a high-throughput service out loud.
