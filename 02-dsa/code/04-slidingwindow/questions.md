# Sliding Window — Practice Questions

---

## 🟢 Easy (5)

### E1. Maximum Sum Subarray of Size K
Given an array and integer `k`, find the maximum sum of any contiguous subarray of size `k`.  
**Hint:** Fixed window — add right element, remove left element each step.  
**Complexity:** O(n) time, O(1) space.

```java
public int maxSumSubarray(int[] nums, int k) {
    int sum = 0;
    for (int i = 0; i < k; i++) sum += nums[i];
    int max = sum;
    for (int i = k; i < nums.length; i++) {
        sum += nums[i] - nums[i - k];
        max = Math.max(max, sum);
    }
    return max;
}
```

### E2. Count Occurrences of Anagram
Count how many times an anagram of pattern `p` appears as a substring of string `s`.  
**Hint:** Fixed window of size `p.length()`; compare frequency arrays.  
**Complexity:** O(n) time, O(26) space.

```java
public int countAnagrams(String s, String p) {
    int[] need = new int[26], win = new int[26];
    for (char c : p.toCharArray()) need[c - 'a']++;
    int k = p.length(), count = 0;
    for (int i = 0; i < s.length(); i++) {
        win[s.charAt(i) - 'a']++;
        if (i >= k) win[s.charAt(i - k) - 'a']--;
        if (i >= k - 1 && Arrays.equals(win, need)) count++;
    }
    return count;
}
```

### E3. Longest Subarray of 1s After Deleting One Element
Given binary array, find the longest subarray of 1s after deleting exactly one element.  
**Hint:** Sliding window allowing at most one 0.  
**Complexity:** O(n) time, O(1) space.

```java
public int longestSubarray(int[] nums) {
    int lo = 0, zeros = 0, best = 0;
    for (int hi = 0; hi < nums.length; hi++) {
        if (nums[hi] == 0) zeros++;
        while (zeros > 1) if (nums[lo++] == 0) zeros--;
        best = Math.max(best, hi - lo); // window size - 1 (the deleted element)
    }
    return best;
}
```

### E4. Average of All Subarrays of Size K
Return an array of averages of all contiguous subarrays of size `k`.  
**Hint:** Maintain a running sum; divide by k at each window.  
**Complexity:** O(n) time, O(1) space.

```java
public double[] findAverages(int[] nums, int k) {
    double[] result = new double[nums.length - k + 1];
    double sum = 0;
    for (int i = 0; i < k; i++) sum += nums[i];
    result[0] = sum / k;
    for (int i = k; i < nums.length; i++) {
        sum += nums[i] - nums[i - k];
        result[i - k + 1] = sum / k;
    }
    return result;
}
```

### E5. First Negative Number in Every Window of Size K
For each window of size `k`, find the first negative number (0 if none exists).  
**Hint:** Use a deque to track indices of negative numbers within the window.  
**Complexity:** O(n) time, O(k) space.

```java
public long[] firstNegative(int[] nums, int k) {
    Deque<Integer> dq = new ArrayDeque<>();
    long[] res = new long[nums.length - k + 1];
    for (int i = 0; i < nums.length; i++) {
        if (nums[i] < 0) dq.addLast(i);
        if (i >= k && !dq.isEmpty() && dq.peekFirst() <= i - k) dq.pollFirst();
        if (i >= k - 1) res[i - k + 1] = dq.isEmpty() ? 0 : nums[dq.peekFirst()];
    }
    return res;
}
```

---

## 🟡 Medium (10)

