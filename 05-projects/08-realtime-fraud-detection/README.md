# Project 8: Real-Time Fraud Detection Engine

## Overview

A sub-100ms fraud scoring engine that evaluates every payment, account creation, and login event in real time using rule-based scoring, velocity checks, device fingerprinting, and an ML model integration — all without blocking the transaction path.

**Business Value**: Reduces fraudulent transaction losses by 80%+ while keeping false-positive rate below 0.5%, preventing legitimate user friction while stopping fraud.

## Why this is a strong portfolio project

Fraud detection combines the hardest aspects of backend engineering: extremely low-latency decision making on a hot path, stream processing with stateful aggregations, ML model serving, distributed feature stores, and the tension between precision and recall with real business cost on both sides. It demonstrates data engineering depth, low-latency system design, and operational sophistication that separates senior engineers from mid-level ones.

---

## Features

- **Real-time scoring**: Every transaction scored in < 50ms P99 using async feature lookup.
- **Rule engine**: Configurable DSL rules (velocity, geo, device, amount) evaluated at scoring time.
- **Velocity checks**: Rolling-window aggregations (1m, 5m, 1h, 24h) per user, device, IP, merchant.
- **Device fingerprinting**: Risk score contribution from device characteristics and consistency.
- **ML scoring**: XGBoost/LightGBM model served via ONNX Runtime; returns fraud probability 0–1.
- **Feature store**: Pre-computed user behavioral features refreshed in near real time via Kafka.
- **ALLOW / REVIEW / BLOCK decision**: Three-tier outcome with configurable thresholds per risk band.
- **Case management API**: Analysts review REVIEW-tier decisions; outcomes feed back into model.
- **Model A/B testing**: Shadow scoring with new model version before live cutover.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21 — low-latency async path |
| Rule engine | Custom DSL with MVEL / SpEL evaluation |
| Feature store | Redis (real-time features), Apache Hive (offline batch features) |
| Velocity counters | Redis sorted sets + sliding window ZCOUNT |
| Stream processing | Kafka Streams (feature aggregation pipeline) |
| ML scoring | ONNX Runtime (Java binding) — model file embedded |
| Event bus | Kafka (transaction-events, fraud-decisions, case-events) |
| Case DB | PostgreSQL (fraud cases, analyst verdicts) |
| Analytics | ClickHouse (fraud rate trends, model performance tracking) |
| Observability | Micrometer, Prometheus, Grafana, Jaeger tracing per decision |

---

## Functional Requirements

- Score every incoming transaction event within 50ms P99 without blocking the payment confirmation path.
- Evaluate a configurable set of risk rules against current transaction context.
- Compute rolling-window velocity features for user, device, IP, and merchant.
- Integrate an ML model serving sub-component for probabilistic fraud scoring.
- Emit a decision (ALLOW / REVIEW / BLOCK) with a score and contributing signals.
- Support analyst review workflow for REVIEW-tier decisions with outcome feedback.

## Non-Functional Requirements

- 50K transactions per second at P99 scoring latency < 50ms.
- False-positive rate (legitimate transactions blocked) < 0.5%.
- Rule configuration changes propagated within 30 seconds, no restart required.
- 99.99% availability for the scoring API; degraded mode allows transactions if scoring fails.
- Audit trail for every decision with full feature snapshot and rule evaluation path.

## Success Metrics

- Fraud loss reduction: measured as fraudulent amount caught vs slipped per week.
- False positive rate: < 0.5% of approved transactions reversed as fraudulent.
- Decision latency: scoring P99 < 50ms under 50K TPS.
- REVIEW queue clearance: analysts clear 95% of REVIEW cases within 4 hours.
- Model drift: monitor AUC-ROC weekly; retrain trigger if AUC drops > 2%.

---

## API Endpoints

```
POST /api/v1/fraud/score
  Body: {
    "transactionId": "txn-123",
    "userId": "...",
    "amount": 249.99,
    "currency": "USD",
    "merchantId": "...",
    "deviceFingerprint": "...",
    "ipAddress": "...",
    "timestamp": "..."
  }
  Response: {
    "decision": "ALLOW",        // ALLOW | REVIEW | BLOCK
    "fraudScore": 0.12,         // 0=clean, 1=fraud
    "signals": [
      { "rule": "VELOCITY_IP_5M", "score": 0.05, "detail": "3 txns in 5m" },
      { "rule": "ML_MODEL", "score": 0.07, "detail": "model v2.1" }
    ],
    "latencyMs": 18
  }

GET  /api/v1/cases?status=REVIEW&page=0&size=20    (analyst queue)
POST /api/v1/cases/{caseId}/verdict
  Body: { "verdict": "FRAUD|LEGITIMATE", "analystId": "...", "notes": "..." }

POST /api/v1/rules              (admin: add/update rule)
GET  /api/v1/rules              (admin: list active rules)
DELETE /api/v1/rules/{ruleId}

POST /api/v1/models/shadow      (register shadow model version for A/B)
POST /api/v1/models/promote     (promote shadow model to live)
```

