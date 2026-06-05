# Pagination and N Plus One

## Purpose
Handle backend list endpoints efficiently and avoid ORM query explosions.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Pagination Patterns
| Pattern | Use | Risk |
|---|---|---|
| OFFSET/LIMIT | Small admin pages | Slow and unstable for deep pages |
| Keyset pagination | Feeds/order history | Requires stable sort key |
| Cursor token | Public APIs | Must encode filters and position safely |

## Keyset Example
```sql
SELECT id, created_at, status
FROM orders
WHERE user_id = :userId
  AND (created_at, id) < (:lastCreatedAt, :lastId)
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

## N Plus One Example
Bad: load 20 orders, then query items once per order.
Better: fetch items with `WHERE order_id IN (...)`, use fetch join carefully, or project only required fields.

## Spring Data/JPA Notes
- Prefer DTO projections for list endpoints.
- Use entity graphs or fetch joins for bounded relationships.
- Keep pagination over parent rows stable before fetching children.

## Interview Questions
- Why is OFFSET slow for deep pages?
- How do you design a cursor for order history?
- How do you detect N+1 in logs or tests?

## Common Mistakes
- Sorting only by created_at without id tie-breaker.
- Using fetch join with pagination over one-to-many blindly.
- Returning entities instead of purpose-built DTOs.

## Self-Check
- [ ] I can write keyset pagination SQL.
- [ ] I can explain cursor stability under new inserts.
- [ ] I can fix N+1 without loading too much data.

## Practical Example
Example: For `/users/{id}/orders`, return the first 20 orders ordered by `(created_at, id)`, include a cursor containing both values, and batch-load item counts by order id.
