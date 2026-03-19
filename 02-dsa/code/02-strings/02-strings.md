# Strings — Complete Theory (Basic → Advanced)

---

## 1. String Fundamentals

A string is a **sequence of characters**. In Java, `String` is an **immutable** object backed by a `char[]` (UTF-16 internally).

```java
String s = "hello";
// s is immutable — every "modification" creates a new object
s = s + " world";   // creates a NEW String — O(n) cost!

// char access
char c = s.charAt(0);    // O(1)
int len = s.length();    // O(1)
String sub = s.substring(l, r); // O(r-l) — copies characters

// Comparison
s.equals("hello");          // content equality ✅
s == "hello";               // reference equality ❌ (use only for interned literals)
s.equalsIgnoreCase("Hello");
s.compareTo("hello");       // lexicographic: negative/0/positive
```

### Immutability Impact on Performance

```java
// ❌ O(n²) — each += creates a new String copying all previous chars
String res = "";
for (String w : words) res += w;

// ✅ O(n) — StringBuilder uses a resizable char[]
StringBuilder sb = new StringBuilder();
for (String w : words) sb.append(w);
String result = sb.toString();
```

---

## 2. Character Types & Utilities

```java
Character.isLetter('a')          // true
Character.isDigit('3')           // true
Character.isLetterOrDigit('_')   // false
Character.isWhitespace(' ')      // true
Character.isUpperCase('A')       // true
Character.toLowerCase('A')       // 'a'
Character.toUpperCase('a')       // 'A'

// ASCII values (memorise these)
'a' = 97,  'z' = 122
'A' = 65,  'Z' = 90
'0' = 48,  '9' = 57

// Convert char ↔ index
int idx = c - 'a';   // 'a'→0, 'b'→1, ..., 'z'→25
char c   = (char)(idx + 'a');

// Char to int digit
int d = c - '0';     // '3' → 3
```

---

## 3. Core String Patterns

### 3.1 Frequency Count (Character Histogram)

```java
// Fixed alphabet (lowercase letters) — O(1) space
int[] freq = new int[26];
for (char c : s.toCharArray()) freq[c - 'a']++;

// Arbitrary characters — HashMap
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);

// Canonical form for anagram grouping
char[] ca = s.toCharArray();
Arrays.sort(ca);
String key = new String(ca);   // sorted chars as key
```

---

### 3.2 Two Pointers on Strings

```java
// Palindrome check
boolean isPalindrome(String s) {
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
void reverseString(char[] s) {
    int l = 0, r = s.length - 1;
    while (l < r) { char t = s[l]; s[l++] = s[r]; s[r--] = t; }
}
```

---

### 3.3 Fixed-Size Sliding Window (Anagram Detection)

```java
// Find all anagram start indices of pattern p in string s
public List<Integer> findAnagrams(String s, String p) {
    List<Integer> res = new ArrayList<>();
    if (s.length() < p.length()) return res;
    int[] pc = new int[26], wc = new int[26];
    for (char c : p.toCharArray()) pc[c-'a']++;
    for (int i = 0; i < s.length(); i++) {
        wc[s.charAt(i)-'a']++;
        if (i >= p.length()) wc[s.charAt(i-p.length())-'a']--;
        if (Arrays.equals(pc, wc)) res.add(i - p.length() + 1);
    }
    return res;
}
```

---

### 3.4 Variable-Size Sliding Window (Minimum Window Substring)

Maintain a **`formed` counter** to know when the window satisfies constraints.

```java
public String minWindow(String s, String t) {
    Map<Character,Integer> need = new HashMap<>(), have = new HashMap<>();
    for (char c : t.toCharArray()) need.merge(c, 1, Integer::sum);
    int formed = 0, required = need.size(), l = 0, minLen = Integer.MAX_VALUE, minL = 0;
    for (int r = 0; r < s.length(); r++) {
        char c = s.charAt(r);
        have.merge(c, 1, Integer::sum);
        if (need.containsKey(c) && have.get(c).equals(need.get(c))) formed++;
        while (formed == required) {                          // valid window — try to shrink
            if (r - l + 1 < minLen) { minLen = r - l + 1; minL = l; }
            char lc = s.charAt(l++);
            have.merge(lc, -1, Integer::sum);
            if (need.containsKey(lc) && have.get(lc) < need.get(lc)) formed--;
        }
    }
    return minLen == Integer.MAX_VALUE ? "" : s.substring(minL, minL + minLen);
}
```

---

### 3.5 Longest Substring Without Repeating Characters

```java
public int lengthOfLongestSubstring(String s) {
    Map<Character,Integer> lastSeen = new HashMap<>();
    int max = 0, left = 0;
    for (int right = 0; right < s.length(); right++) {
        char c = s.charAt(right);
        if (lastSeen.containsKey(c))
            left = Math.max(left, lastSeen.get(c) + 1);  // jump left past duplicate
        lastSeen.put(c, right);
        max = Math.max(max, right - left + 1);
    }
    return max;
}
```

