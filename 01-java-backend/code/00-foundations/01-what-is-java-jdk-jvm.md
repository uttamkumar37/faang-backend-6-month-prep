# What is Java — JDK, JRE, JVM, SDK

---

## 1. What is Java?

Java is three things at once:

| What | Meaning |
|---|---|
| **Programming Language** | Statically typed, object-oriented language with C-like syntax |
| **Platform** | A runtime environment (JVM) that can execute Java programs on any OS |
| **Ecosystem** | Standard library (JDK APIs), build tools (Maven/Gradle), frameworks (Spring) |

### Key properties

- **Compiled AND interpreted**: source is compiled to bytecode, bytecode is interpreted (or JIT-compiled) at runtime.
- **Strongly typed**: every variable has a compile-time type; the compiler rejects type mismatches.
- **Object-oriented**: all code lives inside classes; everything is an object (except primitives).
- **Platform independent**: same `.class` file runs on Windows, Linux, macOS without recompilation.
- **Automatic memory management**: the JVM's garbage collector reclaims unused objects — no `malloc/free`.
- **Multithreaded**: built-in language support for concurrent execution.

---

## 2. The Three Components: JDK, JRE, JVM

```
┌─────────────────────────────────────────────────────────────┐
│                         JDK                                  │
│  (Java Development Kit — what developers install)           │
│                                                             │
│  javac (compiler)  jshell  jmap  jstack  jfr  jar  keytool  │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                     JRE                               │  │
│  │  (Java Runtime Environment — what END USERS need)     │  │
│  │                                                       │  │
│  │  Java Class Library (java.lang, java.util, java.io…)  │  │
│  │                                                       │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │                   JVM                            │  │  │
│  │  │  (Java Virtual Machine — the execution engine)   │  │  │
│  │  │                                                   │  │  │
│  │  │  Class Loader → Bytecode Verifier → Interpreter  │  │  │
│  │  │  JIT Compiler → Garbage Collector → Heap/Stack   │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### JVM — Java Virtual Machine

The JVM is a **specification**, not a product. Multiple implementations exist:

| Implementation | Vendor | Notes |
|---|---|---|
| HotSpot | Oracle / OpenJDK | Default, most widely used, best JIT |
| GraalVM | Oracle | HotSpot + native image compilation |
| Eclipse OpenJ9 | IBM/Eclipse | Lower memory footprint, fast startup |
| Azul Zing | Azul | Pause-less GC (C4) |

**What the JVM does:**
1. Loads `.class` files (ClassLoader subsystem).
2. Verifies bytecode is safe (Bytecode Verifier).
3. Interprets bytecode or JIT-compiles hot methods to native machine code.
4. Manages heap memory and runs the garbage collector.
5. Provides thread scheduling, I/O, and OS abstraction.

**Why "Virtual"?** It abstracts over the underlying hardware and OS. The JVM specification defines a standardized instruction set (bytecode), memory model, and execution semantics. Any hardware that has a JVM implementation can run the same `.class` files.

### JRE — Java Runtime Environment

JRE = JVM + Java Class Library (the standard API).

The **Java Class Library** provides:
- `java.lang` — `Object`, `String`, `Math`, `Thread`, `System`
- `java.util` — `ArrayList`, `HashMap`, `Collections`, `Optional`, `Scanner`
- `java.io` / `java.nio` — file I/O, streams, channels
- `java.net` — sockets, HTTP clients
- `java.util.concurrent` — thread pools, locks, atomics
- `java.time` — modern date/time API (LocalDate, ZonedDateTime)
- `javax.*` / `jakarta.*` — enterprise APIs (JPA, Servlet, etc.)

> Since Java 11, standalone JRE distributions are no longer provided. Install the JDK.

### JDK — Java Development Kit

JDK = JRE + developement tools.

| Tool | Purpose |
|---|---|
| `javac` | Compiler — converts `.java` to `.class` |
| `java` | Launcher — starts the JVM and runs your program |
| `jar` | Packages `.class` files into a JAR archive |
| `javadoc` | Generates HTML documentation from source comments |
| `jshell` | Interactive REPL — try Java expressions instantly |
| `jmap` | Heap dump / histogram |
| `jstack` | Thread dump |
| `jstat` | GC statistics |
| `jcmd` | All-in-one diagnostics (JFR, VM flags, etc.) |
| `jlink` | Create minimal custom JRE for your app |
| `jpackage` | Package app as OS-native installer |
| `keytool` | Manage SSL/TLS keystores |

---

## 3. SDK vs JDK

**SDK (Software Development Kit)** is a general term — a collection of tools, libraries, and APIs for developing software for a specific platform.

- **Android SDK** = tools to build Android apps.
- **AWS SDK** = libraries to call AWS APIs.
- **JDK** = the SDK for the Java platform. Sometimes informally called "Java SDK."

So: **JDK ⊆ SDK** (the JDK is a specific kind of SDK).

---

## 4. Java Editions

| Edition | Full Name | Target |
|---|---|---|
| **Java SE** | Standard Edition | Desktop apps, command-line tools, foundations |
| **Java EE / Jakarta EE** | Enterprise Edition | Web apps, enterprise services (Servlet, JPA, CDI) |
| **Java ME** | Micro Edition | Embedded, IoT, legacy mobile |

Modern backend development uses **Java SE + Jakarta EE APIs** (often via Spring, which wraps them).

---

## 5. Platform Independence — Write Once, Run Anywhere

```
Developer Machine                 Server (Linux/ARM)
────────────────────              ──────────────────
HelloWorld.java                   HelloWorld.class
       │                                  │
   javac (JDK)                     java (JVM for ARM)
       │                                  │
