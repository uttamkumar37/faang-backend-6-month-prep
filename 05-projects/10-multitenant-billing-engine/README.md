# Project 10: Multi-Tenant SaaS Billing & Subscription Engine

## Overview

A production-grade billing and subscription management backend for a SaaS platform — handling subscription plans, usage-based metering, invoicing, payment collection, dunning, and webhook notifications. Comparable to building the core of what Stripe Billing or Chargebee does, but as your own backend.

**Business Value**: Manages $50M+ ARR across 10K+ business tenants with accurate metering, zero revenue leakage, and automated dunning that recovers 35% of failed payments.

## Why this is a strong portfolio project

Billing is where correctness and money intersect. It requires idempotent external API calls, exactly-once charge semantics, immutable audit ledgers, complex state machines, and graceful handling of partial failures across distributed systems. Every FAANG-scale product company has billing infrastructure, and engineers who understand it deeply are rare. This project demonstrates senior-level maturity: financial data integrity, compliance thinking, and business impact ownership.

---

## Features

- **Subscription management**: Create, upgrade, downgrade, pause, cancel plans with prorated billing.
- **Usage-based metering**: Record usage events; aggregate and bill at period end.
- **Invoice generation**: Automated invoice creation with line items, taxes, discounts.
- **Payment collection**: Stripe integration — idempotent charge, retry on failure.
- **Dunning workflow**: Automated retry schedule for failed payments with escalating actions.
- **Webhook delivery**: Reliable event delivery to customers with retry and signature verification.
- **Multi-tenant isolation**: Separate billing configurations, plans, and tax rules per tenant.
- **Audit ledger**: Immutable append-only record of every billing event (SOC 2 ready).
- **Revenue reporting**: MRR, ARR, churn rate, cohort analysis endpoints for the admin dashboard.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21 |
| Payment | Stripe API (idempotent charges, customer + payment methods) |
| Database | PostgreSQL (immutable ledger, subscriptions, invoices, usage events) |
| Metering buffer | Kafka (high-throughput usage events before aggregation) |
| Job scheduler | Project 5 pattern — internal cron for billing periods, dunning |
| Webhook delivery | Outbox pattern + Kafka (reliable, retryable, signed) |
| Cache | Redis (plan configs, active subscriptions — hot read cache) |
| Reporting | ClickHouse (aggregated revenue metrics, cohort analysis) |
| Observability | Micrometer, Prometheus, Grafana |

---

## Functional Requirements

- Create and manage subscriptions on configurable plans with upgrade/downgrade prorations.
- Record high-throughput usage events and accurately aggregate them at billing period close.
- Generate invoices, collect payment via Stripe, and manage retry on failure.
- Deliver webhook events to customer-registered endpoints with at-least-once semantics.
- Provide admin reporting for MRR, ARR, churn, and dunning effectiveness.

## Non-Functional Requirements

- Usage event ingestion at 1M events/second (via Kafka buffer).
- Invoice generation and charging for 100K customers within 2-hour billing window.
- Zero revenue leakage: every usage event accounted for in the final invoice.
- Idempotent Stripe charges — never double-charge a customer.
- Webhook delivery within 30 seconds of event, retried for up to 72 hours.
- Immutable audit ledger: billing records never updated, only appended.

## Success Metrics

- Revenue leakage rate: effectively zero (verified by usage event reconciliation job).
- Failed payment recovery rate via dunning: > 35% within 7-day dunning window.
- Invoice generation throughput: 100K invoices generated < 2 hours.
- Webhook delivery success rate: > 99.5% within 1 minute of event.
- Double-charge incidents: zero (idempotency key + Stripe dedup).

---

## API Endpoints

```
POST /api/v1/subscriptions
  Body: { "customerId": "...", "planId": "plan_pro", "paymentMethodId": "pm_..." }
  Response: { "subscriptionId": "...", "status": "ACTIVE", "currentPeriodEnd": "..." }

PATCH /api/v1/subscriptions/{subId}
  Body: { "newPlanId": "plan_enterprise" }   (upgrade/downgrade — proration applied)

POST  /api/v1/subscriptions/{subId}/cancel    (end of period or immediate)
POST  /api/v1/subscriptions/{subId}/pause
POST  /api/v1/subscriptions/{subId}/resume

POST /api/v1/usage
  Body: { "customerId": "...", "metricName": "api_calls", "quantity": 1500, "timestamp": "..." }
  (High-throughput endpoint — Kafka producer only, returns 202 Accepted)

GET  /api/v1/invoices?customerId=...&status=PAID|OPEN|VOID
GET  /api/v1/invoices/{invoiceId}

POST /api/v1/webhooks/endpoints
  Body: { "url": "https://...", "events": ["invoice.paid", "subscription.cancelled"] }
GET  /api/v1/webhooks/endpoints/{id}/deliveries   (delivery history)

GET  /api/v1/reports/mrr?month=2026-03
GET  /api/v1/reports/churn?from=...&to=...
```

