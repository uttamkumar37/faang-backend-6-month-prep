# Linked List — Complete Theory (Basic → Advanced)

---

## 1. What Is a Linked List?

A linked list is a sequence of nodes where each node stores a **value** and a **pointer to the next node**. Unlike arrays, memory is non-contiguous — each node can live anywhere in the heap.

```
head
 ↓
[1] → [2] → [3] → [4] → null

Singly linked: each node has next
Doubly linked: each node has next + prev
Circular:      last node's next = head
```

**Java Node definition:**
```java
class ListNode {
    int val;
    ListNode next;
    ListNode(int x) { val = x; }
}
```

**Access**: O(n) — must traverse from head
**Insert/Delete at known pointer**: O(1)
**Search**: O(n)

---

## 2. Dummy Head Trick

A **sentinel/dummy** node before the real head eliminates special-casing for operations on the first node.

```java
ListNode dummy = new ListNode(0);
dummy.next = head;
ListNode curr = dummy;
// ... manipulate ...
return dummy.next;
```

**Always use a dummy head** when:
- Possibly deleting the head node
- Building a new list node-by-node
- Merging two lists

---

## 3. Traversal

```java
ListNode curr = head;
while (curr != null) {
    // process curr.val
    curr = curr.next;
}
```

**Length:**
```java
int len = 0;
while (curr != null) { len++; curr = curr.next; }
```

---

## 4. Reversal

### Iterative (3-pointer: prev, curr, next)
```java
public ListNode reverse(ListNode head) {
    ListNode prev = null, curr = head;
    while (curr != null) {
        ListNode next = curr.next;
        curr.next = prev;
        prev = curr;
        curr = next;
    }
    return prev;
}
```

### Recursive
```java
public ListNode reverseRecursive(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode newHead = reverseRecursive(head.next);
    head.next.next = head;
    head.next = null;
    return newHead;
}
```

### Reverse a Sub-Portion [left, right]
```java
public ListNode reverseBetween(ListNode head, int left, int right) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode prev = dummy;
    for (int i = 1; i < left; i++) prev = prev.next;    // reach node before left
    ListNode curr = prev.next;
    for (int i = 0; i < right - left; i++) {
        ListNode next = curr.next;
        curr.next = next.next;
        next.next = prev.next;
        prev.next = next;
    }
    return dummy.next;
}
```

---

## 5. Fast & Slow Pointers

```
slow moves 1 step per iteration
fast moves 2 steps per iteration

If a cycle exists: fast eventually laps slow and they meet.
If no cycle: fast reaches null.
At meeting inside cycle: distance from head to cycle entry == distance from meeting point to cycle entry.
```

### Middle of Linked List
```java
public ListNode middleNode(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;  // for even length: second middle
}
```

### Detect Cycle (Floyd's)
```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}
```

### Find Cycle Entry Point
```java
public ListNode detectCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next; fast = fast.next.next;
        if (slow == fast) {
            slow = head;                   // reset one pointer to head
            while (slow != fast) { slow = slow.next; fast = fast.next; }
            return slow;                   // cycle entry
        }
    }
    return null;
}
```

**Why phase 2 works**: if cycle length is C and entry is at distance D from head, then meeting point is D steps from entry → slow (from head, D steps) meets fast (from meeting point, D steps) exactly at entry.

---

## 6. Merge Two Sorted Lists
```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0), curr = dummy;
    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) { curr.next = l1; l1 = l1.next; }
        else                  { curr.next = l2; l2 = l2.next; }
        curr = curr.next;
    }
    curr.next = l1 != null ? l1 : l2;
    return dummy.next;
}
```

---

## 7. Remove Nth Node from End

Use two pointers `n` apart:
```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0);
    dummy.next = head;
    ListNode fast = dummy, slow = dummy;
    for (int i = 0; i <= n; i++) fast = fast.next;  // advance fast n+1 steps
    while (fast != null) { slow = slow.next; fast = fast.next; }
    slow.next = slow.next.next;
    return dummy.next;
}
```

---

## 8. Intersection of Two Lists

Find the node where two lists merge by equalising the lengths:
```java
public ListNode getIntersectionNode(ListNode a, ListNode b) {
    ListNode p = a, q = b;
    while (p != q) {
        p = p == null ? b : p.next;
        q = q == null ? a : q.next;
    }
    return p;  // either null (no intersection) or the intersection node
}
```

Both pointers traverse len(A) + len(B) − (shared suffix) steps → meet at intersection.

---

## 9. Palindrome Check

```java
public boolean isPalindrome(ListNode head) {
    // 1. Find middle
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }
    // 2. Reverse second half
    ListNode secondHalf = reverse(slow);
    // 3. Compare
    ListNode p1 = head, p2 = secondHalf;
    while (p2 != null) {
        if (p1.val != p2.val) return false;
        p1 = p1.next; p2 = p2.next;
    }
    return true;
}
```

