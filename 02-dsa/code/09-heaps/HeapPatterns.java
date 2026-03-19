package dsa.heaps;

import java.util.*;

/**
 * Heap / priority queue patterns.
 * Top-K, merge K lists, median finder, task scheduler.
 */
public class HeapPatterns {

    // ─── 1. KTH LARGEST ELEMENT ──────────────────────────────────────────────────

    static int findKthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(); // keep top-k
        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) minHeap.poll();
        }
        return minHeap.peek();
    }

    // ─── 2. K CLOSEST POINTS TO ORIGIN ──────────────────────────────────────────

    static int[][] kClosest(int[][] points, int k) {
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>(
            (a, b) -> (b[0] * b[0] + b[1] * b[1]) - (a[0] * a[0] + a[1] * a[1])
        );
        for (int[] p : points) {
            maxHeap.offer(p);
            if (maxHeap.size() > k) maxHeap.poll();
        }
        return maxHeap.toArray(new int[0][]);
    }

    // ─── 3. TOP K FREQUENT ELEMENTS ──────────────────────────────────────────────

    static int[] topKFrequent(int[] nums, int k) {
        Map<Integer, Integer> freq = new HashMap<>();
        for (int n : nums) freq.merge(n, 1, Integer::sum);

        // Min-heap by frequency — keep k most frequent
        PriorityQueue<Map.Entry<Integer, Integer>> minHeap = new PriorityQueue<>(
            Comparator.comparingInt(Map.Entry::getValue)
        );
        for (Map.Entry<Integer, Integer> e : freq.entrySet()) {
            minHeap.offer(e);
            if (minHeap.size() > k) minHeap.poll();
        }
        return minHeap.stream().mapToInt(Map.Entry::getKey).toArray();
    }

    // ─── 4. MERGE K SORTED LISTS ─────────────────────────────────────────────────

    static class ListNode { int val; ListNode next; ListNode(int v) { val = v; } }

    static ListNode mergeKLists(ListNode[] lists) {
        PriorityQueue<ListNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.val));
        for (ListNode node : lists) if (node != null) pq.offer(node);

        ListNode dummy = new ListNode(0), curr = dummy;
        while (!pq.isEmpty()) {
            ListNode node = pq.poll();
            curr.next = node;
            curr = curr.next;
            if (node.next != null) pq.offer(node.next);
        }
        return dummy.next;
    }

    // ─── 5. MEDIAN FINDER ────────────────────────────────────────────────────────

    static class MedianFinder {
        private final PriorityQueue<Integer> lo = new PriorityQueue<>(Comparator.reverseOrder()); // max-heap
        private final PriorityQueue<Integer> hi = new PriorityQueue<>(); // min-heap

        public void addNum(int num) {
            lo.offer(num);
            hi.offer(lo.poll());
            if (lo.size() < hi.size()) lo.offer(hi.poll());
        }

        public double findMedian() {
            return lo.size() > hi.size() ? lo.peek() : (lo.peek() + hi.peek()) / 2.0;
        }
    }

    // ─── 6. TASK SCHEDULER ───────────────────────────────────────────────────────

    static int leastInterval(char[] tasks, int n) {
        int[] freq = new int[26];
        for (char t : tasks) freq[t - 'A']++;

        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
        for (int f : freq) if (f > 0) maxHeap.offer(f);

        int time = 0;
        while (!maxHeap.isEmpty()) {
            List<Integer> temp = new ArrayList<>();
            for (int i = 0; i <= n; i++) {
                if (!maxHeap.isEmpty()) temp.add(maxHeap.poll() - 1);
            }
            for (int t : temp) if (t > 0) maxHeap.offer(t);
            time += maxHeap.isEmpty() ? temp.size() : n + 1;
        }
        return time;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("Kth largest (k=2): " + findKthLargest(new int[]{3,2,1,5,6,4}, 2)); // 5

        System.out.println("K closest: " + Arrays.deepToString(kClosest(new int[][]{{1,3},{-2,2}}, 1)));

        System.out.println("Top K frequent: " + Arrays.toString(topKFrequent(new int[]{1,1,1,2,2,3}, 2))); // [1,2]

        // Merge K sorted lists
        ListNode l1 = build(1, 4, 5);
        ListNode l2 = build(1, 3, 4);
        ListNode l3 = build(2, 6);
        ListNode merged = mergeKLists(new ListNode[]{l1, l2, l3});
        System.out.print("Merge K: ");
        while (merged != null) { System.out.print(merged.val + " "); merged = merged.next; }
        System.out.println();

        MedianFinder mf = new MedianFinder();
        mf.addNum(1); mf.addNum(2);
        System.out.println("Median after [1,2]: " + mf.findMedian()); // 1.5
        mf.addNum(3);
        System.out.println("Median after [1,2,3]: " + mf.findMedian()); // 2.0

        System.out.println("Task scheduler: " + leastInterval(new char[]{'A','A','A','B','B','B'}, 2)); // 8
    }

    private static ListNode build(int... vals) {
        ListNode dummy = new ListNode(0), curr = dummy;
        for (int v : vals) { curr.next = new ListNode(v); curr = curr.next; }
        return dummy.next;
    }
}
