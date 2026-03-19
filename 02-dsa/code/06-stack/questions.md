# Stack & Monotonic Stack — Practice Questions

---

## 🟢 Easy (5)

### E1. Valid Parentheses
Determine if the input string of brackets is valid (every open is closed in the right order).  
**Hint:** Push opens onto stack; on close, check if top matches.  
**Complexity:** O(n) time, O(n) space.

```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if      (c == '(' || c == '[' || c == '{') stack.push(c);
        else if (stack.isEmpty()) return false;
        else if (c == ')' && stack.pop() != '(') return false;
        else if (c == ']' && stack.pop() != '[') return false;
        else if (c == '}' && stack.pop() != '{') return false;
    }
    return stack.isEmpty();
}
```

### E2. Min Stack
Design a stack that supports push, pop, top, and retrieving the minimum in O(1).  
**Hint:** Use a second stack that tracks the current minimum at each state.  
**Complexity:** O(1) all ops, O(n) space.

```java
class MinStack {
    private Deque<Integer> stack = new ArrayDeque<>();
    private Deque<Integer> minStack = new ArrayDeque<>();
    public void push(int val) {
        stack.push(val);
        minStack.push(minStack.isEmpty() ? val : Math.min(val, minStack.peek()));
    }
    public void pop()    { stack.pop(); minStack.pop(); }
    public int top()     { return stack.peek(); }
    public int getMin()  { return minStack.peek(); }
}
```

### E3. Implement Queue Using Stacks
Implement a FIFO queue using only two stacks.  
**Hint:** Push to stack1; when popping, if stack2 is empty, pour all of stack1 into stack2.  
**Complexity:** Amortized O(1) per op.

```java
class MyQueue {
    private Deque<Integer> in = new ArrayDeque<>(), out = new ArrayDeque<>();
    public void push(int x) { in.push(x); }
    public int pop()  { move(); return out.pop(); }
    public int peek() { move(); return out.peek(); }
    public boolean empty() { return in.isEmpty() && out.isEmpty(); }
    private void move() { if (out.isEmpty()) while (!in.isEmpty()) out.push(in.pop()); }
}
```

### E4. Baseball Game
Simulate a baseball game with operations: integer, `+`, `D`, `C`. Return sum of scores.  
**Hint:** Use a stack; process each operation and push/pop accordingly.  
**Complexity:** O(n) time, O(n) space.

```java
public int calPoints(String[] operations) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String op : operations) {
        switch (op) {
            case "+" -> stack.push(stack.peek() + ((Deque<Integer>)(stack)).stream().skip(1).findFirst().orElse(0));
            case "D" -> stack.push(stack.peek() * 2);
            case "C" -> stack.pop();
            default  -> stack.push(Integer.parseInt(op));
        }
    }
    // simpler direct version:
    return stack.stream().mapToInt(Integer::intValue).sum();
}
```

### E5. Reverse a String Using Stack
Reverse a string using an explicit stack (without built-in reverse).  
**Hint:** Push all characters, then pop into result.  
**Complexity:** O(n) time, O(n) space.

```java
public String reverseString(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) stack.push(c);
    StringBuilder sb = new StringBuilder();
    while (!stack.isEmpty()) sb.append(stack.pop());
    return sb.toString();
}
```

---

## 🟡 Medium (10)

### M1. Daily Temperatures
For each day, find how many days until a warmer temperature.  
**Hint:** Monotonic decreasing stack of indices; pop when a warmer day is found.  
**Complexity:** O(n) time, O(n) space.

```java
public int[] dailyTemperatures(int[] temperatures) {
    int[] res = new int[temperatures.length];
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < temperatures.length; i++) {
        while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()])
            res[stack.peek()] = i - stack.pop();
        stack.push(i);
    }
    return res;
}
```

### M2. Evaluate Reverse Polish Notation
Evaluate an expression in RPN (postfix).  
**Hint:** Push numbers; on operator, pop two, compute, push result.  
**Complexity:** O(n) time, O(n) space.

```java
public int evalRPN(String[] tokens) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (String t : tokens) {
        switch (t) {
            case "+" -> stack.push(stack.pop() + stack.pop());
            case "*" -> stack.push(stack.pop() * stack.pop());
            case "-" -> { int b = stack.pop(); stack.push(stack.pop() - b); }
            case "/" -> { int b = stack.pop(); stack.push(stack.pop() / b); }
            default  -> stack.push(Integer.parseInt(t));
        }
    }
    return stack.pop();
}
```

