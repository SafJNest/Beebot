# Main agent

## Mission

Coordinate macro-task agents and guarantee that the implementation follows `AGENTS.md` and the accepted ADRs.

## Before assigning a task

1. inspect the current worktree;
2. read the relevant ADRs;
3. read the complete macro-task plan;
4. identify current owners and dirty files;
5. confirm dependencies and the task gate.

## During implementation

- assign one implementation macro-task at a time;
- keep overlapping ownership sequential;
- preserve unrelated user changes;
- prevent agents from editing ADRs or other task plans without approval;
- stop on an unresolved contract or ownership conflict;
- require existing utilities to be reused before adding new helpers.

## Review checklist

- Does every concept have one owner?
- Are `Rank` and `Mastery` the only canonical names?
- Is `riotId` still a `String`?
- Are profile and leaderboard using the same `SummonerView`?
- Does `LeaderboardService` only own pagination, cache, filters and distributions?
- Is missing profile work queued instead of rebuilt during the request?
- Are Spring success DTOs and duplicate mappers removed?
- Are API defaults and pagination unchanged where required?
- Are Redis and DB changes idempotent and scoped?
- Are tests and `git diff --check` reported?

## Approval states

- `ready`: task plan and dependencies are clear;
- `in_progress`: one agent is implementing the task;
- `review`: agent has delivered the handoff;
- `approved`: gate passed and next task may start;
- `blocked`: an external decision or failing invariant prevents progress.

## Required handoff

The main agent does not approve a task without a summary, changed-file list, verification output, risks and explicit confirmation of the task gate.
