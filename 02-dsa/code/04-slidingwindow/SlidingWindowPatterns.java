package dsa.slidingwindow;

import java.util.*;

/**
 * Sliding window patterns.
 * Fixed-size window, variable-size window, frequency-based window.
 */
public class SlidingWindowPatterns {

    // ─── 1. FIXED WINDOW ─────────────────────────────────────────────────────────

    // Max sum subarray of size k
    static int maxSumFixed(int[] nums, int k) {
        int windowSum = 0;
        for (int i = 0; i < k; i++) windowSum += nums[i];
        int maxSum = windowSum;
        for (int i = k; i < nums.length; i++) {
            windowSum += nums[i] - nums[i - k];
            maxSum = Math.max(maxSum, windowSum);
        }
        return maxSum;
    }

    // ─── 2. VARIABLE WINDOW ──────────────────────────────────────────────────────

    // LeetCode 3 — Longest Substring Without Repeating Characters
    static int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> lastSeen = new HashMap<>();
        int l = 0, max = 0;
        for (int r = 0; r < s.length(); r++) {
            char c = s.charAt(r);
            if (lastSeen.containsKey(c)) l = Math.max(l, lastSeen.get(c) + 1);
            lastSeen.put(c, r);
            max = Math.max(max, r - l + 1);
        }
        return max;
    }

    // LeetCode 159 — Longest substring with at most K distinct characters
    static int longestKDistinct(String s, int k) {
        Map<Character, Integer> freq = new HashMap<>();
        int l = 0, max = 0;
        for (int r = 0; r < s.length(); r++) {
            freq.merge(s.charAt(r), 1, Integer::sum);
            while (freq.size() > k) {
                char lc = s.charAt(l++);
                freq.merge(lc, -1, Integer::sum);
                if (freq.get(lc) == 0) freq.remove(lc);
            }
            max = Math.max(max, r - l + 1);
        }
        return max;
    }

    // LeetCode 424 — Longest Repeating Character Replacement
    static int characterReplacement(String s, int k) {
        int[] freq = new int[26];
        int l = 0, maxFreq = 0, maxLen = 0;
        for (int r = 0; r < s.length(); r++) {
            maxFreq = Math.max(maxFreq, ++freq[s.charAt(r) - 'A']);
            // window - maxFreq = chars to replace; if > k, shrink
            while ((r - l + 1) - maxFreq > k) {
                freq[s.charAt(l++) - 'A']--;
            }
            maxLen = Math.max(maxLen, r - l + 1);
        }
        return maxLen;
    }

    // ─── 3. MINIMUM WINDOW ───────────────────────────────────────────────────────

    // LeetCode 76 — Minimum Window Substring
    static String minWindow(String s, String t) {
        if (s.isEmpty() || t.isEmpty()) return "";
        Map<Character, Integer> need = new HashMap<>(), have = new HashMap<>();
        for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);

        int formed = 0, required = need.size();
        int l = 0, minLen = Integer.MAX_VALUE, minL = 0;

        for (int r = 0; r < s.length(); r++) {
            char c = s.charAt(r);
            have.merge(c, 1, Integer::sum);
            if (need.containsKey(c) && have.get(c).equals(need.get(c))) formed++;

            while (formed == required) {
                if (r - l + 1 < minLen) { minLen = r - l + 1; minL = l; }
                char lc = s.charAt(l);
                have.merge(lc, -1, Integer::sum);
                if (need.containsKey(lc) && have.get(lc) < need.get(lc)) formed--;
                l++;
            }
        }
        return minLen == Integer.MAX_VALUE ? "" : s.substring(minL, minL + minLen);
    }

    // ─── 4. MAXIMUM CONSECUTIVE ONES WITH FLIPS ──────────────────────────────────

    // LeetCode 1004 — Max Consecutive Ones III
    static int longestOnes(int[] nums, int k) {
        int l = 0, zeros = 0, max = 0;
        for (int r = 0; r < nums.length; r++) {
            if (nums[r] == 0) zeros++;
            while (zeros > k) if (nums[l++] == 0) zeros--;
            max = Math.max(max, r - l + 1);
        }
        return max;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("Max sum (k=3): " + maxSumFixed(new int[]{2,1,5,1,3,2}, 3)); // 9

        System.out.println("Longest no-repeat: " + lengthOfLongestSubstring("abcabcbb")); // 3
        System.out.println("Longest no-repeat: " + lengthOfLongestSubstring("pwwkew")); // 3

        System.out.println("Longest K=2 distinct: " + longestKDistinct("eceba", 2)); // 3 ("ece")

        System.out.println("Char replacement (k=2): " + characterReplacement("AABABBA", 1)); // 4

        System.out.println("Min window: " + minWindow("ADOBECODEBANC", "ABC")); // "BANC"

        System.out.println("Max ones III (k=2): " + longestOnes(new int[]{1,1,1,0,0,0,1,1,1,1,0}, 2)); // 6
    }
}
