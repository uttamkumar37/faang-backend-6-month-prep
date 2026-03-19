# Java Collections — Lists and Queues

---

## 1. ArrayList — Dynamic Array

`ArrayList` is backed by a plain `Object[]` array that grows automatically:

```
Initial capacity: 10 (default)
Element added past capacity: newCapacity = oldCapacity + (oldCapacity >> 1) ≈ 1.5×
Backing array:  [ E | E | E | E | E | _ | _ | _ | _ | _ ]
                  0   1   2   3   4   5   6   7   8   9
                                ↑ size=5                ↑ capacity=10
```

```java
// Creation
List<String> list = new ArrayList<>();             // capacity 10
List<String> list = new ArrayList<>(1000);         // pre-size when count is known (avoids copies)
List<String> list = new ArrayList<>(existingList); // copy constructor

// Core operations — complexities
list.add("x");      // O(1) amortized — appends at end; O(n) when resize needed
list.add(2, "x");   // O(n) — shifts elements right by one using System.arraycopy
list.get(i);        // O(1) — direct array index access
list.set(i, "x");   // O(1) — direct assignment
list.remove(i);     // O(n) — shifts elements left by one
list.remove("x");   // O(n) — linear scan then shift
list.contains("x"); // O(n) — linear scan (equals check)
list.size();        // O(1) — maintained as field

// Pre-size trick for many add operations
ArrayList<String> list = new ArrayList<>();
list.ensureCapacity(10_000); // triggers one-time allocation, avoids 13+ resizes to reach 10k

// Trim memory after bulk removals
list.trimToSize(); // shrinks backing array to current size

// Bulk operations (use System.arraycopy internally — fast)
list.addAll(otherList);  // append many at once
list.removeAll(set);     // remove all elements also in set
list.retainAll(set);     // keep only elements also in set

// Iteration — indexed for-loop is fine (cache-friendly)
for (int i = 0; i < list.size(); i++) {
    process(list.get(i)); // O(1) per element
}
```

**Internal grow mechanics** (OpenJDK source):
```java
// When add() triggers grow():
int newCapacity = oldCapacity + (oldCapacity >> 1); // int division, not exactly 1.5x
Arrays.copyOf(elementData, newCapacity);            // copies all existing elements
```

**When NOT to use ArrayList**:
- Frequent insertions/deletions in the middle (use `LinkedList` or reshape the algorithm)
- Concurrent write access (use `CopyOnWriteArrayList` or external synchronization)

---

## 2. LinkedList — Doubly-Linked Node Structure

`LinkedList<E>` implements both `List<E>` and `Deque<E>`. Each element is a `Node`:

```
null ← [prev|A|next] ⇄ [prev|B|next] ⇄ [prev|C|next] ⇄ [prev|D|next] → null
        ↑ head                                              ↑ tail
```

```java
LinkedList<String> list = new LinkedList<>();

// O(1) operations (head/tail)
list.addFirst("x");  // prepend
list.addLast("y");   // append
list.getFirst();     // peek head
list.getLast();      // peek tail
list.removeFirst();  // remove + return head
list.removeLast();   // remove + return tail

// O(n) operations (middle)
list.get(i);         // index traversal — must walk from head or tail
list.add(i, "x");    // find position O(n), then link O(1)
list.remove(i);      // find position O(n), then unlink O(1)
list.contains("x");  // linear scan

// As a Deque (preferred over LinkedList-as-Queue)
Deque<String> deque = new LinkedList<>();
deque.push("x");    // == addFirst
deque.pop();        // == removeFirst
deque.offer("y");   // == addLast
deque.poll();       // == removeFirst (returns null if empty)
```

**When to use LinkedList**:
- You need frequent `addFirst`/`removeFirst` in a FIFO queue pattern
- You're using it as a `Deque` and need both-end access
- Prepend-heavy workloads

