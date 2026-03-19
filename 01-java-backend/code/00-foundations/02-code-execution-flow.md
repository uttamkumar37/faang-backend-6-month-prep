# Java Code Execution Flow — From Source to Running Program

---

## 1. The Complete Picture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         COMPILE TIME  (developer's machine)                  │
│                                                                              │
│   HelloWorld.java                                                            │
│        │                                                                     │
│        ▼                                                                     │
│   ┌─────────┐   tokenize    ┌──────────┐  parse   ┌─────────┐              │
│   │ Source  │ ────────────▶ │  Tokens  │ ───────▶ │   AST   │              │
│   │  file   │               │(javac)   │          │ (tree)  │              │
│   └─────────┘               └──────────┘          └────┬────┘              │
│                                                         │ type check        │
│                                                         ▼                   │
│                                                   ┌──────────┐             │
│                                                   │ Semantic  │             │
│                                                   │ Analysis  │             │
│                                                   └────┬──────┘             │
│                                                        │ generate           │
│                                                        ▼                   │
│                                                  HelloWorld.class           │
│                                                  (bytecode)                │
└──────────────────────────────────────────────────────────────────────────────┘
                                    │
                        package in JAR / deploy
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        RUNTIME  (any machine with JVM)                       │
│                                                                              │
│   java HelloWorld                                                            │
│        │                                                                     │
│        ▼                                                                     │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │                        JVM Startup                                    │  │
│   │   1. Bootstrap ClassLoader loads java.lang.*                         │  │
│   │   2. Platform ClassLoader loads JDK modules                          │  │
│   │   3. Application ClassLoader loads your classes from classpath       │  │
│   └──────────────────────┬───────────────────────────────────────────────┘  │
│                           │                                                  │
│                           ▼                                                  │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │                    Class Loading Pipeline                             │  │
│   │   Loading → Verification → Preparation → Resolution → Initialization │  │
│   └──────────────────────┬───────────────────────────────────────────────┘  │
│                           │                                                  │
│                           ▼                                                  │
│   ┌──────────────────────────────────────────────────────────────────────┐  │
│   │                  Execution Engine                                     │  │
│   │                                                                       │  │
│   │   First call:  Interpreter (slow, but starts instantly)               │  │
│   │       │                                                               │  │
│   │       │  profiling running counts + branch statistics                 │  │
│   │       ▼                                                               │  │
│   │   Hot method (>10k invocations):  JIT Compiler (C1 → C2)             │  │
│   │       │                                                               │  │
│   │       │  inlining, escape analysis, loop unrolling                    │  │
│   │       ▼                                                               │  │
│   │   Native Machine Code in Code Cache                                   │  │
│   └──────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│   Meanwhile: Garbage Collector runs concurrently, reclaiming unreachable    │
│   objects from the heap                                                      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Step 1 — Writing Source Code

```java
// HelloWorld.java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

**Rules at this stage:**
- File name must match the `public` class name: `HelloWorld.java`.
- One `.java` file can have multiple classes, but only one `public` class.
- Package declaration must be the first statement (if used).

---

## 3. Step 2 — Compilation with `javac`

```bash
javac HelloWorld.java
# Output: HelloWorld.class
```

### What javac does internally

#### Phase 1 — Lexing (tokenization)

The source text is broken into **tokens**:

```
public class HelloWorld { public static void main ...
  │       │       │     │    │       │      │     │
TOKEN:  TOKEN:  TOKEN: ... TOKEN:  TOKEN: TOKEN: ...
KEYWORD KEYWORD IDENT     KEYWORD KEYWORD KEYWORD
```

#### Phase 2 — Parsing (AST construction)

Tokens are arranged into an **Abstract Syntax Tree (AST)**:

```
ClassDeclaration: HelloWorld
└── MethodDeclaration: main(String[])
    └── ExpressionStatement
        └── MethodInvocation: System.out.println
            └── StringLiteral: "Hello, World!"
```

#### Phase 3 — Semantic Analysis & Type Checking

The compiler:
- Resolves names (what class is `System`? what field is `out`? what method is `println`?).
- Type-checks every expression (is `"Hello"` a valid argument to `println(String)`?).
- Checks accessibility (can you call `private` method from here?).
- Reports compile errors (type mismatch, undefined symbol, unreachable code).

#### Phase 4 — Desugaring

The compiler transforms syntactic sugar into simpler equivalents:

```java
// Source (enhanced for loop)
for (String s : list) { ... }

// After desugaring (iterator loop)
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    String s = it.next();
    ...
}
```

Other desugaring examples:
- `try-with-resources` → `try/finally` + `Closeable.close()`
- Autoboxing `int → Integer` → `Integer.valueOf(int)`
- String switch → integer switch on `hashCode()` + `equals()`
- Lambdas → inner class method references (invokedynamic)

#### Phase 5 — Bytecode Generation

The AST is traversed and **JVM bytecode instructions** are emitted into a `.class` file.

```
Source:  System.out.println("Hello, World!");
Bytecode:
  0: getstatic     #7   // Field System.out:Ljava/io/PrintStream;
  3: ldc           #13  // String "Hello, World!"
  5: invokevirtual #15  // Method PrintStream.println:(Ljava/lang/String;)V
  8: return
