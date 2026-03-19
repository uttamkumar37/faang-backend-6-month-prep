# LLM Fundamentals for Backend Engineers

## What is a Large Language Model?

A statistical model trained to predict the next token given a sequence of tokens.

```
Input:  "The quick brown"
Output: "fox" (highest probability next token)
```

Trained on trillions of tokens from internet text, books, and code.  
Model weights: GPT-4 ~1.8 trillion parameters; Llama 3.1 70B = 70 billion parameters.

---

## Transformer Architecture

Core innovation enabling modern LLMs (Vaswani et al., "Attention Is All You Need", 2017).

```
Input tokens → Embedding Layer
                   ↓
              [Positional Encoding added]
                   ↓
              N × Transformer Blocks:
                [ Multi-Head Self-Attention ]
                [ Add & Norm               ]
                [ Feed-Forward Network     ]
                [ Add & Norm               ]
                   ↓
              Output Head (Linear + Softmax)
                   ↓
              Probability over vocabulary
```

### Self-Attention

For each token, compute how much to "attend" to every other token:

```
Q (Query), K (Key), V (Value) matrices:

Attention(Q, K, V) = softmax(QK^T / √d_k) × V

QK^T: how similar is each token to every other token?
/√d_k: scale to prevent gradient saturation
softmax: normalize to probabilities
× V: weighted sum of values
```

Multi-head: run attention H times in parallel (different subspaces), concatenate.

---

## Key Concepts for API Usage

### Tokens vs Words

```
~0.75 words per token on average.
"Hello world" → ["Hello", " world"] = 2 tokens
"antidisestablishmentarianism" → ~5 tokens (splits rare words)

GPT-4 context window: 128K tokens ≈ ~96K words ≈ 300 pages
```

### Temperature

Controls randomness of output distribution:

```
temperature=0.0: always pick highest probability token (deterministic, repetitive)
temperature=1.0: sample from the distribution (default, creative)
temperature=2.0: highly random, incoherent

For production APIs (structured output, JSON):  temperature=0.0–0.3
For creative content:                            temperature=0.7–1.0
```

### Top-P (Nucleus Sampling)

```
top_p=0.9: only sample from the top 90% cumulative probability tokens
           → adaptively restricts vocabulary without fixed temperature
```

### Context Window & Attention Window

The maximum total tokens the model can attend to (input + output combined).

| Model | Context Window |
|---|---|
| GPT-3.5 | 16K tokens |
| GPT-4o | 128K tokens |
| Claude 3.5 | 200K tokens |
| Llama 3.1 | 128K tokens |

---

## Model Types

### Base Model vs Instruction-Tuned vs RLHF

```
Base Model: next token prediction only. Raw output, ignores questions.
Instruction-Tuned (SFT): fine-tuned on (instruction → response) pairs. Follows instructions.
RLHF: Reinforcement Learning from Human Feedback. Aligned to be helpful/harmless.

GPT-4 = base → SFT → RLHF (ChatGPT-grade output)
```

### Embeddings Models vs Generation Models

| Type | Input | Output | Use Case |
|---|---|---|---|
| Generation (GPT, Claude) | Prompt | Text | Chat, code, summarization |
| Embedding (text-embedding-3) | Text | Float vector | Similarity search, RAG |
| Reranking (Cohere rerank) | Query + docs | Relevance scores | RAG result ranking |

---

## Prompting Concepts

### System / User / Assistant Roles

```json
[
  {"role": "system",    "content": "You are a helpful Java expert."},
  {"role": "user",      "content": "Explain virtual threads."},
  {"role": "assistant", "content": "Virtual threads in Java 21..."}
]
```

System message: defines persona, constraints, output format.

### Zero-Shot vs Few-Shot

```
Zero-shot: just give the instruction.
  "Classify this review as positive or negative: 'Great product!'"

Few-shot: provide examples first.
  "Classify reviews:
   'Great product!' → positive
   'Broken on arrival' → negative
   'Works as expected' → "
```

### Chain of Thought (CoT)

Add "think step by step" → model reasons before answering → better accuracy on logic tasks.

---

## Inference Parameters Reference

```json
{
  "model": "gpt-4o",
  "messages": [...],
  "temperature": 0.2,
  "max_tokens": 1000,
  "top_p": 0.95,
  "frequency_penalty": 0.5,   // penalize repeated tokens
  "presence_penalty": 0.3,    // penalize tokens already in context
  "response_format": {"type": "json_object"}  // structured output
}
```

---

## Costs & Latency

Typical pricing (approximate, changes often):

| Model | Input ($/1M tokens) | Output ($/1M tokens) |
|---|---|---|
| GPT-4o | $2.50 | $10.00 |
| GPT-4o mini | $0.15 | $0.60 |
| Claude 3.5 Haiku | $0.80 | $4.00 |
| Llama 3.1 8B (self-hosted) | ~$0.05 compute | |

Latency: first token latency (TTFT) 0.5–2s, full response for 500 tokens: 2–10s.

---

## Interview Tips

