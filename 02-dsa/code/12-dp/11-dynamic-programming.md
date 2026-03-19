# Dynamic Programming — Complete Theory (Basic → Advanced)

---

## 1. What Is Dynamic Programming?

Dynamic Programming (DP) solves problems by breaking them into **overlapping subproblems**, storing results to avoid recomputation. It applies when a problem has:

1. **Optimal substructure**: optimal solution contains optimal solutions to subproblems
2. **Overlapping subproblems**: the same subproblem is solved multiple times in a naive recursion

```
Fibonacci (naive recursion):
fib(5) → fib(4) + fib(3)
              fib(3) is computed twice!

DP: memo[3] is stored after first computation → O(n) not O(2^n)
```

**Two approaches:**
- **Top-down (memoization)**: recursive + cache
- **Bottom-up (tabulation)**: iterative, fill dp table from base cases up

---

## 2. Memoization vs Tabulation

### Memoization (top-down)
```java
Map<Integer, Long> memo = new HashMap<>();
public long fib(int n) {
    if (n <= 1) return n;
    if (memo.containsKey(n)) return memo.get(n);
    long res = fib(n-1) + fib(n-2);
    memo.put(n, res);
    return res;
}
```

### Tabulation (bottom-up)
```java
public long fib(int n) {
    if (n <= 1) return n;
    long[] dp = new long[n+1];
    dp[0] = 0; dp[1] = 1;
    for (int i = 2; i <= n; i++) dp[i] = dp[i-1] + dp[i-2];
    return dp[n];
}

// Space-optimised (only last 2 values needed):
long a = 0, b = 1;
for (int i = 2; i <= n; i++) { long c = a + b; a = b; b = c; }
return b;
```

---

## 3. 1D DP — Linear Problems

### Climbing Stairs
```java
// dp[i] = ways to reach step i
// dp[i] = dp[i-1] + dp[i-2]
public int climbStairs(int n) {
    if (n <= 2) return n;
    int a = 1, b = 2;
    for (int i = 3; i <= n; i++) { int c = a + b; a = b; b = c; }
    return b;
}
```

### House Robber
```java
// dp[i] = max money robbing first i houses
// dp[i] = max(dp[i-1], dp[i-2] + nums[i])
public int rob(int[] nums) {
    int prev2 = 0, prev1 = 0;
    for (int n : nums) { int curr = Math.max(prev1, prev2 + n); prev2 = prev1; prev1 = curr; }
    return prev1;
}
```

### Longest Increasing Subsequence — O(n²)
```java
// dp[i] = LIS ending at index i
public int lengthOfLIS(int[] nums) {
    int n = nums.length;
    int[] dp = new int[n]; Arrays.fill(dp, 1);
    int ans = 1;
    for (int i = 1; i < n; i++) {
        for (int j = 0; j < i; j++)
            if (nums[j] < nums[i]) dp[i] = Math.max(dp[i], dp[j]+1);
        ans = Math.max(ans, dp[i]);
    }
    return ans;
}
```

### LIS — O(n log n) with Patience Sorting
```java
public int lengthOfLIS(int[] nums) {
    List<Integer> sub = new ArrayList<>();
    for (int n : nums) {
        int pos = Collections.binarySearch(sub, n);
        if (pos < 0) pos = -(pos + 1);   // insertion point
        if (pos == sub.size()) sub.add(n);
        else sub.set(pos, n);
    }
    return sub.size();
}
```

---

## 4. 2D DP

### Unique Paths
```java
// dp[i][j] = paths to reach (i,j) from (0,0)
// dp[i][j] = dp[i-1][j] + dp[i][j-1]
public int uniquePaths(int m, int n) {
    int[] dp = new int[n]; Arrays.fill(dp, 1);
    for (int i = 1; i < m; i++)
        for (int j = 1; j < n; j++) dp[j] += dp[j-1];
    return dp[n-1];
}
```

### Minimum Path Sum
```java
public int minPathSum(int[][] grid) {
    int m = grid.length, n = grid[0].length;
    int[] dp = new int[n];
    dp[0] = grid[0][0];
    for (int j = 1; j < n; j++) dp[j] = dp[j-1] + grid[0][j];
    for (int i = 1; i < m; i++) {
        dp[0] += grid[i][0];
        for (int j = 1; j < n; j++) dp[j] = Math.min(dp[j], dp[j-1]) + grid[i][j];
    }
    return dp[n-1];
}
```

---

## 5. Knapsack Variants