**When NOT to use LinkedList**:
- Any random access by index (O(n) is slow)
- Iteration-heavy workloads (poor cache locality — nodes scattered in heap)
- **For most use cases, `ArrayDeque` is faster** as a queue or deque

---

## 3. ArrayDeque — The Better Stack and Queue

`ArrayDeque` is backed by a circular resizable array. No boxing, no node objects, contiguous memory = excellent cache performance:

```
Circular buffer (capacity 16, head=12, tail=4):
 [_, _, _, _, E, E, E, E, _, _, _, _, E, E, E, E]
              0  1  2  3                 12 13 14 15
              ↑ tail=4                  ↑ head=12

Conceptually: head→[E, E, E, E, ---empty---, E, E, E, E]←tail
```

```java
// As a stack (LIFO) — preferred over java.util.Stack
Deque<String> stack = new ArrayDeque<>();
stack.push("x");    // addFirst
stack.pop();        // removeFirst
stack.peek();       // peekFirst

// As a queue (FIFO) — preferred over LinkedList
Queue<String> queue = new ArrayDeque<>();
queue.offer("x");   // addLast
queue.poll();       // removeFirst (null if empty)
queue.peek();       // peekFirst (null if empty)

// As a deque (double-ended queue)
deque.offerFirst("x"); // add to front
deque.offerLast("y");  // add to back
deque.pollFirst();     // remove from front
deque.pollLast();      // remove from back

// Iteration is very cache-friendly — sequential array access
```

**`ArrayDeque` vs `LinkedList`**:
| | ArrayDeque | LinkedList |
|---|---|---|
| Internal | Circular array | Doubly-linked nodes |
| Memory overhead | ~0 per element | 24 bytes per node |
| Cache behavior | Excellent (contiguous) | Poor (scattered nodes) |
| Random access | No | No |
| All ends O(1)? | Yes (amortized) | Yes |
| Null elements allowed | No | Yes |

**Recommendation**: Always prefer `ArrayDeque` as a stack or queue unless you need `null` elements.

---

## 4. PriorityQueue — Min-Heap

`PriorityQueue<E>` is a **binary min-heap** stored in an array:

```
Min-heap example (numbers):
          1 (index 0)
        /       \
       3         2 (index 1, 2)
      / \       / \
     7   5     4   6 (index 3, 4, 5, 6)

Array representation: [1, 3, 2, 7, 5, 4, 6]
Parent of node i: (i - 1) / 2
Left child of i:  2i + 1
Right child of i: 2i + 2
```

```java
// Min-heap of integers (natural order)
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(5);  // O(log n) — sifts up until heap property restored
pq.offer(1);
pq.offer(3);

pq.peek();    // O(1) — return minimum element (head of heap)
pq.poll();    // O(log n) — remove and return minimum; sifts down top element

// Max-heap
PriorityQueue<Integer> maxPq = new PriorityQueue<>(Comparator.reverseOrder());

// Custom objects
PriorityQueue<Task> taskQueue = new PriorityQueue<>(
    Comparator.comparingInt(Task::getPriority)
              .thenComparing(Task::getDeadline)
);

// Bulk operations
PriorityQueue<Integer> pq = new PriorityQueue<>(Arrays.asList(5, 1, 3)); // O(n) heapify
pq.addAll(moreItems);

// Iteration does NOT give sorted order — it's heap order!
for (Integer item : pq) { ... }         // heap order (not sorted)
while (!pq.isEmpty()) pq.poll();        // this gives sorted order, O(n log n)
```

**Interview note**: `PriorityQueue` is NOT thread-safe. For concurrent use: `PriorityBlockingQueue`.

---

## 5. Blocking Queues — The Thread-Safe Producer-Consumer API

All `BlockingQueue` implementations:
- `put(e)` — blocks if queue is full
- `take()` — blocks if queue is empty
- `offer(e, timeout, unit)` — blocks up to timeout
- `poll(timeout, unit)` — blocks up to timeout
- `offer(e)` / `poll()` — non-blocking (return false/null on fail)

