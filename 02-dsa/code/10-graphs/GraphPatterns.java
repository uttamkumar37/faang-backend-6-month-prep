package dsa.graphs;

import java.util.*;

/**
 * Graph patterns: BFS, DFS, topological sort, Dijkstra, Union-Find, grid BFS.
 */
public class GraphPatterns {

    // ─── 1. BFS — Shortest Path Unweighted ───────────────────────────────────────

    static int[] bfsShortestPath(List<List<Integer>> adj, int start, int n) {
        int[] dist = new int[n];
        Arrays.fill(dist, -1);
        dist[start] = 0;
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(start);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : adj.get(node)) {
                if (dist[neighbor] == -1) {
                    dist[neighbor] = dist[node] + 1;
                    queue.offer(neighbor);
                }
            }
        }
        return dist;
    }

    // ─── 2. NUMBER OF ISLANDS (DFS on grid) ──────────────────────────────────────

    static int numIslands(char[][] grid) {
        int count = 0;
        for (int i = 0; i < grid.length; i++)
            for (int j = 0; j < grid[0].length; j++)
                if (grid[i][j] == '1') { dfsIsland(grid, i, j); count++; }
        return count;
    }

    private static void dfsIsland(char[][] grid, int r, int c) {
        if (r < 0 || r >= grid.length || c < 0 || c >= grid[0].length || grid[r][c] != '1') return;
        grid[r][c] = '0';
        dfsIsland(grid, r+1, c); dfsIsland(grid, r-1, c);
        dfsIsland(grid, r, c+1); dfsIsland(grid, r, c-1);
    }

    // ─── 3. TOPOLOGICAL SORT (Kahn's BFS) ────────────────────────────────────────

    static int[] topoSort(int n, int[][] prerequisites) {
        List<List<Integer>> adj = new ArrayList<>();
        int[] inDegree = new int[n];
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int[] pre : prerequisites) {
            adj.get(pre[1]).add(pre[0]);
            inDegree[pre[0]]++;
        }

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) if (inDegree[i] == 0) queue.offer(i);

        int[] order = new int[n];
        int idx = 0;
        while (!queue.isEmpty()) {
            int node = queue.poll();
            order[idx++] = node;
            for (int neighbor : adj.get(node))
                if (--inDegree[neighbor] == 0) queue.offer(neighbor);
        }
        return idx == n ? order : new int[]{}; // empty = cycle
    }

    // LeetCode 207 — Course Schedule
    static boolean canFinish(int numCourses, int[][] prerequisites) {
        return topoSort(numCourses, prerequisites).length == numCourses;
    }

    // ─── 4. DIJKSTRA ─────────────────────────────────────────────────────────────

    static int[] dijkstra(int n, int[][] edges, int src) {
        List<List<int[]>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int[] e : edges) {
            adj.get(e[0]).add(new int[]{e[1], e[2]});
            adj.get(e[1]).add(new int[]{e[0], e[2]});
        }

        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        dist[src] = 0;

        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.offer(new int[]{src, 0});

        while (!pq.isEmpty()) {
            int[] curr = pq.poll();
            int node = curr[0], d = curr[1];
            if (d > dist[node]) continue;
            for (int[] neighbor : adj.get(node)) {
                int next = neighbor[0], w = neighbor[1];
                if (dist[node] + w < dist[next]) {
                    dist[next] = dist[node] + w;
                    pq.offer(new int[]{next, dist[next]});
                }
            }
        }
        return dist;
    }

    // ─── 5. UNION-FIND ───────────────────────────────────────────────────────────

    static class UnionFind {
        private final int[] parent, rank;
        private int components;

        UnionFind(int n) {
            parent = new int[n]; rank = new int[n]; components = n;
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }

        boolean union(int x, int y) {
            int px = find(x), py = find(y);
            if (px == py) return false;
            if (rank[px] < rank[py]) { int tmp = px; px = py; py = tmp; }
            parent[py] = px;
            if (rank[px] == rank[py]) rank[px]++;
            components--;
            return true;
        }

        int getComponents() { return components; }
    }

    // LeetCode 684 — Redundant Connection (Union-Find to find cycle)
    static int[] findRedundantConnection(int[][] edges) {
        int n = edges.length;
        UnionFind uf = new UnionFind(n + 1);
        for (int[] edge : edges) {
            if (!uf.union(edge[0], edge[1])) return edge;
        }
        return new int[]{};
    }

    // ─── 6. MULTI-SOURCE BFS (Rotting Oranges) ───────────────────────────────────

    static int orangesRotting(int[][] grid) {
        int m = grid.length, n = grid[0].length;
        Queue<int[]> queue = new LinkedList<>();
        int fresh = 0;
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 2) queue.offer(new int[]{i, j});
                else if (grid[i][j] == 1) fresh++;
            }

        int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
        int minutes = 0;
        while (!queue.isEmpty() && fresh > 0) {
            minutes++;
            for (int size = queue.size(); size > 0; size--) {
                int[] cell = queue.poll();
                for (int[] d : dirs) {
                    int r = cell[0]+d[0], c = cell[1]+d[1];
                    if (r >= 0 && r < m && c >= 0 && c < n && grid[r][c] == 1) {
                        grid[r][c] = 2; fresh--; queue.offer(new int[]{r, c});
                    }
                }
            }
        }
        return fresh == 0 ? minutes : -1;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // BFS shortest path
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < 5; i++) adj.add(new ArrayList<>());
        adj.get(0).add(1); adj.get(1).add(0);
        adj.get(0).add(2); adj.get(2).add(0);
        adj.get(1).add(3); adj.get(3).add(1);
        adj.get(2).add(4); adj.get(4).add(2);
        System.out.println("BFS from 0: " + Arrays.toString(bfsShortestPath(adj, 0, 5)));

        // Islands
        char[][] grid = {
            {'1','1','0','0','0'},
            {'1','1','0','0','0'},
            {'0','0','1','0','0'},
            {'0','0','0','1','1'}
        };
        System.out.println("Num islands: " + numIslands(grid)); // 3

        // Course schedule
        System.out.println("Can finish: " + canFinish(2, new int[][]{{1,0}})); // true
        System.out.println("Can finish (cycle): " + canFinish(2, new int[][]{{1,0},{0,1}})); // false

        // Dijkstra
        int[][] edges = {{0,1,4},{0,2,1},{2,1,2},{1,3,1},{2,3,5}};
        System.out.println("Dijkstra from 0: " + Arrays.toString(dijkstra(4, edges, 0)));

        // Redundant connection
        System.out.println("Redundant edge: " + Arrays.toString(findRedundantConnection(new int[][]{{1,2},{1,3},{2,3}})));

        // Rotting oranges
        int[][] oranges = {{2,1,1},{1,1,0},{0,1,1}};
        System.out.println("Rotting oranges: " + orangesRotting(oranges)); // 4
    }
}
