package dsa.backtracking;

import java.util.*;

/**
 * Backtracking patterns: subsets, permutations, combinations, N-queens, word search.
 */
public class BacktrackingPatterns {

    // ─── 1. SUBSETS ──────────────────────────────────────────────────────────────

    static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrackSubsets(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrackSubsets(int[] nums, int start, List<Integer> path, List<List<Integer>> result) {
        result.add(new ArrayList<>(path));
        for (int i = start; i < nums.length; i++) {
            path.add(nums[i]);
            backtrackSubsets(nums, i + 1, path, result);
            path.remove(path.size() - 1);
        }
    }

    // Subsets II — with duplicates
    static List<List<Integer>> subsetsWithDup(int[] nums) {
        Arrays.sort(nums);
        List<List<Integer>> result = new ArrayList<>();
        backtrackSubsets2(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrackSubsets2(int[] nums, int start, List<Integer> path, List<List<Integer>> result) {
        result.add(new ArrayList<>(path));
        for (int i = start; i < nums.length; i++) {
            if (i > start && nums[i] == nums[i - 1]) continue; // skip duplicates
            path.add(nums[i]);
            backtrackSubsets2(nums, i + 1, path, result);
            path.remove(path.size() - 1);
        }
    }

    // ─── 2. PERMUTATIONS ─────────────────────────────────────────────────────────

    static List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrackPermute(nums, new boolean[nums.length], new ArrayList<>(), result);
        return result;
    }

    private static void backtrackPermute(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> result) {
        if (path.size() == nums.length) { result.add(new ArrayList<>(path)); return; }
        for (int i = 0; i < nums.length; i++) {
            if (used[i]) continue;
            used[i] = true;
            path.add(nums[i]);
            backtrackPermute(nums, used, path, result);
            path.remove(path.size() - 1);
            used[i] = false;
        }
    }

    // ─── 3. COMBINATION SUM (reuse allowed) ──────────────────────────────────────

    static List<List<Integer>> combinationSum(int[] candidates, int target) {
        Arrays.sort(candidates);
        List<List<Integer>> result = new ArrayList<>();
        backtrackCombination(candidates, target, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrackCombination(int[] candidates, int remaining, int start,
                                              List<Integer> path, List<List<Integer>> result) {
        if (remaining == 0) { result.add(new ArrayList<>(path)); return; }
        for (int i = start; i < candidates.length; i++) {
            if (candidates[i] > remaining) break;
            path.add(candidates[i]);
            backtrackCombination(candidates, remaining - candidates[i], i, path, result);
            path.remove(path.size() - 1);
        }
    }

    // ─── 4. N-QUEENS ─────────────────────────────────────────────────────────────

    static List<List<String>> solveNQueens(int n) {
        List<List<String>> result = new ArrayList<>();
        int[] queens = new int[n];
        Arrays.fill(queens, -1);
        Set<Integer> cols = new HashSet<>(), diag1 = new HashSet<>(), diag2 = new HashSet<>();
        backtrackQueens(n, 0, queens, cols, diag1, diag2, result);
        return result;
    }

    private static void backtrackQueens(int n, int row, int[] queens,
                                         Set<Integer> cols, Set<Integer> diag1, Set<Integer> diag2,
                                         List<List<String>> result) {
        if (row == n) { result.add(buildBoard(queens, n)); return; }
        for (int col = 0; col < n; col++) {
            if (cols.contains(col) || diag1.contains(row - col) || diag2.contains(row + col)) continue;
            queens[row] = col;
            cols.add(col); diag1.add(row - col); diag2.add(row + col);
            backtrackQueens(n, row + 1, queens, cols, diag1, diag2, result);
            queens[row] = -1;
            cols.remove(col); diag1.remove(row - col); diag2.remove(row + col);
        }
    }

    private static List<String> buildBoard(int[] queens, int n) {
        List<String> board = new ArrayList<>();
        for (int row = 0; row < n; row++) {
            char[] line = new char[n];
            Arrays.fill(line, '.');
            line[queens[row]] = 'Q';
            board.add(new String(line));
        }
        return board;
    }

    // ─── 5. WORD SEARCH ──────────────────────────────────────────────────────────

    static boolean exist(char[][] board, String word) {
        for (int i = 0; i < board.length; i++)
            for (int j = 0; j < board[0].length; j++)
                if (dfsWord(board, word, i, j, 0)) return true;
        return false;
    }

    private static boolean dfsWord(char[][] board, String word, int r, int c, int idx) {
        if (idx == word.length()) return true;
        if (r < 0 || r >= board.length || c < 0 || c >= board[0].length) return false;
        if (board[r][c] != word.charAt(idx)) return false;
        char tmp = board[r][c];
        board[r][c] = '#';
        boolean found = dfsWord(board, word, r+1, c, idx+1)
                     || dfsWord(board, word, r-1, c, idx+1)
                     || dfsWord(board, word, r, c+1, idx+1)
                     || dfsWord(board, word, r, c-1, idx+1);
        board[r][c] = tmp;
        return found;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("Subsets [1,2,3]: " + subsets(new int[]{1,2,3}));
        // [[], [1], [1,2], [1,2,3], [1,3], [2], [2,3], [3]]

        System.out.println("Permutations [1,2,3]: " + permute(new int[]{1,2,3}));

        System.out.println("Combination sum (target=7): " + combinationSum(new int[]{2,3,6,7}, 7));
        // [[2,2,3],[7]]

        System.out.println("N-Queens (n=4): " + solveNQueens(4).size() + " solutions"); // 2

        char[][] board = {{'A','B','C','E'},{'S','F','C','S'},{'A','D','E','E'}};
        System.out.println("Word 'ABCCED': " + exist(board, "ABCCED")); // true
        System.out.println("Word 'ABCB': " + exist(board, "ABCB"));     // false
    }
}
