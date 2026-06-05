# Rate Limiter LLD

## Purpose
Design an in-process rate limiter library with clear algorithms and thread-safe state.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Limit requests per key.
- Support token bucket and sliding window.
- Return allowed/rejected with retry-after.
- Be thread-safe for concurrent calls.

## Entities
RateLimiter, RateLimitRule, TokenBucket, SlidingWindowCounter, Clock, Decision.

## Class Diagram
```text
RateLimiter -> RateLimitAlgorithm
TokenBucket implements RateLimitAlgorithm
SlidingWindow implements RateLimitAlgorithm
```

## APIs
- `Decision allow(String key)`
- `void updateRule(RateLimitRule rule)`

## Design Decisions
Use `Clock` injection for testability. Keep algorithm state per key. Synchronize per bucket or use concurrent primitives.

## Edge Cases
Clock jumps, first request, rule change, high-cardinality keys, retry-after calculation.

## Extensibility
Distributed Redis-backed limiter, hierarchical limits, tenant plans.

## Test Cases
Burst allowed up to capacity, refill over time, concurrent requests, unknown key default rule.

## Interview Explanation
Explain algorithm trade-off: token bucket handles bursts; sliding window is stricter but more stateful.

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
Example: A tenant with 10 requests/min and burst 5 can spend 5 immediately, then receives new tokens gradually based on elapsed time.
