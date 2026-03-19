package systemdesign.cache;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Consistency Models and CAP Theorem — implementation examples
 *
 * Topics:
 *  1. CAP theorem — CP vs AP behaviour under network partition simulation
 *  2. Quorum read/write — N=3, W=2, R=2; conflict resolution by timestamp
 *  3. Vector clocks — causal ordering, conflict detection
 *  4. Eventual consistency — anti-entropy gossip with LWW (last-write-wins)
 *  5. Strong consistency — linearizable register (synchronous replication)
 *  6. Read-your-own-writes consistency
 */
public class ConsistencyModelsExamples {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. CAP THEOREM — CP vs AP under partition
    //
    //   C — Consistency: every read returns the most recent write (or error)
    //   A — Availability: every request receives a response (not an error)
    //   P — Partition tolerance: system continues despite dropped messages
    //
    //   Under partition, must choose: sacrifice C (AP) or A (CP)
    // ─────────────────────────────────────────────────────────────────────────

    enum NodeRole { PRIMARY, REPLICA }

    static class CpDataStore {
        // CP store: refuses reads/writes during partition (sacrifices Availability)
        private final String nodeId;
        private final Map<String, String> data = new ConcurrentHashMap<>();
        private volatile boolean partitioned = false;

        CpDataStore(String nodeId) { this.nodeId = nodeId; }

        void setPartitioned(boolean p) { this.partitioned = p; }

        String read(String key) {
            if (partitioned) {
                // CP choice: reject request rather than return stale data
                throw new IllegalStateException("[CP" + nodeId + "] Partition detected — refusing read to preserve consistency");
            }
            return data.getOrDefault(key, null);
        }

        void write(String key, String value) {
            if (partitioned) {
                throw new IllegalStateException("[CP" + nodeId + "] Partition detected — refusing write to preserve consistency");
            }
            data.put(key, value);
            System.out.printf("  [CP%s] wrote %s=%s%n", nodeId, key, value);
        }
    }

    static class ApDataStore {
        // AP store: serves stale data during partition (sacrifices strong Consistency)
        private final String nodeId;
        private final Map<String, String> data = new ConcurrentHashMap<>();
        private volatile boolean partitioned = false;

        ApDataStore(String nodeId) { this.nodeId = nodeId; }

        void setPartitioned(boolean p) { this.partitioned = p; }

        String read(String key) {
            if (partitioned) {
                // AP choice: return potentially stale data rather than error
                String value = data.getOrDefault(key, null);
                System.out.printf("  [AP%s] partition active — returning possibly stale: %s=%s%n",
                    nodeId, key, value);
                return value;
            }
            return data.getOrDefault(key, null);
        }

        void write(String key, String value) {
            // AP: always accept writes (resolve conflicts later via gossip/LWW)
            data.put(key, value);
            System.out.printf("  [AP%s] wrote %s=%s%n", nodeId, key, value);
        }

        Map<String, String> getData() { return Collections.unmodifiableMap(data); }
    }

    static void capTheoremDemo() {
        System.out.println("=== 1. CAP Theorem ===");

        CpDataStore cpNode = new CpDataStore("1");
        cpNode.write("balance", "1000");
        System.out.println("  CP store: simulating network partition...");
        cpNode.setPartitioned(true);
        try {
            cpNode.read("balance");
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage());
        }
        System.out.println("  CP examples: HBase, ZooKeeper, etcd, Spanner (during partition)");

        System.out.println();
        ApDataStore apNode = new ApDataStore("1");
        apNode.write("username", "alice");
        System.out.println("  AP store: simulating network partition...");
        apNode.setPartitioned(true);
        apNode.write("username", "alice_updated"); // diverges from replica
        apNode.read("username"); // returns stale but doesn't error
        System.out.println("  AP examples: Cassandra, DynamoDB, CouchDB (during partition)");
        System.out.println("\n  Key insight: P is not optional in distributed systems");
        System.out.println("  Networks will partition → choose C (reject) or A (stale)");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. QUORUM READ / WRITE
    //
    //  N = total replicas
    //  W = write quorum (must acknowledge write)
    //  R = read quorum (must respond to read)
    //  W + R > N → guaranteed to see at least one node with latest write
    //
    //  Common setup: N=3, W=2, R=2  →  W+R=4 > 3
    // ─────────────────────────────────────────────────────────────────────────

