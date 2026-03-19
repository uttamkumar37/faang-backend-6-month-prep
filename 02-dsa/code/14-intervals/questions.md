# Intervals — Practice Questions

---

## 🟢 Easy (5)

### E1. Meeting Rooms I
Given a list of intervals, determine if a person can attend all meetings (no overlaps).  
**Hint:** Sort by start time; check if any `intervals[i].start < intervals[i-1].end`.  
**Complexity:** O(n log n) time, O(1) space.

```java
public boolean canAttendMeetings(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    for (int i = 1; i < intervals.length; i++)
        if (intervals[i][0] < intervals[i-1][1]) return false;
    return true;
}
```

### E2. Remove Covered Intervals
Count the number of intervals that are NOT covered by any other interval in the list.  
**Hint:** Sort by start ascending, end descending. Walk the list and track max end seen so far.  
**Complexity:** O(n log n) time, O(1) space.

```java
public int removeCoveredIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] == b[0] ? b[1] - a[1] : a[0] - b[0]);
    int count = 0, maxEnd = 0;
    for (int[] iv : intervals) {
        if (iv[1] > maxEnd) { count++; maxEnd = iv[1]; }
    }
    return count;
}
```

### E3. Summary Ranges
Convert a sorted unique integer array into the smallest set of ranges that cover all numbers.  
**Hint:** Two-pointer walk — extend range while consecutive, emit when gap breaks.  
**Complexity:** O(n) time, O(1) space.

```java
public List<String> summaryRanges(int[] nums) {
    List<String> res = new ArrayList<>();
    int i = 0, n = nums.length;
    while (i < n) {
        int start = i;
        while (i + 1 < n && nums[i+1] == nums[i] + 1) i++;
        res.add(start == i ? String.valueOf(nums[start]) : nums[start] + "->" + nums[i]);
        i++;
    }
    return res;
}
```

### E4. Maximum Depth of Nested Parentheses
Find the maximum nesting depth of properly nested parentheses (treat `(` as interval open, `)` as close).  
**Hint:** Increment depth on `(`, decrement on `)`; track running maximum.  
**Complexity:** O(n) time, O(1) space.

```java
public int maxDepth(String s) {
    int depth = 0, max = 0;
    for (char c : s.toCharArray()) {
        if (c == '(') max = Math.max(max, ++depth);
        else if (c == ')') depth--;
    }
    return max;
}
```

### E5. Check if Two Events Have Conflict
Given two events as `[startTime, endTime]` (string times "HH:MM"), determine if they overlap.  
**Hint:** Overlap iff `start1 <= end2 && start2 <= end1` — compare strings lexicographically.  
**Complexity:** O(1) time.

```java
public boolean haveConflict(String[] event1, String[] event2) {
    return event1[0].compareTo(event2[1]) <= 0 && event2[0].compareTo(event1[1]) <= 0;
}
```

---

## 🟡 Medium (10)

### M1. Merge Intervals
Merge all overlapping intervals in a list.  
**Hint:** Sort by start; iterate and merge when `current.start <= lastMerged.end` by extending the end.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int[][] merge(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    List<int[]> res = new ArrayList<>();
    for (int[] iv : intervals) {
        if (res.isEmpty() || res.get(res.size()-1)[1] < iv[0]) res.add(iv);
        else res.get(res.size()-1)[1] = Math.max(res.get(res.size()-1)[1], iv[1]);
    }
    return res.toArray(new int[0][]);
}
```

### M2. Insert Interval
Insert a new interval into a sorted non-overlapping list and merge if necessary.  
**Hint:** Add all intervals ending before the new one, merge all overlapping ones, then add the rest.  
**Complexity:** O(n) time, O(n) space.

```java
public int[][] insert(int[][] intervals, int[] newInterval) {
    List<int[]> res = new ArrayList<>();
    int i = 0, n = intervals.length;
    while (i < n && intervals[i][1] < newInterval[0]) res.add(intervals[i++]);
    while (i < n && intervals[i][0] <= newInterval[1]) {
        newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
        newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
        i++;
    }
    res.add(newInterval);
    while (i < n) res.add(intervals[i++]);
    return res.toArray(new int[0][]);
}
```

### M3. Non-Overlapping Intervals
Find the minimum number of intervals to remove to make the rest non-overlapping.  
**Hint:** Sort by end time; greedily keep intervals with the earliest end (classic activity selection). Count removed = total − kept.  
**Complexity:** O(n log n) time, O(1) space.

```java
public int eraseOverlapIntervals(int[][] intervals) {
    Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
    int removed = 0, prevEnd = Integer.MIN_VALUE;
    for (int[] iv : intervals) {
        if (iv[0] >= prevEnd) prevEnd = iv[1];
        else removed++;
    }
    return removed;
}
```

### M4. Meeting Rooms II
Find the minimum number of conference rooms required for a set of meetings.  
**Hint:** Sort starts and ends separately; two-pointer approach — increment rooms when a meeting starts before the earliest ending.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int minMeetingRooms(int[][] intervals) {
    int n = intervals.length;
    int[] starts = new int[n], ends = new int[n];
    for (int i = 0; i < n; i++) { starts[i] = intervals[i][0]; ends[i] = intervals[i][1]; }
    Arrays.sort(starts); Arrays.sort(ends);
    int rooms = 0, e = 0;
    for (int s : starts) { if (s < ends[e]) rooms++; else e++; }
    return rooms;
}
```