```java
// ArrayBlockingQueue — bounded, backed by fixed array (preallocated)
BlockingQueue<Task> q = new ArrayBlockingQueue<>(100); // capacity 100, fair=false
BlockingQueue<Task> q = new ArrayBlockingQueue<>(100, true); // fair (FIFO waiter queue)

// LinkedBlockingQueue — optionally bounded, backed by linked nodes
BlockingQueue<Task> q = new LinkedBlockingQueue<>();      // unbounded (risky for OOM!)
BlockingQueue<Task> q = new LinkedBlockingQueue<>(1000); // bounded — safer

// PriorityBlockingQueue — unbounded, sorted, blocks only on take when empty
BlockingQueue<Task> q = new PriorityBlockingQueue<>(11,
    Comparator.comparingInt(Task::getPriority));
q.put(task); // never blocks (unbounded) — careful with memory

// SynchronousQueue — zero capacity — each put blocks until a take is waiting
BlockingQueue<Runnable> q = new SynchronousQueue<>();
// Used in CachedThreadPool — direct handoff from producer to consumer thread

// DelayQueue — elements become available only after their delay expires
class ScheduledTask implements Delayed {
    private final long readyAt; // nanoseconds
    ScheduledTask(long delayMs) { this.readyAt = System.nanoTime() + delayMs * 1_000_000; }
    public long getDelay(TimeUnit unit) { return unit.convert(readyAt - System.nanoTime(), NANOSECONDS); }
    public int compareTo(Delayed o) { return Long.compare(this.readyAt, ((ScheduledTask)o).readyAt); }
}
BlockingQueue<ScheduledTask> q = new DelayQueue<>();
q.put(new ScheduledTask(5000)); // available in 5 seconds
q.take(); // blocks until earliest task's delay expires

// Classic producer-consumer with BlockingQueue
ExecutorService producers = Executors.newFixedThreadPool(3);
ExecutorService consumers = Executors.newFixedThreadPool(5);
BlockingQueue<Work> channel = new LinkedBlockingQueue<>(500);

producers.submit(() -> {
    while (true) channel.put(fetchWork()); // blocks when full
});
consumers.submit(() -> {
    while (true) processWork(channel.take()); // blocks when empty
});
```

**Choosing a BlockingQueue**:
| Queue | Bounded? | Ordered? | Best for |
|---|---|---|---|
| `ArrayBlockingQueue` | Yes | FIFO | Memory-predictable pipelines |
| `LinkedBlockingQueue` | Optional | FIFO | General-purpose (bound it!) |
| `PriorityBlockingQueue` | No | Priority | Priority processing |
| `SynchronousQueue` | No (0 cap) | N/A | CachedThreadPool, direct handoff |
| `DelayQueue` | No | Delay | Scheduling, retry with backoff |

---

## 6. CopyOnWriteArrayList — Safe Concurrent Iteration

Every mutation (add, remove, set) **copies the entire backing array**:

```java
CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();

// Write: O(n) — copies the array, inserts element, swaps reference atomically
listeners.add(listener);
listeners.remove(listener);

// Read/iteration: O(n) — iterates over a snapshot array taken at iterator creation
// No ConcurrentModificationException — iterator holds its own snapshot reference
for (EventListener l : listeners) {  // safe — iterates the snapshot
    l.onEvent(event);
    // listeners.remove(l) in another thread during iteration is fine — iterates old snapshot
}

// CopyOnWriteArraySet<E> = CopyOnWriteArrayList + Set semantics
CopyOnWriteArraySet<String> set = new CopyOnWriteArraySet<>();
```

**Use when**: writes are rare, reads are frequent, and snapshot-consistent iteration is acceptable. Classic use: event listener lists, configuration subscribers.

**Do NOT use when**: writes are frequent (copying is expensive), list is large (copying is slow).

---

## 7. Immutable Lists — List.of vs List.copyOf vs unmodifiableList

