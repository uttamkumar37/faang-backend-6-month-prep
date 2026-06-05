# BookMyShow LLD

## Purpose
Design show search, seat locking, booking, payment, and confirmation with concurrency safety.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Browse movies, theaters, shows, and seats.
- Lock seats for a short duration during checkout.
- Confirm booking after payment success.
- Release seats on payment failure or timeout.

## Entities
Movie, Theater, Screen, Seat, Show, SeatLock, Booking, Payment, BookingService, SeatLockManager.

## Class Diagram
```text
BookingService -> SeatLockManager
BookingService -> PaymentService
Show -> Seat
Booking -> BookingStatus
```

## APIs
- `List<Seat> availableSeats(showId)`
- `Booking holdSeats(userId, showId, seatIds)`
- `Booking confirm(bookingId, paymentId)`
- `void expireLocks()`

## Design Decisions
Seat lock is the core concurrency primitive. Booking state must be explicit: HELD, CONFIRMED, EXPIRED, CANCELLED.

## Edge Cases
Two users selecting same seat, payment callback after lock expiry, partial seat availability, duplicate confirmation, clock skew.

## Extensibility
Pricing by seat class, coupons, cancellation/refund, distributed lock, audit log.

## Test Cases
Concurrent lock conflict, lock expiry, duplicate payment callback, payment failure release.

## Interview Explanation
Lead with consistency: never confirm a seat unless the same user owns a valid lock or an atomic booking transaction succeeds.

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
Example: User A locks seats S1/S2. User B must receive a conflict for S1 until A confirms, cancels, or the lock expires.
