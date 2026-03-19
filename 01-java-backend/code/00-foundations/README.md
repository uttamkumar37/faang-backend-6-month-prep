# 00 — Java Foundations

> **Learning path (1 of 8):** **`1. 00-foundations`** → `2. collections` → `3. concurrency` → `4. jvm` → `5. performance` → `6. linux-networking` → `7. springboot` → `8. testing-delivery`

Complete guide from absolute basics to advanced internals.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-what-is-java-jdk-jvm.md](01-what-is-java-jdk-jvm.md) | What Java is, JDK vs JRE vs JVM vs SDK, platform independence, Java editions, version history |
| 2 | [02-code-execution-flow.md](02-code-execution-flow.md) | Complete flow from writing .java → javac → bytecode → ClassLoader → JIT → native execution |
| 3 | [03-java-language-basics.md](03-java-language-basics.md) | Primitive types, reference types, variables, operators, control flow, methods, arrays, strings |
| 4 | [04-oop-concepts.md](04-oop-concepts.md) | Classes, objects, constructors, encapsulation, inheritance, polymorphism, abstraction, interfaces, generics, records, sealed classes |
| 5 | [05-modern-java-features.md](05-modern-java-features.md) | Java 8–21: lambdas, streams, Optional, var, switch expressions, pattern matching, records, virtual threads |

## How this fits in the bigger picture

```
00-foundations/          ← START HERE — language + platform fundamentals
01-java-backend/code/
  jvm/                   ← Deep JVM internals (GC, memory, JIT, class loading)
  concurrency/           ← Threads, locks, CompletableFuture, virtual threads
  collections/           ← Data structure internals + production patterns
  springboot/            ← Spring Boot, security, microservices, database
  performance/           ← Profiling, caching, tuning
```

## Study method

1. Read the file end-to-end.
2. Re-draw all diagrams on paper from memory.
3. Write the code snippets from scratch without looking.
4. Explain each concept out loud as if in an interview.
