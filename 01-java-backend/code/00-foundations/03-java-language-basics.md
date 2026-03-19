# Java Language Basics — Types, Variables, Operators, Control Flow, Methods

---

## 1. Data Types

Java has two categories of types: **primitives** and **reference types**.

### 1.1 Primitive Types

Eight primitives — stored directly in the variable (on the stack if local, inline in the object if a field):

| Type | Size | Default | Range | Example |
|---|---|---|---|---|
| `byte` | 1 byte | 0 | -128 to 127 | `byte b = 42;` |
| `short` | 2 bytes | 0 | -32,768 to 32,767 | `short s = 1000;` |
| `int` | 4 bytes | 0 | -2³¹ to 2³¹-1 (~2.1 billion) | `int x = 100;` |
| `long` | 8 bytes | 0L | -2⁶³ to 2⁶³-1 | `long l = 100L;` |
| `float` | 4 bytes | 0.0f | ±3.4×10³⁸ | `float f = 3.14f;` |
| `double` | 8 bytes | 0.0d | ±1.8×10³⁰⁸ | `double d = 3.14;` |
| `char` | 2 bytes | '\u0000' | 0 to 65,535 (Unicode BMP) | `char c = 'A';` |
| `boolean` | 1 bit | false | true / false | `boolean flag = true;` |

```java
int x = 2_000_000;          // underscores allowed for readability (Java 7+)
long bigNum = 9_000_000_000L; // L suffix required for long literals
double pi = 3.141_592_653;
int hex = 0xFF;              // hexadecimal
int bin = 0b1010_1010;      // binary (Java 7+)
```

### 1.2 Integer Arithmetic Pitfalls

```java
int a = Integer.MAX_VALUE; // 2147483647
int b = a + 1;             // overflow → -2147483648 (wraps silently!)
// FIX: use long or Math.addExact()
long safe = (long) a + 1;
Math.addExact(a, 1);       // throws ArithmeticException on overflow

// Integer division truncates toward zero
int result = 7 / 2;        // 3, NOT 3.5
double exact = 7.0 / 2;   // 3.5
double exact2 = (double) 7 / 2; // 3.5 (cast one operand before division)
```

### 1.3 Floating-Point Pitfalls

```java
// Floating-point is APPROXIMATE (IEEE 754)
System.out.println(0.1 + 0.2);      // 0.30000000000000004
System.out.println(0.1 + 0.2 == 0.3); // false!

// FIX 1: compare with epsilon
double epsilon = 1e-10;
Math.abs(a - b) < epsilon

// FIX 2: use BigDecimal for money
BigDecimal price = new BigDecimal("0.10");
BigDecimal tax = new BigDecimal("0.20");
BigDecimal total = price.add(tax); // exactly 0.30
```

### 1.4 Reference Types

Everything else — objects, arrays, strings:

```java
String name = "Alice";            // reference to a String object on the heap
int[] numbers = {1, 2, 3};       // reference to an int array
List<String> list = new ArrayList<>(); // reference to an ArrayList
```

A reference variable holds a **memory address** (pointer) to the object on the heap.
`null` means the reference points to nothing.

```java
String s = null;
s.length();       // NullPointerException — no object to call method on
```

---

## 2. Variables

### 2.1 Variable Categories

| Category | Where declared | Lifetime | Default value |
|---|---|---|---|
| **Local variable** | Inside a method/block | Until block ends | No default — must initialize before use |
| **Instance field** | In a class (not static) | Tied to the object | Type default (0, null, false) |
| **Static field** | In a class with `static` | Class lifetime | Type default |
| **Parameter** | Method signature | During method call | Passed by caller |

```java
class Counter {
    private int count = 0;         // instance field (each Counter has its own)
    private static int total = 0;  // static field (shared by ALL Counter instances)

    public void increment() {
        int step = 1;              // local variable
        count += step;
        total += step;
    }
}
```

### 2.2 `var` — Local Variable Type Inference (Java 10+)