```

---

## 4. Step 3 — The `.class` File Structure

A `.class` file is a binary format defined by the JVM Specification:

```
ClassFile {
  magic number          0xCAFEBABE  (yes, really — Java was a coffee brand)
  minor_version         0
  major_version         65          (65 = Java 21)
  constant_pool_count   N
  constant_pool[N-1]    (strings, class names, method descriptors, ...)
  access_flags          ACC_PUBLIC | ACC_SUPER
  this_class            index into constant pool
  super_class           index into constant pool
  interfaces[...]
  fields[...]
  methods[...]          (bytecode for each method)
  attributes[...]       (source file, line numbers, local vars, ...)
}
```

View a class file with:
```bash
javap -c -verbose HelloWorld.class
```

---

## 5. Step 4 — Class Loading

When the JVM encounters a class for the first time, the ClassLoader subsystem loads it.

### 5.1 Three built-in ClassLoaders

```
Bootstrap ClassLoader  (C code, native — not a Java class)
    Loads: java.lang.*, java.util.*, rt.jar / jdk.base module
               │
               ▼
Platform ClassLoader  (was Extension CL in Java 8)
    Loads: JDK modules not in boot module (java.sql, java.xml, ...)
               │
               ▼
Application ClassLoader  (System CL)
    Loads: your application classes from -classpath / --module-path
               │
               ▼
Custom ClassLoader  (optional, user-defined)
    Loads: plugins, hot-reload classes, OSGi bundles
```

### 5.2 Delegation model (parent-first)

When class `Foo` is first referenced:
1. Application CL asks Parent (Platform CL): "do you have `Foo`?"
2. Platform CL asks Parent (Bootstrap CL): "do you have `Foo`?"
3. Bootstrap CL looks in boot modules — not found.
4. Platform CL looks in platform modules — not found.
5. Application CL looks on classpath — **found** → loads it.

**Why parent-first?** Prevents malicious code from replacing `java.lang.String` with a custom version (Bootstrap CL loads it first, Application CL's version is ignored).

### 5.3 Class Loading Phases

```
Loading → Linking → Initialization
              │
    ┌─────────┼──────────┐
  Verify   Prepare    Resolve
```

| Phase | What happens |
|---|---|
| **Loading** | Read `.class` bytes from disk/jar/network. Create `java.lang.Class` object in Metaspace. |
| **Verification** | Check bytecode is syntactically correct, doesn't overflow stack, no illegal type casts. Security gate. |
| **Preparation** | Allocate memory for static fields. Set to defaults (`0`, `null`, `false`). |
| **Resolution** | Replace symbolic references (class names, method descriptors) with direct memory pointers. |
| **Initialization** | Run static initializers and assign static fields their declared values. Happens the first time the class is actively used. |

```java
class Example {
    static int x = 10;            // Preparation: x = 0; Initialization: x = 10
    static {
        System.out.println("Class initialized!"); // runs at Initialization
    }
}
```

---

## 6. Step 5 — Bytecode Verification

Before executing any bytecode, the JVM's **Bytecode Verifier** checks:

- **Type safety**: operations on the operand stack match the expected types.
- **Stack depth**: no stack underflow or overflow within a method.
- **Branch targets**: jumps land on valid instruction boundaries.
- **Object initialization**: local variables are set before use.
- **Method access**: no invoking private methods of another class.

This is a critical security layer — it ensures that even a maliciously crafted `.class` file cannot corrupt JVM internals.

---

## 7. Step 6 — The Execution Engine

### 7.1 Interpreter (Tier 0)

The first time any method runs, the JVM **interprets** bytecode instruction by instruction:

```
bytecode:  0: iload_1 (push local var 1 onto operand stack)
           1: iload_2 (push local var 2)
           2: iadd   (pop two ints, push their sum)
           3: ireturn (pop and return the int)
