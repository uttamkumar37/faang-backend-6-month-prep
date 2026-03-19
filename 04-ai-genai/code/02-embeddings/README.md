# 02 — Embeddings & Vector Databases

How text becomes searchable numbers, and how to query them efficiently.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [02-vector-databases.md](02-vector-databases.md) | What vectors are, cosine similarity, why SQL is not enough, HNSW index, pgvector vs dedicated vector DBs (Pinecone, Weaviate, Qdrant), filtering, hybrid search |
| 2 | [EmbeddingPipelineExample.java](EmbeddingPipelineExample.java) | Building an ingestion pipeline with Spring AI: load docs → chunk → embed → store in pgvector |

## Key concepts to own

- Cosine similarity vs dot product vs Euclidean distance — when to use each.
- HNSW: why it is fast and what `m` / `ef_construction` parameters control.
- Chunking strategies: fixed-size, recursive split, semantic split.
- Hybrid search: combining keyword BM25 with vector similarity.

## Study method

1. Read the theory file; draw the HNSW graph layers from memory.
2. Trace `EmbeddingPipelineExample.java` end-to-end.
3. Explain: "A user searches for 'Java thread pooling'. Walk me through what happens from query string to returned chunks."
