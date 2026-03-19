# OOP Concepts in Java — Classes, Inheritance, Polymorphism, Interfaces, Generics

---

## 1. Classes and Objects

A **class** is a blueprint. An **object** is an instance — a concrete thing created from that blueprint, living on the heap.

```java
// Class definition
public class BankAccount {
    // === FIELDS (state) ===
    private final String id;         // final field: set once, never changed
    private final String owner;
    private double balance;          // mutable field

    // === CONSTRUCTOR ===
    public BankAccount(String id, String owner, double initialBalance) {
        if (initialBalance < 0) throw new IllegalArgumentException("Balance can't be negative");
        this.id = id;
        this.owner = owner;
        this.balance = initialBalance;
    }

    // === METHODS (behavior) ===
    public void deposit(double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balance += amount;
    }

    public void withdraw(double amount) {
        if (amount > balance) throw new IllegalStateException("Insufficient funds");
        this.balance -= amount;
    }

    public double getBalance() { return balance; }
    public String getId()      { return id; }

    // === toString ===
    @Override
    public String toString() {
        return "BankAccount[id=%s, owner=%s, balance=%.2f]".formatted(id, owner, balance);
    }
}

// Creating objects
BankAccount account = new BankAccount("ACC-001", "Alice", 1000.00);
account.deposit(500);
System.out.println(account.getBalance()); // 1500.0
System.out.println(account);             // BankAccount[id=ACC-001, owner=Alice, balance=1500.00]
```

### Memory layout

```
Stack frame                  Heap
┌─────────────┐              ┌─────────────────────────────────────┐
│  account    │──────────────▶  BankAccount object                 │
│  (reference)│              │  id = "ACC-001"                     │
└─────────────┘              │  owner = "Alice"                    │
                             │  balance = 1500.0                   │
                             └─────────────────────────────────────┘
```

---

## 2. Constructors

```java
public class Point {
    private final double x;
    private final double y;

    // Parameterized constructor
    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // Convenience constructor — delegates using this(...)
    public Point(double x) {
        this(x, 0.0);   // must be FIRST statement
    }

    // No-arg — delegates to parameterized
    public Point() {
        this(0.0, 0.0);
    }

    // Copy constructor
    public Point(Point other) {
        this(other.x, other.y);
    }
}

// If you write NO constructor, Java generates a default no-arg constructor
// If you write ANY constructor, the default is NOT generated
```

---

## 3. Access Modifiers

| Modifier | Same class | Same package | Subclass (any pkg) | Other package |
|---|---|---|---|---|
| `public` | ✅ | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| *(default/package)* | ✅ | ✅ | ❌ | ❌ |
| `private` | ✅ | ❌ | ❌ | ❌ |

Best practice: fields should be `private`; expose behavior through public methods.

---

## 4. Encapsulation

```java
// BAD: public field — any code can set it to illegal values
public double salary;

// GOOD: private field + controlled access
public class Employee {
    private double salary;

    public double getSalary() { return salary; }
    
    public void setSalary(double salary) {
        if (salary < 0) throw new IllegalArgumentException("Salary cannot be negative");
        if (salary > 10_000_000) throw new IllegalArgumentException("Exceeds max salary");
        this.salary = salary;
    }
}
```

**Why encapsulate?**
1. **Invariant enforcement** — guarantee object is always in a valid state
2. **Flexibility to change internals** — callers only see the public API
3. **Controlled mutation** — validated writes prevent illegal state

---

## 5. `static` Keyword

```java
public class Counter {
    private static int totalInstances = 0;  // ONE value shared across ALL Counter objects
    private final int id;

    public Counter() {
        totalInstances++;          // access static field from constructor
        this.id = totalInstances;
    }

    public static int getTotalInstances() { // static method: no 'this', no instance needed
        return totalInstances;     // can access only static fields
    }

    // Static initializer block — runs once when class is first loaded
    static {
        System.out.println("Counter class loaded");
    }
}

Counter.getTotalInstances(); // call on class, not instance
```

### Static vs Instance

| | Static | Instance |
|---|---|---|
| Belongs to | Class | Object |
| Memory location | Metaspace | Heap (per object) |
| Access via | `ClassName.member` or `this` | `this.member` |
| Can access instance members? | ❌ No | ✅ Yes |

---

## 6. `final` Keyword

