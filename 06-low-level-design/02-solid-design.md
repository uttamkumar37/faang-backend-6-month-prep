# SOLID Design

## Purpose
Use SOLID principles as a practical review tool rather than a buzzword list.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Practical SOLID
| Principle | Interview use |
|---|---|
| Single Responsibility | One class owns one reason to change |
| Open/Closed | Add new pricing or eviction policy without rewriting core flow |
| Liskov | Subtypes must preserve expected behavior |
| Interface Segregation | Do not force appenders to implement unrelated methods |
| Dependency Inversion | Services depend on abstractions for external systems |

## Backend Examples
- Payment gateway interface for provider-specific implementations.
- Eviction policy interface for cache algorithms.
- Pricing strategy for parking lot fees.

## Interview Questions
- Which class owns the invariant?
- What behavior varies and should be abstracted?
- How would you test the design without external systems?

## Common Mistakes
- Using design patterns as decoration.
- No API surface before implementation.
- Mixing persistence, business rules, and presentation in one class.

## Self-Check
- [ ] I can justify every interface.
- [ ] I can state invariants for each entity.
- [ ] I can explain what I intentionally left out.

## Practical Example
Example: `ParkingFeeCalculator` lets you add weekend pricing without editing ticket allocation or spot management code.
