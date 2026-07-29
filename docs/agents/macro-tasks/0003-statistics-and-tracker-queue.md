# Macro-task 0003: statistics and DatabaseTracker asynchronous generation

## Objective

Separate statistics persistence from request handling and limit database calculation concurrency.

## Dependencies

- Macro-task 0000 approved;
- ADR-0010 accepted.

## Scope

- reduce `ProfileStatisticsService` to read and refresh methods;
- move API-triggered generation and in-flight deduplication into `DatabaseTracker`;
- process build jobs through one dedicated FIFO queue/worker and all other database jobs through a second FIFO queue/worker;
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
- no more than one build job and one non-build database job execute concurrently;
- one failed generation does not stop another generation;
- failed in-flight markers are removed so a later request can retry;
- match lookup and match analysis queues remain process-owned and unchanged.

## Acceptance criteria

- database queue fields and processor methods are owned by `DatabaseTracker`;
- request paths only read ready aggregates and submit missing work asynchronously;
- scheduler submits, but does not execute, the periodic champion refresh;
- match queue processing remains available.

Per le statistiche champion il job API-triggered è una matrice radicata solo in
`patch + queue`: enumera regioni attive e soglie rank cumulative, usa una sola
scansione Mongo dei match della coppia e mantiene una chiave di deduplicazione
`champion-stats-matrix:<patch>:<queue>`. Le combinazioni vuote vengono
marcate pronte; un job interno parametrico per pre-generare la matrice resta
attività futura.

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

`ProfileStatisticsService` owns read, calculation and persistence. `DatabaseTracker` owns async dispatch and in-flight deduplication using `profile-statistics:<puuid>:<filterKey>`. Overview, profile and `!summoner` read the same aggregate, while each existing presentation remains unchanged unless a style refactor is explicitly requested. `lastUpdate` is written after the calculation completes.