```

Interpretation is straightforward but ~10–50× slower than native code.

### 7.2 JIT Compilation — Tiered Compilation

HotSpot uses **tiered compilation** with 5 levels:

```
Tier 0:  Interpreter
  ↓ method called > 1,500 times OR loop iterated > 10,000 times
Tier 1:  C1 — quick compile, no profiling
  ↓ still hot
Tier 2:  C1 — with limited profiling
  ↓ still hot
Tier 3:  C1 — with full profiling (branch frequencies, receiver types)
  ↓ fully profiled + hot
Tier 4:  C2 — aggressive optimization using profile data
```

### 7.3 JIT Optimizations

**Inlining** (most impactful):
```java
// Source
int result = add(a, b);          // method call

// After inlining — no call overhead
int result = a + b;              // body substituted directly
```

**Escape Analysis**:
```java
Point p = new Point(x, y);       // allocated on heap normally
return p.distanceTo(origin);     // p never leaves this method

// JIT detects p "doesn't escape" → stack alloc or scalar replace
// After optimization:
return Math.sqrt((x-ox)*(x-ox) + (y-oy)*(y-oy)); // no object at all!
```

**Loop Unrolling**:
```java
// Source
for (int i = 0; i < 4; i++) sum += a[i];

// After unrolling (JIT removes loop overhead)
sum += a[0]; sum += a[1]; sum += a[2]; sum += a[3];
```

**Dead code elimination**:
```java
// Source
if (false) { expensiveOperation(); }

// After DCE — entire branch removed
```

**Devirtualization**:
```java
// Source
animal.speak(); // virtual call — must check actual type at runtime

// If JIT's profile shows it's always a Dog:
Dog.speak();    // direct call — much faster
```

### 7.4 Code Cache

JIT-compiled native code is stored in the **Code Cache** (heap memory, but not GC'd).

```
Default Code Cache size: 240–512 MB
If it fills: "CodeCache is full. Compiler has been disabled."
Performance degrades back to interpreter speed.
Fix: -XX:ReservedCodeCacheSize=512m
```

---

## 8. Step 7 — Memory Management (Runtime Heap)

When objects are created during execution:

```java
Order order = new Order("o-1", 99.99);
```

1. JVM allocates space in **Eden** (Young Generation heap) via TLAB (Thread-Local Allocation Buffer) — O(1) bump pointer, no locking.
2. Object fields are initialized.
3. Reference `order` (a stack variable) points to the heap object.
4. When `order` goes out of scope → no more strong references → object is eligible for GC.
5. At next Minor GC: Eden swept, live objects copied to Survivor space.
6. After N Minor GCs (default 15): object promoted to Old Gen.
7. Old Gen fills → Major GC / G1 Mixed GC.

---

## 9. Step 8 — The main() Entry Point

When you run `java HelloWorld`:

```
1. JVM starts
2. Bootstrap CL loads java.lang.*
3. Application CL loads HelloWorld
4. JVM finds: public static void main(String[] args)
             ↑         ↑     ↑            ↑
             required must be  must be    argument type required
             for entry static  main()     (can pass args from CLI)
5. A new stack frame is pushed for main()
6. main() executes
7. main() returns → JVM calls shutdown hooks → exits
```

**Why must main() be `static`?** Because the JVM needs to call it without creating an instance of your class first — there's no object to call an instance method on yet.

---

## 10. Step 9 — Stack Frame Structure

Each method call pushes a **stack frame** onto the thread's stack:

```
Thread Stack
│
├── Frame: main(String[])
│     ├── Local variable table:  args → String[]
│     ├── Operand stack:         (used for intermediate computations)
│     └── Reference to constant pool
│
├── Frame: calculate(int, int)      ← called from main
│     ├── Local variable table:  a → int, b → int, result → int
│     ├── Operand stack:         temp values during bytecode execution
│     └── Reference to constant pool
│
└── Frame: Math.addExact(int, int)  ← called from calculate
      ├── Local variable table: x → int, y → int
      ├── Operand stack:
      └── ...
```

- On method **call** → new frame pushed.
- On method **return** → frame popped, return value passed to caller's operand stack.
- `StackOverflowError` = too many nested calls (stack is full).

---

## 11. Step 10 — Thread Execution Model

```
Main Thread                          GC Thread (concurrent)
──────────────────────────────────   ──────────────────────
main() starts                        G1GC concurrent marking
→ creates OrderService               → marks live objects
→ creates ThreadPoolExecutor         → concurrent sweeping
→ submits tasks                      (runs alongside app threads)
  → Worker Thread 1: task A          → triggers STW pause for
  → Worker Thread 2: task B            final remark + evacuation
                                     → Old Gen compacted
