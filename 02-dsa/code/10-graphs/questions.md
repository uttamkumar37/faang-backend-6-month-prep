# Graphs — Practice Questions

---

## 🟢 Easy (5)

### E1. Find if Path Exists in Graph
Given a bi-directional graph and source/destination, return true if a path exists.  
**Hint:** BFS or DFS from source; check if destination is reachable.  
**Complexity:** O(V+E) time, O(V) space.

```java
public boolean validPath(int n, int[][] edges, int source, int destination) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (int[] e : edges) { adj.get(e[0]).add(e[1]); adj.get(e[1]).add(e[0]); }
    boolean[] visited = new boolean[n];
    Queue<Integer> q = new LinkedList<>();
    q.offer(source); visited[source] = true;
    while (!q.isEmpty()) {
        int cur = q.poll();
        if (cur == destination) return true;
        for (int nb : adj.get(cur)) if (!visited[nb]) { visited[nb] = true; q.offer(nb); }
    }
    return false;
}
```

### E2. Find the Town Judge
In a town of n people, the judge trusts nobody but everyone trusts the judge. Find the judge.  
**Hint:** Maintain in-degree and out-degree arrays; judge has in-degree = n-1 and out-degree = 0.  
**Complexity:** O(n + edges) time, O(n) space.

```java
public int findJudge(int n, int[][] trust) {
    int[] inDeg = new int[n + 1], outDeg = new int[n + 1];
    for (int[] t : trust) { outDeg[t[0]]++; inDeg[t[1]]++; }
    for (int i = 1; i <= n; i++) if (inDeg[i] == n - 1 && outDeg[i] == 0) return i;
    return -1;
}
```

### E3. Flood Fill
Perform a flood fill starting from a pixel (change all connected same-color pixels to new color).  
**Hint:** DFS/BFS from starting pixel; spread to 4-directional neighbors with the same original color.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public int[][] floodFill(int[][] image, int sr, int sc, int color) {
    if (image[sr][sc] == color) return image;
    dfs(image, sr, sc, image[sr][sc], color);
    return image;
}
private void dfs(int[][] img, int r, int c, int orig, int newColor) {
    if (r < 0 || r >= img.length || c < 0 || c >= img[0].length || img[r][c] != orig) return;
    img[r][c] = newColor;
    dfs(img, r+1, c, orig, newColor); dfs(img, r-1, c, orig, newColor);
    dfs(img, r, c+1, orig, newColor); dfs(img, r, c-1, orig, newColor);
}
```

### E4. Number of Islands (Basic)
Count distinct islands (groups of adjacent 1s) in a binary grid.  
**Hint:** For each unvisited 1, run DFS/BFS marking visited cells. Count starts.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public int numIslands(char[][] grid) {
    int count = 0;
    for (int r = 0; r < grid.length; r++)
        for (int c = 0; c < grid[0].length; c++)
            if (grid[r][c] == '1') { sink(grid, r, c); count++; }
    return count;
}
private void sink(char[][] g, int r, int c) {
    if (r < 0 || r >= g.length || c < 0 || c >= g[0].length || g[r][c] != '1') return;
    g[r][c] = '0';
    sink(g,r+1,c); sink(g,r-1,c); sink(g,r,c+1); sink(g,r,c-1);
}
```

### E5. Clone Graph
Return a deep copy of a connected undirected graph.  
**Hint:** BFS/DFS with a HashMap mapping original node → cloned node.  
**Complexity:** O(V+E) time, O(V) space.

```java
public Node cloneGraph(Node node) {
    if (node == null) return null;
    Map<Node, Node> map = new HashMap<>();
    Queue<Node> q = new LinkedList<>();
    map.put(node, new Node(node.val));
    q.offer(node);
    while (!q.isEmpty()) {
        Node cur = q.poll();
        for (Node nb : cur.neighbors) {
            if (!map.containsKey(nb)) { map.put(nb, new Node(nb.val)); q.offer(nb); }
            map.get(cur).neighbors.add(map.get(nb));
        }
    }
    return map.get(node);
}
```

---

## 🟡 Medium (10)

