# Networking for Backend Engineers

This module focuses on the networking depth often expected in strong backend interviews.

## Core topics

- TCP handshake and connection teardown
- latency budget: DNS, TCP, TLS, application processing
- HTTP/1.1 keep-alive vs HTTP/2 multiplexing
- connection pooling and head-of-line blocking
- retries, deadlines, and timeout budgets
- load balancer behavior at L4 vs L7
- gRPC vs REST trade-offs

## Mental models

- Network latency is additive. Small inefficiencies at several layers can dominate service time.
- A timeout is part of correctness, not only resilience.
- Retries can multiply load and cause a partial outage to become a full outage.
- Connection reuse is often one of the cheapest latency wins in backend systems.

## A request path to reason about

Client request path:
1. DNS lookup
2. TCP handshake
3. TLS handshake
4. load balancer hop
5. app processing
6. DB or downstream call
7. response transfer

If each step adds a few milliseconds, P99 grows quickly.

## Interview points that matter

### Timeouts and deadlines
- caller timeout must be shorter than user-visible deadline
- downstream timeout must leave budget for fallback or partial response
- timeout values should differ for connect, read, and whole-request budgets

### Retries
Safe when:
- operation is idempotent
- retry count is bounded
- exponential backoff and jitter are used

Dangerous when:
- write is not idempotent
- downstream is already overloaded
- many clients retry in lockstep

### Connection pooling
Know:
- warm pools reduce handshake cost
- too-small pools create queueing
- too-large pools can overload the dependency
- stale idle connections must be evicted carefully

## REST vs gRPC

Use REST when:
- public API compatibility matters
- browser support matters
- debugging with simple tools matters

Use gRPC when:
- internal service-to-service traffic dominates
- strict schemas and generated clients help
- streaming or low overhead matters

## Questions to practice

- Why does HTTP keep-alive improve backend performance?
- What is the difference between a connect timeout and a read timeout?
- When would HTTP/2 help and when would it not matter much?
- Why can retries worsen an outage?