```

**Stop-The-World (STW) pause**: JVM pauses ALL application threads briefly to:
- Copy live objects (Minor GC / G1 Young GC).
- Perform final concurrent marking reference updates.
Target: G1GC keeps STW pauses < 200ms.

---

## 12. End-to-End Trace: One HTTP Request

```
Browser sends GET /api/orders/123
        │
        ▼
OS receives TCP packet → passes to JVM's Network I/O (NIO selector)
        │
        ▼
Spring's DispatcherServlet.doDispatch()
        │  (already JIT-compiled to native — this runs fast)
        ▼
OrderController.getOrder(UUID id)     ← your code
   │  1. Validate UUID (method call → inlined)
   │  2. orderService.findById(id)    ← call
   │     │  3. Hit CaffeineCache first (in-process)
   │     │  4. Cache miss: JPA query → JDBC → PostgreSQL (network I/O)
   │     │     → virtual thread unmounts here, carrier thread free for others
   │     │  5. ResultSet → Hibernate maps to Order entity (reflection / MethodHandle)
   │     │  6. Return Order
   │  7. Map to OrderResponse (record → constructor)
   │  8. Jackson serializes to JSON bytes
   │  9. Write bytes to HTTP response buffer
   │ 10. NIO sends bytes back over TCP
        │
        ▼
Browser receives JSON response

Total work done:
  → ~50 method invocations (many inlined by JIT)
  → ~3 object allocations on Eden (TLAB bump pointer)
  → 1 I/O wait (virtual thread unmounted)
  → GC untouched (objects short-lived, collected at next Minor GC)
```

---

## 13. Bytecode Instruction Set (Quick Reference)

| Category | Instructions | Meaning |
|---|---|---|
| Load locals | `iload`, `lload`, `fload`, `dload`, `aload` | Push int/long/float/double/reference onto operand stack |
| Store locals | `istore`, `lstore`, `fstore`, `dstore`, `astore` | Pop operand stack to local variable |
| Arithmetic | `iadd`, `isub`, `imul`, `idiv`, `irem` | Integer arithmetic |
| Comparison | `if_icmpeq`, `if_icmplt`, `if_icmpge` | Compare two ints, branch |
| Method calls | `invokevirtual`, `invokestatic`, `invokeinterface`, `invokedynamic` | Call methods |
| Object creation | `new`, `newarray`, `anewarray` | Allocate object/array |
| Field access | `getfield`, `putfield`, `getstatic`, `putstatic` | Read/write fields |
| Return | `ireturn`, `lreturn`, `freturn`, `dreturn`, `areturn`, `return` | Return value |

```bash
# View bytecode of your class
javap -c MyClass.class

# Include line numbers and local variable table
javap -c -l -verbose MyClass.class
```

---

## 14. Interview Q&A

**Q: What happens when you run `java HelloWorld`?**  
The JVM starts, Bootstrap ClassLoader loads `java.lang.*`, Application ClassLoader loads `HelloWorld.class` from the classpath, the class loading pipeline (load → verify → prepare → resolve → initialize) runs, the main thread's stack frame for `main(String[])` is created, and execution begins. The JIT interpreter starts interpreting bytecode; after enough invocations, the JIT compiler (C2) compiles hot methods to native code stored in the Code Cache.

**Q: What is the difference between `invokevirtual` and `invokestatic`?**  
`invokestatic` calls a static method — no receiver object, resolved at compile time, no virtual dispatch. `invokevirtual` calls an instance method — a receiver object reference is on the stack; at runtime the JVM looks up the actual implementation based on the object's type (virtual dispatch / polymorphism). `invokestatic` is faster because it requires no vtable lookup.

**Q: What is `invokedynamic` and how do lambdas use it?**  
`invokedynamic` (Java 7) is a bytecode instruction that defers method resolution to runtime via a "bootstrap method." Lambdas are compiled to `invokedynamic` calls — the first time the lambda site is hit, the bootstrap method (`LambdaMetafactory`) generates a class implementing the functional interface on the fly using `MethodHandles`. Subsequent calls use the cached class. This is more efficient than the old anonymous inner class approach and supports future optimizations.

**Q: How does the JVM achieve better performance over time while a program is running?**  
Through JIT tiered compilation with profile-guided optimization. The interpreter collects profiling data (which branches are taken, which types appear at a call site). Once a method is "hot" (many invocations), the C2 JIT compiler uses that profile to generate aggressively optimized native code: inlining small methods (eliminating call overhead), devirtualizing polymorphic calls when one type dominates, performing escape analysis to stack-allocate short-lived objects, and unrolling loops. The longer the program runs, the better the JIT's profile → the more optimized the native code.
