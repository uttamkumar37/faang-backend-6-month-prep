package dsa.strings;

import java.util.*;

/**
 * String patterns: frequency count, two pointers, sliding window,
 * expand around center, and KMP pattern matching.
 */
public class StringPatterns {

    // ─── 1. CHARACTER FREQUENCY COUNT ────────────────────────────────────────────

    // Fixed alphabet (lowercase a-z) — O(1) space
    static int[] charFrequency(String s) {
        int[] freq = new int[26];
        for (char c : s.toCharArray()) freq[c - 'a']++;
        return freq;
    }

    // Anagram check: same character frequencies
    static boolean isAnagram(String s, String t) {
        if (s.length() != t.length()) return false;
        int[] freq = new int[26];
        for (char c : s.toCharArray()) freq[c - 'a']++;
        for (char c : t.toCharArray()) {
            if (--freq[c - 'a'] < 0) return false;
        }
        return true;
    }

    // Group anagrams together by sorted-character key
    static Map<String, List<String>> groupAnagrams(String[] words) {
        Map<String, List<String>> map = new HashMap<>();
        for (String w : words) {
            char[] ca = w.toCharArray();
            Arrays.sort(ca);
            map.computeIfAbsent(new String(ca), k -> new ArrayList<>()).add(w);
        }
        return map;
    }

    // ─── 2. TWO POINTERS ON STRINGS ──────────────────────────────────────────────

