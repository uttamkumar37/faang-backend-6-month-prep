# Backtracking — Practice Questions

---

## 🟢 Easy (5)

### E1. Letter Case Permutation
Given a string, generate all possible permutations by changing the case of each letter.  
**Hint:** At each letter, branch into lowercase and uppercase; recurse on rest.  
**Complexity:** O(2^n * n) time, O(2^n) space.

```java
public List<String> letterCasePermutation(String s) {
    List<String> res = new ArrayList<>();
    bt(s.toCharArray(), 0, res); return res;
}
private void bt(char[] arr, int i, List<String> res) {
    if (i == arr.length) { res.add(new String(arr)); return; }
    bt(arr, i + 1, res);
    if (Character.isLetter(arr[i])) {
        arr[i] ^= 32;
        bt(arr, i + 1, res);
        arr[i] ^= 32;
    }
}
```

### E2. All Paths From Source to Target
Find all paths from node 0 to node n-1 in a DAG.  
**Hint:** DFS from node 0; add current path to result whenever node n-1 is reached.  
**Complexity:** O(2^n * n) time (exponential paths in worst case).

```java
public List<List<Integer>> allPathsSourceTarget(int[][] graph) {
    List<List<Integer>> res = new ArrayList<>();
    List<Integer> path = new ArrayList<>();
    path.add(0); dfs(graph, 0, path, res);
    return res;
}
private void dfs(int[][] graph, int node, List<Integer> path, List<List<Integer>> res) {
    if (node == graph.length - 1) { res.add(new ArrayList<>(path)); return; }
    for (int nb : graph[node]) { path.add(nb); dfs(graph, nb, path, res); path.removeLast(); }
}
```

### E3. Find All Possible Recipes from Given Supplies
Find which recipes can be made given available ingredients and their dependencies.  
**Hint:** Topological sort or DFS with memoization; treat recipes that can be made as available.  
**Complexity:** O(V+E) time, O(V) space.

```java
public List<String> findAllRecipes(String[] recipes, List<List<String>> ingredients, String[] supplies) {
    Map<String, List<String>> adj = new HashMap<>();
    Map<String, Integer> inDeg = new HashMap<>();
    Set<String> supplySet = new HashSet<>(Arrays.asList(supplies));
    for (int i = 0; i < recipes.length; i++) {
        inDeg.put(recipes[i], ingredients.get(i).size());
        for (String ing : ingredients.get(i))
            adj.computeIfAbsent(ing, k -> new ArrayList<>()).add(recipes[i]);
    }
    Queue<String> q = new LinkedList<>(supplySet);
    List<String> res = new ArrayList<>();
    Set<String> recipeSet = new HashSet<>(Arrays.asList(recipes));
    while (!q.isEmpty()) {
        String cur = q.poll();
        for (String next : adj.getOrDefault(cur, Collections.emptyList()))
            if (inDeg.merge(next, -1, Integer::sum) == 0) { q.offer(next); if (recipeSet.contains(next)) res.add(next); }
    }
    return res;
}
```

### E4. Binary Watch
A binary watch has n LEDs; return all possible times it can represent.  
**Hint:** Iterate all valid hours (0-11) and minutes (0-59); check if bitCount(h) + bitCount(m) == n.  
**Complexity:** O(1) time (fixed 12*60 = 720 iterations).

```java
public List<String> readBinaryWatch(int turnedOn) {
    List<String> res = new ArrayList<>();
    for (int h = 0; h < 12; h++) for (int m = 0; m < 60; m++)
        if (Integer.bitCount(h) + Integer.bitCount(m) == turnedOn)
            res.add(String.format("%d:%02d", h, m));
    return res;
}
```

### E5. Letter Combinations of a Phone Number (2-digit)
Return all letter combinations for a 2-digit phone number input.  
**Hint:** Backtracking over digit characters; use a phone map. No pruning needed for 2 digits.  
**Complexity:** O(4^n * n) time where n = number of digits.

