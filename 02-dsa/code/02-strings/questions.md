# Strings — Practice Questions

---

## 🟢 Easy (5)

### E1. Reverse String
Reverse a character array in-place.  
**Hint:** Two-pointer swap from both ends toward the middle.  
**Complexity:** O(n) time, O(1) space.

```java
public void reverseString(char[] s) {
    int l = 0, r = s.length - 1;
    while (l < r) { char tmp = s[l]; s[l++] = s[r]; s[r--] = tmp; }
}
```

### E2. Valid Palindrome
Given a string, ignoring non-alphanumeric characters and case, determine if it reads the same forwards and backwards.  
**Hint:** Two-pointer, skip non-alphanumeric, compare with `Character.toLowerCase`.  
**Complexity:** O(n) time, O(1) space.

```java
public boolean isPalindrome(String s) {
    int l = 0, r = s.length() - 1;
    while (l < r) {
        while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
        while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
        if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r))) return false;
        l++; r--;
    }
    return true;
}
```

### E3. First Occurrence in String (strStr)
Return the index of the first occurrence of needle in haystack, or -1 if not found.  
**Hint:** Use `String.indexOf` or a simple sliding window comparison.  
**Complexity:** O(n * m) naive; O(n) with KMP.

```java
public int strStr(String haystack, String needle) {
    return haystack.indexOf(needle);
}
```

### E4. Valid Anagram
Determine if two strings are anagrams of each other.  
**Hint:** Character frequency array of size 26; increment for s, decrement for t; check all zeros.  
**Complexity:** O(n) time, O(1) space.

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;
    int[] freq = new int[26];
    for (char c : s.toCharArray()) freq[c - 'a']++;
    for (char c : t.toCharArray()) if (--freq[c - 'a'] < 0) return false;
    return true;
}
```

### E5. Reverse Words in a String III
Reverse the order of characters in each word within a sentence while preserving whitespace and word order.  
**Hint:** Split on space, reverse each word, join.  
**Complexity:** O(n) time, O(n) space.

```java
public String reverseWords(String s) {
    String[] words = s.split(" ");
    StringBuilder res = new StringBuilder();
    for (String w : words) {
        if (res.length() > 0) res.append(' ');
        res.append(new StringBuilder(w).reverse());
    }
    return res.toString();
}
```

---

## 🟡 Medium (10)

### M1. Longest Substring Without Repeating Characters
Find the length of the longest substring without repeating characters.  
**Hint:** Sliding window with a HashSet; shrink left when a duplicate enters the window.  
**Complexity:** O(n) time, O(min(n, 26)) space.

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character, Integer> map = new HashMap<>();
    int max = 0, left = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (map.containsKey(c)) left = Math.max(left, map.get(c) + 1);
        map.put(c, right);
        max = Math.max(max, right - left + 1);
    }
    return max;
}
```

### M2. Longest Palindromic Substring
Find the longest palindromic substring in a string.  
**Hint:** Expand around each character (and each gap) as a center; track the longest found.  
**Complexity:** O(n²) time, O(1) space.

```java
public String longestPalindrome(String s) {
    int start = 0, maxLen = 1;
    for (int i = 0; i < s.length(); i++) {
        for (int[] range : new int[][]{{i, i}, {i, i + 1}}) {
            int l = range[0], r = range[1];
            while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) { l--; r++; }
            if (r - l - 1 > maxLen) { maxLen = r - l - 1; start = l + 1; }
        }
    }
    return s.substring(start, start + maxLen);
}
```

