# Indexing and Query Plans

## Purpose
Understand how indexes support backend latency, pagination, filtering, joins, and uniqueness constraints.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Index Rules
- Index foreign keys used in joins.
- For composite indexes, put equality filters first, then range/order columns.
- Avoid wrapping indexed columns in functions in WHERE clauses.
- Use partial indexes for hot filtered subsets when supported.
- Do not add indexes blindly; they slow writes and consume memory/disk.

## EXPLAIN Example
```sql
EXPLAIN ANALYZE
SELECT id, status, created_at
FROM orders
WHERE user_id = 42 AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;

CREATE INDEX idx_orders_user_status_created
  ON orders(user_id, status, created_at DESC, id DESC);
```

## What To Look For
| Plan signal | Meaning |
|---|---|
| Seq Scan on huge table | Missing or unusable index |
| Rows removed by filter high | Poor selectivity or wrong index |
| Sort after many rows | Index does not match ORDER BY |
| Nested loop with high loops | Join order or index issue |

## Interview Questions
- Design an index for user order history.
- Why can too many indexes hurt writes?
- What does EXPLAIN ANALYZE add beyond EXPLAIN?

## Common Mistakes
- Creating single-column indexes when a composite index matches the query better.
- Ignoring ORDER BY in index design.
- Optimizing without real cardinality or query plan evidence.

## Self-Check
- [ ] I can propose an index from a WHERE plus ORDER BY query.
- [ ] I can explain write overhead.
- [ ] I can spot seq scan and sort issues in a plan.

## Practical Example
Example: For keyset pagination `WHERE user_id=? AND (created_at,id)<(?,?) ORDER BY created_at DESC,id DESC`, index `(user_id, created_at DESC, id DESC)`.
