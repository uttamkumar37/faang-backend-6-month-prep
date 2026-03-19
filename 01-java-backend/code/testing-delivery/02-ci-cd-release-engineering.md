# CI/CD and Release Engineering

This module covers the deployment safety and release mechanics often missing from interview prep.

## CI pipeline goals

- fail fast on syntax, tests, and static analysis
- keep feedback quick for common changes
- make artifacts reproducible
- enforce the same checks on every branch and pull request

## CD and release safety

Know:
- blue-green vs canary releases
- feature flags vs branch-based release control
- rollback strategy
- database migration safety
- contract compatibility during rolling deploys

## Release checklist

1. schema change is backward-compatible first
2. new code handles old and new schema
3. alerts and dashboards exist before rollout
4. rollout is gradual when blast radius is meaningful
5. rollback is tested and operationally simple

## Migration rules of thumb

- add columns before reading them everywhere
- backfill asynchronously when data volume is large
- never require a full-table lock during hot traffic if it can be avoided
- keep old readers and writers working during transition

## Feature flags

Use when:
- gradual rollout matters
- you want instant disable without redeploy
- you need A/B evaluation

Do not use as:
- a substitute for deleting dead code
- a permanent configuration graveyard

## Questions to practice

- Why can a safe DB migration require multiple deploys?
- When is canary better than blue-green?
- What metrics decide whether a rollout continues or stops?