### M5. Minimum Number of Arrows to Burst Balloons
Find the minimum number of arrows shot vertically to burst all balloons (treated as horizontal intervals).  
**Hint:** Sort by end; one arrow at position end[0] bursts all intervals containing it. Move to the next unbursted balloon.  
**Complexity:** O(n log n) time, O(1) space.

```java
public int findMinArrowShots(int[][] points) {
    Arrays.sort(points, (a, b) -> Integer.compare(a[1], b[1]));
    int arrows = 1, arrowPos = points[0][1];
    for (int[] p : points) {
        if (p[0] > arrowPos) { arrows++; arrowPos = p[1]; }
    }
    return arrows;
}
```

### M6. Interval List Intersections
Given two sorted lists of disjoint intervals, return their intersection.  
**Hint:** Two-pointer, one per list. Intersection = [max(a.start, b.start), min(a.end, b.end)] if valid. Advance pointer with smaller end.  
**Complexity:** O(m + n) time, O(m + n) space.

```java
public int[][] intervalIntersection(int[][] A, int[][] B) {
    List<int[]> res = new ArrayList<>();
    int i = 0, j = 0;
    while (i < A.length && j < B.length) {
        int lo = Math.max(A[i][0], B[j][0]), hi = Math.min(A[i][1], B[j][1]);
        if (lo <= hi) res.add(new int[]{lo, hi});
        if (A[i][1] < B[j][1]) i++; else j++;
    }
    return res.toArray(new int[0][]);
}
```

### M7. Find Right Interval
For each interval, find the index of the interval whose start is the smallest value >= the current interval's end.  
**Hint:** Build a TreeMap (sorted map) of start→index. For each interval.end, do a ceiling lookup.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int[] findRightInterval(int[][] intervals) {
    TreeMap<Integer, Integer> startMap = new TreeMap<>();
    for (int i = 0; i < intervals.length; i++) startMap.put(intervals[i][0], i);
    int[] res = new int[intervals.length];
    for (int i = 0; i < intervals.length; i++) {
        Map.Entry<Integer, Integer> e = startMap.ceilingEntry(intervals[i][1]);
        res[i] = e == null ? -1 : e.getValue();
    }
    return res;
}
```

### M8. Car Fleet
N cars travel toward a target at different speeds; cars that meet form a fleet. Count the number of fleets.  
**Hint:** Sort by position descending. Compute each car's time to reach target. A new fleet forms only when a car's time exceeds the current max time.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int carFleet(int target, int[] position, int[] speed) {
    int n = position.length;
    Integer[] indices = new Integer[n];
    Arrays.setAll(indices, i -> i);
    Arrays.sort(indices, (a, b) -> position[b] - position[a]);
    int fleets = 0; double maxTime = 0;
    for (int i : indices) {
        double time = (double)(target - position[i]) / speed[i];
        if (time > maxTime) { fleets++; maxTime = time; }
    }
    return fleets;
}
```

### M9. Number of Flowers in Full Bloom
Given flower bloom/wither intervals and query times, for each query count how many flowers are blooming.  
**Hint:** Separate bloom starts and wither ends into sorted arrays; binary search for `#starts <= time` minus `#ends < time`.  
**Complexity:** O((n + q) log n) time.

