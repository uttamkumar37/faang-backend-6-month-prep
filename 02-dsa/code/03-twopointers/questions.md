# Two Pointers — Practice Questions

---

## 🟢 Easy (5)

### E1. Valid Palindrome
Given a string, return true if it is a palindrome (alphanumeric only, case-insensitive).  
**Hint:** Left pointer from start, right from end; skip non-alphanumeric, compare chars.  
**Complexity:** O(n) time, O(1) space.

```java
public boolean isPalindrome(String s) {
    int lo = 0, hi = s.length() - 1;
    while (lo < hi) {
        while (lo < hi && !Character.isLetterOrDigit(s.charAt(lo))) lo++;
        while (lo < hi && !Character.isLetterOrDigit(s.charAt(hi))) hi--;
        if (Character.toLowerCase(s.charAt(lo)) != Character.toLowerCase(s.charAt(hi))) return false;
        lo++; hi--;
    }
    return true;
}
```

### E2. Reverse String
Reverse a character array in-place.  
**Hint:** Swap `s[left]` and `s[right]` while `left < right`.  
**Complexity:** O(n) time, O(1) space.

```java
public void reverseString(char[] s) {
    int lo = 0, hi = s.length - 1;
    while (lo < hi) { char t = s[lo]; s[lo++] = s[hi]; s[hi--] = t; }
}
```

### E3. Squares of a Sorted Array
Return an array of squares of each number in sorted order.  
**Hint:** Two pointers from both ends; compare absolute values, fill result from back.  
**Complexity:** O(n) time, O(n) space.

```java
public int[] sortedSquares(int[] nums) {
    int n = nums.length;
    int[] res = new int[n];
    int lo = 0, hi = n - 1, pos = n - 1;
    while (lo <= hi) {
        int l = nums[lo] * nums[lo], r = nums[hi] * nums[hi];
        if (l > r) { res[pos--] = l; lo++; } else { res[pos--] = r; hi--; }
    }
    return res;
}
```

### E4. Remove Duplicates from Sorted Array
Remove duplicates in-place and return the new length.  
**Hint:** Slow pointer tracks next unique slot; fast pointer scans ahead.  
**Complexity:** O(n) time, O(1) space.

```java
public int removeDuplicates(int[] nums) {
    int slow = 1;
    for (int fast = 1; fast < nums.length; fast++)
        if (nums[fast] != nums[fast - 1]) nums[slow++] = nums[fast];
    return slow;
}
```

### E5. Merge Sorted Array
Merge `nums2` into `nums1` in-place (nums1 has enough space at end).  
**Hint:** Fill from the back — compare from the largest end of each array.  
**Complexity:** O(m+n) time, O(1) space.

```java
public void merge(int[] nums1, int m, int[] nums2, int n) {
    int i = m - 1, j = n - 1, k = m + n - 1;
    while (i >= 0 && j >= 0)
        nums1[k--] = (nums1[i] > nums2[j]) ? nums1[i--] : nums2[j--];
    while (j >= 0) nums1[k--] = nums2[j--];
}
```

---

## 🟡 Medium (10)

### M1. 3Sum
Find all unique triplets in array that sum to zero.  
**Hint:** Sort, fix first element, two-pointer on rest. Skip duplicates at every level.  
**Complexity:** O(n²) time, O(1) extra space.

```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i-1]) continue;
        int lo = i+1, hi = nums.length-1;
        while (lo < hi) {
            int sum = nums[i] + nums[lo] + nums[hi];
            if (sum == 0) {
                res.add(Arrays.asList(nums[i], nums[lo++], nums[hi--]));
                while (lo < hi && nums[lo] == nums[lo-1]) lo++;
                while (lo < hi && nums[hi] == nums[hi+1]) hi--;
            } else if (sum < 0) lo++; else hi--;
        }
    }
    return res;
}
```

### M2. 4Sum
Find all unique quadruplets that sum to `target`.  
**Hint:** Sort + fix two elements with nested loops, two-pointer on inner pair. Prune with bounds checks.  
**Complexity:** O(n³) time, O(1) extra space.

