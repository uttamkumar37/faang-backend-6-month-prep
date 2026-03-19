# Project 5: Distributed Job Scheduler

## Overview

A production-grade distributed job scheduler that executes cron-based and one-off tasks across a cluster with exactly-once semantics, leader election, dynamic scaling, and full observability. Comparable in concept to internal schedulers used at LinkedIn (Azkaban), Netflix (Fenzo), and Airflow.

**Business Value**: Replaces fragile cron-on-a-single-server setups; enables elastic at-scale batch processing with guaranteed delivery and retry budgets.

## Why this is a strong portfolio project

Distributed schedulers expose every hard problem in distributed systems in one place: leader election, split-brain prevention, distributed locking, exactly-once vs at-least-once trade-offs, node failure recovery, clock skew handling, and ordered event delivery. Getting this right demonstrates SDE II/Senior depth that few candidates show.

---

## Features

- **Cron and one-off jobs**: Standard cron expressions (quartz syntax) plus instant/delayed triggers.
- **Exactly-once execution**: Distributed lock via Redis or PostgreSQL advisory lock before dispatch.
- **Leader election**: One scheduler node is the active dispatcher at any time; followers standby hot.
- **Pluggable executors**: HTTP webhook, Kafka message, gRPC call, internal Java handler.
- **Job DAG support**: Dependencies between jobs — a job runs only when all parents succeed.
- **Retry with backoff**: Configurable exponential backoff, max attempts, dead-letter queue.
- **Distributed tracing**: Each job execution tied to a trace ID for end-to-end observability.
- **Admin UI API**: Pause, resume, trigger, and inspect job history via REST.

---

## Tech Stack

| Layer | Technology |
|---|---|
| API | Spring Boot 3.x, Java 21 |
| Leader election | ZooKeeper (Apache Curator) or Redis SETNX |
| Distributed locking | Redisson RedLock or PostgreSQL advisory locks |
| Scheduler | Custom time-wheel implementation (O(1) scheduling) |
| Job store | PostgreSQL (jobs, triggers, execution_history tables) |
| Message bus | Kafka (job-events, dead-letter-queue topics) |
| Cache | Redis (lock tokens, hot job metadata) |
| Observability | Micrometer + Prometheus + Grafana, Jaeger tracing |
| Infra | Kubernetes (StatefulSet for scheduler nodes), Helm |

---

## Functional Requirements

- Accept job registration with cron expression or trigger time.
- Execute each job exactly once per scheduled slot under any single-node failure.
- Support job dependencies — execute child job only after all parents complete.
- Retry failed jobs up to a configured limit with exponential backoff.
- Emit structured events for job lifecycle: SCHEDULED → TRIGGERED → RUNNING → SUCCESS / FAILED.
- Allow live pause, resume, and manual trigger of any job via API.

## Non-Functional Requirements

- Handle 100K registered jobs and 10K triggered executions per minute.
- Job trigger lag (wall clock vs actual execution start) under 1 second P99.
- Zero duplicate executions under single-node network partition failure.
- Scheduler leader failover recovery under 5 seconds.
- Full execution history retained for 90 days with queryable audit trail.

## Success Metrics

- Duplicate execution rate: effectively zero (measured per slot per job).
- Trigger lag P99 < 1 second under 10K exec/min load.
- Leader failover time < 5 seconds under Kubernetes pod restart.
- DAG completion rate: 99.9% with retry budgets.

---

## API Endpoints

```
POST /api/v1/jobs
  Body: { "name": "...", "cron": "0 * * * *", "executor": { "type": "HTTP", "url": "..." }, "maxRetries": 3 }
  Response: { "jobId": "...", "nextExecution": "..." }

PUT  /api/v1/jobs/{jobId}/pause
PUT  /api/v1/jobs/{jobId}/resume
POST /api/v1/jobs/{jobId}/trigger     (manual immediate trigger)
DELETE /api/v1/jobs/{jobId}

GET  /api/v1/jobs/{jobId}/executions  (history, paginated)
GET  /api/v1/jobs/{jobId}/executions/{execId}

POST /api/v1/jobs/{jobId}/dependencies
  Body: { "requiredJobIds": ["job-a", "job-b"] }
```

---

## Job State Machine

```
SCHEDULED ──► TRIGGERED ──► RUNNING ──► SUCCESS
                               │
                               ▼
                            FAILED ──► RETRYING (backoff)
                               │           │
                               │    (max retries exhausted)
                               ▼           ▼
                         DEAD_LETTER ◄─────┘
                         (DLQ topic)
```

---

## Key Engineering Challenges

### 1. Preventing Duplicate Execution (the hard part)
Use PostgreSQL advisory lock keyed on `(jobId, slotEpoch)`. Only one scheduler node can acquire the lock; it dispatches and immediately writes `execution_status=RUNNING`. Even if the node crashes post-dispatch, the lock row prevents re-dispatch until the heartbeat TTL expires.

### 2. Leader Election Without ZooKeeper
PostgreSQL advisory lock on a well-known key (`scheduler_leader_lock`). Holder refreshes a heartbeat row every 2 seconds. If heartbeat is stale (> 10 s), any follower races to acquire the lock — the first winner becomes leader.

### 3. Clock Skew
Scheduler nodes do NOT use local system time for scheduling decisions. All temporal comparisons use PostgreSQL `NOW()` as the authoritative clock, avoiding multi-node clock drift issues.

### 4. Time-Wheel for O(1) Scheduling
A two-level timing wheel (coarse: seconds, fine: ms) stores upcoming triggers. Scanning a future-trigger table every second is O(n); the time wheel is O(1) per tick for large job counts.

---

## Resume Impact Bullets

- Built a distributed job scheduler handling 10K executions/min with exactly-once semantics via PostgreSQL advisory locks, eliminating duplicate executions under single-node failures.
- Implemented leader election and hot-standby failover achieving sub-5-second recovery, replacing fragile single-node cron with a horizontally scalable cluster.
- Designed a time-wheel O(1) trigger engine supporting 100K registered jobs with P99 trigger lag under 1 second.
