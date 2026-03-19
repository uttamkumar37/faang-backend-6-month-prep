# Arrays — Complete Theory (Basic → Advanced)

---

## 1. What Is an Array?

An array is a **contiguous block of memory** where elements are stored at fixed offsets from the base address.

```
int[] a = {10, 20, 30, 40, 50}

Memory (each int = 4 bytes):
Address:  1000  1004  1008  1012  1016
Value:     10    20    30    40    50
Index:      0     1     2     3     4

a[i]  =  base_address + i × element_size    →  O(1) random access
```

**Java-specific**: arrays are objects on the heap. `int[]` stores primitives directly (no boxing); `Integer[]` stores references.

---

## 2. Static vs Dynamic Arrays

| Property | `int[]` | `ArrayList<Integer>` |
|---|---|---|
| Size | Fixed at creation | Grows dynamically |
| Memory layout | Contiguous primitives | Array of references |
| Resize | — | O(n) copy on overflow |
| Amortised append | — | **O(1)** (doubling) |
| Access `[i]` | O(1) | O(1) |
| Insert/delete middle | O(n) | O(n) |

**Doubling amortisation proof**: Costs are 1+1+2+4+…+n = 2n → O(1) per append amortised.

```java
// Java useful idioms
int[] arr = new int[n];                  // zero-initialised
int[] copy = Arrays.copyOf(arr, n);      // shallow copy, new length n
Arrays.fill(arr, -1);                    // fill entire array
Arrays.fill(arr, l, r, 0);              // fill [l, r)
Arrays.sort(arr);                        // Dual-Pivot QuickSort O(n log n)
int idx = Arrays.binarySearch(arr, x);  // only on sorted arrays
```

---

## 3. Complexity Summary

| Operation | Time |
|---|---|
| Access `a[i]` | O(1) |
| Traverse | O(n) |
| Insert/delete at end | O(1) dynamic |
| Insert/delete at index i | O(n) |
| Search (unsorted) | O(n) |
| Search (sorted, binary) | O(log n) |
| Sort | O(n log n) |

---

## 4. Core Patterns

### 4.1 Prefix Sum — Range Queries in O(1)

Precompute cumulative sums so any range sum becomes a single subtraction.

```
Array:   [  3,  1,  4,  1,  5,  9,  2 ]
Prefix:  [  0,  3,  4,  8,  9, 14, 23, 25 ]   ← size n+1, prefix[0]=0

rangeSum(l, r) = prefix[r+1] - prefix[l]
rangeSum(2,5)  = prefix[6] - prefix[2] = 23 - 4 = 19
```

```java
int[] buildPrefix(int[] a) {
    int[] p = new int[a.length + 1];
    for (int i = 0; i < a.length; i++) p[i+1] = p[i] + a[i];
    return p;
}
int rangeSum(int[] p, int l, int r) { return p[r+1] - p[l]; }
```

**2D Prefix Sum** — matrix range queries:
```java
int[][] build2D(int[][] mat) {
    int m = mat.length, n = mat[0].length;
    int[][] p = new int[m+1][n+1];
    for (int i = 1; i <= m; i++)
        for (int j = 1; j <= n; j++)
            p[i][j] = mat[i-1][j-1] + p[i-1][j] + p[i][j-1] - p[i-1][j-1];
    return p;
}
// sum of rectangle (r1,c1)..(r2,c2), 0-indexed
int query(int[][] p, int r1, int c1, int r2, int c2) {
    return p[r2+1][c2+1] - p[r1][c2+1] - p[r2+1][c1] + p[r1][c1];
}
```

---

### 4.2 Difference Array — Range Updates in O(1)

Add a value to every element in a range without touching each element.

```
Add 5 to indices [1..3]:
diff[1] += 5, diff[4] -= 5
Reconstruct via prefix sum → original + delta applied to range
```

