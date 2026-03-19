# DSA Roadmap

Use this with the actual pattern folders under `02-dsa/code/`, especially `01-arrays/ArrayPatterns.java`, `03-twopointers/TwoPointerPatterns.java`, `04-slidingwindow/SlidingWindowPatterns.java`, and `12-dp/DynamicProgrammingPatterns.java`.
The goal is not only to solve questions, but to recognize patterns fast and explain them cleanly.

## 1. Importance tiers

### Tier 1: mandatory
- arrays and strings
- hashing
- two pointers
- sliding window
- binary search
- linked list
- stack and queue
- trees and BST
- heap / priority queue

### Tier 2: very important
- graphs
- intervals
- prefix sum
- backtracking
- trie
- union find
- recursion patterns

### Tier 3: differentiators
- dynamic programming
- advanced graph problems
- greedy proofs
- segment tree / Fenwick basics if time permits

## 2. FAANG difficulty target

For SDE II / Senior Backend:
- easy: trivial and should be near perfect
- medium: must be strong and repeatable
- hard: must be discussable and partly solvable even if not completed fast

Target readiness:
- 150-180 medium problems
- 40-60 hard problems
- at least 3 revisions of weak patterns

Real expectation for your experience level:
- You are not expected to brute-force through every hard problem from scratch.
- You are expected to recover from being stuck, use hints well, and write clean medium-level code consistently.

## 3. Must-do patterns

### Arrays / strings
- prefix sum
- difference array basics
- in-place transformation
- frequency counting

### Two pointers
- pair finding in sorted data
- palindrome / reverse traversal
- partitioning problems

### Sliding window
- longest substring with constraints
- fixed-size windows
- variable-size windows
- window with hashmap counts

Theory:
- Sliding window is valid when the problem asks for a contiguous range and the state can be updated incrementally as boundaries move.
- If adding an element and removing an element can update the window state in near-constant time, sliding window is usually a candidate.

Code example:

```java
public int longestSubstringWithoutRepeatingCharacters(String value) {
	Map<Character, Integer> lastSeen = new HashMap<>();
	int left = 0;
	int best = 0;

	for (int right = 0; right < value.length(); right++) {
		char current = value.charAt(right);
		if (lastSeen.containsKey(current) && lastSeen.get(current) >= left) {
			left = lastSeen.get(current) + 1;
		}
		lastSeen.put(current, right);
		best = Math.max(best, right - left + 1);
	}
	return best;
}
```

### Stack / monotonic stack
- next greater element
- daily temperatures style
- largest rectangle in histogram
- expression evaluation basics

Theory:
- Monotonic stacks are used when each element waits for the next greater or smaller element.
- The stack usually stores indexes, not values, because the distance between positions often matters.

### Trees
- DFS recursion patterns
- BFS level order
- LCA
- diameter
- path sum
- serialize/deserialize at conceptual level

### Graphs
- DFS/BFS traversal
- cycle detection
- topological sort
- shortest path basics
- connected components
- union-find applications

Theory:
- Use BFS when distance in edges matters and weights are uniform.
- Use DFS when you need traversal structure, recursion/backtracking, or component exploration.
- Use topological sort for dependency ordering in DAG-like problems such as course scheduling.

Code example:

```java
public List<Integer> topologicalSort(int nodeCount, int[][] edges) {
	List<List<Integer>> graph = new ArrayList<>();
	int[] indegree = new int[nodeCount];

	for (int i = 0; i < nodeCount; i++) {
		graph.add(new ArrayList<>());
	}

	for (int[] edge : edges) {
		graph.get(edge[0]).add(edge[1]);
		indegree[edge[1]]++;
	}

	Deque<Integer> queue = new ArrayDeque<>();
	for (int i = 0; i < nodeCount; i++) {
		if (indegree[i] == 0) {
			queue.offer(i);
		}
	}

	List<Integer> order = new ArrayList<>();
	while (!queue.isEmpty()) {
		int current = queue.poll();
		order.add(current);
		for (int neighbor : graph.get(current)) {
			indegree[neighbor]--;
			if (indegree[neighbor] == 0) {
				queue.offer(neighbor);
			}
		}
	}

	return order.size() == nodeCount ? order : List.of();
}
```

### Backtracking
- subsets
- permutations
- combination sum style
- pruning logic

### Dynamic programming
- 1D DP
- 2D DP
- knapsack patterns
- subsequence / substring patterns
- house robber style state compression

Theory:
- DP works when a problem has overlapping subproblems and optimal substructure.
- The most common reason people fail DP interviews is not syntax; it is poor state definition.
- Always define: state, transition, base case, and traversal order.

Code example:

```java
public int coinChange(int[] coins, int amount) {
	int[] dp = new int[amount + 1];
	Arrays.fill(dp, amount + 1);
	dp[0] = 0;

	for (int target = 1; target <= amount; target++) {
		for (int coin : coins) {
			if (coin <= target) {
				dp[target] = Math.min(dp[target], dp[target - coin] + 1);
			}
		}
	}

	return dp[amount] > amount ? -1 : dp[amount];
}
```

## 4. Practice method that actually works

For every problem:
1. Clarify input/output and constraints.
2. State brute-force approach first.
3. Improve to optimal approach.
4. Write code cleanly.
5. Test with edge cases.
6. Summarize complexity.
7. Revisit if not solved fully in 24-72 hours.

Interview communication template:
1. I think this is a sliding-window problem because the answer depends on a contiguous segment and the window state is incrementally maintainable.
2. A brute-force solution would be O(n^2).
3. We can improve to O(n) by moving left only when the constraint breaks.
4. I will code that and then test edge cases like empty input, repeated values, and minimal size.

## 5. Weekly DSA structure

- 5 days per week
- 2 problems per day
- 1 revision slot every Saturday
- 1 timed mock every Sunday

Example:
- Monday: sliding window + hashing
- Tuesday: trees + BST
- Wednesday: graphs
- Thursday: DP
- Friday: mixed interview set
- Saturday: re-solve failed problems
- Sunday: timed mock interview

## 6. Common mistakes

- jumping to code before identifying pattern
- weak edge case testing
- poor variable naming under pressure
- wrong complexity explanation
- not speaking while solving

## 7. Progress tracking sheet format

Track every problem with:
- date
- problem name
- pattern
- difficulty
- solved without help: yes/no
- time taken
- mistakes made
- revision date

## 8. Final DSA readiness bar

You are ready when:
- medium problems feel pattern-driven, not random
- you can recover after being stuck
- your code is usually compile-safe on first pass
- you naturally discuss space/time trade-offs
