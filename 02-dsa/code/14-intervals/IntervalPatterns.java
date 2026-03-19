package dsa.intervals;

import java.util.*;

/**
 * IntervalPatterns — Merge, insert, and query intervals.
 *
 * Very frequently tested at FAANG. Core insight: sort by start time,
 * then scan linearly, merging overlapping intervals greedily.
 *
 * Patterns covered:
 *   1. Merge Intervals (LC 56)          — sort + greedy merge
 *   2. Insert Interval (LC 57)          — find insertion point, merge once
 *   3. Non-overlapping Intervals (LC 435) — greedy activity selection
 *   4. Meeting Rooms I (LC 252)         — any overlap?
 *   5. Meeting Rooms II (LC 253)        — minimum rooms needed
 *   6. Interval List Intersections (LC 986)
 *   7. Employee Free Time (LC 759)
 *
 * Time complexity summary:
 *   Merge / sort-based:   O(n log n)
 *   Insert (unsorted):    O(n)
 *   Meeting Rooms II:     O(n log n)
 *
 * Template to remember:
 *   sort by start → iterate → if overlap (cur.start <= prev.end) → merge
 */
public class IntervalPatterns {

    record Interval(int start, int end) {}

    // ── 1. Merge Overlapping Intervals (LC 56) ───────────────────────────────

    /**
     * Given a list of intervals, merge all overlapping ones.
     *
     * Key insight: after sorting by start, two adjacent intervals [a,b] and [c,d]
     * overlap iff c <= b. Merge to [a, max(b,d)].
     *
     * Time: O(n log n)   Space: O(n)
     *
     * Example: [[1,3],[2,6],[8,10],[15,18]] → [[1,6],[8,10],[15,18]]
     */
    public int[][] merge(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        List<int[]> result = new ArrayList<>();

        for (int[] curr : intervals) {
            if (result.isEmpty() || result.getLast()[1] < curr[0]) {
                result.add(curr);            // no overlap — add as is
            } else {
                result.getLast()[1] = Math.max(result.getLast()[1], curr[1]); // merge
            }
        }
        return result.toArray(new int[0][]);
    }

    // ── 2. Insert Interval (LC 57) ───────────────────────────────────────────

    /**
     * Non-overlapping sorted intervals + a newInterval.
     * Insert newInterval and re-merge if necessary.
     *
     * Three regions: left of new, overlapping, right of new.
     *
     * Time: O(n)   Space: O(n)
     *
     * Example: intervals=[[1,2],[3,5],[6,7],[8,10]], new=[4,8]
     *         → [[1,2],[3,10]]
     */
    public int[][] insert(int[][] intervals, int[] newInterval) {
        List<int[]> result = new ArrayList<>();
        int i = 0, n = intervals.length;

        // Left part: intervals that end before newInterval starts
        while (i < n && intervals[i][1] < newInterval[0]) {
            result.add(intervals[i++]);
        }

        // Overlapping part: merge all that overlap with newInterval
        while (i < n && intervals[i][0] <= newInterval[1]) {
            newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
            newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
            i++;
        }
        result.add(newInterval);

        // Right part: intervals that start after newInterval ends
        while (i < n) result.add(intervals[i++]);

        return result.toArray(new int[0][]);
    }

    // ── 3. Non-overlapping Intervals / Min Removals (LC 435) ─────────────────

    /**
     * Find the minimum number of intervals to REMOVE so that the rest are non-overlapping.
     *
     * Greedy strategy (activity selection): sort by END time.
     * Greedily keep intervals that end earliest — maximizes room for future intervals.
     *
     * Time: O(n log n)   Space: O(1)
     *
     * Example: [[1,2],[2,3],[3,4],[1,3]] → remove [1,3] → 1
     */
    public int eraseOverlapIntervals(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[1] - b[1]); // sort by END

        int removals = 0;
        int prevEnd = Integer.MIN_VALUE;

