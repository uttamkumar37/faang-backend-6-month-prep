package systemdesign.consistenthashing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/**
 * Consistent Hashing & Probabilistic Data Structures
 *
 * Topics:
 *  1. Basic consistent hash ring — O(log n) lookup via TreeMap
 *  2. Virtual nodes — even load distribution
 *  3. Replication — next N nodes on the ring
 *  4. Bloom filter — space-efficient set membership
 *  5. Count-Min Sketch — approximate frequency counter
 *  6. HyperLogLog — cardinality estimation
 *  7. Rendezvous (HRW) hashing — simpler alternative
 */
public class ConsistentHashingExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. BASIC CONSISTENT HASH RING
    // ─────────────────────────────────────────────────────────────────────────

    static class ConsistentHashRing {
        // TreeMap: sorted by hash position, supports ceilingKey() for O(log n)
        private final TreeMap<Long, String> ring = new TreeMap<>();
        private final int vnodes;              // virtual nodes per physical node
        private final HashFunction hashFn;

        ConsistentHashRing(int vnodes) {
            this.vnodes = vnodes;
            this.hashFn = new Md5Hash();
        }

        void addNode(String node) {
            for (int i = 0; i < vnodes; i++) {
                long pos = hashFn.hash(node + "#vnode-" + i);
                ring.put(pos, node);
            }
            System.out.printf("  Added node %-10s → %d positions on ring%n", node, vnodes);
        }

        void removeNode(String node) {
            for (int i = 0; i < vnodes; i++) {
                long pos = hashFn.hash(node + "#vnode-" + i);
                ring.remove(pos);
            }
            System.out.println("  Removed node: " + node);
        }

        // Lookup: hash key → walk clockwise → first node encountered
        String getNode(String key) {
            if (ring.isEmpty()) throw new IllegalStateException("No nodes in ring");
            long hash = hashFn.hash(key);
            // ceilingKey: first key >= hash; wrap around if null
            Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
            if (entry == null) entry = ring.firstEntry();  // wrap around
            return entry.getValue();
        }

        // Replication: get N distinct physical nodes for a key
        List<String> getReplicaNodes(String key, int replicationFactor) {
            if (ring.isEmpty()) throw new IllegalStateException("No nodes in ring");
            List<String> replicas = new ArrayList<>();
            long hash = hashFn.hash(key);

            // Walk clockwise, skip duplicates (same physical node from vnodes)
            NavigableMap<Long, String> tail = ring.tailMap(hash, true);
            for (String node : Iterables.concat(tail.values(), ring.values())) {
                if (!replicas.contains(node)) replicas.add(node);
                if (replicas.size() == replicationFactor) break;
            }
            return replicas;
        }

        Map<String, Integer> distribution(List<String> keys) {
            Map<String, Integer> counts = new TreeMap<>();
            for (String key : keys) {
                counts.merge(getNode(key), 1, Integer::sum);
            }
            return counts;
        }

        // Inner class for concatenating iterables (avoid Guava dependency)
        static class Iterables {
            static <T> Iterable<T> concat(Iterable<T> a, Iterable<T> b) {
                return () -> new Iterator<T>() {
                    final Iterator<T> ia = a.iterator(), ib = b.iterator();
                    public boolean hasNext() { return ia.hasNext() || ib.hasNext(); }
                    public T next() { return ia.hasNext() ? ia.next() : ib.next(); }
                };
            }
        }
    }

    interface HashFunction { long hash(String key); }

    static class Md5Hash implements HashFunction {
        @Override
        public long hash(String key) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(key.getBytes());
                // First 8 bytes → long (consistent hashing needs uniform distribution)
                long h = 0;
                for (int i = 0; i < 8; i++) {
                    h = (h << 8) | (digest[i] & 0xFFL);
                }
                return h;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static void consistentHashRingDemo() {
        System.out.println("=== 1. Consistent Hash Ring ===");

        ConsistentHashRing ring = new ConsistentHashRing(150); // 150 vnodes/node
        ring.addNode("cache-001");
        ring.addNode("cache-002");
        ring.addNode("cache-003");

        // Show lookup
        String[] testKeys = {"user:alice", "user:bob", "order:1001", "session:xyz", "product:abc"};
        for (String key : testKeys) {
            System.out.printf("  %-20s → %s%n", key, ring.getNode(key));
        }

        // Measure redistribution on failure
        System.out.println("\n  Generating 1000 keys before failure...");
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) keys.add("key:" + i);

        Map<String, String> beforeFailure = new HashMap<>();
        for (String k : keys) beforeFailure.put(k, ring.getNode(k));

        System.out.println("  Removing cache-002 (simulating failure)...");
        ring.removeNode("cache-002");

        int remapped = 0;
        for (String k : keys) {
            if (!ring.getNode(k).equals(beforeFailure.get(k))) remapped++;
        }
        System.out.printf("  Remapped: %d/1000 keys (%.1f%%) — expected ~333 (1/3)%n",
            remapped, remapped / 10.0);

        // Distribution check
        System.out.println("\n  Distribution with 150 vnodes:");
        ring.addNode("cache-002"); // restore
        Map<String, Integer> dist = ring.distribution(keys);
        dist.forEach((node, count) ->
            System.out.printf("    %-12s → %d keys (%.1f%%)%n", node, count, count / 10.0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. VIRTUAL NODE LOAD BALANCE DEMO
    // ─────────────────────────────────────────────────────────────────────────

    static void vnodeLoadBalanceDemo() {
        System.out.println("\n=== 2. VNode Distribution Comparison ===");

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) keys.add("key:" + i);

        for (int vnodes : List.of(1, 10, 50, 150)) {
            ConsistentHashRing ring = new ConsistentHashRing(vnodes);
            for (String n : List.of("node-A", "node-B", "node-C")) ring.addNode(n);

            Map<String, Integer> dist = ring.distribution(keys);
            IntSummaryStatistics stats = dist.values().stream()
                .mapToInt(Integer::intValue).summaryStatistics();
            double imbalance = (double)(stats.getMax() - stats.getMin()) / (10_000 / 3);
            System.out.printf("  vnodes=%-4d  min=%d max=%d imbalance=%.1f%%%n",
                vnodes, stats.getMin(), stats.getMax(), imbalance * 100);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. BLOOM FILTER
    // ─────────────────────────────────────────────────────────────────────────

    static class BloomFilter {
        private final BitSet bits;
        private final int    numBits;      // m
        private final int    numHashFns;   // k
        private       int    count = 0;    // items added (for FP rate estimation)

        /**
         * @param expectedItems  n — expected number of inserts
         * @param falsePositiveRate  desired FP rate (0.0−1.0)
         */
        BloomFilter(int expectedItems, double falsePositiveRate) {
            // m = -n * ln(p) / (ln2)^2
            this.numBits   = (int) Math.ceil(-expectedItems * Math.log(falsePositiveRate)
                / (Math.log(2) * Math.log(2)));
            // k = (m/n) * ln2
            this.numHashFns = Math.max(1, (int) Math.round((double) numBits / expectedItems * Math.log(2)));
            this.bits = new BitSet(numBits);
            System.out.printf("  BloomFilter: capacity=%d, bits=%d, hashFns=%d, expectedFP=%.2f%%%n",
                expectedItems, numBits, numHashFns, falsePositiveRate * 100);
        }

        void add(String item) {
            for (int i = 0; i < numHashFns; i++) {
                bits.set(getBitIndex(item, i));
            }
            count++;
        }

        boolean mightContain(String item) {
            for (int i = 0; i < numHashFns; i++) {
                if (!bits.get(getBitIndex(item, i))) return false; // definite miss
            }
            return true; // might be present (could be false positive)
        }

        private int getBitIndex(String item, int seed) {
            // Two-hash trick: h(i) = h1 + i*h2 (avoids computing k separate hash functions)
            int h1 = item.hashCode();
            int h2 = (int) (item.hashCode() * 2654435761L >>> 32); // Knuth multiplicative hash
            int combined = Math.abs(h1 + seed * h2);
            return combined % numBits;
        }

        double estimatedFpRate() {
            // (1 - e^(-k*n/m))^k
            double exp = Math.exp(-(double) numHashFns * count / numBits);
            return Math.pow(1 - exp, numHashFns);
        }
    }

    static void bloomFilterDemo() {
        System.out.println("\n=== 3. Bloom Filter ===");

        // Use case: "Have we seen this URL before?" (web crawler deduplication)
        BloomFilter filter = new BloomFilter(100_000, 0.01); // 1% FP rate

        // Add 10K "seen" URLs
        for (int i = 0; i < 10_000; i++) {
            filter.add("https://example.com/page/" + i);
        }

        // Test for false negatives (must be 0!) — items we DID add
        int falseNegatives = 0;
        for (int i = 0; i < 10_000; i++) {
            if (!filter.mightContain("https://example.com/page/" + i)) falseNegatives++;
        }
        System.out.println("  False negatives (must be 0): " + falseNegatives);

        // Test for false positives — items we did NOT add
        int falsePositives = 0;
        int testSet = 10_000;
        for (int i = 100_000; i < 100_000 + testSet; i++) {
            if (filter.mightContain("https://example.com/page/" + i)) falsePositives++;
        }
        System.out.printf("  False positives: %d/%d = %.2f%% (expected ~1%%)%n",
            falsePositives, testSet, (double) falsePositives / testSet * 100);
        System.out.printf("  Estimated FP rate: %.2f%%%n", filter.estimatedFpRate() * 100);

        // Memory comparison: 10K URLs each at 30 chars = 300KB; Bloom filter: much smaller
        System.out.printf("  BitSet memory: %.1f KB vs HashSet: ~%d KB%n",
            filter.numBits / 8.0 / 1024, 10_000 * 50 / 1024);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. COUNT-MIN SKETCH (approximate frequency)
    // ─────────────────────────────────────────────────────────────────────────

    static class CountMinSketch {
        private final int[][] table;
        private final int depth;   // d — number of hash functions (rows)
        private final int width;   // w — counters per row (columns)
        private final long[] seeds;

        CountMinSketch(double epsilon, double delta) {
            // w = ⌈e/ε⌉,  d = ⌈ln(1/δ)⌉
            this.width  = (int) Math.ceil(Math.E / epsilon);
            this.depth  = (int) Math.ceil(Math.log(1.0 / delta));
            this.table  = new int[depth][width];
            this.seeds  = new long[depth];
            Random rng = new Random(42);
            for (int i = 0; i < depth; i++) seeds[i] = rng.nextLong();
            System.out.printf("  CountMinSketch: depth=%d, width=%d, total counters=%d%n",
                depth, width, depth * width);
        }

        void increment(String item) { increment(item, 1); }

        void increment(String item, int count) {
            for (int i = 0; i < depth; i++) {
                table[i][columnFor(item, i)] += count;
            }
        }

        // Returns an estimate ≥ true count.  Error ≤ ε * total_count w.p. ≥ 1-δ
        int estimate(String item) {
            int min = Integer.MAX_VALUE;
            for (int i = 0; i < depth; i++) {
                min = Math.min(min, table[i][columnFor(item, i)]);
            }
            return min;
        }

        private int columnFor(String item, int row) {
            // MurmurHash-style mixing
            long h = seeds[row] ^ item.hashCode();
            h = h ^ (h >>> 33);
            h *= 0xff51afd7ed558ccdL;
            h = h ^ (h >>> 33);
            return (int) (Math.abs(h) % width);
        }
    }

    static void countMinSketchDemo() {
        System.out.println("\n=== 4. Count-Min Sketch ===");

        // ε=0.001 means error ≤ 0.1% of total count; δ=0.01 means correct 99% of time
        CountMinSketch sketch = new CountMinSketch(0.001, 0.01);

        // Simulate web traffic log — some endpoints more popular
        Random rng = new Random(42);
        String[] endpoints = {"/api/users", "/api/orders", "/api/products", "/health", "/api/auth"};
        int[] trueCounts = new int[endpoints.length];
        int total = 100_000;

        for (int i = 0; i < total; i++) {
            int idx = (int)(rng.nextGaussian() * 1.5 + 2); // bias toward middle
            idx = Math.max(0, Math.min(endpoints.length - 1, idx));
            sketch.increment(endpoints[idx]);
            trueCounts[idx]++;
        }

        System.out.println("  Endpoint frequency estimation:");
        for (int i = 0; i < endpoints.length; i++) {
            int est = sketch.estimate(endpoints[i]);
            System.out.printf("    %-20s true=%6d  est=%6d  error=%.2f%%%n",
                endpoints[i], trueCounts[i], est,
                Math.abs(est - trueCounts[i]) / (double) total * 100);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. HYPERLOGLOG (cardinality estimation)
    // ─────────────────────────────────────────────────────────────────────────

    static class HyperLogLog {
        private final int m;          // number of registers (must be power of 2)
        private final int b;          // b = log2(m) bits used for register index
        private final byte[] registers;

        HyperLogLog(int bitsForRegisters) {
            this.b         = bitsForRegisters;
            this.m         = 1 << b;
            this.registers = new byte[m];
        }

        void add(String item) {
            long h = murmurHash64(item);
            int  registerIdx = (int)(h >>> (64 - b)) & (m - 1);
            int  bitsAfterIdx = Long.numberOfLeadingZeros((h << b) | ((1L << b) - 1)) + 1;
            registers[registerIdx] = (byte) Math.max(registers[registerIdx], bitsAfterIdx);
        }

        long estimate() {
            double alpha;
            if      (m == 16)  alpha = 0.673;
            else if (m == 32)  alpha = 0.697;
            else if (m == 64)  alpha = 0.709;
            else               alpha = 0.7213 / (1 + 1.079 / m);

            double sum = 0;
            for (byte r : registers) sum += Math.pow(2, -r);
            double estimate = alpha * m * m / sum;

            // Small range correction
            if (estimate < 2.5 * m) {
                long zeros = 0;
                for (byte r : registers) if (r == 0) zeros++;
                if (zeros > 0) estimate = m * Math.log((double) m / zeros);
            }
            return Math.round(estimate);
        }

        private long murmurHash64(String key) {
            long h = 0x9368da83e71a3f15L;
            for (char c : key.toCharArray()) {
                h ^= c;
                h *= 0xc4ceb9fe1a85ec53L;
                h = Long.rotateLeft(h, 31);
            }
            h ^= h >>> 33;
            h *= 0xff51afd7ed558ccdL;
            h ^= h >>> 33;
            return h;
        }
    }

    static void hyperLogLogDemo() {
        System.out.println("\n=== 5. HyperLogLog Cardinality Estimation ===");

        HyperLogLog hll = new HyperLogLog(10); // 2^10 = 1024 registers ≈ 1KB

        Set<String> exact = new HashSet<>();
        int[] testCounts = {100, 1_000, 10_000, 100_000, 1_000_000};
        int current = 0;

        for (int target : testCounts) {
            for (; current < target; current++) {
                String item = "user:" + current;
                hll.add(item);
                exact.add(item);
            }
            long hllEst = hll.estimate();
            double error = Math.abs(hllEst - exact.size()) / (double) exact.size() * 100;
            System.out.printf("  n=%-8d  exact=%-8d  HLL=%-8d  error=%.1f%%%n",
                target, exact.size(), hllEst, error);
        }

        System.out.printf("%n  Memory: HLL=%.1fKB vs HashSet≈%dMB (for 1M strings)%n",
            1024.0 / 1024, 1_000_000 * 50 / 1024 / 1024);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. RENDEZVOUS HASHING (HRW)
    // ─────────────────────────────────────────────────────────────────────────

    static class RendezvousHashRing {
        private final List<String> nodes = new ArrayList<>();

        void addNode(String node)    { nodes.add(node); }
        void removeNode(String node) { nodes.remove(node); }

        String getNode(String key) {
            if (nodes.isEmpty()) throw new IllegalStateException("No nodes");
            return nodes.stream()
                .max(Comparator.comparingLong(node -> score(key, node)))
                .orElseThrow();
        }

        private long score(String key, String node) {
            // Combined hash of key+node → select node with highest score
            return Math.abs((long)(key + ":" + node).hashCode() * 2654435761L);
        }

        Map<String, Integer> distribution(List<String> keys) {
            Map<String, Integer> counts = new TreeMap<>();
            for (String k : keys) counts.merge(getNode(k), 1, Integer::sum);
            return counts;
        }
    }

    static void rendezvousHashingDemo() {
        System.out.println("\n=== 6. Rendezvous Hashing ===");

        RendezvousHashRing hrw = new RendezvousHashRing();
        for (String n : List.of("node-A", "node-B", "node-C", "node-D")) hrw.addNode(n);

        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) keys.add("key:" + i);

        // Before removal
        Map<String, String> before = new HashMap<>();
        for (String k : keys) before.put(k, hrw.getNode(k));

        // Distribution
        System.out.println("  Distribution (4 nodes, 1000 keys):");
        hrw.distribution(keys).forEach((n, c) ->
            System.out.printf("    %-8s → %d keys%n", n, c));

        // Minimal disruption on node removal
        hrw.removeNode("node-C");
        int moved = 0;
        for (String k : keys) if (!hrw.getNode(k).equals(before.get(k))) moved++;
        System.out.printf("  After removing node-C: %d/1000 keys moved (expected ~250)%n", moved);

        System.out.println("\n  Consistent Hashing vs Rendezvous:");
        System.out.println("    Consistent: O(log n) lookup, needs vnodes for balance");
        System.out.println("    Rendezvous:  O(n) lookup, perfect balance, simpler code");
        System.out.println("    Use consistent hashing for large node counts (>100 nodes)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        consistentHashRingDemo();
        vnodeLoadBalanceDemo();
        bloomFilterDemo();
        countMinSketchDemo();
        hyperLogLogDemo();
        rendezvousHashingDemo();
        System.out.println("\n=== All consistent hashing demos completed ===");
    }
}
