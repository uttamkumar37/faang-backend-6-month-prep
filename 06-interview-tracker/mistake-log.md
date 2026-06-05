# Mistake Log

## Purpose
Convert repeated mistakes into scheduled repairs instead of vague weakness notes.

## Study Steps
- Use this file during the Sunday review block.
- Fill concrete numbers first: solved count, mocks, defects, shipped project work.
- Mark pass or fail using the criteria in the file, then pick one recovery action.

## Mistake Categories
| Category | Symptoms | Repair |
|---|---|---|
| Pattern miss | Used DFS where BFS shortest path was needed | Re-solve 3 similar problems |
| Edge case miss | Empty input, duplicate keys, overflow, off-by-one | Add edge checklist before coding |
| Complexity miss | O(n^2) accepted mentally for large input | State constraints before algorithm |
| Java syntax/API | Comparator, equals/hashCode, generics, streams | Write tiny Java drill |
| Data structure misuse | Heap vs TreeMap vs deque confusion | Record decision rule |
| Design gap | No failure mode, no SLO, no data model | Redo design in 30 minutes |
| Backend depth gap | Knows API but not internals | Trace runtime behavior |
| Communication gap | Silent coding, rambling, late assumptions | Practice 5-minute explanation |

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
