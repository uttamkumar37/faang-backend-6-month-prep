package dsa.trees;

import java.util.*;

/**
 * Binary tree and BST patterns.
 * DFS traversals, BFS level-order, LCA, diameter, validate BST, serialize/deserialize.
 */
public class TreePatterns {

    static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
    }

    // ─── 1. TRAVERSALS ───────────────────────────────────────────────────────────

    static List<Integer> inorder(TreeNode root) {
        List<Integer> result = new ArrayList<>();
        inorderHelper(root, result);
        return result;
    }
    private static void inorderHelper(TreeNode node, List<Integer> result) {
        if (node == null) return;
        inorderHelper(node.left, result);
        result.add(node.val);
        inorderHelper(node.right, result);
    }

    static List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        if (root == null) return result;
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        while (!queue.isEmpty()) {
            int size = queue.size();
            List<Integer> level = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                level.add(node.val);
                if (node.left != null) queue.offer(node.left);
                if (node.right != null) queue.offer(node.right);
            }
            result.add(level);
        }
        return result;
    }

    // ─── 2. DEPTH & BALANCE ──────────────────────────────────────────────────────

    static int maxDepth(TreeNode root) {
        if (root == null) return 0;
        return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
    }

    static boolean isBalanced(TreeNode root) {
        return checkHeight(root) != -1;
    }
    private static int checkHeight(TreeNode node) {
        if (node == null) return 0;
        int left = checkHeight(node.left);   if (left == -1) return -1;
        int right = checkHeight(node.right); if (right == -1) return -1;
        if (Math.abs(left - right) > 1) return -1;
        return 1 + Math.max(left, right);
    }

    // ─── 3. DIAMETER ─────────────────────────────────────────────────────────────

    private static int maxDiameter = 0;

    static int diameterOfBinaryTree(TreeNode root) {
        maxDiameter = 0;
        dfsHeight(root);
        return maxDiameter;
    }
    private static int dfsHeight(TreeNode node) {
        if (node == null) return 0;
        int left  = dfsHeight(node.left);
        int right = dfsHeight(node.right);
        maxDiameter = Math.max(maxDiameter, left + right);
        return 1 + Math.max(left, right);
    }

    // ─── 4. PATH SUM ─────────────────────────────────────────────────────────────

    static boolean hasPathSum(TreeNode root, int target) {
        if (root == null) return false;
        if (root.left == null && root.right == null) return root.val == target;
        return hasPathSum(root.left, target - root.val) || hasPathSum(root.right, target - root.val);
    }

    // LeetCode 124 — Max path sum (any node to any node)
    private static int maxPathSum = Integer.MIN_VALUE;

    static int maxPathSum(TreeNode root) {
        maxPathSum = Integer.MIN_VALUE;
        maxGain(root);
        return maxPathSum;
    }
    private static int maxGain(TreeNode node) {
        if (node == null) return 0;
        int left  = Math.max(maxGain(node.left), 0);
        int right = Math.max(maxGain(node.right), 0);
        maxPathSum = Math.max(maxPathSum, node.val + left + right);
        return node.val + Math.max(left, right);
    }

    // ─── 5. LCA ──────────────────────────────────────────────────────────────────

    static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null || root == p || root == q) return root;
        TreeNode left  = lowestCommonAncestor(root.left, p, q);
        TreeNode right = lowestCommonAncestor(root.right, p, q);
        if (left != null && right != null) return root;
        return left != null ? left : right;
    }

    // ─── 6. VALIDATE BST ─────────────────────────────────────────────────────────

    static boolean isValidBST(TreeNode root) {
        return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }
    private static boolean validate(TreeNode node, long min, long max) {
        if (node == null) return true;
        if (node.val <= min || node.val >= max) return false;
        return validate(node.left, min, node.val) && validate(node.right, node.val, max);
    }

    // ─── 7. SERIALIZE / DESERIALIZE ──────────────────────────────────────────────

    static String serialize(TreeNode root) {
        if (root == null) return "null";
        return root.val + "," + serialize(root.left) + "," + serialize(root.right);
    }

    static TreeNode deserialize(String data) {
        Queue<String> queue = new LinkedList<>(Arrays.asList(data.split(",")));
        return deserializeHelper(queue);
    }
    private static TreeNode deserializeHelper(Queue<String> queue) {
        String val = queue.poll();
        if ("null".equals(val)) return null;
        TreeNode node = new TreeNode(Integer.parseInt(val));
        node.left  = deserializeHelper(queue);
        node.right = deserializeHelper(queue);
        return node;
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    static TreeNode build(Integer... vals) {
        if (vals.length == 0 || vals[0] == null) return null;
        TreeNode[] nodes = new TreeNode[vals.length];
        for (int i = 0; i < vals.length; i++)
            nodes[i] = (vals[i] == null) ? null : new TreeNode(vals[i]);
        for (int i = 0; i < vals.length; i++) {
            if (nodes[i] == null) continue;
            int l = 2*i+1, r = 2*i+2;
            if (l < vals.length) nodes[i].left  = nodes[l];
            if (r < vals.length) nodes[i].right = nodes[r];
        }
        return nodes[0];
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        TreeNode tree = build(3, 9, 20, null, null, 15, 7);

        System.out.println("Inorder: " + inorder(tree));        // [9,3,15,20,7]
        System.out.println("Level order: " + levelOrder(tree)); // [[3],[9,20],[15,7]]
        System.out.println("Max depth: " + maxDepth(tree));     // 3
        System.out.println("Is balanced: " + isBalanced(tree)); // true
        System.out.println("Diameter: " + diameterOfBinaryTree(build(1,2,3,4,5))); // 3

        System.out.println("Has path sum 22: " + hasPathSum(build(5,4,8,11,null,13,4,7,2,null,null,null,1), 22)); // true

        TreeNode bst = build(6, 2, 8, 0, 4, 7, 9, null, null, 3, 5);
        System.out.println("Valid BST: " + isValidBST(bst)); // true

        String serialized = serialize(tree);
        System.out.println("Serialized: " + serialized);
        System.out.println("Deserialized level order: " + levelOrder(deserialize(serialized)));
    }
}
