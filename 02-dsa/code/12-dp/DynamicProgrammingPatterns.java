package dsa.dp;

import java.util.*;

/**
 * Dynamic programming patterns.
 * 1D DP, 2D DP, knapsack, LIS, LCS, edit distance, word break.
 */
public class DynamicProgrammingPatterns {

    // ─── 1. HOUSE ROBBER ─────────────────────────────────────────────────────────

    static int rob(int[] nums) {
        int rob1 = 0, rob2 = 0;
        for (int n : nums) {
            int cur = Math.max(n + rob1, rob2);
            rob1 = rob2; rob2 = cur;
        }
        return rob2;
    }

    // ─── 2. COIN CHANGE ──────────────────────────────────────────────────────────

    static int coinChange(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1);
        dp[0] = 0;
        for (int i = 1; i <= amount; i++)
            for (int coin : coins)
                if (coin <= i) dp[i] = Math.min(dp[i], 1 + dp[i - coin]);
        return dp[amount] > amount ? -1 : dp[amount];
    }

    // ─── 3. LIS (Longest Increasing Subsequence) ─────────────────────────────────

    static int lengthOfLIS(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        Arrays.fill(dp, 1);
        int max = 1;
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++)
                if (nums[j] < nums[i]) dp[i] = Math.max(dp[i], 1 + dp[j]);
            max = Math.max(max, dp[i]);
        }
        return max;
    }

    // O(n log n) using patience sorting (binary search)
    static int lisOptimal(int[] nums) {
        List<Integer> tails = new ArrayList<>();
        for (int num : nums) {
            int pos = Collections.binarySearch(tails, num);
            if (pos < 0) pos = -(pos + 1);
            if (pos == tails.size()) tails.add(num);
            else tails.set(pos, num);
        }
        return tails.size();
    }

    // ─── 4. LCS (Longest Common Subsequence) ─────────────────────────────────────

    static int longestCommonSubsequence(String text1, String text2) {
        int m = text1.length(), n = text2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i-1) == text2.charAt(j-1)) dp[i][j] = 1 + dp[i-1][j-1];
                else dp[i][j] = Math.max(dp[i-1][j], dp[i][j-1]);
            }
        return dp[m][n];
    }

    // ─── 5. EDIT DISTANCE ────────────────────────────────────────────────────────

    static int minDistance(String word1, String word2) {
        int m = word1.length(), n = word2.length();
        int[][] dp = new int[m+1][n+1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++)
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i-1) == word2.charAt(j-1)) dp[i][j] = dp[i-1][j-1];
                else dp[i][j] = 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
            }
        return dp[m][n];
    }

    // ─── 6. 0/1 KNAPSACK ─────────────────────────────────────────────────────────

    static int knapsack(int[] weights, int[] values, int capacity) {
        int[] dp = new int[capacity + 1];
        for (int i = 0; i < weights.length; i++)
            for (int w = capacity; w >= weights[i]; w--)  // reverse to prevent reuse
                dp[w] = Math.max(dp[w], values[i] + dp[w - weights[i]]);
        return dp[capacity];
    }

    // ─── 7. WORD BREAK ───────────────────────────────────────────────────────────

    static boolean wordBreak(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        boolean[] dp = new boolean[s.length() + 1];
        dp[0] = true;
        for (int i = 1; i <= s.length(); i++)
            for (int j = 0; j < i; j++)
                if (dp[j] && dict.contains(s.substring(j, i))) { dp[i] = true; break; }
        return dp[s.length()];
    }

    // ─── 8. UNIQUE PATHS WITH OBSTACLES ──────────────────────────────────────────

    static int uniquePathsWithObstacles(int[][] grid) {
        int m = grid.length, n = grid[0].length;
        if (grid[0][0] == 1) return 0;
        int[][] dp = new int[m][n];
        for (int i = 0; i < m && grid[i][0] == 0; i++) dp[i][0] = 1;
        for (int j = 0; j < n && grid[0][j] == 0; j++) dp[0][j] = 1;
        for (int i = 1; i < m; i++)
            for (int j = 1; j < n; j++)
                if (grid[i][j] == 0) dp[i][j] = dp[i-1][j] + dp[i][j-1];
        return dp[m-1][n-1];
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("House robber [1,2,3,1]: " + rob(new int[]{1,2,3,1})); // 4
        System.out.println("Coin change [1,5,11] 11: " + coinChange(new int[]{1,5,11}, 11)); // 1
        System.out.println("LIS [10,9,2,5,3,7,101,18]: " + lengthOfLIS(new int[]{10,9,2,5,3,7,101,18})); // 4
        System.out.println("LIS optimal: " + lisOptimal(new int[]{10,9,2,5,3,7,101,18})); // 4
        System.out.println("LCS (abcde, ace): " + longestCommonSubsequence("abcde", "ace")); // 3
        System.out.println("Edit distance (horse, ros): " + minDistance("horse", "ros")); // 3
        System.out.println("Knapsack cap=4: " + knapsack(new int[]{1,2,3}, new int[]{6,10,12}, 4)); // 16 (items 2+3)
        System.out.println("Word break: " + wordBreak("leetcode", List.of("leet","code"))); // true
        System.out.println("Unique paths: " + uniquePathsWithObstacles(new int[][]{{0,0,0},{0,1,0},{0,0,0}})); // 2
    }
}
