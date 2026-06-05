# Failure and Learning

## Purpose
Answer failure questions without defensiveness by showing accountability and a concrete behavior change.

## Study Steps
- Draft the story in STAR format with concrete scope and metrics.
- Compress it into a 90-second answer and a 3-minute deep-dive version.
- Practice two follow-ups: what you would change and what signal proves impact.

## Strong Failure Answer
- Pick a real failure with manageable blast radius.
- Own your decision or missed signal.
- Explain what data you lacked or ignored.
- Show the mechanism you added afterward.
- Avoid blaming management, requirements, or teammates.

## Good Failure Topics
- Underestimated migration risk.
- Shipped insufficient observability.
- Chose overcomplex design.
- Missed a race condition or transaction boundary.

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
Example: You shipped cache-aside without invalidation strategy, causing stale reads. You added event-driven invalidation, TTL guardrails, and a design checklist for cache correctness.
