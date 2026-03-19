# Sliding Window — Complete Theory (Basic → Advanced)

---

## 1. What Is a Sliding Window?

A sliding window maintains a **contiguous sub-range** of an array or string and moves (slides) that range across the data structure without re-processing every element from scratch.

**Core idea:** instead of recomputing the window from scratch each step, add the new right element and remove the departing left element.

```
[a, b, c, d, e, f]
 [——window——]
      [——window——]    ← slide one step right
```

**Time complexity:** O(n) — each element enters and leaves the window at most once.
**Space complexity:** O(k) for fixed window (k = window size) or O(distinct elements) for variable.

**When to reach for sliding window:**
- Subarray / substring must be contiguous
- Optimise (max/min/longest/shortest) over all windows
- Constraint involves sum, count of distinct, frequency of characters

---

## 2. Fixed-Size Window

Window size `k` is given. Initialise window on `[0, k-1]` then slide right, adding `i` and removing `i-k`.

```java
// Template — fixed window of size k
int sum = 0;
for (int i = 0; i < k; i++) sum += nums[i];          // seed
int maxSum = sum;
for (int i = k; i < n; i++) {
    sum += nums[i] - nums[i - k];                     // slide
    maxSum = Math.max(maxSum, sum);
}
```

### Maximum Average Sub-Array
```java
public double findMaxAverage(int[] nums, int k) {
    double sum = 0;
    for (int i = 0; i < k; i++) sum += nums[i];
    double max = sum;
    for (int i = k; i < nums.length; i++) {
        sum += nums[i] - nums[i-k];
        max = Math.max(max, sum);
    }
    return max / k;
}
```

### Find All Anagrams in a String
```java
public List<Integer> findAnagrams(String s, String p) {
    List<Integer> res = new ArrayList<>();
    if (s.length() < p.length()) return res;
    int[] freq = new int[26];
    for (char c : p.toCharArray()) freq[c-'a']++;
    int[] win = new int[26];
    for (int i = 0; i < p.length(); i++) win[s.charAt(i)-'a']++;
    if (Arrays.equals(freq, win)) res.add(0);
    for (int i = p.length(); i < s.length(); i++) {
        win[s.charAt(i)-'a']++;
        win[s.charAt(i-p.length())-'a']--;
        if (Arrays.equals(freq, win)) res.add(i - p.length() + 1);
    }
    return res;
}
```

---

## 3. Variable-Size Window (Shrinkable Left)

Window grows by moving `right`. When a constraint is **violated**, shrink by moving `left` right.

```java
// Template — variable window
int left = 0;
// some window state (sum, map, count)
for (int right = 0; right < n; right++) {
    // 1. Expand: add nums[right] to window
    // 2. Shrink while constraint violated:
    while (/* violated */) {
        // remove nums[left] from window
        left++;
    }
    // 3. Record answer (window is [left, right])
    ans = Math.max(ans, right - left + 1);
}
```

### Longest Substring Without Repeating Characters
```java
public int lengthOfLongestSubstring(String s) {
    int[] lastSeen = new int[128];
    Arrays.fill(lastSeen, -1);
    int left = 0, ans = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen[c] >= left)          // dup inside window
            left = lastSeen[c] + 1;       // jump left past the dup
        lastSeen[c] = right;
        ans = Math.max(ans, right - left + 1);
    }
    return ans;
}
```

### Minimum Window Substring
```java
public String minWindow(String s, String t) {
    int[] need = new int[128];
    for (char c : t.toCharArray()) need[c]++;
    int required = t.length(), left = 0, minLen = Integer.MAX_VALUE, start = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (need[c] > 0) required--;      // one more satisfied
        need[c]--;
        while (required == 0) {           // window contains all of t
            if (right - left + 1 < minLen) { minLen = right - left + 1; start = left; }
            char lc = s.charAt(left);
            need[lc]++;
            if (need[lc] > 0) required++; // losing a needed char
            left++;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(start, start + minLen);
}
```

---

## 4. At-Most-K Trick

**"exactly K"** = **at_most(K)** − **at_most(K-1)**

Used when shrinking to "exactly K distinct" is hard, but "at most K" is easy.

```java
// Subarrays with exactly K distinct integers
public int subarraysWithKDistinct(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k-1);
}

private int atMost(int[] nums, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    int left = 0, res = 0;
    for (int right = 0; right < nums.length; right++) {
        count.merge(nums[right], 1, Integer::sum);
        while (count.size() > k) {
            count.merge(nums[left], -1, Integer::sum);
            if (count.get(nums[left]) == 0) count.remove(nums[left]);
            left++;
        }
        res += right - left + 1;    // all subarrays ending at right
    }
    return res;
}
```

