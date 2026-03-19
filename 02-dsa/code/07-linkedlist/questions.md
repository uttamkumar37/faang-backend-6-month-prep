# Linked List — Practice Questions

---

## 🟢 Easy (5)

### E1. Reverse Linked List
Reverse a singly linked list iteratively.  
**Hint:** Keep prev=null, curr=head; each step: save next, point curr.next to prev, advance both.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode reverseList(ListNode head) {
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

### E2. Merge Two Sorted Lists
Merge two sorted linked lists into one sorted list.  
**Hint:** Use a dummy head; compare current nodes and attach the smaller one.  
**Complexity:** O(m+n) time, O(1) space.

```java
public ListNode mergeTwoLists(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0), cur = dummy;
    while (l1 != null && l2 != null) {
        if (l1.val <= l2.val) { cur.next = l1; l1 = l1.next; }
        else                  { cur.next = l2; l2 = l2.next; }
        cur = cur.next;
    }
    cur.next = (l1 != null) ? l1 : l2;
    return dummy.next;
}
```

### E3. Middle of the Linked List
Return the middle node of a linked list.  
**Hint:** Fast/slow pointers; fast moves 2 steps, slow moves 1 — slow ends at middle.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode middleNode(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }
    return slow;
}
```

### E4. Palindrome Linked List
Check if a linked list is a palindrome.  
**Hint:** Find middle, reverse second half, compare front and back halves.  
**Complexity:** O(n) time, O(1) space.

```java
public boolean isPalindrome(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) { slow = slow.next; fast = fast.next.next; }
    ListNode rev = reverseList(slow);
    ListNode p = head, q = rev;
    while (q != null) { if (p.val != q.val) return false; p = p.next; q = q.next; }
    return true;
}
```

### E5. Linked List Cycle
Detect if a cycle exists in a linked list.  
**Hint:** Fast/slow pointers — if they ever meet, there's a cycle.  
**Complexity:** O(n) time, O(1) space.

```java
public boolean hasCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next; fast = fast.next.next;
        if (slow == fast) return true;
    }
    return false;
}
```

---

## 🟡 Medium (10)

### M1. Add Two Numbers
Add two numbers represented as reversed linked lists; return result as a linked list.  
**Hint:** Simulate addition digit by digit with a carry variable.  
**Complexity:** O(max(m,n)) time, O(max(m,n)) space.

```java
public ListNode addTwoNumbers(ListNode l1, ListNode l2) {
    ListNode dummy = new ListNode(0), cur = dummy;
    int carry = 0;
    while (l1 != null || l2 != null || carry != 0) {
        int sum = carry;
        if (l1 != null) { sum += l1.val; l1 = l1.next; }
        if (l2 != null) { sum += l2.val; l2 = l2.next; }
        carry = sum / 10;
        cur.next = new ListNode(sum % 10);
        cur = cur.next;
    }
    return dummy.next;
}
```

### M2. Remove Nth Node From End of List
Remove the n-th node from the end in one pass.  
**Hint:** Two pointers — fast is n steps ahead; when fast reaches end, slow is at target.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode removeNthFromEnd(ListNode head, int n) {
    ListNode dummy = new ListNode(0, head), fast = dummy, slow = dummy;
    for (int i = 0; i <= n; i++) fast = fast.next;
    while (fast != null) { fast = fast.next; slow = slow.next; }
    slow.next = slow.next.next;
    return dummy.next;
}
```

### M3. Reorder List
Reorder list: L0→Ln→L1→Ln-1→L2→Ln-2→...  
**Hint:** Find middle, reverse second half, then merge two halves alternately.  
**Complexity:** O(n) time, O(1) space.

```java
public void reorderList(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) { slow = slow.next; fast = fast.next.next; }
    ListNode second = reverseList(slow.next);
    slow.next = null;
    ListNode first = head;
    while (second != null) {
        ListNode tmp1 = first.next, tmp2 = second.next;
        first.next = second; second.next = tmp1;
        first = tmp1; second = tmp2;
    }
}
```