### 0/1 Knapsack
```
n items, each with weight w[i] and value v[i]. Bag capacity W.
Maximise value without exceeding capacity.
dp[i][c] = max value using first i items with capacity c
         = max(dp[i-1][c], dp[i-1][c-w[i]] + v[i])
```

```java
public int knapsack(int W, int[] weights, int[] values) {
    int[] dp = new int[W+1];
    for (int i = 0; i < weights.length; i++)
        for (int c = W; c >= weights[i]; c--)    // reverse to avoid reuse
            dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]);
    return dp[W];
}
```

### Unbounded Knapsack (items can be used multiple times)
```java
for (int i = 0; i < weights.length; i++)
    for (int c = weights[i]; c <= W; c++)    // forward to allow reuse
        dp[c] = Math.max(dp[c], dp[c - weights[i]] + values[i]);
```

### Subset Sum / Partition Equal Subset Sum
```java
// Can we partition array into two equal-sum subsets?
public boolean canPartition(int[] nums) {
    int total = Arrays.stream(nums).sum();
    if (total % 2 != 0) return false;
    int target = total / 2;
    boolean[] dp = new boolean[target+1];
    dp[0] = true;
    for (int n : nums)
        for (int c = target; c >= n; c--)
            dp[c] = dp[c] || dp[c-n];
    return dp[target];
}
```

### Coin Change — Minimum coins
```java
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount+1]; Arrays.fill(dp, amount+1); dp[0] = 0;
    for (int c : coins)
        for (int a = c; a <= amount; a++)
            dp[a] = Math.min(dp[a], dp[a-c] + 1);
    return dp[amount] > amount ? -1 : dp[amount];
}
```

### Coin Change II — Number of ways
```java
public int change(int amount, int[] coins) {
    int[] dp = new int[amount+1]; dp[0] = 1;
    for (int c : coins)
        for (int a = c; a <= amount; a++)
            dp[a] += dp[a-c];
    return dp[amount];
}
```

---

## 6. Interval DP

### Longest Palindromic Subsequence
```java
// dp[i][j] = LPS in s[i..j]
public int longestPalindromeSubseq(String s) {
    int n = s.length();
    int[][] dp = new int[n][n];
    for (int i = 0; i < n; i++) dp[i][i] = 1;
    for (int len = 2; len <= n; len++)
        for (int i = 0; i <= n-len; i++) {
            int j = i + len - 1;
            dp[i][j] = s.charAt(i) == s.charAt(j) ? 2 + dp[i+1][j-1] : Math.max(dp[i+1][j], dp[i][j-1]);
        }
    return dp[0][n-1];
}
```

### Minimum Cost to Cut a Stick / Burst Balloons
Both use interval DP: `dp[i][j]` represents the optimal answer for the interval `[i..j]`.

**Burst Balloons** — O(n³):
```java
public int maxCoins(int[] nums) {
    int n = nums.length + 2;
    int[] a = new int[n]; a[0] = a[n-1] = 1;
    for (int i = 1; i <= nums.length; i++) a[i] = nums[i-1];
    int[][] dp = new int[n][n];
    for (int len = 2; len < n; len++)
        for (int l = 0; l < n-len; l++) {
            int r = l + len;
            for (int k = l+1; k < r; k++)   // k is the LAST balloon burst in [l,r]
                dp[l][r] = Math.max(dp[l][r], a[l]*a[k]*a[r] + dp[l][k] + dp[k][r]);
        }
    return dp[0][n-1];
}
```

---

## 7. String DP

### Longest Common Subsequence
```java
public int lcs(String a, String b) {
    int m = a.length(), n = b.length();
    int[] dp = new int[n+1];
    for (int i = 1; i <= m; i++) {
        int prev = 0;
        for (int j = 1; j <= n; j++) {
            int tmp = dp[j];
            dp[j] = a.charAt(i-1) == b.charAt(j-1) ? prev+1 : Math.max(dp[j], dp[j-1]);
            prev = tmp;
        }
    }
    return dp[n];
}
```

### Edit Distance
```java
// dp[i][j] = min edits to convert a[0..i-1] to b[0..j-1]
public int minDistance(String a, String b) {
    int m = a.length(), n = b.length();
    int[] dp = new int[n+1];
    for (int j = 0; j <= n; j++) dp[j] = j;
    for (int i = 1; i <= m; i++) {
        int prev = dp[0]; dp[0] = i;
        for (int j = 1; j <= n; j++) {
            int tmp = dp[j];
            dp[j] = a.charAt(i-1) == b.charAt(j-1) ? prev : 1 + Math.min(prev, Math.min(dp[j], dp[j-1]));
            prev = tmp;
        }
    }
    return dp[n];
}
```

---

