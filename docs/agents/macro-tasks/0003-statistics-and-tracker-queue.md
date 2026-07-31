# Macro-task 0003: statistics and DatabaseTracker asynchronous generation

## Objective

Separate statistics persistence from request handling and limit database calculation concurrency.

## Dependencies

- Macro-task 0000 approved;
- ADR-0010 accepted.

## Scope

- reduce `ProfileStatisticsService` to read and refresh methods;
- move API-triggered generation and in-flight deduplication into `DatabaseTracker`;
- serialize champion builds and statistics on worker 2; worker 1 consumes profile work and worker 2 may help profiles only while no champion task is queued;
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
- champion builds and statistics always execute on worker 2 in one FIFO sequence; profile jobs can execute on either worker;
- one failed generation does not stop another generation;
- failed in-flight markers are removed so a later request can retry;
- match lookup and match analysis queues remain process-owned and unchanged.

## Acceptance criteria

- database queue fields and processor methods are owned by `DatabaseTracker`;
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

`ProfileStatisticsService` owns read, calculation and persistence. `DatabaseTracker` owns async dispatch and in-flight deduplication using `profile-statistics:<puuid>:<filterKey>`. Overview, profile and `!summoner` read the same aggregate, while each existing presentation remains unchanged unless a style refactor is explicitly requested. `lastUpdate` is written after the calculation completes.
