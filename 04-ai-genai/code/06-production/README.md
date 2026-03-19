# 06 — Production AI Backend

Closing the gap between prototype and production: latency, cost, safety, and observability.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [06-production-ai-backend.md](06-production-ai-backend.md) | Latency budget breakdown (TTFT, streaming), cost controls (token caps, caching, model routing), safety (PII scrubbing, prompt injection guardrails), evaluation (RAGAS, G-eval), observability (LangFuse, Micrometer) |

## Key concepts to own

- Latency budget: LLM first-token latency dominates — streaming solves perceived latency, not actual.
- Cost controls: max_tokens cap + Redis semantic cache + small-model intent routing.
- Safety: separate safety check before passing user input to the main LLM chain.
- Evaluation pipeline: automated faithfulness + relevance scoring on every release.
- Observability: log every {input, retrieved_chunks, output, latency, token_count} for debugging.

## Study method

1. Read the file; build the latency budget table for the AI Support Copilot project.
2. Answer: "Your AI feature is costing $5K/day at current traffic. What do you do?"
3. Answer: "How do you catch LLM quality regressions before they reach production?"