### M3. Group Anagrams
Group an array of strings by anagram families.  
**Hint:** Sort each word as a key; group into a HashMap of sorted → list.  
**Complexity:** O(n * k log k) time where k = max word length.

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> map = new HashMap<>();
    for (String s : strs) {
        char[] ca = s.toCharArray(); Arrays.sort(ca);
        map.computeIfAbsent(new String(ca), k -> new ArrayList<>()).add(s);
    }
    return new ArrayList<>(map.values());
}
```

### M4. String to Integer (atoi)
Implement `atoi` which converts a string to an integer, handling whitespace, sign, overflow.  
**Hint:** Skip leading spaces, read sign, parse digits; clamp to `Integer.MAX/MIN_VALUE` on overflow.  
**Complexity:** O(n) time, O(1) space.

```java
public int myAtoi(String s) {
    int i = 0, n = s.length(), sign = 1; long result = 0;
    while (i < n && s.charAt(i) == ' ') i++;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-')) sign = s.charAt(i++) == '-' ? -1 : 1;
    while (i < n && Character.isDigit(s.charAt(i))) {
        result = result * 10 + (s.charAt(i++) - '0');
        if (result * sign >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (result * sign <= Integer.MIN_VALUE) return Integer.MIN_VALUE;
    }
    return (int)(result * sign);
}
```

### M5. Zigzag Conversion
Convert a string written in a zigzag pattern on n rows into a single string read row by row.  
**Hint:** Use n StringBuilders, one per row. Track current row and direction; flip direction at top/bottom.  
**Complexity:** O(n) time, O(n) space.

```java
public String convert(String s, int numRows) {
    if (numRows == 1 || numRows >= s.length()) return s;
    StringBuilder[] rows = new StringBuilder[numRows];
    for (int i = 0; i < numRows; i++) rows[i] = new StringBuilder();
    int row = 0, dir = -1;
    for (char c : s.toCharArray()) {
        rows[row].append(c);
        if (row == 0 || row == numRows - 1) dir = -dir;
        row += dir;
    }
    StringBuilder res = new StringBuilder();
    for (StringBuilder r : rows) res.append(r);
    return res.toString();
}
```

### M6. Decode Ways
Count the number of ways to decode a digit string into letters (A=1..Z=26).  
**Hint:** DP — `dp[i]` = ways to decode `s[0..i-1]`. Transition: single digit (1-9) and two-digit (10-26).  
**Complexity:** O(n) time, O(1) space with rolling variables.

```java
public int numDecodings(String s) {
    if (s.isEmpty() || s.charAt(0) == '0') return 0;
    int prev2 = 1, prev1 = 1;
    for (int i = 1; i < s.length(); i++) {
        int cur = 0;
        if (s.charAt(i) != '0') cur = prev1;
        int two = Integer.parseInt(s.substring(i - 1, i + 1));
        if (two >= 10 && two <= 26) cur += prev2;
        prev2 = prev1; prev1 = cur;
    }
    return prev1;
}
```

### M7. Minimum Window Substring
Find the minimum window in string s that contains all characters of string t.  
**Hint:** Sliding window with two frequency maps; shrink left when all t-chars are satisfied; track minimum window.  
**Complexity:** O(n + m) time, O(m) space.

```java
public String minWindow(String s, String t) {
    Map<Character, Integer> need = new HashMap<>(), have = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    int formed = 0, required = need.size(), l = 0, minLen = Integer.MAX_VALUE, minL = 0;
    for (int r = 0; r < s.length(); r++) {
        char c = s.charAt(r); have.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && have.get(c).equals(need.get(c))) formed++;
        while (formed == required) {
            if (r - l + 1 < minLen) { minLen = r - l + 1; minL = l; }
            char lc = s.charAt(l); have.merge(lc, -1, Integer::sum);
            if (need.containsKey(lc) && have.get(lc) < need.get(lc)) formed--;
            l++;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(minL, minL + minLen);
}
```

### M8. Encode and Decode Strings
Design an algorithm to encode a list of strings to a single string, and decode back.  
**Hint:** Length-prefix encoding: prepend each string with its length + a delimiter (e.g., `"4#word"`).  
**Complexity:** O(n) time for both encode and decode.

```java
public String encode(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    for (String s : strs) sb.append(s.length()).append('#').append(s);
    return sb.toString();
}
public List<String> decode(String s) {
    List<String> res = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
        int j = s.indexOf('#', i);
        int len = Integer.parseInt(s.substring(i, j));
        res.add(s.substring(j + 1, j + 1 + len));
        i = j + 1 + len;
    }
    return res;
}
```

### M9. Rotate String
Check if string `goal` can be obtained by rotating string `s` by any number of positions.  
**Hint:** `s + s` contains all rotations — check if `goal` is a substring. Also check lengths match.  
**Complexity:** O(n²) naive; O(n) with KMP.

```java
public boolean rotateString(String s, String goal) {
    return s.length() == goal.length() && (s + s).contains(goal);
}
```

### M10. Find All Anagrams in a String
Return all start indices of anagram substrings of pattern `p` within string `s`.  
**Hint:** Sliding window of fixed size `p.length()`; maintain a frequency delta count; a match is when `delta == 0`.  
**Complexity:** O(n) time, O(1) space (26 chars).

```java
public List<Integer> findAnagrams(String s, String p) {
    List<Integer> res = new ArrayList<>();
    if (s.length() < p.length()) return res;
    int[] count = new int[26]; int diff = 0;
    for (char c : p.toCharArray()) { count[c - 'a']++; }
    for (int i = 0; i < p.length(); i++) { if (--count[s.charAt(i) - 'a'] == 0) diff--; else if (count[s.charAt(i)-'a'] == -1) diff++; }
    // simpler approach:
    int[] pc = new int[26], sc = new int[26];
    for (char c : p.toCharArray()) pc[c - 'a']++;
    for (int i = 0; i < s.length(); i++) {
        sc[s.charAt(i) - 'a']++;
        if (i >= p.length()) sc[s.charAt(i - p.length()) - 'a']--;
        if (Arrays.equals(pc, sc)) res.add(i - p.length() + 1);
    }
    return res;
}
```

---

## 🔴 Hard (5)

### H1. Longest Palindromic Substring — Manacher's Algorithm
Find the longest palindromic substring in O(n) time.  
**Hint:** Manacher's algorithm: transform string with `#` separators; maintain center and right boundary of the rightmost palindrome to expand in O(1) amortized.  
**Complexity:** O(n) time, O(n) space.

```java
public String longestPalindromeManacher(String s) {
    // Transform: "abc" -> "#a#b#c#"
    StringBuilder t = new StringBuilder("#");
    for (char c : s.toCharArray()) { t.append(c); t.append('#'); }
    int n = t.length();
    int[] p = new int[n]; int center = 0, right = 0;
    int bestLen = 0, bestCenter = 0;
    for (int i = 0; i < n; i++) {
        int mirror = 2 * center - i;
        if (i < right) p[i] = Math.min(right - i, p[mirror]);
        while (i - p[i] - 1 >= 0 && i + p[i] + 1 < n && t.charAt(i - p[i] - 1) == t.charAt(i + p[i] + 1)) p[i]++;
        if (i + p[i] > right) { center = i; right = i + p[i]; }
        if (p[i] > bestLen) { bestLen = p[i]; bestCenter = i; }
    }
    int start = (bestCenter - bestLen) / 2;
    return s.substring(start, start + bestLen);
}
```

### H2. Regular Expression Matching
Implement regex matching with `.` (any single char) and `*` (zero or more of preceding).  
**Hint:** DP table `dp[i][j]` = s[0..i-1] matches p[0..j-1]. `*` handles zero-match (dp[i][j-2]) or one-more-match.  
**Complexity:** O(m * n) time, O(m * n) space.

```java
public boolean isMatch(String s, String p) {
    int m = s.length(), n = p.length();
    boolean[][] dp = new boolean[m + 1][n + 1];
    dp[0][0] = true;
    for (int j = 2; j <= n; j += 2) if (p.charAt(j - 1) == '*') dp[0][j] = dp[0][j - 2];
    for (int i = 1; i <= m; i++)
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1), sc = s.charAt(i - 1);
            if (pc == '*') {
                dp[i][j] = dp[i][j - 2]; // zero occurrences
                if (p.charAt(j - 2) == '.' || p.charAt(j - 2) == sc) dp[i][j] |= dp[i - 1][j];
            } else dp[i][j] = dp[i - 1][j - 1] && (pc == '.' || pc == sc);
        }
    return dp[m][n];
}
```

### H3. Wildcard Matching
Implement wildcard matching with `?` (any single char) and `*` (any sequence including empty).  
**Hint:** DP similar to regex, but `*` matches any sequence: `dp[i][j] = dp[i-1][j] || dp[i][j-1]`.  
**Complexity:** O(m * n) time, O(m * n) space.

```java
public boolean isMatchWildcard(String s, String p) {
    int m = s.length(), n = p.length();
    boolean[][] dp = new boolean[m + 1][n + 1];
    dp[0][0] = true;
    for (int j = 1; j <= n; j++) if (p.charAt(j - 1) == '*') dp[0][j] = dp[0][j - 1];
    for (int i = 1; i <= m; i++)
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j - 1);
            if (pc == '*') dp[i][j] = dp[i - 1][j] || dp[i][j - 1];
            else dp[i][j] = dp[i - 1][j - 1] && (pc == '?' || pc == s.charAt(i - 1));
        }
    return dp[m][n];
}
```

### H4. Minimum Window Substring — Follow-up: All Permutations
Find ALL starting indices in `s` where any permutation of `t` appears as a substring (essentially Find All Anagrams, but emphasizing the harder generalisation with large alphabets/Unicode, using a full sliding window).  
**Hint:** HashMap sliding window; `formed` counter tracks how many unique chars are fully satisfied; shrink left when window is exactly `t.length()`.  
**Complexity:** O(n) time, O(|Σ|) space.

```java
public List<Integer> findAllPermutations(String s, String t) {
    List<Integer> res = new ArrayList<>();
    if (s.length() < t.length()) return res;
    Map<Character, Integer> need = new HashMap<>(), have = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    int required = need.size(), formed = 0, l = 0;
    for (int r = 0; r < s.length(); r++) {
        char c = s.charAt(r); have.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && have.get(c).equals(need.get(c))) formed++;
        if (r - l + 1 == t.length()) {
            if (formed == required) res.add(l);
            char lc = s.charAt(l); have.merge(lc, -1, Integer::sum);
            if (need.containsKey(lc) && have.get(lc) < need.get(lc)) formed--;
            l++;
        }
    }
    return res;
}
```

### H5. Shortest Palindrome
Given a string, add the minimum number of characters in front to make it a palindrome.  
**Hint:** Find the longest palindromic prefix using KMP failure function on `s + '#' + reverse(s)`. The answer is `reverse(suffix) + s`.  
**Complexity:** O(n) time, O(n) space.

```java
public String shortestPalindrome(String s) {
    String rev = new StringBuilder(s).reverse().toString();
    String combined = s + "#" + rev;
    int[] kmp = new int[combined.length()];
    for (int i = 1; i < combined.length(); i++) {
        int j = kmp[i - 1];
        while (j > 0 && combined.charAt(i) != combined.charAt(j)) j = kmp[j - 1];
        if (combined.charAt(i) == combined.charAt(j)) j++;
        kmp[i] = j;
    }
    int palinPrefixLen = kmp[combined.length() - 1];
    return rev.substring(0, s.length() - palinPrefixLen) + s;
}
```
