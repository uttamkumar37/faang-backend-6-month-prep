# SQL Practice Questions

## Purpose
Provide backend-flavored SQL drills with increasing interview difficulty.

## Study Steps
- Run the schema locally in PostgreSQL or adapt it to your SQL engine.
- Write the query before checking answers.md.
- Explain the query plan, index need, and transaction behavior out loud.

## Joins and Aggregation
1. Find users who placed no orders.
2. Find total paid revenue by user.
3. Find products never purchased.
4. Find users with more than 3 paid orders in the last 30 days.

## Window Functions
5. Find latest order per user.
6. Find top 2 products by revenue per category.
7. Calculate running daily revenue.
8. Deduplicate payment provider events, keeping the latest event.

## Indexing and Plans
9. Design indexes for user order history.
10. Explain why `LOWER(email) = ?` may miss a normal email index.
11. Find a missing index from an EXPLAIN plan.

## Transactions
12. Prevent duplicate payment processing.
13. Prevent inventory oversell.
14. Explain and fix a deadlock in two order updates.

## Backend Scenarios
15. Rewrite an N+1 order-items endpoint.
16. Implement keyset pagination for support tickets.
17. Find Kafka events not processed within 10 minutes.

## Interview Questions
- Which query needs a composite index and why?
- Which queries are unsafe under concurrent writes?
- How would this query appear in a Spring repository?

## Common Mistakes
- Solving only the SQL and not the backend scenario.
- No index discussion for list endpoints.
- No transaction discussion for payment or inventory.

## Self-Check
- [ ] I can solve at least 12 of 17 without answers.
- [ ] I can explain plan and index for every list query.
- [ ] I can explain correctness under concurrency for payment and inventory.

## Practical Example
Example: Question 12 should mention unique idempotency key, transaction boundary, and safe handling of duplicate provider callbacks.
