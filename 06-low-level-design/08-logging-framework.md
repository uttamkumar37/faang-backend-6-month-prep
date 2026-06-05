# Logging Framework LLD

## Purpose
Design a lightweight logging framework with levels, appenders, and formatting.

## Study Steps
- Clarify requirements and non-goals before naming classes.
- Identify entities, invariants, and state transitions.
- Define APIs and failure behavior.
- Explain extensibility and test cases before coding skeletons.

## Requirements
- Support levels DEBUG/INFO/WARN/ERROR.
- Support console and file appenders.
- Format messages with timestamp, level, logger name.
- Allow level filtering.

## Entities
Logger, LogLevel, LogEvent, Appender, Formatter, LoggerFactory.

## Class Diagram
```text
Logger -> LogLevel threshold
Logger -> Appender -> Formatter
ConsoleAppender/FileAppender implements Appender
```

## APIs
- `LoggerFactory.getLogger(name)`
- `logger.info(message)`
- `logger.error(message, throwable)`

## Design Decisions
Use appenders as pluggable outputs. Keep formatting independent from filtering.

## Edge Cases
Appender failure, null message, concurrent writes, expensive message construction, file rotation out of scope unless asked.

## Extensibility
Async logging, JSON formatter, MDC/correlation ids, dynamic levels.

## Test Cases
Level filtering, multiple appenders, formatting, appender failure isolation.

## Interview Explanation
Mention that production logging needs correlation ids and async appenders, but keep initial implementation small.

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
Example: INFO logger should drop DEBUG events before formatting to avoid unnecessary string construction.
