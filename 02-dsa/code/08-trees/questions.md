# Trees & BST — Practice Questions

---

## 🟢 Easy (5)

### E1. Maximum Depth of Binary Tree
Return the height of a binary tree.  
**Hint:** `1 + max(depth(left), depth(right))`; base case: null → 0.  
**Complexity:** O(n) time, O(h) space.

```java
public int maxDepth(TreeNode root) {
    if (root == null) return 0;
    return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
}
```

### E2. Invert Binary Tree
Swap left and right children at every node.  
**Hint:** Swap children at each node, recurse on both subtrees.  
**Complexity:** O(n) time, O(h) space.

```java
public TreeNode invertTree(TreeNode root) {
    if (root == null) return null;
    TreeNode tmp = root.left;
    root.left = invertTree(root.right);
    root.right = invertTree(tmp);
    return root;
}
```

### E3. Symmetric Tree
Check if a binary tree is a mirror of itself.  
**Hint:** Recursive isMirror(left, right): left.val == right.val && isMirror(left.left, right.right) && isMirror(left.right, right.left).  
**Complexity:** O(n) time, O(h) space.

```java
public boolean isSymmetric(TreeNode root) {
    return isMirror(root.left, root.right);
}
private boolean isMirror(TreeNode a, TreeNode b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.val == b.val && isMirror(a.left, b.right) && isMirror(a.right, b.left);
}
```

### E4. Path Sum
Check if there is a root-to-leaf path with a given sum.  
**Hint:** Subtract node value from target; at leaf, check if remaining == 0.  
**Complexity:** O(n) time, O(h) space.

```java
public boolean hasPathSum(TreeNode root, int targetSum) {
    if (root == null) return false;
    if (root.left == null && root.right == null) return targetSum == root.val;
    return hasPathSum(root.left, targetSum - root.val) || hasPathSum(root.right, targetSum - root.val);
}
```

### E5. Diameter of Binary Tree
Find the longest path between any two nodes (may not pass through root).  
**Hint:** At each node, diameter candidate = left depth + right depth. Track global max.  
**Complexity:** O(n) time, O(h) space.

```java
private int maxDiam = 0;
public int diameterOfBinaryTree(TreeNode root) {
    depth(root); return maxDiam;
}
private int depth(TreeNode node) {
    if (node == null) return 0;
    int l = depth(node.left), r = depth(node.right);
    maxDiam = Math.max(maxDiam, l + r);
    return 1 + Math.max(l, r);
}
```

---

## 🟡 Medium (10)