```java
void rangeAdd(int[] diff, int l, int r, int val) {
    diff[l] += val;
    if (r + 1 < diff.length) diff[r+1] -= val;
}
int[] reconstruct(int[] diff) {
    int[] a = new int[diff.length];
    a[0] = diff[0];
    for (int i = 1; i < diff.length; i++) a[i] = a[i-1] + diff[i];
    return a;
}
```

**Use when**: many range-update queries, answer needed at the end (not after each update).

---

### 4.3 Kadane's Algorithm — Maximum Subarray Sum

**At each index**: extend current subarray or restart from this element.

```
nums = [-2, 1, -3, 4, -1, 2, 1, -5, 4]
cur     -2  1  -2  4   3  5  6   1  5
ans      6  (subarray [4,-1,2,1])
```

```java
public int maxSubArray(int[] nums) {
    int cur = nums[0], max = nums[0];
    for (int i = 1; i < nums.length; i++) {
        cur = Math.max(nums[i], cur + nums[i]);
        max = Math.max(max, cur);
    }
    return max;
}
```

**Variant — circular subarray max**: `max(kadane(nums), totalSum - kadane(-nums))`.

---

### 4.4 Two Pointers on Arrays

- **Opposite ends** (sorted array pair sum):
```java
int l = 0, r = nums.length - 1;
while (l < r) {
    int sum = nums[l] + nums[r];
    if (sum == target) return new int[]{l, r};
    else if (sum < target) l++;
    else r--;
}
```

- **Fast/slow** (remove duplicates in-place):
```java
int slow = 1;
for (int fast = 1; fast < nums.length; fast++)
    if (nums[fast] != nums[fast-1]) nums[slow++] = nums[fast];
// new length = slow
```

---

### 4.5 Dutch National Flag (3-Way Partition)

Partition into three groups in O(n) time O(1) space.

```
Invariant zones:  [ 0s | 1s | unprocessed | 2s ]
                        ↑lo  ↑mid         hi↑
```

```java
void sortColors(int[] a) {
    int lo = 0, mid = 0, hi = a.length - 1;
    while (mid <= hi) {
        if      (a[mid] == 0) swap(a, lo++, mid++);
        else if (a[mid] == 1) mid++;
        else                  swap(a, mid, hi--);   // don't advance mid
    }
}
```

---

### 4.6 Product Except Self (Two-Pass)

```java
public int[] productExceptSelf(int[] nums) {
    int n = nums.length;
    int[] res = new int[n];
    res[0] = 1;
    for (int i = 1; i < n; i++) res[i] = res[i-1] * nums[i-1];   // prefix
    int right = 1;
    for (int i = n-1; i >= 0; i--) { res[i] *= right; right *= nums[i]; }  // suffix
    return res;
}
```

---

## 5. Sorting — Know the Internals

| Algorithm | Best | Avg | Worst | Space | Stable | Notes |
|---|---|---|---|---|---|---|
| Bubble | O(n) | O(n²) | O(n²) | O(1) | ✅ | Only for nearly sorted |
| Insertion | O(n) | O(n²) | O(n²) | O(1) | ✅ | Best for small/nearly sorted |
| Merge | O(n log n) | O(n log n) | O(n log n) | O(n) | ✅ | Preferred for linked lists |
| Quick | O(n log n) | O(n log n) | O(n²) | O(log n) | ❌ | Best average; pivot choice matters |
| Heap | O(n log n) | O(n log n) | O(n log n) | O(1) | ❌ | Guaranteed; not cache-friendly |
| Counting | O(n+k) | O(n+k) | O(n+k) | O(k) | ✅ | Only for integer values in [0,k] |
| Radix | O(d·n) | O(d·n) | O(d·n) | O(n+k) | ✅ | d = number of digits |

**Java `Arrays.sort`**: Dual-Pivot Quicksort for primitives; TimSort (merge+insertion hybrid) for objects. `Collections.sort` is also TimSort.

---

## 6. Matrix Patterns