```java
public List<List<Integer>> fourSum(int[] nums, int target) {
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    int n = nums.length;
    for (int i = 0; i < n - 3; i++) {
        if (i > 0 && nums[i] == nums[i-1]) continue;
        for (int j = i+1; j < n - 2; j++) {
            if (j > i+1 && nums[j] == nums[j-1]) continue;
            int lo = j+1, hi = n-1;
            while (lo < hi) {
                long sum = (long)nums[i]+nums[j]+nums[lo]+nums[hi];
                if (sum == target) {
                    res.add(Arrays.asList(nums[i], nums[j], nums[lo++], nums[hi--]));
                    while (lo < hi && nums[lo] == nums[lo-1]) lo++;
                    while (lo < hi && nums[hi] == nums[hi+1]) hi--;
                } else if (sum < target) lo++; else hi--;
            }
        }
    }
    return res;
}
```

### M3. Container With Most Water
Two lines forming a container — find the pair that holds the most water.  
**Hint:** Move the pointer with the smaller height inward.  
**Complexity:** O(n) time, O(1) space.

```java
public int maxArea(int[] height) {
    int lo = 0, hi = height.length - 1, max = 0;
    while (lo < hi) {
        max = Math.max(max, Math.min(height[lo], height[hi]) * (hi - lo));
        if (height[lo] < height[hi]) lo++; else hi--;
    }
    return max;
}
```

### M4. Sort Colors (Dutch Flag)
Sort 0s, 1s, 2s in-place in a single pass.  
**Hint:** Three pointers: low=0, mid=0, high=n-1. Swap based on nums[mid].  
**Complexity:** O(n) time, O(1) space.

```java
public void sortColors(int[] nums) {
    int lo = 0, mid = 0, hi = nums.length - 1;
    while (mid <= hi) {
        if      (nums[mid] == 0) { swap(nums, lo++, mid++); }
        else if (nums[mid] == 1) { mid++; }
        else                     { swap(nums, mid, hi--); }
    }
}
private void swap(int[] a, int i, int j) { int t=a[i]; a[i]=a[j]; a[j]=t; }
```

### M5. Boats to Save People
Each boat holds at most 2 people with weight limit. Minimum boats needed?  
**Hint:** Sort, pair heaviest with lightest if possible; otherwise heaviest goes alone.  
**Complexity:** O(n log n) time, O(1) space.

```java
public int numRescueBoats(int[] people, int limit) {
    Arrays.sort(people);
    int lo = 0, hi = people.length - 1, boats = 0;
    while (lo <= hi) {
        if (people[lo] + people[hi] <= limit) lo++;
        hi--; boats++;
    }
    return boats;
}
```

### M6. Find K Closest Elements
Find `k` integers closest to `x` from a sorted array.  
**Hint:** Binary search for insertion point; then two-pointer expand outward picking closer element.  
**Complexity:** O(log n + k) time, O(k) space.

```java
public List<Integer> findClosestElements(int[] arr, int k, int x) {
    int lo = 0, hi = arr.length - k;
    while (lo < hi) {
        int mid = (lo + hi) / 2;
        if (x - arr[mid] > arr[mid + k] - x) lo = mid + 1; else hi = mid;
    }
    List<Integer> res = new ArrayList<>();
    for (int i = lo; i < lo + k; i++) res.add(arr[i]);
    return res;
}
```

