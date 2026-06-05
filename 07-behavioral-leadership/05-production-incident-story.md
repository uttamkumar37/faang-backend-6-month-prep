# Production Incident Story

## Purpose
Prepare incident stories that show calm diagnosis, customer protection, communication, and prevention.

## Study Steps
- Draft the story in STAR format with concrete scope and metrics.
- Compress it into a 90-second answer and a 3-minute deep-dive version.
- Practice two follow-ups: what you would change and what signal proves impact.

## Incident Story Framework
| Phase | What to say |
|---|---|
| Detection | Alert, symptom, customer impact, timeline |
| Triage | What you checked first and why |
| Mitigation | How you stopped impact before full root cause |
| Root cause | Technical explanation with evidence |
| Prevention | Tests, monitors, guardrails, rollout changes |

## Must Include
- Severity and impact.
- Your exact role.
- One mistake or learning.
- One durable prevention mechanism.

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
Example: Kafka consumer lag caused delayed order notifications. You scaled consumers, paused poison messages to DLQ, fixed a slow external call timeout, and added lag burn-rate alerts.
