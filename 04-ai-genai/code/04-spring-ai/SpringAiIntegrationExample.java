package ai.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.function.Function;

/*
 * Spring AI Integration Examples
 * Production patterns: chat completion, structured output, streaming,
 * RAG with memory, function calling, cost tracking.
 *
 * Dependencies (pom.xml):
 *   spring-ai-openai-spring-boot-starter
 *   spring-ai-pgvector-store-spring-boot-starter
 *
 * Config (application.yaml):
 *   spring.ai.openai.api-key: ${OPENAI_API_KEY}
 *   spring.ai.openai.chat.options.model: gpt-4o-mini
 *   spring.ai.openai.chat.options.temperature: 0.3
 */

// ─────────────────────────────────────────────
// 1. BASIC CHAT SERVICE
// ─────────────────────────────────────────────

@Service
class SimpleChatService {
    private final ChatClient chatClient;

    SimpleChatService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("""
                You are a senior Java backend engineer.
                - Be concise and precise.
                - Use code examples when helpful.
                - Cite Java version if relevant.
                """)
            .build();
    }

    public String ask(String question) {
        return chatClient.prompt()
            .user(question)
            .call()
            .content();
    }

    // Structured output — model must return JSON matching this record
    public CodeReview reviewCode(String code, String language) {
        return chatClient.prompt()
            .user(u -> u.text("""
                Review this {language} code for bugs, performance issues,
                and security vulnerabilities.
                
                Code:
                {code}
                """)
                .param("language", language)
                .param("code", code))
            .call()
            .entity(CodeReview.class);
    }

    // Streaming — good for long responses (UI shows tokens as they arrive)
    public Flux<String> streamAnswer(String question) {
        return chatClient.prompt()
            .user(question)
            .stream()
            .content();
    }

    record CodeReview(
        List<String> bugs,
        List<String> performanceIssues,
        List<String> securityVulnerabilities,
        int qualityScore,     // 1-10
        String summary
    ) {}
}

// ─────────────────────────────────────────────
// 2. RAG SERVICE WITH VECTOR STORE
// ─────────────────────────────────────────────

@Service
class RagChatService {
    private final ChatClient ragChatClient;
    private final VectorStore vectorStore;

    RagChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.ragChatClient = builder
            .defaultSystem("""
                You are a helpful customer support agent.
                Answer ONLY based on the provided context.
                If the answer is not in the context, respond:
                "I don't have that information in my knowledge base."
                Always cite which document source you used.
                """)
            .defaultAdvisors(
                new QuestionAnswerAdvisor(vectorStore,
                    SearchRequest.defaults()
                        .withTopK(5)
                        .withSimilarityThreshold(0.6))
            )
            .build();
    }

    public String ask(String question) {
        return ragChatClient.prompt()
            .user(question)
            .call()
            .content();
    }

    // Add documents to the knowledge base
    public void addToKnowledge(String text, Map<String, Object> metadata) {
        Document doc = new Document(text, metadata);
        vectorStore.add(List.of(doc));
    }

    // Similarity search directly (for testing retrieval quality)
    public List<Document> findSimilar(String query, int topK) {
        return vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(topK)
        );
    }
}

// ─────────────────────────────────────────────
// 3. CHAT WITH MEMORY (maintains conversation history)
// ─────────────────────────────────────────────

@Service
class StatefulChatService {
    private final ChatClient chatClient;

