# Graphs — Complete Theory (Basic → Advanced)

---

## 1. Graph Fundamentals

A graph G = (V, E) consists of **vertices** (nodes) and **edges** (connections).

```
Undirected:           Directed (digraph):
  1 — 2                 1 → 2
  |   |                 ↑   ↓
  3 — 4                 4 ← 3
```

**Key properties:**
| Term | Meaning |
|---|---|
| Degree | Number of edges at a vertex |
| In-degree / Out-degree | Directed graph: edges coming in / going out |
| Connected graph | Path between every pair of vertices |
| Strongly connected | Directed; every vertex reachable from every other |
| DAG | Directed Acyclic Graph — no cycles |
| Weighted graph | Edges carry a numeric cost |
| Sparse graph | E ≈ V (few edges) — favour adjacency list |
| Dense graph | E ≈ V² (many edges) — may favour matrix |

---

## 2. Graph Representations

### Adjacency List — O(V + E) space
```java
// Unweighted
List<List<Integer>> graph = new ArrayList<>();
for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
graph.get(u).add(v);
graph.get(v).add(u);  // undirected

// Weighted [dest, weight]
List<int[]>[] g = new List[n];
for (int i = 0; i < n; i++) g[i] = new ArrayList<>();
g[u].add(new int[]{v, w});
```

### Adjacency Matrix — O(V²) space
```java
int[][] adj = new int[n][n];
adj[u][v] = 1;  // or weight
adj[v][u] = 1;  // undirected
```

### Edge List — O(E) space
```java
int[][] edges = {{0,1,4}, {0,2,1}, ...};  // [u, v, weight]
```

---

## 3. BFS — Level-Order Graph Traversal

```java
// BFS from source, finds shortest path (unweighted)
public int[] bfs(int src, List<List<Integer>> graph, int n) {
    int[] dist = new int[n];
    Arrays.fill(dist, -1);
    Queue<Integer> queue = new LinkedList<>();
    dist[src] = 0;
    queue.offer(src);
    while (!queue.isEmpty()) {
        int node = queue.poll();
        for (int neighbor : graph.get(node)) {
            if (dist[neighbor] == -1) {
                dist[neighbor] = dist[node] + 1;
                queue.offer(neighbor);
            }
        }
    }
    return dist;
}
```

**Use BFS when**: shortest path in unweighted graph, level-order processing, flood fill.

---

## 4. DFS — Depth-First Traversal

```java
// Iterative DFS
boolean[] visited = new boolean[n];
public void dfs(int node, List<List<Integer>> graph) {
    Deque<Integer> stack = new ArrayDeque<>();
    stack.push(node);
    while (!stack.isEmpty()) {
        int curr = stack.pop();
        if (visited[curr]) continue;
        visited[curr] = true;
        for (int neighbor : graph.get(curr)) {
            if (!visited[neighbor]) stack.push(neighbor);
        }
    }
}

// Recursive DFS
public void dfsRec(int node, List<List<Integer>> graph, boolean[] visited) {
    visited[node] = true;
    for (int neighbor : graph.get(node))
        if (!visited[neighbor]) dfsRec(neighbor, graph, visited);
}
```

**Use DFS when**: cycle detection, topological sort, connected components, path existence.

---

## 5. Number of Islands (Grid BFS/DFS)

```java
public int numIslands(char[][] grid) {
    int count = 0;
    for (int i = 0; i < grid.length; i++)
        for (int j = 0; j < grid[0].length; j++)
            if (grid[i][j] == '1') { bfsMark(grid, i, j); count++; }
    return count;
}

private void bfsMark(char[][] grid, int r, int c) {
    Queue<int[]> q = new LinkedList<>();
    grid[r][c] = '0';
    q.offer(new int[]{r, c});
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    while (!q.isEmpty()) {
        int[] pos = q.poll();
        for (int[] d : dirs) {
            int nr = pos[0]+d[0], nc = pos[1]+d[1];
            if (nr >= 0 && nr < grid.length && nc >= 0 && nc < grid[0].length && grid[nr][nc] == '1') {
                grid[nr][nc] = '0';
                q.offer(new int[]{nr, nc});
            }
        }
    }
}
```

---

## 6. Topological Sort

Only valid for **DAGs**. Gives a linear ordering where every directed edge `u → v` has `u` before `v`.

### Kahn's Algorithm (BFS-based) — O(V + E)
```java
public int[] topoSort(int n, int[][] edges) {
    List<List<Integer>> graph = new ArrayList<>();
    int[] inDegree = new int[n];
    for (int i = 0; i < n; i++) graph.add(new ArrayList<>());
    for (int[] e : edges) { graph.get(e[0]).add(e[1]); inDegree[e[1]]++; }

    Queue<Integer> queue = new LinkedList<>();
    for (int i = 0; i < n; i++) if (inDegree[i] == 0) queue.offer(i);
    int[] order = new int[n]; int idx = 0;
    while (!queue.isEmpty()) {
        int node = queue.poll();
        order[idx++] = node;
        for (int neighbor : graph.get(node))
            if (--inDegree[neighbor] == 0) queue.offer(neighbor);
    }
    return idx == n ? order : new int[0]; // empty if cycle detected
}
```

