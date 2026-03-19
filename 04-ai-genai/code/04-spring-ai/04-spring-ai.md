# Spring AI — Building LLM Applications in Java

## What is Spring AI?

Spring AI is a Spring Framework project providing a portable API across LLM providers (OpenAI, Anthropic, Ollama, Azure OpenAI, etc.) and vector databases (pgvector, Pinecone, Weaviate, etc.).

---

## Core Abstractions

```
ChatClient           → fluent API for chat completions
ChatModel            → lower-level model interaction
EmbeddingModel       → generate embeddings
VectorStore          → store and query document embeddings
DocumentReader       → load documents from various sources
DocumentTransformer  → transform/split documents
```

---

## Setup

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
# application.yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o
          temperature: 0.3
      embedding:
        options:
          model: text-embedding-3-small
```

---

## ChatClient — Fluent API

```java
@Service
public class AiAssistantService {
    private final ChatClient chatClient;

    public AiAssistantService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a Java expert. Be concise and precise.")
            .build();
    }

    // Simple call
    public String ask(String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }

    // Structured output (JSON → Java record)
    public CodeReviewResult reviewCode(String code) {
        return chatClient.prompt()
            .user(u -> u.text("Review this Java code:\n{code}").param("code", code))
            .call()
            .entity(CodeReviewResult.class);
    }

    // Streaming (Server-Sent Events)
    public Flux<String> streamAnswer(String question) {
        return chatClient.prompt()
            .user(question)
            .stream()
            .content();
    }

    record CodeReviewResult(
        List<String> issues,
        List<String> suggestions,
        String securityConcerns,
        int qualityScore  // 1-10
    ) {}
}
```

---

## VectorStore & RAG Integration

```java
@Configuration
public class RagConfig {

    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        // pgvector store — production ready
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1536)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .build();
    }

    @Bean
    public ChatClient ragChatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
            .defaultAdvisors(
                // Runs retrieval before sending to LLM
                new QuestionAnswerAdvisor(vectorStore,
                    SearchRequest.defaults().withTopK(5).withSimilarityThreshold(0.7))
            )
            .build();
    }
}

@Service
public class DocumentIngestionService {
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.splitter = new TokenTextSplitter(512, 50, 10, 5000, true);
    }

    public void ingestPdf(Resource pdfResource) {
        // 1. Load
        List<Document> docs = new PagePdfDocumentReader(pdfResource,
            PdfDocumentReaderConfig.builder().withPageTopMargin(0).build()
        ).get();

        // 2. Split
        List<Document> chunks = splitter.apply(docs);

        // 3. Embed & Store (embedding happens automatically in vectorStore.add)
        vectorStore.add(chunks);
        System.out.printf("Ingested %d chunks from %s%n", chunks.size(), pdfResource.getFilename());
    }

    // Text ingestion
    public void ingestText(String text, Map<String, Object> metadata) {
        Document doc = new Document(text, metadata);
        List<Document> chunks = splitter.apply(List.of(doc));
        vectorStore.add(chunks);
    }
}
```

---

## Function Calling (Tool Use)

LLM can call your Java functions to retrieve real-time data.

```java
@Configuration
public class WeatherFunctionConfig {

    @Bean
    @Description("Get current weather for a given city")
    public Function<WeatherRequest, WeatherResponse> weatherFunction(WeatherService weather) {
        return request -> weather.getWeather(request.city(), request.unit());
    }

    record WeatherRequest(String city, String unit) {}
    record WeatherResponse(double temperature, String condition, String unit) {}
}

// Usage:
String result = chatClient.prompt()
    .user("What's the weather like in Tokyo?")
    .functions("weatherFunction")   // name of @Bean above
    .call()
    .content();
// LLM → calls weatherFunction("Tokyo", "celsius") → inserts result → generates final answer
```

---

## Prompt Templates

```java
PromptTemplate template = new PromptTemplate("""
    Analyze the following {language} code and identify:
    1. Potential bugs
    2. Performance issues
    3. Security vulnerabilities
    
    Code:
    {code}
    
    Respond in JSON format.
    """);

Prompt prompt = template.create(Map.of(
    "language", "SQL",
    "code", "SELECT * FROM users WHERE id=" + userId  // SQL injection!
));
```

---

## Chat Memory (Conversation History)

```java
@Bean
public ChatClient chatClientWithMemory(ChatClient.Builder builder,
                                        VectorStore vectorStore) {
    return builder
        .defaultAdvisors(
            new MessageChatMemoryAdvisor(new InMemoryChatMemory()),  // keep last N turns
            new QuestionAnswerAdvisor(vectorStore)
        )
        .build();
}

// Each call chains through conversation history automatically:
String r1 = chatClient.prompt().user("My name is Alice").advisors(
    a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)).call().content();
String r2 = chatClient.prompt().user("What's my name?").advisors(
    a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)).call().content();
