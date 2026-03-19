# Distributed Systems Internals

This module goes deeper than high-level design and focuses on the internals behind reliability claims.

## What to know

- quorum reads and writes
- leader election basics
- Raft mental model: leader, log replication, commit index
- split-brain risk and fencing
- exactly-once claims vs practical idempotency
- clocks: clock skew, monotonic time, event ordering limits
- deduplication windows and replay handling

## Core mental models

- Most production systems prefer at-least-once delivery plus idempotent consumers over magical exactly-once guarantees.
- Consensus is about agreeing on order under failures, not making latency free.
- Quorums trade latency and availability for stronger coordination.
- Time is a weak source of truth in distributed systems; IDs and logs are usually safer.

## Quorum math

If there are `N` replicas, choose read quorum `R` and write quorum `W` such that:

`R + W > N`

That ensures at least one overlapping replica between a successful read and write.

## Failure questions to practice

- Why can leader election bugs create double processing?
- Why do leases and fencing tokens matter?
- Why is clock time unsafe for unique ordering across machines?
- What does exactly-once usually mean in real systems?
