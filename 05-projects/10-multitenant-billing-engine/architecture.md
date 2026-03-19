# Project 10: Architecture — Multi-Tenant SaaS Billing Engine

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                     Client / Platform Layer                           │
│   SaaS Dashboard      Customer Portal      Admin Reporting UI        │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│              API Gateway (Spring Cloud Gateway)                       │
│   JWT auth, tenant routing, rate limiting (usage endpoint: 10K/s)    │
└──────┬────────────────────┬──────────────────────┬───────────────────┘
       │                    │                      │
       ▼                    ▼                      ▼
 Subscription          Usage Ingestion         Billing Admin
 Service               Service                 Service
 (CRUD plans,          (202 Accept,            (invoice query,
  state machine,       Kafka producer          revenue reports,
  proration)           only, < 5ms)            dunning config)
       │                    │                      │
       │                    ▼                      │
       │             Kafka Cluster                 │
       │             ├── usage-events              │
       │             ├── invoice-events            │
       │             ├── dunning-events            │
       │             └── webhook-delivery-events   │
       │                    │                      │
       │             ┌──────┘                      │
       │             ▼                             │
       │      Usage Aggregation                    │
       │      Consumer                             │
       │      (Kafka consumer,                     │
       │       micro-batch INSERT                  │
       │       to PostgreSQL)                      │
       │                                           │
       ▼                                           ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        PostgreSQL (primary)                           │
│                                                                      │
│  subscriptions         (id, customer_id, plan_id, status, period_*)  │
│  plans                 (id, tenant_id, price, features, metering)    │
│  invoices              (id, customer_id, period_id, line_items, tot) │
│  billing_events        (APPEND ONLY — charge attempts, outcomes)     │
│  usage_events          (customer_id, metric_name, quantity, ts)      │
│  webhook_endpoints     (customer_id, url, events[], secret)          │
│  outbox_events         (for webhook delivery)                        │
└────────────────────────────────┬─────────────────────────────────────┘
                                 │
               ┌─────────────────┼──────────────────┐
               ▼                 ▼                  ▼
         ┌──────────┐    ┌────────────────┐  ┌─────────────────────┐
         │ Stripe   │    │  Billing Close │  │  Webhook Delivery   │
         │ Payment  │    │  Job Scheduler │  │  Worker             │
         │ API      │    │  (billing      │  │  (Kafka consumer,   │
         │          │    │   period cron) │  │   HMAC-signed POST, │
         └──────────┘    │               │  │   with retry)       │
                         │  Dunning      │  └─────────────────────┘
                         │  Scheduler    │
                         └───────────────┘
                                 │
                                 ▼
                           ClickHouse
                           (MRR, ARR, churn,
                            cohort reports)
```

---

## Invoice Generation Sequence (Billing Period Close)

```
Billing Close Job runs for each subscription with period_end <= NOW():

┌─────────────────────────────────────────────────────────────────────┐
│  Step 1: Usage Aggregation                                          │
│                                                                     │
│  SELECT metric_name, SUM(quantity) as total                         │
│  FROM usage_events                                                  │
│  WHERE customer_id = :cid AND period_id = :pid                      │
│  GROUP BY metric_name                                               │
│  → { "api_calls": 450000, "storage_gb_hours": 1200 }               │
└─────────────────────────────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Step 2: Invoice Calculation                                        │
│                                                                     │
│  line_items = [                                                     │
│    { type: "BASE",  desc: "Pro Plan", amount: 99.00 },              │
│    { type: "USAGE", desc: "API calls (450K × $0.0001)", amt: 45.00 }│
│    { type: "USAGE", desc: "Storage (1200 GB-hrs × $0.01)", amt: 12} │
│    { type: "PRORATION", desc: "Plan change credit", amt: -15.50 }  │
│  ]                                                                  │
│  subtotal = 99 + 45 + 12 - 15.50 = 140.50                          │
│  tax = TaxService.calculateTax(140.50, customer.country)           │
│  total = 140.50 + 14.05 = 154.55                                   │
└─────────────────────────────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Step 3: INSERT Invoice (immutable)                                 │
│                                                                     │
│  INSERT INTO invoices (                                             │
│    id, customer_id, period_id,                                      │
│    line_items (JSONB),                                              │
│    subtotal, tax_amount, total,                                     │
│    status = 'OPEN',                                                 │
│    created_at = NOW()                                               │
│  ) RETURNING id                                                     │
│  ← Invoice is NEVER updated. Status changes go to billing_events.  │
└─────────────────────────────────────────────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Step 4: Stripe Charge (idempotent)                                 │
│                                                                     │
│  idempotency_key = "invoice-charge-" + invoiceId                   │
│  response = stripe.charges.create({                                 │
│    customer: stripeCustomerId,                                      │
│    amount: 15455,  ← cents                                          │
│    currency: "usd",                                                 │
│    idempotencyKey: idempotency_key                                  │
│  })                                                                 │
│                                                                     │
│  IF crash here and retry → Stripe returns same response, no dedup  │
└─────────────────────────────────────────────────────────────────────┘
                   │
        ┌──────────┴───────────┐
        ▼ success              ▼ failure
billing_events:          billing_events:
  CHARGE_SUCCEEDED          CHARGE_FAILED
  stripe_charge_id          error_code
  │                         │
  ▼                         ▼
outbox_events:          subscription.status
  invoice.paid event        = PAST_DUE
  → webhook + Kafka       Enqueue dunning job