```java
var list = new ArrayList<String>();   // inferred as ArrayList<String>
var map = new HashMap<String, List<Integer>>();
var name = "Alice";                   // inferred as String

// var is NOT dynamic typing — the type is fixed at compile time
var x = 42;
x = "hello"; // COMPILE ERROR — x is int, not String

// var only works for LOCAL variables — NOT for fields or method params
```

---

## 3. Type Casting

### Widening (automatic — no data loss)

```
byte → short → int → long → float → double
                char → int
```

```java
int i = 42;
long l = i;       // implicit widening
double d = i;     // implicit widening
float f = i;      // implicit widening (precision warning: float has only ~7 sig digits)
```

### Narrowing (explicit cast — may lose data)

```java
double d = 3.99;
int i = (int) d;   // truncates to 3 (not rounded!)

long l = 1_000_000_000_000L;
int x = (int) l;   // silently truncates high bits — lossy!
```

### Object casting

```java
Object obj = "hello";          // String is an Object (upcasting — always safe)
String s = (String) obj;       // downcasting — must be sure at runtime
String fail = (String) new Integer(5); // ClassCastException at runtime

// Safe cast with instanceof
if (obj instanceof String str) { // pattern matching cast (Java 16+)
    System.out.println(str.length()); // str is already a String here
}
```

---

## 4. Operators

### 4.1 Arithmetic

```java
int a = 10, b = 3;
a + b   // 13
a - b   // 7
a * b   // 30
a / b   // 3  (integer division)
a % b   // 1  (remainder/modulo)
```

### 4.2 Comparison

```java
a == b     // false  (equality — for primitives compares value; for objects compares REFERENCE)
a != b     // true
a > b      // true
a < b      // false
a >= b     // true
a <= b     // false

// WRONG for objects:
String s1 = new String("hello");
String s2 = new String("hello");
s1 == s2        // false — different references!
s1.equals(s2)   // true — compares content
```

### 4.3 Logical

```java
boolean x = true, y = false;
x && y    // false  (AND — short-circuit: if x is false, y is NOT evaluated)
x || y    // true   (OR  — short-circuit: if x is true, y is NOT evaluated)
!x        // false  (NOT)

// Short-circuit importance:
if (list != null && list.size() > 0) { ... } // safe: won't call size() if list is null
```

### 4.4 Bitwise

```java
int a = 0b1010; // 10
int b = 0b1100; // 12
a & b   // 0b1000 = 8   (AND)
a | b   // 0b1110 = 14  (OR)
a ^ b   // 0b0110 = 6   (XOR)
~a      // 0b...10101   (NOT — bitwise complement)
a << 1  // 0b10100 = 20 (left shift = multiply by 2)
a >> 1  // 0b0101  = 5  (right shift = divide by 2, sign-preserving)
a >>> 1 // unsigned right shift (fills with 0, not sign bit)
```

### 4.5 Assignment Shortcuts

```java
x += 5;   // x = x + 5
x -= 5;   // x = x - 5
x *= 2;   // x = x * 2
x /= 2;   // x = x / 2
x %= 3;   // x = x % 3
x++;      // post-increment: use x, then x = x + 1
++x;      // pre-increment: x = x + 1, then use x
```

### 4.6 Ternary

```java
String label = (score >= 60) ? "PASS" : "FAIL";
// equivalent to:
String label;
if (score >= 60) label = "PASS";
else             label = "FAIL";
```

---

## 5. Control Flow

### 5.1 `if / else if / else`

```java
if (score >= 90) {
    grade = "A";
} else if (score >= 80) {
    grade = "B";
} else if (score >= 70) {
    grade = "C";
} else {
    grade = "F";
}
```

### 5.2 `switch` Statement (classic)

```java
switch (day) {
    case "MON":
    case "TUE":
    case "WED":
    case "THU":
    case "FRI":
        System.out.println("Weekday");
        break;           // break is REQUIRED — without it, falls through!
    case "SAT":
    case "SUN":
        System.out.println("Weekend");
        break;
    default:
        System.out.println("Unknown");
}
```

### 5.3 `switch` Expression (Java 14+) — preferred