// r2 → "Your name is Alice."
```

---

## Observability & Cost Tracking

```java
// Spring AI auto-instruments with Micrometer observation:
// gen_ai.client.operation.duration — histogram of LLM call latency
// gen_ai.usage.input_tokens — token usage counters
// gen_ai.usage.output_tokens

// Custom cost estimation:
@EventListener
void onAiUsage(AiUsageEvent event) {
    long inputTokens = event.getUsage().getPromptTokens();
    long outputTokens = event.getUsage().getGenerationTokens();
    double costUsd = (inputTokens * 2.50 + outputTokens * 10.00) / 1_000_000;
    costCounter.increment(costUsd);
}
```

---

## Interview Tips

- Spring AI provides vendor-agnostic API — swap OpenAI for Anthropic or Ollama with one config change.
- VectorStore + QuestionAnswerAdvisor = RAG in ~10 lines of config.
- Function calling allows LLMs to fetch live data (prices, weather, DB queries).
- Always mention rate limiting and retry configuration for production LLM clients.

---

## Multi-Provider Setup with Fallback

```java
@Configuration
public class MultiModelConfig {

    @Bean
    @Qualifier("primary")
    public ChatModel primaryChatModel(OpenAiApi openAiApi) {
        return new OpenAiChatModel(openAiApi,
            OpenAiChatOptions.builder().withModel("gpt-4o").withTemperature(0.0).build());
    }

    @Bean
    @Qualifier("fallback")
    public ChatModel fallbackChatModel(AnthropicApi anthropicApi) {
        return new AnthropicChatModel(anthropicApi,
            AnthropicChatOptions.builder().withModel("claude-3-5-sonnet-20241022").build());
    }

    @Bean
    @Qualifier("local")
    public ChatModel localChatModel(OllamaApi ollamaApi) {
        return new OllamaChatModel(ollamaApi,
            OllamaOptions.builder().withModel("llama3.1:8b").build());
    }
}

@Service
public class ResilientChatService {

    @Qualifier("primary")  private final ChatModel primary;
    @Qualifier("fallback") private final ChatModel fallback;
    @Qualifier("local")    private final ChatModel local;

    private final CircuitBreaker openAiBreaker;
    private final CircuitBreaker anthropicBreaker;