### DFS-based Topological Sort
```java
// Push to stack after visiting all children; reverse stack = topological order
Deque<Integer> stack = new ArrayDeque<>();
boolean[] visited = new boolean[n], onStack = new boolean[n];

public void topoSortDFS(int node) {
    visited[node] = true;
    onStack[node] = true;
    for (int neighbor : graph.get(node)) {
        if (!visited[neighbor]) topoSortDFS(neighbor);
        else if (onStack[neighbor]) throw new RuntimeException("cycle detected");
    }
    onStack[node] = false;
    stack.push(node);
}
```

---

## 7. Dijkstra's Shortest Path — O((V + E) log V)

Works for **non-negative** weighted graphs.

```java
public int[] dijkstra(int src, List<int[]>[] graph, int n) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;
    PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
    pq.offer(new int[]{src, 0});
    while (!pq.isEmpty()) {
        int[] curr = pq.poll();
        int node = curr[0], d = curr[1];
        if (d > dist[node]) continue;      // stale
        for (int[] edge : graph[node]) {
            int newDist = dist[node] + edge[1];
            if (newDist < dist[edge[0]]) {
                dist[edge[0]] = newDist;
                pq.offer(new int[]{edge[0], newDist});
            }
        }
    }
    return dist;
}
```

---

## 8. Bellman-Ford — O(V × E)

Handles **negative edges** (but not negative cycles). Can detect negative cycles.

```java
public int[] bellmanFord(int src, int n, int[][] edges) {
    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[src] = 0;
    for (int i = 0; i < n - 1; i++) {            // n−1 relaxations
        for (int[] e : edges) {
            if (dist[e[0]] != Integer.MAX_VALUE && dist[e[0]] + e[2] < dist[e[1]])
                dist[e[1]] = dist[e[0]] + e[2];
        }
    }
    // Check for negative cycle
    for (int[] e : edges)
        if (dist[e[0]] != Integer.MAX_VALUE && dist[e[0]] + e[2] < dist[e[1]])
            throw new RuntimeException("Negative cycle detected");
    return dist;
}
```

---

## 9. Floyd-Warshall — All-Pairs Shortest Path — O(V³)

```java
public int[][] floydWarshall(int[][] dist) {
    int n = dist.length;
    for (int k = 0; k < n; k++)          // intermediate vertex
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (dist[i][k] != Integer.MAX_VALUE && dist[k][j] != Integer.MAX_VALUE)
                    dist[i][j] = Math.min(dist[i][j], dist[i][k] + dist[k][j]);
    return dist;
}
```

---

## 10. Minimum Spanning Tree

### Kruskal's — O(E log E)
Sort edges by weight, add greedily if no cycle (use Union-Find):
```java
public int kruskalMST(int n, int[][] edges) {
    Arrays.sort(edges, Comparator.comparingInt(e -> e[2]));
    int[] parent = new int[n]; Arrays.fill(parent, -1);
    int cost = 0, edgeCount = 0;
    for (int[] e : edges) {
        int pu = find(parent, e[0]), pv = find(parent, e[1]);
        if (pu != pv) {
            union(parent, pu, pv);
            cost += e[2]; edgeCount++;
            if (edgeCount == n - 1) break;
        }
    }
    return cost;
}
```

### Prim's — O((V + E) log V) with heap
```java
// Start from vertex 0, always pick minimum weight edge to unvisited vertex
boolean[] inMST = new boolean[n];
PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
pq.offer(new int[]{0, 0}); // [vertex, weight]
int cost = 0;
while (!pq.isEmpty()) {
    int[] curr = pq.poll();
    if (inMST[curr[0]]) continue;
    inMST[curr[0]] = true;
    cost += curr[1];
    for (int[] edge : graph[curr[0]])
        if (!inMST[edge[0]]) pq.offer(new int[]{edge[0], edge[1]});
}
```

---

## 11. Union-Find (Disjoint Set Union)

```java
class UnionFind {
    int[] parent, rank;

    UnionFind(int n) {
        parent = new int[n];
        rank   = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
    }

    public int find(int x) {
        if (parent[x] != x) parent[x] = find(parent[x]);  // path compression
        return parent[x];
    }

    public boolean union(int x, int y) {
        int px = find(x), py = find(y);
        if (px == py) return false;                         // already same set
        if (rank[px] < rank[py]) { int t = px; px = py; py = t; }
        parent[py] = px;
        if (rank[px] == rank[py]) rank[px]++;
        return true;
    }
}
```

