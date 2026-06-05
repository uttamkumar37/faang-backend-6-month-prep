# Incident Postmortem Template

## Purpose
Document incidents in a blameless, technical, action-oriented format that interviewers recognize as senior production maturity.

## Study Steps
- State the user-visible symptom, blast radius, and last known good time.
- Check metrics before logs: saturation, error rate, latency, throughput.
- Collect one JVM artifact and one dependency artifact before changing code.
- Mitigate customer impact first, then complete root cause and prevention.

## Template
| Field | Notes |
|---|---|
| Incident title | Short symptom and affected system |
| Severity | SEV level and why |
| Start/end time | Include detection and mitigation time |
| Customer impact | Error rate, latency, data delay, affected tenants |
| Detection | Alert, customer report, dashboard, log |
| Timeline | Timestamped facts only |
| Root cause | Technical cause with evidence |
| Trigger | Deploy, traffic spike, config, data pattern |
| Mitigation | What reduced impact |
| Resolution | Permanent fix |
| What went well | Keep factual |
| What went poorly | Gaps in detection, rollback, ownership |
| Action items | Owner, due date, verification signal |

## Action Item Quality Bar
- Bad: Improve monitoring.
- Good: Add p99 latency alert for `/checkout` when 5-minute burn rate exceeds 2x SLO; owner and due date assigned.

## Interview Questions
- What would you check first and why?
- How do you distinguish mitigation from root-cause fix?
- What metric or alert would prevent recurrence?

## Common Mistakes
- Changing multiple variables before isolating the symptom.
- Ignoring recent deploys and configuration changes.
- Declaring root cause without evidence from metrics, dumps, traces, or query plans.

## Self-Check
- [ ] I can name the first three metrics for this incident type.
- [ ] I know the JVM or dependency command to collect evidence.
- [ ] I can propose one immediate mitigation and one durable prevention.

## Practical Example
Example: Postmortem for DB pool exhaustion includes Hikari acquisition timeout metrics, slow endpoint traces, leaked connection stack, fix PR, and a test that fails if connections are not closed.
