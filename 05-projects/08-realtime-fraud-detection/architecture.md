# Project 8: Architecture — Real-Time Fraud Detection Engine

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Upstream Services                            │
│   Payment Gateway     Auth Service     Account Service               │
└──────────────┬────────────────────────────────────────────────────────┘
               │  POST /fraud/score  (sync — blocking payment path)
               ▼
┌──────────────────────────────────────────────────────────────────────┐
│           Fraud Scoring API (Spring Boot, 20 pods)                   │
│           Virtual threads; CompletableFuture async feature fetch     │
│           ONNX Runtime ML model (embedded, loaded at startup)        │
│           Rule Engine (hot-reloaded from Redis every 30s)            │
└──────┬──────────────────┬───────────────────┬────────────────────────┘
       │                  │                   │
       ▼                  ▼                   ▼
 Redis Cluster     Kafka Producer        PostgreSQL
 (feature store    (fraud-decisions,     (fraud_cases,
  velocity counters, transaction-events)  analyst_verdicts)
  device risk,
  user profiles)
       ▲
       │ feature writes
       │
┌────────────────────────────────────────────────────────────────────┐
│                  Kafka Streams — Feature Pipeline                   │
│                                                                    │
│  transaction-events topic                                          │
│       │                                                            │
│       ▼                                                            │
│  Topology:                                                         │
│  ├── groupBy(userId)  → velocity:user:{id}:{window}               │
│  ├── groupBy(ipAddress) → velocity:ip:{ip}:{window}               │
│  ├── groupBy(deviceId)  → velocity:device:{id}:{window}           │
│  └── windowedBy(1m, 5m, 1h, 24h) + suppress(1s grace)            │
│       │                                                            │
│       ▼                                                            │
│  Redis Sink: ZADD velocity:{type}:{id}:{window} <score> <txn_id>  │
└────────────────────────────────────────────────────────────────────┘
       ▲
       │ transaction events
       │
┌────────────────────────────────────────────────────────────────────┐
│                        Kafka Cluster                                │
│  ├── transaction-events  (all incoming tx for feature pipeline)    │
│  ├── fraud-decisions     (all scoring outputs + audit log)         │
│  └── case-events         (analyst decisions → model feedback)      │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                    Case Management Service                          │
│  Consumes fraud-decisions (REVIEW tier)                            │
│  Exposes analyst queue API                                         │
│  Stores verdicts → feeds Kafka case-events → model retraining      │
└────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────┐
│                  Model Training Pipeline (offline)                  │
│  Reads case-events (labeled fraud/legitimate)                      │
│  Retrains XGBoost / LightGBM                                       │
│  Exports ONNX model artifact to model registry (S3)                │
│  Shadow mode: new model scores alongside live model                │
│  A/B routing: 5% traffic → shadow model until confidence threshold │
└────────────────────────────────────────────────────────────────────┘
```

---

## Scoring Request Latency Breakdown

```
Incoming request arrives at Fraud Scoring API pod
  │
  ├── t=0ms: Start CompletableFuture.allOf() parallel fetch
  │   ├── f1: Redis PIPELINE [ ZCOUNT velocity:user:1m/5m/1h ]    ~3ms
  │   ├── f2: Redis GET device:{fp}:risk                           ~1ms
  │   ├── f3: Redis HGETALL user_profile:{id}                      ~2ms
  │   └── f4: Redis SISMEMBER blacklist:{ip}                       ~1ms
  │
  ├── t=3ms: All features resolved (parallel)
  │
  ├── t=3–4ms: Rule engine evaluation (~20 rules, in-memory)       ~1ms
  │
  ├── t=4–9ms: ONNX Runtime model.run(feature_vector)              ~5ms
  │
  ├── t=9–10ms: Score aggregation + decision                       ~1ms
  │
  ├── t=10ms: Kafka.send() [async, non-blocking]
  │           Redis ZADD velocity write [async]
  │
  └── t=10ms: Return response to caller
  
  Median: ~10ms | P95: ~25ms | P99: ~45ms | Target: < 50ms P99
```

---

## Rule Engine: DSL Example

```
# Rules stored as JSON in Redis hash: fraud:rules
{
  "ruleId": "VELOCITY_USER_1M",
  "description": "Too many transactions in 1 minute",
  "condition": "velocity_1m > 5",
  "scoreContribution": 0.30,
  "enabled": true
}

