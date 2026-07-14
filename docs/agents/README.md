# Agent workflow

This directory contains execution rules for the architecture planner, main agent and macro-task agents.

## Reading order

1. repository [`AGENTS.md`](../../AGENTS.md);
2. architecture index [`docs/architecture/README.md`](../architecture/README.md);
3. accepted ADRs relevant to the task;
4. the assigned macro-task plan;
5. current code and tests.

## Roles

- `source-of-truth-agent.md`: analyzes architecture and maintains proposed decisions and task plans.
- `main-agent.md`: assigns work, reviews diffs and approves transitions.
- macro-task agents: implement one bounded task and follow its handoff contract.

Only the main agent approves changes that alter an accepted ADR or cross task ownership.

## Execution order

```text
0000 -> 0001 -> 0002 -> 0003 -> 0004 -> 0005 -> 0006 -> 0007
```

The main agent may run independent read-only analysis in parallel, but implementation tasks with overlapping owners are sequential.
