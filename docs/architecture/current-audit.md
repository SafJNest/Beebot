# Current LoL persistence audit

- Snapshot: 2026-07-19
- Owner: main agent
- Scope: MongoDB migration for the `league_of_legends` domain
- Source files: `LeagueDB`, LoL services, tracker, message handlers and canonical models

## Current state

- MariaDB is the current primary store.
- Redis owns cache and temporary asynchronous state.
- `LeagueDB` still combines SQL execution, mapping and domain persistence methods.
- Mongo runtime is now concentrated in `MongoDB`, `MongoRecord` and `MongoMigration`.
- `QueryRecord` and `QueryResult` remain valid for MariaDB and are not new Mongo contracts.

## Canonical models

The migration reuses `Summoner`, `Rank`, `Mastery`, `SummonerOverview`, `Match`, `Participant`, `MatchResult`, `ProfileStatistics`, `Build`, `ChampionStatistics` and leaderboard models. No `*Document` duplicate is allowed.

## Deliberate exclusions

- the legacy `summoner.metrics` array and `summoner_metric` table;
- custom builds and their autocomplete queries;
- owner-command repair SQL, unless a separate migration job explicitly claims it.

Champion capability metrics are not summoner metrics. Champion stats remain in their own aggregate collection.

## Migration invariant

During transition MariaDB remains primary. Mongo writes are idempotent mirrors, schema/index creation is code-owned and non-destructive, and every read cutover is gated by model-level reconciliation.

## Evidence and remaining gate

1. schema bootstrap, database suffix selection, query inventory and migration checkpoint behavior are covered by targeted tests;
2. static flow tracing found broken profile/OP.GG query contracts and mirror no-op paths; see [`docs/audit`](../audit/README.md);
3. the local Java 25 validation covers the Mongo core and focused tests, but the full build still has the unrelated JDA `setAudioModuleConfig` error;
4. a real Mongo integration run remains pending because this workspace has no `MONGO_TEST_URI` and no local `mongod`;
5. final cutover remains blocked until query contracts, write acknowledgements and production-sized reconciliation are verified.
