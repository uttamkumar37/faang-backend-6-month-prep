package dsa.linkedlist;

import java.util.*;

/**
 * Linked list patterns: reverse, cycle, merge, remove, reorder, intersection.
 */
public class LinkedListPatterns {

    static class ListNode {
        int val;
        ListNode next;
        ListNode(int val) { this.val = val; }
    }

    // ─── 1. REVERSE ──────────────────────────────────────────────────────────────

    static ListNode reverse(ListNode head) {
        ListNode prev = null, curr = head;
        while (curr != null) {
            ListNode next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        return prev;
    }

    // ─── 2. CYCLE DETECTION (Floyd) ──────────────────────────────────────────────

    static boolean hasCycle(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast) return true;
        }
        return false;
    }

    static ListNode detectCycleStart(ListNode head) {
        ListNode slow = head, fast = head;
        boolean found = false;
        while (fast != null && fast.next != null) {
            slow = slow.next; fast = fast.next.next;
            if (slow == fast) { found = true; break; }
        }
        if (!found) return null;
        slow = head;
        while (slow != fast) { slow = slow.next; fast = fast.next; }
        return slow;
    }

    // ─── 3. FIND MIDDLE ──────────────────────────────────────────────────────────

    static ListNode findMiddle(ListNode head) {
        ListNode slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
        }
        return slow;
    }

    // ─── 4. MERGE TWO SORTED LISTS ───────────────────────────────────────────────

    static ListNode mergeTwoLists(ListNode l1, ListNode l2) {
        ListNode dummy = new ListNode(0), curr = dummy;
        while (l1 != null && l2 != null) {
            if (l1.val <= l2.val) { curr.next = l1; l1 = l1.next; }
            else { curr.next = l2; l2 = l2.next; }
            curr = curr.next;
        }
        curr.next = (l1 != null) ? l1 : l2;
        return dummy.next;
    }

    // ─── 5. REMOVE NTH FROM END ──────────────────────────────────────────────────

    static ListNode removeNthFromEnd(ListNode head, int n) {
        ListNode dummy = new ListNode(0);
        dummy.next = head;
        ListNode fast = dummy, slow = dummy;
        for (int i = 0; i <= n; i++) fast = fast.next;
        while (fast != null) { slow = slow.next; fast = fast.next; }
        slow.next = slow.next.next;
        return dummy.next;
    }

    // ─── 6. REORDER LIST ─────────────────────────────────────────────────────────

    static void reorderList(ListNode head) {
        if (head == null || head.next == null) return;

        // 1. Find middle
        ListNode slow = head, fast = head;
        while (fast.next != null && fast.next.next != null) {
            slow = slow.next; fast = fast.next.next;
        }

        // 2. Reverse second half
        ListNode secondHalf = reverse(slow.next);
        slow.next = null;

        // 3. Interleave
        ListNode first = head, second = secondHalf;
        while (second != null) {
            ListNode tmp1 = first.next, tmp2 = second.next;
            first.next = second;
            second.next = tmp1;
            first = tmp1; second = tmp2;
        }
    }

    // ─── 7. INTERSECTION ─────────────────────────────────────────────────────────

    static ListNode getIntersectionNode(ListNode headA, ListNode headB) {
        ListNode a = headA, b = headB;
        while (a != b) {
            a = (a == null) ? headB : a.next;
            b = (b == null) ? headA : b.next;
        }
        return a;
    }

    // ─── 8. PALINDROME LINKED LIST ───────────────────────────────────────────────

    static boolean isPalindrome(ListNode head) {
        ListNode mid = findMiddle(head);
        ListNode second = reverse(mid);
        ListNode left = head, right = second;
        while (right != null) {
            if (left.val != right.val) return false;
            left = left.next; right = right.next;
        }
        return true;
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────────

    static ListNode build(int... vals) {
        ListNode dummy = new ListNode(0), curr = dummy;
        for (int v : vals) { curr.next = new ListNode(v); curr = curr.next; }
        return dummy.next;
    }

    static String print(ListNode head) {
        StringBuilder sb = new StringBuilder();
        while (head != null) { sb.append(head.val); if (head.next != null) sb.append(" -> "); head = head.next; }
        return sb.toString();
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Reverse
        System.out.println("Reverse: " + print(reverse(build(1, 2, 3, 4, 5)))); // 5->4->3->2->1

        // Merge sorted
        ListNode m = mergeTwoLists(build(1, 2, 4), build(1, 3, 4));
        System.out.println("Merge sorted: " + print(m)); // 1->1->2->3->4->4

        // Remove nth from end
        System.out.println("Remove 2nd from end: " + print(removeNthFromEnd(build(1,2,3,4,5), 2))); // 1->2->3->5

        // Reorder
        ListNode reorder = build(1, 2, 3, 4, 5);
        reorderList(reorder);
        System.out.println("Reordered: " + print(reorder)); // 1->5->2->4->3

        // Palindrome
        System.out.println("Palindrome 1->2->2->1: " + isPalindrome(build(1, 2, 2, 1))); // true
        System.out.println("Palindrome 1->2: " + isPalindrome(build(1, 2)));              // false
    }
}