```java
// No fall-through, no break, can be used as an expression
String type = switch (day) {
    case "MON", "TUE", "WED", "THU", "FRI" -> "Weekday";
    case "SAT", "SUN"                       -> "Weekend";
    default -> throw new IllegalArgumentException("Unknown: " + day);
};

// With a block:
int result = switch (operation) {
    case "add" -> {
        int sum = a + b;
        System.out.println("Computing sum");
        yield sum;      // yield returns value from block
    }
    case "multiply" -> a * b;
    default -> throw new UnsupportedOperationException();
};
```

### 5.4 `for` Loop

```java
// Classic for loop
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}

// Enhanced for-each (any Iterable)
for (String name : names) {
    System.out.println(name);
}

// Iterating with index using IntStream (Java 8+)
IntStream.range(0, names.size())
    .forEach(i -> System.out.println(i + ": " + names.get(i)));
```

### 5.5 `while` and `do-while`

```java
// while: check condition BEFORE each iteration (may execute 0 times)
while (line != null) {
    process(line);
    line = reader.readLine();
}

// do-while: check condition AFTER each iteration (executes at least once)
do {
    input = scanner.nextLine();
} while (input.isEmpty());
```

### 5.6 `break`, `continue`, labeled break

```java
// break — exit innermost loop
for (int i = 0; i < 10; i++) {
    if (i == 5) break; // exits the for loop
}

// continue — skip rest of current iteration
for (int i = 0; i < 10; i++) {
    if (i % 2 == 0) continue; // skip even numbers
    System.out.println(i);
}

// Labeled break — exit OUTER loop from inner loop
outer:
for (int i = 0; i < 5; i++) {
    for (int j = 0; j < 5; j++) {
        if (i == 2 && j == 3) break outer; // breaks out of BOTH loops
    }
}
```

---

## 6. Methods

### 6.1 Method Structure

```java
// access modifier  return type  name     parameters
public            static int   add(     int a, int b) {
    return a + b;
}
```

### 6.2 Method Overloading

Same name, different parameter types or count:

```java
class Printer {
    void print(int value) { System.out.println(value); }
    void print(double value) { System.out.println(value); }
    void print(String value) { System.out.println(value); }
    void print(String value, int times) { ... }
}
// Resolved at COMPILE TIME based on argument types
```

### 6.3 Varargs

```java
// Accepts zero or more int arguments — internally an int[]
public int sum(int... numbers) {
    int total = 0;
    for (int n : numbers) total += n;
    return total;
}
sum();          // numbers = []
sum(1);         // numbers = [1]
sum(1, 2, 3);   // numbers = [1, 2, 3]
int[] arr = {1, 2, 3};
sum(arr);       // can also pass an array

// Varargs must be the LAST parameter
public void log(String level, Object... args) { ... }
```

### 6.4 Pass By Value

**Java is always pass-by-value.** For objects, the value is the *reference* (pointer):

```java
void tryToChange(int x) {
    x = 99; // changes local copy only
}
int n = 5;
tryToChange(n);
System.out.println(n); // still 5

void mutate(List<String> list) {
    list.add("hello"); // changes the OBJECT that list points to
    list = new ArrayList<>(); // rebinds local ref — caller's ref unchanged
}
List<String> myList = new ArrayList<>();
mutate(myList);
System.out.println(myList); // [hello] — object was mutated, but reference unchanged
```

### 6.5 Recursion

```java
// Factorial — each call adds a stack frame
int factorial(int n) {
    if (n <= 1) return 1;   // base case
    return n * factorial(n - 1); // recursive case
}

// Fibonacci (naive — O(2^n), exponential)
int fib(int n) {
    if (n <= 1) return n;
    return fib(n - 1) + fib(n - 2);
}

// Fibonacci (memoized — O(n))
Map<Integer, Long> memo = new HashMap<>();
long fibMemo(int n) {
    if (n <= 1) return n;
    return memo.computeIfAbsent(n, k -> fibMemo(k - 1) + fibMemo(k - 2));
}
```

---

## 7. Arrays

