# Stack & Monotonic Stack — Complete Theory (Basic → Advanced)

---

## 1. What Is a Stack?

A stack is a **Last-In, First-Out (LIFO)** data structure. Elements are pushed onto the top and popped from the top.

```
Push 3 → [3]
Push 1 → [3, 1]
Push 4 → [3, 1, 4]
Pop    → [3, 1]  returns 4
Peek   → 1 (top, no removal)
```

**Java API** (`Deque<> as stack` — preferred over legacy `Stack<>`):
```java
Deque<Integer> stack = new ArrayDeque<>();
stack.push(x);          // push
stack.pop();            // pop (throws if empty)
stack.peek();           // top (throws if empty)
stack.isEmpty();        // check
```

**Core operations:** O(1) push, pop, peek.

**When to reach for a stack:**
- Matching opening/closing delimiters
- "Undo" or backtracking scenarios
- Iterative simulation of recursion
- Next-greater / next-smaller element problems
- Expression evaluation and parsing

---

## 2. Classic Stack Problems

### Valid Parentheses
```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(' || c == '[' || c == '{') stack.push(c);
        else {
            if (stack.isEmpty()) return false;
            char top = stack.pop();
            if (c == ')' && top != '(') return false;
            if (c == ']' && top != '[') return false;
            if (c == '}' && top != '{') return false;
        }
    }
    return stack.isEmpty();
}
```

### Evaluate Reverse Polish Notation
```java
public int evalRPN(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String t : tokens) {
        if ("+-*/".contains(t)) {
            int b = stack.pop(), a = stack.pop();
            switch (t) {
                case "+": stack.push(a + b); break;
                case "-": stack.push(a - b); break;
                case "*": stack.push(a * b); break;
                case "/": stack.push(a / b); break;
            }
        } else stack.push(Integer.parseInt(t));
    }
    return stack.pop();
}
```

### Min Stack — O(1) getMin()
```java
class MinStack {
    Deque<int[]> stack = new ArrayDeque<>();  // [val, currentMin]
    public void push(int val) {
        int min = stack.isEmpty() ? val : Math.min(val, stack.peek()[1]);
        stack.push(new int[]{val, min});
    }
    public void pop() { stack.pop(); }
    public int top() { return stack.peek()[0]; }
    public int getMin() { return stack.peek()[1]; }
}
```

---

## 3. Recursive → Iterative with Stack

Recursive DFS can always be converted to iterative using an explicit stack.

```java
// Iterative preorder traversal (recursive implicit stack → explicit)
public List<Integer> preorder(TreeNode root) {
    List<Integer> res = new ArrayList<>();
    if (root == null) return res;
    Deque<TreeNode> stack = new ArrayDeque<>();
    stack.push(root);
    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        res.add(node.val);
        if (node.right != null) stack.push(node.right);  // right first (LIFO)
        if (node.left  != null) stack.push(node.left);
    }
    return res;
}
```

---

## 4. Monotonic Stack — Core Concept

A **monotonic stack** maintains elements in strictly increasing or decreasing order (from bottom to top). When a new element violates the order, we **pop** until the invariant is restored.

```
Monotonic Increasing Stack (bottom → top: small to large):
Push 3 → [3]
Push 1 → pop 3 (3 > 1), push 1 → [1]
Push 4 → push 4 (4 > 1) → [1, 4]
Push 2 → pop 4 (4 > 2), push 2 → [1, 2]
```

**Key property**: when we pop element `x` because of new element `y`:
- In an **increasing** stack: `y` is the **next smaller** element for `x`
- In a **decreasing** stack: `y` is the **next greater** element for `x`

---

## 5. Next Greater Element

```java
// For each element, find the first element to the right that is greater
public int[] nextGreaterElement(int[] nums) {
    int n = nums.length;
    int[] res = new int[n];
    Arrays.fill(res, -1);
    Deque<Integer> stack = new ArrayDeque<>();  // indices
    for (int i = 0; i < n; i++) {
        // pop all elements smaller than nums[i]
        while (!stack.isEmpty() && nums[stack.peek()] < nums[i])
            res[stack.pop()] = nums[i];
        stack.push(i);
    }
    return res;
}
```

**Circular Array (LeetCode 503)** — iterate twice (i % n):
```java
for (int i = 0; i < 2 * n; i++) {
    while (!stack.isEmpty() && nums[stack.peek()] < nums[i % n])
        res[stack.pop()] = nums[i % n];
    if (i < n) stack.push(i);
}
```

---

## 6. Daily Temperatures

```java
// How many days until a warmer temperature?
public int[] dailyTemperatures(int[] temps) {
    int n = temps.length;
    int[] res = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();  // indices, decreasing temps
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && temps[stack.peek()] < temps[i]) {
            int j = stack.pop();
            res[j] = i - j;
        }
        stack.push(i);
    }
    return res;
}
```

---

## 7. Largest Rectangle in Histogram

```
heights = [2, 1, 5, 6, 2, 3]

For each bar, the max rectangle using it is:
width = distance to the previous smaller bar on left + right

Use monotonic increasing stack to find those boundaries.
```

```java
public int largestRectangleArea(int[] heights) {
    Deque<Integer> stack = new ArrayDeque<>();  // indices, increasing heights
    int max = 0;
    for (int i = 0; i <= heights.length; i++) {
        int h = (i == heights.length) ? 0 : heights[i];
        while (!stack.isEmpty() && heights[stack.peek()] > h) {
            int height = heights[stack.pop()];
            int width = stack.isEmpty() ? i : i - stack.peek() - 1;
            max = Math.max(max, height * width);
        }
        stack.push(i);
    }
    return max;
}
```

