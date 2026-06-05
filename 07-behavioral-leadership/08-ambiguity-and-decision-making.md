# Ambiguity and Decision-Making

## Purpose
Show how you make progress when requirements, ownership, or technical direction are unclear.

## Study Steps
- Draft the story in STAR format with concrete scope and metrics.
- Compress it into a 90-second answer and a 3-minute deep-dive version.
- Practice two follow-ups: what you would change and what signal proves impact.

## Decision Framework
1. Clarify customer outcome and non-goals.
2. Identify constraints: latency, correctness, cost, team capacity, migration risk.
3. Generate two viable options.
4. Choose with a written trade-off.
5. Define validation metric and rollback path.

## Senior Backend Examples
- Choosing SQL versus NoSQL under evolving query patterns.
- Deciding sync versus async for payment status updates.
- Splitting a service boundary without breaking transactions.
- Defining minimum observability before launch.

## Interview Questions
- What was your exact role and decision authority?
- What trade-off did you make and why?
- What would you do differently now?
- How did you measure impact?

## Common Mistakes
- Using 'we' so much that your personal contribution is unclear.
- Over-explaining technical context before the behavioral point.
- No metric, customer impact, or durable learning.

## Self-Check
- [ ] The story fits in 90 seconds without rushing.
- [ ] The result has a number, risk reduction, or user impact.
- [ ] I can answer two follow-ups without inventing details.

## Practical Example
Example: For an unclear analytics feature, you shipped an append-only event schema first, delayed aggregation choices, and validated top queries before committing to materialized views.
