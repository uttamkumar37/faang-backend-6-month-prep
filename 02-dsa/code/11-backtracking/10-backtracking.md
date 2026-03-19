# Backtracking — Complete Theory (Basic → Advanced)

---

## 1. What Is Backtracking?

Backtracking is a **recursive exhaustive search** that builds a solution incrementally and **abandons a path** (backtracks) as soon as a constraint is violated — pruning the search tree.

```
State space tree for subsets of {1, 2, 3}:
                 []
              /   |   \
           [1]   [2]   [3]
          /  \     \
       [1,2] [1,3] [2,3]
        |
     [1,2,3]

Backtracking explores this tree; pruning cuts branches early.
```

**Three components of backtracking:**
1. **State**: current partial solution being built
2. **Choices**: what candidates to try at the current step
3. **Constraints**: when to stop / backtrack

**Template:**
```java
void backtrack(state, start, choices) {
    if (isSolution(state)) { record(state); return; }
    for each candidate in choices starting at start:
        if (isValid(candidate, state)):
            apply(candidate, state);
            backtrack(state, nextStart, choices);
            undo(candidate, state);    // ← backtrack step
}
```

---

## 2. Subsets

```java
// All subsets of [1..n] with no duplicates
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> res = new ArrayList<>();
    backtrackSubsets(nums, 0, new ArrayList<>(), res);
    return res;
}

private void backtrackSubsets(int[] nums, int start, List<Integer> cur, List<List<Integer>> res) {
    res.add(new ArrayList<>(cur));      // record current state (every node is a valid subset)
    for (int i = start; i < nums.length; i++) {
        cur.add(nums[i]);
        backtrackSubsets(nums, i + 1, cur, res);
        cur.remove(cur.size() - 1);     // backtrack
    }
}
```

**With duplicates (Subsets II)** — sort first, skip duplicate branches:
```java
for (int i = start; i < nums.length; i++) {
    if (i > start && nums[i] == nums[i-1]) continue;  // skip dup at same level
    cur.add(nums[i]);
    backtrack(nums, i + 1, cur, res);
    cur.remove(cur.size() - 1);
}
```

---

## 3. Permutations

```java
// All permutations of distinct elements
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> res = new ArrayList<>();
    backtrackPerm(nums, new boolean[nums.length], new ArrayList<>(), res);
    return res;
}

private void backtrackPerm(int[] nums, boolean[] used, List<Integer> cur, List<List<Integer>> res) {
    if (cur.size() == nums.length) { res.add(new ArrayList<>(cur)); return; }
    for (int i = 0; i < nums.length; i++) {
        if (used[i]) continue;
        used[i] = true;
        cur.add(nums[i]);
        backtrackPerm(nums, used, cur, res);
        cur.remove(cur.size() - 1);
        used[i] = false;
    }
}
```

**With duplicates (Permutations II)** — sort + skip same value at same level:
```java
if (i > 0 && nums[i] == nums[i-1] && !used[i-1]) continue;
```

---

## 4. Combinations

```java
// All size-k combinations from [1..n]
public List<List<Integer>> combine(int n, int k) {
    List<List<Integer>> res = new ArrayList<>();
    backtrackComb(n, k, 1, new ArrayList<>(), res);
    return res;
}

private void backtrackComb(int n, int k, int start, List<Integer> cur, List<List<Integer>> res) {
    if (cur.size() == k) { res.add(new ArrayList<>(cur)); return; }
    for (int i = start; i <= n - (k - cur.size()) + 1; i++) {  // pruning: enough elements left?
        cur.add(i);
        backtrackComb(n, k, i + 1, cur, res);
        cur.remove(cur.size() - 1);
    }
}
```

**Combination Sum (unlimited use of candidates):**
```java
private void backtrackCombSum(int[] candidates, int target, int start, List<Integer> cur, List<List<Integer>> res) {
    if (target == 0) { res.add(new ArrayList<>(cur)); return; }
    for (int i = start; i < candidates.length; i++) {
        if (candidates[i] > target) break;     // sorted → no point continuing
        cur.add(candidates[i]);
        backtrackCombSum(candidates, target - candidates[i], i, cur, res);  // i (not i+1) allows reuse
        cur.remove(cur.size() - 1);
    }
}
```

---

## 5. N-Queens

```
Place N queens on N×N board so no two queens attack each other.
A queen attacks: same row, same column, same diagonal.

Track:
  cols:  set of occupied columns
  diag1: row - col (same for all \-diagonals)
  diag2: row + col (same for all /-diagonals)
```

```java
List<List<String>> res = new ArrayList<>();
public List<List<String>> solveNQueens(int n) {
    backtrackQueens(n, 0, new boolean[n], new boolean[2*n], new boolean[2*n], new int[n]);
    return res;
}

private void backtrackQueens(int n, int row, boolean[] cols, boolean[] diag1, boolean[] diag2, int[] queens) {
    if (row == n) { res.add(buildBoard(queens, n)); return; }
    for (int col = 0; col < n; col++) {
        if (cols[col] || diag1[row-col+n] || diag2[row+col]) continue;
        queens[row] = col;
        cols[col] = diag1[row-col+n] = diag2[row+col] = true;
        backtrackQueens(n, row+1, cols, diag1, diag2, queens);
        cols[col] = diag1[row-col+n] = diag2[row+col] = false;
    }
}
```

---

## 6. Sudoku Solver