```java
// Declaration and initialization
int[] arr = new int[5];           // [0, 0, 0, 0, 0]
int[] arr2 = {10, 20, 30, 40, 50};
int[][] matrix = new int[3][4];   // 3 rows, 4 columns
int[][] matrix2 = {{1,2},{3,4},{5,6}};

// Access (0-based index)
arr[0] = 100;
int last = arr[arr.length - 1]; // arr.length is fixed (NOT a method, no parentheses)

// Copy
int[] copy = Arrays.copyOf(arr, arr.length);
int[] slice = Arrays.copyOfRange(arr, 1, 4); // [1, 4)

// Sort
Arrays.sort(arr);                                   // ascending
Arrays.sort(arr2, Comparator.reverseOrder());       // descending (requires Integer[])

// Search (must be sorted first)
int index = Arrays.binarySearch(arr, 30);

// Fill
Arrays.fill(arr, -1); // all elements = -1

// Convert to List (FIXED SIZE — can't add/remove)
List<Integer> list = Arrays.asList(1, 2, 3);

// Convert to modifiable List
List<Integer> mutableList = new ArrayList<>(Arrays.asList(1, 2, 3));
```

### Multi-dimensional arrays

```java
int[][] grid = new int[3][3];
for (int row = 0; row < grid.length; row++) {
    for (int col = 0; col < grid[row].length; col++) {
        grid[row][col] = row * 3 + col;
    }
}
// Jagged array (rows of different lengths)
int[][] jagged = new int[3][];
jagged[0] = new int[2];
jagged[1] = new int[5];
jagged[2] = new int[1];
```

---

## 8. Strings

Strings are **immutable** in Java — every operation returns a NEW String.

```java
String s = "Hello";
s.toUpperCase();        // returns "HELLO" — s is UNCHANGED
System.out.println(s);  // "Hello"
s = s.toUpperCase();    // now s references the new String

// Commonly used String methods
s.length()               // 5
s.charAt(1)              // 'e'
s.indexOf('l')           // 2 (first occurrence)
s.lastIndexOf('l')       // 3
s.substring(1)           // "ello"
s.substring(1, 3)        // "el" (end exclusive)
s.contains("ell")        // true
s.startsWith("He")       // true
s.endsWith("lo")         // true
s.replace("l", "r")      // "Herro"
s.replaceAll("[aeiou]", "*") // regex replace
s.trim()                 // removes leading/trailing whitespace
s.strip()                // like trim, but handles Unicode spaces (Java 11+)
s.split(",")             // split by comma → String[]
s.toCharArray()          // "Hello" → ['H','e','l','l','o']
s.isEmpty()              // length == 0
s.isBlank()              // all whitespace (Java 11+)
String.valueOf(42)       // "42" (int to String)
Integer.parseInt("42")   // 42 (String to int)
```

### String Pool (interning)

```java
String a = "hello";           // from String pool (compile-time constant)
String b = "hello";           // same pool entry
String c = new String("hello"); // forces new object on heap

a == b        // true  — same pool reference
a == c        // false — different objects
a.equals(c)   // true  — same content

// String.intern() forces into pool
String d = c.intern();
a == d  // true
```

### StringBuilder — for string building in a loop

```java
// BAD: O(n²) — each += creates a new String
String result = "";
for (int i = 0; i < 1000; i++) result += i;

// GOOD: O(n) — single buffer, no copies
StringBuilder sb = new StringBuilder(4000); // pre-size to avoid resize
for (int i = 0; i < 1000; i++) sb.append(i);
String result = sb.toString();

// String.join — cleanest for known set of strings
String csv = String.join(", ", "Alice", "Bob", "Charlie");

// Collectors.joining in streams
String names = list.stream().collect(Collectors.joining(", ", "[", "]"));
```

### Text Blocks (Java 15+)

```java
String json = """
        {
            "name": "Alice",
            "age": 30
        }
        """;
// Leading whitespace stripped by common indent algorithm
// No need to escape quotes inside
```

---

## 9. Exceptions

### 9.1 Exception Hierarchy

```
Throwable
├── Error             (JVM errors — don't catch: OutOfMemoryError, StackOverflowError)
└── Exception
    ├── RuntimeException  (unchecked — not required to declare/catch)
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   ├── IndexOutOfBoundsException
    │   ├── ClassCastException
    │   └── ArithmeticException
    └── Checked Exception  (must declare with throws OR catch)
        ├── IOException
        ├── SQLException
        └── InterruptedException
```

