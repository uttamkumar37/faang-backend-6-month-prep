# Design Patterns in Interviews

## Purpose
Apply design patterns only when they simplify a real variation point in the prompt.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Useful Patterns
| Pattern | Good use |
|---|---|
| Strategy | Pricing, eviction, rate limit algorithm |
| Factory | Create vehicle, logger, payment provider when construction varies |
| Observer | Notify subscribers after booking or payment events |
| State | Elevator, booking lifecycle, ticket status |
| Template Method | Shared workflow with specialized steps |

## Anti-Pattern Warning
Naming patterns without explaining the problem they solve is a negative signal. The interviewer wants clarity and trade-offs, not catalog recall.

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
Example: Use State for booking: CREATED -> LOCKED -> PAID -> CONFIRMED or EXPIRED. This is clearer than boolean flags like `paid`, `locked`, and `cancelled`.
