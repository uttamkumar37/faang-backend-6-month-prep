# Two Pointers — Complete Theory (Basic → Advanced)

---

## 1. What Are Two Pointers?

Two pointers is a technique where two index variables traverse a data structure — typically from **opposite ends** or in the **same direction** — to reduce a nested O(n²) loop to O(n).

**When to reach for two pointers:**
- Array/string is sorted (or can be processed in order)
- Searching for pairs/triplets that satisfy a sum/condition
- Removing duplicates or partitioning in-place
- Comparing prefixes and suffixes (palindrome, reverse)
- Merging two sorted arrays/lists

---

## 2. Pattern 1 — Opposite Ends (Converging)

```
l →                ← r
[1, 2, 3, 4, 5, 6]
```

Move `l` right or `r` left based on a comparison. Loop ends when `l >= r`.

**Template:**
```java
int l = 0, r = nums.length - 1;
while (l < r) {
    int val = compute(nums[l], nums[r]);
    if (val == target)  { /* record */ l++; r--; }
    else if (val < target) l++;   // need larger
    else                r--;      // need smaller
}
```

### Two Sum II (sorted array)
```java
public int[] twoSum(int[] nums, int target) {
    int l = 0, r = nums.length - 1;
    while (l < r) {
        int sum = nums[l] + nums[r];
        if      (sum == target) return new int[]{l+1, r+1};
        else if (sum < target)  l++;
        else                    r--;
    }
    return new int[]{};
}
```

### Three Sum (sort + two pointers for each anchor)
```java
public List<List<Integer>> threeSum(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    for (int i = 0; i < nums.length - 2; i++) {
        if (i > 0 && nums[i] == nums[i-1]) continue;   // skip dup anchors
        int l = i+1, r = nums.length-1;
        while (l < r) {
            int sum = nums[i] + nums[l] + nums[r];
            if (sum == 0) {
                res.add(Arrays.asList(nums[i], nums[l], nums[r]));
                while (l < r && nums[l] == nums[l+1]) l++;  // skip dup
                while (l < r && nums[r] == nums[r-1]) r--;  // skip dup
                l++; r--;
            } else if (sum < 0) l++;
            else r--;
        }
    }
    return res;
}
```

### Valid Palindrome
```java
public boolean isPalindrome(String s) {
    int l = 0, r = s.length() - 1;
    while (l < r) {
        while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
        while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
        if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r))) return false;
        l++; r--;
    }
    return true;
}
```

### Container With Most Water
```java
public int maxArea(int[] height) {
    int l = 0, r = height.length - 1, max = 0;
    while (l < r) {
        max = Math.max(max, (r - l) * Math.min(height[l], height[r]));
        if (height[l] < height[r]) l++;
        else r--;
    }
    return max;
}
```

---

## 3. Pattern 2 — Same Direction (Fast / Slow)

```
slow →
fast   →→→
[1, 1, 2, 2, 3, 4]
```

Slow pointer writes valid elements; fast pointer scans for them.

**Template:**
```java
int slow = 0;
for (int fast = 0; fast < n; fast++) {
    if (condition(nums[fast])) {
        nums[slow++] = nums[fast];
    }
}
// new effective length = slow
```

### Remove Duplicates from Sorted Array
```java
public int removeDuplicates(int[] nums) {
    int slow = 1;
    for (int fast = 1; fast < nums.length; fast++)
        if (nums[fast] != nums[fast-1]) nums[slow++] = nums[fast];
    return slow;
}
```

### Move Zeroes
```java
public void moveZeroes(int[] nums) {
    int slow = 0;
    for (int fast = 0; fast < nums.length; fast++)
        if (nums[fast] != 0) nums[slow++] = nums[fast];
    while (slow < nums.length) nums[slow++] = 0;
}
```

### Floyd's Cycle Detection (Linked List / Array)
```java
// Phase 1: detect cycle
int slow = nums[0], fast = nums[0];
do { slow = nums[slow]; fast = nums[nums[fast]]; } while (slow != fast);

// Phase 2: find entry point
slow = nums[0];
while (slow != fast) { slow = nums[slow]; fast = nums[fast]; }
// slow == fast == cycle entry point
```

---

## 4. Pattern 3 — Two Pointers on Two Arrays (Merge)

```java
// Merge two sorted arrays into one
int i = 0, j = 0, k = 0;
while (i < a.length && j < b.length)
    res[k++] = a[i] <= b[j] ? a[i++] : b[j++];
while (i < a.length) res[k++] = a[i++];
while (j < b.length) res[k++] = b[j++];
```

Variants: merge sorted arrays in-place (fill from back), intersect two arrays.

---

## 5. Pattern 4 — Sliding Window (Fixed Distance)

