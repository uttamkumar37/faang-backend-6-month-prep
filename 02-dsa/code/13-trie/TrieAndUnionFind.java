package dsa.trie;

import java.util.*;

/**
 * Trie (Prefix Tree) and Union-Find patterns.
 */
public class TrieAndUnionFind {

    // ─── TRIE ────────────────────────────────────────────────────────────────────

    static class Trie {
        private static class TrieNode {
            TrieNode[] children = new TrieNode[26];
            boolean isEnd = false;
        }

        private final TrieNode root = new TrieNode();

        public void insert(String word) {
            TrieNode node = root;
            for (char c : word.toCharArray()) {
                int idx = c - 'a';
                if (node.children[idx] == null) node.children[idx] = new TrieNode();
                node = node.children[idx];
            }
            node.isEnd = true;
        }

        public boolean search(String word) {
            TrieNode node = getNode(word);
            return node != null && node.isEnd;
        }

        public boolean startsWith(String prefix) {
            return getNode(prefix) != null;
        }

        // LeetCode 211 — search with wildcard '.'
        public boolean searchWithWildcard(String word) {
            return searchHelper(root, word, 0);
        }

        private boolean searchHelper(TrieNode node, String word, int idx) {
            if (node == null) return false;
            if (idx == word.length()) return node.isEnd;
            char c = word.charAt(idx);
            if (c == '.') {
                for (TrieNode child : node.children)
                    if (searchHelper(child, word, idx + 1)) return true;
                return false;
            }
            return searchHelper(node.children[c - 'a'], word, idx + 1);
        }

        private TrieNode getNode(String s) {
            TrieNode node = root;
            for (char c : s.toCharArray()) {
                node = node.children[c - 'a'];
                if (node == null) return null;
            }
            return node;
        }
    }

    // ─── UNION-FIND ──────────────────────────────────────────────────────────────

    static class UnionFind {
        private final int[] parent, rank;
        private int components;

        UnionFind(int n) {
            parent = new int[n]; rank = new int[n]; components = n;
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        public int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]); // path compression
            return parent[x];
        }

        public boolean union(int x, int y) {
            int px = find(x), py = find(y);
            if (px == py) return false;
            if (rank[px] < rank[py]) { int t = px; px = py; py = t; }
            parent[py] = px;
            if (rank[px] == rank[py]) rank[px]++;
            components--;
            return true;
        }

        public boolean connected(int x, int y) { return find(x) == find(y); }
        public int getComponents() { return components; }
    }

    // ─── ACCOUNTS MERGE (Union-Find) ─────────────────────────────────────────────

    static List<List<String>> accountsMerge(List<List<String>> accounts) {
        Map<String, Integer> emailToId = new HashMap<>();
        Map<String, String> emailToName = new HashMap<>();
        int[] nextId = {0};

        for (List<String> account : accounts) {
            String name = account.get(0);
            for (int i = 1; i < account.size(); i++) {
                String email = account.get(i);
                if (!emailToId.containsKey(email)) {
                    emailToId.put(email, nextId[0]++);
                    emailToName.put(email, name);
                }
            }
        }

        UnionFind uf = new UnionFind(nextId[0]);
        for (List<String> account : accounts) {
            int firstId = emailToId.get(account.get(1));
            for (int i = 2; i < account.size(); i++) {
                uf.union(firstId, emailToId.get(account.get(i)));
            }
        }

        Map<Integer, List<String>> components = new HashMap<>();
        for (Map.Entry<String, Integer> entry : emailToId.entrySet()) {
            int root = uf.find(entry.getValue());
            components.computeIfAbsent(root, k -> new ArrayList<>()).add(entry.getKey());
        }

        List<List<String>> result = new ArrayList<>();
        for (List<String> emails : components.values()) {
            Collections.sort(emails);
            emails.add(0, emailToName.get(emails.get(0)));
            result.add(emails);
        }
        return result;
    }

    // ─── WORD SEARCH II (Trie + DFS) ─────────────────────────────────────────────

    static List<String> findWords(char[][] board, String[] words) {
        Trie trie = new Trie();
        for (String w : words) trie.insert(w);

        Set<String> result = new HashSet<>();
        for (int i = 0; i < board.length; i++)
            for (int j = 0; j < board[0].length; j++)
                dfsBoardSearch(board, i, j, trie.root, new StringBuilder(), result);
        return new ArrayList<>(result);
    }

    private static void dfsBoardSearch(char[][] board, int r, int c,
                                        Trie.TrieNode node, StringBuilder path, Set<String> result) {
        if (r < 0 || r >= board.length || c < 0 || c >= board[0].length || board[r][c] == '#') return;
        char ch = board[r][c];
        Trie.TrieNode next = node.children[ch - 'a'];
        if (next == null) return;
        path.append(ch);
        if (next.isEnd) result.add(path.toString());
        board[r][c] = '#';
        dfsBoardSearch(board, r+1, c, next, path, result);
        dfsBoardSearch(board, r-1, c, next, path, result);
        dfsBoardSearch(board, r, c+1, next, path, result);
        dfsBoardSearch(board, r, c-1, next, path, result);
        board[r][c] = ch;
        path.deleteCharAt(path.length() - 1);
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Trie
        Trie trie = new Trie();
        trie.insert("apple");
        System.out.println("search apple: " + trie.search("apple"));   // true
        System.out.println("search app: " + trie.search("app"));       // false
        System.out.println("startsWith app: " + trie.startsWith("app")); // true
        trie.insert("app");
        System.out.println("search app after insert: " + trie.search("app")); // true

        // Wildcard search
        System.out.println("search '.pple': " + trie.searchWithWildcard(".pple")); // true

        // Union-Find
        UnionFind uf = new UnionFind(5);
        uf.union(0, 1); uf.union(1, 2); uf.union(3, 4);
        System.out.println("Connected 0-2: " + uf.connected(0, 2)); // true
        System.out.println("Connected 0-3: " + uf.connected(0, 3)); // false
        System.out.println("Components: " + uf.getComponents());     // 2

        // Accounts merge
        List<List<String>> accounts = List.of(
            List.of("John","johnsmith@mail.com","john_newyork@mail.com"),
            List.of("John","johnsmith@mail.com","john00@mail.com"),
            List.of("Mary","mary@mail.com"),
            List.of("John","johnnybravo@mail.com")
        );
        System.out.println("Merged accounts: " + accountsMerge(accounts).size()); // 3

        // Word Search II
        char[][] board = {{'o','a','a','n'},{'e','t','a','e'},{'i','h','k','r'},{'i','f','l','v'}};
        System.out.println("Found words: " + findWords(board, new String[]{"oath","pea","eat","rain"}));
    }
}