        for (int[] curr : intervals) {
            if (curr[0] >= prevEnd) {
                prevEnd = curr[1]; // keep this interval
            } else {
                removals++;        // skip (remove) this interval
            }
        }
        return removals;
    }

    // ── 4. Meeting Rooms I (LC 252) ───────────────────────────────────────────

    /**
     * Given meeting time intervals, determine if a person can attend ALL meetings.
     * → True iff no two intervals overlap.
     *
     * Time: O(n log n)   Space: O(1)
     */
    public boolean canAttendMeetings(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < intervals[i - 1][1]) return false; // overlap
        }
        return true;
    }

    // ── 5. Meeting Rooms II — Min Rooms Needed (LC 253) ──────────────────────

    /**
     * Minimum number of conference rooms required for all meetings.
     *
     * Key insight: at any point in time, the number of rooms in use equals the number
     * of meetings whose start ≤ current time < end.
     *
     * Two-pointer approach on sorted start and end arrays:
     *   - If next start < smallest end → new room needed (rooms++)
     *   - Else: one meeting ended → reuse that room (endPtr++)
     *
     * Min-heap approach (same logic, more explicit):
     *   - Heap tracks end times of ongoing meetings
     *   - If next start ≥ heap.peek() → that room is free, pop + push
     *   - Else → allocate a new room (push without popping)
     *
     * Time: O(n log n)   Space: O(n)
     *
     * Example: [[0,30],[5,10],[15,20]] → 2 rooms
     */
    public int minMeetingRooms(int[][] intervals) {
        int n = intervals.length;
        if (n == 0) return 0;

        int[] starts = new int[n];
        int[] ends   = new int[n];
        for (int i = 0; i < n; i++) {
            starts[i] = intervals[i][0];
            ends[i]   = intervals[i][1];
        }
        Arrays.sort(starts);
        Arrays.sort(ends);

        int rooms = 0, endPtr = 0;
        for (int i = 0; i < n; i++) {
            if (starts[i] < ends[endPtr]) {
                rooms++;        // need a new room
            } else {
                endPtr++;       // recycle a freed room
            }
        }
        return rooms;
    }

    // Heap-based version for clarity:
    public int minMeetingRoomsHeap(int[][] intervals) {
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        PriorityQueue<Integer> endTimes = new PriorityQueue<>(); // min-heap of end times

        for (int[] interval : intervals) {
            if (!endTimes.isEmpty() && endTimes.peek() <= interval[0]) {
                endTimes.poll(); // this room is free — reuse it
            }
            endTimes.offer(interval[1]); // room is occupied until interval[1]
        }
        return endTimes.size(); // rooms currently allocated
    }

    // ── 6. Interval List Intersections (LC 986) ───────────────────────────────

    /**
     * Two sorted lists of closed intervals A and B.
     * Find all intersections.
     *
     * Two pointers: advance whichever interval ends first.
     * Intersection exists iff max(a.start,b.start) <= min(a.end,b.end)
     *
     * Time: O(m + n)   Space: O(m + n)
     *
     * Example: A=[[0,2],[5,10],[13,23],[24,25]], B=[[1,5],[8,12],[15,24],[25,26]]
     *         → [[1,2],[5,5],[8,10],[15,23],[24,24],[25,25]]
     */
    public int[][] intervalIntersection(int[][] A, int[][] B) {
        List<int[]> result = new ArrayList<>();
        int i = 0, j = 0;

        while (i < A.length && j < B.length) {
            int lo = Math.max(A[i][0], B[j][0]);
            int hi = Math.min(A[i][1], B[j][1]);
            if (lo <= hi) result.add(new int[]{lo, hi});

            // advance the pointer whose interval ends earlier
            if (A[i][1] < B[j][1]) i++;
            else                    j++;
        }
        return result.toArray(new int[0][]);
    }

    // ── 7. Employee Free Time (LC 759) ───────────────────────────────────────

    /**
     * Given employee schedules (list of lists of intervals), find times when ALL
     * employees are free.
     *
     * Strategy: flatten all intervals, sort by start, merge overlapping ones.
     * Gaps between merged intervals = free times.
     *
     * Time: O(n log n)   Space: O(n)
     */
    public List<Interval> employeeFreeTime(List<List<Interval>> schedules) {
        List<Interval> all = new ArrayList<>();
        for (List<Interval> schedule : schedules) all.addAll(schedule);
        all.sort(Comparator.comparingInt(i -> i.start()));

        List<Interval> merged = new ArrayList<>();
        for (Interval curr : all) {
            if (merged.isEmpty() || merged.getLast().end() < curr.start()) {
                merged.add(curr);
            } else {
                Interval last = merged.getLast();
                merged.set(merged.size() - 1, new Interval(last.start(), Math.max(last.end(), curr.end())));
            }
        }

        List<Interval> free = new ArrayList<>();
        for (int i = 1; i < merged.size(); i++) {
            free.add(new Interval(merged.get(i - 1).end(), merged.get(i).start()));
        }
        return free;
    }

    // ── Driver ────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        IntervalPatterns sol = new IntervalPatterns();

        System.out.println("=== Merge Intervals ===");
        int[][] merged = sol.merge(new int[][]{{1,3},{2,6},{8,10},{15,18}});
        for (int[] iv : merged) System.out.println(Arrays.toString(iv));
        // [1,6], [8,10], [15,18]

        System.out.println("\n=== Insert Interval ===");
        int[][] inserted = sol.insert(new int[][]{{1,2},{3,5},{6,7},{8,10}}, new int[]{4,8});
        for (int[] iv : inserted) System.out.println(Arrays.toString(iv));
        // [1,2], [3,10]

        System.out.println("\n=== Min Removals ===");
        System.out.println(sol.eraseOverlapIntervals(new int[][]{{1,2},{2,3},{3,4},{1,3}})); // 1

        System.out.println("\n=== Meeting Rooms I ===");
        System.out.println(sol.canAttendMeetings(new int[][]{{0,30},{5,10},{15,20}})); // false
        System.out.println(sol.canAttendMeetings(new int[][]{{7,10},{2,4}}));           // true

        System.out.println("\n=== Meeting Rooms II ===");
        System.out.println(sol.minMeetingRooms(new int[][]{{0,30},{5,10},{15,20}}));    // 2
        System.out.println(sol.minMeetingRoomsHeap(new int[][]{{0,30},{5,10},{15,20}})); // 2

        System.out.println("\n=== Interval Intersection ===");
        int[][] intersected = sol.intervalIntersection(
            new int[][]{{0,2},{5,10},{13,23},{24,25}},
            new int[][]{{1,5},{8,12},{15,24},{25,26}});
        for (int[] iv : intersected) System.out.println(Arrays.toString(iv));
        // [1,2],[5,5],[8,10],[15,23],[24,24],[25,25]
    }
}
