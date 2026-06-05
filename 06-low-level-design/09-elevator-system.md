# Elevator System LLD

## Purpose
Design an elevator controller with requests, scheduling, state transitions, and safety constraints.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Multiple elevators and floors.
- Handle hall calls and car calls.
- Move elevators based on scheduling policy.
- Track direction, current floor, door state.

## Entities
Elevator, ElevatorController, Request, Scheduler, Door, Direction, ElevatorState.

## Class Diagram
```text
ElevatorController -> Scheduler -> Elevator
Elevator -> Door
RequestQueue grouped by direction/floor
```

## APIs
- `requestPickup(floor, direction)`
- `requestFloor(elevatorId, floor)`
- `tick()` for simulation step

## Design Decisions
Separate scheduler from elevator state. Use state machine for moving, idle, door open.

## Edge Cases
Invalid floor, overloaded elevator, emergency stop, duplicate request, starvation.

## Extensibility
Destination dispatch, priority service, maintenance mode, energy optimization.

## Test Cases
Single pickup, same-direction batching, invalid floor rejection, no starvation under mixed requests.

## Interview Explanation
Keep real-time hardware control out of scope; focus on scheduling and state correctness.

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
Example: If an elevator is moving up from floor 3 to 8, a pickup at floor 5 going up can be batched; floor 2 going down should wait or go to another elevator.
