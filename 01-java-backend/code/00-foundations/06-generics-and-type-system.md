# Java Generics and Type System — Type Erasure, Wildcards, PECS

---

## 1. Why Generics?

Before Java 5, collections stored `Object` — every read required an explicit cast, and type errors crashed at runtime:

```java
// Pre-generics (Java 1.4)
List employees = new ArrayList();
employees.add(new Employee());
employees.add("oops — wrong type, no compiler error");
Employee e = (Employee) employees.get(1); // ClassCastException at RUNTIME

// With generics (Java 5+)
List<Employee> employees = new ArrayList<>();
employees.add("oops"); // COMPILE ERROR — type safety at compile time!
Employee e = employees.get(0); // no cast needed
```

Generics encode type information in **source code** and **bytecode metadata**, but the type is erased at runtime (see Section 4).

---

## 2. Generic Classes and Interfaces

```java
// Type parameter naming conventions:
// T — Type (generic entity)
// E — Element (collections)
// K, V — Key, Value (maps)
// N — Number
// R — Return type

public class Pair<A, B> {                   // two type parameters
    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public A getFirst() { return first; }
    public B getSecond() { return second; }

    // Type-safe swap returns a new Pair with swapped types
    public Pair<B, A> swap() { return new Pair<>(second, first); }
}

Pair<String, Integer> p = new Pair<>("Alice", 30);
Pair<Integer, String> swapped = p.swap();  // compiler infers types!

// Generic interface
public interface Repository<T, ID> {
    T findById(ID id);
    void save(T entity);
    List<T> findAll();
}

public class UserRepository implements Repository<User, Long> { ... }
```

---

## 3. Generic Methods

A generic method declares its own type parameters, independent of the class:

```java
public class Utils {
    // Type parameter <T> declared on the method
    public static <T> List<T> repeat(T value, int times) {
        List<T> result = new ArrayList<>();
        for (int i = 0; i < times; i++) result.add(value);
        return result;
    }

    // Type inference: compiler deduces T from argument
    List<String> words = Utils.repeat("hello", 3); // T inferred as String
    List<Integer> nums = Utils.repeat(42, 5);       // T inferred as Integer

    // Explicit type witness (when inference fails)
    List<Number> nums = Utils.<Number>repeat(42, 3); // force T = Number
}
```

---

## 4. Bounded Type Parameters

```java
// Upper bound: T must be a Number or subclass of Number
public static <T extends Number> double sum(List<T> list) {
    return list.stream().mapToDouble(Number::doubleValue).sum();
}
sum(List.of(1, 2, 3));    // T = Integer ✓
sum(List.of(1.5, 2.5));   // T = Double  ✓
// sum(List.of("a","b")); // COMPILE ERROR — String not a Number

// Multiple bounds: T is both Cloneable AND Comparable
public static <T extends Cloneable & Comparable<T>> T cloneAndMax(T a, T b) {
    return a.compareTo(b) >= 0 ? a : b;
}
// Note: class bound must come FIRST (before interface bounds)
// <T extends AbstractClass & Interface1 & Interface2>

// Recursive bound: self-referential — "T is comparable to itself"
public static <T extends Comparable<T>> T max(List<T> list) {
    return list.stream().max(Comparator.naturalOrder()).orElseThrow();
}
// This is the standard pattern for natural ordering constraints
// Collections.sort(List<T extends Comparable<T>>)
```

---

## 5. Type Erasure — How Generics Actually Work at Runtime

At compile time, the Java compiler checks generic types exhaustively.  
At runtime, all type parameters are **erased** — `List<String>` becomes `List`, `T` becomes `Object` (or the upper bound):

```java
// Source code:
public class Box<T> {
    private T value;
    public T get() { return value; }
    public void set(T v) { this.value = v; }
}

// After erasure (what JVM actually executes):
public class Box {
    private Object value;            // T erased to Object
    public Object get() { return value; }
    public void set(Object v) { this.value = v; }
}

// Source code with bounded parameter:
public class SortedBox<T extends Comparable<T>> {
    private T value;
    public int compare(T other) { return value.compareTo(other); }
}

// After erasure: T erased to its UPPER BOUND (Comparable), not Object
public class SortedBox {
    private Comparable value;        // T erased to Comparable (upper bound)
    public int compare(Comparable other) { return value.compareTo(other); }
}
```

