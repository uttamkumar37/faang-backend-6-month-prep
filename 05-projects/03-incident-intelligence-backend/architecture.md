# Project 3: Architecture — Incident Intelligence Backend

## System Diagram

```
External Alert Sources
┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│ Prometheus   │  │  Datadog     │  │ Custom Webhook   │
│ Alertmanager │  │  Webhook     │  │ (any HTTP source)│
└──────┬───────┘  └──────┬───────┘  └────────┬─────────┘
       │                 │                   │
       └─────────────────┴───────────────────┘
                         │  POST /api/v1/alerts/webhook/{source}
                         ▼
              ┌─────────────────────────┐
              │     Alert Ingestion     │
              │     API (Spring Boot)   │
              │  ─ Validate schema      │
              │  ─ Normalize format     │
              │  ─ Compute fingerprint  │
              └──────────┬──────────────┘
                         │
              ┌──────────┴──────────────┐
              ▼                         ▼
     Redis: check                Kafka: alert-raw-events
     dedup fingerprint           (TTL 1 hour in cache)
        │                               │
     DUPLICATE                     NEW ALERT
     → dropped                          │
                                        ▼
                             ┌─────────────────────┐
                             │  Correlation Engine  │
                             │  (Kafka Consumer)    │
                             │  ─ find open incident│
                             │    for same service  │
                             │    (time window 15m) │
                             │  ─ create or attach  │
                             └──────────┬──────────┘
                                        │
                         ┌──────────────┴──────────────┐
                         ▼                             ▼
               New Incident Created            Attached to existing
               → Kafka: incident-events         incident
                         │
              ┌──────────┴──────────────────────────┐
              ▼            ▼                         ▼
    ML Scoring     SLO Burn Rate           Runbook Lookup
    Service        Monitor                 (Spring AI +
    (ONNX model)   (TimescaleDB            pgvector)
    → severity     query: burn > 5x?)
                         │
                         ▼
              ┌─────────────────────────┐
              │  Notification Router    │
              │  ─ PagerDuty (P1/P2)    │
              │  ─ Slack (P3/P4)        │
              │  ─ escalation timer     │
              └─────────────────────────┘
```

---

## Alert Ingestion Pipeline Detail

```
1. Receive Prometheus webhook (batch of alerts)

2. For each alert:
   a. Normalize to internal format:
      { source, name, labels{}, severity, firedAt, fingerprint }
   b. Fingerprint = SHA-256(sorted(labels.toString()))
      Labels used: {alertname, job, instance, cluster, env}
   c. Check Redis SET: "dedup:{fingerprint}"
      - EXISTS → discard (duplicate within 1h window)
      - NOT EXISTS → SET with TTL 1h, continue

3. Persist to alerts table (status=FIRING)
4. Produce to Kafka "alert-raw-events" topic

5. Correlation Engine (separate consumer group):
   - Query open incidents: WHERE service = ? AND created_at > now() - '15 min'
   - If found: link alert to incident
   - If not: create new incident, trigger ML scoring + notification
```

---

## ML Scoring Flow

```
Incident Created Event (Kafka)
        │
        ▼
ML Scoring Consumer:
  1. Extract features from incident + alert batch:
     - alert_count_in_window
     - affected_services (count from labels)
     - is_production (env label)
     - slo_burn_rate (query TimescaleDB)
     - historical_p99_mttr (lookup by service name)
     - hour_of_day, weekday

  2. Call ONNX Runtime in-process (microsoft/onnxruntime Java):
     OrtSession.Result result = session.run(inputTensor);
     float[] probs = (float[]) result.get(0).getValue();
     // probs[0]=P1, [1]=P2, [2]=P3, [3]=P4
     
  3. Update incident: severity = predicted class
  4. Publish "incident-severity-assigned" Kafka event
```

---

## SLO Burn Rate Monitoring

```
Continuous job (every 60 seconds):
  For each registered SLO:
    SELECT
      SUM(error_requests) / SUM(total_requests) AS error_rate
    FROM request_metrics_5m
    WHERE service = ? AND time > now() - interval '60 minutes'

  Burn rate = error_rate / SLO_error_budget

  If burn_rate > 14.4: alert P1 (budget exhausted in 1 hour)
  If burn_rate > 6:    alert P2 (exhausted in ~1 day)
  If burn_rate > 1:    alert P3 (on track to miss monthly target)
  
TimescaleDB continuous aggregate:
  CREATE MATERIALIZED VIEW request_metrics_5m
  WITH (timescaledb.continuous) AS
  SELECT time_bucket('5 minutes', ts) AS time, service,
         COUNT(*) total, SUM(is_error) errors
  FROM http_requests GROUP BY 1, 2;
```

---

## Runbook Semantic Search

```
Pre-ingestion (admin):
  Upload runbooks (Markdown) to pgvector index
  Each runbook chunked by H2 heading (procedure sections)
  Embedded with OpenAI text-embedding-3-small

At incident creation:
  Embed incident title + top 3 alert names
  pgvector search: SELECT content, source FROM runbook_chunks
                   ORDER BY embedding <=> $query
                   WHERE service_tag = ?
                   LIMIT 3;
  Top-3 runbook sections attached to Slack notification pane:
  "Suggested remediation: [link] [link] [link]"
```

---

## Scaling Notes

| Bottleneck | Solution |
|---|---|
| Burst of 10K alerts during outage | Kafka absorbs spike; consumers autoscale with KEDA |
| ML scoring latency | ONNX in-process (<5ms), no network hop |
| On-call routing at 3AM | Pre-computed on-call schedule cached in Redis, refreshed hourly |
| TimescaleDB CPU on burn-rate query | Continuous aggregates pre-compute 5-min buckets; query is O(1) |

---

## Trade-offs

| Decision | Why it helps | What it costs |
|---|---|---|
| In-process ONNX scoring | Low latency and fewer network hops | Harder model lifecycle management inside JVM service |
| Redis dedup cache | Fast duplicate suppression during alert storms | Possible false suppression if fingerprint design is too coarse |
| Correlation by service and time window | Simple and explainable incident grouping | Less accuracy than a richer graph-based correlator |
| Runbook retrieval at incident time | Faster operator action | Additional dependency in already-stressed incident flows |

## Failure modes and mitigations

- Kafka backlog during major outage: scale consumers with KEDA and prioritize routing over enrichment.
- Bad model output: cap automatic severity escalation and keep manual override path.
- False-positive correlation: keep full alert audit trail and allow split or reclassification.
- Slack or PagerDuty outage: retry and escalate to secondary delivery path.

## What to measure in a real implementation

- Alert suppression ratio and false-suppression review rate.
- Incident creation latency from first alert to routed notification.
- Severity model confidence distribution and override frequency.
- Burn-rate escalation accuracy versus post-incident review outcomes.