### M4. Linked List Cycle II
Find the node where the cycle begins.  
**Hint:** Floyd's detection: after fast/slow meet, move one pointer to head; advance both one step — they'll meet at cycle start.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode detectCycle(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next; fast = fast.next.next;
        if (slow == fast) {
            slow = head;
            while (slow != fast) { slow = slow.next; fast = fast.next; }
            return slow;
        }
    }
    return null;
}
```

### M5. Sort List
Sort a linked list in O(n log n) time and O(1) space.  
**Hint:** Merge sort — find middle, split, recursively sort, merge.  
**Complexity:** O(n log n) time, O(log n) stack space.

```java
public ListNode sortList(ListNode head) {
    if (head == null || head.next == null) return head;
    ListNode mid = getMid(head);
    ListNode right = sortList(mid.next);
    mid.next = null;
    ListNode left = sortList(head);
    return mergeTwoLists(left, right);
}
private ListNode getMid(ListNode head) {
    ListNode slow = head, fast = head;
    while (fast.next != null && fast.next.next != null) { slow = slow.next; fast = fast.next.next; }
    return slow;
}
```

### M6. Swap Nodes in Pairs
Swap every two adjacent nodes and return the new head.  
**Hint:** Dummy head; swap (first, second) pair, advance by two nodes each iteration.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode swapPairs(ListNode head) {
    ListNode dummy = new ListNode(0, head), prev = dummy;
    while (prev.next != null && prev.next.next != null) {
        ListNode a = prev.next, b = prev.next.next;
        prev.next = b; a.next = b.next; b.next = a;
        prev = a;
    }
    return dummy.next;
}
```

### M7. Rotate List
Rotate linked list to the right by `k` places.  
**Hint:** Find length, compute effective k, find the new tail (n-k-1 position), break chain.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode rotateRight(ListNode head, int k) {
    if (head == null) return null;
    int len = 1; ListNode tail = head;
    while (tail.next != null) { tail = tail.next; len++; }
    tail.next = head; // make circular
    k = len - k % len;
    ListNode newTail = head;
    for (int i = 1; i < k; i++) newTail = newTail.next;
    ListNode newHead = newTail.next;
    newTail.next = null;
    return newHead;
}
```

### M8. Copy List with Random Pointer
Deep copy a linked list where each node has a `next` and `random` pointer.  
**Hint:** Interleave cloned nodes; set random pointers; then separate the two lists.  
**Complexity:** O(n) time, O(1) space.

```java
public Node copyRandomList(Node head) {
    if (head == null) return null;
    // Step 1: interleave
    for (Node cur = head; cur != null; cur = cur.next.next) {
        Node clone = new Node(cur.val);
        clone.next = cur.next; cur.next = clone;
    }
    // Step 2: set random
    for (Node cur = head; cur != null; cur = cur.next.next)
        if (cur.random != null) cur.next.random = cur.random.next;
    // Step 3: separate
    Node dummy = new Node(0), cloneCur = dummy;
    for (Node cur = head; cur != null; cur = cur.next) {
        cloneCur.next = cur.next; cloneCur = cloneCur.next;
        cur.next = cur.next.next;
    }
    return dummy.next;
}
```

### M9. Partition List
Given a value x, rearrange the list so all nodes < x come before nodes ≥ x.  
**Hint:** Two dummy heads — one for less, one for greater-or-equal; connect them at end.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode partition(ListNode head, int x) {
    ListNode lessD = new ListNode(0), greaterD = new ListNode(0);
    ListNode less = lessD, greater = greaterD;
    for (ListNode cur = head; cur != null; cur = cur.next) {
        if (cur.val < x) { less.next = cur; less = less.next; }
        else             { greater.next = cur; greater = greater.next; }
    }
    greater.next = null; less.next = greaterD.next;
    return lessD.next;
}
```

### M10. Flatten a Multilevel Doubly Linked List
Flatten a multilevel doubly linked list (some nodes have a `child` pointer).  
**Hint:** When a child is found, insert child list between current and current.next using a stack.  
**Complexity:** O(n) time, O(n) space.

```java
public Node flatten(Node head) {
    Deque<Node> stack = new ArrayDeque<>();
    Node cur = head;
    while (cur != null) {
        if (cur.child != null) {
            if (cur.next != null) stack.push(cur.next);
            cur.next = cur.child; cur.child.prev = cur; cur.child = null;
        }
        if (cur.next == null && !stack.isEmpty()) {
            Node saved = stack.pop();
            cur.next = saved; saved.prev = cur;
        }
        cur = cur.next;
    }
    return head;
}
```

