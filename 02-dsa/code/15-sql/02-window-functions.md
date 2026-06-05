# Window Functions

## Purpose
Use SQL windows for latest row, ranking, running totals, and deduplication without losing row-level detail.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Core Functions
| Function | Use |
|---|---|
| ROW_NUMBER | Pick exactly one row per partition |
| RANK/DENSE_RANK | Ranking with ties |
| LAG/LEAD | Compare with previous or next row |
| SUM() OVER | Running total or partition total |

## Examples
```sql
-- Latest order per user
WITH ranked AS (
  SELECT o.*, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at DESC, id DESC) AS rn
  FROM orders o
)
SELECT * FROM ranked WHERE rn = 1;

-- Monthly revenue running total
SELECT month, revenue,
       SUM(revenue) OVER (ORDER BY month) AS running_revenue
FROM monthly_revenue;
```

## Backend Notes
Window functions are excellent for analytics endpoints and admin dashboards, but verify plans because partition/order columns often need indexes.

## Interview Questions
- When would you use ROW_NUMBER instead of GROUP BY?
- How do you get top 3 products per category?
- What index helps latest order per user?

## Common Mistakes
- Using RANK when exactly one row is required.
- Forgetting deterministic tie-breakers in ORDER BY.
- Assuming windows reduce rows; they do not unless filtered outside.

## Self-Check
- [ ] I can write latest-row-per-parent queries.
- [ ] I can explain partition versus order.
- [ ] I can add a tie-breaker column.

## Practical Example
Example: Latest payment per order should use `ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY created_at DESC, id DESC)` so duplicate timestamps are deterministic.
