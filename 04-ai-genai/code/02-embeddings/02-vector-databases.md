# Vector Databases Deep Dive

## What is a Vector?

Text, images, audio → numeric representation (embedding) preserving semantic meaning.

```
"Java virtual threads"          → [0.23, -0.47, 0.12, ..., 0.89]  (1536 dims)
"Project Loom lightweight threads" → [0.21, -0.45, 0.15, ..., 0.87]  (very close!)
"Coffee bean varieties"          → [0.71,  0.34, -0.56, ..., 0.12]  (far away)

Cosine similarity = cos(angle between vectors)
  values: -1 (opposite) to 1 (identical meaning)
```

---

## Why Not Just Use SQL?

Exact-match (SQL): find rows where column = value.  
Vector search: find K rows where embedding is MOST SIMILAR to query embedding.

```sql
-- Exact match (SQL):
SELECT * FROM products WHERE name = 'Java Book'  → O(log n) with index

-- Similarity search (vector):
SELECT *, (embedding <=> $query_vec) AS dist
FROM documents
ORDER BY dist
LIMIT 5;  → O(n) full scan without ANN index
```

Vector DBs add ANN (Approximate Nearest Neighbor) indexes to make this fast.

---

## HNSW Index

Hierarchical Navigable Small World — the dominant ANN index algorithm.

```
Layer 3: sparse long-range connections
          A ─────────── F
          
Layer 2:  A ── C ─── F
          
Layer 1:  A ─ B ─ C ─ D ─ E ─ F

Layer 0 (all nodes):
          A ─ B ─ C ─ D ─ E ─ F ─ G ─ H (dense connections)

Search: start at top layer, greedily descend → very fast path to candidates.
```

Complexity: O(log n) for build and query.  
Tradeoff: `ef_construction` (index quality) vs build time; `ef_search` vs recall.

### HNSW Parameters

```
m = 16          # max connections per node (more = better recall, more memory)
ef_construction = 200  # beam width during index build (higher = better quality)
ef_search = 50  # beam width during search (higher = better recall, slower)
```

---

## Distance Metrics

| Metric | Formula | Best For |
|---|---|---|
| Cosine Similarity | 1 - (A·B / ‖A‖‖B‖) | Text embeddings (magnitude varies) |
| L2 (Euclidean) | √Σ(ai - bi)² | Image embeddings, dense vectors |
| Dot Product | A·B | When vectors are normalized (=cosine); max inner product search |
| Hamming | Count different bits | Binary embeddings |