    record VersionedValue(String value, long timestamp) {}

    static class QuorumNode {
        private final String nodeId;
        private final Map<String, VersionedValue> store = new ConcurrentHashMap<>();
        private volatile boolean available = true;

        QuorumNode(String nodeId) { this.nodeId = nodeId; }

        void setAvailable(boolean a) { this.available = a; }

        void write(String key, String value, long ts) {
            if (!available) throw new IllegalStateException("Node " + nodeId + " unavailable");
            store.put(key, new VersionedValue(value, ts));
        }

        VersionedValue read(String key) {
            if (!available) throw new IllegalStateException("Node " + nodeId + " unavailable");
            return store.get(key);
        }

        String id() { return nodeId; }
    }

    static class QuorumCoordinator {
        private final List<QuorumNode> nodes;
        private final int W; // write quorum
        private final int R; // read quorum

        QuorumCoordinator(List<QuorumNode> nodes, int w, int r) {
            this.nodes = nodes; this.W = w; this.R = r;
        }

        void write(String key, String value) {
            long ts = System.currentTimeMillis();
            int acks = 0;
            for (QuorumNode node : nodes) {
                try {
                    node.write(key, value, ts);
                    acks++;
                    System.out.printf("  [Quorum] node %s ack'd write %s=%s ts=%d%n",
                        node.id(), key, value, ts);
                } catch (IllegalStateException e) {
                    System.out.printf("  [Quorum] node %s UNAVAILABLE for write%n", node.id());
                }
            }
            if (acks < W) {
                throw new IllegalStateException(
                    "Write quorum not reached: " + acks + "/" + W + " acks");
            }
            System.out.printf("  [Quorum] Write COMMITTED (%d/%d acks)%n", acks, W);
        }

        String read(String key) {
            List<VersionedValue> responses = new ArrayList<>();
            for (QuorumNode node : nodes) {
                try {
                    VersionedValue v = node.read(key);
                    if (v != null) responses.add(v);
                    System.out.printf("  [Quorum] node %s responded: %s ts=%d%n",
                        node.id(), v != null ? v.value() : "null", v != null ? v.timestamp() : 0);
                } catch (IllegalStateException e) {
                    System.out.printf("  [Quorum] node %s UNAVAILABLE for read%n", node.id());
                }
            }
            if (responses.size() < R) {
                throw new IllegalStateException(
                    "Read quorum not reached: " + responses.size() + "/" + R + " responses");
            }
            // Conflict resolution: last-write-wins (highest timestamp)
            return responses.stream()
                .max(Comparator.comparingLong(VersionedValue::timestamp))
                .map(VersionedValue::value)
                .orElse(null);
        }
    }

