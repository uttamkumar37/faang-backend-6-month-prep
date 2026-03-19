# 03 — RAG (Retrieval-Augmented Generation)

Combine retrieved documents with an LLM to answer questions on private data.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [03-rag-architecture.md](03-rag-architecture.md) | Full ingestion + query pipeline, chunking strategies, reranking, query rewriting, multi-tenancy, evaluation (faithfulness, relevance, context precision) |
| 2 | [RagServiceExample.java](RagServiceExample.java) | Spring AI RAG service: embed query → vector search → assemble prompt → call LLM → return cited answer |

## Key concepts to own

- Offline (ingestion) vs online (query) path and their separate latency budgets.
- Why reranking improves precision and when the latency cost is worth it.
- Evaluation metrics: faithfulness (answer grounded in docs?), relevance (docs match question?).
- Multi-tenant RAG: metadata filters to isolate tenant data in a shared vector store.

## Study method

1. Draw the full RAG pipeline on paper from memory (ingestion side + query side).
2. Trace `RagServiceExample.java` — identify where each pipeline stage happens.
3. Answer: "Your RAG system is returning irrelevant answers. Walk me through how you debug it."