---

## 10. Reorder List

```
L0 → L1 → L2 → ... → Ln-2 → Ln-1
becomes
L0 → Ln-1 → L1 → Ln-2 → L2 → Ln-3 → ...
```

```java
public void reorderList(ListNode head) {
    // 1. Find middle
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) { slow = slow.next; fast = fast.next.next; }
    // 2. Reverse second half
    ListNode second = reverse(slow.next);
    slow.next = null;   // cut
    // 3. Interleave
    ListNode first = head;
    while (second != null) {
        ListNode tmp1 = first.next, tmp2 = second.next;
        first.next = second;
        second.next = tmp1;
        first = tmp1;
        second = tmp2;
    }
}
```

---

## 11. Merge K Sorted Lists — O(n log k)

Use a min-heap to always pick the smallest current head:
```java
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.val));
    for (ListNode l : lists) if (l != null) pq.offer(l);
    ListNode dummy = new ListNode(0), curr = dummy;
    while (!pq.isEmpty()) {
        ListNode node = pq.poll();
        curr.next = node;
        curr = curr.next;
        if (node.next != null) pq.offer(node.next);
    }
    return dummy.next;
}
```

---

## 12. Copy List with Random Pointer

Three-pass O(n) / O(1) space approach:
```java
public Node copyRandomList(Node head) {
    if (head == null) return null;
    // Pass 1: interleave copy nodes  [A → A' → B → B' → ...]
    Node curr = head;
    while (curr != null) {
        Node copy = new Node(curr.val);
        copy.next = curr.next;
        curr.next = copy;
        curr = copy.next;
    }
    // Pass 2: set random pointers on copies
    curr = head;
    while (curr != null) {
        if (curr.random != null) curr.next.random = curr.random.next;
        curr = curr.next.next;
    }
    // Pass 3: separate the two lists
    Node dummy = new Node(0);
    Node copyHead = dummy;
    curr = head;
    while (curr != null) {
        copyHead.next = curr.next;
        curr.next = curr.next.next;
        curr = curr.next;
        copyHead = copyHead.next;
    }
    return dummy.next;
}
```

---

## 13. LRU Cache — O(1) get and put

Combine a **HashMap** (O(1) lookup) with a **doubly linked list** (O(1) move-to-front / remove):

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0);  // dummy head (MRU side)
    private final Node tail = new Node(0, 0);  // dummy tail (LRU side)
    
    static class Node {
        int key, val;
        Node prev, next;
        Node(int k, int v) { key = k; val = v; }
    }

    LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail; tail.prev = head;
    }

    public int get(int key) {
        Node n = map.get(key);
        if (n == null) return -1;
        moveToFront(n);
        return n.val;
    }

    public void put(int key, int value) {
        Node n = map.get(key);
        if (n != null) { n.val = value; moveToFront(n); return; }
        if (map.size() == capacity) {
            Node lru = tail.prev;
            remove(lru);
            map.remove(lru.key);
        }
        Node newNode = new Node(key, value);
        map.put(key, newNode);
        addFront(newNode);
    }

    private void addFront(Node n) { n.next = head.next; n.prev = head; head.next.prev = n; head.next = n; }
    private void remove(Node n) { n.prev.next = n.next; n.next.prev = n.prev; }
    private void moveToFront(Node n) { remove(n); addFront(n); }
}
```

---

## 14. Complexity Reference

| Operation | Singly | Doubly |
|---|---|---|
| Access by index | O(n) | O(n) |
| Insert at head | O(1) | O(1) |
| Insert at tail (with tail ptr) | O(1) | O(1) |
| Delete at known node | O(n) (find prev) | O(1) |
| Search | O(n) | O(n) |
| Reverse | O(n) | O(n) |

---

## 15. Decision Guide

| Scenario | Pattern |
|---|---|
| Delete head / corner cases | Dummy head |
| Find middle | Fast/slow pointers |
| Detect / find cycle | Floyd's algorithm |
| kth from end / two-pass → one-pass | Gap of k fast/slow pointers |
| Palindrome check | Find middle + reverse second half |
| Merge sorted lists | Dummy head + two-pointer merge |
| Merge k sorted | Min-heap |
| Clone with random | Interleave + separate |
| O(1) get/put with recency | HashMap + doubly linked list (LRU) |

---

## 16. Common Pitfalls

- **Null pointer**: always check `curr != null` and `curr.next != null` before dereferencing
- **Losing the reference**: save `next` before re-linking: `ListNode next = curr.next; curr.next = ...`
- **Fast pointer off by one**: `fast.next != null && fast.next.next != null` for middle of even-length list
- **Cycle detection reset**: after meeting, reset only ONE pointer to head (not both)
- **Intersection trick**: pointer wraps to other list when it hits null, so use `p = (p == null) ? b : p.next`
- **Dummy head return**: return `dummy.next`, not `dummy`
