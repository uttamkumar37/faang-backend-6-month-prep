# Trees & BST — Complete Theory (Basic → Advanced)

---

## 1. Tree Terminology

```
         1          ← root (depth 0, level 1)
        / \
       2   3        ← internal nodes (depth 1)
      / \    \
     4   5    6     ← leaves (depth 2)

height of tree = max depth of any leaf = 2
height of node = max depth in its subtree
degree of node = number of children
```

**Key definitions:**
- **Binary tree**: each node has at most 2 children (left, right)
- **Full binary tree**: every node has 0 or 2 children
- **Complete binary tree**: all levels filled except possibly the last (filled left-to-right)
- **Perfect binary tree**: all leaves at same depth, all internal nodes have 2 children. n = 2^(h+1) - 1
- **BST property**: `left subtree < root < right subtree` (strict for all nodes)
- **Balanced tree**: height O(log n). AVL, Red-Black Tree guarantee this.

**Java node:**
```java
class TreeNode {
    int val;
    TreeNode left, right;
    TreeNode(int x) { val = x; }
}
```

---

## 2. DFS Traversals (Recursive)

```
Tree:  1
      / \
     2   3
    / \
   4   5

Preorder  (root-left-right): 1, 2, 4, 5, 3
Inorder   (left-root-right): 4, 2, 5, 1, 3  ← sorted order for BST!
Postorder (left-right-root): 4, 5, 2, 3, 1
```

```java
void preorder(TreeNode root)  { if(root==null) return; visit(root); preorder(root.left);  preorder(root.right); }
void inorder(TreeNode root)   { if(root==null) return; inorder(root.left);  visit(root); inorder(root.right); }
void postorder(TreeNode root) { if(root==null) return; postorder(root.left); postorder(root.right); visit(root); }
```

---

## 3. DFS Traversals (Iterative)

### Preorder — explicit stack
```java
public List<Integer> preorderIterative(TreeNode root) {
    List<Integer> res = new ArrayList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    if (root != null) stack.push(root);
    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        res.add(node.val);
        if (node.right != null) stack.push(node.right);
        if (node.left  != null) stack.push(node.left);
    }
    return res;
}
```

### Inorder — iterative with "go left as far as possible"
```java
public List<Integer> inorderIterative(TreeNode root) {
    List<Integer> res = new ArrayList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    while (curr != null || !stack.isEmpty()) {
        while (curr != null) { stack.push(curr); curr = curr.left; }
        curr = stack.pop();
        res.add(curr.val);
        curr = curr.right;
    }
    return res;
}
```

### Postorder — two-stack or reverse preorder trick
```java
// Reverse of "root-right-left" preorder = postorder
public List<Integer> postorderIterative(TreeNode root) {
    LinkedList<Integer> res = new LinkedList<>();
    Deque<TreeNode> stack = new ArrayDeque<>();
    if (root != null) stack.push(root);
    while (!stack.isEmpty()) {
        TreeNode node = stack.pop();
        res.addFirst(node.val);          // prepend
        if (node.left  != null) stack.push(node.left);
        if (node.right != null) stack.push(node.right);
    }
    return res;
}
```

---

## 4. BFS — Level Order Traversal

```java
public List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> queue = new LinkedList<>();
    queue.offer(root);
    while (!queue.isEmpty()) {
        int size = queue.size();          // snapshot of current level
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TreeNode node = queue.poll();
            level.add(node.val);
            if (node.left  != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        res.add(level);
    }
    return res;
}
```

**Variants**: right side view (last element of each level), zigzag (reverse alternate levels), level averages.

---

## 5. Tree Height & Balance

```java
public int height(TreeNode root) {
    if (root == null) return -1;  // (-1 for edges, 0 for nodes)
    return 1 + Math.max(height(root.left), height(root.right));
}

public boolean isBalanced(TreeNode root) {
    return checkHeight(root) != -1;
}

private int checkHeight(TreeNode root) {     // returns -1 if unbalanced
    if (root == null) return 0;
    int l = checkHeight(root.left);
    int r = checkHeight(root.right);
    if (l == -1 || r == -1 || Math.abs(l - r) > 1) return -1;
    return 1 + Math.max(l, r);
}
```

