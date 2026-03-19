# Dynamic Programming — Practice Questions

---

## 🟢 Easy (5)

### E1. Climbing Stairs
Count distinct ways to climb n steps (take 1 or 2 steps at a time).  
**Hint:** `dp[i] = dp[i-1] + dp[i-2]`; base cases dp[1]=1, dp[2]=2.  
**Complexity:** O(n) time, O(1) space (rolling variables).

```java
public int climbStairs(int n) {
    if (n <= 2) return n;
    int a = 1, b = 2;
    for (int i = 3; i <= n; i++) { int c = a + b; a = b; b = c; }
    return b;
}
```

### E2. House Robber
Rob houses in a row; no two adjacent houses. Maximize stolen amount.  
**Hint:** `dp[i] = max(dp[i-1], dp[i-2] + nums[i])`.  
**Complexity:** O(n) time, O(1) space.

```java
public int rob(int[] nums) {
    int prev = 0, cur = 0;
    for (int n : nums) { int next = Math.max(cur, prev + n); prev = cur; cur = next; }
    return cur;
}
```

### E3. Pascal's Triangle
Generate the first n rows of Pascal's Triangle.  
**Hint:** Each row starts/ends with 1; middle elements = sum of two elements above.  
**Complexity:** O(n²) time and space.

```java
public List<List<Integer>> generate(int numRows) {
    List<List<Integer>> res = new ArrayList<>();
    for (int i = 0; i < numRows; i++) {
        List<Integer> row = new ArrayList<>();
        for (int j = 0; j <= i; j++)
            row.add((j == 0 || j == i) ? 1 : res.get(i-1).get(j-1) + res.get(i-1).get(j));
        res.add(row);
    }
    return res;
}
```

### E4. Min Cost Climbing Stairs
Minimum cost to reach the top; can start at step 0 or 1, pay cost[i] when stepping on it.  
**Hint:** `dp[i] = cost[i] + min(dp[i-1], dp[i-2])`.  
**Complexity:** O(n) time, O(1) space.

```java
public int minCostClimbingStairs(int[] cost) {
    int a = cost[0], b = cost[1];
    for (int i = 2; i < cost.length; i++) { int c = cost[i] + Math.min(a, b); a = b; b = c; }
    return Math.min(a, b);
}
```

### E5. Counting Bits
For every number from 0 to n, return the count of 1-bits.  
**Hint:** `dp[i] = dp[i >> 1] + (i & 1)` — right shift and check LSB.  
**Complexity:** O(n) time, O(n) space.

```java
public int[] countBits(int n) {
    int[] dp = new int[n + 1];
    for (int i = 1; i <= n; i++) dp[i] = dp[i >> 1] + (i & 1);
    return dp;
}
```

---

## 🟡 Medium (10)

### M1. Longest Increasing Subsequence (LIS)
Find the length of the longest strictly increasing subsequence.  
**Hint:** O(n²) DP: `dp[i] = max(dp[j]+1) for j<i where nums[j]<nums[i]`. O(n log n) with patience sorting (binary search).  
**Complexity:** O(n log n) time with binary search.

```java
public int lengthOfLIS(int[] nums) {
    List<Integer> tails = new ArrayList<>();
    for (int n : nums) {
        int lo = 0, hi = tails.size();
        while (lo < hi) { int mid = (lo+hi)/2; if (tails.get(mid) < n) lo=mid+1; else hi=mid; }
        if (lo == tails.size()) tails.add(n); else tails.set(lo, n);
    }
    return tails.size();
}
```

### M2. Coin Change
Minimum number of coins needed to make `amount`.  
**Hint:** `dp[i] = min(dp[i], dp[i-coin]+1)` for each coin. Initialize dp with amount+1 (infinity).  
**Complexity:** O(amount * coins) time, O(amount) space.

```java
public int coinChange(int[] coins, int amount) {
    int[] dp = new int[amount + 1];
    Arrays.fill(dp, amount + 1);
    dp[0] = 0;
    for (int i = 1; i <= amount; i++)
        for (int c : coins) if (c <= i) dp[i] = Math.min(dp[i], dp[i-c] + 1);
    return dp[amount] > amount ? -1 : dp[amount];
}
```

### M3. Unique Paths
Count paths from top-left to bottom-right of an m×n grid (only right/down moves).  
**Hint:** `dp[i][j] = dp[i-1][j] + dp[i][j-1]`. Or use combinatorics: C(m+n-2, m-1).  
**Complexity:** O(m*n) time, O(n) space.