### M1. Longest Substring Without Repeating Characters
Find the length of the longest substring without repeating characters.  
**Hint:** Expand right pointer; when duplicate found, advance left past the previous occurrence.  
**Complexity:** O(n) time, O(min(n,128)) space.

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> map = new HashMap<>();
    int lo = 0, best = 0;
    for (int hi = 0; hi < s.length(); hi++) {
        char c = s.charAt(hi);
        if (map.containsKey(c)) lo = Math.max(lo, map.get(c) + 1);
        map.put(c, hi);
        best = Math.max(best, hi - lo + 1);
    }
    return best;
}
```

### M2. Minimum Window Substring
Find the smallest window in `s` containing all characters of `t`.  
**Hint:** Two maps for required/window counts; shrink left when all chars are satisfied.  
**Complexity:** O(n + m) time, O(m) space.

```java
public String minWindow(String s, String t) {
    Map<Character, Integer> need = new HashMap<>(), win = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    int lo = 0, have = 0, required = need.size(), start = 0, minLen = Integer.MAX_VALUE;
    for (int hi = 0; hi < s.length(); hi++) {
        char c = s.charAt(hi);
        win.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && win.get(c).equals(need.get(c))) have++;
        while (have == required) {
            if (hi - lo + 1 < minLen) { minLen = hi - lo + 1; start = lo; }
            char lc = s.charAt(lo++);
            win.merge(lc, -1, Integer::sum);
            if (need.containsKey(lc) && win.get(lc) < need.get(lc)) have--;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(start, start + minLen);
}
```

### M3. Fruit Into Baskets
You have two baskets; each holds one fruit type. Find max fruits you can collect in a contiguous subarray.  
**Hint:** Sliding window with at most 2 distinct values; use HashMap for counts.  
**Complexity:** O(n) time, O(1) space.

```java
public int totalFruit(int[] fruits) {
    Map<Integer, Integer> basket = new HashMap<>();
    int lo = 0, best = 0;
    for (int hi = 0; hi < fruits.length; hi++) {
        basket.merge(fruits[hi], 1, Integer::sum);
        while (basket.size() > 2) {
            basket.merge(fruits[lo], -1, Integer::sum);
            if (basket.get(fruits[lo]) == 0) basket.remove(fruits[lo]);
            lo++;
        }
        best = Math.max(best, hi - lo + 1);
    }
    return best;
}
```

### M4. Permutation in String
Return true if `s2` contains a permutation of `s1` as a substring.  
**Hint:** Fixed window of size `s1.length()`; track character frequency differences.  
**Complexity:** O(n) time, O(26) space.

```java
public boolean checkInclusion(String s1, String s2) {
    int[] need = new int[26], win = new int[26];
    for (char c : s1.toCharArray()) need[c - 'a']++;
    int k = s1.length();
    for (int i = 0; i < s2.length(); i++) {
        win[s2.charAt(i) - 'a']++;
        if (i >= k) win[s2.charAt(i - k) - 'a']--;
        if (Arrays.equals(win, need)) return true;
    }
    return false;
}
```

### M5. Longest Repeating Character Replacement
Given string and integer `k`, find the longest substring where you can replace at most `k` characters to make all chars the same.  
**Hint:** Track maxFreq of dominant character; window is valid if `windowSize - maxFreq <= k`.  
**Complexity:** O(n) time, O(26) space.

```java
public int characterReplacement(String s, int k) {
    int[] freq = new int[26];
    int lo = 0, maxFreq = 0, best = 0;
    for (int hi = 0; hi < s.length(); hi++) {
        maxFreq = Math.max(maxFreq, ++freq[s.charAt(hi) - 'A']);
        if (hi - lo + 1 - maxFreq > k) freq[s.charAt(lo++) - 'A']--;
        best = Math.max(best, hi - lo + 1);
    }
    return best;
}
```

### M6. Max Consecutive Ones III
Given binary array, flip at most `k` zeros. Return the max consecutive ones.  
**Hint:** Sliding window counting zeros; shrink when zero count exceeds k.  
**Complexity:** O(n) time, O(1) space.

```java
public int longestOnes(int[] nums, int k) {
    int lo = 0, zeros = 0, best = 0;
    for (int hi = 0; hi < nums.length; hi++) {
        if (nums[hi] == 0) zeros++;
        while (zeros > k) if (nums[lo++] == 0) zeros--;
        best = Math.max(best, hi - lo + 1);
    }
    return best;
}
```

### M7. Subarray Product Less Than K
Count contiguous subarrays where the product of elements is strictly less than `k`.  
**Hint:** Expand right; if product >= k, shrink left. Each valid right adds (right - left + 1) subarrays.  
**Complexity:** O(n) time, O(1) space.

```java
public int numSubarrayProductLessThanK(int[] nums, int k) {
    if (k <= 1) return 0;
    int lo = 0, prod = 1, count = 0;
    for (int hi = 0; hi < nums.length; hi++) {
        prod *= nums[hi];
        while (prod >= k) prod /= nums[lo++];
        count += hi - lo + 1;
    }
    return count;
}
```

### M8. Minimum Size Subarray Sum
Find the minimal length of a contiguous subarray with sum ≥ `target`.  
**Hint:** Expand right, track sum; whenever sum >= target, try shrinking left and update min length.  
**Complexity:** O(n) time, O(1) space.

```java
public int minSubArrayLen(int target, int[] nums) {
    int lo = 0, sum = 0, minLen = Integer.MAX_VALUE;
    for (int hi = 0; hi < nums.length; hi++) {
        sum += nums[hi];
        while (sum >= target) {
            minLen = Math.min(minLen, hi - lo + 1);
            sum -= nums[lo++];
        }
    }
    return minLen == Integer.MAX_VALUE ? 0 : minLen;
}
```

### M9. Longest Substring with At Most K Distinct Characters
Find the longest substring containing at most `k` distinct characters.  
**Hint:** HashMap with character counts; remove entry when count hits 0.  
**Complexity:** O(n) time, O(k) space.

```java
public int lengthOfLongestSubstringKDistinct(String s, int k) {
    Map<Character, Integer> map = new HashMap<>();
    int lo = 0, best = 0;
    for (int hi = 0; hi < s.length(); hi++) {
        map.merge(s.charAt(hi), 1, Integer::sum);
        while (map.size() > k) {
            char lc = s.charAt(lo++);
            map.merge(lc, -1, Integer::sum);
            if (map.get(lc) == 0) map.remove(lc);
        }
        best = Math.max(best, hi - lo + 1);
    }
    return best;
}
```

### M10. Maximum Points You Can Obtain from Cards
Pick exactly `k` cards from either end of the array to maximize total points.  
**Hint:** Total - minimum subarray of size (n - k) in the middle.  
**Complexity:** O(n) time, O(1) space.

```java
public int maxScore(int[] cardPoints, int k) {
    int n = cardPoints.length, total = 0;
    for (int p : cardPoints) total += p;
    int winSize = n - k, winSum = 0, minWin = Integer.MAX_VALUE;
    for (int i = 0; i < n; i++) {
        winSum += cardPoints[i];
        if (i >= winSize) winSum -= cardPoints[i - winSize];
        if (i >= winSize - 1) minWin = Math.min(minWin, winSum);
    }
    return total - minWin;
}
```

---

## 🔴 Hard (5)

### H1. Sliding Window Maximum
Given array and window size `k`, return max of each window as it slides.  
**Hint:** Monotonic deque (decreasing). Remove indices out of window from front; pop smaller elements from back.  
**Complexity:** O(n) time, O(k) space.

```java
public int[] maxSlidingWindow(int[] nums, int k) {
    Deque<Integer> dq = new ArrayDeque<>();
    int[] res = new int[nums.length - k + 1];
    for (int i = 0; i < nums.length; i++) {
        if (!dq.isEmpty() && dq.peekFirst() <= i - k) dq.pollFirst();
        while (!dq.isEmpty() && nums[dq.peekLast()] < nums[i]) dq.pollLast();
        dq.addLast(i);
        if (i >= k - 1) res[i - k + 1] = nums[dq.peekFirst()];
    }
    return res;
}
```

### H2. Substring with Concatenation of All Words
Find all starting indices of substrings that are a concatenation of each word in `words` exactly once.  
**Hint:** Slide a window of size `totalLen`; compare HashMap of word frequencies.  
**Complexity:** O(n * wordLen) time.

```java
public List<Integer> findSubstring(String s, String[] words) {
    List<Integer> res = new ArrayList<>();
    if (s.isEmpty() || words.length == 0) return res;
    int wLen = words[0].length(), wCount = words.length, total = wLen * wCount;
    Map<String, Integer> need = new HashMap<>();
    for (String w : words) need.merge(w, 1, Integer::sum);
    for (int i = 0; i <= s.length() - total; i++) {
        Map<String, Integer> seen = new HashMap<>();
        int j = 0;
        while (j < wCount) {
            String w = s.substring(i + j * wLen, i + (j + 1) * wLen);
            if (!need.containsKey(w)) break;
            seen.merge(w, 1, Integer::sum);
            if (seen.get(w) > need.get(w)) break;
            j++;
        }
        if (j == wCount) res.add(i);
    }
    return res;
}
```

### H3. Minimum Number of K Consecutive Bit Flips
Find minimum number of flips of k consecutive bits to make all bits 1. Return -1 if impossible.  
**Hint:** Track flipping effect with a difference array (or deque) to avoid O(k) cost per flip.  
**Complexity:** O(n) time, O(k) space.

```java
public int minKBitFlips(int[] nums, int k) {
    int n = nums.length, flips = 0, flipCount = 0;
    int[] isFlipped = new int[n];
    for (int i = 0; i < n; i++) {
        if (i >= k) flipCount -= isFlipped[i - k];
        if ((nums[i] + flipCount) % 2 == 0) { // needs flip
            if (i + k > n) return -1;
            isFlipped[i] = 1;
            flipCount++;
            flips++;
        }
    }
    return flips;
}
```

### H4. Count Subarrays with At Most K Distinct Integers
Count subarrays with at most `k` distinct values. Use to solve "exactly k" = "atMost(k) - atMost(k-1)".  
**Hint:** Standard sliding window with HashMap; each valid right contributes (right - left + 1) subarrays.  
**Complexity:** O(n) time, O(k) space.

```java
public int subarraysWithKDistinct(int[] nums, int k) {
    return atMost(nums, k) - atMost(nums, k - 1);
}
private int atMost(int[] nums, int k) {
    Map<Integer, Integer> map = new HashMap<>();
    int lo = 0, count = 0;
    for (int hi = 0; hi < nums.length; hi++) {
        map.merge(nums[hi], 1, Integer::sum);
        while (map.size() > k) {
            map.merge(nums[lo], -1, Integer::sum);
            if (map.get(nums[lo]) == 0) map.remove(nums[lo]);
            lo++;
        }
        count += hi - lo + 1;
    }
    return count;
}
```

### H5. Longest Continuous Subarray With Absolute Diff ≤ Limit
Find the longest subarray where the absolute diff between max and min ≤ `limit`.  
**Hint:** Two monotonic deques (one for max, one for min); shrink window when diff exceeds limit.  
**Complexity:** O(n) time, O(n) space.

```java
public int longestSubarray(int[] nums, int limit) {
    Deque<Integer> maxD = new ArrayDeque<>(), minD = new ArrayDeque<>();
    int lo = 0, best = 0;
    for (int hi = 0; hi < nums.length; hi++) {
        while (!maxD.isEmpty() && nums[maxD.peekLast()] <= nums[hi]) maxD.pollLast();
        while (!minD.isEmpty() && nums[minD.peekLast()] >= nums[hi]) minD.pollLast();
        maxD.addLast(hi); minD.addLast(hi);
        while (nums[maxD.peekFirst()] - nums[minD.peekFirst()] > limit) {
            lo++;
            if (maxD.peekFirst() < lo) maxD.pollFirst();
            if (minD.peekFirst() < lo) minD.pollFirst();
        }
        best = Math.max(best, hi - lo + 1);
    }
    return best;
}
```

---

## 🟡 Medium (10)

### M1. Longest Substring Without Repeating Characters
Find the length of the longest substring without repeating characters.  
**Hint:** Expand right pointer; when duplicate found, advance left past the previous occurrence.  
**Complexity:** O(n) time, O(min(n,128)) space.

### M2. Minimum Window Substring
Find the smallest window in `s` containing all characters of `t`.  
**Hint:** Two maps for required/window counts; shrink left when all chars are satisfied.  
**Complexity:** O(n + m) time, O(m) space.

### M3. Fruit Into Baskets
You have two baskets; each holds one fruit type. Find max fruits you can collect in a contiguous subarray.  
**Hint:** Sliding window with at most 2 distinct values; use HashMap for counts.  
**Complexity:** O(n) time, O(1) space.

### M4. Permutation in String
Return true if `s2` contains a permutation of `s1` as a substring.  
**Hint:** Fixed window of size `s1.length()`; track character frequency differences.  
**Complexity:** O(n) time, O(26) space.

### M5. Longest Repeating Character Replacement
Given string and integer `k`, find the longest substring where you can replace at most `k` characters to make all chars the same.  
**Hint:** Track maxFreq of dominant character; window is valid if `windowSize - maxFreq <= k`.  
**Complexity:** O(n) time, O(26) space.

### M6. Max Consecutive Ones III
Given binary array, flip at most `k` zeros. Return the max consecutive ones.  
**Hint:** Sliding window counting zeros; shrink when zero count exceeds k.  
**Complexity:** O(n) time, O(1) space.

### M7. Subarray Product Less Than K
Count contiguous subarrays where the product of elements is strictly less than `k`.  
**Hint:** Expand right; if product >= k, shrink left. Each valid right adds (right - left + 1) subarrays.  
**Complexity:** O(n) time, O(1) space.

### M8. Minimum Size Subarray Sum
Find the minimal length of a contiguous subarray with sum ≥ `target`.  
**Hint:** Expand right, track sum; whenever sum >= target, try shrinking left and update min length.  
**Complexity:** O(n) time, O(1) space.

### M9. Longest Substring with At Most K Distinct Characters
Find the longest substring containing at most `k` distinct characters.  
**Hint:** HashMap with character counts; remove entry when count hits 0.  
**Complexity:** O(n) time, O(k) space.

### M10. Maximum Points You Can Obtain from Cards
Pick exactly `k` cards from either end of the array to maximize total points.  
**Hint:** Total - minimum subarray of size (n - k) in the middle.  
**Complexity:** O(n) time, O(1) space.

---

## 🔴 Hard (5)

### H1. Sliding Window Maximum
Given array and window size `k`, return max of each window as it slides.  
**Hint:** Monotonic deque (decreasing). Remove indices out of window from front; pop smaller elements from back.  
**Complexity:** O(n) time, O(k) space.

### H2. Substring with Concatenation of All Words
Find all starting indices of substrings that are a concatenation of each word in `words` exactly once.  
**Hint:** Slide a window of size `totalLen`; compare HashMap of word frequencies.  
**Complexity:** O(n * wordLen) time.

### H3. Minimum Number of K Consecutive Bit Flips
Find minimum number of flips of k consecutive bits to make all bits 1. Return -1 if impossible.  
**Hint:** Track flipping effect with a difference array (or deque) to avoid O(k) cost per flip.  
**Complexity:** O(n) time, O(k) space.

### H4. Count Subarrays with At Most K Distinct Integers
Count subarrays with at most `k` distinct values. Use to solve "exactly k" = "atMost(k) - atMost(k-1)".  
**Hint:** Standard sliding window with HashMap; each valid right contributes (right - left + 1) subarrays.  
**Complexity:** O(n) time, O(k) space.

### H5. Longest Continuous Subarray With Absolute Diff ≤ Limit
Find the longest subarray where the absolute diff between max and min ≤ `limit`.  
**Hint:** Two monotonic deques (one for max, one for min); shrink window when diff exceeds limit.  
**Complexity:** O(n) time, O(n) space.
