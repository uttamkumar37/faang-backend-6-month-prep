# 04 — Spring AI

The Spring Framework abstraction layer for building LLM-powered Java services.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [04-spring-ai.md](04-spring-ai.md) | Core abstractions (ChatClient, EmbeddingModel, VectorStore), Maven setup, application.yaml config, structured output, advisors, multi-model routing |
| 2 | [SpringAiIntegrationExample.java](SpringAiIntegrationExample.java) | Full Spring Boot service wiring: ChatClient with system prompt, VectorStore ingestion, structured output with records, streaming responses |

## Key concepts to own

- Why Spring AI: portable across OpenAI, Anthropic, Ollama, Azure — swap model without rewriting.
- Advisors: pre/post-processing hooks (e.g., `QuestionAnswerAdvisor` for automatic RAG context injection).
- Structured output: `chatClient.call().entity(MyRecord.class)` — how Spring AI maps JSON to types.
- Token streaming with `Flux<String>` and Server-Sent Events.

## Study method

1. Read the theory file noting each core abstraction.
2. Trace `SpringAiIntegrationExample.java` — where does each abstraction appear?
3. Answer: "How would you add a second LLM provider as a fallback in Spring AI?"