---

## 6. Lowest Common Ancestor (LCA)

```java
// LCA in a general binary tree
public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left  = lowestCommonAncestor(root.left,  p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) return root;  // p and q on different sides
    return left != null ? left : right;
}

// LCA in a BST — O(h) time, use BST property
public TreeNode lcaBST(TreeNode root, TreeNode p, TreeNode q) {
    while (root != null) {
        if (p.val < root.val && q.val < root.val) root = root.left;
        else if (p.val > root.val && q.val > root.val) root = root.right;
        else return root;
    }
    return null;
}
```

---

## 7. Diameter of Binary Tree

```
Diameter = longest path between any two nodes (may not pass through root)
         = max over all nodes of (leftHeight + rightHeight)
```

```java
int diameter = 0;
public int diameterOfBinaryTree(TreeNode root) {
    dfs(root);
    return diameter;
}
private int dfs(TreeNode node) {
    if (node == null) return 0;
    int l = dfs(node.left), r = dfs(node.right);
    diameter = Math.max(diameter, l + r);
    return 1 + Math.max(l, r);
}
```

---

## 8. BST Operations

### Search
```java
public TreeNode searchBST(TreeNode root, int val) {
    while (root != null) {
        if      (val < root.val) root = root.left;
        else if (val > root.val) root = root.right;
        else return root;
    }
    return null;
}
```

### Insert
```java
public TreeNode insertBST(TreeNode root, int val) {
    if (root == null) return new TreeNode(val);
    if (val < root.val) root.left  = insertBST(root.left,  val);
    else if (val > root.val) root.right = insertBST(root.right, val);
    return root;
}
```

### Delete
```java
public TreeNode deleteBST(TreeNode root, int key) {
    if (root == null) return null;
    if (key < root.val) root.left = deleteBST(root.left, key);
    else if (key > root.val) root.right = deleteBST(root.right, key);
    else {
        if (root.left == null) return root.right;
        if (root.right == null) return root.left;
        // Replace with inorder successor (min of right subtree)
        TreeNode successor = root.right;
        while (successor.left != null) successor = successor.left;
        root.val = successor.val;
        root.right = deleteBST(root.right, successor.val);
    }
    return root;
}
```

### Validate BST
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

---

## 9. BST Inorder = Sorted — Key Insight

Inorder traversal of BST yields elements in ascending order. This lets us:
- Find kth smallest: inorder, count k steps
- Validate BST: inorder must be strictly increasing
- Find in-order successor/predecessor: BST search + tracking

```java
// Kth Smallest in BST
public int kthSmallest(TreeNode root, int k) {
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode curr = root;
    while (curr != null || !stack.isEmpty()) {
        while (curr != null) { stack.push(curr); curr = curr.left; }
        curr = stack.pop();
        if (--k == 0) return curr.val;
        curr = curr.right;
    }
    return -1;
}
```

---

## 10. Path Problems

### Path Sum (root to leaf)
```java
public boolean hasPathSum(TreeNode root, int target) {
    if (root == null) return false;
    if (root.left == null && root.right == null) return root.val == target;
    return hasPathSum(root.left, target - root.val) || hasPathSum(root.right, target - root.val);
}
```

### Max Path Sum (any path)
```java
int maxSum = Integer.MIN_VALUE;
public int maxPathSum(TreeNode root) {
    gain(root);
    return maxSum;
}
private int gain(TreeNode node) {
    if (node == null) return 0;
    int l = Math.max(gain(node.left),  0);  // ignore negative paths
    int r = Math.max(gain(node.right), 0);
    maxSum = Math.max(maxSum, node.val + l + r);
    return node.val + Math.max(l, r);        // return single branch to parent
}
```

---

## 11. Construct Tree from Traversals