**Why erasure?** Backward compatibility with pre-Java-5 code. JVM stays binary-compatible.

### Consequences of Erasure

```java
// 1. Cannot use instanceof with parameterized types
if (obj instanceof List<String>) { ... }    // COMPILE ERROR
if (obj instanceof List<?>)      { ... }    // OK — unbounded wildcard allowed

// 2. Cannot create instances of type parameters
class Container<T> {
    T value = new T();  // COMPILE ERROR — can't instantiate T
    // Workaround: pass Class<T> and use reflection
    T value = clazz.getDeclaredConstructor().newInstance();
}

// 3. Cannot create generic arrays
T[] arr = new T[10];             // COMPILE ERROR
Object[] arr = new Object[10];   // workaround (unchecked)
T[] arr = (T[]) Array.newInstance(clazz, 10); // with Class<T> token

// 4. Cannot catch parameterized exceptions
// catch (MyException<String> e) { }  // COMPILE ERROR — Throwable can't be parameterized

// 5. Static members cannot use class type parameters
class Box<T> {
    static T shared;  // COMPILE ERROR — T is per-instance, not per-class
}
```

---

## 6. Bridge Methods — Preserving Polymorphism After Erasure

When a generic class is subclassed, the compiler may generate a **bridge method** to maintain proper overriding:

```java
// Generic interface
interface Comparable<T> {
    int compareTo(T other);
}

// After erasure:
interface Comparable {
    int compareTo(Object other);   // erased signature
}

// Your implementation:
class Employee implements Comparable<Employee> {
    public int compareTo(Employee other) { ... }
}

// After compilation, the Employee .class file contains:
// 1. Your method:
public int compareTo(Employee other) { ... }

// 2. COMPILER-GENERATED bridge method (links erased interface to your method):
public int compareTo(Object other) {
    return compareTo((Employee) other);   // safe cast inserted by compiler
}
```

Bridge methods ensure runtime polymorphism works correctly despite erasure. You can see them with `javap -verbose ClassName`.

---

## 7. Wildcards

Wildcards allow flexibility when the exact type parameter is unknown:

```java
// Unbounded wildcard: ? — "any type"
public void printList(List<?> list) {  // accepts List<String>, List<Integer>, etc.
    for (Object element : list) {      // can only read as Object
        System.out.println(element);
    }
    // list.add("hello"); // COMPILE ERROR — can't add to List<?> (type unknown at compile time)
}

// Upper-bounded wildcard: ? extends T — "T or any subtype"
public double sumNumbers(List<? extends Number> numbers) {
    return numbers.stream().mapToDouble(Number::doubleValue).sum();
}
sumNumbers(List.of(1, 2, 3));         // Integer extends Number ✓
sumNumbers(List.of(1.5, 2.5, 3.5));   // Double extends Number  ✓
// Can READ elements as Number, but CANNOT write (compiler blocks add())

// Lower-bounded wildcard: ? super T — "T or any supertype"
public void addNumbers(List<? super Integer> list) {
    list.add(1);    // ✓ — Integer is always valid (it's the lower bound)
    list.add(2);    // ✓
    // Integer x = list.get(0); // can only read as Object (super-type is unknown)
}
addNumbers(new ArrayList<Integer>());  // ✓
addNumbers(new ArrayList<Number>());   // ✓ — Number is supertype of Integer
addNumbers(new ArrayList<Object>());   // ✓ — Object is supertype of Integer
```

---

## 8. PECS — Producer Extends, Consumer Super

**PECS** is the rule for choosing wildcards when passing collections to methods:

> **P**roducer → `? extends T` (you READ / produce values from it)  
> **C**onsumer → `? super T` (you WRITE / consume values into it)