### M1. Course Schedule (Cycle Detection)
Given prerequisites, determine if you can finish all courses (detect cycle in directed graph).  
**Hint:** Topological sort (Kahn's or DFS coloring: white/gray/black). Cycle = gray node revisited.  
**Complexity:** O(V+E) time, O(V+E) space.

```java
public boolean canFinish(int numCourses, int[][] prerequisites) {
    int[] inDeg = new int[numCourses];
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < numCourses; i++) adj.add(new ArrayList<>());
    for (int[] p : prerequisites) { adj.get(p[1]).add(p[0]); inDeg[p[0]]++; }
    Queue<Integer> q = new LinkedList<>();
    for (int i = 0; i < numCourses; i++) if (inDeg[i] == 0) q.offer(i);
    int processed = 0;
    while (!q.isEmpty()) {
        int cur = q.poll(); processed++;
        for (int nb : adj.get(cur)) if (--inDeg[nb] == 0) q.offer(nb);
    }
    return processed == numCourses;
}
```

### M2. Number of Connected Components in Undirected Graph
Count connected components in an undirected graph with n nodes.  
**Hint:** Union-Find or BFS/DFS marking visited nodes; count BFS/DFS starts.  
**Complexity:** O(V+E) time, O(V) space.

```java
public int countComponents(int n, int[][] edges) {
    int[] parent = new int[n];
    Arrays.fill(parent, -1);
    for (int[] e : edges) union(parent, e[0], e[1]);
    int count = 0;
    for (int i = 0; i < n; i++) if (parent[i] == -1) count++;
    return count;
}
private void union(int[] parent, int a, int b) {
    int ra = find(parent, a), rb = find(parent, b);
    if (ra != rb) parent[ra] = rb;
}
private int find(int[] parent, int x) {
    if (parent[x] == -1) return x;
    return parent[x] = find(parent, parent[x]);
}
```

### M3. Pacific Atlantic Water Flow
Water flows to Pacific (top/left border) or Atlantic (bottom/right border). Find cells that flow to both.  
**Hint:** Reverse BFS from both borders; return intersection of reachable cells.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public List<List<Integer>> pacificAtlantic(int[][] heights) {
    int m = heights.length, n = heights[0].length;
    boolean[][] pac = new boolean[m][n], atl = new boolean[m][n];
    Queue<int[]> pq = new LinkedList<>(), aq = new LinkedList<>();
    for (int i = 0; i < m; i++) { pq.offer(new int[]{i,0}); pac[i][0]=true; aq.offer(new int[]{i,n-1}); atl[i][n-1]=true; }
    for (int j = 0; j < n; j++) { pq.offer(new int[]{0,j}); pac[0][j]=true; aq.offer(new int[]{m-1,j}); atl[m-1][j]=true; }
    bfsPA(heights, pq, pac); bfsPA(heights, aq, atl);
    List<List<Integer>> res = new ArrayList<>();
    for (int i = 0; i < m; i++) for (int j = 0; j < n; j++) if (pac[i][j] && atl[i][j]) res.add(Arrays.asList(i, j));
    return res;
}
private void bfsPA(int[][] h, Queue<int[]> q, boolean[][] vis) {
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.isEmpty()) {
        int[] c = q.poll();
        for (int[] d : dirs) {
            int r = c[0]+d[0], col = c[1]+d[1];
            if (r>=0 && r<h.length && col>=0 && col<h[0].length && !vis[r][col] && h[r][col]>=h[c[0]][c[1]])
            { vis[r][col]=true; q.offer(new int[]{r,col}); }
        }
    }
}
```

### M4. Rotting Oranges
Every minute, fresh oranges adjacent to rotten ones become rotten. Return minutes until all rot (or -1).  
**Hint:** Multi-source BFS starting from all rotten oranges simultaneously.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public int orangesRotting(int[][] grid) {
    int m = grid.length, n = grid[0].length, fresh = 0, minutes = 0;
    Queue<int[]> q = new LinkedList<>();
    for (int i = 0; i < m; i++) for (int j = 0; j < n; j++) {
        if (grid[i][j] == 2) q.offer(new int[]{i, j});
        else if (grid[i][j] == 1) fresh++;
    }
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.isEmpty() && fresh > 0) {
        minutes++;
        for (int size = q.size(); size > 0; size--) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int r = c[0]+d[0], col = c[1]+d[1];
                if (r>=0 && r<m && col>=0 && col<n && grid[r][col]==1) { grid[r][col]=2; fresh--; q.offer(new int[]{r,col}); }
            }
        }
    }
    return fresh == 0 ? minutes : -1;
}
```