```java
public int[] fullBloomFlowers(int[][] flowers, int[] queries) {
    int[] starts = new int[flowers.length], ends = new int[flowers.length];
    for (int i = 0; i < flowers.length; i++) { starts[i] = flowers[i][0]; ends[i] = flowers[i][1]; }
    Arrays.sort(starts); Arrays.sort(ends);
    int[] res = new int[queries.length];
    for (int i = 0; i < queries.length; i++) {
        int q = queries[i];
        int bloomed = upperBound(starts, q);   // starts <= q
        int withered = lowerBound(ends, q);    // ends < q
        res[i] = bloomed - withered;
    }
    return res;
}
private int upperBound(int[] arr, int val) { int lo=0,hi=arr.length; while(lo<hi){int mid=(lo+hi)/2; if(arr[mid]<=val)lo=mid+1; else hi=mid;} return lo; }
private int lowerBound(int[] arr, int val) { int lo=0,hi=arr.length; while(lo<hi){int mid=(lo+hi)/2; if(arr[mid]<val)lo=mid+1; else hi=mid;} return lo; }
```

### M10. Data Stream as Disjoint Intervals
Add integers from a stream one at a time; at any point return all disjoint intervals covering added numbers.  
**Hint:** Use a TreeMap keyed by interval start. On `addNum(val)` find potential left/right neighbors and merge greedily.  
**Complexity:** O(log n) per `addNum`, O(n) space.

```java
class SummaryRanges {
    TreeMap<Integer, Integer> map = new TreeMap<>();
    public void addNum(int val) {
        if (map.containsKey(val)) return;
        Integer lo = map.lowerKey(val), hi = map.higherKey(val);
        boolean mergeLeft  = lo != null && map.get(lo) + 1 >= val;
        boolean mergeRight = hi != null && hi == val + 1;
        if (mergeLeft && mergeRight) { map.put(map.lowerKey(val), map.get(hi)); map.remove(hi); }
        else if (mergeLeft)  map.put(lo, Math.max(map.get(lo), val));
        else if (mergeRight) { map.put(val, map.get(hi)); map.remove(hi); }
        else map.put(val, val);
    }
    public int[][] getIntervals() {
        return map.entrySet().stream().map(e -> new int[]{e.getKey(), e.getValue()}).toArray(int[][]::new);
    }
}
```

---

## 🔴 Hard (5)

### H1. The Skyline Problem
Given buildings as `[left, right, height]`, return the key points that form the skyline silhouette.  
**Hint:** Event-based sweep line: add start/end events sorted by x. Use a max-heap of active heights; emit a point when the max height changes.  
**Complexity:** O(n log n) time, O(n) space.

```java
public List<List<Integer>> getSkyline(int[][] buildings) {
    List<int[]> events = new ArrayList<>();
    for (int[] b : buildings) { events.add(new int[]{b[0], -b[2]}); events.add(new int[]{b[1], b[2]}); }
    events.sort((a, b) -> a[0] != b[0] ? a[0]-b[0] : a[1]-b[1]);
    TreeMap<Integer, Integer> heights = new TreeMap<>(Collections.reverseOrder());
    heights.put(0, 1);
    List<List<Integer>> res = new ArrayList<>();
    int prevMax = 0;
    for (int[] e : events) {
        if (e[1] < 0) heights.merge(-e[1], 1, Integer::sum);
        else { heights.compute(e[1], (k, v) -> v == 1 ? null : v - 1); }
        int curMax = heights.firstKey();
        if (curMax != prevMax) { res.add(Arrays.asList(e[0], curMax)); prevMax = curMax; }
    }
    return res;
}
```

### H2. Rectangle Area II
Compute the total area covered by a list of axis-aligned rectangles (may overlap).  
**Hint:** Coordinate compression on x-axis + sweep line with a sorted set (TreeMap) tracking active y-intervals, or inclusion-exclusion with segment tree.  
**Complexity:** O(n² log n) with compression, O(n log n) with segment tree.

