# Macro-task 0000: source of truth

## Objective

Create the repository instructions, ADR index, accepted ADRs, agent workflow and detailed macro-task plans for the LoL data refactor.

## Dependencies

None.

## Scope

- create `AGENTS.md`;
- create architecture README and ADRs 0001-0005;
- create source-of-truth, main-agent and macro-agent instructions;
- create macro-task plans 0001-0006.

## Out of scope

- Java, SQL, Redis and Spring implementation;
- changes to existing application behavior.

## Invariants

- `Rank`, `Mastery`, `SummonerView`, `Match`, `Participant` and `MatchResult` are canonical names;
- accepted ADRs are the architecture authority;
- one macro-task is implemented at a time;
- the main agent approves gates.

## Acceptance criteria

- every planned macro-task has dependencies, scope, gate and handoff rules;
- the documentation does not create a second source of truth;
- no application file is modified.

## Verification

- inspect the complete documentation tree;
- run `git diff --check`;
- confirm only documentation files are added by this task.

## Handoff

The main agent reviews the document tree and accepts the ADRs before assigning macro-task 0001.
