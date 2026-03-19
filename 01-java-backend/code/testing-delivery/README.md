# testing-delivery — Testing Strategy, CI/CD & Release Engineering

> **Learning path (8 of 8):** `1. 00-foundations` → `2. collections` → `3. concurrency` → `4. jvm` → `5. performance` → `6. linux-networking` → `7. springboot` → **`8. testing-delivery`**

Production readiness: testing pyramid, safe rollout patterns, feature flags, and backward-compatible database migrations.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [01-testing-strategy.md](01-testing-strategy.md) | Testing pyramid, unit vs integration vs contract vs E2E, Testcontainers, WireMock, test isolation principles |
| 2 | [02-ci-cd-release-engineering.md](02-ci-cd-release-engineering.md) | CI/CD pipeline goals, blue-green and canary deployments, feature flags, expand-and-contract migrations, rollback strategies |
| 3 | [TestingAndReleasePatterns.java](TestingAndReleasePatterns.java) | DeterministicRetryPolicy, FeatureFlagService, CompatibilityMigration — production-safe release patterns in code |

## How this fits in the bigger picture

```
springboot/          ← build the service
testing-delivery/    ← YOU ARE HERE — verify it and ship it safely
03-system-design/    ← design the systems that host these services
05-projects/         ← combine everything into a portfolio-ready project
```

## Study method

1. Read `01-testing-strategy.md` — map each test type to a concrete example in your own codebase or a project.
2. Read `02-ci-cd-release-engineering.md` — draw the release pipeline for one of the `05-projects/` services.
3. Implement `FeatureFlagService` from `TestingAndReleasePatterns.java` from scratch; hook it to a fake config source.
4. Write an expand-and-contract migration plan for a fictional schema change (rename a column across two releases).
5. Explain the difference between blue-green and canary deployment, and when you would not use canary, out loud.
