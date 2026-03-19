# Intervals — Complete Theory (Basic → Advanced)

---

## 1. What Are Interval Problems?

An interval is a range `[start, end]` (inclusive) representing a continuous segment on a number line. Interval problems involve:

- **Merging** overlapping intervals
- **Inserting** a new interval into a sorted list
- **Finding** overlapping, non-overlapping, or gap intervals
- **Scheduling** (maximize tasks, minimize rooms)
- **Sweep line** techniques over event points

**Core overlap check:**
```
A: [s1, e1]
B: [s2, e2]

A and B overlap ⟺ s1 <= e2 && s2 <= e1

Equivalently, they DON'T overlap ⟺ e1 < s2 || e2 < s1
```

---

## 2. Merge Intervals

```
Input:  [[1,3],[2,6],[8,10],[15,18]]
Output: [[1,6],[8,10],[15,18]]
```

**Algorithm**: sort by start → merge greedily:
```java
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> res = new ArrayList<>();
    int[] curr = intervals[0];
    for (int i = 1; i < intervals.length; i++) {
        if (intervals[i][0] <= curr[1])   // overlaps or touches
            curr[1] = Math.max(curr[1], intervals[i][1]);
        else { res.add(curr); curr = intervals[i]; }
    }
    res.add(curr);
    return res.toArray(new int[0][]);
}
```

**Time**: O(n log n) for sort + O(n) scan = **O(n log n)**
**Space**: O(n)

---

## 3. Insert Interval

```
Input:  [[1,3],[6,9]], newInterval=[2,5]
Output: [[1,5],[6,9]]
```

Three phases: add all before new interval, merge overlapping, add all after:
```java
public int[][] insert(int[][] intervals, int[] newInterval) {
    List<int[]> res = new ArrayList<>();
    int i = 0, n = intervals.length;

    // Phase 1: add all intervals ending before newInterval starts
    while (i < n && intervals[i][1] < newInterval[0])
        res.add(intervals[i++]);

    // Phase 2: merge overlapping intervals with newInterval
    while (i < n && intervals[i][0] <= newInterval[1]) {
        newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
        newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
        i++;
    }
    res.add(newInterval);

    // Phase 3: add remaining intervals
    while (i < n) res.add(intervals[i++]);
    return res.toArray(new int[0][]);
}
```

**Time**: O(n) — single pass (no sort needed if input is sorted).

---

## 4. Non-Overlapping Intervals (Minimum Removals)

```
Input:  [[1,2],[2,3],[3,4],[1,3]]
Remove minimum intervals so no two intervals overlap.
Output: 1  (remove [1,3])
```

**Greedy**: sort by end time → keep the interval that ends earliest:
```java
public int eraseOverlapIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);  // sort by END
    int count = 0, prevEnd = intervals[0][1];
    for (int i = 1; i < intervals.length; i++) {
        if (intervals[i][0] < prevEnd) count++;      // overlaps: remove this one
        else prevEnd = intervals[i][1];              // keep: update boundary
    }
    return count;
}
```

This is the classic **Activity Selection Problem** (greedy — always pick earliest finish).

---

## 5. Meeting Rooms I & II

### Meeting Rooms I — Can a person attend all meetings?
```java
// No two intervals should overlap
public boolean canAttendMeetings(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    for (int i = 1; i < intervals.length; i++)
        if (intervals[i][0] < intervals[i-1][1]) return false;
    return true;
}
```

### Meeting Rooms II — Minimum conference rooms needed
```java
// Count max simultaneous overlapping intervals
public int minMeetingRooms(int[][] intervals) {
    int n = intervals.length;
    int[] start = new int[n], end = new int[n];
    for (int i = 0; i < n; i++) { start[i] = intervals[i][0]; end[i] = intervals[i][1]; }
    Arrays.sort(start); Arrays.sort(end);
    int rooms = 0, maxRooms = 0, e = 0;
    for (int s = 0; s < n; s++) {
        if (start[s] < end[e]) rooms++;    // new meeting starts before a current one ends
        else e++;                           // a room freed up
        maxRooms = Math.max(maxRooms, rooms);
    }
    return maxRooms;
}
```

**Alternative with min-heap (end times)**:
```java
public int minMeetingRooms(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    PriorityQueue<Integer> pq = new PriorityQueue<>();  // stores end times
    for (int[] iv : intervals) {
        if (!pq.isEmpty() && pq.peek() <= iv[0]) pq.poll();  // free a room
        pq.offer(iv[1]);
    }
    return pq.size();
}
```

---

## 6. Sweep Line

Process **start** and **end** events in sorted order:

```
Events: for each interval [s, e] → add event (s, +1) and (e, -1)
Sort events; tie-break: ends before starts at same point (or vice versa depending on problem)

Running count = max simultaneous active intervals
```

```java
public int maxOverlap(int[][] intervals) {
    List<int[]> events = new ArrayList<>();
    for (int[] iv : intervals) {
        events.add(new int[]{iv[0], 1});   // start
        events.add(new int[]{iv[1], -1});  // end
    }
    events.sort((a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);  // ends before starts at same time
    int count = 0, max = 0;
    for (int[] e : events) { count += e[1]; max = Math.max(max, count); }
    return max;
}
```