---

## Decision Tiers

```
Fraud Score:   0.0 ──────────── 0.3 ──────────── 0.7 ──────────── 1.0
Decision:         ALLOW              REVIEW              BLOCK

ALLOW  (0.0–0.3):  Transaction proceeds immediately.
REVIEW (0.3–0.7):  Transaction proceeds (no friction) but queued for analyst review.
                   High-value REVIEW (amount > $1000): trigger step-up auth.
BLOCK  (0.7–1.0):  Transaction declined. User notified via notification service.

Thresholds are configurable per merchant category, user tier, and geography.
```

---

## Scoring Pipeline

```
Incoming transaction event
  │
  ▼
1. Async parallel feature fetch (CompletableFuture, timeout 20ms):
   a. User velocity features:
      ZCOUNT velocity:user:{id}:1m  (now-60s, now)
      ZCOUNT velocity:user:{id}:5m  (now-300s, now)
      ZCOUNT velocity:user:{id}:1h  (now-3600s, now)
   b. IP velocity features (same pattern)
   c. Device risk score: GET device:{fingerprint}:risk
   d. User behavioral profile: HGETALL user_profile:{id}
   e. Merchant risk flag: GET merchant:{id}:risk_tier

2. Rule engine evaluation (all rules, parallel):
   Rules loaded from Redis / config store at startup, refreshed every 30s.
   Each rule: IF conditions THEN add_score(weight)
   
   Example rules:
   - VELOCITY_CARD_1M: count > 5 in 60s → score += 0.3
   - HIGH_AMOUNT_NEW_DEVICE: amount > 500 AND device_seen_first_time → score += 0.4
   - GEO_MISMATCH: tx_country != user_home_country → score += 0.2
   - MIDNIGHT_LARGE: hour in [0,4] AND amount > 200 → score += 0.15

3. ML model inference (ONNX Runtime, 2–5ms):
   Feature vector: [velocity_1m, velocity_5m, amount_zscore, device_age_days, ...]
   model.run(input_vector) → fraud_probability (0.0–1.0)

4. Weighted score aggregation:
   final_score = W_rules × rule_score + W_ml × ml_score
   W_rules=0.4, W_ml=0.6

5. Decision + emit:
   Emit to Kafka fraud-decisions topic (async, non-blocking)
   Add ZADD velocity:user:{id}:1m <now> <txn_id>
   Return decision response
```

---

## Key Engineering Challenges

### 1. Sub-50ms Under 50K TPS
All feature fetches are issued in parallel using CompletableFuture.allOf(). Redis pipeline batches velocity ZCOUNT calls. Total latency budget: network 5ms + Redis 3ms + rules 1ms + ML 5ms + overhead = ~18ms median.

### 2. Streaming Feature Updates
Kafka Streams consumes `transaction-events`, aggregates rolling windows using `suppress()` with 1-second grace period, and writes updated velocity counters to Redis. Feature staleness: < 2 seconds.

### 3. Rule Hot-Reload Without Restart
Rules are stored in Redis hash with a version counter. Background thread polls version every 30 seconds. On version change, recompiles and atomically swaps the rule evaluator reference.

### 4. Fail-Open for Availability
If scoring service timeout (> 50ms), the default policy is ALLOW with score=0.0 and flag `SCORED_DEGRADED=true`. Degraded decisions are queued for analyst review. This prevents fraud scoring from becoming a payment availability dependency.

---

## Resume Impact Bullets

- Built a real-time fraud scoring engine handling 50K TPS at P99 < 50ms using parallel async feature fetch from Redis, a configurable rule engine, and ONNX Runtime ML model inference.
- Designed a Kafka Streams velocity aggregation pipeline computing rolling-window counters (1m/5m/1h/24h) per user and device with < 2-second feature staleness.
- Implemented fail-open degradation mode ensuring 99.99% payment availability even during scoring service incidents, with degraded decisions queued for analyst review.
