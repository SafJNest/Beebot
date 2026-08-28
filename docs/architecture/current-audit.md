# Current LoL persistence audit

- Snapshot: 2026-07-26
- Names/routing refresh: 2026-08-20 (see ADR-0010, ADR-0011, ADR-0012)
- Owner: main agent
- Scope: MongoDB migration for the `league_of_legends` domain
- Source files: `LeagueDB`, LoL services, tracker, message handlers and canonical models

## Current state

- MongoDB is the LoL runtime store.
- MariaDB is retained only as the migration source through `MongoMigration`.
- Redis owns cache and temporary asynchronous state.
- `RedisKey` owns all LoL Redis patterns and TTLs. Temporarily, `RedisClient` assigns a 60-second expiration to every newly written key, including locks, counters, lists and sets; remove this override before release to restore the declared policy.
- R4J match payloads remain persistent until tracker consumption and successful Mongo persistence; transient processing failures leave the queue state retryable.
- `LeagueDB` is reduced to SQL execution and migration reads.
- Mongo runtime is concentrated in `MongoDB`, `QueryRecordParser` and `MongoMigration`; secondary indexes are managed outside the runtime.
- `QueryRecord` and `List<QueryRecord>` are the common flat/nested projection contract.

The canonical profile-statistics flow is documented in [`profile-statistics-source-of-truth.md`](profile-statistics-source-of-truth.md). It is the starting point for changes involving `Filter`, `ProfileStatistics`, `SummonerOverview`, `recentMatches` or `lastUpdate`.

## Canonical models

The migration reuses `Summoner`, `Rank`, `Mastery`, `SummonerOverview`, `Match`, `Participant`, `MatchResult`, `ProfileStatistics`, `ProfileActivity`, `Build`, `ChampionStatistics` and leaderboard models. No `*Document` duplicate is allowed.

## Deliberate exclusions

- the legacy `summoner.metrics` array and `summoner_metric` table;
- custom builds and their autocomplete queries;
- owner-command repair SQL, unless a separate migration job explicitly claims it.

Champion capability metrics are not summoner metrics. Champion stats remain in their own aggregate collection.

## Migration invariant

During transition MariaDB remains available as migration source. Mongo writes are idempotent runtime writes, schema/index creation is code-owned and non-destructive, and every migration read is gated by checkpoint and reconciliation.

## Evidence and remaining gate

1. schema bootstrap, database suffix selection, declared index policy, unique profile-statistics preflight, query inventory and migration checkpoint behavior are covered statically and by focused tests;
2. static flow tracing found broken profile/OP.GG query contracts and mirror no-op paths; see [`docs/audit`](../audit/README.md);
3. a real Mongo integration run and `explain("executionStats")` evidence remain pending until a representative Mongo dataset is available;
4. static index policy does not prove winning plans, disk sorts or storage overhead; `collStats` and `indexSizes` must be recorded before/after;
5. final cutover remains blocked until query contracts, write acknowledgements and production-sized reconciliation are verified.