{
  "ruleId": "HIGH_AMOUNT_NEW_DEVICE",
  "description": "Large amount on device seen for first time",
  "condition": "amount > 500 AND device_first_seen",
  "scoreContribution": 0.40,
  "enabled": true
}

{
  "ruleId": "GEO_MISMATCH",
  "description": "Transaction country differs from user home country",
  "condition": "tx_country != user_home_country",
  "scoreContribution": 0.20,
  "enabled": true
}

Rule evaluation in Java:
  StandardEvaluationContext ctx = new StandardEvaluationContext();
  ctx.setVariable("velocity_1m", features.velocity1m);
  ctx.setVariable("amount", tx.amount);
  // ...
  boolean match = (Boolean) parser.parseExpression(rule.condition).getValue(ctx);
  if (match) totalRuleScore += rule.scoreContribution;
```

---

## Velocity Counter Design

```
Redis Sorted Set per (entity_type, entity_id, window):
  Key:   velocity:user:{userId}:1m
  Score: epoch_milliseconds of transaction
  Value: transaction_id

On new transaction:
  ZADD velocity:user:{userId}:1m <now_ms> <txn_id>
  EXPIRE velocity:user:{userId}:1m 120      ← 2x window for safety

On velocity query:
  ZCOUNT velocity:user:{userId}:1m (now_ms - 60000) +inf
  → number of transactions in the last 60 seconds

Window sizes:  1m, 5m, 1h, 24h
Entity types:  user, ip, device, merchant, card_bin, recipient_account
```

---

## Model A/B Testing (Shadow Mode)

```
Traffic routing:
  100% → LIVE model   (returns decision to caller)
   10% → SHADOW model (runs in background, never returns to caller)

Shadow scoring collects:
  { txnId, liveScore, shadowScore, liveDecision, shadowDecision }
  → written to ClickHouse for analysis

Promotion decision (human analyst + automated gate):
  IF shadow_model.AUC_ROC > live_model.AUC_ROC + 0.01
     AND shadow_model.false_positive_rate <= live_model
     AND P99 inference latency within 5ms of live:
    → Gradual promotion: 5% → 25% → 100% live traffic over 3 days
  ELSE:
    → Abort promotion, rollback
```

---

## Failure Modes and Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Redis down | Feature fetch fails → no score | Fail-open: ALLOW + SCORED_DEGRADED=true; queue for REVIEW |
| ONNX model crash | ML score unavailable | Rule-only scoring fallback (lower confidence, conservative thresholds) |
| Kafka unavailable | Audit trail drops; feature pipeline stalls | Local audit buffer (ring buffer in memory, flush to DB directly) |
| Rule misconfiguration | Massive block rate spike | Rule change requires peer review; traffic spike auto-alert (block rate > 2× baseline) |
| Feature pipeline lag | Stale velocity counters | Monitor Kafka consumer lag; alert if > 30s; fallback to DB-based velocity check |

---

## Observability

```yaml
# Critical metrics
fraud_scoring_latency_p99{model_version}
fraud_decision_distribution{decision=ALLOW|REVIEW|BLOCK}  # real-time fraud rate signal
fraud_rule_hits_total{rule_id}                            # per-rule trigger rate
fraud_feature_fetch_latency_p99                           # Redis lookup latency
ml_model_inference_latency_p99                            # ONNX model performance
fraud_false_positive_rate_7d                              # analyst verdicts rolling window
fraud_case_queue_depth                                    # analyst backlog
```

---

## Trade-offs

| Decision | Alternative | Why |
|---|---|---|
| Fail-open on timeout | Fail-closed (block on error) | Payment availability is top priority; fraud on degraded path is reviewed, not ignored |
| ONNX Runtime embedded | External model serving (Triton/TorchServe) | Eliminates network hop on hot path; saves 5–10ms per request |
| Redis sorted-set velocity | Kafka Streams state store | Redis accessible from all API pods; state store requires sticky routing |
| SpEL for rule DSL | Drools / Rete engine | Spring-native, low overhead for < 50 rules; Drools adds JVM warmup complexity |
| Shadow mode A/B | Hard cutover | Eliminates risk of bad model going fully live; enables data-driven promotion |