- Explain transformers at a conceptual level: self-attention = tokens learning context from each other.
- Know temperature and when to set it low (structured tasks) vs high (creative tasks).
- RAG is the #1 production pattern — understand why (avoids hallucination, adds knowledge without fine-tuning).
- Context window limits are practical engineering constraints — chunking and data selection matter.

---

## Tokenization Deep Dive

### BPE (Byte Pair Encoding)

The most common tokenization algorithm used by GPT models.

How it works:
1. Start with individual characters as tokens.
2. Repeatedly merge the most frequently co-occurring pair into a new token.
3. Repeat until vocabulary size is reached (GPT-4: ~100K tokens).

```
Training corpus example: "low low low lowest newest"
Start:  l o w | l o w | l o w | l o w e s t | n e w e s t
Step 1: merge 'lo' → "lo w | lo w | lo w | lo w e s t | n e w e s t"
Step 2: merge 'low' → "low | low | low | low e s t | n e w e s t"
...
Final tokens for "lowest": ["low", "est"]
```

Implications for engineers:
- Rare words split into many tokens → expensive to process.
- Technical jargon, code, and non-English text produce more tokens per word.
- Numbers tokenize unpredictably: "12345" may be 1, 2, or 4 tokens depending on context.
- Chinese/Japanese characters often take 2-3 tokens per character.

Token counting before API calls:

```java
// Using tiktoken4j
Encoding encoding = EncodingRegistry.getInstance().getEncoding(EncodingType.CL100K_BASE);
int tokenCount = encoding.countTokens(prompt);

// Rule of thumb without library: tokens ≈ characters / 4
int estimatedTokens = prompt.length() / 4;
```

---

## Why Hallucinations Happen: Mechanistic View

The model generates text by predicting the next token given all previous tokens. It does NOT:
- look up a database
- verify facts against ground truth
- know what it does or does not know

What the model actually does:
- Finds the statistical pattern that best fits the prompt.
- If the prompt sounds like a factual question, it generates text that sounds like a factual answer.
- The "confidence" of the output is just the probability of the chosen tokens — not factual certainty.

Types of hallucination:

| Type | Example | Root cause |
|---|---|---|
| Factual hallucination | Invents citations, wrong dates | Training data bias, statistical pattern completion |
| Intrinsic hallucination | Contradicts information in the provided context | Attention failure to weight context correctly |
| Extrinsic hallucination | Adds information not in context or training data | Model filling gaps with plausible text |
| Self-contradiction | Disagrees with itself across paragraphs | Stateless generation; each token is local |

Mitigation strategies in backend systems:
- Retrieval grounding (RAG): inject real source text. Model is constrained to generate from it.
- Instruction: "Answer ONLY from the provided context. If the answer is not there, say you do not know."
- Confidence scoring: use structured output to ask model for its confidence. Route low-confidence results for review.
- Citation enforcement: require model to quote the exact passage it is using.
- Post-generation verification: use a separate LLM call to check faithfulness.

---

## Fine-Tuning: Types and Trade-offs

Fine-tuning adapts a pre-trained model to a specific domain or task by continuing training on custom data.

### Types of fine-tuning

Full fine-tuning:
- Update all model weights.
- Highest quality adaptation.
- Expensive: requires the same GPU memory and compute as training.
- Risk of catastrophic forgetting (forgetting general knowledge).

LoRA (Low-Rank Adaptation):
- Insert small trainable matrices inside transformer layers.
- Only 1-5% of parameters are updated.
- Can run on consumer-grade GPUs.
- Quality close to full fine-tuning for task adaptation.

PEFT (Parameter-Efficient Fine-Tuning):
- Umbrella term for methods including LoRA, Prefix-tuning, IA3.
- The standard approach for production fine-tuning today.

SFT (Supervised Fine-Tuning):
- Train on (instruction → response) pairs.
- Good for: teaching a new format, domain-specific vocabulary, response style.

DPO (Direct Preference Optimization):
- Train on (prompt, preferred response, rejected response) triples.
- Better than RLHF for aligning model behavior with specific preferences.
- No reward model needed.

RLHF (Reinforcement Learning from Human Feedback):
- Train a reward model from human preference data.
- Use PPO (Proximal Policy Optimization) to optimize the LLM against the reward model.
- Used by OpenAI and Anthropic for alignment.
- Complex: requires reward model training + RL loop.

### RAG vs fine-tuning decision framework

| Situation | Use RAG | Use fine-tuning |
|---|---|---|
| Company-specific factual knowledge | ✓ | |
| Source data changes frequently | ✓ | |
| Need traceable citations | ✓ | |
| Need to match a specific output style/format | | ✓ |
| Domain vocabulary not in training data | | ✓ |
| General knowledge + company-specific facts | ✓ (RAG on company data) | |
| Low latency → no retrieval step | | ✓ |
| Limited budget for annotation data | ✓ | |

Theory:
- Fine-tuning is not a substitute for retrieval when data freshness matters.
- Combine both: fine-tune for style and format, use RAG for factual grounding.
- Fine-tuned models are not automatically more accurate; they can hallucinate with more confidence.

---

## Quantization Basics

