# Binary Search — Complete Theory (Basic → Advanced)

---

## 1. What Is Binary Search?

Binary search eliminates half the search space in each step, giving **O(log n)** search time on a **sorted** (or monotone) collection.

```
Search 7 in [1, 3, 5, 7, 9, 11, 13]
         lo=0              hi=6
step 1:  mid=3  a[3]=7   → found!

Search 6:
step 1:  mid=3  a[3]=7   > 6  → hi=2
step 2:  mid=1  a[1]=3   < 6  → lo=2
step 3:  mid=2  a[2]=5   < 6  → lo=3 > hi → not found
```

**Key insight**: binary search works whenever the search predicate is **monotone** — once false, always false (or vice versa). The array doesn't need to literally be sorted, just have this monotone structure.

---

## 2. Classic Template — Exact Match

```java
// Returns index where target exists, or -1
public int binarySearch(int[] nums, int target) {
    int lo = 0, hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;   // avoid int overflow vs (lo+hi)/2
        if      (nums[mid] == target) return mid;
        else if (nums[mid] < target)  lo = mid + 1;
        else                          hi = mid - 1;
    }
    return -1;
}
```

**Loop invariant**: target, if present, is always within `[lo, hi]`.

---

## 3. Lower Bound / Upper Bound

### Lower Bound — first index where `nums[i] >= target`
```java
public int lowerBound(int[] nums, int target) {
    int lo = 0, hi = nums.length;    // hi = n, not n-1
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] < target) lo = mid + 1;
        else                    hi = mid;          // include mid
    }
    return lo;  // == hi
}
```

### Upper Bound — first index where `nums[i] > target`
```java
public int upperBound(int[] nums, int target) {
    int lo = 0, hi = nums.length;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] <= target) lo = mid + 1;
        else                     hi = mid;
    }
    return lo;
}
```

**Count of targets** = `upperBound(nums, target) - lowerBound(nums, target)`

---

## 4. Lo+1 < Hi Template (Boundary Finder)

Useful when you want to find the boundary without handling edge cases manually.

```java
// Invariant: nums[lo] < target <= nums[hi]
int lo = 0, hi = nums.length - 1;
while (lo + 1 < hi) {
    int mid = lo + (hi - lo) / 2;
    if (nums[mid] < target) lo = mid;
    else                    hi = mid;
}
// lo and hi are adjacent, check both
```

---

## 5. Rotated Sorted Array

```
[4, 5, 6, 7, 0, 1, 2]  ← rotation at index 4
```

Key insight: one half is always sorted. Determine which half, then decide.

```java
public int search(int[] nums, int target) {
    int lo = 0, hi = nums.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] == target) return mid;
        // left half is sorted
        if (nums[lo] <= nums[mid]) {
            if (nums[lo] <= target && target < nums[mid]) hi = mid - 1;
            else lo = mid + 1;
        } else {   // right half is sorted
            if (nums[mid] < target && target <= nums[hi]) lo = mid + 1;
            else hi = mid - 1;
        }
    }
    return -1;
}
```

**Find Minimum in Rotated Array:**
```java
public int findMin(int[] nums) {
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] > nums[hi]) lo = mid + 1;  // min is right of mid
        else                      hi = mid;       // min is at mid or left
    }
    return nums[lo];
}
```

---

## 6. Answer-Space Binary Search

Binary search over the **answer** rather than the array index. Works when:
- You can check "is X a valid answer?" in O(n) or O(n log n)
- The valid answers form a monotone sequence: if X is valid, X+1 may also be valid

**Template:**
```java
int lo = minPossibleAnswer, hi = maxPossibleAnswer;
while (lo < hi) {
    int mid = lo + (hi - lo) / 2;
    if (feasible(mid)) hi = mid;     // mid works, try smaller
    else               lo = mid + 1; // mid too small
}
return lo;  // smallest valid answer
```

### Koko Eating Bananas
```java
// Find minimum speed k such that all piles eaten in h hours
public int minEatingSpeed(int[] piles, int h) {
    int lo = 1, hi = Arrays.stream(piles).max().getAsInt();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        int hours = 0;
        for (int p : piles) hours += (p + mid - 1) / mid;  // ceil(p/mid)
        if (hours <= h) hi = mid;
        else            lo = mid + 1;
    }
    return lo;
}
```

### Split Array Largest Sum (Minimize maximum partition sum)
```java
public int splitArray(int[] nums, int k) {
    int lo = Arrays.stream(nums).max().getAsInt();
    int hi = Arrays.stream(nums).sum();
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (canSplit(nums, k, mid)) hi = mid;
        else lo = mid + 1;
    }
    return lo;
}

private boolean canSplit(int[] nums, int k, int limit) {
    int parts = 1, sum = 0;
    for (int n : nums) {
        if (sum + n > limit) { parts++; sum = 0; }
        sum += n;
    }
    return parts <= k;
}
```

