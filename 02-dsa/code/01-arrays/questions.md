# Arrays & Strings — Practice Questions

---

## 🟢 Easy (5)

### E1. Two Sum
Given an array of integers `nums` and a target, return indices of the two numbers that add up to target.  
**Hint:** Use a HashMap to store complement → index.  
**Complexity:** O(n) time, O(n) space.

```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> map = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int complement = target - nums[i];
        if (map.containsKey(complement)) return new int[]{map.get(complement), i};
        map.put(nums[i], i);
    }
    return new int[]{};
}
```

### E2. Contains Duplicate
Given an integer array, return `true` if any value appears at least twice.  
**Hint:** HashSet — if add returns false, duplicate exists.  
**Complexity:** O(n) time, O(n) space.

```java
public boolean containsDuplicate(int[] nums) {
    Set<Integer> seen = new HashSet<>();
    for (int n : nums) if (!seen.add(n)) return true;
    return false;
}
```

### E3. Move Zeroes
Move all `0`s to the end while keeping the relative order of non-zero elements in-place.  
**Hint:** Two-pointer — slow pointer tracks position for next non-zero.  
**Complexity:** O(n) time, O(1) space.

```java
public void moveZeroes(int[] nums) {
    int slow = 0;
    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) nums[slow++] = nums[fast];
    }
    while (slow < nums.length) nums[slow++] = 0;
}
```

### E4. Single Number
Every element appears twice except one. Find the element that appears only once.  
**Hint:** XOR all elements — duplicates cancel out (a ^ a = 0).  
**Complexity:** O(n) time, O(1) space.

```java
public int singleNumber(int[] nums) {
    int result = 0;
    for (int n : nums) result ^= n;
    return result;
}
```

### E5. Best Time to Buy and Sell Stock
Find the maximum profit from one buy and one sell (buy before sell).  
**Hint:** Track running minimum price; update max profit at each step.  
**Complexity:** O(n) time, O(1) space.

```java
public int maxProfit(int[] prices) {
    int minPrice = Integer.MAX_VALUE, maxProfit = 0;
    for (int p : prices) {
        minPrice = Math.min(minPrice, p);
        maxProfit = Math.max(maxProfit, p - minPrice);
    }
    return maxProfit;
}
```

---

## 🟡 Medium (10)

### M1. Product of Array Except Self
Return an array where each element is the product of all other elements. No division allowed.  
**Hint:** Build left-prefix products, then multiply right-suffix in a second pass.  
**Complexity:** O(n) time, O(1) extra space (output array excluded).

```java
public int[] productExceptSelf(int[] nums) {
    int n = nums.length;
    int[] res = new int[n];
    res[0] = 1;
    for (int i = 1; i < n; i++) res[i] = res[i-1] * nums[i-1];
    int right = 1;
    for (int i = n-1; i >= 0; i--) { res[i] *= right; right *= nums[i]; }
    return res;
}
```

### M2. Maximum Subarray (Kadane's)
Find the contiguous subarray with the largest sum.  
**Hint:** `maxEndingHere = max(num, maxEndingHere + num)`.  
**Complexity:** O(n) time, O(1) space.

```java
public int maxSubArray(int[] nums) {
    int maxSum = nums[0], curr = nums[0];
    for (int i = 1; i < nums.length; i++) {
        curr = Math.max(nums[i], curr + nums[i]);
        maxSum = Math.max(maxSum, curr);
    }
    return maxSum;
}
```