### M5. 01 Matrix
For each cell, find the distance to the nearest 0.  
**Hint:** Multi-source BFS starting from all 0 cells at once; expand outward.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public int[][] updateMatrix(int[][] mat) {
    int m = mat.length, n = mat[0].length;
    int[][] dist = new int[m][n];
    Queue<int[]> q = new LinkedList<>();
    for (int i = 0; i < m; i++) for (int j = 0; j < n; j++) {
        if (mat[i][j] == 0) q.offer(new int[]{i, j});
        else dist[i][j] = Integer.MAX_VALUE;
    }
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.isEmpty()) {
        int[] c = q.poll();
        for (int[] d : dirs) {
            int r = c[0]+d[0], col = c[1]+d[1];
            if (r>=0 && r<m && col>=0 && col<n && dist[r][col] > dist[c[0]][c[1]]+1) {
                dist[r][col] = dist[c[0]][c[1]]+1; q.offer(new int[]{r,col});
            }
        }
    }
    return dist;
}
```

### M6. Walls and Gates
Fill each empty room with the distance to the nearest gate (-1=wall, 0=gate, INF=room).  
**Hint:** Multi-source BFS from all gates simultaneously.  
**Complexity:** O(m*n) time, O(m*n) space.

```java
public void wallsAndGates(int[][] rooms) {
    int m = rooms.length, n = rooms[0].length, INF = Integer.MAX_VALUE;
    Queue<int[]> q = new LinkedList<>();
    for (int i = 0; i < m; i++) for (int j = 0; j < n; j++) if (rooms[i][j] == 0) q.offer(new int[]{i, j});
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.isEmpty()) {
        int[] c = q.poll();
        for (int[] d : dirs) {
            int r = c[0]+d[0], col = c[1]+d[1];
            if (r>=0 && r<m && col>=0 && col<n && rooms[r][col]==INF) { rooms[r][col]=rooms[c[0]][c[1]]+1; q.offer(new int[]{r,col}); }
        }
    }
}
```

### M7. Graph Valid Tree
Determine if n nodes and edges form a valid tree (connected, no cycles).  
**Hint:** Valid tree has exactly n-1 edges and is connected. Use Union-Find or DFS + edge count.  
**Complexity:** O(V+E) time, O(V) space.

```java
public boolean validTree(int n, int[][] edges) {
    if (edges.length != n - 1) return false;
    int[] parent = new int[n];
    Arrays.setAll(parent, i -> i);
    for (int[] e : edges) {
        int ra = find(parent, e[0]), rb = find(parent, e[1]);
        if (ra == rb) return false;
        parent[ra] = rb;
    }
    return true;
}
private int find(int[] parent, int x) {
    if (parent[x] != x) parent[x] = find(parent, parent[x]);
    return parent[x];
}
```

### M8. Minimum Height Trees
Find all roots that minimize tree height for a given undirected tree.  
**Hint:** Iteratively trim leaf nodes (degree 1) until ≤ 2 nodes remain. Those are the answer.  
**Complexity:** O(n) time, O(n) space.

```java
public List<Integer> findMinHeightTrees(int n, int[][] edges) {
    if (n == 1) return List.of(0);
    List<Set<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new HashSet<>());
    for (int[] e : edges) { adj.get(e[0]).add(e[1]); adj.get(e[1]).add(e[0]); }
    Queue<Integer> leaves = new LinkedList<>();
    for (int i = 0; i < n; i++) if (adj.get(i).size() == 1) leaves.offer(i);
    int remaining = n;
    while (remaining > 2) {
        int size = leaves.size(); remaining -= size;
        for (int i = 0; i < size; i++) {
            int leaf = leaves.poll();
            int nb = adj.get(leaf).iterator().next();
            adj.get(nb).remove(leaf);
            if (adj.get(nb).size() == 1) leaves.offer(nb);
        }
    }
    return new ArrayList<>(leaves);
}
```

### M9. Accounts Merge
Merge accounts sharing the same email into one account.  
**Hint:** Union-Find on emails; group emails by root; attach owner name.  
**Complexity:** O(n * k * α(n)) where k = avg emails per account.

```java
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    Map<String, String> parent = new HashMap<>(), owner = new HashMap<>();
    for (List<String> acc : accounts) {
        for (int i = 1; i < acc.size(); i++) {
            parent.putIfAbsent(acc.get(i), acc.get(i));
            owner.put(acc.get(i), acc.get(0));
        }
        for (int i = 2; i < acc.size(); i++) union(parent, acc.get(1), acc.get(i));
    }
    Map<String, TreeSet<String>> groups = new HashMap<>();
    for (String email : parent.keySet()) {
        String root = find(parent, email);
        groups.computeIfAbsent(root, k -> new TreeSet<>()).add(email);
    }
    List<List<String>> res = new ArrayList<>();
    for (Map.Entry<String, TreeSet<String>> e : groups.entrySet()) {
        List<String> acc = new ArrayList<>();
        acc.add(owner.get(e.getKey()));
        acc.addAll(e.getValue());
        res.add(acc);
    }
    return res;
}
private void union(Map<String,String> p, String a, String b) { p.put(find(p,a), find(p,b)); }
private String find(Map<String,String> p, String x) {
    if (!p.get(x).equals(x)) p.put(x, find(p, p.get(x)));
    return p.get(x);
}
```

### M10. Shortest Path in Binary Matrix
Find the shortest clear path (8-directional) from top-left to bottom-right of a binary matrix.  
**Hint:** BFS with 8-directional movement; use distance array to track levels.  
**Complexity:** O(n²) time, O(n²) space.

```java
public int shortestPathBinaryMatrix(int[][] grid) {
    int n = grid.length;
    if (grid[0][0] == 1 || grid[n-1][n-1] == 1) return -1;
    Queue<int[]> q = new LinkedList<>();
    q.offer(new int[]{0, 0, 1}); grid[0][0] = 1;
    int[][] dirs = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
    while (!q.isEmpty()) {
        int[] c = q.poll();
        if (c[0] == n-1 && c[1] == n-1) return c[2];
        for (int[] d : dirs) {
            int r = c[0]+d[0], col = c[1]+d[1];
            if (r>=0 && r<n && col>=0 && col<n && grid[r][col]==0) { grid[r][col]=1; q.offer(new int[]{r,col,c[2]+1}); }
        }
    }
    return -1;
}
```

---

## 🔴 Hard (5)

### H1. Word Ladder
Transform beginWord to endWord one character at a time (each intermediate must be in wordList). Return min steps.  
**Hint:** BFS on word graph; for each word, try all 26*length neighbors. Use HashSet for O(1) lookup.  
**Complexity:** O(M² * N) time where M = word length, N = list size.

```java
public int ladderLength(String beginWord, String endWord, List<String> wordList) {
    Set<String> wordSet = new HashSet<>(wordList);
    if (!wordSet.contains(endWord)) return 0;
    Queue<String> q = new LinkedList<>();
    q.offer(beginWord); int steps = 1;
    while (!q.isEmpty()) {
        for (int size = q.size(); size > 0; size--) {
            char[] w = q.poll().toCharArray();
            for (int i = 0; i < w.length; i++) {
                char orig = w[i];
                for (char c = 'a'; c <= 'z'; c++) {
                    if (c == orig) continue;
                    w[i] = c;
                    String next = new String(w);
                    if (next.equals(endWord)) return steps + 1;
                    if (wordSet.remove(next)) q.offer(next);
                    w[i] = orig;
                }
            }
        }
        steps++;
    }
    return 0;
}
```

### H2. Alien Dictionary
Given sorted words of an alien language, determine the character order.  
**Hint:** Build a directed graph from adjacent word comparisons; topological sort. If cycle, return "".  
**Complexity:** O(C) where C = total characters in all words.

```java
public String alienOrder(String[] words) {
    Map<Character, Set<Character>> adj = new HashMap<>();
    Map<Character, Integer> inDeg = new HashMap<>();
    for (String w : words) for (char c : w.toCharArray()) { adj.putIfAbsent(c, new HashSet<>()); inDeg.putIfAbsent(c, 0); }
    for (int i = 0; i < words.length - 1; i++) {
        String a = words[i], b = words[i+1];
        if (a.length() > b.length() && a.startsWith(b)) return "";
        for (int j = 0; j < Math.min(a.length(), b.length()); j++) {
            if (a.charAt(j) != b.charAt(j)) { if (adj.get(a.charAt(j)).add(b.charAt(j))) inDeg.merge(b.charAt(j), 1, Integer::sum); break; }
        }
    }
    Queue<Character> q = new LinkedList<>();
    for (char c : inDeg.keySet()) if (inDeg.get(c) == 0) q.offer(c);
    StringBuilder sb = new StringBuilder();
    while (!q.isEmpty()) {
        char c = q.poll(); sb.append(c);
        for (char nb : adj.get(c)) if (inDeg.merge(nb, -1, Integer::sum) == 0) q.offer(nb);
    }
    return sb.length() == inDeg.size() ? sb.toString() : "";
}
```

### H3. Reconstruct Itinerary
Given airline tickets, reconstruct the itinerary starting from "JFK" using all tickets.  
**Hint:** Hierholzer's algorithm (Eulerian path); DFS with post-order insertion to result.  
**Complexity:** O(E log E) time (sorting edges).

```java
public List<String> findItinerary(List<List<String>> tickets) {
    Map<String, PriorityQueue<String>> adj = new HashMap<>();
    for (List<String> t : tickets)
        adj.computeIfAbsent(t.get(0), k -> new PriorityQueue<>()).offer(t.get(1));
    LinkedList<String> result = new LinkedList<>();
    dfsItin(adj, "JFK", result);
    return result;
}
private void dfsItin(Map<String, PriorityQueue<String>> adj, String airport, LinkedList<String> result) {
    PriorityQueue<String> dests = adj.get(airport);
    while (dests != null && !dests.isEmpty()) dfsItin(adj, dests.poll(), result);
    result.addFirst(airport);
}
```

### H4. Critical Connections in a Network
Find all bridges (edges whose removal increases number of connected components).  
**Hint:** Tarjan's bridge-finding algorithm using DFS with discovery time and low value arrays.  
**Complexity:** O(V+E) time, O(V+E) space.

```java
private int timer = 0;
public List<List<Integer>> criticalConnections(int n, List<List<Integer>> connections) {
    List<List<Integer>> adj = new ArrayList<>(), res = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (List<Integer> c : connections) { adj.get(c.get(0)).add(c.get(1)); adj.get(c.get(1)).add(c.get(0)); }
    int[] disc = new int[n], low = new int[n];
    Arrays.fill(disc, -1);
    for (int i = 0; i < n; i++) if (disc[i] == -1) dfsBridge(adj, i, -1, disc, low, res);
    return res;
}
private void dfsBridge(List<List<Integer>> adj, int u, int parent, int[] disc, int[] low, List<List<Integer>> res) {
    disc[u] = low[u] = timer++;
    for (int v : adj.get(u)) {
        if (v == parent) continue;
        if (disc[v] == -1) { dfsBridge(adj, v, u, disc, low, res); low[u] = Math.min(low[u], low[v]); if (low[v] > disc[u]) res.add(Arrays.asList(u, v)); }
        else low[u] = Math.min(low[u], disc[v]);
    }
}
```

### H5. Network Delay Time
How long for signal to reach all nodes from source (Dijkstra's)?  
**Hint:** Dijkstra from source using a min-heap; update distances greedily.  
**Complexity:** O(E log V) time, O(V+E) space.

```java
public int networkDelayTime(int[][] times, int n, int k) {
    List<List<int[]>> adj = new ArrayList<>();
    for (int i = 0; i <= n; i++) adj.add(new ArrayList<>());
    for (int[] t : times) adj.get(t[0]).add(new int[]{t[1], t[2]});
    int[] dist = new int[n + 1]; Arrays.fill(dist, Integer.MAX_VALUE);
    dist[k] = 0;
    PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
    pq.offer(new int[]{k, 0});
    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        if (cur[1] > dist[cur[0]]) continue;
        for (int[] nb : adj.get(cur[0])) {
            int newDist = dist[cur[0]] + nb[1];
            if (newDist < dist[nb[0]]) { dist[nb[0]] = newDist; pq.offer(new int[]{nb[0], newDist}); }
        }
    }
    int maxDist = 0;
    for (int i = 1; i <= n; i++) { if (dist[i] == Integer.MAX_VALUE) return -1; maxDist = Math.max(maxDist, dist[i]); }
    return maxDist;
}
```
