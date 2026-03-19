package dsa.stack;

import java.util.*;

/**
 * Stack & monotonic stack patterns.
 * Covers: valid parentheses, min stack, next greater, daily temps, histogram.
 */
public class StackPatterns {

    // ─── 1. VALID PARENTHESES ────────────────────────────────────────────────────

    static boolean isValid(String s) {
        Deque<Character> stack = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                stack.push(c);
            } else {
                if (stack.isEmpty()) return false;
                char top = stack.pop();
                if (c == ')' && top != '(') return false;
                if (c == ']' && top != '[') return false;
                if (c == '}' && top != '{') return false;
            }
        }
        return stack.isEmpty();
    }

    // ─── 2. MIN STACK ────────────────────────────────────────────────────────────

    static class MinStack {
        private final Deque<int[]> stack = new ArrayDeque<>(); // [value, currentMin]

        public void push(int val) {
            int min = stack.isEmpty() ? val : Math.min(val, stack.peek()[1]);
            stack.push(new int[]{val, min});
        }
        public void pop() { stack.pop(); }
        public int top() { return stack.peek()[0]; }
        public int getMin() { return stack.peek()[1]; }
    }

    // ─── 3. NEXT GREATER ELEMENT ─────────────────────────────────────────────────

    static int[] nextGreaterElement(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Deque<Integer> stack = new ArrayDeque<>(); // indices

        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && nums[stack.peek()] < nums[i]) {
                result[stack.pop()] = nums[i];
            }
            stack.push(i);
        }
        return result;
    }

    // ─── 4. DAILY TEMPERATURES ───────────────────────────────────────────────────

    static int[] dailyTemperatures(int[] temps) {
        int n = temps.length;
        int[] result = new int[n];
        Deque<Integer> stack = new ArrayDeque<>();

        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && temps[stack.peek()] < temps[i]) {
                int prev = stack.pop();
                result[prev] = i - prev;
            }
            stack.push(i);
        }
        return result;
    }

    // ─── 5. LARGEST RECTANGLE IN HISTOGRAM ──────────────────────────────────────

    static int largestRectangleArea(int[] heights) {
        int n = heights.length;
        Deque<Integer> stack = new ArrayDeque<>();
        int maxArea = 0;

        for (int i = 0; i <= n; i++) {
            int h = (i == n) ? 0 : heights[i];
            while (!stack.isEmpty() && heights[stack.peek()] > h) {
                int height = heights[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, height * width);
            }
            stack.push(i);
        }
        return maxArea;
    }

    // ─── 6. MAXIMAL RECTANGLE IN BINARY MATRIX ───────────────────────────────────

    static int maximalRectangle(char[][] matrix) {
        if (matrix.length == 0) return 0;
        int n = matrix[0].length;
        int[] heights = new int[n];
        int maxArea = 0;

        for (char[] row : matrix) {
            for (int j = 0; j < n; j++) {
                heights[j] = row[j] == '1' ? heights[j] + 1 : 0;
            }
            maxArea = Math.max(maxArea, largestRectangleArea(heights));
        }
        return maxArea;
    }

    // ─── 7. DECODE STRING (Stack) ────────────────────────────────────────────────

    // LeetCode 394 — e.g. "3[a2[c]]" → "accaccacc"
    static String decodeString(String s) {
        Deque<Integer> counts = new ArrayDeque<>();
        Deque<StringBuilder> strings = new ArrayDeque<>();
        StringBuilder current = new StringBuilder();
        int k = 0;

        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                k = k * 10 + (c - '0');
            } else if (c == '[') {
                counts.push(k);
                strings.push(current);
                current = new StringBuilder();
                k = 0;
            } else if (c == ']') {
                int repeat = counts.pop();
                StringBuilder parent = strings.pop();
                String segment = current.toString();
                for (int i = 0; i < repeat; i++) parent.append(segment);
                current = parent;
            } else {
                current.append(c);
            }
        }
        return current.toString();
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("Valid '()[]{}': " + isValid("()[]{}")); // true
        System.out.println("Valid '(]': " + isValid("(]"));          // false

        MinStack ms = new MinStack();
        ms.push(-2); ms.push(0); ms.push(-3);
        System.out.println("Min: " + ms.getMin()); // -3
        ms.pop();
        System.out.println("Top: " + ms.top());    // 0
        System.out.println("Min: " + ms.getMin()); // -2

        System.out.println("Next greater: " + Arrays.toString(nextGreaterElement(new int[]{2,1,2,4,3}))); // [4,2,4,-1,-1]

        System.out.println("Daily temps: " + Arrays.toString(dailyTemperatures(new int[]{73,74,75,71,69,72,76,73}))); // [1,1,4,2,1,1,0,0]

        System.out.println("Largest rect: " + largestRectangleArea(new int[]{2,1,5,6,2,3})); // 10

        char[][] matrix = {
            {'1','0','1','0','0'},
            {'1','0','1','1','1'},
            {'1','1','1','1','1'},
            {'1','0','0','1','0'}
        };
        System.out.println("Maximal rectangle: " + maximalRectangle(matrix)); // 6

        System.out.println("Decode '3[a2[c]]': " + decodeString("3[a2[c]]")); // accaccacc
    }
}
