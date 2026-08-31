# LoL and MongoDB flow audit

- Date: 2026-07-22 — historical audit, code terminology updated 2026-08-31 (see `docs/HANDBOOK.md` §6 for status)
- Name/routing update: 2026-08-31 — current queue `QueueHandler`/`RiotScheduler`/`ComputeScheduler`/`SyncScheduler` (ADR-0014), formerly `DatabaseTracker`/`R4JQueue`
- Type: static code and contract audit
- Mongo runtime: not executed in this workspace; MariaDB remains authorized only via `MongoMigration`
- Scope: `LeagueDB` writes, all LoL commands, `Tracker`, HTTP profile, and queries used by consumers

## Summary outcome

The LoL runtime has been moved to Mongo-only; MariaDB queries remain confined to the migration. End-to-end verification with real servers is still pending:

| Severity | Flow | Outcome |
|---|---|---|
| P0 | Tracker / match insert | verify with real Mongo that the match is acknowledged before participant upserts |
| P1 | Tracker / queue | verify the received queue and the Redis queue structure |
| P1 | Tracker / participant | verify handling of unresolved Riot participants |
| P1 | account/tracking | verify add, unlink, and tracking with `userId` ownership |
| P1 | champion stats | verify aggregation of separate `match` and `match_events` |

## Documents

> **Historical** — archived in `_archive/` (01-07). Kept as a trace, but the operational source is `docs/HANDBOOK.md` + `profile-statistics-source-of-truth.md`.

1. [_archive/01-write-flow-match.md](_archive/01-write-flow-match.md)
2. [_archive/02-summoner-profile-flow.md](_archive/02-summoner-profile-flow.md)
3. [_archive/03-opgg-flow.md](_archive/03-opgg-flow.md)
4. [_archive/04-http-profile-flow.md](_archive/04-http-profile-flow.md)
5. [_archive/05-query-contract-findings.md](_archive/05-query-contract-findings.md)
6. [_archive/06-all-lol-commands-tracker.md](_archive/06-all-lol-commands-tracker.md)
7. [_archive/07-champion-stats-build-flow.md](_archive/07-champion-stats-build-flow.md)

The operational source of truth for the unified `ProfileStatistics` flow is [`../architecture/profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md). It covers `filterKey` encoding, Mongo index, cache, async refresh, and Discord/API composition.

## Runtime verification method

Before fixing consumers, run a real case with a `puuid` and a known match, recording:

1. full Riot match id;
2. Mongo document after each write;
3. `acknowledged`, `matchedCount`, `modifiedCount`, and `upsertedId` for each write;
4. count and `puuid` of participants in the match document;
5. keys actually present in the `List<QueryRecord>` Mongo delivered to the consumer;
6. Redis cache contents before and after the test.

A real Mongo test with `MONGO_TEST_URI` is still required; without this variable, static mismatches cannot be distinguished from connection, authentication, or schema issues on the server.