---

## 7. Binary Search on 2D Matrix

### Matrix where rows and columns are sorted
```java
public boolean searchMatrix(int[][] mat, int target) {
    int r = 0, c = mat[0].length - 1;
    while (r < mat.length && c >= 0) {
        if      (mat[r][c] == target) return true;
        else if (mat[r][c] > target)  c--;
        else                          r++;
    }
    return false;
}
```

### Sorted 2D matrix treated as 1D
```java
// mat[r][c] = nums[mid] where r=mid/cols, c=mid%cols
public boolean searchMatrix(int[][] mat, int target) {
    int rows = mat.length, cols = mat[0].length;
    int lo = 0, hi = rows * cols - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        int val = mat[mid / cols][mid % cols];
        if      (val == target) return true;
        else if (val < target)  lo = mid + 1;
        else                    hi = mid - 1;
    }
    return false;
}
```

---

## 8. Peak Element

A peak is `nums[i] > nums[i-1]` and `nums[i] > nums[i+1]`.

```java
public int findPeakElement(int[] nums) {
    int lo = 0, hi = nums.length - 1;
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (nums[mid] < nums[mid+1]) lo = mid + 1;  // slope going up → peak right
        else                         hi = mid;        // slope going down → peak left or mid
    }
    return lo;
}
```

---

## 9. Binary Search on Floating Point

Used to find square roots, cube roots, etc. Use `eps` convergence criterion.

```java
public double sqrt(double x) {
    double lo = 0, hi = x;
    while (hi - lo > 1e-9) {
        double mid = (lo + hi) / 2;
        if (mid * mid <= x) lo = mid;
        else                hi = mid;
    }
    return lo;
}
```

---

## 10. Median of Two Sorted Arrays — O(log(min(m,n)))

```
Goal: partition A and B such that left halves combined == right halves combined

A: [1, 3, 5, 7]    B: [2, 4, 6, 8]
       i                   j
left count = (m + n + 1) / 2

Binary search on cuts of A; cut of B is derived.
Valid partition: A[i-1] <= B[j] && B[j-1] <= A[i]
```

```java
public double findMedianSortedArrays(int[] A, int[] B) {
    if (A.length > B.length) return findMedianSortedArrays(B, A);
    int m = A.length, n = B.length, half = (m + n + 1) / 2;
    int lo = 0, hi = m;
    while (lo < hi) {
        int i = lo + (hi - lo) / 2;
        int j = half - i;
        if (A[i] < B[j-1]) lo = i + 1;
        else                hi = i;
    }
    int i = lo, j = half - i;
    int maxLeft = Math.max(i == 0 ? Integer.MIN_VALUE : A[i-1],
                           j == 0 ? Integer.MIN_VALUE : B[j-1]);
    if ((m + n) % 2 == 1) return maxLeft;
    int minRight = Math.min(i == m ? Integer.MAX_VALUE : A[i],
                            j == n ? Integer.MAX_VALUE : B[j]);
    return (maxLeft + minRight) / 2.0;
}
```

---

## 11. Complexity Reference

| Problem | Time | Space |
|---|---|---|
| Classic exact search | O(log n) | O(1) |
| Lower / upper bound | O(log n) | O(1) |
| Rotated array search | O(log n) | O(1) |
| Answer-space search | O(n log(hi-lo)) | O(1) |
| Median of two arrays | O(log min(m,n)) | O(1) |
| 2D matrix search | O(m+n) or O(log mn) | O(1) |

---

## 12. Decision Guide

| Scenario | Variant |
|---|---|
| Exact value in sorted array | Classic lo ≤ hi |
| First position ≥ target | Lower bound |
| Last position ≤ target | Upper bound - 1 |
| Search in rotated array | Halve by sorted side |
| Minimise maximum / maximise minimum | Answer-space binary search |
| Find peak or valley | One-sided elimination by slope |
| Real-valued answer | Floating-point binary search |

---

## 13. Common Pitfalls

- **Overflow**: always `mid = lo + (hi - lo) / 2`, not `(lo + hi) / 2`
- **Infinite loop**: if `hi = mid` and `lo = mid` co-exist, one must advance (add +1 or -1)
- **Wrong boundary**: `hi = nums.length` (inclusive of out-of-bounds slot) for lower bound
- **Answer-space direction**: know whether you want smallest valid or largest valid, then set `hi=mid` or `lo=mid+1` accordingly
- **Two sorted arrays**: always binary search on the shorter array
- **Rotated array**: `nums[lo] <= nums[mid]` (note `<=`) handles the edge case where `lo == mid`
