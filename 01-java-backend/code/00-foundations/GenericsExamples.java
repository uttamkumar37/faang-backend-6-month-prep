package com.faangprep.javabackend.foundations;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/**
 * Generics and Type System — Type Erasure, Wildcards, PECS
 *
 * Topics:
 *  1. Generic class + methods — basic mechanics
 *  2. Bounded type parameters — extends + multiple bounds
 *  3. Type erasure — what is and isn't available at runtime
 *  4. Wildcards — unbounded, upper-bounded, lower-bounded
 *  5. PECS (Producer Extends, Consumer Super) — copy example
 *  6. Wildcard capture — swap via helper
 *  7. Raw types — why they're dangerous
 *  8. Type token — Class<T> and ParameterizedType workaround
 *  9. Recursive bounds — Comparable<T> pattern
 * 10. Invariance vs covariance — arrays vs generics comparison
 */
public class GenericsExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GENERIC CLASS + GENERIC METHODS
    // ─────────────────────────────────────────────────────────────────────────

    // Generic pair — holds two values of potentially different types
    static class Pair<A, B> {
        private final A first;
        private final B second;

        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public A getFirst()  { return first; }
        public B getSecond() { return second; }

        // Generic method — independent type parameter on the method
        public <C> Pair<C, B> withFirst(C newFirst) {
            return new Pair<>(newFirst, this.second);
        }

        public Pair<B, A> swap() { return new Pair<>(second, first); }

        @Override
        public String toString() { return "(" + first + ", " + second + ")"; }
    }

    // Generic method — static, own type parameter <T>
    static <T> List<T> repeat(T value, int times) {
        List<T> result = new ArrayList<>(times);
        for (int i = 0; i < times; i++) result.add(value);
        return result;
    }

    // Generic method with multiple type parameters
    static <K, V> Map<V, K> invertMap(Map<K, V> original) {
        Map<V, K> inverted = new HashMap<>();
        original.forEach((k, v) -> inverted.put(v, k));  // note: duplicate values will overwrite
        return inverted;
    }

    static void genericClassAndMethodDemo() {
        System.out.println("=== 1. Generic Class + Methods ===");

        Pair<String, Integer> nameAge = new Pair<>("Alice", 30);
        System.out.println("Pair: " + nameAge);
        System.out.println("Swapped: " + nameAge.swap());           // (30, Alice)

        Pair<Double, Integer> withSalary = nameAge.withFirst(99_000.0);
        System.out.println("With salary: " + withSalary);           // (99000.0, 30)

        // Type inference — compiler deduces T from argument
        List<String> words  = repeat("hello", 3);  // T = String
        List<Integer> zeros = repeat(0, 5);         // T = Integer
        System.out.println("Repeated: " + words);
        System.out.println("Repeated: " + zeros);

        // Invert a map
        Map<String, Integer> scores = Map.of("Alice", 95, "Bob", 87, "Carol", 92);
        Map<Integer, String> byScore = invertMap(scores);
        System.out.println("Score 95 belongs to: " + byScore.get(95));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. BOUNDED TYPE PARAMETERS
    // ─────────────────────────────────────────────────────────────────────────

    // Upper bound: T must extend Number
    static <T extends Number> double sumList(List<T> list) {
        return list.stream().mapToDouble(Number::doubleValue).sum();
    }

    // Multiple bounds: T must implement both Comparable and Cloneable
    // (class bound first, then interfaces)
    static <T extends Comparable<T>> T clamp(T value, T min, T max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }

    // Recursive bound: T is Comparable to itself — the standard sorting constraint
    static <T extends Comparable<T>> Optional<T> findMax(List<T> list) {
        return list.stream().max(Comparator.naturalOrder());
    }

    static void boundedTypeDemo() {
        System.out.println("\n=== 2. Bounded Type Parameters ===");

        System.out.println("Sum of ints: "    + sumList(List.of(1, 2, 3)));    // T = Integer
        System.out.println("Sum of doubles: " + sumList(List.of(1.5, 2.5)));   // T = Double

        System.out.println("clamp(15, 0, 10): " + clamp(15, 0, 10));          // 10
        System.out.println("clamp(5, 0, 10): "  + clamp(5, 0, 10));           // 5
        System.out.println("clamp('z','a','m'): " + clamp('z', 'a', 'm'));     // 'm'

        System.out.println("Max of strings: " + findMax(List.of("banana","apple","cherry")));
        System.out.println("Max of ints: "    + findMax(List.of(3, 1, 4, 1, 5, 9, 2)));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. TYPE ERASURE — what survives at runtime vs what doesn't
    // ─────────────────────────────────────────────────────────────────────────

    static void typeErasureDemo() {
        System.out.println("\n=== 3. Type Erasure ===");

        List<String>  strings  = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();

        // At runtime, both are just "ArrayList" — type parameter erased
        System.out.println("Same class? " + (strings.getClass() == integers.getClass())); // true
        System.out.println("Class name: " + strings.getClass().getName()); // java.util.ArrayList

        // instanceof with raw type is OK; with parameterized type: compile error
        Object obj = new ArrayList<String>();
        System.out.println("Is ArrayList? " + (obj instanceof ArrayList<?>));  // OK — wildcard
        System.out.println("Is List?      " + (obj instanceof List));          // OK — raw type

        // After erasure, you CAN add wrong types via raw type (heap pollution)
        @SuppressWarnings({"rawtypes","unchecked"})
        List rawList = new ArrayList<String>();
        rawList.add(42);   // no compile error through raw reference
        rawList.add("ok");
        System.out.println("Raw list (heap pollution): " + rawList);
        // Accessing via typed reference causes ClassCastException:
        try {
            @SuppressWarnings("unchecked")
            List<String> typed = rawList;
            String s = typed.get(0); // throws ClassCastException — 42 is not a String
        } catch (ClassCastException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // Retrieve generic type info from superclass (TYPE INFO IS STORED in class hierarchy!)
        // This is how ParameterizedTypeReference works in Spring
        Type superType = new ArrayList<String>(){}.getClass().getGenericSuperclass();
        if (superType instanceof ParameterizedType pt) {
            System.out.println("Recovered type argument: " + pt.getActualTypeArguments()[0]);
            // Prints: class java.lang.String  — not erased because stored in class bytecode!
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. WILDCARDS — read-only vs write-only semantics
    // ─────────────────────────────────────────────────────────────────────────

    // Unbounded wildcard: accepts ANY List — read-only as Object
    static void printAll(List<?> list) {
        list.forEach(item -> System.out.print(item + " "));
        System.out.println();
        // list.add("oops"); // COMPILE ERROR — can't add to List<?>
    }

    // Upper-bounded: read elements as Number
    static double sumNumbers(List<? extends Number> numbers) {
        return numbers.stream().mapToDouble(Number::doubleValue).sum();
    }

    // Lower-bounded: write Integer values into the list
    static void addCountdown(List<? super Integer> list, int from) {
        for (int i = from; i >= 0; i--) {
            list.add(i); // safe — Integer is always a valid element for List<? super Integer>
        }
        // Integer item = list.get(0); // COMPILE ERROR — can only read as Object
    }

    static void wildcardDemo() {
        System.out.println("\n=== 4. Wildcards ===");

        printAll(List.of(1, 2, 3));         // List<Integer> — OK
        printAll(List.of("a", "b"));        // List<String>  — OK
        printAll(List.of(3.14, 2.71));      // List<Double>  — OK

        System.out.println("Sum of ints:    " + sumNumbers(List.of(1, 2, 3, 4)));   // 10.0
        System.out.println("Sum of doubles: " + sumNumbers(List.of(1.5, 2.5)));      // 4.0

        List<Number> numbers = new ArrayList<>();
        addCountdown(numbers, 5);
        System.out.println("Countdown into List<Number>: " + numbers);

        List<Object> objects = new ArrayList<>();
        addCountdown(objects, 3);
        System.out.println("Countdown into List<Object>: " + objects);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. PECS — Producer Extends, Consumer Super
    // ─────────────────────────────────────────────────────────────────────────

    // Classic PECS: copy from src (producer = extends) into dst (consumer = super)
    static <T> void copy(List<? super T> dst, List<? extends T> src) {
        for (T element : src) {    // reading from producer: element is T
            dst.add(element);       // writing to consumer: add as T
        }
    }

    // Stack with PECS
    static class TypedStack<T> {
        private final Deque<T> stack = new ArrayDeque<>();

        public void push(T t) { stack.push(t); }
        public T pop()        { return stack.pop(); }
        public boolean isEmpty() { return stack.isEmpty(); }

        // pushAll: source is a PRODUCER of T → use ? extends T
        public void pushAll(Iterable<? extends T> src) {
            for (T item : src) push(item);
        }

        // popAll: destination is a CONSUMER of T → use ? super T
        public void popAll(Collection<? super T> dst) {
            while (!isEmpty()) dst.add(pop());
        }
    }

    static void pecsDemo() {
        System.out.println("\n=== 5. PECS (Producer Extends, Consumer Super) ===");

        // copy: T = Integer, src = List<Integer> (extends Integer), dst = List<Number>
        List<Integer> ints = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        List<Number>  nums = new ArrayList<>();
        copy(nums, ints);  // T inferred as Integer
        System.out.println("Copied to List<Number>: " + nums);

        // Also works with T = Number, src = List<Double>
        List<Double> doubles = new ArrayList<>(List.of(1.1, 2.2, 3.3));
        List<Object> objects = new ArrayList<>();
        copy(objects, doubles);  // T = Double, dst accepts Object (super of Double)
        System.out.println("Copied to List<Object>: " + objects);

        // TypedStack with PECS
        TypedStack<Number> numStack = new TypedStack<>();
        numStack.pushAll(List.of(1, 2, 3));        // List<Integer> provides Number ✓
        numStack.pushAll(List.of(1.5, 2.5));       // List<Double>  provides Number ✓
        System.out.println("Stack size after pushAll: " + numStack.stack.size()); // 5

        List<Object> drain = new ArrayList<>();
        numStack.popAll(drain);                     // List<Object> consumes Number ✓
        System.out.println("Drained to Object list: " + drain);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. WILDCARD CAPTURE — swap elements (can't do with raw ?)
    // ─────────────────────────────────────────────────────────────────────────

    // Public API uses wildcard — callers don't need to know the type
    static void swap(List<?> list, int i, int j) {
        swapHelper(list, i, j); // delegate to capture helper
    }

    // Helper "captures" ? as T — now T is known inside this method
    private static <T> void swapHelper(List<T> list, int i, int j) {
        T temp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, temp);
    }

    static void wildcardCaptureDemo() {
        System.out.println("\n=== 6. Wildcard Capture ===");

        List<String>  strings  = new ArrayList<>(List.of("a", "b", "c", "d"));
        List<Integer> integers = new ArrayList<>(List.of(10, 20, 30, 40));

        swap(strings,  0, 3);  // List<?> accepts both List<String> and List<Integer>
        swap(integers, 1, 2);

        System.out.println("Swapped strings:  " + strings);   // [d, b, c, a]
        System.out.println("Swapped integers: " + integers);  // [10, 30, 20, 40]
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. RAW TYPES — why they compile but cause runtime issues
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressWarnings({"rawtypes","unchecked"})
    static void rawTypeDemo() {
        System.out.println("\n=== 7. Raw Types (danger! use for background only) ===");

        // Raw Pair — acts like Pair<Object, Object>
        Pair rawPair = new Pair("hello", 42);    // unchecked warning
        Object first = rawPair.getFirst();        // OK — returns Object
        System.out.println("Raw pair first: " + first);

        // The danger: raw type method loses generic checking
        Pair<String, Integer> typedPair = new Pair<>("Alice", 30);
        Pair rawRef = typedPair;          // raw reference to typed object (legal, with warning)
        rawRef.withFirst(9999);           // works at runtime — returns Pair<Integer, Integer>
        // but if we had added an incompatible type, we'd get ClassCastException at USE SITE

        // Comparison: raw vs parameterized
        List rawList      = new ArrayList();     // raw — no type safety
        List<String> safe = new ArrayList<>();   // parameterized — compile-time safety
        System.out.println("Raw list class: " + rawList.getClass().getSimpleName());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. TYPE TOKEN — Class<T> for runtime reflection
    // ─────────────────────────────────────────────────────────────────────────

    // Type token pattern — pass Class<T> to carry type information through erasure
    static class TypeSafeContainer<T> {
        private final Class<T> type;
        private T value;

        public TypeSafeContainer(Class<T> type) {
            this.type = type;
        }

        public void set(Object value) {
            this.value = type.cast(value); // runtime type check via type token
        }

        public T get() { return value; }

        // Can now create instances via reflection
        public T createNew() throws ReflectiveOperationException {
            return type.getDeclaredConstructor().newInstance();
        }
    }

    // Recovering type argument from anonymous subclass (ParameterizedTypeReference trick)
    static abstract class TypeReference<T> {
        private final Type type;

        protected TypeReference() {
            // Superclass of this anonymous class encodes T in bytecode — not erased!
            Type superType = getClass().getGenericSuperclass();
            this.type = ((ParameterizedType) superType).getActualTypeArguments()[0];
        }

        public Type getType() { return type; }
    }

    static void typeTokenDemo() {
        System.out.println("\n=== 8. Type Token ===");

        TypeSafeContainer<String> container = new TypeSafeContainer<>(String.class);
        container.set("hello world");
        String value = container.get();    // no cast needed — type-safe
        System.out.println("String container: " + value);

        try {
            container.set(42);  // ClassCastException — caught early
        } catch (ClassCastException e) {
            System.out.println("Caught wrong type: " + e.getMessage());
        }

        // ParameterizedTypeReference — recovers generic type argument
        TypeReference<List<String>> ref = new TypeReference<List<String>>() {};
        System.out.println("Recovered type: " + ref.getType());
        // Prints: java.util.List<java.lang.String>  — String was NOT erased here!

        // This is exactly how Spring's ParameterizedTypeReference<T> works
        // to restore type info for RestTemplate / WebClient deserialization
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. RECURSIVE BOUNDS — self-referential Comparable
    // ─────────────────────────────────────────────────────────────────────────

    // T extends Comparable<T> means "T can be compared to itself"
    static <T extends Comparable<T>> List<T> sort(List<T> input) {
        List<T> sorted = new ArrayList<>(input);
        Collections.sort(sorted);  // uses T.compareTo(T) — guaranteed to exist by bound
        return sorted;
    }

    static <T extends Comparable<T>> Pair<T, T> minMax(List<T> list) {
        if (list.isEmpty()) throw new IllegalArgumentException("empty list");
        T min = list.get(0), max = list.get(0);
        for (T t : list) {
            if (t.compareTo(min) < 0) min = t;
            if (t.compareTo(max) > 0) max = t;
        }
        return new Pair<>(min, max);
    }

    static void recursiveBoundsDemo() {
        System.out.println("\n=== 9. Recursive Bounds ===");

        List<Integer> ints    = List.of(3, 1, 4, 1, 5, 9, 2, 6);
        List<String>  strings = List.of("banana", "apple", "cherry", "date");

        System.out.println("Sorted ints:    " + sort(ints));
        System.out.println("Sorted strings: " + sort(strings));

        System.out.println("Int  min/max: " + minMax(ints));
        System.out.println("Str  min/max: " + minMax(strings));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. INVARIANCE vs COVARIANCE — arrays vs generics
    // ─────────────────────────────────────────────────────────────────────────

    static void invarianceVsCovarianceDemo() {
        System.out.println("\n=== 10. Invariance (generics) vs Covariance (arrays) ===");

        // Arrays are COVARIANT — String[] IS-A Object[]
        String[] strings = {"hello", "world"};
        Object[] objects = strings;             // legal — covariant
        System.out.println("Array assigned to Object[]: OK at compile time");
        try {
            objects[0] = 42;                    // ArrayStoreException — caught at RUNTIME
            System.out.println("ERROR: should have thrown!");
        } catch (ArrayStoreException e) {
            System.out.println("ArrayStoreException (covariant arrays): " + e.getMessage());
        }

        // Generics are INVARIANT — List<String> IS NOT List<Object>
        List<String> stringList = new ArrayList<>(List.of("a", "b"));
        // List<Object> objectList = stringList; // COMPILE ERROR — invariant

        // Fix: use wildcard (read-only)
        List<? extends Object> readOnly = stringList;  // OK — but can't add
        System.out.println("Wildcard read: " + readOnly.get(0)); // "a"
        // readOnly.add("c"); // COMPILE ERROR — can't add to ? extends Object

        // Invariance is SAFER — the error is caught at compile time, not runtime
        System.out.println("Generic invariance catches errors at compile time (safer)");

        // Arrays with generics — "unchecked" for a reason
        @SuppressWarnings("unchecked")
        List<String>[] arr = new ArrayList[2];  // raw array of List — legal but unsafe
        arr[0] = new ArrayList<>(List.of("ok"));
        System.out.println("Generic array access: " + arr[0].get(0));
        // This is heap pollution — arr[1] could hold List<Integer> without compile error
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        genericClassAndMethodDemo();
        boundedTypeDemo();
        typeErasureDemo();
        wildcardDemo();
        pecsDemo();
        wildcardCaptureDemo();
        rawTypeDemo();
        typeTokenDemo();
        recursiveBoundsDemo();
        invarianceVsCovarianceDemo();
    }
}
