# CAP Theorem, PACELC & Consistency Models

## CAP Theorem

A distributed system can guarantee at most 2 of 3:

```
         Consistency (C)
             / \
            /   \
           /     \
    CA    /  CP   \  CA
         /         \
Availability(A)━━━━Partition Tolerance(P)
              AP
```

- **Consistency**: every read receives the most recent write (or error).
- **Availability**: every request gets a response (no error), but may not be latest.
- **Partition Tolerance**: system keeps operating when network partitions occur.

> Network partitions WILL occur in any distributed system. So real choice: **CP vs AP**.

### When Partition Happens

```
CP system: refuse requests until partition heals (returns error)
  → Always consistent; might be unavailable during partition.

AP system: serve stale data from available nodes (may return old value)
  → Always available; might return stale data.
```

### Database Placement

| System | Type | Why |
|---|---|---|
| Zookeeper, HBase | CP | Strong consistency, may return error under partition |
| Cassandra, CouchDB | AP | Always available, eventual consistency |
| MySQL (single) | CA | No partition tolerance (single node) |
| DynamoDB | AP (default) / CP (strong reads) | Configurable per read |
| Google Spanner | CP | TrueTime, strict serializability |

---

## PACELC Theorem

Extends CAP: even without partitions, there's a tradeoff between latency and consistency.

```
IF Partition:
  choose between Availability (A) vs Consistency (C)
ELSE (normal operation):
  choose between Latency (L) vs Consistency (C)
```

```
DynamoDB:  PA/EL — available under partition; low latency (eventual consistency)
BigTable:  PC/EC — consistent under partition; also consistent (low latency write replication)
Cassandra: PA/EL — available + low latency (tunable consistency)
Spanner:   PC/EC — consistent everywhere (uses TrueTime, globally synchronized)
```

---

## Consistency Models (Strongest to Weakest)

### Linearizability (Strict Serializability)

Strongest model. Every operation appears to happen at a single point in time, in real-time order.

```
Write(x=1) completes at T=10.
Any Read(x) after T=10 → must return 1.
```

Cost: high latency (quorum reads/writes required).  
Used by: etcd, Zookeeper (consensus protocols).

### Sequential Consistency

All operations appear in the same order to all processes, but not necessarily in real time.

### Causal Consistency

Operations causally related are seen in order; concurrent operations may be seen differently.

```
Alice: writes X=1
Bob:   reads X=1, then writes Y=2  (causally after X=1)
Carol: must see X=1 before seeing Y=2
```

### Eventual Consistency

Given no new updates, all replicas eventually converge to the same value.

```
Cassandra with ONE consistency level:
  Write goes to 1 replica; others async-replicate.
  Immediate read may get stale value.
  After replication propagates: all nodes agree.
```

| Consistency Level | Description | Use Case |
|---|---|---|
| Strong | Always latest | Banking, inventory |
| Bounded Staleness | Latest within Δ time | Commerce pricing |
| Session | Monotonic reads within session | User profile |
| Eventual | Highest availability, lowest cost | Social likes, view counts |

---

## Quorum Reads/Writes

```
N = total replicas
W = replicas that must acknowledge write
R = replicas that must respond to read

Strong consistency: R + W > N
  N=3, W=2, R=2 → 2+2=4 > 3 ✓

Fast writes, slow reads: W=1, R=3 (N=3)
Fast reads, slow writes: W=3, R=1 (N=3)
Balanced: W=2, R=2 (N=3) — most common
```

Cassandra consistency levels map directly to this:

```
ALL   → R/W = N
QUORUM → R/W = N/2 + 1
ONE   → R/W = 1
```

---

## Vector Clocks

Track causality in distributed systems without synchronized clocks.

```
Process A: [A:1]
B receives from A: [A:1, B:1]
B sends to C: [A:1, B:1]
C sends to A: [A:1, B:1, C:1]
A increments: [A:2, B:1, C:1]

Compare two events:
  V1 > V2 if every component of V1 >= V2 (V1 happened-after V2)
  V1 || V2 if neither dominates (concurrent events → may need conflict resolution)
```

Used by: Amazon Dynamo (shopping cart merge), Riak.

---

## Consensus Algorithms

**Raft** (easier to understand than Paxos):

```
Leader election:
  Candidates start timer (150-300ms random).
  First candidate → sends RequestVote to peers.
  Majority vote → becomes leader.

Log replication:
  Leader receives write → appends to log.
  Replicates to followers.
  Once quorum (N/2+1) acknowledges → commits → responds to client.
```

Used by: etcd (Kubernetes), CockroachDB, TiKV.

---

## Interview Tips

- CAP: P is non-negotiable in distributed systems → choice is CP vs AP.
- Bring up PACELC to show depth: normal operations also have latency vs consistency tradeoff.
- Know at least two examples each for CP and AP systems.
- Quorum formula (R+W>N) synthesizes consistency into concrete numbers.