### Maximal Rectangle in Binary Matrix
Reduce each row to histogram heights, then apply largest-rectangle-in-histogram:
```java
public int maximalRectangle(char[][] matrix) {
    int[] heights = new int[matrix[0].length];
    int max = 0;
    for (char[] row : matrix) {
        for (int j = 0; j < row.length; j++)
            heights[j] = row[j] == '1' ? heights[j] + 1 : 0;
        max = Math.max(max, largestRectangleArea(heights));
    }
    return max;
}
```

---

## 8. Stock Span Problem

```java
// Span[i] = number of consecutive days before i where price was <= price[i]
public int[] calculateSpan(int[] prices) {
    int n = prices.length;
    int[] span = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();  // indices, decreasing prices
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && prices[stack.peek()] <= prices[i])
            stack.pop();
        span[i] = stack.isEmpty() ? i + 1 : i - stack.peek();
        stack.push(i);
    }
    return span;
}
```

---

## 9. Sum of Subarray Minimums / Maximums

```
For each element, find:
  - left[i]  = distance to previous smaller element (left boundary)
  - right[i] = distance to next smaller or equal element (right boundary)

contribution = nums[i] * left[i] * right[i]
```

```java
public int sumSubarrayMins(int[] arr) {
    int n = arr.length, MOD = 1_000_000_007;
    int[] left = new int[n], right = new int[n];
    Deque<Integer> stack = new ArrayDeque<>();

    // left[i]: distance to previous smaller (exclusive)
    for (int i = 0; i < n; i++) {
        while (!stack.isEmpty() && arr[stack.peek()] >= arr[i]) stack.pop();
        left[i] = stack.isEmpty() ? i + 1 : i - stack.peek();
        stack.push(i);
    }
    stack.clear();

    // right[i]: distance to next smaller or equal
    for (int i = n-1; i >= 0; i--) {
        while (!stack.isEmpty() && arr[stack.peek()] > arr[i]) stack.pop();
        right[i] = stack.isEmpty() ? n - i : stack.peek() - i;
        stack.push(i);
    }

    long ans = 0;
    for (int i = 0; i < n; i++) ans = (ans + (long)arr[i] * left[i] * right[i]) % MOD;
    return (int) ans;
}
```

---

## 10. Remove Duplicate Letters / Lexicographically Smallest

```java
// Remove duplicates, keep lexicographically smallest subsequence
public String removeDuplicateLetters(String s) {
    int[] count = new int[26];
    boolean[] inStack = new boolean[26];
    for (char c : s.toCharArray()) count[c-'a']++;
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        count[c-'a']--;
        if (inStack[c-'a']) continue;
        while (!stack.isEmpty() && c < stack.peek() && count[stack.peek()-'a'] > 0) {
            inStack[stack.pop()-'a'] = false;
        }
        stack.push(c);
        inStack[c-'a'] = true;
    }
    StringBuilder sb = new StringBuilder();
    for (char c : stack) sb.append(c);
    return sb.reverse().toString();
}
```

---

## 11. Decode String (Stack-Based Parsing)

```java
// "3[a2[c]]" → "accaccacc"
public String decodeString(String s) {
    Deque<Integer> counts = new ArrayDeque<>();
    Deque<StringBuilder> strings = new ArrayDeque<>();
    StringBuilder cur = new StringBuilder();
    int k = 0;
    for (char c : s.toCharArray()) {
        if (Character.isDigit(c)) k = k * 10 + (c - '0');
        else if (c == '[') { counts.push(k); strings.push(cur); cur = new StringBuilder(); k = 0; }
        else if (c == ']') {
            int times = counts.pop();
            StringBuilder rep = new StringBuilder();
            while (times-- > 0) rep.append(cur);
            cur = strings.pop().append(rep);
        } else cur.append(c);
    }
    return cur.toString();
}
```

---

## 12. Complexity Reference

| Operation | Time | Space |
|---|---|---|
| Push / Pop / Peek | O(1) | — |
| Next greater (array of n) | O(n) | O(n) |
| Largest rectangle | O(n) | O(n) |
| Sum subarray mins | O(n) | O(n) |

Each element is pushed and popped at most once → loop + stack = **O(n)** total.

---

## 13. Decision Guide

| Signal | Pattern |
|---|---|
| Match brackets / delimiters | Plain stack |
| Simulate recursion iteratively | Explicit stack DFS |
| Next greater / smaller | Monotonic decreasing / increasing |
| Temperature wait days | Monotonic decreasing stack |
| Histogram rectangle | Monotonic increasing stack |
| Contribution of each element as min/max | Monotonic + left/right boundary |
| Lexicographically smallest after removals | Greedy + monotone stack |

---

## 14. Common Pitfalls

- **Legacy `Stack<>` class is slow** — use `ArrayDeque<>` instead
- **Pop on empty**: always check `!stack.isEmpty()` before popping
- **Index vs value in stack**: monotonic stack usually stores **indices**, not values (you need the position for width/distance calculations)
- **Circular next-greater**: iterate `2*n` times, only push when `i < n`
- **Tie-breaking in left/right boundaries** (sum of mins): one side uses `<`, the other `<=` to avoid double counting