---

## 7. TreeMap for Dynamic Intervals

`TreeMap` provides `floorKey`, `ceilingKey`, `higherKey`, `lowerKey` for efficient interval lookups.

### My Calendar I — No Double Booking
```java
TreeMap<Integer, Integer> calendar = new TreeMap<>();

public boolean book(int start, int end) {
    // Check if any existing interval overlaps [start, end)
    Integer prevStart = calendar.floorKey(start);
    Integer nextStart = calendar.ceilingKey(start);
    // prev interval didn't end before start?
    if (prevStart != null && calendar.get(prevStart) > start) return false;
    // next interval started before end?
    if (nextStart != null && nextStart < end) return false;
    calendar.put(start, end);
    return true;
}
```

### My Calendar III — Maximum Simultaneous Bookings
```java
TreeMap<Integer, Integer> delta = new TreeMap<>();

public int book(int start, int end) {
    delta.merge(start,  1, Integer::sum);
    delta.merge(end,   -1, Integer::sum);
    int maxBook = 0, currBook = 0;
    for (int v : delta.values()) { currBook += v; maxBook = Math.max(maxBook, currBook); }
    return maxBook;
}
```

---

## 8. Interval Scheduling Maximisation

Classic greedy problem (weighted interval scheduling):

**Unweighted** — pick maximum non-overlapping intervals:
```
Sort by end time. Greedily pick each interval if it doesn't conflict with last picked.
This is the Activity Selection Problem.
```

```java
public int maxActivities(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
    int count = 1, lastEnd = intervals[0][1];
    for (int i = 1; i < intervals.length; i++)
        if (intervals[i][0] >= lastEnd) { count++; lastEnd = intervals[i][1]; }
    return count;
}
```

**Weighted** — use DP + binary search:
```java
// dp[i] = max profit from first i intervals (sorted by end)
// dp[i] = max(dp[i-1], weight[i] + dp[latest non-overlapping before i])
// find latest non-overlapping: binary search on end times
```

---

## 9. Interval List Intersections

```
A = [[0,2],[5,10],[13,23],[24,25]]
B = [[1,5],[8,12],[15,24],[25,26]]
Intersection: [[1,2],[5,5],[8,10],[15,23],[24,24],[25,25]]
```

```java
public int[][] intervalIntersection(int[][] A, int[][] B) {
    List<int[]> res = new ArrayList<>();
    int i = 0, j = 0;
    while (i < A.length && j < B.length) {
        int lo = Math.max(A[i][0], B[j][0]);
        int hi = Math.min(A[i][1], B[j][1]);
        if (lo <= hi) res.add(new int[]{lo, hi});
        if (A[i][1] < B[j][1]) i++;    // advance the one that ends sooner
        else j++;
    }
    return res.toArray(new int[0][]);
}
```

---

## 10. Count Days Covered / Gaps

```java
// Given intervals, find total days covered (no double-counting)
public int totalCoverage(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    int total = 0, currEnd = Integer.MIN_VALUE;
    for (int[] iv : intervals) {
        int start = Math.max(iv[0], currEnd);   // don't re-count overlap
        if (start < iv[1]) total += iv[1] - start;
        currEnd = Math.max(currEnd, iv[1]);
    }
    return total;
}
```

---

## 11. Complexity Reference

| Operation | Time | Space |
|---|---|---|
| Merge intervals | O(n log n) | O(n) |
| Insert interval (sorted input) | O(n) | O(n) |
| Min meeting rooms (sort + heap) | O(n log n) | O(n) |
| Sweep line | O(n log n) | O(n) |
| TreeMap book (single) | O(log n) | O(n) |
| Interval intersection | O(m + n) | O(m + n) |
| Activity selection | O(n log n) | O(1) |

---

## 12. Decision Guide

| Scenario | Pattern |
|---|---|
| Merge all overlapping intervals | Sort by start + greedy merge |
| Insert one interval into sorted list | Three-phase scan |
| Minimum ranges to remove | Sort by end + greedy (Activity Selection) |
| Maximum parallel intervals (rooms) | Sort starts + ends separately OR min-heap of end times |
| Dynamic interval booking with overlap check | TreeMap floorKey/ceilingKey |
| Count simultaneous events | Sweep line events |
| Intersection of two interval lists | Two-pointer merge |
| Total days covered | Sort + running max end |

---

## 13. Common Pitfalls

- **Sort by start for merge, sort by end for activity selection** — getting this backward gives wrong results
- **Overlap condition direction**: `a[0] <= b[1] && b[0] <= a[1]` (works for closed intervals); for half-open `[s, e)` use `a[0] < b[1] && b[0] < a[1]`
- **Tie-breaking in sweep line**: at the same timestamp, process end events before start events if the problem uses closed intervals (e.g., [1,5] and [5,10] don't overlap at 5)
- **TreeMap calendar**: use `floorKey` (not `lowerKey`) to find the most recent interval that started at or before `start`
- **Meeting rooms heap**: add `iv[1]` after checking (not before), or size will be wrong on the first iteration
- **Integer overflow in sorting comparator**: use `Integer.compare(a[0], b[0])` not `a[0] - b[0]` when values can be negative
