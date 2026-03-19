package ai.rag;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG Pipeline Implementation in Pure Java
 * Demonstrates document loading, chunking, embedding generation,
 * cosine similarity search, and retrieval — without Spring AI dependency.
 * Shows the concepts that Spring AI automates.
 */
public class RagServiceExample {

    // ─────────────────────────────────────────────
    // 1. DOCUMENT & CHUNK MODELS
    // ─────────────────────────────────────────────

    record Document(String id, String content, Map<String, String> metadata) {}

    record Chunk(String id, String parentDocId, String content,
                 int chunkIndex, int startChar, int endChar,
                 Map<String, String> metadata) {}

    record EmbeddedChunk(Chunk chunk, float[] embedding) {}

    // ─────────────────────────────────────────────
    // 2. TEXT CHUNKER
    // Recursive character splitting: try \n\n → \n → . → space
    // ─────────────────────────────────────────────

    static class RecursiveTextSplitter {
        private final int chunkSize;     // max tokens per chunk (approx. chars/4)
        private final int chunkOverlap;  // overlap between adjacent chunks

        RecursiveTextSplitter(int chunkSize, int chunkOverlap) {
            this.chunkSize = chunkSize;
            this.chunkOverlap = chunkOverlap;
        }

        public List<Chunk> split(Document doc) {
            List<String> rawChunks = splitText(doc.content());
            List<Chunk> chunks = new ArrayList<>();
            int offset = 0;

            for (int i = 0; i < rawChunks.size(); i++) {
                String text = rawChunks.get(i);
                int start = doc.content().indexOf(text, offset);
                if (start == -1) start = offset;

                chunks.add(new Chunk(
                    doc.id() + "-chunk-" + i,
                    doc.id(),
                    text,
                    i,
                    start,
                    start + text.length(),
                    new HashMap<>(doc.metadata())
                ));
                // Move offset past this chunk minus overlap
                offset = Math.max(start, start + text.length() - chunkOverlap);
            }
            return chunks;
        }

        private List<String> splitText(String text) {
            // Try splitting by paragraph first
            if (text.length() <= chunkSize) return List.of(text);

            String[] separators = {"\n\n", "\n", ". ", " "};
            for (String sep : separators) {
                String[] parts = text.split(sep, -1);
                if (parts.length > 1) {
                    return mergeIntoChunks(parts, sep);
                }
            }
            // Forceful split by character
            return splitByChar(text);
        }

        private List<String> mergeIntoChunks(String[] parts, String sep) {
            List<String> result = new ArrayList<>();
            StringBuilder current = new StringBuilder();

            for (String part : parts) {
                if (!current.isEmpty() && current.length() + part.length() + sep.length() > chunkSize) {
                    result.add(current.toString().strip());
                    // Keep overlap
                    String str = current.toString();
                    current = new StringBuilder(str.substring(Math.max(0, str.length() - chunkOverlap)));
                }
                if (!current.isEmpty()) current.append(sep);
                current.append(part);
            }
            if (!current.isEmpty()) result.add(current.toString().strip());
            return result.stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
        }

        private List<String> splitByChar(String text) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < text.length(); i += chunkSize - chunkOverlap) {
                result.add(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
            return result;
        }
    }

    // ─────────────────────────────────────────────
    // 3. EMBEDDING MODEL (STUB — in production: call OpenAI/Ollama API)
    // ─────────────────────────────────────────────

    interface EmbeddingModel {
        float[] embed(String text);
        List<float[]> embedBatch(List<String> texts);
    }

    // Deterministic stub — generates reproducible vectors based on text hash
    static class StubEmbeddingModel implements EmbeddingModel {
        private static final int DIMENSIONS = 8;  // reduced for demo (real: 1536)

        @Override
        public float[] embed(String text) {
            float[] vector = new float[DIMENSIONS];
            int hash = text.hashCode();
            for (int i = 0; i < DIMENSIONS; i++) {
                // Spread hash bits across dimensions + add text statistics
                vector[i] = (float)(Math.sin(hash * (i + 1) * 0.001) +
                                    text.length() * 0.001 * (i + 1));
            }
            return normalize(vector);
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            return texts.stream().map(this::embed).collect(Collectors.toList());
        }

        private float[] normalize(float[] v) {
            float norm = 0f;
            for (float x : v) norm += x * x;
            norm = (float) Math.sqrt(norm);
            if (norm == 0) return v;
            float[] result = new float[v.length];
            for (int i = 0; i < v.length; i++) result[i] = v[i] / norm;
            return result;
        }
    }

    // ─────────────────────────────────────────────
    // 4. VECTOR STORE (In-Memory — replace with pgvector/Pinecone in production)
    // ─────────────────────────────────────────────

    static class InMemoryVectorStore {
        private final List<EmbeddedChunk> store = new ArrayList<>();
        private final EmbeddingModel embeddingModel;

        InMemoryVectorStore(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public void add(List<Chunk> chunks) {
            List<String> texts = chunks.stream().map(Chunk::content).toList();
            List<float[]> embeddings = embeddingModel.embedBatch(texts);

            for (int i = 0; i < chunks.size(); i++) {
                store.add(new EmbeddedChunk(chunks.get(i), embeddings.get(i)));
            }
            System.out.printf("[VectorStore] Added %d chunks (total: %d)%n", chunks.size(), store.size());
        }

        public List<ScoredChunk> search(String query, int topK, double minSimilarity) {
            float[] queryEmbedding = embeddingModel.embed(query);

            return store.stream()
                .map(ec -> {
                    double sim = cosineSimilarity(queryEmbedding, ec.embedding());
                    return new ScoredChunk(ec.chunk(), sim);
                })
                .filter(sc -> sc.score() >= minSimilarity)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
        }

        private double cosineSimilarity(float[] a, float[] b) {
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += a[i] * a[i];
                normB += b[i] * b[i];
            }
            double denom = Math.sqrt(normA) * Math.sqrt(normB);
            return denom == 0 ? 0 : dot / denom;
        }
    }

