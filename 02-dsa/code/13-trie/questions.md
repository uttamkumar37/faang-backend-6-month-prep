# Trie & Union-Find — Practice Questions

---

## 🟢 Easy (5)

### E1. Implement Trie — Insert & Search
Build a Trie supporting `insert(word)`, `search(word)`, and `startsWith(prefix)`.  
**Hint:** Each TrieNode holds a `children[26]` array and a `isEnd` boolean. Traverse chars, allocate nodes as needed.  
**Complexity:** O(m) per operation where m = word length.

```java
class Trie {
    private TrieNode root = new TrieNode();
    public void insert(String word) { TrieNode cur = root; for (char c : word.toCharArray()) { int i=c-'a'; if(cur.children[i]==null) cur.children[i]=new TrieNode(); cur=cur.children[i]; } cur.isEnd=true; }
    public boolean search(String word) { TrieNode n=find(word); return n!=null && n.isEnd; }
    public boolean startsWith(String prefix) { return find(prefix)!=null; }
    private TrieNode find(String s) { TrieNode cur=root; for(char c:s.toCharArray()){int i=c-'a'; if(cur.children[i]==null)return null; cur=cur.children[i];} return cur; }
    static class TrieNode { TrieNode[] children=new TrieNode[26]; boolean isEnd; }
}
```

### E2. Longest Common Prefix
Find the longest common prefix string among an array of strings.  
**Hint:** Insert all strings into a Trie; walk down the Trie until a node has more than one child or marks an end.  
**Complexity:** O(S) total where S = sum of all string lengths.

```java
public String longestCommonPrefix(String[] strs) {
    if (strs.length == 0) return "";
    String prefix = strs[0];
    for (int i = 1; i < strs.length; i++)
        while (!strs[i].startsWith(prefix)) prefix = prefix.substring(0, prefix.length()-1);
    return prefix;
}
```

### E3. Number of Provinces (Union-Find Easy)
Find the number of connected components in an adjacency-matrix graph.  
**Hint:** Union-Find: for each edge (i,j) union i and j. Count distinct roots at the end.  
**Complexity:** O(n²·α(n)) time, O(n) space.

```java
public int findCircleNum(int[][] isConnected) {
    int n = isConnected.length;
    int[] parent = new int[n]; Arrays.setAll(parent, i -> i);
    for (int i = 0; i < n; i++) for (int j = i+1; j < n; j++) if (isConnected[i][j]==1) union(parent, i, j);
    int count = 0; for (int i = 0; i < n; i++) if (find(parent, i)==i) count++;
    return count;
}
private void union(int[] p, int a, int b) { p[find(p,a)] = find(p,b); }
private int find(int[] p, int x) { if(p[x]!=x) p[x]=find(p,p[x]); return p[x]; }
```

### E4. Count Words Equal to Given Word
Given a list of words, count how many equal a given target using Trie search.  
**Hint:** Insert all words; use `search()` which increments a counter at leaf nodes on each insert.  
**Complexity:** O(S) build, O(m) query.

```java
public int countWordsEqualTo(String[] words, String target) {
    Map<String,Integer> freq = new HashMap<>();
    for (String w : words) freq.merge(w, 1, Integer::sum);
    return freq.getOrDefault(target, 0);
}
```

