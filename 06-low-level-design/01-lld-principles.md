# LLD Principles

## Purpose
Build a repeatable LLD approach for machine-coding and object-oriented design rounds.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Core Principles
- Model behavior, not just nouns.
- Keep invariants close to the state they protect.
- Use interfaces where behavior varies, not everywhere.
- Prefer simple composition over deep inheritance.
- Make state transitions explicit.

## Class Diagram Text Format
```text
Service -> Repository -> Entity
BookingService uses SeatLockManager
PaymentService publishes PaymentResult
```

## Design Decision Checklist
- What can fail?
- What must be atomic?
- What changes are likely?
- What should be hidden behind an interface?
- What can remain simple for the interview scope?

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
Example: A cache design needs `EvictionPolicy` as a strategy because eviction varies. It does not need a distributed cluster abstraction unless the prompt asks for it.