```java
public int uniquePaths(int m, int n) {
    int[] dp = new int[n]; Arrays.fill(dp, 1);
    for (int i = 1; i < m; i++) for (int j = 1; j < n; j++) dp[j] += dp[j-1];
    return dp[n-1];
}
```

### M4. Word Break
Determine if a string can be segmented into words from a dictionary.  
**Hint:** `dp[i]` = can form s[0..i-1] from wordDict. Check all `dp[j] && dict contains s[j..i]`.  
**Complexity:** O(n² * m) time where m = avg word length for substring checks.

```java
public boolean wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);
    boolean[] dp = new boolean[s.length() + 1]; dp[0] = true;
    for (int i = 1; i <= s.length(); i++)
        for (int j = 0; j < i; j++) if (dp[j] && dict.contains(s.substring(j, i))) { dp[i]=true; break; }
    return dp[s.length()];
}
```

### M5. Partition Equal Subset Sum
Determine if an array can be split into two subsets with equal sum.  
**Hint:** Target = totalSum/2. 0/1 knapsack boolean DP: `dp[j] |= dp[j-nums[i]]`.  
**Complexity:** O(n * sum/2) time, O(sum/2) space.

```java
public boolean canPartition(int[] nums) {
    int sum = 0; for (int n : nums) sum += n;
    if (sum % 2 != 0) return false;
    int target = sum / 2;
    boolean[] dp = new boolean[target + 1]; dp[0] = true;
    for (int n : nums) for (int j = target; j >= n; j--) dp[j] |= dp[j-n];
    return dp[target];
}
```

### M6. Longest Common Subsequence
Find the length of the LCS of two strings.  
**Hint:** `dp[i][j] = dp[i-1][j-1]+1` if chars match, else `max(dp[i-1][j], dp[i][j-1])`.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public int longestCommonSubsequence(String text1, String text2) {
    int m = text1.length(), n = text2.length();
    int[][] dp = new int[m+1][n+1];
    for (int i = 1; i <= m; i++) for (int j = 1; j <= n; j++)
        dp[i][j] = text1.charAt(i-1)==text2.charAt(j-1) ? dp[i-1][j-1]+1 : Math.max(dp[i-1][j], dp[i][j-1]);
    return dp[m][n];
}
```

### M7. Jump Game II
Find the minimum number of jumps to reach the last index.  
**Hint:** Greedy DP — track current reach and max reachable. Increment jumps when you exhaust current reach.  
**Complexity:** O(n) time, O(1) space.

```java
public int jump(int[] nums) {
    int jumps = 0, curEnd = 0, farthest = 0;
    for (int i = 0; i < nums.length - 1; i++) {
        farthest = Math.max(farthest, i + nums[i]);
        if (i == curEnd) { jumps++; curEnd = farthest; }
    }
    return jumps;
}
```

### M8. Decode Ways
Count number of ways to decode a numeric string into letters (A=1..Z=26).  
**Hint:** `dp[i]` = ways to decode s[0..i-1]. Add `dp[i-1]` if s[i-1] != '0'; add `dp[i-2]` if s[i-2..i-1] in [10..26].  
**Complexity:** O(n) time, O(1) space.

```java
public int numDecodings(String s) {
    int prev2 = 1, prev1 = s.charAt(0) == '0' ? 0 : 1;
    for (int i = 1; i < s.length(); i++) {
        int cur = 0;
        if (s.charAt(i) != '0') cur += prev1;
        int two = Integer.parseInt(s.substring(i-1, i+1));
        if (two >= 10 && two <= 26) cur += prev2;
        prev2 = prev1; prev1 = cur;
    }
    return prev1;
}
```

### M9. Maximum Product Subarray
Find the maximum product of a contiguous subarray.  
**Hint:** Track both max and min products (min can flip to max with a negative number).  
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

### M10. 0/1 Knapsack
Given weights and values of items and a capacity W, maximize value without exceeding capacity.  
**Hint:** `dp[j] = max(dp[j], dp[j-weight[i]] + value[i])` iterating items then capacity (inner loop descending).  
**Complexity:** O(n * W) time, O(W) space.

```java
public int knapsack(int W, int[] weight, int[] value) {
    int[] dp = new int[W + 1];
    for (int i = 0; i < weight.length; i++)
        for (int j = W; j >= weight[i]; j--)
            dp[j] = Math.max(dp[j], dp[j - weight[i]] + value[i]);
    return dp[W];
}
```

---

## 🔴 Hard (5)

### H1. Edit Distance
Find the minimum number of operations (insert, delete, replace) to convert one string to another.  
**Hint:** `dp[i][j]` = edit distance for s1[0..i] and s2[0..j]. If chars match: dp[i-1][j-1]; else 1 + min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1]).  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public int minDistance(String word1, String word2) {
    int m = word1.length(), n = word2.length();
    int[][] dp = new int[m+1][n+1];
    for (int i = 0; i <= m; i++) dp[i][0] = i;
    for (int j = 0; j <= n; j++) dp[0][j] = j;
    for (int i = 1; i <= m; i++) for (int j = 1; j <= n; j++)
        dp[i][j] = word1.charAt(i-1)==word2.charAt(j-1) ? dp[i-1][j-1]
                  : 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
    return dp[m][n];
}
```

