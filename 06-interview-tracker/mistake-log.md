# Mistake Log

## Purpose
Convert repeated mistakes into scheduled repairs instead of vague weakness notes.

## Study Steps
- Use this file during the Sunday review block.
- Fill concrete numbers first: solved count, mocks, defects, shipped project work.
- Mark pass or fail using the criteria in the file, then pick one recovery action.

## Mistake Categories
| Required category | Symptoms | Repair |
|---|---|---|
| DSA logic error | Invariant is wrong, pointer movement is wrong, recursion state is mutated incorrectly | Re-solve the same problem from scratch and write the invariant first |
| DSA pattern recognition error | Used DFS where BFS shortest path was needed, missed binary search on answer, missed monotonic stack | Re-solve 3 adjacent pattern problems and write the trigger rule |
| Edge case miss | Empty input, duplicate keys, overflow, one-element input, all-negative values, off-by-one | Add an edge-case checklist before coding and test it manually |
| Time complexity issue | Chose O(n^2) under large constraints, used nested scans where a map/heap/deque was needed | State constraints and target complexity before the algorithm |
| Java syntax/design issue | Comparator, equals/hashCode, generics, stream misuse, mutable keys, poor class boundaries | Write a tiny Java drill and explain the API/design decision |
| System design trade-off miss | No SLO, no failure mode, weak data model, no consistency trade-off, no capacity estimate | Redo the design in 30 minutes using requirements, scale, data, APIs, bottlenecks |
| Communication issue | Silent coding, rambling, late assumptions, no checkpoints, unclear trade-off explanation | Practice 5-minute explanation with explicit assumptions and checkpoints |
| Behavioral answer weakness | No ownership, no measurable result, blames others, weak learning, no customer/business impact | Rewrite in STAR format with impact, trade-off, and lesson learned |

## Log Template
| Date | Round type | Problem/topic | Mistake category | Root cause | Correct approach | Re-test date | Fixed? |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

## Severity
- P0: Would likely fail the round; schedule repair within 48 hours.
- P1: Important but isolated; repair within 1 week.
- P2: Polish issue; batch in weekly review.

## Interview Questions
- What mistake category repeats most often?
- Which defects are round-failing versus polish?
- How do you know a mistake is fixed?

## Common Mistakes
- Logging only the problem name, not the root cause.
- Fixing by rereading instead of re-solving.
- Not tagging communication mistakes.

## Self-Check
- [ ] Every P0 has a date and repair task.
- [ ] I can show three mistakes that no longer repeat.
- [ ] My weekly plan is based on this log.

## Practical Example
Example: For `minimum window substring`, root cause is not 'hard problem'; it is 'window validity invariant was not stated before coding'. Repair with 3 sliding-window invariant drills.