```java
public List<String> letterCombinations(String digits) {
    if (digits.isEmpty()) return new ArrayList<>();
    String[] map = {"","","abc","def","ghi","jkl","mno","pqrs","tuv","wxyz"};
    List<String> res = new ArrayList<>();
    btPhone(digits, 0, new StringBuilder(), map, res);
    return res;
}
private void btPhone(String digits, int i, StringBuilder sb, String[] map, List<String> res) {
    if (i == digits.length()) { res.add(sb.toString()); return; }
    for (char c : map[digits.charAt(i) - '0'].toCharArray()) {
        sb.append(c); btPhone(digits, i+1, sb, map, res); sb.deleteCharAt(sb.length()-1);
    }
}
```

---

## 🟡 Medium (10)

### M1. Subsets
Return all possible subsets (the power set) of a distinct integer array.  
**Hint:** At each index, choose to include or skip. Or iterate and add current element to all existing subsets.  
**Complexity:** O(2^n * n) time, O(2^n) space.

```java
public List<List<Integer>> subsets(int[] nums) {
    List<List<Integer>> res = new ArrayList<>();
    btSubsets(nums, 0, new ArrayList<>(), res);
    return res;
}
private void btSubsets(int[] nums, int start, List<Integer> cur, List<List<Integer>> res) {
    res.add(new ArrayList<>(cur));
    for (int i = start; i < nums.length; i++) {
        cur.add(nums[i]); btSubsets(nums, i+1, cur, res); cur.removeLast();
    }
}
```

### M2. Permutations
Return all permutations of distinct integers.  
**Hint:** Swap current index with each subsequent index; backtrack (swap back).  
**Complexity:** O(n! * n) time, O(n) space.

```java
public List<List<Integer>> permute(int[] nums) {
    List<List<Integer>> res = new ArrayList<>();
    btPerm(nums, 0, res); return res;
}
private void btPerm(int[] nums, int start, List<List<Integer>> res) {
    if (start == nums.length) { List<Integer> l = new ArrayList<>(); for (int n : nums) l.add(n); res.add(l); return; }
    for (int i = start; i < nums.length; i++) {
        swap(nums, start, i); btPerm(nums, start+1, res); swap(nums, start, i);
    }
}
private void swap(int[] a, int i, int j) { int t = a[i]; a[i] = a[j]; a[j] = t; }
```

### M3. Combination Sum
Find all combinations that sum to target; elements can be reused.  
**Hint:** DFS from current index; subtract candidate, recurse; backtrack. No need to advance index (reuse allowed).  
**Complexity:** O(n^(target/min)) time.

```java
public List<List<Integer>> combinationSum(int[] candidates, int target) {
    List<List<Integer>> res = new ArrayList<>();
    btCombo(candidates, target, 0, new ArrayList<>(), res);
    return res;
}
private void btCombo(int[] c, int rem, int start, List<Integer> cur, List<List<Integer>> res) {
    if (rem == 0) { res.add(new ArrayList<>(cur)); return; }
    for (int i = start; i < c.length && c[i] <= rem; i++) {
        cur.add(c[i]); btCombo(c, rem-c[i], i, cur, res); cur.removeLast();
    }
}
```

### M4. Combination Sum II (No Reuse, With Duplicates)
Combinations summing to target using each element at most once; no duplicate combos.  
**Hint:** Sort input; skip duplicate siblings at the same depth level (i > start && nums[i] == nums[i-1]).  
**Complexity:** O(2^n * n) time.

```java
public List<List<Integer>> combinationSum2(int[] candidates, int target) {
    Arrays.sort(candidates);
    List<List<Integer>> res = new ArrayList<>();
    btCombo2(candidates, target, 0, new ArrayList<>(), res);
    return res;
}
private void btCombo2(int[] c, int rem, int start, List<Integer> cur, List<List<Integer>> res) {
    if (rem == 0) { res.add(new ArrayList<>(cur)); return; }
    for (int i = start; i < c.length && c[i] <= rem; i++) {
        if (i > start && c[i] == c[i-1]) continue;
        cur.add(c[i]); btCombo2(c, rem-c[i], i+1, cur, res); cur.removeLast();
    }
}
```

