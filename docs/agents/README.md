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

## MongoDB LoL migration

La strategia specifica per la migrazione Mongo è in [`docs/mongo/07-agent-strategy.md`](../mongo/07-agent-strategy.md).

Il workflow Mongo aggiunge:

- un audit read-only di tutte le query e scritture `LeagueDB` prima dell'implementazione;
- agenti query separati per profile, match, statistiche/champion e leaderboard;
- un agent per schema/index e uno per mapping verso i modelli esistenti;
- una verifica esplicita dell'assenza di dual-write e outbox nel runtime;
- un agent dedicato a backfill e uno a riconciliazione/cutover;
- un guardian indipendente che approva i gate e un verifier finale.

Gli agenti Mongo seguono lo stesso handoff del [`macro-task-template.md`](macro-task-template.md). Nessun implementatore può approvare il proprio lavoro o modificare l'ADR per sbloccare il proprio task.

## Champion statistics

La guida operativa per analizzare e ridurre l'uso di memoria della pipeline
Champion Statistics, senza cambiare contratti o risultati, e in
[`rusted-java.md`](rusted-java.md).

La proposta per le metriche di lane e timing oggetti dei matchup e in
[`champion-matchup-event-payload.md`](champion-matchup-event-payload.md).
