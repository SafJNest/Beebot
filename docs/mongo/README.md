# MongoDB LoL persistence

MongoDB is the sole LoL runtime store. MariaDB is read only by
`MongoMigration`; runtime errors do not fall back to SQL. Redis is a disposable
cache and derived-index store.

## Data and ownership

- `summoner._id` is the PUUID. Ranks and masteries are stored on the canonical
  summoner record.
- `match._id` is the full Riot match ID. Events live separately in
  `match_events`.
- Profile statistics, activity and matchups use `{ puuid, filterKey }` as their
  logical identity. The corresponding unique indexes protect that pair.
- Champion statistics and builds are rebuildable aggregates. CHERRY persistence
  rules are in [`docs/arena-build`](../arena-build/README.md).
- SQL structures are historical migration input; see [`docs/sql`](../sql/README.md).

## Migration and maintenance

Owner-only migration and regeneration commands are implemented in
`MongoMigration` and the existing test command. The main operations are:

- `!test migrate` — raw backfill from MariaDB.
- `!test migrate tracked` and `!test migrate ranks` — recover tracked match and
  rank data.
- `!test regenerate profiles`, `records`, `champions` or `indexables` — rebuild
  the selected derived data.
- `!test fix-timeline <puuid>` or `!test fix-timeline-patch <patch>` — restore
  missing timeline events from Riot.

Backfills use bounded batches, checkpoints and idempotent upserts. Review the
command implementation before running an operation; these commands write data.

## Indexes and queries

Mongo initialization is create-only: it creates missing collections and
compatible indexes and fails on conflicting definitions. It does not drop
indexes or clean legacy records. The profile identity index preflights missing
and duplicate keys before creation.

See [query inventory](08-query-inventory.md), [profile record indexes](12-profile-record-indexes.md)
and [contextual ranking](13-contextual-ranking.md) for query and index owners.
Use `explain("executionStats")` to verify query plans; do not infer live index
state from these files.

## Configuration

`rsc/settings.json` holds the server-level Mongo URI; the URI does not name the
application database. Do not log or commit real credentials. Tests use the
database selected by `App.isTesting()` and current `MongoDB` constants.

`match_events` uses Mongo's native WiredTiger Zstandard compression. Structured
models remain BSON; event payloads are stored separately from matches.
