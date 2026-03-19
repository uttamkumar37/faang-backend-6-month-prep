# Project 1: Architecture — AI Support Copilot

## Component Diagram

```
┌───────────────────────────────────────────────────────────────────┐
│                         Client Layer                               │
│   Support Agent UI      Customer Chat Widget      Admin Portal    │
│  (React + SSE stream)   (Embedded iframe)        (Angular SPA)    │
└────────────────┬───────────────────┬───────────────────┬──────────┘
                 │                   │                   │
                 └───────────────────┴───────────────────┘
                                     │
                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                   API Gateway (Spring Cloud Gateway)             │
│   JWT validation, rate limiting (Redis), tenant routing,        │
│   request logging, CORS enforcement                             │
└─────────────────────────────────┬───────────────────────────────┘
                                  │
            ┌─────────────────────┼─────────────────────┐
            ▼                     ▼                     ▼
   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
   │  Chat API       │  │  Ingestion API  │  │   Admin API     │
   │  (Spring Boot)  │  │  (Spring Boot)  │  │  (Spring Boot)  │
   │  Port 8080      │  │  Port 8081      │  │  Port 8082      │
   └────────┬────────┘  └────────┬────────┘  └────────┬────────┘
            │                    │                     │
            ▼                    ▼                     │
   ┌─────────────────┐  ┌─────────────────┐           │
   │  Spring AI      │  │  Kafka Producer │           │
   │  ChatClient +   │  │  (doc-ingestion │           │
   │  QuestionAnswer │  │   topic)        │           │
   │  Advisor        │  └────────┬────────┘           │
   └────────┬────────┘           │                    │
            │                    ▼                    │
            │         ┌─────────────────┐            │
            │         │  Ingestion      │            │
            │         │  Worker Service │            │
            │         │ (Kafka consumer)│            │
            │         │  PDFBox, Jsoup  │            │
            │         └────────┬────────┘            │
            │                  │                     │
            ▼                  ▼                     ▼
┌───────────────────────────────────────────────────────────────┐
│                      Data Layer                               │
│                                                               │
│  PostgreSQL (primary)          Redis Cluster                  │
│  ├── application DB            ├── session cache (TTL 30min)  │
│  │   (tenants, sessions,       ├── rate limit counters        │
│  │    messages, sources)       └── hot query cache (TTL 5min) │
│  └── pgvector extension                                       │
│      (vector(1536) columns,                                   │
│       HNSW index)                                             │
└───────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────┐
│  OpenAI API                     │
│  ├── text-embedding-3-small     │
│  └── gpt-4o-mini                │
└─────────────────────────────────┘
```

---

## RAG Data Flow (Chat Request)

```
1. User asks: "How long do refunds take?"
   
2. API Gateway:
   - Validate JWT / API key
   - Check rate limit (Redis): 100 req/min per tenant
   - Route to Chat API
   
3. Chat API:
   - Load session history from Redis (last 10 turns)
   - If context window would exceed 8K tokens → summarize older history
   
4. Spring AI QuestionAnswerAdvisor (before LLM call):
   - Embed question: POST openai.com/v1/embeddings
   - Vector search: SELECT ... FROM vectors WHERE tenant_id = ? ORDER BY embedding <=> $query LIMIT 5
   - Filter: similarity_score > 0.60
   
5. Prompt construction:
   - System message: role + constraints
   - Context: top-5 retrieved chunks with source labels
   - History: last 5 turns
   - User message: question
   
6. LLM call: POST openai.com/v1/chat/completions (stream=true)
   
7. Response streaming:
   - Tokens arrive via Server-Sent Events
   - Final response stored in chat_messages table
   - Token count + confidence score logged to Micrometer
   
8. If confidence < 0.6:
   - Return answer with warning
   - Post alert to human_agent_queue (Kafka)
```

---

## Ingestion Data Flow

```
Admin uploads PDF
  → Ingestion API: store metadata, produce "ingest-jobs" Kafka message
  
Ingestion Worker consumes:
  1. Load PDF bytes from S3 (or in-memory for small files)
  2. Extract text: Apache PDFBox
  3. Section-aware chunking (split on H1/H2 headings found in PDF structure)
  4. For each chunk: batch embed via OpenAI text-embedding-3-small
  5. Store in pgvector: (chunk_text, embedding, tenant_id, source_id, chunk_index)
  6. Update knowledge_sources.status = 'indexed', chunks_count = N
  7. Publish "ingestion-completed" event → notify admin UI via WebSocket
```

---

## Kubernetes Deployment

```yaml
# Horizontal Pod Autoscaler — scale chat-api on CPU + custom token metric
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    name: chat-api
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: llm_tokens_per_second
      target:
        averageValue: 5000
```

---

## Observability

```
Dashboards (Grafana):
  - RAG retrieval quality: avg similarity score over time
  - LLM latency: P50/P99 first-token and total latency
  - Token usage & cost: tokens/hour broken down by tenant
  - Low-confidence rate: % of responses below threshold (quality signal)
  - Human escalation rate: % of responses routed to human

Alerts (PagerDuty):
  - OpenAI API error rate > 5% → SEV2
  - P99 chat latency > 8s → SEV3
  - Token budget > 90% for any tenant → INFO notification
```

---

## Trade-offs

| Decision | Why it helps | What it costs |
|---|---|---|
| pgvector over dedicated vector DB | Fewer moving parts and easier team adoption | Lower specialization and tuning headroom at very large scale |
| Kafka-based ingestion | Keeps document processing off the request path | Adds async operational complexity |
| Low-confidence human escalation | Safer product behavior for ambiguous answers | Higher support handoff volume on weak retrieval |
| Session history summarization | Controls context cost and latency | Can lose nuance from long conversations |

## Failure modes and mitigations

- Retrieval returns irrelevant chunks: enforce similarity threshold, tenant filters, and analytics on low-quality results.
- Model provider outage: fail closed with low-confidence response rather than hallucinated answer.
- Ingestion backlog growth: scale workers independently and track Kafka lag.
- Budget exhaustion for a tenant: stop new model calls and return a quota-aware message.

## What to measure in a real implementation

- First-token latency and full-response latency.
- Retrieval similarity distribution by query category.
- Percentage of answers with at least one valid citation.
- Escalation rate and resolution quality after escalation.