---

## Subscription State Machine

```
TRIALING ──────────────────►─────────────────────────────────────────────┐
  │                                                                        │
  │ (trial ends)                                                           │
  ▼                                                                        │
ACTIVE ───────────────────────────────────────────────────────────────────┤
  │                                                                        │
  │ (payment fails)                                                        │
  ▼                                                                        │
PAST_DUE ──► dunning retries ──► recovery ──► ACTIVE                     │
  │                                                                        │
  │ (max dunning exhausted)                                                │
  ▼                                                                        ▼
UNPAID ──────────────────────────────────────────► CANCELLED (final state)

ACTIVE ──► PAUSED ──► ACTIVE   (re-activate)
ACTIVE ──► CANCELLED           (immediate or period-end)
```

---

## Billing Period Close Flow

```
Cron trigger: billing_close_job (nightly, batch)

For each subscription with period_end <= NOW():

1. Aggregate usage (Kafka consumer has already written to usage_events table):
   SELECT metric_name, SUM(quantity)
   FROM usage_events
   WHERE customer_id = ? AND period_id = ?
   GROUP BY metric_name

2. Calculate charges:
   base_charge = plan.base_price
   usage_charges = SUM(metric.quantity × metric.unit_price) for each metered metric
   prorations = prorated_credits_or_charges (from plan changes mid-period)
   tax = TaxService.calculate(base + usage + prorations, customer.country)
   total = base + usage + prorations + tax

3. Generate invoice (immutable):
   INSERT invoices (customer_id, period_id, line_items_jsonb, total, status=OPEN)
   (Never update — only status transitions via separate event log)

4. Charge via Stripe (idempotent):
   idempotency_key = "invoice-charge-{invoiceId}"   ← prevents double charge on retry
   stripe.charges.create({
     customer: stripeCustomerId,
     amount: totalInCents,
     idempotencyKey: idempotency_key
   })

5. On success:
   INSERT billing_events (invoice_id, type=CHARGE_SUCCEEDED, amount, stripe_charge_id)
   UPDATE subscription SET period_start/end = next_period
   Produce to Kafka: invoice.paid event → webhook delivery

6. On failure:
   INSERT billing_events (invoice_id, type=CHARGE_FAILED, error_code)
   Transition subscription to PAST_DUE
   Enqueue dunning job
```

---

## Dunning Workflow

```
Scheduled dunning retries (exponential schedule):

Day 1 after failure: Retry payment
Day 3: Retry + "Payment failed" email warning
Day 5: Retry + "Service at risk" email
Day 7: Final retry + "Final notice" email
Day 8: Mark subscription UNPAID, restrict service

Each retry:
  1. stripe.charges.create(idempotency_key = "dunning-{invoiceId}-attempt-{N}")
  2. On success: ACTIVE, send "account restored" email
  3. On failure: Schedule next retry
  4. After max attempts: CANCELLED, send "subscription cancelled" email
```

---

## Key Engineering Challenges

### 1. Usage Event Scale (1M events/sec)
Usage events go to Kafka immediately (202 Accepted, < 5ms). Kafka consumers aggregate events into `usage_events` PostgreSQL table in micro-batches. Billing close queries this table — no real-time aggregation needed.

### 2. Idempotent Stripe Charges
Every charge uses a deterministic idempotency key: `"invoice-charge-{invoiceId}"`. If the service crashes after calling Stripe but before writing the result, the next retry will return the same Stripe response (idempotent) — never creating a second charge.

### 3. Proration Calculation
When a customer changes plans mid-period: credit for unused days on old plan + charge for remaining days on new plan. Stored as a proration line item on the next invoice. Calculation uses `unused_days / period_days × plan_price`.

### 4. Immutable Audit Ledger
Invoice records are never updated. State transitions are separate `billing_events` rows (append-only). This provides a complete audit trail and simplifies SOC 2 compliance.

---

## Resume Impact Bullets

- Built a SaaS billing engine managing $50M+ ARR processing 1M usage events/sec through Kafka buffering with zero revenue leakage, verified via automated period-end reconciliation jobs.
- Implemented idempotent Stripe payment charging using deterministic idempotency keys per invoice, achieving zero double-charge incidents across all billing period close runs.
- Designed an automated dunning workflow recovering 35% of failed subscription payments through a 7-day escalating retry + notification schedule with configurable per-plan dunning policies.