With path compression + union by rank: **O(α(n))** per operation (nearly O(1)).

---

## 12. Cycle Detection

### Undirected Graph — DFS with parent tracking
```java
public boolean hasCycle(int n, List<List<Integer>> graph) {
    boolean[] visited = new boolean[n];
    for (int i = 0; i < n; i++)
        if (!visited[i] && dfsCycle(i, -1, graph, visited)) return true;
    return false;
}
private boolean dfsCycle(int node, int parent, List<List<Integer>> graph, boolean[] visited) {
    visited[node] = true;
    for (int neighbor : graph.get(node))
        if (!visited[neighbor]) { if (dfsCycle(neighbor, node, graph, visited)) return true; }
        else if (neighbor != parent) return true;          // back edge
    return false;
}
```

### Directed Graph — 3-color DFS (white=0, grey=1, black=2)
```java
int[] color = new int[n]; // 0=unvisited, 1=in-stack, 2=done
public boolean hasCycleDirected(int node) {
    color[node] = 1;
    for (int neighbor : graph.get(node)) {
        if (color[neighbor] == 1) return true;          // back edge → cycle
        if (color[neighbor] == 0 && hasCycleDirected(neighbor)) return true;
    }
    color[node] = 2;
    return false;
}
```

---

## 13. Tarjan's — Strongly Connected Components (SCC)

Finds all maximal sets of vertices where each vertex is reachable from every other.

```java
int[] id = new int[n], low = new int[n]; int timer = 0;
boolean[] onStack = new boolean[n];
Deque<Integer> stack = new ArrayDeque<>();
List<List<Integer>> sccs = new ArrayList<>();

public void tarjan(int node) {
    id[node] = low[node] = timer++;
    stack.push(node); onStack[node] = true;
    for (int neighbor : graph.get(node)) {
        if (id[neighbor] == -1) { tarjan(neighbor); low[node] = Math.min(low[node], low[neighbor]); }
        else if (onStack[neighbor]) low[node] = Math.min(low[node], id[neighbor]);
    }
    if (low[node] == id[node]) {           // node is root of SCC
        List<Integer> scc = new ArrayList<>();
        while (true) { int w = stack.pop(); onStack[w] = false; scc.add(w); if (w == node) break; }
        sccs.add(scc);
    }
}
```

---

## 14. Bridges & Articulation Points

A **bridge** is an edge whose removal disconnects the graph.
```java
// Bridge detection with low-link values
public void dfs(int u, int parent, List<List<Integer>> g, int[] disc, int[] low, boolean[] visited, List<int[]> bridges) {
    disc[u] = low[u] = timer++;
    visited[u] = true;
    for (int v : g.get(u)) {
        if (!visited[v]) {
            dfs(v, u, g, disc, low, visited, bridges);
            low[u] = Math.min(low[u], low[v]);
            if (low[v] > disc[u]) bridges.add(new int[]{u, v});
        } else if (v != parent) {
            low[u] = Math.min(low[u], disc[v]);
        }
    }
}
```

---

## 15. Complexity Reference

| Algorithm | Time | Space |
|---|---|---|
| BFS / DFS | O(V + E) | O(V) |
| Topological sort | O(V + E) | O(V) |
| Dijkstra (min-heap) | O((V+E) log V) | O(V) |
| Bellman-Ford | O(V × E) | O(V) |
| Floyd-Warshall | O(V³) | O(V²) |
| Kruskal | O(E log E) | O(V) |
| Prim (heap) | O((V+E) log V) | O(V) |
| Union-Find (with opt) | O(α(n)) per op | O(V) |
| Tarjan SCC | O(V + E) | O(V) |

---

## 16. Decision Guide

| Problem | Algorithm |
|---|---|
| Shortest path, unweighted | BFS |
| Shortest path, non-negative weights | Dijkstra |
| Shortest path, negative weights | Bellman-Ford |
| All-pairs shortest paths | Floyd-Warshall |
| Task ordering, prerequisites | Topological sort (Kahn's / DFS) |
| Minimum spanning tree | Kruskal or Prim |
| Connected components / cycle | Union-Find or DFS |
| Strongly connected components | Tarjan or Kosaraju |
| Bridges / articulation points | Low-link DFS |

---

## 17. Common Pitfalls

- **Visited tracking**: always mark visited BEFORE adding to queue (BFS), not after polling
- **Directed vs undirected cycle**: undirected cycles need parent tracking; directed need grey coloring
- **Dijkstra with negative edges**: will give wrong answer — use Bellman-Ford
- **Topological sort on cyclic graph**: Kahn's returns fewer than n nodes; check this
- **Union-Find path compression**: `parent[x] = find(parent[x])` — not `parent[x]++`
- **Grid BFS**: mark cell as visited before enqueuing, or you'll enqueue the same cell multiple times
- **Multiple components**: outer loop over all nodes for BFS/DFS to handle disconnected graphs