---

### 3.6 Expand Around Center — Palindromic Substrings

For each center (n odd-length + n-1 even-length = 2n-1 total):

```java
public String longestPalindrome(String s) {
    int start = 0, maxLen = 1;
    for (int i = 0; i < s.length(); i++) {
        // try both odd (i,i) and even (i,i+1) centers
        for (int[] c : new int[][]{{i,i},{i,i+1}}) {
            int l = c[0], r = c[1];
            while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) { l--; r++; }
            if (r - l - 1 > maxLen) { maxLen = r - l - 1; start = l + 1; }
        }
    }
    return s.substring(start, start + maxLen);
}
```

---

### 3.7 KMP — O(n) Pattern Matching

**Key idea**: the failure function tells us how much of the *prefix* we can reuse when a mismatch occurs, avoiding redundant re-comparisons.

```
Pattern:  a b a b c a b
kmp[]:    0 0 1 2 0 1 2

kmp[i] = length of longest proper prefix of pattern[0..i] that is also a suffix
```

```java
int[] buildKMP(String p) {
    int[] kmp = new int[p.length()];
    int j = 0;
    for (int i = 1; i < p.length(); i++) {
        while (j > 0 && p.charAt(i) != p.charAt(j)) j = kmp[j-1];
        if (p.charAt(i) == p.charAt(j)) j++;
        kmp[i] = j;
    }
    return kmp;
}

List<Integer> kmpSearch(String text, String pattern) {
    int[] kmp = buildKMP(pattern);
    List<Integer> res = new ArrayList<>();
    int j = 0;
    for (int i = 0; i < text.length(); i++) {
        while (j > 0 && text.charAt(i) != pattern.charAt(j)) j = kmp[j-1];
        if (text.charAt(i) == pattern.charAt(j)) j++;
        if (j == pattern.length()) { res.add(i - j + 1); j = kmp[j-1]; }
    }
    return res;
}
```

**KMP trick — shortest palindrome / rotation**:
- Check if `goal` is a rotation of `s`: test if `goal` is in `s + s`
- KMP on `s + "#" + reverse(s)` finds longest palindromic prefix

---

### 3.8 Rabin-Karp — Rolling Hash

O(n+m) expected pattern matching using polynomial hashing.

```java
// Rolling hash: hash(s[i..i+m]) = (hash(s[i-1..i+m-1]) - s[i-1]*base^m) * base + s[i+m]
long rollingHash(String s, int start, int len, long base, long mod) {
    long h = 0, pow = 1;
    for (int i = start + len - 1; i >= start; i--) {
        h = (h + s.charAt(i) * pow) % mod;
        pow = pow * base % mod;
    }
    return h;
}
```

---

## 4. String Encoding & Manipulation

### 4.1 Encode/Decode (Length-Prefix)

```java
// Encode: "4#word3#cat" → ["word", "cat"]
String encode(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    for (String s : strs) sb.append(s.length()).append('#').append(s);
    return sb.toString();
}
List<String> decode(String s) {
    List<String> res = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
        int j = s.indexOf('#', i);
        int len = Integer.parseInt(s.substring(i, j));
        res.add(s.substring(j+1, j+1+len));
        i = j + 1 + len;
    }
    return res;
}
```

### 4.2 Reverse Words in a Sentence

```java
// "the sky is blue" → "blue is sky the"
public String reverseWords(String s) {
    String[] words = s.trim().split("\\s+");
    int l = 0, r = words.length - 1;
    while (l < r) { String t = words[l]; words[l++] = words[r]; words[r--] = t; }
    return String.join(" ", words);
}
```

---

## 5. Advanced Algorithms

### 5.1 Manacher's Algorithm — O(n) Longest Palindrome

Transform `"abc"` → `"#a#b#c#"` (always odd length). Maintain the rightmost palindrome boundary to expand in O(1) amortised.

```
p[i] = radius of palindrome centred at i in transformed string
```

```java
public String manacher(String s) {
    StringBuilder t = new StringBuilder("#");
    for (char c : s.toCharArray()) { t.append(c); t.append('#'); }
    int n = t.length();
    int[] p = new int[n]; int center = 0, right = 0;
    int bestLen = 0, bestCenter = 0;
    for (int i = 0; i < n; i++) {
        int mirror = 2 * center - i;
        if (i < right) p[i] = Math.min(right - i, p[mirror]);
        while (i - p[i] - 1 >= 0 && i + p[i] + 1 < n
               && t.charAt(i-p[i]-1) == t.charAt(i+p[i]+1)) p[i]++;
        if (i + p[i] > right) { center = i; right = i + p[i]; }
        if (p[i] > bestLen) { bestLen = p[i]; bestCenter = i; }
    }
    return s.substring((bestCenter - bestLen) / 2, (bestCenter + bestLen) / 2);
}
```

