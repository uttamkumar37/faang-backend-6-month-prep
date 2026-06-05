# Transactions and Isolation

## Purpose
Prepare for backend correctness questions involving ACID, isolation anomalies, locks, deadlocks, and idempotency.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Isolation Concepts
| Level | Prevents | Still possible depending on DB |
|---|---|---|
| READ COMMITTED | Dirty reads | Non-repeatable reads, phantoms |
| REPEATABLE READ | Non-repeatable reads | Phantoms in some engines |
| SERIALIZABLE | Most anomalies | Retries due to serialization failures |

## Practical Scenarios
- Inventory decrement must guard against oversell.
- Payment creation needs idempotency key uniqueness.
- Money transfer needs both ledger rows in one transaction.
- Long transactions can block writers and exhaust pools.

## Deadlock Pattern
Transaction A locks order 1 then order 2. Transaction B locks order 2 then order 1. Fix with consistent lock ordering and small transaction scope.

## SQL Example
```sql
BEGIN;
SELECT quantity FROM inventory WHERE sku = 'SKU-1' FOR UPDATE;
UPDATE inventory SET quantity = quantity - 1 WHERE sku = 'SKU-1' AND quantity > 0;
COMMIT;
```

## Interview Questions
- How do you prevent duplicate payment records?
- What causes a deadlock?
- When would you use SELECT FOR UPDATE?

## Common Mistakes
- Using transactions for too much non-DB work.
- Forgetting unique constraints for idempotency.
- Assuming isolation solves external side effects.

## Self-Check
- [ ] I can explain dirty, non-repeatable, and phantom reads.
- [ ] I can design idempotent payment writes.
- [ ] I can name a deadlock prevention technique.

## Practical Example
Example: A payment callback should insert with unique `(provider, provider_event_id)` inside a transaction so duplicate callbacks are acknowledged without double-applying state.