The classic example — `Collections.copy()`:
```java
// JDK source:
public static <T> void copy(
    List<? super T> dest,      // Consumer: we WRITE T into dest
    List<? extends T> src      // Producer: we READ T from src
) {
    for (T e : src) {          // reading from src as T ✓
        dest.add(e);           // writing into dest as T ✓
    }
}

// Usage:
List<Integer> ints = List.of(1, 2, 3);
List<Number> nums = new ArrayList<>();
Collections.copy(nums, ints);  // T=Integer, dest=List<Number>(consumer), src=List<Integer>(producer)
```

Real-world PECS application:
```java
// A stack with push/pop PECS:
public class Stack<T> {
    // pushAll: reads from the source — source is Producer of T
    public void pushAll(Iterable<? extends T> src) {
        for (T item : src) push(item);
    }

    // popAll: writes to the destination — destination is Consumer of T
    public void popAll(Collection<? super T> dst) {
        while (!isEmpty()) dst.add(pop());
    }
}

Stack<Number> numberStack = new Stack<>();
numberStack.pushAll(List.of(1, 2, 3));         // List<Integer> is producer of Number ✓
numberStack.popAll(new ArrayList<Object>());   // ArrayList<Object> is consumer of Number ✓
```

**Memory trick**: `extends` = out (read/produce), `super` = in (write/consume)

---

## 9. Wildcard Capture and Type Inference

```java
// Wildcard capture — compiler infers concrete type for ?
public static void swap(List<?> list, int i, int j) {
    swapHelper(list, i, j);  // delegates to capture helper
}

// Helper captures ? as T — now we can assign within the method
private static <T> void swapHelper(List<T> list, int i, int j) {
    T temp = list.get(i);
    list.set(i, list.get(j));
    list.set(j, temp);
}

// When to use <T> vs <?> in a method signature:
// Use <T> when:
//   - You need to relate the return type to a parameter type
//   - You need to relate multiple parameter types
//   - You need to use the type inside the method body
public static <T> T pickOne(List<T> list, int i) { return list.get(i); }

// Use <?> when:
//   - You only care about the operations available (e.g., just iterating)
//   - You don't need to capture or name the type
public static void print(List<?> list) { list.forEach(System.out::println); }
```

---

## 10. Reifiable vs Non-Reifiable Types

A **reifiable** type has full type information available at runtime. A **non-reifiable** type has been partially erased:

| Type | Reifiable? | Runtime type |
|------|-----------|--------------|
| `String` | ✓ Yes | `String` |
| `String[]` | ✓ Yes | `String[]` (arrays always reifiable) |
| `List<String>` | ✗ No | `List` (String erased) |
| `List<?>` | ✓ Yes | `List<?>` is reifiable |
| `Map<String,Integer>` | ✗ No | `Map` |

**Arrays are covariant, generics are invariant** — this is a famous inconsistency:
```java
// Arrays are covariant — safe at compile time, checked at runtime
String[] strings = new String[3];
Object[] objects = strings;    // legal — covariant assignment
objects[0] = 42;               // ArrayStoreException at RUNTIME

// Generics are invariant — caught at COMPILE time (safer)
List<String> strings = new ArrayList<>();
List<Object> objects = strings; // COMPILE ERROR — invariant

// Use wildcards for generic "covariance":
List<? extends Object> objects = strings; // OK — but read-only!
```

---

## 11. Raw Types — Avoid Them

```java
// Raw type: no type parameter (for backward compat only)
List rawList = new ArrayList();   // raw type — avoids all generic checks
rawList.add("hello");
rawList.add(42);
String s = (String) rawList.get(1); // ClassCastException at runtime

// Generates "unchecked" compiler warnings
List rawList = new ArrayList<String>(); // warning: raw use of parameterized type
rawList.add(42);                        // warning: unchecked call to add(Object)

// What to do instead:
List<Object> objectList = new ArrayList<>();    // explicit Object type
List<?>      unknownList = someMethod();       // read-only unknown type
```

---

## 12. Type Token Pattern

Working around erasure when you need the class at runtime (common in frameworks):