Models are large. Reducing numerical precision reduces memory and speeds up inference.

| Precision | Bits per weight | Model size (7B params) | Quality impact |
|---|---|---|---|
| FP32 (full) | 32 | ~28 GB | Baseline |
| FP16 / BF16 | 16 | ~14 GB | Negligible |
| INT8 | 8 | ~7 GB | Minor |
| INT4 (GGUF) | 4 | ~3.5 GB | Small, noticeable on complex tasks |

Practical implications:
- Cloud API providers handle this transparently.
- Self-hosted models (Ollama, vLLM): INT4 GGUF lets you run a 7B model on 8 GB RAM.
- INT4 is good enough for embeddings; use INT8 or FP16 for production chat quality.

GGUF format:
- Standard file format for quantized local models.
- Used by Ollama, llama.cpp, LM Studio.
- Supported by Spring AI via Ollama integration for local development and testing.

```yaml
# application.yml for local dev with Ollama
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1:8b-instruct-q4_K_M
      embedding:
        options:
          model: nomic-embed-text
```

---

## In-Context Learning and Its Limits

In-context learning (ICL) is the ability of LLMs to learn from examples provided in the prompt without weight updates.

Few-shot prompting is ICL in action. But it has limits:

Limit 1 — Context window:
- You can only fit so many examples in the prompt.
- GPT-4o: 128K tokens ≈ ~100K words. Sounds large, but long conversations fill it fast.

Limit 2 — Attention dilution:
- Very long prompts cause the model to "forget" examples from the start.
- The "needle in the haystack" problem: retrieval accuracy drops for information buried in the middle of a long context.

Limit 3 — Not actual learning:
- The model does not update its weights from ICL.
- Start a new session and all the examples are gone.
- Fine-tuning converts ICL examples into permanent behavior.

Limit 4 — ICL is stateless per session:
- Conversation history must be maintained by the application, not the model.
- Memory architectures (Redis, vector store) externalize state.

```java
// Maintaining conversation history in application layer (Spring AI)
List<Message> history = new ArrayList<>();
history.add(new SystemMessage("You are a Java expert."));

// Each turn:
history.add(new UserMessage(userInput));
ChatResponse response = chatModel.call(new Prompt(history));
history.add(new AssistantMessage(response.getResult().getOutput().getContent()));

// Trim history when too long (sliding window):
if (history.size() > MAX_HISTORY_TOKENS) {
    history = history.subList(history.size() - MAX_HISTORY_TOKENS, history.size());
}
```

---

## Streaming vs Batch Inference

### Streaming (token-by-token)
- Model outputs one token at a time as it generates.
- The application receives tokens via Server-Sent Events (SSE) or WebSocket.
- User perceives lower latency because they see the first word within 300-800ms.
- Implementation: set `stream=true` in the API call.

```java
// Spring AI streaming
@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String question) {
    return chatClient.prompt()
        .user(question)
        .stream()
        .content()
        .doOnError(e -> log.error("Stream error", e))
        .onErrorReturn("Error generating response.");
}
```

### Batch inference
- Send many prompts at once.
- Good for: offline processing, report generation, bulk classification.
- OpenAI Batch API charges 50% less than standard API but has 24-hour latency SLA.

```java
// Batch embedding for document ingestion (more efficient)
List<String> chunks = documentChunker.split(document);

// Batch in groups of 512 (OpenAI embedding batch limit)
List<List<String>> batches = partition(chunks, 512);
List<float[]> allEmbeddings = new ArrayList<>();

for (List<String> batch : batches) {
    List<float[]> batchEmbeddings = embeddingModel.embedAll(batch);
    allEmbeddings.addAll(batchEmbeddings);
}
```

---

## Model Selection Guide for Backend Engineers

| Use case | Recommended model tier | Why |
|---|---|---|
| Simple classification / extraction | GPT-4o-mini, Claude Haiku, Gemini Flash | Low cost, fast, sufficient |
| RAG over internal docs | GPT-4o, Claude Sonnet | Good instruction following, citation quality |
| Code generation / review | GPT-4o, Claude Sonnet | Strong on code |
| Complex multi-step reasoning | GPT-4o, Claude Opus | Best reasoning quality |
| Embeddings | text-embedding-3-small | Cost-effective, good quality |
| Embeddings (highest quality) | text-embedding-3-large | 2x dimensions, better recall |
| Local dev / testing | Ollama + llama3.1:8b | Free, no API key, offline |
| Self-hosted production | vLLM + llama3.1:70b or Mistral | Full control, data privacy |

Model routing in code:

```java
public enum QueryComplexity { SIMPLE, MODERATE, COMPLEX }

public String routedCall(String prompt, QueryComplexity complexity) {
    String model = switch (complexity) {
        case SIMPLE   -> "gpt-4o-mini";
        case MODERATE -> "gpt-4o";
        case COMPLEX  -> "gpt-4o";   // gpt-4o is the ceiling for OpenAI
    };
    return chatModel.call(new Prompt(prompt,
        OpenAiChatOptions.builder().withModel(model).build()
    )).getResult().getOutput().getContent();
}
```