```java
public int rectangleArea(int[][] rectangles) {
    final int MOD = 1_000_000_007;
    Set<Integer> xSet = new TreeSet<>(), ySet = new TreeSet<>();
    for (int[] r : rectangles) { xSet.add(r[0]); xSet.add(r[2]); ySet.add(r[1]); ySet.add(r[3]); }
    Integer[] xs = xSet.toArray(new Integer[0]), ys = ySet.toArray(new Integer[0]);
    Map<Integer, Integer> xi = new HashMap<>(), yi = new HashMap<>();
    for (int i = 0; i < xs.length; i++) xi.put(xs[i], i);
    for (int i = 0; i < ys.length; i++) yi.put(ys[i], i);
    boolean[][] grid = new boolean[xs.length][ys.length];
    for (int[] r : rectangles)
        for (int i = xi.get(r[0]); i < xi.get(r[2]); i++)
            for (int j = yi.get(r[1]); j < yi.get(r[3]); j++) grid[i][j] = true;
    long area = 0;
    for (int i = 0; i < xs.length-1; i++)
        for (int j = 0; j < ys.length-1; j++)
            if (grid[i][j]) area = (area + (long)(xs[i+1]-xs[i]) * (ys[j+1]-ys[j])) % MOD;
    return (int) area;
}
```

### H3. Minimum Number of Taps to Open to Water a Garden
Given a garden [0, n] and taps at each position with a range, find the minimum number of taps to water the entire garden.  
**Hint:** Convert taps to intervals [i-range, i+range]; then apply the classic "minimum jumps to cover entire range" greedy (like Jump Game II).  
**Complexity:** O(n) time after O(n) preprocessing.

```java
public int minTaps(int n, int[] ranges) {
    int[] maxReach = new int[n + 1];
    for (int i = 0; i <= n; i++) {
        int l = Math.max(0, i - ranges[i]);
        maxReach[l] = Math.max(maxReach[l], i + ranges[i]);
    }
    int taps = 0, curEnd = 0, farthest = 0;
    for (int i = 0; i <= n; i++) {
        if (i > farthest) return -1;
        farthest = Math.max(farthest, maxReach[i]);
        if (i == curEnd && i < n) { taps++; curEnd = farthest; }
    }
    return taps;
}
```

### H4. Minimum Interval to Include Each Query
For each query value, find the size of the smallest interval [l, r] such that l <= query <= r.  
**Hint:** Sort both intervals (by start) and queries (by value). Use a min-heap of (size, end) for active intervals; sweep queries and evict expired intervals.  
**Complexity:** O((n + q) log n) time, O(n + q) space.

```java
public int[] minInterval(int[][] intervals, int[] queries) {
    Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
    int q = queries.length;
    Integer[] idx = new Integer[q]; Arrays.setAll(idx, i -> i);
    Arrays.sort(idx, (a, b) -> queries[a] - queries[b]);
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]); // (size, end)
    int[] res = new int[q]; int j = 0;
    for (int i : idx) {
        int qv = queries[i];
        while (j < intervals.length && intervals[j][0] <= qv)
            pq.offer(new int[]{intervals[j][1]-intervals[j][0]+1, intervals[j][1]});
        j = /* already advanced */ j; // j is shared, don't reset
        while (!pq.isEmpty() && pq.peek()[1] < qv) pq.poll();
        res[i] = pq.isEmpty() ? -1 : pq.peek()[0];
    }
    return res;
}
```

### H5. Count of Smaller Numbers After Self
For each element, count how many numbers to its right are smaller.  
**Hint:** Process from right to left using a Binary Indexed Tree (Fenwick tree) or merge sort with index tracking. Coordinate-compress values first.  
**Complexity:** O(n log n) time, O(n) space.

```java
public List<Integer> countSmaller(int[] nums) {
    int n = nums.length;
    int[] sorted = nums.clone(); Arrays.sort(sorted);
    Map<Integer, Integer> rank = new HashMap<>();
    int r = 1; for (int v : sorted) rank.putIfAbsent(v, r++);
    int[] bit = new int[rank.size() + 2];
    Integer[] res = new Integer[n];
    for (int i = n - 1; i >= 0; i--) {
        int ri = rank.get(nums[i]);
        res[i] = query(bit, ri - 1);
        update(bit, ri, bit.length);
    }
    return Arrays.asList(res);
}
private int query(int[] bit, int i) { int s=0; for(;i>0;i-=i&(-i)) s+=bit[i]; return s; }
private void update(int[] bit, int i, int n) { for(;i<n;i+=i&(-i)) bit[i]++; }
```