```java
// List.of() — truly immutable, no nulls, random-access
List<String> fixed = List.of("a", "b", "c"); // unordered in iteration? No — order maintained
fixed.add("d");    // UnsupportedOperationException
fixed.set(0, "x"); // UnsupportedOperationException
fixed.contains(null); // NullPointerException (no nulls allowed)

// List.copyOf() — defensive copy + immutable
List<String> snapshot = List.copyOf(mutableList); // copies then freezes
// Modifications to mutableList after copyOf() do NOT affect snapshot

// Collections.unmodifiableList() — view, NOT a copy — changes to backing list ARE visible
List<String> mutable = new ArrayList<>(List.of("a", "b"));
List<String> view = Collections.unmodifiableList(mutable);
mutable.add("c");
System.out.println(view.size()); // 3 — view reflects the change!
view.add("d"); // UnsupportedOperationException
```

**Summary**:
| | `List.of` | `List.copyOf` | `unmodifiableList` |
|---|---|---|---|
| Truly immutable | Yes | Yes | No (view only) |
| Nulls | No | No | Yes |
| Backed by original | No | No (copy) | Yes |
| Use case | Small literal lists | Defensive API returns | Temporary read-only view |

---

## 8. Iteration Patterns and Pitfalls

```java
// WRONG — ConcurrentModificationException (fail-fast iterator)
for (String s : list) {
    if (condition(s)) list.remove(s); // modifies list while iterating
}

// CORRECT — removeIf (efficient, no CME)
list.removeIf(s -> condition(s));

// CORRECT — Iterator.remove()
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (condition(it.next())) it.remove(); // safe removal via iterator
}

// CORRECT — collect to separate list then bulk remove
List<String> toRemove = list.stream().filter(s -> condition(s)).toList();
list.removeAll(toRemove);

// IndexedBasedRemoval — remove from back to front to keep indices valid
for (int i = list.size() - 1; i >= 0; i--) {
    if (condition(list.get(i))) list.remove(i);
}
```

---

## 9. Interview Q&A

**Q: Why is `ArrayDeque` preferred over `Stack` and `LinkedList` for stack/queue use?**  
`java.util.Stack` extends `Vector`, which synchronizes every method — expensive even in single-threaded code. `LinkedList` as a queue works but each element requires a `Node` object (24 bytes overhead), and nodes are scattered in heap (poor CPU cache performance). `ArrayDeque` uses a circular array: no synchronization, no object overhead per element, contiguous memory layout = excellent cache hit rate. For both stack and queue use, `ArrayDeque` is almost always faster.

**Q: What happens in ArrayList when you call `remove(int index)` on a large list frequently?**  
Each `remove(int index)` call invokes `System.arraycopy` to shift all elements after the removed index one position left — O(n) work. If you remove many elements in a loop this becomes O(n²). The fix: (1) use `removeIf(predicate)` which does a single pass, (2) use `Iterator.remove()` which is one pass, or (3) collect surviving elements into a new list (if removals are many).

**Q: When would you use `PriorityBlockingQueue` vs `ArrayBlockingQueue`?**  
`ArrayBlockingQueue`: when you need strict FIFO ordering and a bounded queue to create backpressure (block the producer when consumers are slow). `PriorityBlockingQueue`: when you need priority-ordered processing (e.g., high-priority jobs served first regardless of arrival order). `PriorityBlockingQueue` is unbounded — you must monitor queue size and apply backpressure externally to avoid OOM.

**Q: What is the difference between `offer()` and `put()` in a BlockingQueue?**  
`put(e)` blocks the calling thread if the queue is at capacity until space becomes available — suitable for producers in a pipeline where blocking is acceptable. `offer(e)` returns `false` immediately if the queue is full — suitable for fire-and-forget producers that back off or discard when busy. `offer(e, timeout, unit)` is the middle ground: block up to a timeout, then return false. For most producer-consumer pipelines, `put` is the right choice; for non-blocking event handling, `offer` is more appropriate.
