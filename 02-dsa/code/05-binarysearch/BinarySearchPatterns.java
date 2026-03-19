package dsa.binarysearch;

import java.util.Arrays;

/**
 * Binary search patterns: classic, first/last occurrence,
 * rotated sorted array, and search on answer space.
 */
public class BinarySearchPatterns {

    // ─── 1. CLASSIC ──────────────────────────────────────────────────────────────

    static int binarySearch(int[] nums, int target) {
        int l = 0, r = nums.length - 1;
        while (l <= r) {
            int mid = l + (r - l) / 2;
            if (nums[mid] == target) return mid;
            else if (nums[mid] < target) l = mid + 1;
            else r = mid - 1;
        }
        return -1;
    }

    static int firstOccurrence(int[] nums, int target) {
        int l = 0, r = nums.length - 1, result = -1;
        while (l <= r) {
            int mid = l + (r - l) / 2;
            if (nums[mid] == target) { result = mid; r = mid - 1; }
            else if (nums[mid] < target) l = mid + 1;
            else r = mid - 1;
        }
        return result;
    }

    static int lastOccurrence(int[] nums, int target) {
        int l = 0, r = nums.length - 1, result = -1;
        while (l <= r) {
            int mid = l + (r - l) / 2;
            if (nums[mid] == target) { result = mid; l = mid + 1; }
            else if (nums[mid] < target) l = mid + 1;
            else r = mid - 1;
        }
        return result;
    }

    // ─── 2. ROTATED SORTED ARRAY ─────────────────────────────────────────────────

    // LeetCode 153 — Find Minimum in Rotated Sorted Array
    static int findMin(int[] nums) {
        int l = 0, r = nums.length - 1;
        while (l < r) {
            int mid = l + (r - l) / 2;
            if (nums[mid] > nums[r]) l = mid + 1;
            else r = mid;
        }
        return nums[l];
    }

    // LeetCode 33 — Search in Rotated Sorted Array
    static int searchRotated(int[] nums, int target) {
        int l = 0, r = nums.length - 1;
        while (l <= r) {
            int mid = l + (r - l) / 2;
            if (nums[mid] == target) return mid;
            if (nums[l] <= nums[mid]) {             // left half sorted
                if (nums[l] <= target && target < nums[mid]) r = mid - 1;
                else l = mid + 1;
            } else {                                 // right half sorted
                if (nums[mid] < target && target <= nums[r]) l = mid + 1;
                else r = mid - 1;
            }
        }
        return -1;
    }

    // ─── 3. SEARCH ON ANSWER SPACE ──────────────────────────────────────────────

    // LeetCode 875 — Koko Eating Bananas
    static int minEatingSpeed(int[] piles, int h) {
        int lo = 1, hi = Arrays.stream(piles).max().getAsInt();
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (canFinish(piles, mid, h)) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    private static boolean canFinish(int[] piles, int speed, int h) {
        long hours = 0;
        for (int pile : piles) hours += (pile + speed - 1) / speed;
        return hours <= h;
    }

    // LeetCode 1011 — Capacity to Ship Packages Within D Days
    static int shipWithinDays(int[] weights, int days) {
        int lo = Arrays.stream(weights).max().getAsInt(); // must carry heaviest
        int hi = Arrays.stream(weights).sum();            // ship all in 1 day
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (canShip(weights, mid, days)) hi = mid;
            else lo = mid + 1;
        }
        return lo;
    }

    private static boolean canShip(int[] weights, int cap, int days) {
        int daysNeeded = 1, currentLoad = 0;
        for (int w : weights) {
            if (currentLoad + w > cap) { daysNeeded++; currentLoad = 0; }
            currentLoad += w;
        }
        return daysNeeded <= days;
    }

    // LeetCode 74 — Search a 2D Matrix
    static boolean searchMatrix(int[][] matrix, int target) {
        int m = matrix.length, n = matrix[0].length;
        int l = 0, r = m * n - 1;
        while (l <= r) {
            int mid = l + (r - l) / 2;
            int val = matrix[mid / n][mid % n];
            if (val == target) return true;
            else if (val < target) l = mid + 1;
            else r = mid - 1;
        }
        return false;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int[] sorted = {1, 3, 5, 7, 9, 11, 13};
        System.out.println("Binary search 7: " + binarySearch(sorted, 7));   // 3
        System.out.println("Binary search 6: " + binarySearch(sorted, 6));   // -1

        int[] dups = {1, 2, 2, 2, 3, 4};
        System.out.println("First 2: " + firstOccurrence(dups, 2)); // 1
        System.out.println("Last 2: " + lastOccurrence(dups, 2));   // 3

        int[] rotated = {4, 5, 6, 7, 0, 1, 2};
        System.out.println("Min in rotated: " + findMin(rotated));          // 0
        System.out.println("Search 0 in rotated: " + searchRotated(rotated, 0)); // 4

        System.out.println("Min eating speed: " + minEatingSpeed(new int[]{3,6,7,11}, 8));  // 4
        System.out.println("Ship capacity: " + shipWithinDays(new int[]{1,2,3,4,5,6,7,8,9,10}, 5)); // 15

        int[][] matrix = {{1,3,5,7},{10,11,16,20},{23,30,34,60}};
        System.out.println("Search matrix 3: " + searchMatrix(matrix, 3)); // true
        System.out.println("Search matrix 13: " + searchMatrix(matrix, 13)); // false
    }
}
