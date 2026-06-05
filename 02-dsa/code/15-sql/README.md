# SQL and Database Interview Practice

## Purpose
Practice backend SQL scenarios that appear in SDE-II interviews: joins, aggregation, windows, indexing, transactions, pagination, and ORM pitfalls.

## Study Steps
- Load schema-practice.sql.
- Study joins and aggregation first.
- Move to window functions and indexing.
- Finish with transactions, pagination, N+1, and practice questions.

## File Map
| File | Focus |
|---|---|
| 01-sql-joins.md | JOIN, GROUP BY, HAVING, anti-join |
| 02-window-functions.md | rank, row_number, running totals, dedupe |
| 03-indexing-query-plans.md | indexes, EXPLAIN, covering indexes |
| 04-transactions-isolation.md | ACID, isolation, locks, deadlocks |
| 05-pagination-and-n-plus-one.md | keyset pagination and ORM query traps |
| 06-deadlocks-and-locking.md | row locks, deadlock prevention, retry strategy |
| 07-database-normalization.md | schema design, constraints, denormalization trade-offs |
| SqlPracticeQuestions.md | Interview questions |
| schema-practice.sql | Practice schema and seed data |
| answers.md | Reference answers |

## Backend Interview Bar
You should be able to write the SQL, explain why it is correct, name the index, and describe how it behaves under concurrent writes.

## Interview Questions
- How would you find customers with no orders?
- How do you get the latest order per customer?
- How do you avoid N+1 queries in Spring Data JPA?

## Common Mistakes
- Writing a query that works but cannot explain the execution plan.
- Using offset pagination for deep scrolling feeds.
- Ignoring isolation level when discussing duplicate payments or inventory.

## Self-Check
- [ ] I can write joins without guessing row cardinality.
- [ ] I can use row_number and rank correctly.
- [ ] I can read basic EXPLAIN output and propose indexes.

## Practical Example
Example: For an order history endpoint, use keyset pagination on `(created_at, id)` instead of `OFFSET 50000`, and index the same ordering columns.
