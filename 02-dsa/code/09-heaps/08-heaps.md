# Heaps & Priority Queues — Complete Theory (Basic → Advanced)

---

## 1. What Is a Heap?

A **heap** is a complete binary tree stored as an array, satisfying the **heap property**:

- **Min-heap**: every parent ≤ its children → root is the global minimum
- **Max-heap**: every parent ≥ its children → root is the global maximum

```
Min-heap:          Max-heap:
      1                  9
    /   \              /   \
   3     2            7     8
  / \   /            / \   /
 5   4  6           3   4  1

Array:  [1, 3, 2, 5, 4, 6]   [9, 7, 8, 3, 4, 1]
Index:   0  1  2  3  4  5     0  1  2  3  4  5
```

**Index relationships (0-based):**
```
parent(i)     = (i - 1) / 2
left child(i) = 2*i + 1
right child(i)= 2*i + 2
```

**Core operations:**
| Operation | Time |
|---|---|
| Insert (heapify up) | O(log n) |
| Extract min/max (heapify down) | O(log n) |
| Peek min/max | O(1) |
| Build heap from n elements | O(n) |
| Heap sort | O(n log n) |

---

## 2. Java PriorityQueue

`PriorityQueue` in Java is a **min-heap** by default.

```java
// Min-heap (default)
PriorityQueue<Integer> minHeap = new PriorityQueue<>();

// Max-heap
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());

// Custom comparator
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);

// Operations
pq.offer(x);      // insert  — O(log n)
pq.poll();        // remove min — O(log n)
pq.peek();        // view min — O(1)
pq.size();
pq.isEmpty();
```

---

## 3. Top-K Patterns

### Kth Largest Element in Array
**Approach 1: min-heap of size k — O(n log k)**
```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    for (int n : nums) {
        minHeap.offer(n);
        if (minHeap.size() > k) minHeap.poll();   // keep only k largest
    }
    return minHeap.peek();    // smallest of the k largest = kth largest
}
```

**Approach 2: QuickSelect — O(n) average**
```java
public int findKthLargest(int[] nums, int k) {
    return quickSelect(nums, 0, nums.length-1, nums.length - k);
}
private int quickSelect(int[] nums, int lo, int hi, int target) {
    int pivot = partition(nums, lo, hi);
    if   (pivot == target) return nums[pivot];
    else if (pivot < target) return quickSelect(nums, pivot+1, hi, target);
    else return quickSelect(nums, lo, pivot-1, target);
}
private int partition(int[] nums, int lo, int hi) {
    int pivot = nums[hi], i = lo;
    for (int j = lo; j < hi; j++) if (nums[j] <= pivot) { int t = nums[i]; nums[i++] = nums[j]; nums[j] = t; }
    int t = nums[i]; nums[i] = nums[hi]; nums[hi] = t;
    return i;
}
```

### K Closest Points to Origin
```java
public int[][] kClosest(int[][] points, int k) {
    // Max-heap of size k: evict the farthest
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> dist(b) - dist(a));
    for (int[] p : points) {
        pq.offer(p);
        if (pq.size() > k) pq.poll();
    }
    return pq.toArray(new int[0][]);
}
private int dist(int[] p) { return p[0]*p[0] + p[1]*p[1]; }
```

---

## 4. Two-Heap Pattern (Median Maintenance)

Maintain two heaps:
- `lo`: max-heap of lower half
- `hi`: min-heap of upper half

**Invariant**: `lo.size() == hi.size()` or `lo.size() == hi.size() + 1`

```java
class MedianFinder {
    PriorityQueue<Integer> lo = new PriorityQueue<>(Comparator.reverseOrder()); // max-heap
    PriorityQueue<Integer> hi = new PriorityQueue<>();                           // min-heap

    public void addNum(int num) {
        lo.offer(num);                   // always add to lo first
        hi.offer(lo.poll());             // push lo's max to hi (balance)
        if (hi.size() > lo.size())       // keep lo >= hi in size
            lo.offer(hi.poll());
    }

    public double findMedian() {
        return lo.size() > hi.size()
            ? lo.peek()
            : (lo.peek() + hi.peek()) / 2.0;
    }
}
```

---

## 5. K-Way Merge — O(n log k)

Merge k sorted arrays/lists using a min-heap.

```java
public int[] mergeKSorted(int[][] arrays) {
    // heap stores [value, arrayIndex, elementIndex]
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
    int total = 0;
    for (int i = 0; i < arrays.length; i++) {
        if (arrays[i].length > 0) {
            pq.offer(new int[]{arrays[i][0], i, 0});
            total += arrays[i].length;
        }
    }
    int[] res = new int[total];
    int idx = 0;
    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        res[idx++] = curr[0];
        int ai = curr[1], ei = curr[2];
        if (ei + 1 < arrays[ai].length)
            pq.offer(new int[]{arrays[ai][ei+1], ai, ei+1});
    }
    return res;
}
```

---