### H2. Burst Balloons
Pop balloons to maximize coins; popping index i gives nums[left] * nums[i] * nums[right].  
**Hint:** Interval DP — think of the last balloon to pop in range [i, j]; `dp[i][j]` = max coins from that range.  
**Complexity:** O(n³) time, O(n²) space.

```java
public int maxCoins(int[] nums) {
    int n = nums.length;
    int[] balloons = new int[n + 2];
    balloons[0] = balloons[n+1] = 1;
    for (int i = 0; i < n; i++) balloons[i+1] = nums[i];
    int N = n + 2;
    int[][] dp = new int[N][N];
    for (int len = 2; len < N; len++) {
        for (int left = 0; left < N - len; left++) {
            int right = left + len;
            for (int k = left+1; k < right; k++)
                dp[left][right] = Math.max(dp[left][right], balloons[left]*balloons[k]*balloons[right] + dp[left][k] + dp[k][right]);
        }
    }
    return dp[0][N-1];
}
```

### H3. Palindrome Partitioning II
Minimum cuts to partition a string so every substring is a palindrome.  
**Hint:** Pre-compute isPalin[i][j] table. Then `dp[i] = min(dp[j-1]+1)` for all j where s[j..i] is palindrome.  
**Complexity:** O(n²) time, O(n²) space.

```java
public int minCut(String s) {
    int n = s.length();
    boolean[][] palin = new boolean[n][n];
    for (int i = n-1; i >= 0; i--) for (int j = i; j < n; j++)
        palin[i][j] = s.charAt(i)==s.charAt(j) && (j-i<=2 || palin[i+1][j-1]);
    int[] dp = new int[n]; Arrays.fill(dp, Integer.MAX_VALUE);
    for (int i = 0; i < n; i++) {
        if (palin[0][i]) { dp[i] = 0; continue; }
        for (int j = 1; j <= i; j++) if (palin[j][i]) dp[i] = Math.min(dp[i], dp[j-1]+1);
    }
    return dp[n-1];
}
```

### H4. Russian Doll Envelopes
Max number of envelopes you can nest (both width and height must be strictly larger).  
**Hint:** Sort by width ascending, height descending (for ties). Then find LIS on heights using binary search.  
**Complexity:** O(n log n) time, O(n) space.

```java
public int maxEnvelopes(int[][] envelopes) {
    Arrays.sort(envelopes, (a, b) -> a[0]!=b[0] ? a[0]-b[0] : b[1]-a[1]);
    List<Integer> tails = new ArrayList<>();
    for (int[] e : envelopes) {
        int h = e[1], lo = 0, hi = tails.size();
        while (lo < hi) { int mid=(lo+hi)/2; if(tails.get(mid)<h) lo=mid+1; else hi=mid; }
        if (lo == tails.size()) tails.add(h); else tails.set(lo, h);
    }
    return tails.size();
}
```

### H5. Regular Expression Matching
Implement regex matching with `.` (any char) and `*` (zero or more of preceding).  
**Hint:** `dp[i][j]` = does s[0..i-1] match p[0..j-1]? Handle `*` by matching zero occurrences (dp[i][j-2]) or extending.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public boolean isMatch(String s, String p) {
    int m = s.length(), n = p.length();
    boolean[][] dp = new boolean[m+1][n+1]; dp[0][0] = true;
    for (int j = 2; j <= n; j++) if (p.charAt(j-1)=='*') dp[0][j] = dp[0][j-2];
    for (int i = 1; i <= m; i++) for (int j = 1; j <= n; j++) {
        char sc = s.charAt(i-1), pc = p.charAt(j-1);
        if (pc == '*') {
            dp[i][j] = dp[i][j-2]; // zero occurrences
            char prev = p.charAt(j-2);
            if (prev == '.' || prev == sc) dp[i][j] |= dp[i-1][j];
        } else dp[i][j] = (pc=='.' || pc==sc) && dp[i-1][j-1];
    }
    return dp[m][n];
}
```