### Spiral Traversal
```java
// Shrink boundaries: top, bottom, left, right
int top=0, bottom=m-1, left=0, right=n-1;
while (top<=bottom && left<=right) {
    for (int c=left;  c<=right;  c++) res.add(mat[top][c]);  top++;
    for (int r=top;   r<=bottom; r++) res.add(mat[r][right]); right--;
    if (top<=bottom)  for(int c=right;c>=left;c--)  res.add(mat[bottom][c]); bottom--;
    if (left<=right)  for(int r=bottom;r>=top;r--)  res.add(mat[r][left]);   left++;
}
```

### Rotate 90° Clockwise (in-place)
```
Step 1: Transpose  (swap mat[i][j] ↔ mat[j][i])
Step 2: Reverse each row
```

---

## 7. Advanced Techniques

### 7.1 Boyer-Moore Voting — Majority Element (> n/2)

```java
int candidate = nums[0], count = 1;
for (int i = 1; i < nums.length; i++) {
    if (count == 0) { candidate = nums[i]; count = 1; }
    else if (nums[i] == candidate) count++;
    else count--;
}
// candidate is the majority if one exists — verify with a second pass if needed
```

### 7.2 Floyd's Cycle Detection — Duplicate in [1..n]

Array treated as a linked list: index → value → next index.

```java
int slow = nums[0], fast = nums[0];
do { slow = nums[slow]; fast = nums[nums[fast]]; } while (slow != fast);
slow = nums[0];
while (slow != fast) { slow = nums[slow]; fast = nums[fast]; }
return slow; // duplicate
```

### 7.3 Monotonic Stack — Next Greater Element

```java
int[] nextGreater(int[] nums) {
    int n = nums.length;
    int[] res = new int[n]; Arrays.fill(res, -1);
    Deque<Integer> stack = new ArrayDeque<>();  // stores indices
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && nums[i] > nums[stack.peek()])
            res[stack.pop()] = nums[i];
        stack.push(i);
    }
    return res;
}
```

### 7.4 Binary Indexed Tree (Fenwick Tree) — O(log n) prefix sum + update

```java
class BIT {
    int[] bit;
    BIT(int n) { bit = new int[n+1]; }
    void update(int i, int d) { for (i++; i<bit.length; i+=i&-i) bit[i]+=d; }
    int query(int i) { int s=0; for (i++; i>0; i-=i&-i) s+=bit[i]; return s; }
    int rangeQuery(int l, int r) { return query(r) - (l>0 ? query(l-1) : 0); }
}
```

### 7.5 Segment Tree — Range Query + Range Update

```java
// See segment tree pattern for sum/min/max with lazy propagation
// Build: O(n), query: O(log n), update: O(log n)
```

---

## 8. Complexity Mental Model

```
Input n:   10^8     10^6      10^4     500      20      12
Algorithm: O(n)  O(n log n)  O(n²)   O(n³)   O(2^n)  O(n!)
Time (≈1s): ✅       ✅        ✅       ✅      ✅      ✅
```

---

## 9. Interview Decision Guide

| Problem Type | Go-To Pattern |
|---|---|
| Range sum queries, static | Prefix sum |
| Range update, static queries | Difference array |
| Range queries + point updates | Fenwick tree (BIT) |
| Range queries + range updates | Segment tree with lazy propagation |
| Maximum subarray | Kadane's algorithm |
| 3-way partition | Dutch National Flag |
| Majority element (> n/2) | Boyer-Moore voting |
| Duplicate in [1..n] | Floyd's cycle detection |
| Next greater/smaller element | Monotonic stack |
| Pair/triplet with target | Two pointers (sort first) |

---

## 10. Common Pitfalls

- **Overflow**: use `lo + (hi-lo)/2` not `(lo+hi)/2`; use `long` for products
- **Off-by-one prefix**: `prefix[r+1] - prefix[l]`, array size `n+1`
- **Kadane with all negatives**: initialise to `nums[0]`, not `0`
- **Dutch flag — don't advance mid after swapping with hi**
- **Difference array**: always size `n+1` to allow `diff[r+1]` when `r = n-1`
- **In-place vs extra space**: read constraints — many FAANG problems require O(1) extra
