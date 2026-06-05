# SQL Practice Answers

## Purpose
Give reference answers with the reasoning expected in backend interviews.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Selected Answers
```sql
-- 1. Users with no orders
SELECT u.id, u.email
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE o.id IS NULL;

-- 2. Paid revenue by user
SELECT u.id, u.email, SUM(oi.quantity * oi.unit_price) AS revenue
FROM users u
JOIN orders o ON o.user_id = u.id
JOIN order_items oi ON oi.order_id = o.id
WHERE o.status = 'PAID'
GROUP BY u.id, u.email;

-- 5. Latest order per user
WITH ranked AS (
  SELECT o.*, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) rn
  FROM orders o
)
SELECT * FROM ranked WHERE rn = 1;

-- 12. Duplicate payment prevention
INSERT INTO payment_events(provider, provider_event_id, order_id, status, created_at)
VALUES (:provider, :eventId, :orderId, :status, now())
ON CONFLICT (provider, provider_event_id) DO NOTHING;
```

## Index Notes
- `orders(user_id, status, created_at DESC, id DESC)` supports filtered order history.
- `order_items(order_id)` supports joining items by order.
- `payment_events(provider, provider_event_id)` must be unique for idempotency.

## Transaction Notes
For inventory, use an atomic update with `quantity > 0` or lock the row. For high contention, prefer reservation records and asynchronous expiration.

## Interview Questions
- Why is ON CONFLICT useful for idempotency?
- Why should order history indexes include the sort key?
- What answer would change for MySQL versus PostgreSQL?

## Common Mistakes
- Copying answers without explaining indexes.
- Ignoring the uniqueness constraint behind idempotency.
- Returning latest rows without deterministic tie-breakers.

## Self-Check
- [ ] I can re-derive each answer from schema relationships.
- [ ] I can state the index behind each query.
- [ ] I can explain transaction correctness for payments and inventory.

## Practical Example
Example: If two callbacks with the same provider event arrive concurrently, the unique constraint ensures only one row is inserted; the loser reads the existing result and returns success.