    StatefulChatService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant with memory of the conversation.")
            .defaultAdvisors(
                new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                new QuestionAnswerAdvisor(vectorStore)
            )
            .build();
    }

    public String chat(String sessionId, String message) {
        return chatClient.prompt()
            .user(message)
            .advisors(advisor -> advisor
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
                .param(MessageChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
            .call()
            .content();
    }
}

// ─────────────────────────────────────────────
// 4. FUNCTION CALLING CONFIGURATION
// LLM can invoke real Java methods to get live data.
// ─────────────────────────────────────────────

@Configuration
class AiFunctionConfig {

    // Function bean: LLM will call this when it decides it needs weather data
    @Bean("getWeather")
    @org.springframework.context.annotation.Description(
        "Get current weather for a city. Returns temperature in Celsius and conditions.")
    public Function<WeatherRequest, WeatherResponse> weatherFunction() {
        return request -> {
            // In production: call actual weather API
            System.out.println("[Tool] Fetching weather for: " + request.city());
            return new WeatherResponse(22.0, "partly cloudy", "Celsius");
        };
    }

    @Bean("executeJavaCode")
    @org.springframework.context.annotation.Description(
        "Execute a Java code snippet and return the output.")
    public Function<CodeRequest, CodeResponse> codeExecutor() {
        return request -> {
            // In production: sandboxed execution (never execute without sandboxing!)
            System.out.println("[Tool] Would execute: " + request.code());
            return new CodeResponse("Hello World", null, 0);
        };
    }

    record WeatherRequest(String city, String unit) {}
    record WeatherResponse(double temperature, String condition, String unit) {}
    record CodeRequest(String code, String language) {}
    record CodeResponse(String output, String error, int exitCode) {}
}

// ─────────────────────────────────────────────
// 5. FUNCTION CALLING CHAT SERVICE
// ─────────────────────────────────────────────

@Service
class AgentChatService {
    private final ChatClient chatClient;

    AgentChatService(ChatClient.Builder builder) {
        this.chatClient = builder
            .defaultSystem("You are a helpful assistant with access to tools.")
            .build();
    }

    public String askWithTools(String question) {
        return chatClient.prompt()
            .user(question)
            .functions("getWeather", "executeJavaCode")  // function bean names
            .call()
            .content();
        // LLM automatically decides when to call tools,
        // calls them, gets results, then generates final answer.
    }
}

// ─────────────────────────────────────────────
// 6. REST CONTROLLER
// ─────────────────────────────────────────────

@RestController
@RequestMapping("/api/v1/ai")
class AiController {
    private final SimpleChatService chatService;
    private final RagChatService ragService;
    private final StatefulChatService statefulChat;
    private final AgentChatService agentService;

    AiController(SimpleChatService chatService, RagChatService ragService,
                 StatefulChatService statefulChat, AgentChatService agentService) {
        this.chatService = chatService;
        this.ragService = ragService;
        this.statefulChat = statefulChat;
        this.agentService = agentService;
    }

    @PostMapping("/ask")
    public Map<String, String> ask(@RequestBody Map<String, String> body) {
        String answer = chatService.ask(body.get("question"));
        return Map.of("answer", answer);
    }

    @PostMapping("/review-code")
    public SimpleChatService.CodeReview reviewCode(@RequestBody Map<String, String> body) {
        return chatService.reviewCode(body.get("code"), body.get("language"));
    }

    // Streaming endpoint — sends tokens as SSE
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> stream(@RequestParam String question) {
        return chatService.streamAnswer(question);
    }

    @PostMapping("/rag/ask")
    public Map<String, String> ragAsk(@RequestBody Map<String, String> body) {
        return Map.of("answer", ragService.ask(body.get("question")));
    }

    @PostMapping("/rag/ingest")
    public Map<String, Object> ingest(@RequestBody Map<String, Object> body) {
        ragService.addToKnowledge(
            (String) body.get("text"),
            (Map<String, Object>) body.getOrDefault("metadata", Map.of())
        );
        return Map.of("status", "ingested");
    }

    @PostMapping("/chat/{sessionId}")
    public Map<String, String> chat(@PathVariable String sessionId,
                                     @RequestBody Map<String, String> body) {
        String response = statefulChat.chat(sessionId, body.get("message"));
        return Map.of("response", response);
    }

    @PostMapping("/agent")
    public Map<String, String> agentAsk(@RequestBody Map<String, String> body) {
        return Map.of("answer", agentService.askWithTools(body.get("question")));
    }
}

// Entry point for documentation purposes
public class SpringAiIntegrationExample {
    // This file is a Spring Boot application component.
    // Wire into your @SpringBootApplication main class.
    // All beans above auto-configure via spring-ai-openai-spring-boot-starter.

    /*
     * Sample API calls:
     *
     * curl -X POST localhost:8080/api/v1/ai/ask \
     *   -H "Content-Type: application/json" \
     *   -d '{"question":"Explain CompletableFuture in 3 sentences."}'
     *
     * curl -X POST localhost:8080/api/v1/ai/review-code \
     *   -d '{"code":"for(int i=0;i<list.size();i++) {...}","language":"Java"}'
     *
     * curl -X POST localhost:8080/api/v1/ai/rag/ingest \
     *   -d '{"text":"Refunds take 5-7 days.","metadata":{"source":"faq","category":"refunds"}}'
     *
     * curl -X POST localhost:8080/api/v1/ai/rag/ask \
     *   -d '{"question":"How long does a refund take?"}'
     */
}