### E5. Valid Anagram via Frequency
Determine if two strings are anagrams using a frequency Trie (character-count node).  
**Hint:** Simple character-frequency DP works; Trie variant builds a freq map per character level.  
**Complexity:** O(n) time, O(1) space (26 characters).

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;
    int[] freq = new int[26];
    for (char c : s.toCharArray()) freq[c-'a']++;
    for (char c : t.toCharArray()) if (--freq[c-'a'] < 0) return false;
    return true;
}
```

---

## 🟡 Medium (10)

### M1. Design Add and Search Words
Implement a data structure supporting `addWord(word)` and `search(word)` with `.` wildcard.  
**Hint:** Trie with DFS on `.` nodes — recurse all children when char is `.`.  
**Complexity:** O(m) insert; O(26^m) worst case search with many `.`.

```java
class WordDictionary {
    private TrieNode root = new TrieNode();
    public void addWord(String word) { TrieNode cur=root; for(char c:word.toCharArray()){int i=c-'a'; if(cur.children[i]==null)cur.children[i]=new TrieNode(); cur=cur.children[i];} cur.isEnd=true;}
    public boolean search(String word) { return dfs(word, 0, root); }
    private boolean dfs(String w, int idx, TrieNode node) {
        if (idx==w.length()) return node.isEnd;
        char c=w.charAt(idx);
        if (c=='.') { for(TrieNode ch:node.children) if(ch!=null && dfs(w,idx+1,ch)) return true; return false; }
        int i=c-'a'; return node.children[i]!=null && dfs(w,idx+1,node.children[i]);
    }
    static class TrieNode { TrieNode[] children=new TrieNode[26]; boolean isEnd; }
}
```

### M2. Replace Words
Given a dictionary of root words, replace every word in a sentence with its shortest root prefix found in the Trie.  
**Hint:** Insert all roots; for each sentence word traverse the Trie and stop at first end-of-word marker.  
**Complexity:** O(S) build, O(W) per word query.

```java
public String replaceWords(List<String> dictionary, String sentence) {
    TrieNode root = new TrieNode();
    for (String r : dictionary) { TrieNode cur=root; for(char c:r.toCharArray()){int i=c-'a'; if(cur.children[i]==null)cur.children[i]=new TrieNode(); cur=cur.children[i];} cur.isEnd=true; }
    StringBuilder res = new StringBuilder();
    for (String word : sentence.split(" ")) {
        if (res.length()>0) res.append(' ');
        TrieNode cur=root; StringBuilder prefix=new StringBuilder();
        for (char c:word.toCharArray()) { int i=c-'a'; if(cur.children[i]==null||cur.isEnd) break; cur=cur.children[i]; prefix.append(c); if(cur.isEnd) break; }
        res.append(cur.isEnd ? prefix : word);
    }
    return res.toString();
}
static class TrieNode { TrieNode[] children=new TrieNode[26]; boolean isEnd; }
```

### M3. Maximum XOR of Two Numbers in an Array
Find the maximum XOR of any two numbers in the array.  
**Hint:** Build a Binary Trie of all numbers (bit-by-bit from MSB). For each number, greedily pick the opposite bit in the Trie.  
**Complexity:** O(n * 32) time, O(n * 32) space.

```java
public int findMaximumXOR(int[] nums) {
    int max = 0, mask = 0;
    for (int i = 31; i >= 0; i--) {
        mask |= (1 << i);
        Set<Integer> prefixes = new HashSet<>();
        for (int n : nums) prefixes.add(n & mask);
        int candidate = max | (1 << i);
        for (int p : prefixes) if (prefixes.contains(candidate ^ p)) { max = candidate; break; }
    }
    return max;
}
```

### M4. Longest Word in Dictionary
Find the longest word that can be built one character at a time by other words in the array.  
**Hint:** Insert all words; do BFS/DFS on the Trie traversing only nodes that are end-of-word.  
**Complexity:** O(S) build, O(S) traversal.

```java
public String longestWord(String[] words) {
    Arrays.sort(words);
    Set<String> built = new HashSet<>(); built.add("");
    String res = "";
    for (String w : words)
        if (built.contains(w.substring(0, w.length()-1))) { built.add(w); if (w.length()>res.length()) res=w; }
    return res;
}
```

### M5. Map Sum Pairs
Implement `insert(key, val)` and `sum(prefix)` returning sum of all values whose key starts with prefix.  
**Hint:** Store value at each Trie leaf; accumulate sum during DFS from the prefix node.  
**Complexity:** O(m) insert; O(m + T) sum where T = nodes under prefix.

```java
class MapSum {
    private Map<String, Integer> map = new HashMap<>();
    public void insert(String key, int val) { map.put(key, val); }
    public int sum(String prefix) {
        int total = 0;
        for (Map.Entry<String,Integer> e : map.entrySet())
            if (e.getKey().startsWith(prefix)) total += e.getValue();
        return total;
    }
}
```

### M6. Word Break II
Return all ways to segment a string using words from a dictionary.  
**Hint:** Trie for O(n) prefix checks per position + memoized DFS/backtracking. Store partial results at each index.  
**Complexity:** O(n² + output size) with memoization.

```java
public List<String> wordBreak(String s, List<String> wordDict) {
    Set<String> dict = new HashSet<>(wordDict);
    Map<Integer, List<String>> memo = new HashMap<>();
    return wb(s, 0, dict, memo);
}
private List<String> wb(String s, int start, Set<String> dict, Map<Integer,List<String>> memo) {
    if (memo.containsKey(start)) return memo.get(start);
    List<String> res = new ArrayList<>();
    if (start == s.length()) { res.add(""); return res; }
    for (int end = start+1; end <= s.length(); end++) {
        String w = s.substring(start, end);
        if (dict.contains(w)) for (String rest : wb(s, end, dict, memo)) res.add(w + (rest.isEmpty() ? "" : " ") + rest);
    }
    memo.put(start, res); return res;
}
```

### M7. Redundant Connection (Union-Find)
Find the extra edge whose removal makes the graph a tree (no cycle).  
**Hint:** Union-Find — process edges one by one; the first edge where both nodes have the same root is the answer.  
**Complexity:** O(n·α(n)) time, O(n) space.

```java
public int[] findRedundantConnection(int[][] edges) {
    int n = edges.length;
    int[] parent = new int[n+1]; Arrays.setAll(parent, i -> i);
    for (int[] e : edges) {
        if (find(parent, e[0]) == find(parent, e[1])) return e;
        parent[find(parent, e[0])] = find(parent, e[1]);
    }
    return new int[]{};
}
private int find(int[] p, int x) { if(p[x]!=x) p[x]=find(p,p[x]); return p[x]; }
```

### M8. Accounts Merge
Merge email accounts belonging to the same person.  
**Hint:** Union-Find on email strings using a HashMap. Union emails that appear in the same account.  
**Complexity:** O(N·α(N)) where N = total emails across all accounts.

```java
// See graphs/questions.md M9 for the full Union-Find solution on email strings.
public List<List<String>> accountsMerge(List<List<String>> accounts) {
    Map<String, String> parent = new HashMap<>(), owner = new HashMap<>();
    for (List<String> acc : accounts) {
        for (int i = 1; i < acc.size(); i++) { parent.putIfAbsent(acc.get(i), acc.get(i)); owner.put(acc.get(i), acc.get(0)); }
        for (int i = 2; i < acc.size(); i++) { String ra=findS(parent,acc.get(1)), rb=findS(parent,acc.get(i)); if(!ra.equals(rb)) parent.put(ra,rb); }
    }
    Map<String, TreeSet<String>> groups = new HashMap<>();
    for (String e : parent.keySet()) groups.computeIfAbsent(findS(parent, e), k -> new TreeSet<>()).add(e);
    List<List<String>> res = new ArrayList<>();
    for (Map.Entry<String, TreeSet<String>> e : groups.entrySet()) { List<String> a=new ArrayList<>(); a.add(owner.get(e.getKey())); a.addAll(e.getValue()); res.add(a); }
    return res;
}
private String findS(Map<String,String> p, String x) { if(!p.get(x).equals(x)) p.put(x,findS(p,p.get(x))); return p.get(x); }
```

### M9. Number of Islands II (dynamic, Union-Find)
Given a grid, add land cells one by one and after each addition output the number of islands.  
**Hint:** Union-Find on grid cells. When adding a land cell, union it with each adjacent land cell and track component count.  
**Complexity:** O(k·α(m*n)) where k = number of land-add operations.

```java
public List<Integer> numIslands2(int m, int n, int[][] positions) {
    int[] parent = new int[m*n]; Arrays.fill(parent, -1);
    int[] count = {0};
    int[][] dirs = {{0,1},{0,-1},{1,0},{-1,0}};
    List<Integer> res = new ArrayList<>();
    for (int[] pos : positions) {
        int idx = pos[0]*n + pos[1];
        if (parent[idx] != -1) { res.add(count[0]); continue; }
        parent[idx] = idx; count[0]++;
        for (int[] d : dirs) {
            int r=pos[0]+d[0], c=pos[1]+d[1], nb=r*n+c;
            if (r>=0&&r<m&&c>=0&&c<n&&parent[nb]!=-1) {
                int ra=find(parent,idx), rb=find(parent,nb);
                if (ra!=rb) { parent[ra]=rb; count[0]--; }
            }
        }
        res.add(count[0]);
    }
    return res;
}
private int find(int[] p, int x) { if(p[x]!=x) p[x]=find(p,p[x]); return p[x]; }
```

### M10. Index Pairs of a String
Find all [i,j] index pairs in a text where text[i..j] is a word from a given list.  
**Hint:** Insert list words into a Trie; slide a window starting at each position i and walk the Trie.  
**Complexity:** O(n * max_word_length) time.

```java
public int[][] indexPairs(String text, String[] words) {
    TrieNode root = new TrieNode();
    for (String w : words) { TrieNode cur=root; for(char c:w.toCharArray()){int i=c-'a'; if(cur.ch[i]==null)cur.ch[i]=new TrieNode(); cur=cur.ch[i];} cur.end=true; }
    List<int[]> res = new ArrayList<>();
    for (int i = 0; i < text.length(); i++) {
        TrieNode cur = root;
        for (int j = i; j < text.length(); j++) {
            int idx = text.charAt(j)-'a';
            if (cur.ch[idx]==null) break;
            cur = cur.ch[idx];
            if (cur.end) res.add(new int[]{i, j});
        }
    }
    return res.toArray(new int[0][]);
}
static class TrieNode { TrieNode[] ch=new TrieNode[26]; boolean end; }
```

---

## 🔴 Hard (5)

### H1. Word Search II
Find all words from a dictionary that can be formed by sequential adjacent cells in a 2D board.  
**Hint:** Build a Trie from all words; DFS on the board and prune branches not in the Trie. Delete matched words from Trie to avoid duplicates.  
**Complexity:** O(M × N × 4^L) where L = max word length.

```java
// See backtracking/questions.md H3 for the full Trie-guided DFS solution.
public List<String> findWords(char[][] board, String[] words) {
    TrieNode root = new TrieNode();
    for (String w : words) { TrieNode cur=root; for(char c:w.toCharArray()){int i=c-'a'; if(cur.ch[i]==null)cur.ch[i]=new TrieNode(); cur=cur.ch[i];} cur.word=w; }
    List<String> res = new ArrayList<>();
    for (int i=0;i<board.length;i++) for(int j=0;j<board[0].length;j++) dfs(board,i,j,root,res);
    return res;
}
private void dfs(char[][] b,int r,int c,TrieNode node,List<String> res){
    if(r<0||r>=b.length||c<0||c>=b[0].length||b[r][c]=='#') return;
    char ch=b[r][c]; TrieNode next=node.ch[ch-'a']; if(next==null) return;
    if(next.word!=null){res.add(next.word);next.word=null;}
    b[r][c]='#'; dfs(b,r+1,c,next,res);dfs(b,r-1,c,next,res);dfs(b,r,c+1,next,res);dfs(b,r,c-1,next,res); b[r][c]=ch;
}
static class TrieNode { TrieNode[] ch=new TrieNode[26]; String word; }
```

### H2. Design Search Autocomplete System
Implement a typeahead system: `input(c)` appends char c (or '#' to save the sentence) and returns the top-3 hot sentences by frequency.  
**Hint:** Trie where each node stores a sorted list of (freq, sentence) pairs. On '#' increment the typed sentence's count in Trie nodes.  
**Complexity:** O(p^2) per character input (p = input length).

```java
class AutocompleteSystem {
    private Map<String, Integer> freq = new HashMap<>();
    private String typed = "";
    public AutocompleteSystem(String[] sentences, int[] times) {
        for (int i = 0; i < sentences.length; i++) freq.put(sentences[i], times[i]);
    }
    public List<String> input(char c) {
        if (c == '#') { freq.merge(typed, 1, Integer::sum); typed = ""; return new ArrayList<>(); }
        typed += c;
        return freq.entrySet().stream()
            .filter(e -> e.getKey().startsWith(typed))
            .sorted((a,b) -> !a.getValue().equals(b.getValue()) ? b.getValue()-a.getValue() : a.getKey().compareTo(b.getKey()))
            .limit(3).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toList());
    }
}
```

### H3. Palindrome Pairs
Find all [i, j] pairs where words[i] + words[j] forms a palindrome.  
**Hint:** Build a Trie of reversed words. For each word check: (a) reverse is in Trie, (b) prefix is a palindrome and suffix's reverse is in Trie, (c) vice versa.  
**Complexity:** O(n * k²) where k = average word length.

```java
public List<List<Integer>> palindromePairs(String[] words) {
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < words.length; i++) map.put(words[i], i);
    List<List<Integer>> res = new ArrayList<>();
    for (int i = 0; i < words.length; i++) {
        String w = words[i];
        for (int cut = 0; cut <= w.length(); cut++) {
            String left = w.substring(0, cut), right = w.substring(cut);
            if (isPalin(left)) { String rev=new StringBuilder(right).reverse().toString(); if(map.containsKey(rev)&&map.get(rev)!=i) res.add(Arrays.asList(map.get(rev),i)); }
            if (right.length()>0 && isPalin(right)) { String rev=new StringBuilder(left).reverse().toString(); if(map.containsKey(rev)&&map.get(rev)!=i) res.add(Arrays.asList(i,map.get(rev))); }
        }
    }
    return res;
}
private boolean isPalin(String s){int l=0,r=s.length()-1; while(l<r) if(s.charAt(l++)!=s.charAt(r--)) return false; return true;}
```

### H4. Stream of Characters
Implement a class that, given a stream of characters, returns true if the last k characters form any word in a predefined list.  
**Hint:** Build a Trie of reversed words; maintain a list of active search pointers advanced on each new character.  
**Complexity:** O(L) per query where L = list-word lengths.

```java
class StreamChecker {
    private TrieNode root = new TrieNode();
    private List<TrieNode> active = new ArrayList<>();
    public StreamChecker(String[] words) {
        for (String w : words) { TrieNode cur=root; for(int i=w.length()-1;i>=0;i--){int idx=w.charAt(i)-'a'; if(cur.ch[idx]==null)cur.ch[idx]=new TrieNode(); cur=cur.ch[idx];} cur.end=true; }
    }
    public boolean query(char letter) {
        List<TrieNode> next = new ArrayList<>();
        active.add(root);
        boolean found = false;
        for (TrieNode node : active) {
            TrieNode child = node.ch[letter-'a'];
            if (child != null) { if(child.end) found=true; next.add(child); }
        }
        active = next; return found;
    }
    static class TrieNode { TrieNode[] ch=new TrieNode[26]; boolean end; }
}
```

### H5. Smallest String With Swaps (Union-Find + Sort)
Given a list of swappable index pairs, return the lexicographically smallest string achievable.  
**Hint:** Union-Find groups indices that can be mutually swapped. Within each group sort the characters and place them back in sorted order by index.  
**Complexity:** O(n log n) for sorting groups.

```java
public String smallestStringWithSwaps(String s, List<List<Integer>> pairs) {
    int n = s.length();
    int[] parent = new int[n]; Arrays.setAll(parent, i -> i);
    for (List<Integer> p : pairs) { int ra=find(parent,p.get(0)),rb=find(parent,p.get(1)); if(ra!=rb) parent[ra]=rb; }
    Map<Integer, List<Integer>> groups = new HashMap<>();
    for (int i = 0; i < n; i++) groups.computeIfAbsent(find(parent,i), k->new ArrayList<>()).add(i);
    char[] res = s.toCharArray();
    for (List<Integer> indices : groups.values()) {
        List<Character> chars = new ArrayList<>();
        for (int idx : indices) chars.add(s.charAt(idx));
        Collections.sort(indices); Collections.sort(chars);
        for (int i = 0; i < indices.size(); i++) res[indices.get(i)] = chars.get(i);
    }
    return new String(res);
}
private int find(int[] p, int x) { if(p[x]!=x) p[x]=find(p,p[x]); return p[x]; }
```