> For most NLP use cases: normalize embedding → use cosine (or dot product, they're equivalent).

---

## pgvector

PostgreSQL extension for vector storage. Best for teams already using Postgres.

```sql
-- Enable extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create table with vector column
CREATE TABLE documents (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT,
    embedding   vector(1536),    -- OpenAI text-embedding-3-small dimension
    metadata    JSONB,
    created_at  TIMESTAMP DEFAULT now()
);

-- Create HNSW index
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Cosine similarity search
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM documents
ORDER BY embedding <=> $1          -- <=> is cosine distance operator
LIMIT 5;

-- Hybrid: combine with keyword filter
SELECT id, content, (embedding <=> $1) AS dist
FROM documents
WHERE metadata->>'category' = 'policy'
ORDER BY dist
LIMIT 5;
```

### Spring AI + pgvector

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
```

---

## Pinecone (Managed)

Fully managed vector DB. No infra to manage.

```python
# Python client example (for comparison — Spring AI abstracts this)
import pinecone

pc = pinecone.Pinecone(api_key="...", environment="us-east1-gcp")
index = pc.Index("my-index")

# Upsert vectors
index.upsert(vectors=[
    {"id": "doc1", "values": [0.1, 0.2, ...], "metadata": {"text": "...", "source": "faq"}},
])

# Query
results = index.query(vector=[0.1, 0.3, ...], top_k=5, include_metadata=True)
```

Key features: namespaces (multi-tenancy), metadata filtering, real-time updates.

---

## Metadata Filtering

Combine vector similarity with attribute filters:

```
Vector search: "What are the refund policies?"
Filter: source = "support_docs" AND language = "en" AND updated_after = "2024-01-01"
→ Narrows the search space before vector computation
```

In pgvector: WHERE clause.  
In Pinecone: filter parameter.  
In Weaviate: GraphQL where clause.

---

## Chunking & Embedding Tradeoffs

```
Shorter chunks (128 tokens):
  + Precise retrieval — relevant chunk is small
  - May lose context

Longer chunks (1024 tokens):
  + More context per chunk
  - Relevant sentence diluted by surrounding text → lower similarity score

Recommended: 256-512 tokens, 20-50 token overlap.
Parent-child: store small child chunks for retrieval, re-fetch parent for full context.
```

---

## Multi-Tenancy in Vector DBs

| Approach | pgvector | Pinecone |
|---|---|---|
| Schema per tenant | ✓ | ✗ |
| Metadata filter (tenant_id) | ✓ | ✓ (filter) |
| Namespace | ✗ | ✓ (namespaces) |
| Separate index | ✓ (table) | ✓ (separate index) |

For security: always filter by tenant_id in queries — never allow cross-tenant access.

---

## Interview Tips

- HNSW is the standard ANN index — know the tradeoff: recall vs speed.
- Cosine similarity for text embeddings because magnitude carries no meaning.
- pgvector for Postgres shops (simpler ops); Pinecone/Weaviate for large-scale standalone vector workloads.
- Metadata filtering + vector search = hybrid search, important for production RAG accuracy.

---

## Vector Database Comparison: Qdrant vs Weaviate vs pgvector

| Feature | pgvector | Qdrant | Weaviate | Pinecone |
|---|---|---|---|---|
| Deployment | Add-on to Postgres | Standalone / cloud | Standalone / cloud | Managed cloud only |
| Max scale (approx) | ~10M vectors (with tuning) | 100M+ vectors | 100M+ vectors | Unlimited (managed) |
| Hybrid search | Manual BM25 via pg_bm25 | Built-in sparse + dense | Built-in BM25 + dense | Built-in (sparse vectors) |
| Filtering | SQL WHERE clause | JSON payload filter | GraphQL filter | Metadata filter |
| Multi-tenancy | Schema-per-tenant or row-level | Dedicated collections or payload filter | Classes or tenancy API | Namespaces |
| Spring AI support | ✓ First-class | ✓ | ✓ | ✓ |
| Best for | Postgres-based apps, simple RAG | High-performance, self-hosted production | Flexible schema, GraphQL | Fully managed, no ops |
| Cost | Free (Postgres hosting) | Free + cloud plans | Free + cloud | Paid (generous free tier) |

---

## Hybrid Search: BM25 + Dense + Reciprocal Rank Fusion

Dense vector search excels at semantic similarity.
BM25 (keyword) search excels at exact term matching.
Hybrid combines both for the best of both worlds.

```
Query: "Java OutOfMemoryError heap space"
Dense results: [JVM tuning guide, GC configuration, heap analysis guide]
BM25 results:  [OutOfMemoryError javadoc, heap dump analysis, -Xmx flag guide]
Hybrid (RRF): [heap analysis guide, JVM tuning guide, OutOfMemoryError javadoc, ...]
```

### RRF (Reciprocal Rank Fusion)

$RRF(d) = \sum_{r \in R} \frac{1}{k + r(d)}$

Where:
- $R$ = set of rankings (dense, sparse)
- $r(d)$ = rank of document $d$ in ranking $R$
- $k$ = constant (default: 60) to dampen high ranks

```java
@Component
public class HybridSearchService {

    private final VectorStore vectorStore;
    private final KeywordSearchService bm25Service;

    public List<Document> hybridSearch(String query, int topK) {
        // Run both in parallel
        CompletableFuture<List<Document>> denseFuture = CompletableFuture.supplyAsync(() ->
            vectorStore.similaritySearch(SearchRequest.query(query).withTopK(topK * 2)));

        CompletableFuture<List<Document>> sparseFuture = CompletableFuture.supplyAsync(() ->
            bm25Service.search(query, topK * 2));

        List<Document> denseResults;
        List<Document> sparseResults;
        try {
            denseResults  = denseFuture.get();
            sparseResults = sparseFuture.get();
        } catch (Exception e) {
            throw new RuntimeException("Hybrid search failed", e);
        }

        return reciprocalRankFusion(denseResults, sparseResults, topK);
    }

    private List<Document> reciprocalRankFusion(
            List<Document> list1, List<Document> list2, int topK) {
        int K = 60;
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Document> docById = new HashMap<>();

        // Score by ID using RRF formula
        for (int i = 0; i < list1.size(); i++) {
            Document doc = list1.get(i);
            String id = docId(doc);
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
            docById.put(id, doc);
        }
        for (int i = 0; i < list2.size(); i++) {
            Document doc = list2.get(i);
            String id = docId(doc);
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
            docById.put(id, doc);
        }

        // Sort by RRF score descending, return top-K
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> docById.get(e.getKey()))
            .collect(Collectors.toList());
    }

    private String docId(Document doc) {
        return (String) doc.getMetadata().getOrDefault("id", doc.getContent().substring(0, 50));
    }
}
```

---

## Embedding Model Selection and Fine-Tuning

### Off-the-shelf embedding models

| Model | Dimensions | Max tokens | Free | Notes |
|---|---|---|---|---|
| text-embedding-3-small | 1536 (default) | 8191 | No (API) | Best cost/quality ratio |
| text-embedding-3-large | 3072 (default) | 8191 | No (API) | Highest quality |
| nomic-embed-text | 768 | 8192 | Yes (Ollama) | Best open-source option |
| bge-m3 | 1024 | 8192 | Yes (HuggingFace) | Multilingual, hybrid search support |
| e5-large-v2 | 1024 | 512 | Yes (HuggingFace) | Strong on BEIR benchmarks |

Matryoshka Representation Learning (MRL):
- `text-embedding-3-*` models support dimension reduction without re-embedding.
- Use 256 dimensions instead of 1536 → 6x storage savings, minimal quality loss.

```java
// OpenAI: reduce dimensions at query time
EmbeddingOptions options = OpenAiEmbeddingOptions.builder()
    .withModel("text-embedding-3-small")
    .withDimensions(256)   // MRL: reduce from 1536 to 256
    .build();
