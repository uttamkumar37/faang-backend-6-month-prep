# Mock Interview Bank

Use this file as a practice source for coding, design, and leadership rounds.

## 1. Coding mock themes

### Arrays / strings
- longest substring without repeating characters
- minimum window substring
- product of array except self
- merge intervals

### Trees / graphs
- binary tree level order traversal
- lowest common ancestor
- number of islands
- course schedule
- clone graph

### DP / advanced
- coin change
- longest increasing subsequence
- word break
- house robber variations

### SQL / database coding
- write a query for latest order per user using window functions
- design indexes for user order history with keyset pagination
- prevent duplicate payment processing with a unique idempotency key
- identify and fix an N+1 query pattern in an order-items endpoint

## 2. System design mock prompts

- Design a scalable URL shortener.
- Design a notification service for email, SMS, and push.
- Design an API rate limiter for a public platform.
- Design a real-time chat backend.
- Design an order processing service for e-commerce.
- Design a metrics ingestion pipeline.
- Design an AI-powered document Q and A platform.
- Design a recommendation service backend.

## 3. LLD / OOD prompts

- Design a parking lot system.
- Design a meeting scheduler.
- Design a rate limiter library.
- Design an in-memory cache with eviction policy.
- Design a notification template engine.
- Design Splitwise.
- Design BookMyShow seat locking and booking.
- Design an elevator system.
- Design a logging framework.
- Use `06-low-level-design/` for requirements, entities, APIs, edge cases, Java skeletons, and test ideas.

## 4. Production debugging prompts

- A Java service suddenly uses 390 percent CPU on a 4-core instance. Walk through debugging.
- p99 latency for `/checkout` increased from 180 ms to 2.4 seconds after a deploy. What do you check?
- The service is OOM-killed every 6 hours. How do you distinguish leak from allocation pressure?
- Database query execution is fast, but API calls time out. How do you debug connection pool exhaustion?
- Kafka consumer lag grows on only one partition. What are likely causes and mitigations?
- Redis hit ratio is stable, but latency spikes. How do you inspect big keys, slow logs, and evictions?
- Thread pool queue depth grows while CPU is low. What does that suggest?
- Write a postmortem action item that is measurable and owned.

## 5. Java/backend deep-dive prompts

- Explain `@Transactional` proxy behavior, rollback rules, propagation, and self-invocation risk.
- How would you size and monitor a thread pool for outbound API calls?
- Explain Kafka at-least-once delivery and how your consumer remains idempotent.
- Explain Redis cache-aside invalidation and stampede prevention.
- Explain how to read a basic JVM thread dump during high latency.
- Explain how Spring Boot handles a request from servlet thread to controller to service to repository.

## 6. AI discussion prompts

- How would you build a RAG system for internal company docs?
- When would you use fine-tuning instead of prompt + retrieval?
- How would you evaluate hallucinations in a production assistant?
- How would you reduce latency and cost for an AI endpoint?
- How would you protect against prompt injection and data leakage?

## 7. Behavioral prompts

- Tell me about a time you handled a production issue.
- Tell me about a time you disagreed with a design decision.
- Tell me about a time you improved performance significantly.
- Tell me about a time you led without authority.
- Tell me about a project that failed and what you changed after that.
- Tell me about a time you mentored another engineer.
- Tell me about a time you made a decision under ambiguity.
- Tell me about a time you balanced delivery pressure with operational risk.

## 8. Company-specific mixed mocks

- Google-style: 2 coding rounds with strict communication, 1 system design, 1 leadership/ambiguity story.
- Microsoft-style: coding plus practical design trade-offs, collaboration, customer focus, and growth-mindset stories.
- Amazon-style: coding, system design, and Leadership Principle follow-ups in every round.
- Uber-style: high-scale marketplace or real-time system design plus operational trade-offs.
- Flipkart-style: DSA plus LLD/machine coding plus commerce backend design.
- Atlassian-style: data structures, code design, system design, collaboration, and values alignment.
- LinkedIn-style: graph/feed/search/notification design plus Java backend depth and product impact.

## 9. Mock scoring template

After each mock, score 1-5 on:
- problem understanding
- structure of solution
- correctness
- communication
- trade-off reasoning
- depth
- confidence without bluffing
- production readiness
- behavioral evidence
- time management

## 10. Post-mock review template

Write down:
- What did I miss?
- Where did I ramble?
- Which trade-off should I have mentioned?
- What question from interviewer surprised me?
- What will I change before next mock?

Store the result in `06-interview-tracker/mock-feedback.md` and add repeated issues to `06-interview-tracker/mistake-log.md`.

## 11. Monthly mock targets

| Month | Minimum mock mix |
|---|---|
| 1 | 2 coding/timed sets, 1 basic design walkthrough |
| 2 | 2 coding, 1 Java backend, 1 LLD |
| 3 | 2 coding, 2 design, 1 production debugging |
| 4 | 2 coding, 2 design, 1 LLD, 1 AI/backend |
| 5 | 4 mixed mocks including company-specific rounds |
| 6 | 6+ final loop simulations across target companies |