### M3. 3Sum
Find all unique triplets that sum to zero.  
**Hint:** Sort + fix one element, then two-pointer on the rest. Skip duplicates.  
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
                res.add(Arrays.asList(nums[i], nums[lo], nums[hi]));
                while (lo < hi && nums[lo] == nums[lo+1]) lo++;
                while (lo < hi && nums[hi] == nums[hi-1]) hi--;
                lo++; hi--;
            } else if (sum < 0) lo++; else hi--;
        }
    }
    return res;
}
```

### M4. Container With Most Water
Given height array, find two lines that form a container holding the most water.  
**Hint:** Two pointers from both ends; move the pointer with the smaller height.  
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

### M5. Subarray Sum Equals K
Count the number of subarrays whose sum equals `k`.  
**Hint:** Prefix sum + HashMap: count how many times `prefixSum - k` appeared.  
**Complexity:** O(n) time, O(n) space.

```java
public int subarraySum(int[] nums, int k) {
    Map<Integer, Integer> map = new HashMap<>();
    map.put(0, 1);
    int sum = 0, count = 0;
    for (int n : nums) {
        sum += n;
        count += map.getOrDefault(sum - k, 0);
        map.merge(sum, 1, Integer::sum);
    }
    return count;
}
```

### M6. Rotate Array
Rotate array to the right by `k` steps in-place.  
**Hint:** Reverse whole array, then reverse first `k`, then reverse rest.  
**Complexity:** O(n) time, O(1) space.

```java
public void rotate(int[] nums, int k) {
    k %= nums.length;
    reverse(nums, 0, nums.length-1);
    reverse(nums, 0, k-1);
    reverse(nums, k, nums.length-1);
}
private void reverse(int[] a, int lo, int hi) {
    while (lo < hi) { int t = a[lo]; a[lo++] = a[hi]; a[hi--] = t; }
}
```

### M7. Find the Duplicate Number
Array of n+1 integers in range [1, n]; find the duplicate. No modifying the array, O(1) extra space.  
**Hint:** Floyd's cycle detection — treat array as linked list (index → value).  
**Complexity:** O(n) time, O(1) space.

```java
public int findDuplicate(int[] nums) {
    int slow = nums[0], fast = nums[0];
    do { slow = nums[slow]; fast = nums[nums[fast]]; } while (slow != fast);
    slow = nums[0];
    while (slow != fast) { slow = nums[slow]; fast = nums[fast]; }
    return slow;
}
```

### M8. Sort Colors (Dutch National Flag)
Sort array of 0s, 1s, and 2s in-place in one pass.  
**Hint:** Three-pointer: low, mid, high. Swap based on `nums[mid]`.  
**Complexity:** O(n) time, O(1) space.

```java
public void sortColors(int[] nums) {
    int lo = 0, mid = 0, hi = nums.length - 1;
    while (mid <= hi) {
        if (nums[mid] == 0) { swap(nums, lo++, mid++); }
        else if (nums[mid] == 1) { mid++; }
        else { swap(nums, mid, hi--); }
    }
}
private void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
```

### M9. Maximum Product Subarray
Find the contiguous subarray with the largest product.  
**Hint:** Track both max and min (min can become max when multiplied by negative).  
**Complexity:** O(n) time, O(1) space.

```java
public int maxProduct(int[] nums) {
    int maxP = nums[0], minP = nums[0], res = nums[0];
    for (int i = 1; i < nums.length; i++) {
        if (nums[i] < 0) { int t = maxP; maxP = minP; minP = t; }
        maxP = Math.max(nums[i], maxP * nums[i]);
        minP = Math.min(nums[i], minP * nums[i]);
        res = Math.max(res, maxP);
    }
    return res;
}
```

### M10. Next Permutation
Rearrange numbers in place to the next lexicographically greater permutation.  
**Hint:** Find rightmost ascending pair, swap with next larger element, reverse suffix.  
**Complexity:** O(n) time, O(1) space.

```java
public void nextPermutation(int[] nums) {
    int n = nums.length, i = n-2;
    while (i >= 0 && nums[i] >= nums[i+1]) i--;
    if (i >= 0) {
        int j = n-1;
        while (nums[j] <= nums[i]) j--;
        swap(nums, i, j);
    }
    // reverse suffix
    int lo = i+1, hi = n-1;
    while (lo < hi) swap(nums, lo++, hi--);
}
```

---

## 🔴 Hard (5)

### H1. Trapping Rain Water
Given elevation map, compute total water it can trap.  
**Hint:** Two-pointer: track leftMax and rightMax; water at each bar = min(leftMax, rightMax) - height[i].  
**Complexity:** O(n) time, O(1) space.

```java
public int trap(int[] height) {
    int lo = 0, hi = height.length-1, leftMax = 0, rightMax = 0, water = 0;
    while (lo < hi) {
        if (height[lo] < height[hi]) {
            if (height[lo] >= leftMax) leftMax = height[lo]; else water += leftMax - height[lo];
            lo++;
        } else {
            if (height[hi] >= rightMax) rightMax = height[hi]; else water += rightMax - height[hi];
            hi--;
        }
    }
    return water;
}
```

### H2. First Missing Positive
Find the smallest missing positive integer. O(n) time, O(1) space.  
**Hint:** Use the array itself as a hash — place value `v` at index `v-1` via cyclic swaps.  
**Complexity:** O(n) time, O(1) space.

```java
public int firstMissingPositive(int[] nums) {
    int n = nums.length;
    for (int i = 0; i < n; i++)
        while (nums[i] > 0 && nums[i] <= n && nums[nums[i]-1] != nums[i])
            swap(nums, i, nums[i]-1);
    for (int i = 0; i < n; i++) if (nums[i] != i+1) return i+1;
    return n+1;
}
```

### H3. Largest Rectangle in Histogram
Find the largest rectangle area in a histogram.  
**Hint:** Monotonic stack — pop when a shorter bar is found, compute width using indices.  
**Complexity:** O(n) time, O(n) space.

```java
public int largestRectangleArea(int[] heights) {
    Deque<Integer> stack = new ArrayDeque<>();
    int max = 0;
    for (int i = 0; i <= heights.length; i++) {
        int h = (i == heights.length) ? 0 : heights[i];
        while (!stack.isEmpty() && h < heights[stack.peek()]) {
            int height = heights[stack.pop()];
            int width = stack.isEmpty() ? i : i - stack.peek() - 1;
            max = Math.max(max, height * width);
        }
        stack.push(i);
    }
    return max;
}
```

### H4. Median of Two Sorted Arrays
Find the median of two sorted arrays of size m and n.  
**Hint:** Binary search on the smaller array to find the correct partition point.  
**Complexity:** O(log(min(m,n))) time.

```java
public double findMedianSortedArrays(int[] A, int[] B) {
    if (A.length > B.length) return findMedianSortedArrays(B, A);
    int m = A.length, n = B.length, lo = 0, hi = m;
    while (lo <= hi) {
        int i = (lo + hi) / 2, j = (m + n + 1) / 2 - i;
        int maxL_A = (i == 0) ? Integer.MIN_VALUE : A[i-1];
        int minR_A = (i == m) ? Integer.MAX_VALUE : A[i];
        int maxL_B = (j == 0) ? Integer.MIN_VALUE : B[j-1];
        int minR_B = (j == n) ? Integer.MAX_VALUE : B[j];
        if (maxL_A <= minR_B && maxL_B <= minR_A) {
            if ((m + n) % 2 == 0) return (Math.max(maxL_A, maxL_B) + Math.min(minR_A, minR_B)) / 2.0;
            else return Math.max(maxL_A, maxL_B);
        } else if (maxL_A > minR_B) hi = i-1; else lo = i+1;
    }
    throw new IllegalArgumentException();
}
```

### H5. Longest Consecutive Sequence
Find the length of the longest consecutive elements sequence. O(n) time.  
**Hint:** HashSet — only start counting from numbers that have no left neighbour (x-1 not in set).  
**Complexity:** O(n) time, O(n) space.

```java
public int longestConsecutive(int[] nums) {
    Set<Integer> set = new HashSet<>();
    for (int n : nums) set.add(n);
    int best = 0;
    for (int n : set) {
        if (!set.contains(n - 1)) {
            int len = 1;
            while (set.contains(n + len)) len++;
            best = Math.max(best, len);
        }
    }
    return best;
}
```