---

## 🔴 Hard (5)

### H1. Merge K Sorted Lists
Merge `k` sorted linked lists into one sorted list.  
**Hint:** Min-heap of (value, node); repeatedly extract min and push its next.  
**Complexity:** O(N log k) time, O(k) space.

```java
public ListNode mergeKLists(ListNode[] lists) {
    PriorityQueue<ListNode> pq = new PriorityQueue<>(Comparator.comparingInt(n -> n.val));
    for (ListNode node : lists) if (node != null) pq.offer(node);
    ListNode dummy = new ListNode(0), cur = dummy;
    while (!pq.isEmpty()) {
        cur.next = pq.poll(); cur = cur.next;
        if (cur.next != null) pq.offer(cur.next);
    }
    return dummy.next;
}
```

### H2. Reverse Nodes in k-Group
Reverse every k nodes of a linked list.  
**Hint:** Check if k nodes exist; reverse them in-place; recursively process the rest.  
**Complexity:** O(n) time, O(n/k) stack space.

```java
public ListNode reverseKGroup(ListNode head, int k) {
    ListNode check = head;
    for (int i = 0; i < k; i++) { if (check == null) return head; check = check.next; }
    ListNode prev = null, cur = head;
    for (int i = 0; i < k; i++) { ListNode next = cur.next; cur.next = prev; prev = cur; cur = next; }
    head.next = reverseKGroup(cur, k);
    return prev;
}
```

### H3. LRU Cache
Design a data structure with O(1) get and put using LRU eviction.  
**Hint:** HashMap + doubly linked list. On access/insert: move to head; on overflow: remove tail.  
**Complexity:** O(1) per operation.

```java
class LRUCache {
    private final int capacity;
    private final Map<Integer, int[]> map = new LinkedHashMap<>(); // key -> [value]
    public LRUCache(int capacity) { this.capacity = capacity; }
    public int get(int key) {
        if (!map.containsKey(key)) return -1;
        int val = map.remove(key)[0]; map.put(key, new int[]{val}); return val;
    }
    public void put(int key, int value) {
        map.remove(key);
        if (map.size() == capacity) map.remove(map.keySet().iterator().next());
        map.put(key, new int[]{value});
    }
}
```

### H4. LFU Cache
Design a cache that evicts the least frequently used item (ties broken by least recently used).  
**Hint:** HashMap of key→value, HashMap of key→freq, HashMap of freq→LinkedHashSet of keys. Track minFreq.  
**Complexity:** O(1) per operation.

```java
class LFUCache {
    private final int cap;
    private int minFreq = 0;
    private final Map<Integer,Integer> keyVal = new HashMap<>(), keyFreq = new HashMap<>();
    private final Map<Integer, LinkedHashSet<Integer>> freqKeys = new HashMap<>();
    public LFUCache(int capacity) { this.cap = capacity; }
    public int get(int key) {
        if (!keyVal.containsKey(key)) return -1;
        updateFreq(key); return keyVal.get(key);
    }
    public void put(int key, int value) {
        if (cap == 0) return;
        if (keyVal.containsKey(key)) { keyVal.put(key, value); updateFreq(key); return; }
        if (keyVal.size() == cap) {
            int evict = freqKeys.get(minFreq).iterator().next();
            freqKeys.get(minFreq).remove(evict); keyVal.remove(evict); keyFreq.remove(evict);
        }
        keyVal.put(key, value); keyFreq.put(key, 1);
        freqKeys.computeIfAbsent(1, k -> new LinkedHashSet<>()).add(key);
        minFreq = 1;
    }
    private void updateFreq(int key) {
        int freq = keyFreq.get(key);
        keyFreq.put(key, freq + 1);
        freqKeys.get(freq).remove(key);
        if (freqKeys.get(freq).isEmpty() && freq == minFreq) minFreq++;
        freqKeys.computeIfAbsent(freq + 1, k -> new LinkedHashSet<>()).add(key);
    }
}
```