---

## 5. Sliding Window with Deque (Maximum in Window)

Maintain a **monotonic decreasing deque** of indices. Front always holds the max of the current window.

```
nums = [1, 3, -1, -3, 5, 3, 6, 7]  k=3
       deque always: indices, front = max index, back = weakest

Window [1,3,-1]  deque: [1,2]  → max 3
Window [3,-1,-3] deque: [1,2,3] → max 3
Window [-1,-3,5] deque: [4]     → max 5
```

```java
public int[] maxSlidingWindow(int[] nums, int k) {
    Deque<Integer> dq = new ArrayDeque<>();   // indices
    int[] res = new int[nums.length - k + 1];
    for (int i = 0; i < nums.length; i++) {
        // remove index outside window
        while (!dq.isEmpty() && dq.peekFirst() < i - k + 1) dq.pollFirst();
        // remove weaker candidates from back
        while (!dq.isEmpty() && nums[dq.peekLast()] < nums[i]) dq.pollLast();
        dq.offerLast(i);
        if (i >= k - 1) res[i - k + 1] = nums[dq.peekFirst()];
    }
    return res;
}
```
Time: O(n) — each element enqueued and dequeued at most once.

---

## 6. Sliding Window with Frequency Map

### Longest Substring with At Most K Distinct Characters
```java
public int lengthOfLongestSubstringKDistinct(String s, int k) {
    Map<Character, Integer> freq = new HashMap<>();
    int left = 0, ans = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        freq.merge(c, 1, Integer::sum);
        while (freq.size() > k) {
            char lc = s.charAt(left++);
            freq.merge(lc, -1, Integer::sum);
            if (freq.get(lc) == 0) freq.remove(lc);
        }
        ans = Math.max(ans, right - left + 1);
    }
    return ans;
}
```

### Permutation in String
```java
public boolean checkInclusion(String s1, String s2) {
    int[] need = new int[26], win = new int[26];
    for (char c : s1.toCharArray()) need[c-'a']++;
    int l = s1.length();
    for (int i = 0; i < s2.length(); i++) {
        win[s2.charAt(i)-'a']++;
        if (i >= l) win[s2.charAt(i-l)-'a']--;
        if (Arrays.equals(need, win)) return true;
    }
    return false;
}
```

---

## 7. Binary Array / Flip Windows

### Longest Ones After Flipping K Zeros
```java
public int longestOnes(int[] nums, int k) {
    int left = 0, zeros = 0, ans = 0;
    for (int right = 0; right < nums.length; right++) {
        if (nums[right] == 0) zeros++;
        while (zeros > k) if (nums[left++] == 0) zeros--;
        ans = Math.max(ans, right - left + 1);
    }
    return ans;
}
```

---

## 8. Multi-String Window (General Template)

When the window spans multiple arrays simultaneously (e.g., median of K sorted arrays), use a priority queue combined with a pointer array. This is an advanced variant — see heaps section for k-way merge.

---

## 9. Complexity Reference

| Window Type | Time | Space |
|---|---|---|
| Fixed size | O(n) | O(1) |
| Variable, freq array | O(n) | O(1) (26 chars) |
| Variable, HashMap | O(n) avg | O(k) |
| Monotonic deque | O(n) | O(k) |
| At-most-K trick | O(n) | O(k) |

---

## 10. Decision Guide

| Problem Signal | Pattern |
|---|---|
| Fixed window size k given | Seed + slide |
| Longest subarray with constraint | Variable window, shrink on violation |
| Shortest subarray with constraint | Variable window, shrink while valid |
| Exactly K (count of something) | at_most(k) − at_most(k-1) |
| Maximum in each window of size k | Monotonic decreasing deque |
| Anagram / permutation check | Fixed freq-array comparison |
| Contains all chars of pattern | Minimum window with need/required counter |

---

## 11. Common Pitfalls

- **Sliding fixed window**: seed first `[0..k-1]`, then loop from `i=k`
- **Never reset `left` inside variable window**: just shrink step by step
- **At-most trick off by one**: `atMost(k) - atMost(k-1)` — both call the same helper
- **Arrays.equals on freq arrays is O(26)**: fine for alphabet, but use a `formed` counter for efficiency at scale
- **Deque window: `i - k + 1`**: left boundary of valid indices, not `i - k`
- **Minimum window**: don't record answer until `required == 0`
