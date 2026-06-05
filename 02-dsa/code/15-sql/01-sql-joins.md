# SQL Joins

## Purpose
Build confidence with join cardinality, aggregation, HAVING, and backend reporting queries.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Join Patterns
| Pattern | Use |
|---|---|
| INNER JOIN | Matching rows on both sides |
| LEFT JOIN | Keep parent rows even without children |
| Anti-join | Find missing relationship with `LEFT JOIN ... WHERE child.id IS NULL` |
| Self join | Compare rows in same table |
| GROUP BY/HAVING | Aggregate and filter groups |

## Examples
```sql
-- Total paid order value per customer
SELECT u.id, u.email, SUM(oi.quantity * oi.unit_price) AS total_value
FROM users u
JOIN orders o ON o.user_id = u.id
JOIN order_items oi ON oi.order_id = o.id
WHERE o.status = 'PAID'
GROUP BY u.id, u.email
HAVING SUM(oi.quantity * oi.unit_price) > 1000;

-- Users who never placed an order
SELECT u.id, u.email
FROM users u
LEFT JOIN orders o ON o.user_id = u.id
WHERE o.id IS NULL;
```

## Backend Notes
- Always reason about one-to-many joins before aggregating.
- Count distinct parent ids when joining to child tables.
- Place filters carefully: filtering a LEFT JOIN child in WHERE can turn it into an INNER JOIN.

## Interview Questions
- Explain INNER JOIN versus LEFT JOIN with a user/order example.
- How do you find products never purchased?
- Why can joining order_items inflate order counts?

## Common Mistakes
- Counting rows after a one-to-many join without DISTINCT.
- Putting child filters in WHERE after LEFT JOIN.
- Forgetting HAVING is for aggregate filters.

## Self-Check
- [ ] I can predict row count changes after each join.
- [ ] I can write anti-joins without subquery confusion.
- [ ] I can explain GROUP BY columns.

## Practical Example
Example: To count paid orders per user with items, use `COUNT(DISTINCT o.id)`, not `COUNT(*)`, because each item multiplies the order row.
