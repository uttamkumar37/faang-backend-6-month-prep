# RAG Architecture (Retrieval-Augmented Generation)

## What & Why

LLMs have a knowledge cutoff and can hallucinate facts. RAG grounds LLM responses in real data.

```
Without RAG: "What are our refund policies?" → hallucinated or generic answer
With RAG:    Search policy docs → inject into context → LLM answers from real docs

RAG = Retrieval (find relevant docs) + Augmented (inject into prompt) + Generation (LLM answers)
```

---

## RAG Pipeline Overview

```
INGESTION (offline):
Documents → Chunking → Embedding Model → Vector DB

QUERY (online):
User Question → Embed Question → Vector Search → Top-K docs
→ Reranker (optional) → Prompt with docs injected → LLM → Answer
```

---

## Step 1: Document Ingestion

### Document Loading

```
PDF → Apache PDFBox / PdfReader
Web → Selenium / Playwright (rendered) or Jsoup (static)
Word → Apache POI
Database → JDBC + custom loader
```

### Chunking Strategies

**Fixed-size chunking:**

```
chunk_size = 512 tokens, overlap = 50 tokens
Pros: simple, predictable
Cons: sentences split mid-thought
```

**Recursive character split:**

Split on `\n\n` → `\n` → `. ` → ` ` in sequence until chunks are small enough.  
Preserves paragraph/sentence structure. Used in LangChain4j and LangChain.

**Semantic chunking:**

Embed each sentence; group sentences with high cosine similarity into same chunk.  
Pro: semantically coherent chunks.  
Con: more expensive.

**Rule-based:**

Use document structure (H1/H2 headings, page breaks).

---

## Step 2: Embedding

Convert text to dense vector. Semantically similar texts → nearby vectors.

```
"Java virtual threads" → [0.23, -0.47, 0.12, ..., 0.89]  (1536 dims for text-embedding-3-small)

Cosine similarity:
  sim("virtual threads", "Project Loom") → 0.92  (very similar)
  sim("virtual threads", "coffee price") → 0.08  (unrelated)
```

### Embedding Model Options

| Model | Dimensions | Notes |
|---|---|---|
| text-embedding-3-small | 1536 | OpenAI, cheap, fast |
| text-embedding-3-large | 3072 | OpenAI, higher quality |
| all-MiniLM-L6-v2 | 384 | Open source, runs locally |
| mxbai-embed-large | 1024 | Strong open source |

---

## Step 3: Vector Storage

### How Vector DBs Work

```
Store: (chunk_text, embedding_vector, metadata)
Query: embed(user_question) → find top-K nearest vectors → return chunk_texts

Approximate Nearest Neighbor (ANN):
HNSW (Hierarchical Navigable Small World) graph:
  - O(log n) index, O(log n) query
  - Trade exact recall for speed (typically >95% recall)
```

### Vector DB Options

| DB | Notes |
|---|---|
| pgvector | PostgreSQL extension, simplest for existing Postgres users |
| Pinecone | Managed, easy scaling, no infra |
| Weaviate | OSS, hybrid search (vector + keyword) |
| Milvus | OSS, Kubernetes-native, very scalable |
| ChromaDB | Simple local dev |
| Redis with RediSearch | In-memory vector search |

---

## Step 4: Retrieval & Reranking

### Similarity Search (Dense Retrieval)

```
Embed query → cosine similarity search in vector DB → top-5 chunks
```

### Hybrid Search (Dense + Sparse)

Combine vector similarity with BM25 keyword matching. Better for exact terms.

```
score = alpha × vector_score + (1 - alpha) × bm25_score
alpha = 0.7 (lean vector) works for most use cases
```

### Reranking

After retrieving top-20 candidates, use a cross-encoder reranker to score each against the query.

```
Cross-encoder reranker: takes (query, chunk) → relevance score (0-1)
Slower than bi-encoder but more accurate. Apply to top-20, keep top-5.

Options: Cohere Rerank API, BGE-reranker, Jina Reranker
```

---

## Step 5: Prompt Construction

```
SYSTEM:
You are a helpful customer support agent. Answer ONLY based on the provided context.
If the answer is not in the context, say "I don't have that information."

CONTEXT:
[Chunk 1]: Refunds are processed within 5-7 business days...
[Chunk 2]: To initiate a refund, go to Order History...
[Chunk 3]: Digital downloads are non-refundable...

USER:
How long does a refund take?
```

---

## Advanced RAG Patterns

### HyDE (Hypothetical Document Embedding)

Generate a hypothetical answer, embed it, use THAT to search. Better for obscure queries.

