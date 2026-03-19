# Trie & Union-Find — Complete Theory (Basic → Advanced)

---

## Part 1: Trie (Prefix Tree)

---

## 1.1 What Is a Trie?

A **Trie** (re**trie**val tree) is a tree data structure used to store strings where each node represents a **character**, and paths from root to marked nodes spell out complete words.

```
Words: "apple", "app", "apt", "bat"

         root
        /    \
       a      b
       |      |
       p      a
      / \     |
     p   t    t
     |   |
     l   (end)
     |
     e
   (end)
 (end at "app")
```

**Key properties:**
- Shared prefixes share nodes → very space-efficient for many similar strings
- All operations (insert, search, startsWith) are **O(L)** where L = word length
- Alphabet size determines branching factor (26 for lowercase English)

---

## 1.2 TrieNode & Trie Implementation

```java
class TrieNode {
    TrieNode[] children = new TrieNode[26];
    boolean isEnd = false;
}

class Trie {
    private TrieNode root = new TrieNode();

    public void insert(String word) {
        TrieNode curr = root;
        for (char c : word.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null) curr.children[idx] = new TrieNode();
            curr = curr.children[idx];
        }
        curr.isEnd = true;
    }

    public boolean search(String word) {
        TrieNode node = traverse(word);
        return node != null && node.isEnd;
    }

    public boolean startsWith(String prefix) {
        return traverse(prefix) != null;
    }

    private TrieNode traverse(String s) {
        TrieNode curr = root;
        for (char c : s.toCharArray()) {
            int idx = c - 'a';
            if (curr.children[idx] == null) return null;
            curr = curr.children[idx];
        }
        return curr;
    }
}
```

---

## 1.3 Word Dictionary with Wildcards (. matches any char)

```java
public boolean searchWithWildcard(String word) {
    return dfs(word, 0, root);
}

private boolean dfs(String word, int i, TrieNode node) {
    if (i == word.length()) return node.isEnd;
    char c = word.charAt(i);
    if (c == '.') {
        for (TrieNode child : node.children)
            if (child != null && dfs(word, i+1, child)) return true;
        return false;
    } else {
        TrieNode child = node.children[c-'a'];
        return child != null && dfs(word, i+1, child);
    }
}
```

---

## 1.4 Word Search II (Find all words in a board)

Use a Trie of the word list + DFS on the board:

```java
public List<String> findWords(char[][] board, String[] words) {
    Trie trie = new Trie();
    for (String w : words) trie.insert(w);
    Set<String> res = new HashSet<>();
    for (int i = 0; i < board.length; i++)
        for (int j = 0; j < board[0].length; j++)
            dfs(board, i, j, trie.root, new StringBuilder(), res);
    return new ArrayList<>(res);
}

private void dfs(char[][] board, int r, int c, TrieNode node, StringBuilder sb, Set<String> res) {
    if (r < 0 || r >= board.length || c < 0 || c >= board[0].length || board[r][c] == '#') return;
    char ch = board[r][c];
    TrieNode next = node.children[ch-'a'];
    if (next == null) return;          // no word starts with this prefix
    sb.append(ch);
    if (next.isEnd) res.add(sb.toString());
    board[r][c] = '#';
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    for (int[] d : dirs) dfs(board, r+d[0], c+d[1], next, sb, res);
    board[r][c] = ch;
    sb.deleteCharAt(sb.length()-1);
}
```

---

## 1.5 Longest Common Prefix

```java
// Insert all words; traverse while only one child and not end-of-word
public String longestCommonPrefix(String[] strs) {
    Trie trie = new Trie();
    for (String s : strs) trie.insert(s);
    StringBuilder sb = new StringBuilder();
    TrieNode curr = trie.root;
    while (true) {
        // count non-null children
        TrieNode next = null; int cnt = 0;
        for (TrieNode child : curr.children) if (child != null) { next = child; cnt++; }
        if (cnt != 1 || curr.isEnd) break;
        sb.append((char)('a' + findChildIndex(curr)));
        curr = next;
    }
    return sb.toString();
}
```

---

## 1.6 XOR Trie (Maximum XOR of Two Numbers)

Store numbers bit by bit (from MSB to LSB) in a trie. For each number, greedily choose the opposite bit at each level to maximise XOR.

```java
class XORTrie {
    int[][] children = new int[65][2];  // [node][bit] = child node index
    int size = 1;

    public void insert(int num) {
        int node = 0;
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1;
            if (children[node][bit] == 0) children[node][bit] = size++;
            node = children[node][bit];
        }
    }

    public int maxXOR(int num) {
        int node = 0, xor = 0;
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1, want = 1 - bit;
            if (children[node][want] != 0) { xor |= (1 << i); node = children[node][want]; }
            else node = children[node][bit];
        }
        return xor;
    }
}
```

---

## 1.7 Auto-Complete / Prefix Counting

Add a `count` field to TrieNode to track how many words share a prefix:

```java
class TrieNode {
    TrieNode[] children = new TrieNode[26];
    boolean isEnd;
    int prefixCount = 0;    // words passing through this node
}

// In insert: increment prefixCount for each node on the path
// countWordsStartingWith(prefix): traverse to prefix node, return node.prefixCount
```

---

## Part 2: Union-Find (Disjoint Set Union — DSU)