```

### When to fine-tune embeddings

Fine-tune when your domain has specialized vocabulary not well-represented in training:
- Legal contracts (specific legal phrasing)
- Medical records (ICD codes, medication names)
- Internal codebase search (proprietary API names)

Process:
1. Collect (query, relevant passage, irrelevant passage) triplets.
2. Fine-tune using sentence-transformers with TripletLoss or MultipleNegativesRankingLoss.
3. Evaluate improvement on a held-out test set using NDCG@10 or MRR@10.

---

## Index Tuning Parameters

pgvector HNSW tuning:

```sql
-- Index build: balance quality vs build time
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops)
WITH (
    m = 16,           -- max connections per layer; ↑ = better recall, more memory
    ef_construction = 128  -- candidate pool during build; ↑ = better recall, slower build
);

-- Query time: ef_search = candidate pool during ANN search
SET hnsw.ef_search = 100;   -- ↑ = better recall, slower queries

-- Rule of thumb:
-- ef_construction = 2x to 4x m
-- ef_search >= topK * 2
```

pgvector IVFFlat (faster build, slightly lower recall):

```sql
CREATE INDEX ON documents USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);  -- number of clusters (lists) = sqrt(num_rows)

-- Query time: probes = number of clusters to search
SET ivfflat.probes = 10;  -- ↑ = better recall, slower
```

---

## Production Operations

### Index backup and restore (pgvector)

```sql
-- Full backup via pg_dump includes the index
pg_dump -Fc mydb > backup.dump

-- Restore
pg_restore -d mydb backup.dump

-- For large tables: use point-in-time recovery (WAL streaming) instead
```

### Re-embedding when model version changes

```java
@Component
public class IndexMigrationService {

    // When you upgrade the embedding model, all existing vectors become invalid
    // Use a migration table to track re-embedding progress
    @Transactional
    public void reembedPage(int pageSize, int pageNumber) {
        // Read existing chunks
        List<DocumentChunk> chunks = chunkRepo.findAll(
            PageRequest.of(pageNumber, pageSize)).getContent();

        // Re-embed with new model
        List<float[]> newEmbeddings = embeddingModel.embedAll(
            chunks.stream().map(DocumentChunk::getContent).toList());

        // Update vector store
        for (int i = 0; i < chunks.size(); i++) {
            Document doc = new Document(chunks.get(i).getContent());
            doc.getMetadata().putAll(chunks.get(i).getMetadata());
            // Adding with same ID updates in place (implementation-dependent)
            vectorStore.add(List.of(doc));
        }

        log.info("Re-embedded page {} ({} chunks)", pageNumber, chunks.size());
    }
}
```

### Monitoring vector store health

Key metrics to track:
- `vector.search.latency.p99` — target < 100ms
- `vector.index.size` — alert if grows > 80% of allocated memory
- `vector.search.recall@10` — run nightly evaluation, alert if drops > 5%
- `vector.ingestion.rate` — chunks per second during ingestion
- `vector.store.error.rate` — alert if > 0.1%

```yaml
# Grafana alert (pgvector via pg_stat_statements)
alert: VectorSearchSlow
expr: pg_stat_statements_mean_exec_time_seconds{query=~".*<->.*"} > 0.1
for: 5m
labels:
  severity: warning
annotations:
  summary: "Vector similarity search P99 exceeds 100ms"
```