```java
// final VARIABLE — value set once, never reassigned
final int MAX = 100;
MAX = 200; // compile error

// final FIELD — must be set in constructor (or field declaration)
public class Token {
    private final String value;
    public Token(String value) { this.value = value; } // OK
}

// final PARAMETER — can't reassign param inside method
public void process(final String input) {
    input = "other"; // compile error
}

// final METHOD — subclasses cannot override it
public class Base {
    public final double computeFee() { return 0.02; } // locked
}

// final CLASS — cannot be extended (IMMUTABLE design pattern)
public final class String { ... }  // java.lang.String is final
public final class Integer { ... } // all wrapper types are final
```

---

## 7. Inheritance

### `extends` — single inheritance

```java
// Base class
public class Animal {
    private final String name;

    public Animal(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public void makeSound() {
        System.out.println(name + " makes a sound");
    }
}

// Subclass — inherits ALL non-private members
public class Dog extends Animal {
    private final String breed;

    public Dog(String name, String breed) {
        super(name); // MUST call super constructor first if parent has no default
        this.breed = breed;
    }

    @Override
    public void makeSound() {           // OVERRIDING parent method
        System.out.println(getName() + " barks");
    }

    public String getBreed() { return breed; }
}

// Usage
Animal a = new Dog("Rex", "Labrador");  // upcasting — Dog IS-A Animal
a.makeSound();   // prints "Rex barks" — Dog's version (runtime polymorphism!)
// a.getBreed() — COMPILE ERROR: Animal type doesn't know about getBreed
Dog d = (Dog) a; // downcast back — safe because a actually IS a Dog
d.getBreed();    // now accessible
```

### Method Overriding Rules

```java
@Override // annotation — tells compiler to verify this is actually an override
public void makeSound() { ... } // same name, same param types, same/covariant return type

// Covariant return type — subclass can return more specific type
class Animal { Animal clone() { ... } }
class Dog extends Animal { 
    @Override Dog clone() { ... } // Return type Dog (subtype of Animal) — allowed
}

// Can't override:
// - private methods (not inherited)
// - static methods (hidden, not overridden — "static dispatch")
// - final methods
```

### `super` keyword

```java
public class SavingsAccount extends BankAccount {
    private double interestRate;

    public SavingsAccount(String id, String owner, double balance, double rate) {
        super(id, owner, balance);   // call parent constructor
        this.interestRate = rate;
    }

    @Override
    public void deposit(double amount) {
        super.deposit(amount);       // call parent's version
        System.out.println("Deposit recorded in savings log");
    }
}
```

### Inheritance gotchas

```java
// Constructor chaining goes UP: most specific first
// new SavingsAccount() → SavingsAccount() → BankAccount() → Object()

// Don't call overridable methods from constructors!
class Base {
    Base() {
        init(); // DANGEROUS — calls overridden version in subclass
    }
    void init() { System.out.println("Base.init"); }
}
class Sub extends Base {
    private int value = 42;
    @Override void init() { System.out.println("Sub.init: " + value); } // value is 0 here!!
}
new Sub(); // prints "Sub.init: 0" — value not yet initialized when init() is called from Base()!
```

---

## 8. Polymorphism

### Compile-time (overloading)

```java
class Calculator {
    int add(int a, int b) { return a + b; }
    double add(double a, double b) { return a + b; }
    int add(int a, int b, int c) { return a + b + c; }
}
// Java picks the right method at COMPILE TIME based on argument types
```

### Runtime (override + dynamic dispatch)

```java
// The JVM stores a virtual method table (vtable) per class
// At runtime, method call is resolved by looking up the vtable of the ACTUAL object

Animal a1 = new Dog("Rex", "Lab");
Animal a2 = new Cat("Whiskers");
Animal a3 = new Animal("Generic");

List<Animal> animals = List.of(a1, a2, a3);
for (Animal a : animals) {
    a.makeSound(); // Calls Dog.makeSound(), Cat.makeSound(), Animal.makeSound() respectively
                   // Decided at RUNTIME by actual object type
}
```

---

## 9. Abstract Classes

```java
// Can't instantiate — is a base for related classes
public abstract class Shape {
    private String color;

    public Shape(String color) { this.color = color; }

    // abstract method — no body, MUST be overridden by subclasses
    public abstract double area();
    public abstract double perimeter();

    // concrete method — shared behavior
    public void printInfo() {
        System.out.printf("%s [color=%s, area=%.2f, perimeter=%.2f]%n",
            getClass().getSimpleName(), color, area(), perimeter());
    }
}

public class Circle extends Shape {
    private final double radius;
    public Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }
    @Override public double area() { return Math.PI * radius * radius; }
    @Override public double perimeter() { return 2 * Math.PI * radius; }
}
```