### M7. Remove Nth Node From End (array simulation)
Remove the n-th node from the end of the list in one pass.  
**Hint:** Fast pointer advances n steps; then both move until fast reaches end.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0, head), fast = dummy, slow = dummy;
    for (int i = 0; i <= n; i++) fast = fast.next;
    while (fast != null) { fast = fast.next; slow = slow.next; }
    slow.next = slow.next.next;
    return dummy.next;
}
```

### M8. Partition Array by Even and Odd
Rearrange so all even numbers appear before odd numbers.  
**Hint:** Left pointer finds odd, right finds even; swap them.  
**Complexity:** O(n) time, O(1) space.

```java
public int[] sortArrayByParity(int[] nums) {
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        while (lo < hi && nums[lo] % 2 == 0) lo++;
        while (lo < hi && nums[hi] % 2 == 1) hi--;
        if (lo < hi) { int t = nums[lo]; nums[lo++] = nums[hi]; nums[hi--] = t; }
    }
    return nums;
}
```

### M9. Minimum Operations to Reduce X to Zero
Find the minimum number of elements from the left or right that sum to `x`.  
**Hint:** Equivalent to finding the longest subarray with sum = totalSum - x (sliding window).  
**Complexity:** O(n) time, O(1) space.

```java
public int minOperations(int[] nums, int x) {
    int target = -x;
    for (int n : nums) target += n; // target = totalSum - x
    if (target < 0) return -1;
    int lo = 0, sum = 0, best = -1;
    for (int hi = 0; hi < nums.length; hi++) {
        sum += nums[hi];
        while (sum > target) sum -= nums[lo++];
        if (sum == target) best = Math.max(best, hi - lo + 1);
    }
    return best == -1 ? -1 : nums.length - best;
}
```

### M10. 3Sum Closest
Find three integers whose sum is closest to `target`.  
**Hint:** Sort + fix one element, two-pointer; track minimum absolute difference.  
**Complexity:** O(n²) time, O(1) space.

```java
public int threeSumClosest(int[] nums, int target) {
    Arrays.sort(nums);
    int closest = nums[0] + nums[1] + nums[2];
    for (int i = 0; i < nums.length - 2; i++) {
        int lo = i+1, hi = nums.length-1;
        while (lo < hi) {
            int sum = nums[i] + nums[lo] + nums[hi];
            if (Math.abs(sum - target) < Math.abs(closest - target)) closest = sum;
            if      (sum < target) lo++;
            else if (sum > target) hi--;
            else return sum;
        }
    }
    return closest;
}
```

---

## 🔴 Hard (5)

### H1. Trapping Rain Water
Compute total trapped water given elevation map.  
**Hint:** Two pointers; maintain leftMax and rightMax. Process the side with smaller max.  
**Complexity:** O(n) time, O(1) space.

```java
public int trap(int[] height) {
    int lo = 0, hi = height.length-1, leftMax = 0, rightMax = 0, water = 0;
    while (lo < hi) {
        if (height[lo] < height[hi]) {
            leftMax = Math.max(leftMax, height[lo]);
            water += leftMax - height[lo++];
        } else {
            rightMax = Math.max(rightMax, height[hi]);
            water += rightMax - height[hi--];
        }
    }
    return water;
}
```

### H2. 4Sum II
Count tuples (i,j,k,l) such that A[i]+B[j]+C[k]+D[l] == 0 from four arrays.  
**Hint:** HashMap of all A+B sums; then check if -(C+D) exists in the map.  
**Complexity:** O(n²) time, O(n²) space.

```java
public int fourSumCount(int[] a, int[] b, int[] c, int[] d) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int x : a) for (int y : b) map.merge(x + y, 1, Integer::sum);
    int count = 0;
    for (int x : c) for (int y : d) count += map.getOrDefault(-(x + y), 0);
    return count;
}
```

### H3. Shortest Subarray with Sum at Least K
Find the shortest subarray (can have negatives) with sum ≥ k.  
**Hint:** Prefix sums + monotonic deque (not a simple two-pointer due to negatives).  
**Complexity:** O(n) time, O(n) space.

```java
public int shortestSubarray(int[] nums, int k) {
    int n = nums.length, minLen = Integer.MAX_VALUE;
    long[] prefix = new long[n + 1];
    for (int i = 0; i < n; i++) prefix[i+1] = prefix[i] + nums[i];
    Deque<Integer> dq = new ArrayDeque<>();
    for (int i = 0; i <= n; i++) {
        while (!dq.isEmpty() && prefix[i] - prefix[dq.peekFirst()] >= k)
            minLen = Math.min(minLen, i - dq.pollFirst());
        while (!dq.isEmpty() && prefix[i] <= prefix[dq.peekLast()])
            dq.pollLast();
        dq.addLast(i);
    }
    return minLen == Integer.MAX_VALUE ? -1 : minLen;
}
```

### H4. Count Pairs With Absolute Difference ≤ Target
Given sorted array, count pairs (i,j) with |nums[i]-nums[j]| ≤ target.  
**Hint:** For each left, binary search for the rightmost valid index. Or two-pointer counting.  
**Complexity:** O(n log n) time, O(1) space.

```java
public long countPairs(int[] nums, int target) {
    long count = 0;
    int lo = 0, hi = nums.length - 1;
    Arrays.sort(nums);
    while (lo < hi) {
        if (nums[hi] - nums[lo] <= target) { count += hi - lo; lo++; }
        else hi--;
    }
    return count;
}
```

### H5. Minimum Window with All Distinct Characters
Find the shortest substring containing all distinct characters of the original string.  
**Hint:** First find total distinct count; then shrink standard minimum-window sliding window.  
**Complexity:** O(n) time, O(n) space.

```java
public String smallestWindowContainingAll(String s) {
    Map<Character, Integer> freq = new HashMap<>();
    for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
    int required = freq.size(), have = 0, lo = 0, minLen = Integer.MAX_VALUE, start = 0;
    Map<Character, Integer> win = new HashMap<>();
    for (int hi = 0; hi < s.length(); hi++) {
        char c = s.charAt(hi);
        win.merge(c, 1, Integer::sum);
        if (win.get(c).equals(freq.get(c))) have++;
        while (have == required) {
            if (hi - lo + 1 < minLen) { minLen = hi - lo + 1; start = lo; }
            char lc = s.charAt(lo++);
            win.merge(lc, -1, Integer::sum);
            if (win.get(lc) < freq.get(lc)) have--;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(start, start + minLen);
}
```

---

## 🟡 Medium (10)

### M1. 3Sum
Find all unique triplets in array that sum to zero.  
**Hint:** Sort, fix first element, two-pointer on rest. Skip duplicates at every level.  
**Complexity:** O(n²) time, O(1) extra space.

### M2. 4Sum
Find all unique quadruplets that sum to `target`.  
**Hint:** Sort + fix two elements with nested loops, two-pointer on inner pair. Prune with bounds checks.  
**Complexity:** O(n³) time, O(1) extra space.

### M3. Container With Most Water
Two lines forming a container — find the pair that holds the most water.  
**Hint:** Move the pointer with the smaller height inward.  
**Complexity:** O(n) time, O(1) space.

### M4. Sort Colors (Dutch Flag)
Sort 0s, 1s, 2s in-place in a single pass.  
**Hint:** Three pointers: low=0, mid=0, high=n-1. Swap based on nums[mid].  
**Complexity:** O(n) time, O(1) space.

### M5. Boats to Save People
Each boat holds at most 2 people with weight limit. Minimum boats needed?  
**Hint:** Sort, pair heaviest with lightest if possible; otherwise heaviest goes alone.  
**Complexity:** O(n log n) time, O(1) space.

### M6. Find K Closest Elements
Find `k` integers closest to `x` from a sorted array.  
**Hint:** Binary search for insertion point; then two-pointer expand outward picking closer element.  
**Complexity:** O(log n + k) time, O(k) space.

### M7. Remove Nth Node From End (array simulation)
Remove the n-th node from the end of the list in one pass.  
**Hint:** Fast pointer advances n steps; then both move until fast reaches end.  
**Complexity:** O(n) time, O(1) space.

### M8. Partition Array by Even and Odd
Rearrange so all even numbers appear before odd numbers.  
**Hint:** Left pointer finds odd, right finds even; swap them.  
**Complexity:** O(n) time, O(1) space.

### M9. Minimum Operations to Reduce X to Zero
Find the minimum number of elements from the left or right that sum to `x`.  
**Hint:** Equivalent to finding the longest subarray with sum = totalSum - x (sliding window).  
**Complexity:** O(n) time, O(1) space.

### M10. 3Sum Closest
Find three integers whose sum is closest to `target`.  
**Hint:** Sort + fix one element, two-pointer; track minimum absolute difference.  
**Complexity:** O(n²) time, O(1) space.

---

## 🔴 Hard (5)

### H1. Trapping Rain Water
Compute total trapped water given elevation map.  
**Hint:** Two pointers; maintain leftMax and rightMax. Process the side with smaller max.  
**Complexity:** O(n) time, O(1) space.

### H2. 4Sum II
Count tuples (i,j,k,l) such that A[i]+B[j]+C[k]+D[l] == 0 from four arrays.  
**Hint:** HashMap of all A+B sums; then check if -(C+D) exists in the map.  
**Complexity:** O(n²) time, O(n²) space.

### H3. Shortest Subarray with Sum at Least K
Find the shortest subarray (can have negatives) with sum ≥ k.  
**Hint:** Prefix sums + monotonic deque (not a simple two-pointer due to negatives).  
**Complexity:** O(n) time, O(n) space.

### H4. Count Pairs With Absolute Difference ≤ Target
Given sorted array, count pairs (i,j) with |nums[i]-nums[j]| ≤ target.  
**Hint:** For each left, binary search for the rightmost valid index. Or two-pointer counting.  
**Complexity:** O(n log n) time, O(1) space.

### H5. Minimum Window with All Distinct Characters
Find the shortest substring containing all distinct characters of the original string.  
**Hint:** First find total distinct count; then shrink standard minimum-window sliding window.  
**Complexity:** O(n) time, O(n) space.
