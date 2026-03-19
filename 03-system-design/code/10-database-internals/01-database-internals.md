# Database Internals and Query Tuning

This module adds the database depth strong backend engineers need when discussing scale, isolation, and query performance.

## Topics to know

- B-tree indexes and why range queries work well on them
- LSM trees and write amplification trade-offs
- MVCC and snapshot reads
- isolation anomalies: dirty read, non-repeatable read, phantom, write skew
- replication lag and read-after-write consistency issues
- query planning basics: index scan vs full scan
- hot partitions and skewed access patterns
- online migrations and backfills

## Interview mental models

- An index speeds some access paths but adds write cost.
- The best query optimization usually comes from query shape and schema design, not only hardware.
- Replicas improve read scale but can violate read-after-write expectations.
- Isolation level is a correctness trade-off, not an academic detail.

## Query tuning checklist

1. confirm the exact query shape
2. look at predicates, sort order, and returned columns
3. check if the index matches filter and order-by pattern
4. avoid N+1 and oversized result sets
5. measure before and after

## Replication questions to handle

- Why can a user write successfully and still not see their own update on a replica?
- Why can an index improve reads but hurt writes?
- When would you choose a covering index?