```java
// The problem: T is erased — can't create instances or deserialize without Class<T>
public class GenericService<T> {
    private final Class<T> type;
    public GenericService(Class<T> type) { this.type = type; }  // "type token"

    public T deserialize(String json) {
        return objectMapper.readValue(json, type);  // uses Class<T> for reflection
    }
}

GenericService<User> svc = new GenericService<>(User.class);
User user = svc.deserialize(json);

// Spring's ParameterizedTypeReference — for generic types like List<User>
ResponseEntity<List<User>> response = restTemplate.exchange(
    url, HttpMethod.GET, null,
    new ParameterizedTypeReference<List<User>>() {}  // anonymous subclass captures type info
);
List<User> users = response.getBody();

// Under the hood, ParameterizedTypeReference uses getClass().getGenericSuperclass()
// to read the type argument from the class's bytecode metadata (not erased!)
// because the anonymous class's supertype IS stored in bytecode.
```

---

## 13. Common Pitfalls

```java
// Pitfall 1: Confusing invariance with PECS
List<Integer> ints = new ArrayList<>();
List<Number> nums = ints;   // COMPILE ERROR — List<Integer> is NOT a List<Number>
// Fix: List<? extends Number> nums = ints; (if read-only)

// Pitfall 2: Unchecked cast from raw type (silent ClassCastException at use site)
List rawList = getListFromLegacyCode();
List<String> strings = (List<String>) rawList;  // compiles with warning
strings.get(0).length();                         // ClassCastException if rawList has non-String!

// Pitfall 3: Creating generic arrays
List<String>[] arr = new ArrayList<String>[10];  // COMPILE ERROR
// Use List<List<String>> instead:
List<List<String>> listOfLists = new ArrayList<>();

// Pitfall 4: instanceof with generics
void process(Object obj) {
    if (obj instanceof List<String>) { ... }  // COMPILE ERROR
    if (obj instanceof List<?>)      { ... }  // OK
    if (obj instanceof List)         { ... }  // OK (raw type)
    // To check element type, you must iterate and check each element individually
}
```

---

## 14. Interview Q&A

**Q: What is type erasure and why does Java use it?**  
Type erasure is the process by which the Java compiler removes all generic type information from bytecode. `List<String>` becomes `List`, `T` becomes `Object` (or its upper bound). Java chose erasure for backward compatibility with pre-Java-5 code — the same JVM can run both old and new bytecode. The compiler inserts type checks (casts) at the call sites to maintain type safety, so ClassCastExceptions still prevent wrong types from leaking, just earlier than classical runtime checks.

**Q: What is the difference between `List<?>` and `List<Object>`?**  
`List<Object>` only accepts `List<Object>` (generics are invariant — `List<String>` is NOT a `List<Object>`). `List<?>` accepts any `List<X>` regardless of X — but you can only read from it as `Object` and cannot add any elements (except null). Use `List<Object>` when you genuinely want a mixed-type list. Use `List<?>` when you accept any list and only need to iterate over its elements.

**Q: Explain PECS with an example.**  
PECS means: use `? extends T` (upper bound) for sources you only READ from (producer), and `? super T` (lower bound) for destinations you only WRITE to (consumer). Example: `Collections.copy(List<? super T> dest, List<? extends T> src)` — `src` is a producer (you read T's from it), `dest` is a consumer (you write T's into it). If you both read and write, use `<T>` without a wildcard.

**Q: How do bridge methods work?**  
When a class implements or overrides a generic method, the compiler generates an additional synthetic "bridge method" with the erased signature that delegates to the actual implementation. For example, `class Employee implements Comparable<Employee>` — the compiler generates `int compareTo(Object other) { return compareTo((Employee) other); }` as a bridge so that code using the raw interface (`Comparable`) can still call `compareTo(Object)` and reach the correct implementation through polymorphism.

**Q: Why can't you create `new T[]` or `new T()`?**  
Because of type erasure — at runtime, the JVM has no idea what `T` is. `new T[]` would need to create a typed array, but the array type is a runtime concept that requires the actual class. `new T()` would need to know which constructor to call. The common workaround is to pass `Class<T>` as a parameter ("type token") and use `clazz.getDeclaredConstructor().newInstance()` for instantiation, or `Array.newInstance(clazz, size)` for arrays.