## 8. Decision DP (State Machine)

### Best Time to Buy and Sell Stock with Cooldown
```java
// States: held, sold (just sold), rest
public int maxProfit(int[] prices) {
    int held = Integer.MIN_VALUE, sold = 0, rest = 0;
    for (int p : prices) {
        int prevSold = sold;
        sold = held + p;                        // sell today
        held = Math.max(held, rest - p);         // buy today (or hold)
        rest = Math.max(rest, prevSold);         // rest or continue resting
    }
    return Math.max(sold, rest);
}
```

---

## 9. Bitmask DP

Used for subsets over small n (usually n ≤ 20). Each bitmask represents a subset.

### Traveling Salesman (TSP) — O(n² × 2^n)
```java
// dp[mask][i] = min cost to visit all nodes in mask, ending at i
int[][] dp = new int[1<<n][n];
// Initialize: dp[1<<i][i] = cost[src][i] for all i
// Transition: for each mask, for each unvisited city j:
//   dp[mask | (1<<j)][j] = min(dp[mask|1<<j][j], dp[mask][i] + dist[i][j])
```

### Minimum XOR Sum of Two Arrays
```java
// Both arrays of length n (n ≤ 14). Assign b[j] to a[i] to minimise XOR sum.
// dp[mask] = min sum when we've assigned j-th bits of mask to first popcount(mask) elements of a
int[] dp = new int[1<<n]; Arrays.fill(dp, Integer.MAX_VALUE); dp[0] = 0;
for (int mask = 0; mask < (1<<n); mask++) {
    int i = Integer.bitCount(mask);    // which a[i] to assign
    for (int j = 0; j < n; j++) {
        if ((mask & (1<<j)) == 0)      // j not yet assigned
            dp[mask|(1<<j)] = Math.min(dp[mask|(1<<j)], dp[mask] + (a[i] ^ b[j]));
    }
}
return dp[(1<<n)-1];
```

---

## 10. Digit DP

Count numbers in [l, r] that satisfy a digit constraint using DP on each digit's position.

**State**: `(position, tight, visited_zero, extra_state)`

```java
// Count numbers from 0 to n with no consecutive equal digits
int n; int[] digits; int[][] memo;
int solve(int pos, boolean tight, boolean started, int prevDigit) {
    if (pos == digits.length) return started ? 1 : 0;
    if (memo[pos][prevDigit] != -1 && !tight && started) return memo[pos][prevDigit];
    int limit = tight ? digits[pos] : 9, count = 0;
    for (int d = 0; d <= limit; d++) {
        if (started && d == prevDigit) continue;
        count += solve(pos+1, tight && d == limit, started || d!=0, started ? d : -1);
    }
    if (!tight && started) memo[pos][prevDigit] = count;
    return count;
}
```

---

## 11. Complexity Reference

| Problem | Time | Space |
|---|---|---|
| Fibonacci | O(n) | O(1) |
| LIS O(n²) | O(n²) | O(n) |
| LIS O(n log n) | O(n log n) | O(n) |
| Knapsack 0/1 | O(n × W) | O(W) |
| Coin Change | O(n × A) | O(A) |
| LCS / Edit Distance | O(m × n) | O(n) |
| Interval DP | O(n³) | O(n²) |
| Bitmask DP | O(n² × 2^n) | O(n × 2^n) |

---

## 12. Decision Guide

| Signal | DP Pattern |
|---|---|
| Max/min over subarray, no restart | 1D DP (Kadane variant) |
| Pick or skip each item | 0/1 Knapsack |
| Unlimited use of items | Unbounded Knapsack |
| Count distinct ways | Combination DP |
| 2D grid optimal path | 2D DP with row compression |
| Interval [i, j] → optimal split | Interval DP |
| Subset of n ≤ 20 | Bitmask DP |
| Count numbers with constraint | Digit DP |
| State machine (buy/sell/hold) | State transition DP |

---

## 13. Common Pitfalls

- **Off-by-one in dp array size**: `int[] dp = new int[n+1]` when using 1-indexed dp
- **0/1 vs unbounded knapsack loop direction**: reverse for 0/1, forward for unbounded
- **Coin change initialisation**: `Arrays.fill(dp, amount+1)` (not MAX_VALUE — prevents overflow when dp[a-c]+1 overflows)
- **Edit distance space optimisation**: track `prev` before overwriting `dp[j]`
- **Interval DP order**: iterate by length (outer=len, inner=start), not by start then end
- **Bitmask DP popcount trap**: `Integer.bitCount(mask)` gives correct position, but be careful about 0-indexed vs 1-indexed mapping
