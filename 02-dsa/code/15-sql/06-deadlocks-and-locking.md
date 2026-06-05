# Deadlocks and Locking

## Purpose
Prepare for backend interviews where correctness depends on row locks, transaction order, isolation level, and retry behavior.

## How to Use
Practice each scenario by writing the SQL, naming the locks taken, and explaining how your Spring transaction should retry or fail safely.

## Implementation Notes
- Keep transactions short.
- Access shared resources in a consistent order.
- Add indexes so updates lock targeted rows, not broad ranges.
- Use optimistic locking for user-edit conflicts and pessimistic locking for scarce inventory.

## Practical Example
Two users try to reserve the last seat:

```sql
START TRANSACTION;
SELECT id, status
FROM seats
WHERE show_id = 42 AND id = 1001
FOR UPDATE;

UPDATE seats
SET status = 'HELD'
WHERE id = 1001 AND status = 'AVAILABLE';
COMMIT;
```

The backend should return a conflict if the update count is zero and should retry only safe transient failures.

## Locking Patterns
| Pattern | SQL/API | Use case | Risk |
|---|---|---|---|
| Optimistic locking | `@Version` | Profile/post edits | Retry/merge UX needed |
| Pessimistic write | `SELECT ... FOR UPDATE` | Inventory, wallet debit | Long transaction blocks others |
| Unique constraint | `UNIQUE(user_id, post_id)` | Idempotent like/follow | Needs duplicate-key handling |
| Consistent ordering | Sort IDs before update | Wallet transfer | Requires discipline in all writers |

## Deadlock Scenario
Transaction A updates account 1 then account 2. Transaction B updates account 2 then account 1. Both wait forever until DB aborts one.

```sql
-- Fix: always lock lower account id first
SELECT * FROM accounts WHERE id IN (1, 2) ORDER BY id FOR UPDATE;
```

## Interview Questions
- How do deadlocks happen in MySQL/Postgres?
- When would you use optimistic vs pessimistic locking?
- How should a Spring service handle deadlock retry?
- Why can a missing index increase lock contention?

## Common Mistakes
- Retrying non-idempotent operations without idempotency keys.
- Holding locks while calling external services.
- Assuming `READ COMMITTED` prevents lost updates by itself.

## Self-Check
- [ ] I can explain row locks, gap locks, and transaction order.
- [ ] I can design retry for deadlock loser transactions.
- [ ] I know which operations need idempotency keys.

## Weekly Tracking Format
| Week | Scenario | Lock strategy | Retry strategy | Score /100 | Pass/fail | Recovery |
|---|---|---|---|---:|---|---|
| | Wallet transfer | Ordered row locks | Retry deadlock loser once with idempotency | | | |

## Score Out of 100
- 30: Correct lock choice.
- 25: Correct isolation and retry explanation.
- 20: Index and query-plan awareness.
- 15: Failure-mode handling.
- 10: Clear interview explanation.

## Pass/Fail Criteria
- Pass: 80+ and can explain one deadlock with a prevention strategy.
- Fail: Cannot separate locking, isolation, and idempotency.

## Recovery Plan
Rework wallet transfer, seat reservation, and like-toggle examples until the lock order and retry policy are explicit.