## 6. Top-K Frequent Elements

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer, Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);
    // min-heap by frequency, size k
    PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.comparingInt(freq::get));
    for (int n : freq.keySet()) {
        pq.offer(n);
        if (pq.size() > k) pq.poll();
    }
    return pq.stream().mapToInt(i -> i).toArray();
}
```

**Alternative: Bucket Sort — O(n)**
```java
// Bucket index = frequency, bucket[i] = list of nums with freq i
List<Integer>[] buckets = new List[nums.length + 1];
for (int n : freq.keySet()) {
    int f = freq.get(n);
    if (buckets[f] == null) buckets[f] = new ArrayList<>();
    buckets[f].add(n);
}
// collect from highest frequency buckets
```

---

## 7. Task Scheduler (CPU Cooling)

```java
// Minimum time to finish tasks with n cooling gap between same tasks
public int leastInterval(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char c : tasks) freq[c-'A']++;
    Arrays.sort(freq);
    int maxFreq = freq[25];
    int idle = (maxFreq - 1) * n;
    for (int i = 24; i >= 0; i--) idle -= Math.min(freq[i], maxFreq - 1);
    return tasks.length + Math.max(0, idle);
}
```

---

## 8. Find Median from Data Stream (sliding window variant)

Use the two-heap approach above. For a sliding window of size k:
```java
// Sliding window median uses a TreeMap or indexed heap for O(log n) removal
// Key insight: you must be able to remove arbitrary elements from both heaps
// Solution: use two TreeMaps (like HeapSort but with removal)
TreeMap<Integer, Integer> lo = new TreeMap<>(Comparator.reverseOrder()); // max
TreeMap<Integer, Integer> hi = new TreeMap<>(); // min
```

---

## 9. Heap Sort (in-place, O(n log n))

```java
public void heapSort(int[] nums) {
    int n = nums.length;
    // Build max-heap (heapify from last non-leaf upward)
    for (int i = n/2 - 1; i >= 0; i--) heapifyDown(nums, i, n);
    // Extract elements one by one
    for (int i = n-1; i > 0; i--) {
        int tmp = nums[0]; nums[0] = nums[i]; nums[i] = tmp;
        heapifyDown(nums, 0, i);
    }
}

private void heapifyDown(int[] nums, int i, int n) {
    int largest = i, l = 2*i+1, r = 2*i+2;
    if (l < n && nums[l] > nums[largest]) largest = l;
    if (r < n && nums[r] > nums[largest]) largest = r;
    if (largest != i) {
        int tmp = nums[i]; nums[i] = nums[largest]; nums[largest] = tmp;
        heapifyDown(nums, largest, n);
    }
}
```

**Why buildHeap is O(n)**: leaves (n/2 nodes) take O(1); each level doubles nodes but halves the work. Sum telescopes to O(n).

---

## 10. Dijkstra's Algorithm (Heap-based)

Covered in Graphs section but uses a min-heap:

```java
PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
// [node, distance]
pq.offer(new int[]{src, 0});
while (!pq.isEmpty()) {
    int[] curr = pq.poll();
    int node = curr[0], dist = curr[1];
    if (dist > distances[node]) continue;  // stale entry
    for (int[] edge : graph[node]) {
        int newDist = dist + edge[1];
        if (newDist < distances[edge[0]]) {
            distances[edge[0]] = newDist;
            pq.offer(new int[]{edge[0], newDist});
        }
    }
}
```

---

## 11. Complexity Summary

| Pattern | Time | Space |
|---|---|---|
| Kth largest (min-heap size k) | O(n log k) | O(k) |
| Kth largest (QuickSelect) | O(n) avg, O(n²) worst | O(1) |
| K-way merge (n total, k lists) | O(n log k) | O(k) |
| Median maintenance (each addNum) | O(log n) | O(n) |
| Build heap | O(n) | O(1) in-place |
| Heap sort | O(n log n) | O(1) |

---

## 12. Decision Guide

| Problem Signal | Heap Pattern |
|---|---|
| Kth largest/smallest | Min-heap of size k |
| Top-k elements | Min-heap size k (max for k-closest) |
| Merge k sorted streams | Min-heap with [val, listIdx, elemIdx] |
| Running median | Two-heap (max-lo, min-hi) |
| Scheduling with cooldown | Frequency analysis |
| Shortest path in weighted graph | Dijkstra with min-heap |

---

## 13. Common Pitfalls

- **Java PriorityQueue default is min-heap**: for max-heap use `Comparator.reverseOrder()`
- **Custom objects**: comparator must be consistent with equals or use `(a, b) -> Integer.compare(a[0], b[0])` not `a[0] - b[0]` (overflow risk with negatives)
- **Two-heap invariant**: always push to lo first, then re-balance; don't split directly
- **QuickSelect modifies array**: if input must be preserved, copy first
- **Heap vs TreeSet**: TreeSet gives O(log n) remove of arbitrary element; standard `PriorityQueue.remove(x)` is O(n)
- **Stale entries in Dijkstra**: check `if (dist > distances[node]) continue` to skip outdated heap entries