### M5. Generate Parentheses
Generate all combinations of n pairs of valid parentheses.  
**Hint:** Track open and close counts; add '(' if open < n, add ')' if close < open.  
**Complexity:** O(4^n / sqrt(n)) time (Catalan number).

```java
public List<String> generateParenthesis(int n) {
    List<String> res = new ArrayList<>();
    btParen(n, 0, 0, new StringBuilder(), res);
    return res;
}
private void btParen(int n, int open, int close, StringBuilder sb, List<String> res) {
    if (sb.length() == 2 * n) { res.add(sb.toString()); return; }
    if (open < n)   { sb.append('('); btParen(n, open+1, close, sb, res); sb.deleteCharAt(sb.length()-1); }
    if (close < open) { sb.append(')'); btParen(n, open, close+1, sb, res); sb.deleteCharAt(sb.length()-1); }
}
```

### M6. Palindrome Partitioning
Partition a string such that every substring is a palindrome; return all such partitions.  
**Hint:** DFS from current index; for each end, if substring is palindrome, recurse from end+1.  
**Complexity:** O(n * 2^n) time.

```java
public List<List<String>> partition(String s) {
    List<List<String>> res = new ArrayList<>();
    btPalin(s, 0, new ArrayList<>(), res);
    return res;
}
private void btPalin(String s, int start, List<String> cur, List<List<String>> res) {
    if (start == s.length()) { res.add(new ArrayList<>(cur)); return; }
    for (int end = start + 1; end <= s.length(); end++) {
        String sub = s.substring(start, end);
        if (isPalin(sub)) { cur.add(sub); btPalin(s, end, cur, res); cur.removeLast(); }
    }
}
private boolean isPalin(String s) { int l=0,r=s.length()-1; while(l<r) if(s.charAt(l++)!=s.charAt(r--)) return false; return true; }
```

### M7. Word Search
Search for a word in a 2D grid; letters can connect horizontally or vertically.  
**Hint:** DFS with backtracking; mark cell visited by temporarily mutating (restore after); prune early.  
**Complexity:** O(m * n * 4^L) where L = word length.

```java
public boolean exist(char[][] board, String word) {
    int m = board.length, n = board[0].length;
    for (int i = 0; i < m; i++) for (int j = 0; j < n; j++)
        if (dfsWord(board, i, j, word, 0)) return true;
    return false;
}
private boolean dfsWord(char[][] b, int r, int c, String w, int i) {
    if (i == w.length()) return true;
    if (r<0||r>=b.length||c<0||c>=b[0].length||b[r][c]!=w.charAt(i)) return false;
    char tmp = b[r][c]; b[r][c] = '#';
    boolean found = dfsWord(b,r+1,c,w,i+1)||dfsWord(b,r-1,c,w,i+1)||dfsWord(b,r,c+1,w,i+1)||dfsWord(b,r,c-1,w,i+1);
    b[r][c] = tmp; return found;
}
```

### M8. Combinations
Return all combinations of k numbers chosen from 1 to n.  
**Hint:** DFS with start pointer; pruning: if remaining needed > remaining available, stop early.  
**Complexity:** O(C(n,k) * k) time.

```java
public List<List<Integer>> combine(int n, int k) {
    List<List<Integer>> res = new ArrayList<>();
    btCombine(n, k, 1, new ArrayList<>(), res);
    return res;
}
private void btCombine(int n, int k, int start, List<Integer> cur, List<List<Integer>> res) {
    if (cur.size() == k) { res.add(new ArrayList<>(cur)); return; }
    int need = k - cur.size();
    for (int i = start; i <= n - need + 1; i++) {
        cur.add(i); btCombine(n, k, i+1, cur, res); cur.removeLast();
    }
}
```

### M9. Restore IP Addresses
Return all valid IP address combinations from a string of digits.  
**Hint:** Backtrack with 4 segments; each segment must be 0-255 and must not have leading zeros.  
**Complexity:** O(1) time (at most 3^4 = 81 combinations).

