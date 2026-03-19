package dsa.twopointers;

import java.util.*;

/**
 * Two-pointer patterns: opposite ends, same direction (fast/slow).
 */
public class TwoPointerPatterns {

    // ─── 1. SORTED TWO SUM ───────────────────────────────────────────────────────

    static int[] twoSumSorted(int[] nums, int target) {
        int l = 0, r = nums.length - 1;
        while (l < r) {
            int sum = nums[l] + nums[r];
            if (sum == target) return new int[]{l + 1, r + 1};
            else if (sum < target) l++;
            else r--;
        }
        return new int[]{};
    }

    // ─── 2. THREE SUM ────────────────────────────────────────────────────────────

    static List<List<Integer>> threeSum(int[] nums) {
        Arrays.sort(nums);
        List<List<Integer>> result = new ArrayList<>();
        for (int i = 0; i < nums.length - 2; i++) {
            if (i > 0 && nums[i] == nums[i - 1]) continue;
            int l = i + 1, r = nums.length - 1;
            while (l < r) {
                int sum = nums[i] + nums[l] + nums[r];
                if (sum == 0) {
                    result.add(List.of(nums[i], nums[l], nums[r]));
                    while (l < r && nums[l] == nums[l + 1]) l++;
                    while (l < r && nums[r] == nums[r - 1]) r--;
                    l++; r--;
                } else if (sum < 0) l++;
                else r--;
            }
        }
        return result;
    }

    // ─── 3. CONTAINER WITH MOST WATER ────────────────────────────────────────────

    static int maxArea(int[] height) {
        int l = 0, r = height.length - 1, max = 0;
        while (l < r) {
            max = Math.max(max, Math.min(height[l], height[r]) * (r - l));
            if (height[l] < height[r]) l++;
            else r--;
        }
        return max;
    }

    // ─── 4. TRAPPING RAIN WATER ──────────────────────────────────────────────────

    static int trap(int[] height) {
        int l = 0, r = height.length - 1, leftMax = 0, rightMax = 0, water = 0;
        while (l < r) {
            if (height[l] < height[r]) {
                if (height[l] >= leftMax) leftMax = height[l];
                else water += leftMax - height[l];
                l++;
            } else {
                if (height[r] >= rightMax) rightMax = height[r];
                else water += rightMax - height[r];
                r--;
            }
        }
        return water;
    }

    // ─── 5. FAST/SLOW POINTERS ───────────────────────────────────────────────────

    // Remove duplicates from sorted array in-place
    static int removeDuplicates(int[] nums) {
        int slow = 1;
        for (int fast = 1; fast < nums.length; fast++) {
            if (nums[fast] != nums[fast - 1]) nums[slow++] = nums[fast];
        }
        return slow;
    }

    // Partition: move all zeros to end, preserve relative order of non-zeros
    static void moveZeroes(int[] nums) {
        int slow = 0;
        for (int fast = 0; fast < nums.length; fast++) {
            if (nums[fast] != 0) nums[slow++] = nums[fast];
        }
        while (slow < nums.length) nums[slow++] = 0;
    }

    // ─── 6. PALINDROME ───────────────────────────────────────────────────────────

    static boolean isPalindrome(String s) {
        int l = 0, r = s.length() - 1;
        while (l < r) {
            while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
            while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
            if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r))) return false;
            l++; r--;
        }
        return true;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("Two sum sorted: " + Arrays.toString(twoSumSorted(new int[]{2,7,11,15}, 9))); // [1,2]

        System.out.println("Three sum: " + threeSum(new int[]{-1,0,1,2,-1,-4}));
        // [[-1,-1,2],[-1,0,1]]

        System.out.println("Max area: " + maxArea(new int[]{1,8,6,2,5,4,8,3,7})); // 49

        System.out.println("Trap water: " + trap(new int[]{0,1,0,2,1,0,1,3,2,1,2,1})); // 6

        int[] arr = {0, 1, 0, 3, 12};
        moveZeroes(arr);
        System.out.println("Move zeroes: " + Arrays.toString(arr)); // [1,3,12,0,0]

        System.out.println("Is palindrome: " + isPalindrome("A man, a plan, a canal: Panama")); // true
    }
}