### M3. Decode String
Decode strings like `3[a2[bc]]` → `abcbcabcbcabcbc`.  
**Hint:** Two stacks: one for counts, one for strings. Push on `[`, build result on `]`.  
**Complexity:** O(n * maxRepeat) time.

```java
public String decodeString(String s) {
    Deque<Integer> counts = new ArrayDeque<>();
    Deque<StringBuilder> strings = new ArrayDeque<>();
    StringBuilder cur = new StringBuilder();
    int k = 0;
    for (char c : s.toCharArray()) {
        if (Character.isDigit(c)) { k = k * 10 + (c - '0'); }
        else if (c == '[') { counts.push(k); k = 0; strings.push(cur); cur = new StringBuilder(); }
        else if (c == ']') { StringBuilder prev = strings.pop(); int rep = counts.pop(); prev.append(cur.toString().repeat(rep)); cur = prev; }
        else cur.append(c);
    }
    return cur.toString();
}
```

### M4. Next Greater Element II (Circular)
For a circular array, find the next greater element for each position.  
**Hint:** Traverse array twice (2n); monotonic stack of indices, take index mod n.  
**Complexity:** O(n) time, O(n) space.

```java
public int[] nextGreaterElements(int[] nums) {
    int n = nums.length;
    int[] res = new int[n];
    Arrays.fill(res, -1);
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < 2 * n; i++) {
        while (!stack.isEmpty() && nums[i % n] > nums[stack.peek()])
            res[stack.pop()] = nums[i % n];
        if (i < n) stack.push(i);
    }
    return res;
}
```

### M5. Asteroid Collision
Simulate asteroids moving; positive = right, negative = left. Same size destroys both.  
**Hint:** Stack; only conflict when top > 0 and current < 0.  
**Complexity:** O(n) time, O(n) space.

```java
public int[] asteroidCollision(int[] asteroids) {
    Deque<Integer> stack = new ArrayDeque<>();
    for (int a : asteroids) {
        boolean alive = true;
        while (alive && a < 0 && !stack.isEmpty() && stack.peek() > 0) {
            if (stack.peek() < -a) stack.pop();
            else if (stack.peek() == -a) { stack.pop(); alive = false; }
            else alive = false;
        }
        if (alive) stack.push(a);
    }
    int[] res = new int[stack.size()];
    for (int i = res.length - 1; i >= 0; i--) res[i] = stack.pop();
    return res;
}
```

### M6. Remove K Digits
Remove k digits to form the smallest possible number.  
**Hint:** Monotonic increasing stack; pop when current digit is smaller than top and k > 0.  
**Complexity:** O(n) time, O(n) space.

```java
public String removeKdigits(String num, int k) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : num.toCharArray()) {
        while (k > 0 && !stack.isEmpty() && stack.peek() > c) { stack.pop(); k--; }
        stack.push(c);
    }
    while (k-- > 0) stack.pop();
    StringBuilder sb = new StringBuilder();
    boolean leadZero = true;
    for (char c : stack.stream().collect(java.util.stream.Collectors.toList())) {
        if (leadZero && c == '0') continue;
        leadZero = false; sb.append(c);
    }
    return sb.isEmpty() ? "0" : sb.reverse().toString();
}
```

### M7. Simplify Path
Simplify a Unix-style file path.  
**Hint:** Split by `/`; push valid names, pop on `..`, ignore `.` and empty.  
**Complexity:** O(n) time, O(n) space.

```java
public String simplifyPath(String path) {
    Deque<String> stack = new ArrayDeque<>();
    for (String part : path.split("/")) {
        if (part.equals("..")) { if (!stack.isEmpty()) stack.pop(); }
        else if (!part.isEmpty() && !part.equals(".")) stack.push(part);
    }
    StringBuilder sb = new StringBuilder();
    for (String s : stack) sb.insert(0, "/" + s);
    return sb.isEmpty() ? "/" : sb.toString();
}
```

### M8. Online Stock Span
For each day's price, find how many consecutive previous prices were ≤ today's.  
**Hint:** Monotonic decreasing stack storing (price, span) pairs.  
**Complexity:** Amortized O(1) per push, O(n) space.

```java
class StockSpanner {
    private Deque<int[]> stack = new ArrayDeque<>(); // [price, span]
    public int next(int price) {
        int span = 1;
        while (!stack.isEmpty() && stack.peek()[0] <= price) span += stack.pop()[1];
        stack.push(new int[]{price, span});
        return span;
    }
}
```