    // Palindrome check — ignore non-alphanumeric, case-insensitive
    static boolean isPalindrome(String s) {
        int l = 0, r = s.length() - 1;
        while (l < r) {
            while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
            while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
            if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r))) return false;
            l++; r--;
        }
        return true;
    }

    // Reverse string in-place
    static void reverseString(char[] s) {
        int l = 0, r = s.length - 1;
        while (l < r) { char t = s[l]; s[l++] = s[r]; s[r--] = t; }
    }

    // ─── 3. FIXED-SIZE SLIDING WINDOW — ANAGRAM DETECTION ────────────────────────

    // Find all start indices in s where a permutation of p begins
    // Time: O(n), Space: O(1) — fixed 26-char array
    static List<Integer> findAnagrams(String s, String p) {
        List<Integer> result = new ArrayList<>();
        if (s.length() < p.length()) return result;
        int[] pc = new int[26], wc = new int[26];
        for (char c : p.toCharArray()) pc[c - 'a']++;
        for (int i = 0; i < s.length(); i++) {
            wc[s.charAt(i) - 'a']++;
            if (i >= p.length()) wc[s.charAt(i - p.length()) - 'a']--;
            if (Arrays.equals(pc, wc)) result.add(i - p.length() + 1);
        }
        return result;
    }

    // ─── 4. VARIABLE-SIZE SLIDING WINDOW — LONGEST WITHOUT REPEAT ────────────────

    // Time: O(n), Space: O(1) — 128-char ASCII
    static int lengthOfLongestSubstring(String s) {
        int[] lastSeen = new int[128];
        Arrays.fill(lastSeen, -1);
        int max = 0, left = 0;
        for (int right = 0; right < s.length(); right++) {
            int c = s.charAt(right);
            if (lastSeen[c] >= left) left = lastSeen[c] + 1;  // jump left past duplicate
            lastSeen[c] = right;
            max = Math.max(max, right - left + 1);
        }
        return max;
    }

    // ─── 5. MINIMUM WINDOW SUBSTRING ─────────────────────────────────────────────

    // Find the smallest window in s containing all characters of t.
    // Time: O(n + m), Space: O(m)
    static String minWindow(String s, String t) {
        Map<Character, Integer> need = new HashMap<>(), have = new HashMap<>();
        for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
        int formed = 0, required = need.size(), l = 0, minLen = Integer.MAX_VALUE, minL = 0;
        for (int r = 0; r < s.length(); r++) {
            char c = s.charAt(r);
            have.merge(c, 1, Integer::sum);
            if (need.containsKey(c) && have.get(c).equals(need.get(c))) formed++;
            while (formed == required) {
                if (r - l + 1 < minLen) { minLen = r - l + 1; minL = l; }
                char lc = s.charAt(l++);
                have.merge(lc, -1, Integer::sum);
                if (need.containsKey(lc) && have.get(lc) < need.get(lc)) formed--;
            }
        }
        return minLen == Integer.MAX_VALUE ? "" : s.substring(minL, minL + minLen);
    }

    // ─── 6. EXPAND AROUND CENTER — LONGEST PALINDROMIC SUBSTRING ─────────────────

    // Time: O(n²), Space: O(1)
    static String longestPalindrome(String s) {
        int start = 0, maxLen = 1;
        for (int i = 0; i < s.length(); i++) {
            // try both odd (i,i) and even (i,i+1) centers
            for (int[] c : new int[][]{{i, i}, {i, i + 1}}) {
                int l = c[0], r = c[1];
                while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) { l--; r++; }
                if (r - l - 1 > maxLen) { maxLen = r - l - 1; start = l + 1; }
            }
        }
        return s.substring(start, start + maxLen);
    }

    // Count total palindromic substrings
    static int countSubstrings(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            for (int[] c : new int[][]{{i, i}, {i, i + 1}}) {
                int l = c[0], r = c[1];
                while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) { l--; r--; count++; }
            }
        }
        return count;
    }

    // ─── 7. KMP — O(n) EXACT PATTERN MATCHING ────────────────────────────────────

    // Build the failure (partial match) table.
    // lps[i] = length of the longest proper prefix of pattern[0..i] that is also a suffix.
    private static int[] buildLPS(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0, i = 1;
        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                lps[i++] = ++len;
            } else if (len > 0) {
                len = lps[len - 1];  // fall back — do NOT increment i
            } else {
                lps[i++] = 0;
            }
        }
        return lps;
    }

    // Return all start indices where pattern occurs in text. Time: O(n + m)
    static List<Integer> kmpSearch(String text, String pattern) {
        List<Integer> result = new ArrayList<>();
        if (pattern.isEmpty()) return result;
        int n = text.length(), m = pattern.length();
        int[] lps = buildLPS(pattern);
        int i = 0, j = 0;
        while (i < n) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++; j++;
                if (j == m) {
                    result.add(i - j);
                    j = lps[j - 1];  // look for next match
                }
            } else if (j > 0) {
                j = lps[j - 1];  // mismatch — fall back using lps
            } else {
                i++;
            }
        }
        return result;
    }

    // ─── 8. ENCODE / DECODE STRINGS ──────────────────────────────────────────────

    // Encode a list of strings into a single string using length-prefix encoding.
    // Format: "<len>#<str>" for each element. Safe for any character including '#'.
    static String encode(List<String> strs) {
        StringBuilder sb = new StringBuilder();
        for (String s : strs) sb.append(s.length()).append('#').append(s);
        return sb.toString();
    }

    static List<String> decode(String s) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int j = s.indexOf('#', i);
            int len = Integer.parseInt(s.substring(i, j));
            result.add(s.substring(j + 1, j + 1 + len));
            i = j + 1 + len;
        }
        return result;
    }

    // ─── MAIN ────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        System.out.println("Is anagram (anagram, nagaram): " + isAnagram("anagram", "nagaram")); // true
        System.out.println("Group anagrams: " + groupAnagrams(new String[]{"eat","tea","tan","ate","nat","bat"}));

        System.out.println("Is palindrome: " + isPalindrome("A man, a plan, a canal: Panama")); // true
        System.out.println("Is palindrome: " + isPalindrome("race a car")); // false

        System.out.println("Find anagrams (cbaebabacd, abc): " + findAnagrams("cbaebabacd", "abc")); // [0, 6]

        System.out.println("Longest substring without repeat (abcabcbb): "
                + lengthOfLongestSubstring("abcabcbb")); // 3

        System.out.println("Min window (ADOBECODEBANC, ABC): " + minWindow("ADOBECODEBANC", "ABC")); // BANC

        System.out.println("Longest palindrome (babad): " + longestPalindrome("babad")); // bab or aba
        System.out.println("Longest palindrome (cbbd): " + longestPalindrome("cbbd")); // bb

        System.out.println("KMP search (aabxaabxaaabxaaabx, aabxaaabx): "
                + kmpSearch("aabxaabxaaabxaaabx", "aabxaaabx")); // [8]

        List<String> original = List.of("hello", "world", "#sharp#");
        String encoded = encode(original);
        System.out.println("Encoded: " + encoded);
        System.out.println("Decoded: " + decode(encoded)); // [hello, world, #sharp#]
    }
}