**When to use abstract class?** When you have IS-A relationship with shared state/behavior across subclasses. Think: `Animal` → `Dog/Cat`, `Shape` → `Circle/Rectangle`.

---

## 10. Interfaces

```java
// Interface: a contract — "what", not "how"
// Can have: abstract methods, default methods (Java 8), static methods (Java 8), constants
public interface Drawable {
    void draw();                               // abstract (implicitly public abstract)
    
    default void drawWithBorder() {            // default method (Java 8) — has body
        System.out.println("Drawing border");
        draw();
    }
    
    static Drawable noOp() {                   // static factory in interface (Java 8)
        return () -> {};
    }
}

public interface Resizable {
    void resize(double factor);
}

// Class can implement MULTIPLE interfaces (Java's way of multiple inheritance)
public class Canvas implements Drawable, Resizable {
    @Override public void draw() { System.out.println("Drawing on canvas"); }
    @Override public void resize(double factor) { ... }
}
```

### Interface vs Abstract Class

| Feature | Interface | Abstract Class |
|---|---|---|
| Multiple implementation | ✅ Yes | ❌ Single inheritance only |
| State (instance fields) | ❌ Only constants | ✅ Yes |
| Constructor | ❌ No | ✅ Yes |
| Default methods | ✅ Yes (Java 8+) | ✅ Yes |
| IS-A relationship | Not necessarily | Yes |
| Best for | Capabilities/contracts | Shared base implementation |

**Prefer interfaces** when defining a capability that can apply to unrelated classes (e.g., `Serializable`, `Comparable`, `Runnable`).

### Functional Interface (Java 8)

An interface with exactly ONE abstract method — can be implemented with a lambda:

```java
@FunctionalInterface
public interface Validator<T> {
    boolean validate(T value);
    // Can have default/static methods and still be functional
    default Validator<T> and(Validator<T> other) {
        return value -> this.validate(value) && other.validate(value);
    }
}

// Lambda implementation
Validator<String> notEmpty = s -> !s.isBlank();
Validator<String> maxLength = s -> s.length() <= 100;
Validator<String> bothChecks = notEmpty.and(maxLength);

bothChecks.validate("hello"); // true
bothChecks.validate("");      // false
```

Built-in functional interfaces (in `java.util.function`):

| Interface | Signature | Use |
|---|---|---|
| `Supplier<T>` | `T get()` | factory / lazy computation |
| `Consumer<T>` | `void accept(T t)` | side effects |
| `Function<T,R>` | `R apply(T t)` | transform |
| `Predicate<T>` | `boolean test(T t)` | filter condition |
| `BiFunction<T,U,R>` | `R apply(T t, U u)` | two inputs → output |
| `UnaryOperator<T>` | `T apply(T t)` | same type in/out |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` | two same type → same type |

---

## 11. The `Object` Class

Every class implicitly extends `Object`. Key methods:

### `equals()` and `hashCode()` — the contract

```java
// CONTRACT: if a.equals(b) is true, then a.hashCode() == b.hashCode()
// (reverse is NOT guaranteed — hash collisions are OK)

public class Point {
    private final int x, y;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;           // same reference → equal
        if (!(o instanceof Point other)) return false; // null or different type → not equal
        return x == other.x && y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y); // combine fields consistently
    }
}

// If you override equals but NOT hashCode:
Set<Point> set = new HashSet<>();
Point p1 = new Point(1, 2);
set.add(p1);
set.contains(new Point(1, 2)); // FALSE! — different hashCode, lookup fails in wrong bucket
```

### `toString()`

```java
@Override
public String toString() {
    return "Point[x=%d, y=%d]".formatted(x, y);
}
// Called automatically by System.out.println, string concatenation (+), loggers
```

### `clone()` — prefer copy constructors instead

```java
// Requires implements Cloneable
// Shallow copy by default — nested objects are NOT cloned
// Fragile API — prefer copy constructor: new Point(existing)
```

### `Comparable<T>` — natural ordering

```java
public class Product implements Comparable<Product> {
    private final String name;
    private final double price;

    @Override
    public int compareTo(Product other) {
        return Double.compare(this.price, other.price); // ascending by price
    }
}