### From Preorder + Inorder
```java
public TreeNode buildTree(int[] preorder, int[] inorder) {
    Map<Integer, Integer> inMap = new HashMap<>();
    for (int i = 0; i < inorder.length; i++) inMap.put(inorder[i], i);
    return build(preorder, 0, preorder.length-1, inorder, 0, inorder.length-1, inMap);
}
TreeNode build(int[] pre, int ps, int pe, int[] in, int is, int ie, Map<Integer,Integer> map) {
    if (ps > pe) return null;
    TreeNode root = new TreeNode(pre[ps]);
    int inIdx = map.get(pre[ps]);
    int leftSize = inIdx - is;
    root.left  = build(pre, ps+1, ps+leftSize, in, is, inIdx-1, map);
    root.right = build(pre, ps+leftSize+1, pe, in, inIdx+1, ie, map);
    return root;
}
```

---

## 12. Serialize and Deserialize Binary Tree

```java
// BFS serialization
public String serialize(TreeNode root) {
    if (root == null) return "null";
    StringBuilder sb = new StringBuilder();
    Queue<TreeNode> q = new LinkedList<>();
    q.offer(root);
    while (!q.isEmpty()) {
        TreeNode node = q.poll();
        if (node == null) { sb.append("null,"); continue; }
        sb.append(node.val).append(",");
        q.offer(node.left); q.offer(node.right);
    }
    return sb.toString();
}

public TreeNode deserialize(String data) {
    String[] parts = data.split(",");
    TreeNode root = new TreeNode(Integer.parseInt(parts[0]));
    Queue<TreeNode> q = new LinkedList<>();
    q.offer(root);
    int i = 1;
    while (!q.isEmpty() && i < parts.length) {
        TreeNode node = q.poll();
        if (!parts[i].equals("null")) { node.left = new TreeNode(Integer.parseInt(parts[i])); q.offer(node.left); }
        i++;
        if (i < parts.length && !parts[i].equals("null")) { node.right = new TreeNode(Integer.parseInt(parts[i])); q.offer(node.right); }
        i++;
    }
    return root;
}
```

---

## 13. Morris Traversal — O(1) Space Inorder

```java
public List<Integer> morrisInorder(TreeNode root) {
    List<Integer> res = new ArrayList<>();
    TreeNode curr = root;
    while (curr != null) {
        if (curr.left == null) {
            res.add(curr.val);
            curr = curr.right;
        } else {
            // Find inorder predecessor
            TreeNode pred = curr.left;
            while (pred.right != null && pred.right != curr) pred = pred.right;
            if (pred.right == null) {
                pred.right = curr;          // make thread
                curr = curr.left;
            } else {
                pred.right = null;          // remove thread
                res.add(curr.val);
                curr = curr.right;
            }
        }
    }
    return res;
}
```

---

## 14. Complexity Reference

| Operation | BST (avg) | BST (worst, skewed) | AVL/RB Tree |
|---|---|---|---|
| Search | O(log n) | O(n) | O(log n) |
| Insert | O(log n) | O(n) | O(log n) |
| Delete | O(log n) | O(n) | O(log n) |
| Inorder traversal | O(n) | O(n) | O(n) |

---

## 15. Decision Guide

| Signal | Approach |
|---|---|
| Process each node once | DFS recursive |
| Level-by-level processing | BFS (queue) |
| Iterative traversal | Explicit stack |
| BST validation / search | Use bounds (min, max) |
| Ancestor of two nodes | LCA — differentiate by sides |
| Construct tree from traversals | Preorder root + inorder split |
| O(1) space traversal | Morris threading |
| Diameter / max path sum | Bottom-up return of single-branch gain |

---

## 16. Common Pitfalls

- **Null before dereference**: always check `node == null` before `node.left`/`.right`
- **Postorder "return" value**: return `1 + max(left, right)` to parent, record diameter as `left + right`
- **BST validation**: pass strict bounds, not just `left.val < root.val`
- **Integer overflow in max path sum**: initialize to `Integer.MIN_VALUE`, not 0
- **Level order size snapshot**: capture `queue.size()` before inner loop (it changes as you add children)
- **Morris traversal modifies tree**: restore threads — don't use if tree must be read-only