    record ScoredChunk(Chunk chunk, double score) {}

    // ─────────────────────────────────────────────
    // 5. PROMPT BUILDER
    // ─────────────────────────────────────────────

    static class RagPromptBuilder {
        private static final String SYSTEM_PROMPT = """
            You are a helpful customer support agent.
            Answer ONLY based on the provided context.
            If the answer is not in the context, respond:
            "I don't have that information in my knowledge base."
            Always cite the source document name.
            """;

        public String buildPrompt(String question, List<ScoredChunk> retrievedChunks) {
            StringBuilder sb = new StringBuilder();
            sb.append("CONTEXT:\n");
            for (int i = 0; i < retrievedChunks.size(); i++) {
                ScoredChunk sc = retrievedChunks.get(i);
                String source = sc.chunk().metadata().getOrDefault("source", "unknown");
                sb.append("[Doc %d (source=%s, score=%.3f)]:%n%s%n%n"
                    .formatted(i + 1, source, sc.score(), sc.chunk().content()));
            }
            sb.append("QUESTION:\n").append(question);
            return sb.toString();
        }

        public String getSystemPrompt() { return SYSTEM_PROMPT; }
    }

    // ─────────────────────────────────────────────
    // 6. FULL RAG PIPELINE
    // ─────────────────────────────────────────────

    static class RagPipeline {
        private final RecursiveTextSplitter splitter;
        private final InMemoryVectorStore vectorStore;
        private final RagPromptBuilder promptBuilder;

        RagPipeline() {
            this.splitter = new RecursiveTextSplitter(500, 50);
            this.vectorStore = new InMemoryVectorStore(new StubEmbeddingModel());
            this.promptBuilder = new RagPromptBuilder();
        }

        // INGESTION: load → chunk → embed → store
        public void ingest(Document doc) {
            List<Chunk> chunks = splitter.split(doc);
            vectorStore.add(chunks);
        }

        // RETRIEVAL: embed query → search → build prompt
        public String buildRagPrompt(String question) {
            List<ScoredChunk> results = vectorStore.search(question, 3, 0.0);
            return promptBuilder.buildPrompt(question, results);
        }

        // In production: pass the built prompt to the LLM ChatClient
        public String answer(String question) {
            String ragPrompt = buildRagPrompt(question);
            System.out.println("[RAG] Built prompt:\n" + ragPrompt);
            return "[In production: send this to ChatClient and return LLM response]";
        }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("=== RAG Pipeline Demo ===\n");

        RagPipeline rag = new RagPipeline();

        // Step 1: Ingest knowledge base documents
        System.out.println("--- Ingesting Documents ---");
        rag.ingest(new Document("doc1",
            """
            Our refund policy allows customers to request refunds within 30 days of purchase.
            Refunds are processed within 5-7 business days.
            To initiate a refund, go to Order History and click 'Request Refund'.
            Digital downloads are non-refundable once downloaded.
            """,
            Map.of("source", "refund-policy-v2", "category", "refunds")));

        rag.ingest(new Document("doc2",
            """
            Shipping options: Standard (5-7 days, free), Express (2-3 days, $12.99).
            International shipping is available to 45 countries.
            Lost packages must be reported within 14 days of expected delivery.
            """,
            Map.of("source", "shipping-policy", "category", "shipping")));

        rag.ingest(new Document("doc3",
            """
            Java virtual threads (Project Loom) in Java 21 allow creating millions of
            lightweight threads without OS thread overhead. Use Thread.ofVirtual().start().
            Virtual threads are ideal for I/O-bound tasks in web servers.
            """,
            Map.of("source", "java21-guide", "category", "technical")));

        // Step 2: Query the RAG system
        System.out.println("\n--- RAG Query 1: Refund timing ---");
        rag.answer("How long does a refund take?");

        System.out.println("\n--- RAG Query 2: Shipping ---");
        rag.answer("What shipping options are available?");

        System.out.println("\n--- RAG Query 3: Technical question ---");
        rag.answer("How do virtual threads work in Java?");

        // Step 3: Show chunking for a larger document
        System.out.println("\n--- Chunking Demo ---");
        RecursiveTextSplitter splitter = new RecursiveTextSplitter(200, 20);
        Document largeDoc = new Document("large",
            "Section 1: Introduction\n\n" +
            "This document describes our API guidelines.\n\n" +
            "Section 2: Authentication\n\n" +
            "All API requests must include a Bearer token in the Authorization header.\n\n" +
            "Section 3: Rate Limiting\n\n" +
            "Requests are limited to 1000 per minute per API key.",
            Map.of("source", "api-guide")
        );
        List<Chunk> chunks = splitter.split(largeDoc);
        System.out.printf("Chunked into %d chunks:%n", chunks.size());
        chunks.forEach(c -> System.out.printf("  [%d] (chars %d-%d): %s...%n",
            c.chunkIndex(), c.startChar(), c.endChar(),
            c.content().substring(0, Math.min(60, c.content().length()))));
    }
}