### M1. Binary Tree Level Order Traversal
Return nodes grouped by level (BFS).  
**Hint:** Queue-based BFS; record queue size at start of each level.  
**Complexity:** O(n) time, O(n) space.

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> q = new LinkedList<>();
    q.offer(root);
    while (!q.isEmpty()) {
        int size = q.size();
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TreeNode node = q.poll();
            level.add(node.val);
            if (node.left  != null) q.offer(node.left);
            if (node.right != null) q.offer(node.right);
        }
        res.add(level);
    }
    return res;
}
```

### M2. Lowest Common Ancestor of a BST
Find LCA in a BST.  
**Hint:** If both values < root, go left; if both > root, go right; else root is LCA.  
**Complexity:** O(h) time, O(1) space.

```java
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    while (root != null) {
        if (p.val < root.val && q.val < root.val) root = root.left;
        else if (p.val > root.val && q.val > root.val) root = root.right;
        else return root;
    }
    return null;
}
```

### M3. Validate Binary Search Tree
Determine if a binary tree is a valid BST.  
**Hint:** Pass min/max bounds down the tree; each node's value must be in (min, max).  
**Complexity:** O(n) time, O(h) space.

```java
public boolean isValidBST(TreeNode root) {
    return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
}
private boolean validate(TreeNode node, long min, long max) {
    if (node == null) return true;
    if (node.val <= min || node.val >= max) return false;
    return validate(node.left, min, node.val) && validate(node.right, node.val, max);
}
```

### M4. Kth Smallest Element in a BST
Find the k-th smallest element using in-order traversal.  
**Hint:** In-order traversal of BST yields sorted values; decrement k, return when k==0.  
**Complexity:** O(h+k) time, O(h) space.

```java
private int count, result;
public int kthSmallest(TreeNode root, int k) {
    count = k; inorder(root); return result;
}
private void inorder(TreeNode node) {
    if (node == null) return;
    inorder(node.left);
    if (--count == 0) result = node.val;
    inorder(node.right);
}
```

### M5. Construct Binary Tree from Preorder and Inorder Traversal
Rebuild the tree from its traversal arrays.  
**Hint:** Preorder[0] is root; find its index in inorder to split left/right subtrees. Use a HashMap for O(1) lookup.  
**Complexity:** O(n) time, O(n) space.

```java
public TreeNode buildTree(int[] preorder, int[] inorder) {
    Map<Integer,Integer> idx = new HashMap<>();
    for (int i = 0; i < inorder.length; i++) idx.put(inorder[i], i);
    return build(preorder, 0, preorder.length-1, 0, inorder.length-1, idx);
}
private int[] pre;
private TreeNode build(int[] pre, int pL, int pR, int iL, int iR, Map<Integer,Integer> idx) {
    if (pL > pR) return null;
    TreeNode root = new TreeNode(pre[pL]);
    int m = idx.get(pre[pL]);
    int leftSize = m - iL;
    root.left  = build(pre, pL+1, pL+leftSize, iL, m-1, idx);
    root.right = build(pre, pL+leftSize+1, pR, m+1, iR, idx);
    return root;
}
```

### M6. Binary Tree Right Side View
Return the last node visible from the right at each level.  
**Hint:** BFS; last node in each level's queue snapshot is the right-side view.  
**Complexity:** O(n) time, O(n) space.

```java
public List<Integer> rightSideView(TreeNode root) {
    List<Integer> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> q = new LinkedList<>();
    q.offer(root);
    while (!q.isEmpty()) {
        int size = q.size(); TreeNode last = null;
        for (int i = 0; i < size; i++) {
            last = q.poll();
            if (last.left  != null) q.offer(last.left);
            if (last.right != null) q.offer(last.right);
        }
        res.add(last.val);
    }
    return res;
}
```

### M7. Path Sum II
Return all root-to-leaf paths that sum to target.  
**Hint:** DFS with backtracking; add path to result when leaf is reached with remaining == 0.  
**Complexity:** O(n²) worst case (copying paths), O(h) stack space.

```java
public List<List<Integer>> pathSum(TreeNode root, int target) {
    List<List<Integer>> res = new ArrayList<>();
    dfs(root, target, new ArrayList<>(), res);
    return res;
}
private void dfs(TreeNode node, int rem, List<Integer> path, List<List<Integer>> res) {
    if (node == null) return;
    path.add(node.val);
    if (node.left == null && node.right == null && rem == node.val) res.add(new ArrayList<>(path));
    dfs(node.left,  rem - node.val, path, res);
    dfs(node.right, rem - node.val, path, res);
    path.removeLast();
}
```

### M8. Count Good Nodes in Binary Tree
Count nodes where no node on root-to-node path has a greater value.  
**Hint:** DFS passing the max value seen so far; a node is "good" if its value >= that max.  
**Complexity:** O(n) time, O(h) space.

```java
public int goodNodes(TreeNode root) {
    return countGood(root, Integer.MIN_VALUE);
}
private int countGood(TreeNode node, int maxSoFar) {
    if (node == null) return 0;
    int good = (node.val >= maxSoFar) ? 1 : 0;
    int newMax = Math.max(maxSoFar, node.val);
    return good + countGood(node.left, newMax) + countGood(node.right, newMax);
}
```

### M9. Binary Tree Zigzag Level Order Traversal
Level order traversal that alternates left-to-right and right-to-left.  
**Hint:** BFS; for odd levels, add to front of deque (or reverse the level's list).  
**Complexity:** O(n) time, O(n) space.

```java
public List<List<Integer>> zigzagLevelOrder(TreeNode root) {
    List<List<Integer>> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> q = new LinkedList<>();
    q.offer(root); boolean leftToRight = true;
    while (!q.isEmpty()) {
        int size = q.size();
        LinkedList<Integer> level = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            TreeNode node = q.poll();
            if (leftToRight) level.addLast(node.val); else level.addFirst(node.val);
            if (node.left  != null) q.offer(node.left);
            if (node.right != null) q.offer(node.right);
        }
        res.add(level); leftToRight = !leftToRight;
    }
    return res;
}
```

### M10. Flatten Binary Tree to Linked List
Flatten into a linked list in-place (using right pointers, in pre-order).  
**Hint:** "Morris" approach: find rightmost node of left subtree; connect it to right subtree; move left to right.  
**Complexity:** O(n) time, O(1) space.

```java
public void flatten(TreeNode root) {
    TreeNode cur = root;
    while (cur != null) {
        if (cur.left != null) {
            TreeNode rightmost = cur.left;
            while (rightmost.right != null) rightmost = rightmost.right;
            rightmost.right = cur.right;
            cur.right = cur.left;
            cur.left = null;
        }
        cur = cur.right;
    }
}
```

---

## 🔴 Hard (5)

### H1. Binary Tree Maximum Path Sum
Find the maximum path sum between any two nodes.  
**Hint:** At each node, max gain from left/right (ignore negatives). Global max = node + leftGain + rightGain.  
**Complexity:** O(n) time, O(h) space.

```java
private int maxSum = Integer.MIN_VALUE;
public int maxPathSum(TreeNode root) {
    gain(root); return maxSum;
}
private int gain(TreeNode node) {
    if (node == null) return 0;
    int leftGain  = Math.max(0, gain(node.left));
    int rightGain = Math.max(0, gain(node.right));
    maxSum = Math.max(maxSum, node.val + leftGain + rightGain);
    return node.val + Math.max(leftGain, rightGain);
}
```

### H2. Recover Binary Search Tree
Two nodes of a BST are swapped; recover without changing the structure.  
**Hint:** In-order traversal — find two "violations" (first desc pair first node, last desc pair's second node); swap their values.  
**Complexity:** O(n) time, O(1) with Morris traversal.

```java
private TreeNode first, second, prev;
public void recoverTree(TreeNode root) {
    inorder(root);
    int tmp = first.val; first.val = second.val; second.val = tmp;
}
private void inorder(TreeNode node) {
    if (node == null) return;
    inorder(node.left);
    if (prev != null && prev.val > node.val) {
        if (first == null) first = prev;
        second = node;
    }
    prev = node;
    inorder(node.right);
}
```

### H3. Serialize and Deserialize Binary Tree
Design an algorithm to serialize and deserialize a binary tree.  
**Hint:** Pre-order serialization with null markers. Parse the string back using a queue/index pointer.  
**Complexity:** O(n) time, O(n) space.

```java
public String serialize(TreeNode root) {
    if (root == null) return "null,";
    return root.val + "," + serialize(root.left) + serialize(root.right);
}
public TreeNode deserialize(String data) {
    Queue<String> q = new LinkedList<>(Arrays.asList(data.split(",")));
    return buildDeser(q);
}
private TreeNode buildDeser(Queue<String> q) {
    String val = q.poll();
    if ("null".equals(val)) return null;
    TreeNode node = new TreeNode(Integer.parseInt(val));
    node.left  = buildDeser(q);
    node.right = buildDeser(q);
    return node;
}
```

### H4. Vertical Order Traversal of Binary Tree
Return nodes grouped by vertical column and sorted by (row, value) within each column.  
**Hint:** DFS tracking (col, row, value); sort entries; group by column.  
**Complexity:** O(n log n) time, O(n) space.

```java
public List<List<Integer>> verticalTraversal(TreeNode root) {
    List<int[]> nodes = new ArrayList<>(); // [col, row, val]
    dfsVert(root, 0, 0, nodes);
    nodes.sort((a, b) -> a[0] != b[0] ? a[0]-b[0] : a[1] != b[1] ? a[1]-b[1] : a[2]-b[2]);
    List<List<Integer>> res = new ArrayList<>();
    int prevCol = Integer.MIN_VALUE;
    for (int[] n : nodes) {
        if (n[0] != prevCol) { res.add(new ArrayList<>()); prevCol = n[0]; }
        res.getLast().add(n[2]);
    }
    return res;
}
private void dfsVert(TreeNode node, int col, int row, List<int[]> nodes) {
    if (node == null) return;
    nodes.add(new int[]{col, row, node.val});
    dfsVert(node.left,  col-1, row+1, nodes);
    dfsVert(node.right, col+1, row+1, nodes);
}
```

### H5. Binary Tree Cameras
Install cameras on some nodes; every node must be monitored. Find minimum cameras.  
**Hint:** Greedy DFS bottom-up; 3 states: not-covered, has-camera, covered. Place camera at a parent when a child is not covered.  
**Complexity:** O(n) time, O(h) space.

```java
private int cameras = 0;
public int minCameraCover(TreeNode root) {
    return (dfsCamera(root) == 0 ? 1 : 0) + cameras;
}
// returns: 0=not-covered, 1=has-camera, 2=covered
private int dfsCamera(TreeNode node) {
    if (node == null) return 2;
    int left = dfsCamera(node.left), right = dfsCamera(node.right);
    if (left == 0 || right == 0) { cameras++; return 1; }
    if (left == 1 || right == 1) return 2;
    return 0;
}
```

---

## 🟡 Medium (10)

### M1. Binary Tree Level Order Traversal
Return nodes grouped by level (BFS).  
**Hint:** Queue-based BFS; record queue size at start of each level.  
**Complexity:** O(n) time, O(n) space.

### M2. Lowest Common Ancestor of a BST
Find LCA in a BST.  
**Hint:** If both values < root, go left; if both > root, go right; else root is LCA.  
**Complexity:** O(h) time, O(1) space.

### M3. Validate Binary Search Tree
Determine if a binary tree is a valid BST.  
**Hint:** Pass min/max bounds down the tree; each node's value must be in (min, max).  
**Complexity:** O(n) time, O(h) space.

### M4. Kth Smallest Element in a BST
Find the k-th smallest element using in-order traversal.  
**Hint:** In-order traversal of BST yields sorted values; decrement k, return when k==0.  
**Complexity:** O(h+k) time, O(h) space.

### M5. Construct Binary Tree from Preorder and Inorder Traversal
Rebuild the tree from its traversal arrays.  
**Hint:** Preorder[0] is root; find its index in inorder to split left/right subtrees. Use a HashMap for O(1) lookup.  
**Complexity:** O(n) time, O(n) space.

### M6. Binary Tree Right Side View
Return the last node visible from the right at each level.  
**Hint:** BFS; last node in each level's queue snapshot is the right-side view.  
**Complexity:** O(n) time, O(n) space.

### M7. Path Sum II
Return all root-to-leaf paths that sum to target.  
**Hint:** DFS with backtracking; add path to result when leaf is reached with remaining == 0.  
**Complexity:** O(n²) worst case (copying paths), O(h) stack space.

### M8. Count Good Nodes in Binary Tree
Count nodes where no node on root-to-node path has a greater value.  
**Hint:** DFS passing the max value seen so far; a node is "good" if its value >= that max.  
**Complexity:** O(n) time, O(h) space.

### M9. Binary Tree Zigzag Level Order Traversal
Level order traversal that alternates left-to-right and right-to-left.  
**Hint:** BFS; for odd levels, add to front of deque (or reverse the level's list).  
**Complexity:** O(n) time, O(n) space.

### M10. Flatten Binary Tree to Linked List
Flatten into a linked list in-place (using right pointers, in pre-order).  
**Hint:** "Morris" approach: find rightmost node of left subtree; connect it to right subtree; move left to right.  
**Complexity:** O(n) time, O(1) space.

---

## 🔴 Hard (5)

### H1. Binary Tree Maximum Path Sum
Find the maximum path sum between any two nodes.  
**Hint:** At each node, max gain from left/right (ignore negatives). Global max = node + leftGain + rightGain.  
**Complexity:** O(n) time, O(h) space.

### H2. Recover Binary Search Tree
Two nodes of a BST are swapped; recover without changing the structure.  
**Hint:** In-order traversal — find two "violations" (first desc pair first node, last desc pair's second node); swap their values.  
**Complexity:** O(n) time, O(1) with Morris traversal.

### H3. Serialize and Deserialize Binary Tree
Design an algorithm to serialize and deserialize a binary tree.  
**Hint:** Pre-order serialization with null markers. Parse the string back using a queue/index pointer.  
**Complexity:** O(n) time, O(n) space.

### H4. Vertical Order Traversal of Binary Tree
Return nodes grouped by vertical column and sorted by (row, value) within each column.  
**Hint:** DFS tracking (col, row, value); sort entries; group by column.  
**Complexity:** O(n log n) time, O(n) space.

### H5. Binary Tree Cameras
Install cameras on some nodes; every node must be monitored. Find minimum cameras.  
**Hint:** Greedy DFS bottom-up; 3 states: not-covered, has-camera, covered. Place camera at a parent when a child is not covered.  
**Complexity:** O(n) time, O(h) space.