List<Product> products = ...;
Collections.sort(products);   // uses compareTo
products.sort(Comparator.naturalOrder()); // same

// Multi-field sorting with Comparator
products.sort(Comparator.comparingDouble(Product::getPrice)
                        .thenComparing(Product::getName));
```

---

## 12. Generics

### Why generics?

```java
// Without generics — ClassCastException at runtime
List list = new ArrayList();
list.add("hello");
list.add(42);
String s = (String) list.get(1); // ClassCastException!

// With generics — type error caught at COMPILE TIME
List<String> strings = new ArrayList<>();
strings.add("hello");
strings.add(42); // COMPILE ERROR
String s = strings.get(0); // no cast needed
```

### Generic classes and methods

```java
// Generic class
public class Pair<A, B> {
    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() { return first; }
    public B getSecond() { return second; }

    // Static factory — type inferred
    public static <A, B> Pair<A, B> of(A a, B b) {
        return new Pair<>(a, b);
    }
}

Pair<String, Integer> pair = Pair.of("Alice", 30);
String name = pair.getFirst();  // no cast!
int age = pair.getSecond();
```

### Bounded type parameters

```java
// <T extends Comparable<T>> — T must implement Comparable
public <T extends Comparable<T>> T max(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
max(3, 7)           // 7
max("apple","pear") // "pear"

// Upper bounded wildcard — "? extends T" means "T or subtype of T" — READ ONLY
public double sumList(List<? extends Number> list) {
    return list.stream().mapToDouble(Number::doubleValue).sum();
}
sumList(List.of(1, 2, 3));       // List<Integer> — Integer extends Number
sumList(List.of(1.0, 2.0));      // List<Double> — Double extends Number

// Lower bounded wildcard — "? super T" means "T or supertype of T" — WRITE ONLY
public void addNumbers(List<? super Integer> list) {
    list.add(1); list.add(2); list.add(3);
}
// PECS: Producer Extends, Consumer Super
```

### Type Erasure

```java
// At COMPILE TIME, generic types are used for type checking
// At RUNTIME, type parameters are ERASED — replaced by Object (or upper bound)
List<String> strings = new ArrayList<>();
List<Integer> ints = new ArrayList<>();
strings.getClass() == ints.getClass() // true! Both are just ArrayList at runtime

// Can't do:
if (list instanceof List<String>) { } // COMPILE ERROR — can't check generic type at runtime
new T() // COMPILE ERROR — can't instantiate type parameter
T[] array = new T[10]; // COMPILE ERROR — can't create generic array
```

---

## 13. Records (Java 16)

Records are compact immutable data carriers — they auto-generate `equals`, `hashCode`, `toString`, and accessors:

```java
public record Point(double x, double y) { }

// Equivalent to:
// - two final private fields: x and y
// - constructor Point(double x, double y) with validation
// - accessors: x() and y() — NOT getX()/getY()
// - equals, hashCode, toString based on all fields

Point p1 = new Point(1.0, 2.0);
Point p2 = new Point(1.0, 2.0);
p1.equals(p2)   // true
p1.x()          // 1.0

// Custom compact constructor — for validation
public record Range(int min, int max) {
    Range {   // compact constructor — no parameter list, fields set automatically
        if (min > max) throw new IllegalArgumentException("min must be <= max");
    }
}

// Records can implement interfaces
public record Employee(String name, double salary) implements Comparable<Employee> {
    @Override
    public int compareTo(Employee other) {
        return Double.compare(this.salary, other.salary);
    }
}
```

---

## 14. Sealed Classes (Java 17)

Allows controlled inheritance — only permitted subclasses can extend:

```java
// Sealed class lists exactly which classes can extend it
public sealed class Shape permits Circle, Rectangle, Triangle { }

public final class Circle extends Shape {    // final: can't be extended further
    private final double radius;
    ...
}

public non-sealed class Rectangle extends Shape { // non-sealed: can be freely extended
    private final double width, height;
    ...
}

// Used with pattern matching in switch (Java 21)
double area = switch (shape) {
    case Circle c    -> Math.PI * c.radius() * c.radius();
    case Rectangle r -> r.width() * r.height();
    case Triangle t  -> 0.5 * t.base() * t.height();
    // No default needed — compiler knows all subtypes!
};
```

---

## 15. Nested Classes

```java
// 1. Static nested class — no reference to outer instance
class Outer {
    static class StaticNested {
        void show() { System.out.println("Static nested class"); }
    }
}
new Outer.StaticNested().show();

// 2. Inner (non-static) class — holds reference to outer instance
class Outer {
    private int x = 10;
    class Inner {
        void show() { System.out.println(x); } // can access outer's private fields
    }
}
new Outer().new Inner().show();

// 3. Local class — defined inside a method
void method() {
    class LocalHelper { void help() { ... } }
    new LocalHelper().help();
}

// 4. Anonymous class — inline implementation, no name given
Runnable r = new Runnable() {
    @Override
    public void run() { System.out.println("Running"); }
};
// Usually replaced by lambda: Runnable r = () -> System.out.println("Running");

// Builder pattern uses static nested class
public class HttpRequest {
    private final String url;
    private final String method;
    
    private HttpRequest(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
    }

    public static class Builder {
        private String url;
        private String method = "GET";

        public Builder url(String url) { this.url = url; return this; }
        public Builder method(String method) { this.method = method; return this; }
        public HttpRequest build() { return new HttpRequest(this); }
    }
}
HttpRequest req = new HttpRequest.Builder()
    .url("https://api.example.com/users")
    .method("POST")
    .build();
```

---

## 16. Composition vs Inheritance

**Prefer composition** — "has-a" over inheritance's "is-a":

```java
// Inheritance: Engine IS-A Vehicle (questionable)
class Vehicle { void start() { ... } }
class Engine extends Vehicle { ... } // WRONG — Engine is not a Vehicle

// Composition: Car HAS-A Engine
class Engine {
    void start() { System.out.println("Engine started"); }
    void stop() { System.out.println("Engine stopped"); }
}

class Car {
    private final Engine engine = new Engine(); // composition

    public void start() { engine.start(); }
    public void stop() { engine.stop(); }
    // Car can also add more methods without being tied to Engine's hierarchy
}
```

**Inheritance pitfalls**:
- Fragile base class problem — changes in parent break subclasses
- Leaks implementation details (subclass must know parent internals)
- Can't change "IS-A" relationship at runtime
- Java doesn't have multiple class inheritance

---

## 17. Interview Q&A

**Q: What's the difference between overloading and overriding?**  
**Overloading** (compile-time polymorphism): same method name, different parameter signature in the SAME class. Resolved by the compiler based on argument types. Not real polymorphism — just syntactic convenience. **Overriding** (runtime polymorphism): subclass redefines a parent method with the SAME signature. Resolved at runtime via dynamic dispatch using the vtable. Requires same name, same parameter types, same/covariant return type, and same or wider access modifier.

**Q: Can you explain the `equals` / `hashCode` contract?**  
If `a.equals(b)` is true, then `a.hashCode()` must equal `b.hashCode()`. Used by hash-based collections (`HashMap`, `HashSet`): they first compare hash codes (to find the bucket), then `equals()` (to confirm the match). Violating the contract causes silent failures: objects that are logically equal won't be found in a `HashSet` or will create duplicates.

**Q: Why can't Java have multiple inheritance of classes?**  
The diamond problem: if class C extends both A and B, and both A and B define the same method `foo()`, which version does C inherit? Java avoids ambiguity by allowing only single class inheritance. Interfaces provide multiple inheritance of type (API contracts) without state conflicts. Java 8+ default methods can create a similar diamond scenario — resolved by explicitly overriding the ambiguous method.

**Q: What is type erasure and why does it matter?**  
At runtime, generic type parameters are erased and replaced with `Object` (or their upper bound). This maintains backward compatibility with pre-Java-5 bytecode. Consequences: can't use `instanceof List<String>` (only `instanceof List`), can't create `new T()`, can't create `new T[]`, and runtime reflection shows only raw types. Workaround: pass a `Class<T>` token when runtime type info is needed.

**Q: When would you choose an abstract class over an interface?**  
Choose **abstract class** when subclasses share state (fields) or significant common implementation (concrete methods), and when there's a genuine IS-A hierarchy (Animal → Dog). Choose **interface** when defining a capability that many unrelated classes might implement (`Serializable`, `Comparable`, `AutoCloseable`), or when you need multiple inheritance of type. Since Java 8 added `default` methods to interfaces, the line has blurred — but interfaces still can't have instance state (fields). General rule: **code to interfaces, use abstract classes for shared implementation**.
