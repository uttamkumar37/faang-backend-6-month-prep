# Parking Lot LLD

## Purpose
Design a parking lot with spot allocation, tickets, fee calculation, and exit flow.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Support cars, bikes, and trucks.
- Allocate compatible nearest available spot.
- Issue ticket on entry and calculate fee on exit.
- Track spot availability and ticket status.

## Entities
Vehicle, ParkingSpot, ParkingFloor, Ticket, ParkingLot, SpotAllocator, FeeCalculator.

## Class Diagram
```text
ParkingLot -> ParkingFloor -> ParkingSpot
EntryGate -> SpotAllocator -> TicketService
ExitGate -> FeeCalculator -> TicketService
```

## APIs
- `Ticket park(Vehicle vehicle)`
- `Receipt exit(String ticketId)`
- `List<Spot> availableByType(VehicleType type)`

## Design Decisions
Use strategy for spot allocation and fee calculation. Keep ticket status transitions explicit.

## Edge Cases
No spot available, duplicate exit, lost ticket, clock skew, unsupported vehicle type.

## Extensibility
Add reserved spots, EV charging, dynamic pricing, multiple allocation policies.

## Test Cases
Park and exit car, no spot available, duplicate ticket close, fee boundary at hour change.

## Interview Explanation
Lead with allocation and ticket lifecycle; pricing is a strategy, not embedded in spot logic.

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
Example: A truck should not occupy a bike spot even if it is free; compatibility is checked before allocation.
