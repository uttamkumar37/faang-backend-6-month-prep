# 04 — AI / GenAI for Backend Engineers

## What's in this folder

All study material lives under `code/`, and each topic folder keeps the markdown notes next to the runnable Java examples.

### code/01-llm-foundations/
Understand how large language models work:
- `01-llm-fundamentals.md`

### code/02-embeddings/
How text becomes vectors and how to query them at scale:
- `02-vector-databases.md`
- `EmbeddingPipelineExample.java`

### code/03-rag/
Retrieval-Augmented Generation — grounding LLMs in real data:
- `03-rag-architecture.md`
- `RagServiceExample.java`

### code/04-spring-ai/
Spring AI framework for building LLM services in Java:
- `04-spring-ai.md`
- `SpringAiIntegrationExample.java`

### code/05-prompt-engineering/
Writing prompts that produce reliable, parseable production output:
- `05-prompt-engineering.md`

### code/06-production/
Latency, cost controls, safety, evaluation, and observability:
- `06-production-ai-backend.md`

## Learning path

```
01-llm-foundations/    ← START HERE — what an LLM is and how it works
02-embeddings/         ← How text becomes vectors; HNSW; pgvector
03-rag/                ← Ingestion + retrieval pipeline; reranking; evaluation
04-spring-ai/          ← Java API: ChatClient, VectorStore, structured output
05-prompt-engineering/ ← System prompts, few-shot, CoT, injection defense
06-production/         ← Latency budgets, cost controls, safety, observability
```

## What interviewers expect

- Design an AI feature end-to-end as a backend system.
- Know when to use RAG vs fine-tuning vs prompt engineering alone.
- Reason about latency, cost, and quality trade-offs.
- Know how to evaluate AI output quality before release.
- Know the security risks: prompt injection, data leakage, PII.

## Study method

1. Read the numbered markdown in the topic folder.
2. Open the corresponding Java file and trace through every method.
3. Close the file and re-implement the key flow from memory.
4. Explain it out loud as if in an interview.