    static void quorumDemo() {
        System.out.println("\n=== 2. Quorum Read/Write (N=3, W=2, R=2) ===");

        List<QuorumNode> nodes = List.of(
            new QuorumNode("A"), new QuorumNode("B"), new QuorumNode("C")
        );
        QuorumCoordinator qc = new QuorumCoordinator(nodes, 2, 2);

        System.out.println("  Writing 'price=100' (all nodes up):");
        qc.write("price", "100");

        System.out.println("\n  Reading 'price':");
        String v = qc.read("price");
        System.out.println("  Read result: " + v);

        // Simulate one node down
        System.out.println("\n  Node C goes down:");
        nodes.get(2).setAvailable(false);
        qc.write("price", "200"); // still works: 2/3 acks

        System.out.println("\n  Reading with node C still down:");
        System.out.println("  Conflict resolution: latest timestamp wins");
        String v2 = qc.read("price");
        System.out.println("  Read result: " + v2);

        System.out.println("\n  Math: W+R=" + (2+2) + " > N=" + nodes.size() +
            " → at least one node in read set saw the latest write");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. VECTOR CLOCKS — causal ordering + conflict detection
    //
    //  Each node tracks a vector: { nodeA: counter, nodeB: counter, ... }
    //  On write: increment own counter
    //  On receive message: merge vectors (take max per node), increment own
    //  Conflict: neither clock dominates → concurrent write (user must resolve)
    // ─────────────────────────────────────────────────────────────────────────

    static class VectorClock {
        private final Map<String, Integer> clock;

        VectorClock()                            { this.clock = new HashMap<>(); }
        VectorClock(Map<String, Integer> clock)  { this.clock = new HashMap<>(clock); }

        VectorClock increment(String nodeId) {
            Map<String, Integer> next = new HashMap<>(clock);
            next.merge(nodeId, 1, Integer::sum);
            return new VectorClock(next);
        }

        VectorClock merge(VectorClock other) {
            Map<String, Integer> merged = new HashMap<>(clock);
            other.clock.forEach((k, v) -> merged.merge(k, v, Math::max));
            return new VectorClock(merged);
        }

        // a happens-before b if every counter in a ≤ b AND at least one is strictly <
        boolean happensBefore(VectorClock other) {
            Set<String> allNodes = new HashSet<>(clock.keySet());
            allNodes.addAll(other.clock.keySet());
            boolean strictlyLess = false;
            for (String n : allNodes) {
                int myVal    = clock.getOrDefault(n, 0);
                int otherVal = other.clock.getOrDefault(n, 0);
                if (myVal > otherVal) return false; // violates ≤
                if (myVal < otherVal) strictlyLess = true;
            }
            return strictlyLess;
        }

        boolean concurrent(VectorClock other) {
            return !happensBefore(other) && !other.happensBefore(this) && !equals(other);
        }

        @Override public String toString() { return clock.toString(); }
        @Override public boolean equals(Object o) {
            return o instanceof VectorClock vc && clock.equals(vc.clock);
        }
        @Override public int hashCode() { return clock.hashCode(); }
    }

    record VersionedEntry(String value, VectorClock clock, String author) {}

    static void vectorClockDemo() {
        System.out.println("\n=== 3. Vector Clocks ===");

        // Three nodes: nodeA, nodeB, nodeC
        VectorClock v0 = new VectorClock();

        // nodeA writes "cart=[apple]"
        VectorClock v1 = v0.increment("nodeA");
        System.out.println("  nodeA writes cart=[apple]    clock=" + v1);

        // nodeA writes "cart=[apple, bread]"
        VectorClock v2 = v1.increment("nodeA");
        System.out.println("  nodeA writes cart=[apple,bread] clock=" + v2);

        // nodeB receives v1 and makes concurrent update "cart=[apple, milk]"
        VectorClock v3 = v1.merge(new VectorClock()).increment("nodeB");
        System.out.println("  nodeB (from v1) writes cart=[apple,milk] clock=" + v3);

        System.out.println();
        System.out.println("  Causality checks:");
        System.out.printf("    v1 happens-before v2? %b (yes — nodeA continued)%n", v1.happensBefore(v2));
        System.out.printf("    v2 concurrent with v3? %b (yes — conflict!)%n", v2.concurrent(v3));

        System.out.println("\n  Conflict: v2 and v3 are concurrent writes to 'cart'");
        System.out.println("  Resolution strategies:");
        System.out.println("    Last-write-wins (LWW): use physical timestamp → risk losing data");
        System.out.println("    Multi-value register: surface both to application (DynamoDB sibling)");
        System.out.println("    CRDT: data structure merges automatically (OR-Set for shopping cart)");

        // Merge resolution (application merges both carts)
        VectorClock resolved = v2.merge(v3).increment("nodeA");
        System.out.println("  Resolved by merging: cart=[apple,bread,milk] clock=" + resolved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. EVENTUAL CONSISTENCY — anti-entropy / gossip with LWW
    //
    //  Each node periodically exchanges state with random peers.
    //  Eventually all nodes converge on the same value.
    // ─────────────────────────────────────────────────────────────────────────

    static class GossipNode {
        final String nodeId;
        // key → (value, timestamp)
        final Map<String, VersionedValue> store = new ConcurrentHashMap<>();

        GossipNode(String nodeId) { this.nodeId = nodeId; }

        void write(String key, String value) {
            long ts = System.currentTimeMillis();
            store.put(key, new VersionedValue(value, ts));
        }

        // Anti-entropy: receive state from peer, apply LWW
        void mergeFrom(GossipNode peer) {
            int updated = 0;
            for (var entry : peer.store.entrySet()) {
                String key = entry.getKey();
                VersionedValue incoming = entry.getValue();
                VersionedValue existing = store.get(key);

                if (existing == null || incoming.timestamp() > existing.timestamp()) {
                    store.put(key, incoming);
                    updated++;
                }
            }
            if (updated > 0) {
                System.out.printf("  [%s] anti-entropy from [%s]: merged %d updates%n",
                    nodeId, peer.nodeId, updated);
            }
        }

        String read(String key) {
            VersionedValue v = store.get(key);
            return v == null ? null : v.value();
        }

        void printState() {
            System.out.printf("  [%s] store=%s%n", nodeId,
                store.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().value())));
        }
    }

    static void eventualConsistencyDemo() throws Exception {
        System.out.println("\n=== 4. Eventual Consistency (Gossip / Anti-Entropy) ===");

        GossipNode n1 = new GossipNode("DC1-node1");
        GossipNode n2 = new GossipNode("DC1-node2");
        GossipNode n3 = new GossipNode("DC2-node3");

        // Initial divergent writes (simulate partition)
        n1.write("config.timeout", "30s");
        n2.write("config.timeout", "45s"); // different value written to n2
        n3.write("config.maxConns", "100"); // n3 has a different key

        System.out.println("  State before gossip:");
        n1.printState();
        n2.printState();
        n3.printState();

        // Round 1 of gossip (each node exchanges with one peer)
        System.out.println("\n  Round 1 gossip:");
        n1.mergeFrom(n2); // n1 and n2 gossip → LWW: n2 wrote 45s later
        n2.mergeFrom(n3); // n2 and n3 gossip
        Thread.sleep(5);
        n1.write("config.timeout", "60s"); // n1 writes again (wins eventually)

        // Round 2
        System.out.println("  Round 2 gossip:");
        n3.mergeFrom(n1);
        n2.mergeFrom(n1);

        System.out.println("\n  State after gossip (converged):");
        n1.printState();
        n2.printState();
        n3.printState();
        System.out.println("\n  All nodes converge → eventual consistency achieved");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. STRONG CONSISTENCY (LINEARIZABILITY) — leader-based sync replication
    // ─────────────────────────────────────────────────────────────────────────

    static class LinearizableRegister {
        // Leader always has the current value.
        // Write: replicate to ALL followers synchronously, then ack.
        // Read: always from leader.
        private volatile String value = null;
        private final List<String> followers = new ArrayList<>();
        private volatile boolean leaderAlive = true;

        LinearizableRegister(String... followerIds) {
            this.followers.addAll(Arrays.asList(followerIds));
        }

        void write(String v) {
            if (!leaderAlive) throw new IllegalStateException("Leader unavailable — no writes accepted (CP)");
            System.out.printf("  [Leader] replicating '%s' to %d followers synchronously%n",
                v, followers.size());
            // Synchronous replication — all must ack before returning
            for (String f : followers) System.out.printf("  [%s] ack write '%s'%n", f, v);
            this.value = v; // commit only after all replicas confirmed
            System.out.printf("  [Leader] write COMMITTED: '%s'%n", v);
        }

        String read() {
            if (!leaderAlive) throw new IllegalStateException("Leader unavailable");
            return value;
        }

        void killLeader() {
            leaderAlive = false;
            System.out.println("  [Leader] CRASHED — new leader election in progress (unavailable)");
        }
    }

    static void linearizabilityDemo() {
        System.out.println("\n=== 5. Linearizability (Strong Consistency) ===");

        LinearizableRegister reg = new LinearizableRegister("follower-1", "follower-2");

        reg.write("version=1");
        System.out.println("  Read: " + reg.read());

        reg.write("version=2");
        System.out.println("  Read: " + reg.read());

        // Leader crash → temporarily unavailable (CP behaviour)
        System.out.println("\n  Simulating leader crash:");
        reg.killLeader();
        try {
            reg.read();
        } catch (IllegalStateException e) {
            System.out.println("  " + e.getMessage());
        }
        System.out.println("  → Linearizable systems sacrifice Availability for Consistency (CP)");
        System.out.println("  Examples: etcd, ZooKeeper, Spanner, CockroachDB");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. READ-YOUR-OWN-WRITES CONSISTENCY
    //    After a user writes, they must always read their own write (same user).
    //    Other users may still see stale data.
    // ─────────────────────────────────────────────────────────────────────────

    static class ReadYourWritesStore {
        // Primary: always up-to-date
        private final Map<String, String> primary = new ConcurrentHashMap<>();
        // Replica: may lag behind
        private final Map<String, String> replica = new ConcurrentHashMap<>();

        // Per-session: track last write timestamp to know if replica is fresh
        private final Map<String, Long> sessionWriteTs = new ConcurrentHashMap<>();
        private long replicationLagMs = 500; // configured lag simulation

        void setReplicationLag(long ms) { this.replicationLagMs = ms; }

        String write(String sessionId, String key, String value) {
            long ts = System.currentTimeMillis();
            primary.put(key, value);
            sessionWriteTs.put(sessionId, ts);
            System.out.printf("  [Primary] session=%s wrote %s=%s%n", sessionId, key, value);

            // Async replication to replica (delayed)
            new Thread(() -> {
                try { Thread.sleep(replicationLagMs); } catch (InterruptedException ignored) {}
                replica.put(key, value);
                System.out.printf("  [Replica] replicated %s=%s (after %dms lag)%n",
                    key, value, replicationLagMs);
            }).start();

            return value;
        }

        String read(String sessionId, String key) throws InterruptedException {
            Long writeTs = sessionWriteTs.get(sessionId);

            if (writeTs != null) {
                long elapsed = System.currentTimeMillis() - writeTs;
                if (elapsed < replicationLagMs) {
                    // Stale replica — route to primary to guarantee read-your-writes
                    System.out.printf("  [Router] session=%s → PRIMARY (lag not elapsed yet)%n", sessionId);
                    return primary.getOrDefault(key, null);
                }
            }
            // Safe to read from replica
            System.out.printf("  [Router] session=%s → REPLICA%n", sessionId);
            return replica.getOrDefault(key, null);
        }
    }

    static void readYourWritesDemo() throws Exception {
        System.out.println("\n=== 6. Read-Your-Own-Writes Consistency ===");

        ReadYourWritesStore store = new ReadYourWritesStore();
        store.setReplicationLag(300);

        // User alice updates her profile
        store.write("session-alice", "alice:bio", "Software Engineer at FAANG");

        // Immediately after write, alice reads her bio — must see her own write
        System.out.println("  Alice reads immediately:");
        System.out.println("  bio = " + store.read("session-alice", "alice:bio"));

        // Bob reads alice's bio at the same time — may see stale
        System.out.println("  Bob reads (different session — may be stale):");
        System.out.println("  bio = " + store.read("session-bob", "alice:bio"));

        // Wait for replication
        Thread.sleep(400);
        System.out.println("  After replication lag, Bob reads:");
        System.out.println("  bio = " + store.read("session-bob", "alice:bio"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAIN
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        capTheoremDemo();
        quorumDemo();
        vectorClockDemo();
        eventualConsistencyDemo();
        linearizabilityDemo();
        readYourWritesDemo();
        System.out.println("\n=== All consistency model demos completed ===");
    }
}
