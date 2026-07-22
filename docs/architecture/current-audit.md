# Current LoL persistence audit

- Snapshot: 2026-07-19
- Owner: main agent
- Scope: MongoDB migration for the `league_of_legends` domain
- Source files: `LeagueDB`, LoL services, tracker, message handlers and canonical models

## Current state

- MongoDB is the LoL runtime store.
- MariaDB is retained only as the migration source through `MongoMigration`.
- Redis owns cache and temporary asynchronous state.
- `LeagueDB` is reduced to SQL execution and migration reads.
- Mongo runtime is concentrated in `MongoDB`, `QueryRecordParser` and `MongoMigration`.
- `QueryRecord` and `List<QueryRecord>` are the common flat/nested projection contract.

## Canonical models

The migration reuses `Summoner`, `Rank`, `Mastery`, `SummonerOverview`, `Match`, `Participant`, `MatchResult`, `ProfileStatistics`, `Build`, `ChampionStatistics` and leaderboard models. No `*Document` duplicate is allowed.

## Deliberate exclusions

- the legacy `summoner.metrics` array and `summoner_metric` table;
- custom builds and their autocomplete queries;
- owner-command repair SQL, unless a separate migration job explicitly claims it.

Champion capability metrics are not summoner metrics. Champion stats remain in their own aggregate collection.

## Migration invariant

During transition MariaDB remains available as migration source. Mongo writes are idempotent runtime writes, schema/index creation is code-owned and non-destructive, and every migration read is gated by checkpoint and reconciliation.

## Evidence and remaining gate

1. schema bootstrap, database suffix selection, query inventory and migration checkpoint behavior are covered by targeted tests;
2. static flow tracing found broken profile/OP.GG query contracts and mirror no-op paths; see [`docs/audit`](../audit/README.md);
3. the local Java 25 validation covers the Mongo core and focused tests, but the full build still has the unrelated JDA `setAudioModuleConfig` error;
4. a real Mongo integration run remains pending because this workspace has no `MONGO_TEST_URI` and no local `mongod`;
5. final cutover remains blocked until query contracts, write acknowledgements and production-sized reconciliation are verified.