```java
public List<String> restoreIpAddresses(String s) {
    List<String> res = new ArrayList<>();
    btIP(s, 0, new ArrayList<>(), res);
    return res;
}
private void btIP(String s, int start, List<String> parts, List<String> res) {
    if (parts.size() == 4) { if (start == s.length()) res.add(String.join(".", parts)); return; }
    for (int len = 1; len <= 3; len++) {
        if (start + len > s.length()) break;
        String seg = s.substring(start, start + len);
        if (seg.length() > 1 && seg.charAt(0) == '0') break;
        if (Integer.parseInt(seg) > 255) break;
        parts.add(seg); btIP(s, start+len, parts, res); parts.removeLast();
    }
}
```

### M10. Subsets II (With Duplicates)
Return all unique subsets when input may contain duplicates.  
**Hint:** Sort input; skip duplicate elements at the same recursion level (same as Comb Sum II trick).  
**Complexity:** O(2^n * n) time.

```java
public List<List<Integer>> subsetsWithDup(int[] nums) {
    Arrays.sort(nums);
    List<List<Integer>> res = new ArrayList<>();
    btSubDup(nums, 0, new ArrayList<>(), res);
    return res;
}
private void btSubDup(int[] nums, int start, List<Integer> cur, List<List<Integer>> res) {
    res.add(new ArrayList<>(cur));
    for (int i = start; i < nums.length; i++) {
        if (i > start && nums[i] == nums[i-1]) continue;
        cur.add(nums[i]); btSubDup(nums, i+1, cur, res); cur.removeLast();
    }
}
```

---

## 🔴 Hard (5)

### H1. N-Queens
Place n queens on an n×n board so no two queens attack each other. Return all solutions.  
**Hint:** DFS row by row; track occupied columns, diagonals (col-row), and anti-diagonals (col+row) in sets.  
**Complexity:** O(n!) time, O(n²) space for storing boards.

```java
public List<List<String>> solveNQueens(int n) {
    List<List<String>> res = new ArrayList<>();
    Set<Integer> cols = new HashSet<>(), diag = new HashSet<>(), antiDiag = new HashSet<>();
    char[][] board = new char[n][n];
    for (char[] row : board) Arrays.fill(row, '.');
    btQueens(board, 0, cols, diag, antiDiag, res);
    return res;
}
private void btQueens(char[][] b, int row, Set<Integer> cols, Set<Integer> diag, Set<Integer> anti, List<List<String>> res) {
    if (row == b.length) { List<String> l = new ArrayList<>(); for (char[] r : b) l.add(new String(r)); res.add(l); return; }
    for (int c = 0; c < b.length; c++) {
        if (cols.contains(c) || diag.contains(c-row) || anti.contains(c+row)) continue;
        b[row][c]='Q'; cols.add(c); diag.add(c-row); anti.add(c+row);
        btQueens(b, row+1, cols, diag, anti, res);
        b[row][c]='.'; cols.remove(c); diag.remove(c-row); anti.remove(c+row);
    }
}
```

### H2. Sudoku Solver
Solve a Sudoku puzzle by filling empty cells.  
**Hint:** Find next empty cell; try digits 1-9; check row/col/box validity; recurse; backtrack if stuck.  
**Complexity:** O(9^m) worst case where m = number of empty cells (in practice much faster).

```java
public void solveSudoku(char[][] board) { btSudoku(board); }
private boolean btSudoku(char[][] b) {
    for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
        if (b[r][c] != '.') continue;
        for (char d = '1'; d <= '9'; d++) {
            if (isValid(b, r, c, d)) {
                b[r][c] = d;
                if (btSudoku(b)) return true;
                b[r][c] = '.';
            }
        }
        return false;
    }
    return true;
}
private boolean isValid(char[][] b, int row, int col, char d) {
    for (int i = 0; i < 9; i++) {
        if (b[row][i] == d || b[i][col] == d) return false;
        int br = 3*(row/3)+i/3, bc = 3*(col/3)+i%3;
        if (b[br][bc] == d) return false;
    }
    return true;
}
```