```java
public void solveSudoku(char[][] board) {
    solve(board);
}

private boolean solve(char[][] board) {
    for (int i = 0; i < 9; i++)
        for (int j = 0; j < 9; j++) {
            if (board[i][j] != '.') continue;
            for (char c = '1'; c <= '9'; c++) {
                if (isValid(board, i, j, c)) {
                    board[i][j] = c;
                    if (solve(board)) return true;
                    board[i][j] = '.';            // backtrack
                }
            }
            return false;   // no valid digit → backtrack to caller
        }
    return true;   // all cells filled
}

private boolean isValid(char[][] b, int r, int c, char ch) {
    for (int i = 0; i < 9; i++) {
        if (b[r][i] == ch || b[i][c] == ch) return false;
        if (b[3*(r/3)+i/3][3*(c/3)+i%3] == ch) return false;    // box check
    }
    return true;
}
```

---

## 7. Word Search

```java
public boolean exist(char[][] board, String word) {
    for (int i = 0; i < board.length; i++)
        for (int j = 0; j < board[0].length; j++)
            if (dfs(board, word, i, j, 0)) return true;
    return false;
}

private boolean dfs(char[][] b, String w, int r, int c, int idx) {
    if (idx == w.length()) return true;
    if (r < 0 || r >= b.length || c < 0 || c >= b[0].length || b[r][c] != w.charAt(idx)) return false;
    char tmp = b[r][c];
    b[r][c] = '#';     // mark visited
    boolean found = dfs(b, w, r+1, c, idx+1) || dfs(b, w, r-1, c, idx+1)
                 || dfs(b, w, r, c+1, idx+1) || dfs(b, w, r, c-1, idx+1);
    b[r][c] = tmp;     // restore (backtrack)
    return found;
}
```

---

## 8. Generate Parentheses

```java
public List<String> generateParenthesis(int n) {
    List<String> res = new ArrayList<>();
    backtrack(n, 0, 0, new StringBuilder(), res);
    return res;
}

private void backtrack(int n, int open, int close, StringBuilder sb, List<String> res) {
    if (sb.length() == 2*n) { res.add(sb.toString()); return; }
    if (open < n) {
        sb.append('(');
        backtrack(n, open+1, close, sb, res);
        sb.deleteCharAt(sb.length()-1);
    }
    if (close < open) {    // constraint: can't close more than opened
        sb.append(')');
        backtrack(n, open, close+1, sb, res);
        sb.deleteCharAt(sb.length()-1);
    }
}
```

---

## 9. Letter Combinations of Phone Number

```java
private static final String[] MAP = {"", "", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};

public List<String> letterCombinations(String digits) {
    List<String> res = new ArrayList<>();
    if (digits.isEmpty()) return res;
    backtrack(digits, 0, new StringBuilder(), res);
    return res;
}

private void backtrack(String digits, int idx, StringBuilder sb, List<String> res) {
    if (idx == digits.length()) { res.add(sb.toString()); return; }
    for (char c : MAP[digits.charAt(idx) - '0'].toCharArray()) {
        sb.append(c);
        backtrack(digits, idx+1, sb, res);
        sb.deleteCharAt(sb.length()-1);
    }
}
```

---

## 10. Palindrome Partitioning

```java
public List<List<String>> partition(String s) {
    List<List<String>> res = new ArrayList<>();
    backtrack(s, 0, new ArrayList<>(), res);
    return res;
}

private void backtrack(String s, int start, List<String> cur, List<List<String>> res) {
    if (start == s.length()) { res.add(new ArrayList<>(cur)); return; }
    for (int end = start+1; end <= s.length(); end++) {
        String substr = s.substring(start, end);
        if (isPalindrome(substr)) {
            cur.add(substr);
            backtrack(s, end, cur, res);
            cur.remove(cur.size()-1);
        }
    }
}
```

---

## 11. Pruning Strategies

**Feasibility pruning**: skip if remaining capacity is exhausted
```java
if (candidates[i] > target) break;  // sorted array
```

**Symmetry/duplicate pruning**: skip same value at same recursion depth
```java
if (i > start && nums[i] == nums[i-1]) continue;
```

**Depth pruning**: insufficient elements remain for required length
```java
for (int i = start; i <= n - (k - cur.size()) + 1; i++)
```

**Constraint propagation** (sudoku): before recursing, propagate what cells can still be filled.

---

## 12. Complexity Analysis

Exact complexity depends on the problem. General bounds:

| Problem | # Solutions | Time |
|---|---|---|
| Subsets of n | 2^n | O(n × 2^n) |
| Permutations of n | n! | O(n × n!) |
| Combinations n choose k | C(n,k) | O(k × C(n,k)) |
| N-Queens | O(n!) | O(n!)|
| Combination Sum (target T, min val m) | exponential | O((T/m)^n) with pruning |

---

## 13. Decision Guide

| Signal | Pattern |
|---|---|
| All subsets / power set | Backtrack, add every node |
| All permutations | Backtrack with `used[]` |
| All combinations of size k | Backtrack, pass `start` index |
| Constraint satisfaction (n-queens, sudoku) | Backtrack with validity check |
| Path in grid (word search) | DFS with in-place marking |
| Balanced structures (parentheses) | Backtrack with count constraints |
| Duplicate elements | Sort first + skip same value at same depth |

---

## 14. Common Pitfalls

- **Forgetting to copy the list**: `res.add(new ArrayList<>(cur))` not `res.add(cur)` (cur is mutated)
- **Wrong backtrack undo**: must undo exactly what was added — `cur.remove(cur.size()-1)`, not `cur.remove(element)` (removes first occurrence)
- **Duplicate skip condition**: `i > start` for combination/subset, `!used[i-1]` for permutation duplicates
- **Combination sum with i vs i+1**: pass `i` to allow reuse, `i+1` to forbid reuse
- **Grid restoration**: always restore board cell after DFS, even if `found` is true (for all-solutions problems)
- **N-Queens diag1 offset**: `row - col` can be negative → add offset n: `diag1[row - col + n]`
