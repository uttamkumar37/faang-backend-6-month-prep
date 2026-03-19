# Heaps (Priority Queue) — Practice Questions

---

## 🟢 Easy (5)

### E1. Kth Largest Element in a Stream
Design a class that returns the kth largest element in a stream after each add.  
**Hint:** Min-heap of size k; if new element > heap.peek(), remove peek and add new element.  
**Complexity:** O(log k) per add, O(k) space.

```java
class KthLargest {
    private final PriorityQueue<Integer> heap;
    private final int k;
    public KthLargest(int k, int[] nums) {
        this.k = k;
        heap = new PriorityQueue<>();
        for (int n : nums) add(n);
    }
    public int add(int val) {
        heap.offer(val);
        if (heap.size() > k) heap.poll();
        return heap.peek();
    }
}
```

### E2. Last Stone Weight
Smash the two heaviest stones repeatedly; return remaining weight (0 if none).  
**Hint:** Max-heap; extract two largest, push difference if not equal.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int lastStoneWeight(int[] stones) {
    PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    for (int s : stones) maxHeap.offer(s);
    while (maxHeap.size() > 1) {
        int a = maxHeap.poll(), b = maxHeap.poll();
        if (a != b) maxHeap.offer(a - b);
    }
    return maxHeap.isEmpty() ? 0 : maxHeap.peek();
}
```

### E3. Relative Ranks
Assign medals (Gold/Silver/Bronze) and ranks to athletes based on scores.  
**Hint:** Sort indices by score descending; assign rank/medal based on position.  
**Complexity:** O(n log n) time, O(n) space.

```java
public String[] findRelativeRanks(int[] score) {
    int n = score.length;
    Integer[] idx = new Integer[n];
    for (int i = 0; i < n; i++) idx[i] = i;
    Arrays.sort(idx, (a, b) -> score[b] - score[a]);
    String[] res = new String[n];
    for (int i = 0; i < n; i++) {
        if      (i == 0) res[idx[i]] = "Gold Medal";
        else if (i == 1) res[idx[i]] = "Silver Medal";
        else if (i == 2) res[idx[i]] = "Bronze Medal";
        else             res[idx[i]] = String.valueOf(i + 1);
    }
    return res;
}
```

### E4. Take Gifts From Richest Pile
Each second, take floor(sqrt) from the richest pile, repeat k times. Return remaining sum.  
**Hint:** Max-heap; extract top, push floor(sqrt(top)) back, repeat k times.  
**Complexity:** O(k log n) time, O(n) space.

```java
public long pickGifts(int[] gifts, int k) {
    PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    for (int g : gifts) maxHeap.offer(g);
    for (int i = 0; i < k; i++) {
        int top = maxHeap.poll();
        maxHeap.offer((int) Math.sqrt(top));
    }
    long sum = 0;
    for (int g : maxHeap) sum += g;
    return sum;
}
```

### E5. Find Closest Number to Zero in Array
Return the number closest to zero (if tie, return positive).  
**Hint:** Linear scan — no heap needed. Track min absolute value, break ties by positivity.  
**Complexity:** O(n) time, O(1) space.

```java
public int findClosestNumber(int[] nums) {
    int closest = nums[0];
    for (int n : nums) {
        if (Math.abs(n) < Math.abs(closest) || (Math.abs(n) == Math.abs(closest) && n > closest))
            closest = n;
    }
    return closest;
}
```

---

## 🟡 Medium (10)

### M1. Kth Largest Element in an Array
Find the kth largest element without sorting the entire array.  
**Hint:** Min-heap of size k; push each element, pop if size > k. Top of heap is the answer.  
**Complexity:** O(n log k) time, O(k) space.

```java
public int findKthLargest(int[] nums, int k) {
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    for (int n : nums) {
        minHeap.offer(n);
        if (minHeap.size() > k) minHeap.poll();
    }
    return minHeap.peek();
}
```

### M2. Top K Frequent Elements
Return the k most frequent elements.  
**Hint:** HashMap for frequencies; min-heap of size k on (freq, element); or bucket sort O(n).  
**Complexity:** O(n log k) time, O(n) space.

```java
public int[] topKFrequent(int[] nums, int k) {
    Map<Integer,Integer> freq = new HashMap<>();
    for (int n : nums) freq.merge(n, 1, Integer::sum);
    PriorityQueue<Integer> minHeap = new PriorityQueue<>(Comparator.comparingInt(freq::get));
    for (int key : freq.keySet()) {
        minHeap.offer(key);
        if (minHeap.size() > k) minHeap.poll();
    }
    return minHeap.stream().mapToInt(Integer::intValue).toArray();
}
```

### M3. K Closest Points to Origin
Find k closest points to origin (0,0).  
**Hint:** Max-heap of size k by distance; pop if new point's distance < heap top.  
**Complexity:** O(n log k) time, O(k) space.

```java
public int[][] kClosest(int[][] points, int k) {
    PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
        (a, b) -> (b[0]*b[0]+b[1]*b[1]) - (a[0]*a[0]+a[1]*a[1]));
    for (int[] p : points) {
        maxHeap.offer(p);
        if (maxHeap.size() > k) maxHeap.poll();
    }
    return maxHeap.toArray(new int[k][]);
}
```

### M4. Task Scheduler
Find minimum intervals to finish all tasks (same task needs `n` cooldown).  
**Hint:** Max-heap for task frequencies; each round, pick top tasks, track idle time needed.  
**Complexity:** O(n) time (bounded by 26 task types).

```java
public int leastInterval(char[] tasks, int n) {
    int[] freq = new int[26];
    for (char c : tasks) freq[c - 'A']++;
    PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
    for (int f : freq) if (f > 0) maxHeap.offer(f);
    int time = 0;
    Queue<int[]> cooldown = new LinkedList<>(); // [remainFreq, availableAt]
    while (!maxHeap.isEmpty() || !cooldown.isEmpty()) {
        if (!cooldown.isEmpty() && cooldown.peek()[1] <= time)
            maxHeap.offer(cooldown.poll()[0]);
        if (!maxHeap.isEmpty()) {
            int rem = maxHeap.poll() - 1;
            if (rem > 0) cooldown.offer(new int[]{rem, time + n + 1});
        }
        time++;
    }
    return time;
}
```

### M5. Reorganize String
Rearrange string so no two adjacent characters are the same, else return "".  
**Hint:** Max-heap by char frequency; always pick the two most frequent and alternate.  
**Complexity:** O(n log n) time, O(n) space.

```java
public String reorganizeString(String s) {
    int[] freq = new int[26];
    for (char c : s.toCharArray()) freq[c - 'a']++;
    PriorityQueue<int[]> maxHeap = new PriorityQueue<>((a, b) -> b[1] - a[1]);
    for (int i = 0; i < 26; i++) if (freq[i] > 0) maxHeap.offer(new int[]{i, freq[i]});
    StringBuilder sb = new StringBuilder();
    while (maxHeap.size() >= 2) {
        int[] a = maxHeap.poll(), b = maxHeap.poll();
        sb.append((char)('a' + a[0])).append((char)('a' + b[0]));
        if (--a[1] > 0) maxHeap.offer(a);
        if (--b[1] > 0) maxHeap.offer(b);
    }
    if (!maxHeap.isEmpty()) {
        int[] last = maxHeap.poll();
        if (last[1] > 1) return "";
        sb.append((char)('a' + last[0]));
    }
    return sb.toString();
}
```

### M6. Find K Pairs with Smallest Sums
Given two sorted arrays, find k pairs (one from each) with the smallest pairwise sum.  
**Hint:** Min-heap initialized with (nums1[i], nums2[0]) for all i; expand by incrementing nums2 index.  
**Complexity:** O(k log k) time, O(k) space.

```java
public List<List<Integer>> kSmallestPairs(int[] nums1, int[] nums2, int k) {
    List<List<Integer>> res = new ArrayList<>();
    PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> (a[0]+a[1]) - (b[0]+b[1]));
    for (int i = 0; i < Math.min(nums1.length, k); i++)
        minHeap.offer(new int[]{nums1[i], nums2[0], 0}); // val1, val2, idx2
    while (!minHeap.isEmpty() && res.size() < k) {
        int[] cur = minHeap.poll();
        res.add(Arrays.asList(cur[0], cur[1]));
        if (cur[2] + 1 < nums2.length)
            minHeap.offer(new int[]{cur[0], nums2[cur[2]+1], cur[2]+1});
    }
    return res;
}
```

### M7. Minimum Cost to Connect Sticks
Connect all sticks with minimum cost (cost = sum of two sticks being connected).  
**Hint:** Min-heap (Huffman coding); always merge two smallest sticks first.  
**Complexity:** O(n log n) time.

```java
public long connectSticks(int[] sticks) {
    PriorityQueue<Long> minHeap = new PriorityQueue<>();
    for (int s : sticks) minHeap.offer((long) s);
    long cost = 0;
    while (minHeap.size() > 1) {
        long merged = minHeap.poll() + minHeap.poll();
        cost += merged;
        minHeap.offer(merged);
    }
    return cost;
}
```

### M8. Single-Threaded CPU
Given tasks with enqueueTime and processingTime, simulate CPU scheduling (pick earliest-available, shortest job).  
**Hint:** Sort by enqueue time; min-heap by (processingTime, taskIndex) for available tasks.  
**Complexity:** O(n log n) time.

```java
public int[] getOrder(int[][] tasks) {
    int n = tasks.length;
    Integer[] idx = new Integer[n];
    for (int i = 0; i < n; i++) idx[i] = i;
    Arrays.sort(idx, (a, b) -> tasks[a][0] - tasks[b][0]);
    PriorityQueue<int[]> available = new PriorityQueue<>(
        (a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
    int[] order = new int[n];
    long time = 0; int i = 0, j = 0;
    while (j < n) {
        while (i < n && tasks[idx[i]][0] <= time) available.offer(new int[]{tasks[idx[i]][1], idx[i++]});
        if (available.isEmpty()) { time = tasks[idx[i]][0]; continue; }
        int[] task = available.poll();
        order[j++] = task[1];
        time += task[0];
    }
    return order;
}
```

### M9. Seat Reservation Manager
Implement a seat reservation manager (reserve smallest available seat, unreserve any).  
**Hint:** Min-heap initialized with all seat numbers; push on unreserve, pop on reserve.  
**Complexity:** O(log n) per operation.

```java
class SeatManager {
    private final PriorityQueue<Integer> available;
    public SeatManager(int n) {
        available = new PriorityQueue<>();
        for (int i = 1; i <= n; i++) available.offer(i);
    }
    public int reserve()         { return available.poll(); }
    public void unreserve(int s) { available.offer(s); }
}
```

### M10. Maximum Subsequence Score
Pick k indices to maximize score = (sum of nums1 values) * (min of nums2 values at those indices).  
**Hint:** Sort by nums2 descending; iterate and maintain a min-heap of size k for nums1 values.  
**Complexity:** O(n log n) time, O(k) space.

```java
public long maxScore(int[] nums1, int[] nums2, int k) {
    int n = nums1.length;
    Integer[] idx = new Integer[n];
    for (int i = 0; i < n; i++) idx[i] = i;
    Arrays.sort(idx, (a, b) -> nums2[b] - nums2[a]);
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    long sumTop = 0, best = 0;
    for (int i : idx) {
        minHeap.offer(nums1[i]); sumTop += nums1[i];
        if (minHeap.size() > k) sumTop -= minHeap.poll();
        if (minHeap.size() == k) best = Math.max(best, sumTop * (long) nums2[i]);
    }
    return best;
}
```

---

## 🔴 Hard (5)

### H1. Find Median from Data Stream
Add numbers from a stream; retrieve the median at any time.  
**Hint:** Two heaps: max-heap for lower half, min-heap for upper half. Balance so sizes differ by at most 1.  
**Complexity:** O(log n) per add, O(1) per median, O(n) space.

```java
class MedianFinder {
    private PriorityQueue<Integer> lo = new PriorityQueue<>(Collections.reverseOrder()); // max-heap
    private PriorityQueue<Integer> hi = new PriorityQueue<>();                           // min-heap
    public void addNum(int num) {
        lo.offer(num);
        hi.offer(lo.poll());
        if (lo.size() < hi.size()) lo.offer(hi.poll());
    }
    public double findMedian() {
        return lo.size() > hi.size() ? lo.peek() : (lo.peek() + hi.peek()) / 2.0;
    }
}
```

### H2. Merge K Sorted Lists
Merge k sorted linked lists into one sorted list.  
**Hint:** Min-heap of (value, node); repeatedly extract min and push its next node.  
**Complexity:** O(N log k) time, O(k) space.

```java
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> minHeap = new PriorityQueue<>(Comparator.comparingInt(a -> a.val));
    for (ListNode l : lists) if (l != null) minHeap.offer(l);
    ListNode dummy = new ListNode(0), cur = dummy;
    while (!minHeap.isEmpty()) {
        cur.next = minHeap.poll();
        cur = cur.next;
        if (cur.next != null) minHeap.offer(cur.next);
    }
    return dummy.next;
}
```

### H3. IPO
Select at most `k` projects to maximize final capital (each project requires min capital).  
**Hint:** Sort by capital; add affordable projects to max-heap by profit; pick top profit each round.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int findMaximizedCapital(int k, int w, int[] profits, int[] capital) {
    int n = profits.length;
    int[][] projects = new int[n][2];
    for (int i = 0; i < n; i++) projects[i] = new int[]{capital[i], profits[i]};
    Arrays.sort(projects, (a, b) -> a[0] - b[0]);
    PriorityQueue<Integer> maxProfit = new PriorityQueue<>(Collections.reverseOrder());
    int i = 0;
    for (int j = 0; j < k; j++) {
        while (i < n && projects[i][0] <= w) maxProfit.offer(projects[i++][1]);
        if (maxProfit.isEmpty()) break;
        w += maxProfit.poll();
    }
    return w;
}
```

### H4. Smallest Range Covering Elements from K Lists
Find the smallest range [a,b] such that it includes at least one number from each list.  
**Hint:** Min-heap with one element per list initially; expand range by replacing min element with next from its list.  
**Complexity:** O(N log k) time, O(k) space.

```java
public int[] smallestRange(List<List<Integer>> nums) {
    PriorityQueue<int[]> minHeap = new PriorityQueue<>(Comparator.comparingInt(a -> a[0]));
    int max = Integer.MIN_VALUE;
    for (int i = 0; i < nums.size(); i++) {
        minHeap.offer(new int[]{nums.get(i).get(0), i, 0});
        max = Math.max(max, nums.get(i).get(0));
    }
    int[] res = {minHeap.peek()[0], max};
    while (true) {
        int[] cur = minHeap.poll();
        if (cur[2] + 1 == nums.get(cur[1]).size()) break;
        int next = nums.get(cur[1]).get(cur[2] + 1);
        minHeap.offer(new int[]{next, cur[1], cur[2] + 1});
        max = Math.max(max, next);
        int curMin = minHeap.peek()[0];
        if (max - curMin < res[1] - res[0]) res = new int[]{curMin, max};
    }
    return res;
}
```

### H5. Maximum Performance of a Team
Pick at most `k` engineers to maximize sum(speeds) * min(efficiency).  
**Hint:** Sort by efficiency descending; maintain min-heap of size k for speeds. Track max at each step.  
**Complexity:** O(n log k) time, O(k) space.

```java
public int maxPerformance(int n, int[] speed, int[] efficiency, int k) {
    int MOD = 1_000_000_007;
    Integer[] idx = new Integer[n];
    for (int i = 0; i < n; i++) idx[i] = i;
    Arrays.sort(idx, (a, b) -> efficiency[b] - efficiency[a]);
    PriorityQueue<Integer> minHeap = new PriorityQueue<>();
    long sumSpeed = 0, best = 0;
    for (int i : idx) {
        minHeap.offer(speed[i]); sumSpeed += speed[i];
        if (minHeap.size() > k) sumSpeed -= minHeap.poll();
        best = Math.max(best, sumSpeed * (long) efficiency[i]);
    }
    return (int)(best % MOD);
}
```