### 9.2 try/catch/finally

```java
try {
    int result = 10 / 0;          // ArithmeticException
    String s = null;
    s.length();                   // NullPointerException
} catch (ArithmeticException e) {
    System.out.println("Divide by zero: " + e.getMessage());
} catch (NullPointerException e) {
    System.out.println("Null pointer");
} catch (Exception e) {           // catch-all (catches any Exception)
    e.printStackTrace();
} finally {
    System.out.println("Always runs — cleanup here");
}

// Multi-catch (Java 7+)
} catch (IOException | SQLException e) {
    log.error("IO or DB error", e);
}
```

### 9.3 try-with-resources (Java 7+)

```java
// Any AutoCloseable is automatically closed when block exits
try (
    InputStream in = new FileInputStream("input.txt");
    OutputStream out = new FileOutputStream("output.txt")
) {
    // use in and out
} // in.close() and out.close() called automatically, even on exception
// Equivalent to: try { ... } finally { in.close(); out.close(); }
```

### 9.4 Custom Exceptions

```java
public class OrderNotFoundException extends RuntimeException {
    private final UUID orderId;

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
        this.orderId = orderId;
    }

    public UUID getOrderId() { return orderId; }
}
```

**Rule**: Extend `RuntimeException` for application-logic failures (no caller needs to catch). Extend checked `Exception` only when the caller is EXPECTED to handle it (e.g., `FileNotFoundException`).

---

## 10. Enums

```java
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;
}

// Enums can have fields and methods
public enum Planet {
    MERCURY(3.303e+23, 2.4397e6),
    VENUS(4.869e+24, 6.0518e6),
    EARTH(5.976e+24, 6.37814e6);

    private final double mass;    // kg
    private final double radius;  // meters

    Planet(double mass, double radius) {
        this.mass = mass;
        this.radius = radius;
    }

    public double surfaceGravity() {
        final double G = 6.67300E-11;
        return G * mass / (radius * radius);
    }
}

// Usage
OrderStatus status = OrderStatus.CONFIRMED;
status.name()        // "CONFIRMED"
status.ordinal()     // 1 (position in declaration)
OrderStatus.valueOf("PENDING")    // OrderStatus.PENDING
OrderStatus.values()              // all values as array

// In switch
switch (status) {
    case PENDING   -> startProcessing();
    case CANCELLED -> refund();
    default        -> doNothing();
}
```

---

## 11. Interview Q&A

**Q: What is the difference between `==` and `.equals()` for Strings?**  
`==` compares references (memory addresses). Two `String` variables are `==` only if they point to the exact same object. `.equals()` compares content character by character. String literals are interned (pooled), so `"hello" == "hello"` is true (same pool entry), but `new String("hello") == new String("hello")` is false (different heap objects). Always use `.equals()` to compare String values.

**Q: Why is String immutable in Java?**  
Three reasons: (1) Thread safety — immutable objects can be freely shared between threads without synchronization. (2) Security — class names, file paths, network addresses passed as Strings can't be modified by malicious code after validation. (3) String pool — since Strings can't change, they can be safely cached in a pool; multiple references can point to the same String object safely.

**Q: Explain checked vs unchecked exceptions.**  
Checked exceptions extend `Exception` (but not `RuntimeException`) — the compiler requires you to either declare them with `throws` or catch them. They model expected failure scenarios (file not found, network timeout). Unchecked exceptions extend `RuntimeException` — no compile-time requirement. They model programming errors (null pointer, array index out of bounds, illegal argument). Controversy: checked exceptions add verbosity and can leak implementation details; many modern APIs (Spring) prefer unchecked wrappers.

**Q: What is integer overflow and how do you prevent it?**  
`int` is 32 bits; it wraps silently on overflow. `Integer.MAX_VALUE + 1` gives `Integer.MIN_VALUE` with no exception. Prevention: (1) Use `long` if values may exceed 2 billion. (2) Use `Math.addExact()`, `Math.multiplyExact()` which throw `ArithmeticException` on overflow. (3) Use `BigInteger` for truly arbitrary values.
