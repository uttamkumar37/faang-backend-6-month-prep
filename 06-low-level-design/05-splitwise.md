# Splitwise LLD

## Purpose
Design expense sharing with users, groups, splits, balances, and settlement.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Add equal, exact, and percentage expenses.
- Track balances between users.
- Show what each user owes or is owed.
- Simplify debts for settlement.

## Entities
User, Group, Expense, Split, BalanceSheet, ExpenseService, SettlementService.

## Class Diagram
```text
ExpenseService -> SplitStrategy -> Split
ExpenseService -> BalanceSheet
SettlementService -> BalanceSheet
```

## APIs
- `addExpense(paidBy, amount, splits)`
- `balancesFor(userId)`
- `simplify(groupId)`

## Design Decisions
Store normalized pairwise balances. Use split strategies for equal/exact/percentage validation.

## Edge Cases
Split total mismatch, negative amount, user not in group, rounding cents, duplicate expense submission.

## Extensibility
Multiple currencies, recurring expenses, audit log, payment integration.

## Test Cases
Equal split among three, exact split mismatch, settlement simplification, duplicate idempotency key.

## Interview Explanation
Focus on balance invariants: total owed equals total lent, and every expense updates balances atomically.

## Interview Questions
- What are the core entities and invariants?
- Which operation needs concurrency control?
- What extension would be easiest with your design?

## Common Mistakes
- Skipping edge cases until after coding.
- Using too many abstract classes for a small prompt.
- No test plan for state transitions.

## Self-Check
- [ ] I can draw the text class diagram quickly.
- [ ] I can state at least five edge cases.
- [ ] I can point to the Java skeleton and explain the main flow.

## Practical Example
Example: If A pays 90 for A/B/C equally, B owes A 30 and C owes A 30; A's own share does not create a balance.
