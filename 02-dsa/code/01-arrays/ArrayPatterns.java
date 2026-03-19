package dsa.arrays;

import java.util.*;

/**
 * Core array patterns for FAANG interviews.
 * Covers: prefix sum, frequency counting, subarray problems, in-place transforms.
 */
public class ArrayPatterns {

    // ─── 1. PREFIX SUM ──────────────────────────────────────────────────────────

    static int[] buildPrefix(int[] a) {
        int[] p = new int[a.length + 1];
        for (int i = 0; i < a.length; i++) p[i + 1] = p[i] + a[i];
        return p;
    }

    static int rangeSum(int[] prefix, int l, int r) {
        return prefix[r + 1] - prefix[l];
    }

    // LeetCode 560 — Subarray Sum Equals K
    static int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> prefixCount = new HashMap<>();
        prefixCount.put(0, 1);
        int count = 0, sum = 0;
        for (int num : nums) {
            sum += num;
            count += prefixCount.getOrDefault(sum - k, 0);
            prefixCount.merge(sum, 1, Integer::sum);
        }
        return count;
    }

    // ─── 2. FREQUENCY COUNT ──────────────────────────────────────────────────────

    // LeetCode 242 — Valid Anagram
    static boolean isAnagram(String s, String t) {
        if (s.length() != t.length()) return false;
        int[] freq = new int[26];
        for (char c : s.toCharArray()) freq[c - 'a']++;
        for (char c : t.toCharArray()) if (--freq[c - 'a'] < 0) return false;
        return true;
    }

    // LeetCode 49 — Group Anagrams
    static List<List<String>> groupAnagrams(String[] strs) {
        Map<String, List<String>> map = new HashMap<>();
        for (String s : strs) {
            char[] arr = s.toCharArray();
            Arrays.sort(arr);
            map.computeIfAbsent(new String(arr), k -> new ArrayList<>()).add(s);
        }
        return new ArrayList<>(map.values());
    }

    // ─── 3. IN-PLACE TRANSFORMS ──────────────────────────────────────────────────

    // LeetCode 189 — Rotate Array
    static void rotateRight(int[] nums, int k) {
        k %= nums.length;
        reverse(nums, 0, nums.length - 1);
        reverse(nums, 0, k - 1);
        reverse(nums, k, nums.length - 1);
    }

    private static void reverse(int[] a, int l, int r) {
        while (l < r) { int tmp = a[l]; a[l++] = a[r]; a[r--] = tmp; }
    }

    // LeetCode 73 — Set Matrix Zeroes (O(1) space)
    static void setZeroes(int[][] matrix) {
        int m = matrix.length, n = matrix[0].length;
        boolean firstRowZero = false, firstColZero = false;

        for (int j = 0; j < n; j++) if (matrix[0][j] == 0) firstRowZero = true;
        for (int i = 0; i < m; i++) if (matrix[i][0] == 0) firstColZero = true;

        for (int i = 1; i < m; i++)
            for (int j = 1; j < n; j++)
                if (matrix[i][j] == 0) { matrix[i][0] = 0; matrix[0][j] = 0; }

        for (int i = 1; i < m; i++)
            for (int j = 1; j < n; j++)
                if (matrix[i][0] == 0 || matrix[0][j] == 0) matrix[i][j] = 0;

        if (firstRowZero) Arrays.fill(matrix[0], 0);
        if (firstColZero) for (int i = 0; i < m; i++) matrix[i][0] = 0;
    }

    // ─── 4. CLASSIC PATTERNS ─────────────────────────────────────────────────────

    // LeetCode 238 — Product of Array Except Self
    static int[] productExceptSelf(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        result[0] = 1;
        for (int i = 1; i < n; i++) result[i] = result[i - 1] * nums[i - 1];
        int right = 1;
        for (int i = n - 1; i >= 0; i--) {
            result[i] *= right;
            right *= nums[i];
        }
        return result;
    }

    // LeetCode 169 — Majority Element (Boyer-Moore Voting)
    static int majorityElement(int[] nums) {
        int candidate = nums[0], count = 1;
        for (int i = 1; i < nums.length; i++) {
            if (count == 0) { candidate = nums[i]; count = 1; }
            else if (nums[i] == candidate) count++;
            else count--;
        }
        return candidate;
    }

    // LeetCode 1 — Two Sum
    static int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) return new int[]{map.get(complement), i};
            map.put(nums[i], i);
        }
        return new int[]{};
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Prefix sum
        int[] arr = {3, 1, 4, 1, 5, 9, 2};
        int[] prefix = buildPrefix(arr);
        System.out.println("Range sum [2..5]: " + rangeSum(prefix, 2, 5)); // 19

        // Subarray sum equals K
        System.out.println("Subarrays summing to 3: " + subarraySum(new int[]{1, 1, 1}, 2)); // 2

        // Anagram
        System.out.println("anagram/nagaram: " + isAnagram("anagram", "nagaram")); // true

        // Group anagrams
        System.out.println("Group anagrams: " + groupAnagrams(new String[]{"eat","tea","tan","ate","nat","bat"}));

        // Rotate
        int[] nums = {1, 2, 3, 4, 5, 6, 7};
        rotateRight(nums, 3);
        System.out.println("Rotated right 3: " + Arrays.toString(nums)); // [5,6,7,1,2,3,4]

        // Product except self
        System.out.println("Product except self: " + Arrays.toString(productExceptSelf(new int[]{1,2,3,4}))); // [24,12,8,6]

        // Majority element
        System.out.println("Majority element: " + majorityElement(new int[]{2, 2, 1, 1, 2})); // 2

        // Two sum
        System.out.println("Two sum: " + Arrays.toString(twoSum(new int[]{2, 7, 11, 15}, 9))); // [0,1]
    }
}
