package ai.embeddings;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Embedding Pipeline Examples
 * Covers: cosine similarity, dot product, embedding generation patterns,
 * semantic search, text classification via embeddings, deduplication.
 */
public class EmbeddingPipelineExample {

    // ─────────────────────────────────────────────
    // 1. VECTOR MATH UTILITIES
    // ─────────────────────────────────────────────

    static class VectorMath {

        /** Cosine similarity between two vectors. Returns -1 to 1. */
        public static double cosineSimilarity(float[] a, float[] b) {
            if (a.length != b.length) throw new IllegalArgumentException("Dimension mismatch");
            double dot = 0, normA = 0, normB = 0;
            for (int i = 0; i < a.length; i++) {
                dot += a[i] * b[i];
                normA += (double) a[i] * a[i];
                normB += (double) b[i] * b[i];
            }
            double denom = Math.sqrt(normA) * Math.sqrt(normB);
            return denom == 0 ? 0.0 : dot / denom;
        }

        /** Euclidean (L2) distance between two vectors. Lower = more similar. */
        public static double euclideanDistance(float[] a, float[] b) {
            double sum = 0;
            for (int i = 0; i < a.length; i++) {
                double diff = a[i] - b[i];
                sum += diff * diff;
            }
            return Math.sqrt(sum);
        }

        /** Dot product. Equivalent to cosine similarity when vectors are L2-normalized. */
        public static double dotProduct(float[] a, float[] b) {
            double dot = 0;
            for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
            return dot;
        }

        /** Normalize a vector to unit length (L2 norm = 1). */
        public static float[] normalize(float[] v) {
            float norm = 0f;
            for (float x : v) norm += x * x;
            norm = (float) Math.sqrt(norm);
            if (norm == 0) return v.clone();
            float[] result = new float[v.length];
            for (int i = 0; i < v.length; i++) result[i] = v[i] / norm;
            return result;
        }

        /** Component-wise average of multiple vectors. Used for creating "topic centroids". */
        public static float[] average(List<float[]> vectors) {
            if (vectors.isEmpty()) throw new IllegalArgumentException("Empty list");
            int dims = vectors.get(0).length;
            float[] avg = new float[dims];
            for (float[] v : vectors) {
                for (int i = 0; i < dims; i++) avg[i] += v[i];
            }
            for (int i = 0; i < dims; i++) avg[i] /= vectors.size();
            return normalize(avg);
        }