```

---

## Usage Metering Pipeline

```
High-throughput path (1M events/sec):

POST /api/v1/usage
  → Validate request (< 1ms)
  → Kafka.send("usage-events", customerId_key, event_json)
    (async, fire-and-forget from API perspective)
  → Return 202 Accepted immediately

Kafka Usage Consumer (10 consumer instances):
  Batch poll: 500 events per poll, 100ms max batch wait
  INSERT INTO usage_events VALUES (...), (...), ...  ← batch insert
  Commit offset AFTER successful DB insert
  Throughput: 500 × 10 consumers × 10 polls/s = 50K events/s per consumer group
  10 consumer groups → 500K events/s; scale consumers for 1M/s target

Aggregation at billing close (not streaming — no real-time running total needed):
  Period-end query aggregates entire period in one SQL GROUP BY.
  This is fast for 30-day periods (≤ 1M rows per customer, all indexed by customer_id + period_id).
```

---

## Webhook Delivery (Outbox Pattern)

```
On billing event (invoice paid, subscription changed, etc.):

1. INSERT outbox_events IN SAME TRANSACTION as the business change:
   (event_type, payload, customer_id, status=PENDING)
   ← Guarantees: if business change commits, webhook will eventually deliver

2. Outbox Poller (every 1 second):
   SELECT id, event_type, payload, customer_id FROM outbox_events
   WHERE status = 'PENDING' AND retry_at <= NOW()
   LIMIT 100
   FOR UPDATE SKIP LOCKED

3. For each event: lookup registered webhook endpoints for customer + event_type

4. HTTP POST to endpoint (30s timeout):
   Headers:
     X-Signature: HMAC-SHA256(payload, endpoint.secret)  ← customer verifies
     X-Event-Type: invoice.paid
     X-Delivery-ID: {deliveryId}

5. On success: UPDATE outbox_events SET status=DELIVERED, delivered_at=NOW()

6. On failure: exponential backoff retry
   Attempt: 1m, 5m, 30m, 2h, 8h, 24h, 48h (then FAILED after 72h total)
   UPDATE outbox_events SET retry_at = NOW() + backoff, attempts++
```

---

## Dunning State Machine

```
CHARGE_FAILED event → subscription.status = PAST_DUE

Dunning schedule (all times relative to first failure):

T+0:    Initial failure notification email (payment_failed template)
T+24h:  Retry 1 → stripe.charges.create(idempotency="dunning-{invoiceId}-1")
         success → ACTIVE + "payment received" email
         failure → schedule next
T+72h:  Retry 2 + "account at risk" email
T+120h: Retry 3 + "service suspension warning" email
T+168h: Final retry + "final notice" email
T+192h: subscription.status = UNPAID; apply service restrictions
T+216h: subscription.status = CANCELLED; emit subscription.cancelled event

Recovery at any point: PAST_DUE → ACTIVE on successful charge
All dunning actions are idempotent (keyed on invoiceId + attempt number)
```

---

## Failure Modes and Mitigations

| Failure | Impact | Mitigation |
|---|---|---|
| Stripe API down | Charges fail | Retry with exponential backoff; dunning schedule absorbs transient outages |
| Double charge | Customer overcharged | Deterministic idempotency key per invoice; Stripe idempotency deduplicates |
| Billing close job crash mid-run | Some invoices not generated | Job is idempotent: checks IF invoice already EXISTS for period_id, skips if so |
| Usage events lost | Under-billing | Kafka replication factor=3; consumers commit after DB insert (at-least-once) |
| Usage consumer lag | Delayed aggregation | Monitor consumer lag; billing close waits for consumer_lag < 100 events before closing |
| Webhook endpoint down | Events not delivered | Outbox retry up to 72h; delivery history viewable by customer for debugging |
| PostgreSQL primary failure | Billing paused | Read replicas serve reads; billing close waits for primary recovery; no data loss (sync replica) |

---

## Revenue Reporting Queries (ClickHouse)

```sql
-- Monthly Recurring Revenue (MRR)
SELECT
    toStartOfMonth(period_end) as month,
    sum(total) / period_months as mrr
FROM invoices
WHERE status = 'PAID'
GROUP BY month
ORDER BY month

-- Monthly Churn Rate
SELECT
    month,
    cancelled_subscriptions / start_of_month_active_subscriptions * 100 as churn_pct
FROM monthly_subscription_snapshot
ORDER BY month

-- Cohort Analysis: MRR retention by signup month
SELECT
    cohort_month, months_since_signup,
    avg(active_mrr / initial_mrr) as retention_rate
FROM customer_cohort_revenue
GROUP BY cohort_month, months_since_signup
```

---

## Trade-offs

| Decision | Alternative | Why |
|---|---|---|
| Kafka buffer for usage events | Direct DB write | 1M/sec to PostgreSQL is not viable; Kafka absorbs burst and micro-batches to DB |
| Aggregate at period close, not streaming | Real-time running total | Running total adds complexity (aggregation state); period-end SQL GROUP BY is simple, correct, and fast enough |
| Immutable invoice + billing_events log | Updateable invoice status column | Append-only audit trail required for SOC 2 / GAAP; simplifies reconciliation and debugging |
| Outbox pattern for webhooks | Direct HTTP call in transaction | Direct call in transaction couples webhook reliability to DB transaction; outbox decouples |
| Idempotency key per invoice | Per-request UUID | Deterministic key survives service restart; UUID requires persisting the key before the Stripe call |