HelloWorld.class  ──── same file ──→  executes here!
```

The `.class` file contains **bytecode** — an intermediate representation that is:
- Higher level than native machine code (doesn't depend on CPU architecture).
- Lower level than source code (already compiled, stripped of comments, layout).

Each OS/CPU platform has its own JVM implementation that translates bytecode to native instructions.

---

## 6. Java Version History (Key Milestones)

| Version | Year | Key Feature |
|---|---|---|
| Java 1.0 | 1996 | First release — Applets, AWT |
| Java 1.2 | 1998 | Collections Framework, Swing |
| Java 5 | 2004 | Generics, Enums, Annotations, Autoboxing, Varargs, for-each loop |
| Java 6 | 2006 | Performance improvements, scripting API |
| Java 7 | 2011 | try-with-resources, diamond operator `<>`, NIO.2 |
| Java 8 | 2014 | **Lambdas**, Stream API, Optional, `java.time`, default methods |
| Java 9 | 2017 | **Module System (Jigsaw)**, JShell, G1GC default |
| Java 10 | 2018 | `var` (local variable type inference) |
| Java 11 | 2018 | LTS — String methods, HTTP Client API, no standalone JRE |
| Java 14 | 2020 | `switch` expressions (standard), records (preview) |
| Java 16 | 2021 | Records (standard), `instanceof` pattern matching |
| Java 17 | 2021 | **LTS** — Sealed classes, enhanced switch, strong encapsulation |
| Java 19 | 2022 | Virtual Threads (preview), Structured Concurrency (preview) |
| Java 21 | 2023 | **LTS** — Virtual Threads (standard), Sequenced Collections, pattern matching in switch |

> FAANG interviews focus: Java 8+ features are expected knowledge. Java 17 and 21 LTS features are increasingly discussed.

---

## 7. Java Module System (Java 9+)

Before Java 9, the JDK was one massive jar — you couldn't use just part of it.

```
Java 9 introduced JPMS (Java Platform Module System):
  module-info.java declares:
    - module name
    - what packages it EXPORTS (visible to others)
    - what modules it REQUIRES (dependencies)

Example:
  module com.myapp.orders {
      requires java.net.http;
      requires com.myapp.users;
      exports com.myapp.orders.api;
  }
```

**Why it matters:**
- Strong encapsulation: unexported packages are truly private.
- Faster startup: JVM only loads needed modules.
- `jlink` can create a minimal custom JRE.
- Breaks old reflection hacks (`--add-opens` needed in some frameworks).

---

## 8. OpenJDK vs Oracle JDK

| | OpenJDK | Oracle JDK |
|---|---|---|
| License | GPL v2 + Classpath Exception (free) | BCL (free for dev, paid for production in older versions) |
| Source | Open source | Was closed source; now mostly OpenJDK |
| Support | Community builds (Adoptium, Amazon Corretto, Azul) | Oracle (paid) |
| Performance | Same from Java 11 onward | Identical (same codebase) |

**Recommendation**: Use **Eclipse Temurin** (Adoptium) or **Amazon Corretto** for free, production-ready OpenJDK builds.

---

## 9. How Java Achieves Backward Compatibility

Java has maintained almost perfect backward compatibility since Java 1.0. A class compiled with Java 5 can run on Java 21's JVM.

Mechanism:
- `.class` files contain a **major version number** (e.g., 65 = Java 21).
- The JVM checks: if `.class` major version > JVM's supported max → `UnsupportedClassVersionError`.
- If `.class` is older than the JVM → runs fine (backward compatible).

```bash
# Check what version compiled your class
javap -verbose MyClass.class | grep major
```

---

## 10. Interview Q&A

**Q: What is the difference between JDK, JRE, and JVM?**  
JVM is the execution engine — it loads bytecode, JIT-compiles hot paths, and manages memory. JRE is JVM + the standard class library (java.lang, java.util, etc.) — everything needed to RUN a Java program. JDK is JRE + developer tools (javac, jshell, jmap) — everything needed to BUILD a Java program. Since Java 11, JRE is no longer distributed separately; install the JDK.

**Q: What does "platform independence" mean in Java?**  
Java source compiles to platform-neutral bytecode (.class files). Any OS/CPU that has a JVM implementation can execute that bytecode. The JVM handles OS-specific details (file paths, threading, memory layout). This means you compile once and run anywhere — on Windows, Linux, ARM servers, or Docker containers — without recompilation.

**Q: Why can't we just run Java bytecode directly on the CPU?**  
CPUs execute native machine code specific to their instruction set (x86, ARM, RISC-V). Bytecode is a higher-level, architecture-neutral instruction set designed for the JVM. The JVM translates bytecode to native instructions either by interpreting it instruction-by-instruction or by JIT-compiling hot methods to native code. This translation layer is what enables platform independence.

**Q: What is GraalVM native image?**  
GraalVM Native Image AOT (ahead-of-time) compiles Java to a native executable, bypassing the JVM at runtime. The result starts in milliseconds (vs JVM's 500ms–5s), uses less memory, and needs no JVM installed on the target machine. Trade-off: the JIT's runtime profile-guided optimizations are unavailable, so peak throughput can be lower. Used by Quarkus and Micronaut for cloud/serverless workloads.