### H5. Reverse Linked List II
Reverse a sublist from position `left` to `right` in one pass.  
**Hint:** Advance to (left-1) position; reverse the sublist using three-pointer technique; reconnect.  
**Complexity:** O(n) time, O(1) space.

```java
public ListNode reverseBetween(ListNode head, int left, int right) {
    ListNode dummy = new ListNode(0, head), pre = dummy;
    for (int i = 1; i < left; i++) pre = pre.next;
    ListNode cur = pre.next;
    for (int i = 0; i < right - left; i++) {
        ListNode next = cur.next;
        cur.next = next.next;
        next.next = pre.next;
        pre.next = next;
    }
    return dummy.next;
}
```

---

## 🟡 Medium (10)

### M1. Add Two Numbers
Add two numbers represented as reversed linked lists; return result as a linked list.  
**Hint:** Simulate addition digit by digit with a carry variable.  
**Complexity:** O(max(m,n)) time, O(max(m,n)) space.

### M2. Remove Nth Node From End of List
Remove the n-th node from the end in one pass.  
**Hint:** Two pointers — fast is n steps ahead; when fast reaches end, slow is at target.  
**Complexity:** O(n) time, O(1) space.

### M3. Reorder List
Reorder list: L0→Ln→L1→Ln-1→L2→Ln-2→...  
**Hint:** Find middle, reverse second half, then merge two halves alternately.  
**Complexity:** O(n) time, O(1) space.

### M4. Linked List Cycle II
Find the node where the cycle begins.  
**Hint:** Floyd's detection: after fast/slow meet, move one pointer to head; advance both one step — they'll meet at cycle start.  
**Complexity:** O(n) time, O(1) space.

### M5. Sort List
Sort a linked list in O(n log n) time and O(1) space.  
**Hint:** Merge sort — find middle, split, recursively sort, merge.  
**Complexity:** O(n log n) time, O(log n) stack space.

### M6. Swap Nodes in Pairs
Swap every two adjacent nodes and return the new head.  
**Hint:** Dummy head; swap (first, second) pair, advance by two nodes each iteration.  
**Complexity:** O(n) time, O(1) space.

### M7. Rotate List
Rotate linked list to the right by `k` places.  
**Hint:** Find length, compute effective k, find the new tail (n-k-1 position), break chain.  
**Complexity:** O(n) time, O(1) space.

### M8. Copy List with Random Pointer
Deep copy a linked list where each node has a `next` and `random` pointer.  
**Hint:** Interleave cloned nodes; set random pointers; then separate the two lists.  
**Complexity:** O(n) time, O(1) space.

### M9. Partition List
Given a value x, rearrange the list so all nodes < x come before nodes ≥ x.  
**Hint:** Two dummy heads — one for less, one for greater-or-equal; connect them at end.  
**Complexity:** O(n) time, O(1) space.

### M10. Flatten a Multilevel Doubly Linked List
Flatten a multilevel doubly linked list (some nodes have a `child` pointer).  
**Hint:** When a child is found, insert child list between current and current.next using a stack.  
**Complexity:** O(n) time, O(n) space.

---

## 🔴 Hard (5)

### H1. Merge K Sorted Lists
Merge `k` sorted linked lists into one sorted list.  
**Hint:** Min-heap of (value, node); repeatedly extract min and push its next.  
**Complexity:** O(N log k) time, O(k) space.

### H2. Reverse Nodes in k-Group
Reverse every k nodes of a linked list.  
**Hint:** Check if k nodes exist; reverse them in-place; recursively process the rest.  
**Complexity:** O(n) time, O(n/k) stack space.

### H3. LRU Cache
Design a data structure with O(1) get and put using LRU eviction.  
**Hint:** HashMap + doubly linked list. On access/insert: move to head; on overflow: remove tail.  
**Complexity:** O(1) per operation.

### H4. LFU Cache
Design a cache that evicts the least frequently used item (ties broken by least recently used).  
**Hint:** HashMap of key→value, HashMap of key→freq, HashMap of freq→LinkedHashSet of keys. Track minFreq.  
**Complexity:** O(1) per operation.

### H5. Reverse Linked List II
Reverse a sublist from position `left` to `right` in one pass.  
**Hint:** Advance to (left-1) position; reverse the sublist using three-pointer technique; reconnect.  
**Complexity:** O(n) time, O(1) space.