Two pointers with a fixed gap. See dedicated sliding-window guide for variable gap.

```java
// Is there a subarray of length k with sum == target?
int sum = 0;
for (int i = 0; i < k; i++) sum += nums[i];
if (sum == target) return true;
for (int i = k; i < nums.length; i++) {
    sum += nums[i] - nums[i-k];
    if (sum == target) return true;
}
```

---

## 6. Pattern 5 — Dutch National Flag (3-Pointer Partition)

```java
// Partition: lo (0s) | mid (1s in progress) | hi (2s)
int lo = 0, mid = 0, hi = n - 1;
while (mid <= hi) {
    if      (a[mid] == 0) swap(a, lo++, mid++);
    else if (a[mid] == 1) mid++;
    else                  swap(a, mid, hi--);  // don't advance mid
}
```

---

## 7. When Sorting is Acceptable

Two-pointer often **requires sorting first** (O(n log n)). This is fine when:
- The problem asks for values (not original indices)
- Duplicates need to be handled and sorted order simplifies that

```java
// k-diff pairs in array — sort, then two pointers
Arrays.sort(nums);
int l = 0, r = 0, count = 0;
while (l < nums.length && r < nums.length) {
    if (l == r || nums[r] - nums[l] < k) r++;
    else if (nums[r] - nums[l] > k) l++;
    else { count++; l++; }
}
```

---

## 8. Advanced — Four Sum

```java
// Reduce to 2-Sum with two outer loops + inner two pointers — O(n³)
public List<List<Integer>> fourSum(int[] nums, int target) {
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    int n = nums.length;
    for (int a = 0; a < n - 3; a++) {
        if (a > 0 && nums[a] == nums[a-1]) continue;
        for (int b = a+1; b < n - 2; b++) {
            if (b > a+1 && nums[b] == nums[b-1]) continue;
            int l = b+1, r = n-1;
            while (l < r) {
                long sum = (long)nums[a]+nums[b]+nums[l]+nums[r];
                if      (sum == target) { res.add(Arrays.asList(nums[a],nums[b],nums[l++],nums[r--])); while(l<r&&nums[l]==nums[l-1])l++; while(l<r&&nums[r]==nums[r+1])r--; }
                else if (sum < target)  l++;
                else                    r--;
            }
        }
    }
    return res;
}
```

---

## 9. Trapping Rain Water (Advanced Two-Pointer)

```
height = [0,1,0,2,1,0,1,3,2,1,2,1]
water   = 6 units trapped

water[i] = min(maxLeft[i], maxRight[i]) - height[i]
```

**O(1) space solution** — track running max from both sides:
```java
public int trap(int[] height) {
    int l = 0, r = height.length-1, maxL = 0, maxR = 0, water = 0;
    while (l < r) {
        if (height[l] <= height[r]) {
            if (height[l] >= maxL) maxL = height[l];
            else water += maxL - height[l];
            l++;
        } else {
            if (height[r] >= maxR) maxR = height[r];
            else water += maxR - height[r];
            r--;
        }
    }
    return water;
}
```

**Why it works**: the side with the smaller current height is the bottleneck. The water at that position is determined by the max from its own side.

---

## 10. Complexity Analysis

| Pattern | Time | Space |
|---|---|---|
| Opposite ends (pair sum) | O(n) after sort | O(1) |
| Three sum | O(n²) after sort | O(1) |
| Fast/slow (remove duplicates) | O(n) | O(1) |
| Floyd's cycle | O(n) | O(1) |
| Merge two sorted | O(m+n) | O(1) in-place |
| Trapping rain water | O(n) | O(1) |

---

## 11. Decision Guide

| Scenario | Two-Pointer Pattern |
|---|---|
| Pair/triplet sum in sorted array | Converging (opposite ends) |
| Palindrome check | Converging |
| Remove duplicates / move zeroes | Fast/slow, same direction |
| Detect cycle in linked list | Fast (×2) / slow (×1) |
| Find cycle start | Floyd's phase 2 |
| Merge sorted arrays | Parallel two pointers |
| 3-way partition | Three pointer (Dutch Flag) |
| Trapping water | Two-pointer with running max |

---

## 12. Common Pitfalls

- **Forgetting to skip duplicates**: after recording a valid triplet, advance both pointers and skip equals
- **Off-by-one in loop condition**: `while (l < r)` not `<=` (two pointers must not cross)
- **Not sorting first**: two-pointer only works on sorted input for most patterns
- **Fast/slow wrong initialisation**: both must start at head (or same position) for Floyd's
- **Four sum overflow**: `(long)nums[a]+nums[b]+nums[l]+nums[r]` — cast before addition
