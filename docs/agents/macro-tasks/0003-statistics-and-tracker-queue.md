# Macro-task 0003: statistics and DatabaseTracker asynchronous generation

Implemented. Queue routing after 2026-08-20 follows amended ADR-0010
(insert-time least-loaded profile placement, no steal). Do not copy the
historical worker-2 steal wording below as current behavior.

The request infrastructure and compute owner are now named
`AbstractRequestDispatcher` and `ComputeRequestDispatcher`; tracker/match
workflow ownership moved to `SyncRequestDispatcher` under ADR-0014.

## Objective

Separate statistics persistence from request handling and limit database calculation concurrency.

## Dependencies

- Macro-task 0000 approved;
- ADR-0010 accepted.

## Scope

- reduce `ProfileStatisticsService` to read and refresh methods;
- move API-triggered generation and in-flight deduplication into `DatabaseTracker`;
- serialize champion builds and statistics on the `CHAMPION` channel; profile-logical work is assigned at insert to the lighter channel;
- keep the existing match queues separate and unchanged;
- keep calendar ownership in `TrackerScheduler`, which only submits scheduled database work.

## Out of scope

- Redis match queue behavior;
- Riot fetch Future concurrency in `LeagueService`;
- leaderboard response redesign outside the missing-statistics status change.

## Invariants

- API-triggered generation is submitted immediately to the database queue;
- no raw aggregate calculation runs on an HTTP request thread;
- repeated requests for the same PUUID and complete summoner filter are deduplicated while queued or running;
- champion builds and statistics always execute on the `CHAMPION` channel in one FIFO sequence; profile jobs can execute on either channel;
- one failed generation does not stop another generation;
- failed in-flight markers are removed so a later request can retry;
- match lookup and match analysis queues remain process-owned and unchanged.

## Acceptance criteria

- database routing, queue fields and processor methods are owned by
  `lol.queue.DatabaseTracker`; generic task lifecycle and in-flight cleanup are
  inherited from `lol.queue.AbstractQueueScheduler`;
- request paths only read ready aggregates and submit missing work asynchronously;
- scheduler submits, but does not execute, the periodic champion refresh;
- match queue processing remains available.

Per le statistiche champion il job API-triggered è una matrice radicata solo in
`patch + queue`: enumera regioni attive e soglie rank cumulative, esegue una
scansione base dei match e poi risolve gli eventi per gli stessi ID a blocchi,
senza `$lookup` e senza trattenere i payload evento, e mantiene una chiave di deduplicazione
`champion-stats-matrix:<patch>:<queue>`. Le combinazioni vuote vengono
marcate pronte. Le ultime tre patch della stessa queue sono accodate dalla più
vecchia alla più nuova, così la seconda e la terza matrice trovano già pronto
l’aggregato precedente per il trend.

## Handoff

Report executor ownership, deduplication keys, failure cleanup, scheduler changes and verification results.

## Implemented flow reference

The implemented source-of-truth contract is [`profile-statistics-source-of-truth.md`](../../architecture/profile-statistics-source-of-truth.md). The important recovery invariant is:

```text
ProfileStatistics identity = puuid + Filter.toSummonerKey()
Mongo unique index         = profile_statistics_identity on puuid + filterKey
Mongo _id                  = random ObjectId, $setOnInsert only
recentMatches              = separate MatchResult query with the same Filter
```

`ProfileService` owns read, calculation and persistence; `ProfileAnalyzer` is pure.
`lol.queue.DatabaseTracker` owns async dispatch and uses the shared abstract
scheduler for in-flight deduplication with
`profile-statistics:<puuid>:<filterKey>`. Overview, profile and `!summoner` read
the same aggregate, while each existing presentation remains unchanged unless a
style refactor is explicitly requested. `lastUpdate` is written after the
calculation completes.
