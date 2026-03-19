# Testing Strategy for Backend Engineers

This module covers the testing depth expected beyond basic unit tests.

## Testing pyramid for backend systems

- unit tests for pure logic and boundary conditions
- slice tests for controllers, repositories, and serialization
- integration tests with real dependencies using Testcontainers
- contract tests for service boundaries
- selective end-to-end tests for user-critical flows

## What strong interview answers include

- why the test exists
- which failures it isolates
- which dependencies are mocked vs real
- what level of confidence it provides
- what it does not prove

## Typical backend test stack

- JUnit 5
- Mockito for unit tests
- Spring Boot test slices
- Testcontainers for PostgreSQL, Redis, Kafka
- WireMock or mock web servers for external APIs

## What to test by layer

### Unit tests
- pricing logic
- idempotency decision rules
- retry policy calculation
- validation and mapping logic

### Repository tests
- query correctness
- indexing assumptions in hot queries
- transaction behavior

### Controller tests
- status codes
- request validation
- auth and authorization boundaries
- error contract shape

### Integration tests
- outbox to Kafka flow
- DB transaction + event publication behavior
- cache invalidation side effects

## Common mistakes

- too many mocks for behavior that should be integration-tested
- asserting implementation details instead of business outcomes
- ignoring unhappy paths and timeout behavior
- no tests for idempotency, retries, or concurrency-sensitive logic

## Questions to be ready for

- When would you use Testcontainers instead of mocks?
- What should remain unit-tested even in a heavily integrated service?
- How do you test retry or circuit-breaker behavior deterministically?