```
Query: "What's the capital of the country that invented tea?"
→ GPT generates: "The capital of China is Beijing."
→ Embed "Beijing" to search for relevant docs
```

### Multi-Query Retrieval

Generate 3 paraphrases of the query, search for each, merge results.  
Improves recall for ambiguous queries.

### RAG Fusion

Retrieve with multiple queries → combine result lists using Reciprocal Rank Fusion (RRF).

---

## Spring AI RAG Architecture

```java
// DocumentLoader → Splitter → VectorStore (see code file)
// At query time:
QuestionAnswerAdvisor advisor = new QuestionAnswerAdvisor(vectorStore);
ChatResponse response = chatClient.prompt()
    .user(question)
    .advisors(advisor)          // injects retrieved docs
    .call()
    .chatResponse();
```

---

## Evaluation

```
Faithfulness:  Is the answer grounded in retrieved context?
Relevancy:     Is the answer relevant to the question?
Context recall: Did retrieval actually fetch needed information?

Tools: RAGAS framework, LangSmith, LlamaIndex evaluators
```

---

## Interview Tips

- RAG vs fine-tuning: RAG is faster to implement, cheaper, no retraining when data changes.
- Chunking is often the most impactful quality lever — mention different strategies.
- Hybrid search typically outperforms pure vector for enterprise search.
- Always mention hallucination mitigation: ground responses in retrieved docs, add source attribution.

---

## Complete Ingestion Pipeline — Java Implementation

```java
@Service
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public IngestionResult ingest(MultipartFile file, String tenantId, String knowledgeBaseId) {
        // 1. Load document
        Resource resource = file.getResource();
        List<Document> rawDocs = new PagePdfDocumentReader(resource,
            PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(
                    ExtractedTextFormatter.builder().withNumberOfTopPagesToSkipLayout(0).build())
                .build())
            .get();

        // 2. Enrich metadata BEFORE chunking so every chunk carries it
        rawDocs.forEach(doc -> {
            doc.getMetadata().put("tenant_id", tenantId);
            doc.getMetadata().put("knowledge_base_id", knowledgeBaseId);
            doc.getMetadata().put("source_file", file.getOriginalFilename());
            doc.getMetadata().put("ingested_at", Instant.now().toString());
        });

        // 3. Chunk
        TokenTextSplitter splitter = new TokenTextSplitter(512, 50, 10, 5000, true);
        List<Document> chunks = splitter.apply(rawDocs);

        // 4. Embed and store (Spring AI handles embedding internally)
        vectorStore.add(chunks);

        log.info("Ingested {} chunks for tenant={}", chunks.size(), tenantId);
        return new IngestionResult(chunks.size(), rawDocs.size());
    }
}
```

---

## Query Pipeline — Full Implementation

```java
@Service
public class RagQueryService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final QueryRewriter queryRewriter;

    public RagAnswer query(String userQuestion, String tenantId) {
        // Step 1: Rewrite the query for better retrieval
        String expandedQuery = queryRewriter.expand(userQuestion);

        // Step 2: Retrieve with tenant filter
        SearchRequest searchRequest = SearchRequest.query(expandedQuery)
            .withTopK(5)
            .withSimilarityThreshold(0.72)
            .withFilterExpression("tenant_id == '" + tenantId + "'");

        List<Document> context = vectorStore.similaritySearch(searchRequest);

        if (context.isEmpty()) {
            return RagAnswer.noContext(userQuestion);
        }

        // Step 3: Build prompt with context and citations
        String contextText = context.stream()
            .map(doc -> String.format("[Source: %s]\n%s",
                doc.getMetadata().get("source_file"), doc.getContent()))
            .collect(Collectors.joining("\n\n---\n\n"));

        // Step 4: Generate
        String answer = chatClient.prompt()
            .system("""
                You are a helpful assistant. Answer using ONLY the provided context.
                If the answer is not in the context, say "I don't have enough information."
                Always cite the source document in your answer.
                """)
            .user(String.format("Context:\n%s\n\nQuestion: %s", contextText, userQuestion))
            .call()
            .content();

        List<String> citations = context.stream()
            .map(doc -> (String) doc.getMetadata().get("source_file"))
            .distinct()
            .collect(Collectors.toList());

        return new RagAnswer(userQuestion, answer, citations, context.size());
    }
}
```

---

## Query Rewriting for Better Retrieval