### M9. Validate Stack Sequences
Given pushed and popped sequences, determine if they are valid stack operations.  
**Hint:** Simulate with a stack; push from pushed, pop when top matches next in popped.  
**Complexity:** O(n) time, O(n) space.

```java
public boolean validateStackSequences(int[] pushed, int[] popped) {
    Deque<Integer> stack = new ArrayDeque<>();
    int popIdx = 0;
    for (int val : pushed) {
        stack.push(val);
        while (!stack.isEmpty() && stack.peek() == popped[popIdx]) { stack.pop(); popIdx++; }
    }
    return stack.isEmpty();
}
```

### M10. 132 Pattern
Find if there exists indices i < j < k such that nums[i] < nums[k] < nums[j].  
**Hint:** Traverse right to left; maintain monotonic stack and track the "third" value so far.  
**Complexity:** O(n) time, O(n) space.

```java
public boolean find132pattern(int[] nums) {
    Deque<Integer> stack = new ArrayDeque<>();
    int third = Integer.MIN_VALUE; // nums[k]
    for (int i = nums.length - 1; i >= 0; i--) {
        if (nums[i] < third) return true;
        while (!stack.isEmpty() && stack.peek() < nums[i]) third = stack.pop();
        stack.push(nums[i]);
    }
    return false;
}
```

---

## 🔴 Hard (5)

### H1. Largest Rectangle in Histogram
Find the largest rectangle that can be formed in a histogram.  
**Hint:** Monotonic increasing stack; pop on smaller bar and compute width using index difference.  
**Complexity:** O(n) time, O(n) space.

```java
public int largestRectangleArea(int[] heights) {
    Deque<Integer> stack = new ArrayDeque<>();
    int max = 0;
    for (int i = 0; i <= heights.length; i++) {
        int h = (i == heights.length) ? 0 : heights[i];
        while (!stack.isEmpty() && h < heights[stack.peek()]) {
            int height = heights[stack.pop()];
            int width = stack.isEmpty() ? i : i - stack.peek() - 1;
            max = Math.max(max, height * width);
        }
        stack.push(i);
    }
    return max;
}
```

### H2. Maximal Rectangle
Find the largest rectangle of 1s in a binary matrix.  
**Hint:** Reduce each row to a histogram (cumulative height of 1s), then apply Largest Rectangle in Histogram per row.  
**Complexity:** O(m*n) time, O(n) space.

```java
public int maximalRectangle(char[][] matrix) {
    int n = matrix[0].length, max = 0;
    int[] heights = new int[n];
    for (char[] row : matrix) {
        for (int j = 0; j < n; j++)
            heights[j] = (row[j] == '1') ? heights[j] + 1 : 0;
        max = Math.max(max, largestRectangleArea(heights));
    }
    return max;
}
```

### H3. Basic Calculator
Implement a calculator supporting +, -, and parentheses.  
**Hint:** Stack for saving (result, sign) when entering parentheses; restore on `)`.  
**Complexity:** O(n) time, O(n) space.

```java
public int calculate(String s) {
    Deque<Integer> stack = new ArrayDeque<>();
    int result = 0, num = 0, sign = 1;
    for (char c : s.toCharArray()) {
        if (Character.isDigit(c)) num = num * 10 + (c - '0');
        else if (c == '+') { result += sign * num; num = 0; sign = 1; }
        else if (c == '-') { result += sign * num; num = 0; sign = -1; }
        else if (c == '(') { stack.push(result); stack.push(sign); result = 0; sign = 1; }
        else if (c == ')') { result += sign * num; num = 0; result *= stack.pop(); result += stack.pop(); }
    }
    return result + sign * num;
}
```

### H4. Remove Duplicate Letters
Remove duplicate letters so each letter appears once and in the smallest lexicographic order.  
**Hint:** Monotonic stack; only pop if the character will appear later (lastIndex tracking).  
**Complexity:** O(n) time, O(26) space.

```java
public String removeDuplicateLetters(String s) {
    int[] lastIdx = new int[26];
    for (int i = 0; i < s.length(); i++) lastIdx[s.charAt(i) - 'a'] = i;
    boolean[] inStack = new boolean[26];
    Deque<Character> stack = new ArrayDeque<>();
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (inStack[c - 'a']) continue;
        while (!stack.isEmpty() && stack.peek() > c && lastIdx[stack.peek() - 'a'] > i) {
            inStack[stack.pop() - 'a'] = false;
        }
        stack.push(c); inStack[c - 'a'] = true;
    }
    StringBuilder sb = new StringBuilder();
    for (char c : stack) sb.append(c);
    return sb.reverse().toString();
}
```

