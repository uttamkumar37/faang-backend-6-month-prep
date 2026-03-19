# 05 — Prompt Engineering

Writing prompts that produce reliable, parseable output in production.

## File Order

| # | File | What you will learn |
|---|---|---|
| 1 | [05-prompt-engineering.md](05-prompt-engineering.md) | System prompt structure, zero-shot vs few-shot, chain-of-thought, structured output (JSON mode), prompt injection defense, prompt versioning, token budgeting inside prompts |

## Key concepts to own

- System prompt anatomy: Role → Task → Constraints → Output format → Fallback.
- Few-shot: when to use examples in the prompt, and how to pick good examples.
- Chain-of-thought: "think step by step" → when it helps and when it hurts latency.
- Prompt injection attacks: what they are and how to mitigate them in production.
- Versioning: treating prompts as code (tested, reviewed, deployed).

## Study method

1. Read the file; copy one "bad prompt" → rewrite it to the "good prompt" pattern.
2. Answer: "A user is submitting inputs that make your system prompt ignore its rules. How do you defend against it?"
3. Write a system prompt from scratch for the AI Support Copilot project (project 01 in `05-projects/`).