        /** Compute all pairwise cosine similarities for a set of vectors. O(n²) */
        public static double[][] pairwiseSimilarities(List<float[]> vectors) {
            int n = vectors.size();
            double[][] sim = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    double s = cosineSimilarity(vectors.get(i), vectors.get(j));
                    sim[i][j] = sim[j][i] = s;
                }
            }
            return sim;
        }
    }

    // ─────────────────────────────────────────────
    // 2. DETERMINISTIC EMBEDDING STUB
    // Generates realistic-looking vectors. Replace with OpenAI API in production.
    // ─────────────────────────────────────────────

    static class EmbeddingService {
        private static final int DIMS = 16;  // reduced from 1536 for demo

        /**
         * Generates an embedding for a text string.
         * Production: POST https://api.openai.com/v1/embeddings
         *             with model: "text-embedding-3-small"
         */
        public float[] embed(String text) {
            float[] v = new float[DIMS];
            // Different texts → different vectors based on content
            String lower = text.toLowerCase();
            for (int i = 0; i < DIMS; i++) {
                v[i] = (float)(Math.sin(text.hashCode() * (i + 1) * 0.01)
                             + Math.cos(lower.length() * (i + 1) * 0.005));
            }
            // Add semantic features: word frequency signals
            if (lower.contains("java"))    v[0] += 0.5f;
            if (lower.contains("thread"))  v[1] += 0.5f;
            if (lower.contains("refund"))  v[2] += 0.5f;
            if (lower.contains("ship"))    v[3] += 0.5f;
            if (lower.contains("cache"))   v[4] += 0.5f;
            if (lower.contains("micro"))   v[5] += 0.5f;

            return VectorMath.normalize(v);
        }

        public List<float[]> embedBatch(List<String> texts) {
            // In production: batch API call to avoid N round trips
            return texts.stream().map(this::embed).collect(Collectors.toList());
        }
    }

    // ─────────────────────────────────────────────
    // 3. SEMANTIC SEARCH ENGINE
    // ─────────────────────────────────────────────

    record TextItem(String id, String text, Map<String, String> metadata) {}
    record SearchResult(TextItem item, double score) {}

    static class SemanticSearchEngine {
        private final EmbeddingService embedder;
        private final List<TextItem> items = new ArrayList<>();
        private final List<float[]> embeddings = new ArrayList<>();

        SemanticSearchEngine(EmbeddingService embedder) {
            this.embedder = embedder;
        }

        public void index(List<TextItem> newItems) {
            for (TextItem item : newItems) {
                items.add(item);
                embeddings.add(embedder.embed(item.text()));
            }
            System.out.printf("[Index] Added %d items (total: %d)%n", newItems.size(), items.size());
        }

        public List<SearchResult> search(String query, int topK) {
            float[] queryVec = embedder.embed(query);

            List<SearchResult> results = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                double sim = VectorMath.cosineSimilarity(queryVec, embeddings.get(i));
                results.add(new SearchResult(items.get(i), sim));
            }

            return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .collect(Collectors.toList());
        }
    }

    // ─────────────────────────────────────────────
    // 4. SEMANTIC DEDUPLICATION
    // Find near-duplicate texts (similarity > threshold).
    // ─────────────────────────────────────────────

    static class SemanticDeduplicator {
        private final EmbeddingService embedder;
        private final double threshold;

        SemanticDeduplicator(EmbeddingService embedder, double threshold) {
            this.embedder = embedder;
            this.threshold = threshold;
        }

        record DuplicatePair(int i, int j, double similarity, String textA, String textB) {}

        public List<DuplicatePair> findDuplicates(List<String> texts) {
            List<float[]> vecs = embedder.embedBatch(texts);
            double[][] sim = VectorMath.pairwiseSimilarities(vecs);

            List<DuplicatePair> duplicates = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                for (int j = i + 1; j < texts.size(); j++) {
                    if (sim[i][j] >= threshold) {
                        duplicates.add(new DuplicatePair(i, j, sim[i][j],
                            texts.get(i), texts.get(j)));
                    }
                }
            }
            return duplicates;
        }

        /** Returns deduplicated list (keeps first of each duplicate cluster). */
        public List<String> deduplicate(List<String> texts) {
            List<float[]> vecs = embedder.embedBatch(texts);
            boolean[] removed = new boolean[texts.size()];

            for (int i = 0; i < texts.size(); i++) {
                if (removed[i]) continue;
                for (int j = i + 1; j < texts.size(); j++) {
                    if (!removed[j] && VectorMath.cosineSimilarity(vecs.get(i), vecs.get(j)) >= threshold) {
                        removed[j] = true;
                    }
                }
            }

            List<String> result = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                if (!removed[i]) result.add(texts.get(i));
            }
            return result;
        }
    }

    // ─────────────────────────────────────────────
    // 5. EMBEDDING-BASED CLASSIFICATION
    // Classify text by similarity to class centroids.
    // ─────────────────────────────────────────────

    static class ZeroShotClassifier {
        private final EmbeddingService embedder;
        private final Map<String, float[]> classCentroids;

        ZeroShotClassifier(EmbeddingService embedder, Map<String, List<String>> classSamples) {
            this.embedder = embedder;
            this.classCentroids = new HashMap<>();

            // Build centroid for each class by averaging sample embeddings
            classSamples.forEach((className, samples) -> {
                List<float[]> vecs = embedder.embedBatch(samples);
                classCentroids.put(className, VectorMath.average(vecs));
            });
        }

        record ClassificationResult(String label, double confidence, Map<String, Double> scores) {}

        public ClassificationResult classify(String text) {
            float[] vec = embedder.embed(text);

            Map<String, Double> scores = new LinkedHashMap<>();
            classCentroids.forEach((label, centroid) ->
                scores.put(label, VectorMath.cosineSimilarity(vec, centroid)));

            String bestLabel = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");

            return new ClassificationResult(bestLabel, scores.get(bestLabel), scores);
        }
    }

    // ─────────────────────────────────────────────
    // DEMO
    // ─────────────────────────────────────────────

    public static void main(String[] args) {
        EmbeddingService embedder = new EmbeddingService();

        System.out.println("=== 1. Cosine Similarity Demo ===");
        String[] texts = {
            "Java virtual threads Project Loom",
            "Lightweight threading in Java",
            "Refund policy customer support",
            "Coffee brewing methods"
        };
        float[][] vecs = Arrays.stream(texts).map(embedder::embed).toArray(float[][]::new);

        System.out.println("\nSimilarity matrix:");
        System.out.printf("%-42s", "");
        for (String t : texts) System.out.printf("%-12s", t.substring(0, Math.min(11, t.length())));
        System.out.println();
        for (int i = 0; i < texts.length; i++) {
            System.out.printf("%-42s", texts[i].substring(0, Math.min(40, texts[i].length())));
            for (int j = 0; j < texts.length; j++) {
                System.out.printf("%-12.3f", VectorMath.cosineSimilarity(vecs[i], vecs[j]));
            }
            System.out.println();
        }

        System.out.println("\n=== 2. Semantic Search ===");
        SemanticSearchEngine engine = new SemanticSearchEngine(embedder);
        engine.index(List.of(
            new TextItem("d1", "Java virtual threads enable high-concurrency with lightweight threads", Map.of("topic","java")),
            new TextItem("d2", "Redis is an in-memory cache and message broker", Map.of("topic","infra")),
            new TextItem("d3", "Microservice circuit breaker prevents cascade failures", Map.of("topic","patterns")),
            new TextItem("d4", "Project Loom introduces green threads to JVM", Map.of("topic","java")),
            new TextItem("d5", "Refunds are processed in 5-7 business days", Map.of("topic","support")),
            new TextItem("d6", "Cache stampede occurs when popular cached items expire simultaneously", Map.of("topic","infra"))
        ));

        String[] queries = {"java concurrency lightweight", "cache performance issue", "customer refund time"};
        for (String q : queries) {
            System.out.printf("%nQuery: \"%s\"%n", q);
            engine.search(q, 3).forEach(r ->
                System.out.printf("  %.3f | %s%n", r.score(), r.item().text()));
        }

        System.out.println("\n=== 3. Semantic Deduplication ===");
        SemanticDeduplicator dedup = new SemanticDeduplicator(embedder, 0.97);
        List<String> testTexts = List.of(
            "Java virtual threads are lightweight",
            "Java virtual threads are very lightweight",  // near-duplicate
            "Redis is a fast in-memory database",
            "Redis provides fast in-memory data storage",  // near-duplicate
            "Kafka is a distributed event streaming platform"
        );
        List<String> unique = dedup.deduplicate(testTexts);
        System.out.println("Original: " + testTexts.size() + " texts");
        System.out.println("After dedup: " + unique.size() + " texts");
        unique.forEach(t -> System.out.println("  - " + t));

        System.out.println("\n=== 4. Zero-Shot Classification ===");
        ZeroShotClassifier classifier = new ZeroShotClassifier(embedder, Map.of(
            "technical",  List.of("Java threading", "Redis caching", "microservices architecture"),
            "support",    List.of("refund policy", "shipping time", "account reset"),
            "business",   List.of("revenue growth", "market strategy", "customer acquisition")
        ));

        String[] samples = {
            "How do virtual threads improve throughput?",
            "I need to cancel my order and get a refund",
            "What is our go-to-market strategy for Q4?"
        };
        for (String s : samples) {
            var result = classifier.classify(s);
            System.out.printf("Text: %-50s → %s (%.3f)%n", s, result.label(), result.confidence());
        }
    }
}
