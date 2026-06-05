# Database Normalization

## Purpose
Prepare for schema-design interviews where you must balance normalization, query performance, integrity, and product requirements.

## How to Use
For every schema question, state entities, relationships, constraints, indexes, and any deliberate denormalization. Score yourself out of 100.

## Implementation Notes
- Normalize first for correctness and integrity.
- Add constraints for business invariants, not only application checks.
- Denormalize only when a measured read path needs it.
- Keep derived counters repairable with reconciliation jobs.

## Normal Forms in Backend Terms
| Level | Interview meaning | Example fix |
|---|---|---|
| 1NF | Atomic values, no repeating columns | Move `tag1, tag2, tag3` to `post_tags` |
| 2NF | Non-key columns depend on the whole key | Move `user_email` out of `order_items` |
| 3NF | Non-key columns do not depend on other non-key columns | Store `category_name` in `categories`, not every post |

## Practical Example
Blog schema:

```sql
CREATE TABLE posts (
  id BIGINT PRIMARY KEY,
  author_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  category_id BIGINT NULL,
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (author_id) REFERENCES users(id),
  FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE TABLE post_likes (
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  PRIMARY KEY (post_id, user_id)
);
```

`likes_count` can be denormalized on `posts` only if the feed needs it at high read volume, and then it needs reconciliation.

## Backend API Scenarios
- Feed endpoint: normalized likes table plus denormalized `likes_count` for fast reads.
- Search endpoint: normalized source of truth plus search index for text retrieval.
- Audit endpoint: append-only audit table instead of overwriting status history.
- Soft delete: `deleted_at` retains data while default queries exclude deleted rows.

## Interview Questions
- When would you denormalize a counter?
- How do you model tags, categories, and likes?
- How do constraints prevent application bugs?
- How do you migrate a denormalized field safely?

## Common Mistakes
- Treating normalization as always better than product latency.
- Storing comma-separated IDs in one column.
- Forgetting unique constraints for many-to-many tables.
- Denormalizing without a repair/reconciliation plan.

## Self-Check
- [ ] I can draw entities and relationships before writing SQL.
- [ ] I can name primary, foreign, unique, and composite indexes.
- [ ] I can justify every denormalized field with a read path.

## Weekly Tracking Format
| Week | Schema | Normalized model | Denormalization | Score /100 | Pass/fail | Recovery |
|---|---|---|---|---:|---|---|
| | Blog feed | users/posts/comments/likes | likes_count for feed | | | |

## Score Out of 100
- 30: Correct entities and relationships.
- 20: Constraints and indexes.
- 20: Transaction and consistency reasoning.
- 20: Performance trade-offs.
- 10: Clear interview explanation.

## Pass/Fail Criteria
- Pass: 80+ and can explain one normalization and one denormalization decision.
- Fail: Cannot map product requirements to constraints and indexes.

## Recovery Plan
Redesign BlogHub, Splitwise, and BookMyShow schemas and explain one read-heavy denormalization for each.