### 5.2 Z-Algorithm — O(n) Prefix-based Matching

`Z[i]` = length of longest substring starting at `i` which is also a prefix of the string.

```java
int[] zFunction(String s) {
    int n = s.length();
    int[] z = new int[n]; z[0] = n;
    int l = 0, r = 0;
    for (int i = 1; i < n; i++) {
        if (i < r) z[i] = Math.min(r - i, z[i - l]);
        while (i + z[i] < n && s.charAt(z[i]) == s.charAt(i + z[i])) z[i]++;
        if (i + z[i] > r) { l = i; r = i + z[i]; }
    }
    return z;
}
// Pattern matching: build "pattern + '$' + text", find Z[i] == pattern.length()
```

### 5.3 Suffix Array (concept)

A **suffix array** SA is the sorted order of all suffixes. Combined with the LCP array it enables O(n log n) construction and O(m) pattern matching.

```
s = "banana"
Suffixes sorted:  a, ana, anana, banana, na, nana
SA =              [5, 3, 1, 0, 4, 2]
LCP array tells: longest common prefix between adjacent sorted suffixes
```

---

## 6. DP on Strings

### 6.1 Longest Common Subsequence (LCS)

```java
int lcs(String a, String b) {
    int m = a.length(), n = b.length();
    int[][] dp = new int[m+1][n+1];
    for (int i = 1; i <= m; i++)
        for (int j = 1; j <= n; j++)
            dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                       ? dp[i-1][j-1] + 1
                       : Math.max(dp[i-1][j], dp[i][j-1]);
    return dp[m][n];
}
```

### 6.2 Edit Distance (Levenshtein)

```java
int editDistance(String a, String b) {
    int m = a.length(), n = b.length();
    int[][] dp = new int[m+1][n+1];
    for (int i = 0; i <= m; i++) dp[i][0] = i;
    for (int j = 0; j <= n; j++) dp[0][j] = j;
    for (int i = 1; i <= m; i++)
        for (int j = 1; j <= n; j++)
            dp[i][j] = a.charAt(i-1) == b.charAt(j-1)
                       ? dp[i-1][j-1]
                       : 1 + Math.min(dp[i-1][j-1], Math.min(dp[i-1][j], dp[i][j-1]));
    return dp[m][n];
}
```

### 6.3 Regular Expression Matching

```java
boolean isMatch(String s, String p) {
    int m = s.length(), n = p.length();
    boolean[][] dp = new boolean[m+1][n+1];
    dp[0][0] = true;
    for (int j = 2; j <= n; j += 2) if (p.charAt(j-1)=='*') dp[0][j] = dp[0][j-2];
    for (int i = 1; i <= m; i++)
        for (int j = 1; j <= n; j++) {
            char pc = p.charAt(j-1);
            if (pc == '*') {
                dp[i][j] = dp[i][j-2];  // zero of preceding
                if (p.charAt(j-2)=='.' || p.charAt(j-2)==s.charAt(i-1)) dp[i][j] |= dp[i-1][j];
            } else dp[i][j] = dp[i-1][j-1] && (pc=='.' || pc==s.charAt(i-1));
        }
    return dp[m][n];
}
```

---

## 7. Decision Guide

| Scenario | Pattern |
|---|---|
| Anagram check | Frequency array comparison |
| All anagram positions | Fixed sliding window + freq array |
| Minimum window with all chars | Variable sliding window + `formed` |
| Longest substring no repeat | Sliding window with last-seen map |
| Palindrome check | Two pointers |
| Longest palindromic substring | Expand-around-center O(n²) or Manacher O(n) |
| Pattern search | KMP (O(n+m)) or Rabin-Karp (rolling hash) |
| All occurrences of many patterns | Aho-Corasick trie (advanced) |
| Transform / edit one string to another | Edit distance DP |
| Longest common subsequence | LCS DP |
| Shortest palindrome | KMP on `s+"#"+reverse(s)` |
| String rotation | `s+s` contains `goal` |

---

## 8. Common Pitfalls

- **`+= char` in loop** is O(n²) — always use `StringBuilder`
- **`substring` is O(k)** — avoid in tight inner loops
- **Shrink `left` before updating `formed`**: shrink first, then decrement counter
- **Even vs odd palindromes**: always check both `(i,i)` and `(i,i+1)` centers
- **Case folding**: use `Character.toLowerCase` before comparing
- **`split(" ")` vs `split("\\s+")`: former leaves empty strings on multiple spaces
- **Integer overflow in hashing**: use `long` and `% MOD` throughout
