# Low-Level Design and Machine Coding

## Purpose
Prepare Java-focused LLD and machine-coding rounds with practical requirements, entities, APIs, edge cases, and skeleton implementations.

## Study Steps
- Study principles and SOLID first.
- Implement one design every two weeks from month 2 onward.
- Time-box machine coding to 90-120 minutes.
- After each design, explain extension points and tests.

## Design Set
| Design | Notes | Code |
|---|---|---|
| Parking Lot | Allocation, pricing, ticket lifecycle | code/parkinglot/ParkingLotDesign.java |
| Splitwise | Expenses, balances, settlement | code/splitwise/SplitwiseDesign.java |
| Rate Limiter | Token bucket/sliding window choices | code/ratelimiter/RateLimiterDesign.java |
| Cache | Eviction and concurrency boundaries | code/cache/CacheDesign.java |
| Logging Framework | Levels, appenders, formatting | code/logging/LoggingFrameworkDesign.java |
| Elevator | Scheduling and state machines | code/elevator/ElevatorSystemDesign.java |
| BookMyShow | Seats, shows, booking, payments | code/bookmyshow/BookMyShowDesign.java |

## Interview Flow
1. Requirements.
2. Entities and relationships.
3. APIs.
4. Class diagram in text.
5. Edge cases and concurrency.
6. Skeleton code.
7. Tests and extensions.

## Interview Questions
- How do you avoid overengineering LLD?
- Where do you place business invariants?
- What tests prove this design works?

## Common Mistakes
- Starting with patterns instead of requirements.
- No state transition rules.
- Ignoring concurrency in booking, cache, or rate limiter designs.

## Self-Check
- [ ] I can complete one LLD in 45 minutes on paper.
- [ ] I can code a small working skeleton in Java.
- [ ] Every design has edge cases and tests.

## Practical Example
Example: In BookMyShow, seat locking and payment expiry are core invariants; UI search filters are secondary and should not dominate the design.