---

## 2.1 What Is Union-Find?

A **Union-Find** (DSU) maintains a collection of **disjoint sets** and supports two operations efficiently:

- `find(x)`: which set does x belong to? (returns the set representative / root)
- `union(x, y)`: merge the sets containing x and y

```
Initially: {0} {1} {2} {3} {4}

union(0,1): {0,1} {2} {3} {4}
union(1,2): {0,1,2} {3} {4}
find(0) == find(2)? Yes → same component
```

---

## 2.2 Basic Implementation

```java
class UnionFind {
    int[] parent, rank;
    int components;

    UnionFind(int n) {
        parent = new int[n]; rank = new int[n]; components = n;
        for (int i = 0; i < n; i++) parent[i] = i;
    }

    // Path compression
    public int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]);
        return parent[x];
    }

    // Union by rank
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
```

**Time per operation**: O(α(n)) — inverse Ackermann function, effectively O(1).

---

## 2.3 Number of Connected Components

```java
public int countComponents(int n, int[][] edges) {
    UnionFind uf = new UnionFind(n);
    for (int[] e : edges) uf.union(e[0], e[1]);
    return uf.getComponents();
}
```

---

## 2.4 Redundant Connection (Cycle Detection)

```java
public int[] findRedundantConnection(int[][] edges) {
    UnionFind uf = new UnionFind(edges.length + 1);
    for (int[] e : edges) {
        if (!uf.union(e[0], e[1])) return e;   // already same component → cycle
    }
    return new int[0];
}
```

---

## 2.5 Accounts Merge

```java
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    Map<String, Integer> emailToId = new HashMap<>();
    Map<String, String> emailToName = new HashMap<>();
    int id = 0;
    UnionFind uf = new UnionFind(accounts.size() * 10);  // generous bound
    for (List<String> acc : accounts) {
        String name = acc.get(0);
        for (int i = 1; i < acc.size(); i++) {
            String email = acc.get(i);
            emailToName.put(email, name);
            emailToId.putIfAbsent(email, id++);
            uf.union(emailToId.get(acc.get(1)), emailToId.get(email));
        }
    }
    Map<Integer, List<String>> components = new HashMap<>();
    for (String email : emailToId.keySet()) {
        int root = uf.find(emailToId.get(email));
        components.computeIfAbsent(root, k -> new ArrayList<>()).add(email);
    }
    List<List<String>> res = new ArrayList<>();
    for (List<String> emails : components.values()) {
        Collections.sort(emails);
        emails.add(0, emailToName.get(emails.get(0)));
        res.add(emails);
    }
    return res;
}
```

---

## 2.6 Weighted Union-Find (Relative Weights)

Used for problems like "Evaluate Division" where edges have a ratio:

```java
class WeightedUF {
    int[] parent; double[] weight;  // weight[x] = x / parent[x]
    WeightedUF(int n) { parent = new int[n]; weight = new double[n]; Arrays.fill(weight, 1.0); for(int i=0;i<n;i++) parent[i]=i; }
    int find(int x) {
        if (parent[x] != x) { int root = find(parent[x]); weight[x] *= weight[parent[x]]; parent[x] = root; }
        return parent[x];
    }
    void union(int x, int y, double r) {  // r = weight(x)/weight(y)
        int px = find(x), py = find(y);
        if (px == py) return;
        parent[px] = py; weight[px] = weight[y] * r / weight[x];
    }
}
```

---

## 2.7 Offline Connectivity Queries

For queries like "were x and y connected at time t?", use DSU offline:
Process queries in reverse (add edges in reverse, count disconnections).

---

## Complexity Reference

| Structure | Operation | Time | Space |
|---|---|---|---|
| Trie insert | O(L) | — | O(n × L × 26) |
| Trie search | O(L) | — | — |
| Trie search with wildcard (.) | O(L × 26^dots) | — | — |
| Union-Find (find/union, with optimisations) | O(α(n)) ≈ O(1) | — | O(n) |
| Connected components | O(E × α(V)) | — | O(V) |

---

## Decision Guide

| Signal | Structure |
|---|---|
| Prefix lookup / autocomplete | Trie |
| Word existence / starts-with | Trie |
| Wildcard word match | Trie + DFS |
| Maximum XOR pair | XOR Trie (bit trie) |
| Find words in grid | Trie + backtracking DFS |
| Connected components | Union-Find |
| Cycle detection (undirected) | Union-Find |
| Dynamic connectivity | Union-Find |
| Merge overlapping entities | Union-Find (accounts merge) |

---

## Common Pitfalls

**Trie:**
- **Lowercase only**: `c - 'a'` gives 0-25; use HashMap for arbitrary characters
- **isEnd flag**: don't forget to set `curr.isEnd = true` at insert's last node
- **Wildcard DFS**: iterate all 26 children, don't short-circuit on null

**Union-Find:**
- **Path compression**: use `parent[x] = find(parent[x])` (recursive), not iterative two-pass unless explicitly needed
- **Union by rank vs size**: either works; rank avoids integer overflow; both give O(α(n))
- **0-indexed vs 1-indexed**: be consistent; off-by-one in initialisation causes wrong results
- **Weighted UF multiply order**: weight update must happen BEFORE parent pointer update when compressing