### H5. Maximum Width Ramp
Find max `j - i` where `i < j` and `nums[i] <= nums[j]`.  
**Hint:** Build a decreasing stack of candidates from the left; scan right to left and pop greedily.  
**Complexity:** O(n) time, O(n) space.

```java
public int maxWidthRamp(int[] nums) {
    int n = nums.length;
    Deque<Integer> stack = new ArrayDeque<>();
    for (int i = 0; i < n; i++)
        if (stack.isEmpty() || nums[stack.peek()] > nums[i]) stack.push(i);
    int maxWidth = 0;
    for (int j = n - 1; j >= 0; j--) {
        while (!stack.isEmpty() && nums[stack.peek()] <= nums[j]) {
            maxWidth = Math.max(maxWidth, j - stack.pop());
        }
    }
    return maxWidth;
}
```

---

## 🟡 Medium (10)

### M1. Daily Temperatures
For each day, find how many days until a warmer temperature.  
**Hint:** Monotonic decreasing stack of indices; pop when a warmer day is found.  
**Complexity:** O(n) time, O(n) space.

### M2. Evaluate Reverse Polish Notation
Evaluate an expression in RPN (postfix).  
**Hint:** Push numbers; on operator, pop two, compute, push result.  
**Complexity:** O(n) time, O(n) space.

### M3. Decode String
Decode strings like `3[a2[bc]]` → `abcbcabcbcabcbc`.  
**Hint:** Two stacks: one for counts, one for strings. Push on `[`, build result on `]`.  
**Complexity:** O(n * maxRepeat) time.

### M4. Next Greater Element II (Circular)
For a circular array, find the next greater element for each position.  
**Hint:** Traverse array twice (2n); monotonic stack of indices, take index mod n.  
**Complexity:** O(n) time, O(n) space.

### M5. Asteroid Collision
Simulate asteroids moving; positive = right, negative = left. Same size destroys both.  
**Hint:** Stack; only conflict when top > 0 and current < 0.  
**Complexity:** O(n) time, O(n) space.

### M6. Remove K Digits
Remove k digits to form the smallest possible number.  
**Hint:** Monotonic increasing stack; pop when current digit is smaller than top and k > 0.  
**Complexity:** O(n) time, O(n) space.

### M7. Simplify Path
Simplify a Unix-style file path.  
**Hint:** Split by `/`; push valid names, pop on `..`, ignore `.` and empty.  
**Complexity:** O(n) time, O(n) space.

### M8. Online Stock Span
For each day's price, find how many consecutive previous prices were ≤ today's.  
**Hint:** Monotonic decreasing stack storing (price, span) pairs.  
**Complexity:** Amortized O(1) per push, O(n) space.

### M9. Validate Stack Sequences
Given pushed and popped sequences, determine if they are valid stack operations.  
**Hint:** Simulate with a stack; push from pushed, pop when top matches next in popped.  
**Complexity:** O(n) time, O(n) space.

### M10. 132 Pattern
Find if there exists indices i < j < k such that nums[i] < nums[k] < nums[j].  
**Hint:** Traverse right to left; maintain monotonic stack and track the "third" value so far.  
**Complexity:** O(n) time, O(n) space.

---

## 🔴 Hard (5)

### H1. Largest Rectangle in Histogram
Find the largest rectangle that can be formed in a histogram.  
**Hint:** Monotonic increasing stack; pop on smaller bar and compute width using index difference.  
**Complexity:** O(n) time, O(n) space.

### H2. Maximal Rectangle
Find the largest rectangle of 1s in a binary matrix.  
**Hint:** Reduce each row to a histogram (cumulative height of 1s), then apply Largest Rectangle in Histogram per row.  
**Complexity:** O(m*n) time, O(n) space.

### H3. Basic Calculator
Implement a calculator supporting +, -, and parentheses.  
**Hint:** Stack for saving (result, sign) when entering parentheses; restore on `)`.  
**Complexity:** O(n) time, O(n) space.

### H4. Remove Duplicate Letters
Remove duplicate letters so each letter appears once and in the smallest lexicographic order.  
**Hint:** Monotonic stack; only pop if the character will appear later (lastIndex tracking).  
**Complexity:** O(n) time, O(26) space.

### H5. Maximum Width Ramp
Find max `j - i` where `i < j` and `nums[i] <= nums[j]`.  
**Hint:** Build a decreasing stack of candidates from the left; scan right to left and pop greedily.  
**Complexity:** O(n) time, O(n) space.