    public String call(String prompt) {
        try {
            return openAiBreaker.executeCallable(() -> primary.call(prompt));
        } catch (CallNotPermittedException | OpenAiApiException e) {
            log.warn("OpenAI unavailable, trying Anthropic: {}", e.getMessage());
            try {
                return anthropicBreaker.executeCallable(() -> fallback.call(prompt));
            } catch (Exception e2) {
                log.warn("Anthropic unavailable, falling back to local: {}", e2.getMessage());
                return local.call(prompt);
            }
        }
    }
}
```

---

## Complete RAG Service with Access Control

```java
@Service
@Slf4j
public class SecureRagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final AuditService auditService;

    public RagResponse query(RagRequest request) {
        String tenantId = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        // Access control: tenant isolation via metadata filter
        String filterExpression = String.format(
            "tenant_id == '%s' AND knowledge_base_id == '%s'",
            sanitize(tenantId),
            sanitize(request.knowledgeBaseId())
        );

        List<Document> docs;
        try {
            docs = vectorStore.similaritySearch(
                SearchRequest.query(request.question())
                    .withTopK(5)
                    .withSimilarityThreshold(0.70)
                    .withFilterExpression(filterExpression));
        } catch (Exception e) {
            log.error("Vector store query failed for tenant={}", tenantId, e);
            auditService.recordFailure(tenantId, request.question(), "retrieval_error");
            throw new RagServiceException("Retrieval failed", e);
        }

        if (docs.isEmpty()) {
            return RagResponse.noContext(request.question());
        }

        // Build context string
        String context = docs.stream()
            .map(d -> "Source: " + d.getMetadata().get("source_file") + "\n" + d.getContent())
            .collect(Collectors.joining("\n\n---\n\n"));

        // Generate with circuit breaker around LLM call
        String answer;
        try {
            answer = chatClient.prompt()
                .system("""
                    You are a helpful assistant. Answer ONLY from the provided context.
                    If the context does not contain the answer, say you don't know.
                    Do not reveal internal document structure or tenant information.
                    """)
                .user("Context:\n" + context + "\n\nQuestion: " + request.question())
                .call()
                .content();
        } catch (Exception e) {
            log.error("LLM call failed for tenant={}", tenantId, e);
            auditService.recordFailure(tenantId, request.question(), "llm_error");
            throw new RagServiceException("LLM generation failed", e);
        }

        List<String> citations = docs.stream()
            .map(d -> (String) d.getMetadata().get("source_file"))
            .distinct()
            .toList();

        auditService.recordQuery(tenantId, request.question(), citations);
        return new RagResponse(answer, citations, docs.size());
    }

    private String sanitize(String value) {
        // Prevent filter expression injection
        return value.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }
}
```

---

## Streaming SSE Controller

```java
@RestController
@RequestMapping("/api/v1/chat")
public class StreamingChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    // Streaming RAG endpoint
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamRag(
            @RequestParam String question,
            @RequestParam String knowledgeBaseId,
            Authentication auth) {

        String tenantId = auth.getName();

        // Perform retrieval synchronously (can't stream retrieval)
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(question)
                .withTopK(4)
                .withFilterExpression("tenant_id == '" + tenantId + "'"));

        String context = docs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n---\n"));

        if (context.isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder()
                .event("message")
                .data("I don't have enough information to answer that question.")
                .build());
        }

        return chatClient.prompt()
            .system("Answer from context only. Context:\n" + context)
            .user(question)
            .stream()
            .content()
            .map(token -> ServerSentEvent.<String>builder()
                .event("token")
                .data(token)
                .build())
            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build()))
            .doOnError(e -> log.error("Stream error for tenant={}", tenantId, e))
            .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                .event("error")
                .data("Error generating response.")
                .build()));
    }
}
```

---

## Integration Testing with Ollama (Local)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RagServiceIntegrationTest {

    @Autowired private SecureRagService ragService;
    @Autowired private DocumentIngestionService ingestionService;
    @Autowired private VectorStore vectorStore;

    // application-test.yml uses Ollama (no API key needed)
    // spring.ai.ollama.chat.options.model=llama3.1:8b
    // spring.ai.ollama.embedding.options.model=nomic-embed-text

    @BeforeEach
    void ingestTestDocuments() throws Exception {
        MockMultipartFile testDoc = new MockMultipartFile(
            "file", "test-policy.txt", "text/plain",
            "The refund policy allows returns within 30 days for a full refund.".getBytes()
        );
        ingestionService.ingest(testDoc, "test-tenant", "kb-001");
    }

    @AfterEach
    void cleanup() {
        // Delete all test tenant documents
        vectorStore.delete(List.of("test-tenant"));
    }

    @Test
    void shouldReturnGroundedAnswerFromContext() {
        var request = new RagRequest("What is the refund period?", "kb-001");

        // Mock authentication
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("test-tenant", null, List.of()));

        RagResponse response = ragService.query(request);

        assertThat(response.answer()).containsIgnoringCase("30 days");
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.chunksRetrieved()).isGreaterThan(0);
    }

    @Test
    void shouldReturnNoContextWhenOutOfScope() {
        var request = new RagRequest("What is the weather in Tokyo?", "kb-001");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("test-tenant", null, List.of()));

        RagResponse response = ragService.query(request);

        // Should return a "don't know" response, not a hallucinated answer
        assertThat(response.answer().toLowerCase())
            .containsAnyOf("don't know", "not enough information", "cannot find");
    }
}
```

---

## Asynchronous Ingestion with Kafka

For large document sets, ingestion should be async to avoid blocking HTTP threads.

```java
// Producer: publish ingestion job to Kafka
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentUploadController {

    private final KafkaTemplate<String, IngestionJob> kafkaTemplate;
    private final ObjectStorageService storageService;

    @PostMapping("/ingest")
    public ResponseEntity<IngestionJobResponse> uploadAndQueue(
            @RequestParam MultipartFile file,
            @RequestParam String knowledgeBaseId,
            Authentication auth) {

        String jobId = UUID.randomUUID().toString();
        String tenantId = auth.getName();

        // Store file in S3/GCS first
        String objectKey = storageService.upload(file, tenantId, jobId);

        // Publish async ingestion job
        IngestionJob job = new IngestionJob(jobId, tenantId, knowledgeBaseId,
            objectKey, file.getOriginalFilename(), Instant.now());

        kafkaTemplate.send("document-ingestion", tenantId, job);

        return ResponseEntity.accepted()
            .body(new IngestionJobResponse(jobId, "queued"));
    }
}

// Consumer: process ingestion in background
@Component
@Slf4j
public class DocumentIngestionConsumer {

    private final DocumentIngestionService ingestionService;
    private final ObjectStorageService storageService;
    private final JobStatusService jobStatus;

    @KafkaListener(topics = "document-ingestion", groupId = "ingestion-workers",
        concurrency = "4")
    public void processIngestionJob(IngestionJob job) {
        jobStatus.markInProgress(job.jobId());
        try {
            Resource resource = storageService.download(job.objectKey());
            IngestionResult result = ingestionService.ingest(resource, job.tenantId(),
                job.knowledgeBaseId(), job.filename());

            jobStatus.markComplete(job.jobId(), result.chunksCreated());
            log.info("Ingested job={} chunks={}", job.jobId(), result.chunksCreated());
        } catch (Exception e) {
            jobStatus.markFailed(job.jobId(), e.getMessage());
            log.error("Ingestion failed for job={}", job.jobId(), e);
            throw e;  // triggers Kafka retry / DLQ
        }
    }
}
```