### H3. Word Search II
Find all words from a list that exist in the board.  
**Hint:** Build a Trie from the word list; DFS on the board guided by the Trie to prune early.  
**Complexity:** O(m * n * 4^L) where L = max word length.

```java
public List<String> findWords(char[][] board, String[] words) {
    TrieNode root = new TrieNode();
    for (String w : words) insert(root, w);
    List<String> res = new ArrayList<>();
    for (int i = 0; i < board.length; i++) for (int j = 0; j < board[0].length; j++)
        dfsWS(board, i, j, root, res);
    return res;
}
private void dfsWS(char[][] b, int r, int c, TrieNode node, List<String> res) {
    if (r<0||r>=b.length||c<0||c>=b[0].length||b[r][c]=='#') return;
    char ch = b[r][c];
    TrieNode next = node.children[ch-'a'];
    if (next == null) return;
    if (next.word != null) { res.add(next.word); next.word = null; }
    b[r][c]='#';
    dfsWS(b,r+1,c,next,res); dfsWS(b,r-1,c,next,res); dfsWS(b,r,c+1,next,res); dfsWS(b,r,c-1,next,res);
    b[r][c]=ch;
}
private void insert(TrieNode root, String word) {
    TrieNode cur = root;
    for (char c : word.toCharArray()) { if(cur.children[c-'a']==null) cur.children[c-'a']=new TrieNode(); cur=cur.children[c-'a']; }
    cur.word = word;
}
static class TrieNode { TrieNode[] children = new TrieNode[26]; String word; }
```

### H4. Remove Invalid Parentheses
Remove the minimum number of invalid parentheses to make the string valid; return all results.  
**Hint:** BFS level by level; each level removes one character. Stop when valid strings are found.  
**Complexity:** O(2^n * n) worst case.

```java
public List<String> removeInvalidParentheses(String s) {
    List<String> res = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Queue<String> q = new LinkedList<>();
    q.offer(s); visited.add(s);
    boolean found = false;
    while (!q.isEmpty()) {
        String cur = q.poll();
        if (isValidParen(cur)) { res.add(cur); found = true; }
        if (found) continue;
        for (int i = 0; i < cur.length(); i++) {
            if (cur.charAt(i) != '(' && cur.charAt(i) != ')') continue;
            String next = cur.substring(0, i) + cur.substring(i+1);
            if (visited.add(next)) q.offer(next);
        }
    }
    return res;
}
private boolean isValidParen(String s) {
    int cnt = 0;
    for (char c : s.toCharArray()) { if(c=='(') cnt++; else if(c==')') if(--cnt<0) return false; }
    return cnt == 0;
}
```

### H5. Expression Add Operators
Add +, -, or * between digits to reach a target value; return all such expressions.  
**Hint:** Backtracking with current value and previous operand (needed to handle * precedence by "undoing" previous multiplication).  
**Complexity:** O(n * 4^n) time.

```java
public List<String> addOperators(String num, int target) {
    List<String> res = new ArrayList<>();
    btOps(num, target, 0, 0, 0, "", res);
    return res;
}
private void btOps(String num, long target, int pos, long val, long prev, String expr, List<String> res) {
    if (pos == num.length()) { if (val == target) res.add(expr); return; }
    for (int len = 1; len <= num.length() - pos; len++) {
        String part = num.substring(pos, pos + len);
        if (part.length() > 1 && part.charAt(0) == '0') break;
        long cur = Long.parseLong(part);
        if (pos == 0) btOps(num, target, len, cur, cur, part, res);
        else {
            btOps(num, target, pos+len, val+cur, cur, expr+"+"+part, res);
            btOps(num, target, pos+len, val-cur, -cur, expr+"-"+part, res);
            btOps(num, target, pos+len, val-prev+prev*cur, prev*cur, expr+"*"+part, res);
        }
    }
}
```