```java
@Component
public class QueryRewriter {

    private final ChatModel chatModel;

    // Expand to multiple phrasings for multi-query retrieval
    public String expand(String originalQuery) {
        String prompt = """
            Generate 3 alternative phrasings of the following question
            that capture the same intent using different vocabulary.
            Return only the queries, one per line, no numbering.
            Original: %s
            """.formatted(originalQuery);

        String alternatives = chatModel.call(prompt);
        return originalQuery + "\n" + alternatives;
    }

    // Decontextualize a follow-up question using chat history
    public String decontextualize(String currentQuestion, List<String> recentHistory) {
        if (recentHistory.isEmpty()) return currentQuestion;

        String historyText = String.join("\n",
            recentHistory.subList(Math.max(0, recentHistory.size() - 4), recentHistory.size()));

        return chatModel.call("""
            Given:
            %s
            
            Rewrite as a standalone question with no pronouns:
            %s
            """.formatted(historyText, currentQuestion)).trim();
    }
}
```

---

## Parent-Child Chunking

Problem: small chunks are precise but lack context; large chunks rank poorly.

Solution: index small child chunks, but return their parent chunk in the prompt.

```java
@Service
public class ParentChildIngestionService {

    private final VectorStore vectorStore;   // small children (128 tokens)
    private final RedisTemplate<String, String> parentStore;  // full passages

    public void ingest(Document document) {
        List<Document> parents = new TokenTextSplitter(512, 50).apply(List.of(document));

        for (Document parent : parents) {
            String parentId = UUID.randomUUID().toString();
            parent.getMetadata().put("parent_id", parentId);
            parentStore.opsForValue().set("parent:" + parentId, parent.getContent());

            List<Document> children = new TokenTextSplitter(128, 20).apply(List.of(parent));
            children.forEach(c -> c.getMetadata().put("parent_id", parentId));
            vectorStore.add(children);
        }
    }

    public List<Document> retrieveWithParents(String query) {
        return vectorStore.similaritySearch(SearchRequest.query(query).withTopK(10))
            .stream()
            .map(c -> (String) c.getMetadata().get("parent_id"))
            .distinct()
            .map(id -> parentStore.opsForValue().get("parent:" + id))
            .filter(Objects::nonNull)
            .map(Document::new)
            .collect(Collectors.toList());
    }
}
```

---

## Multi-Hop Reasoning

Some questions require retrieving information, then using it to retrieve more.

```
Q: "What does the SLA say about penalty if the API availability drops below 99.5% AND the incident lasts more than 4 hours?"

Hop 1: search "API availability SLA penalty" → get penalty clause
Hop 2: search "incident duration threshold SLA" → get duration clause
Synthesis: combine both to produce final answer
```

```java
public String multiHopQuery(String question) {
    List<String> collectedContext = new ArrayList<>();
    String currentQuery = question;

    for (int hop = 0; hop < 3; hop++) {
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(currentQuery).withTopK(3));
        String context = docs.stream().map(Document::getContent).collect(Collectors.joining("\n\n"));
        collectedContext.add(context);

        String decision = chatModel.call("""
            Original question: %s
            Context so far: %s
            Current chunk: %s
            
            Do you need more information? If yes reply: SEARCH: <query>
            If no reply: DONE
            """.formatted(question, String.join("\n---\n", collectedContext), context)).trim();

        if (!decision.startsWith("SEARCH: ")) break;
        currentQuery = decision.substring("SEARCH: ".length());
    }

    return chatModel.call("Answer using only this context:\nContext: %s\nQuestion: %s"
        .formatted(String.join("\n---\n", collectedContext), question));
}
```

---

## RAG Failure Modes and Debugging

| Symptom | Likely cause | Fix |
|---|---|---|
| "I don't know" for obvious questions | Retrieval fails — threshold too high | Lower threshold to 0.70, try hybrid search |
| Answer contains fabricated info | Wrong chunks retrieved, model ignores instructions | Strengthen system prompt: "ONLY from context below" |
| Retrieved chunks are off-topic | Chunks too large — mixed content | Reduce chunk size; split on semantic boundaries |
| Answer contradicts source | Middle-of-context dilution | Move key facts to top of prompt |
| Slow response | Sequential embed + retrieve + LLM | Cache query embeddings; parallelize reranking |
| High hallucination rate | Model temperature too high | Lower temperature to 0 for factual tasks |

Debugging checklist:
1. Log retrieved chunks with similarity scores. Are the right ones coming back?
2. Log the exact prompt. Is context present and coherent?
3. Test retrieval independently before end-to-end.
4. Run RAGAS faithfulness score — below 0.7 means model is hallucinating from context.
5. Check embedding model version match between ingestion and query.
