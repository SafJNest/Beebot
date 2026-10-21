# MongoDB LoL persistence

MongoDB owns all LoL persistence. Match insert/upsert also updates the
rebuildable `match_facets` participant projection. Historical MariaDB imports
and SQL recovery commands have been removed. Redis is a disposable cache and
derived-index store.

## Data and ownership

- `summoner._id` is the PUUID. Ranks and masteries are stored on the canonical
  summoner record.
- `match._id` is the full Riot match ID. Events live separately in
  `match_events`.
- Profile statistics, activity and matchups use `{ puuid, filterKey }` as their
  logical identity. The corresponding unique indexes protect that pair.
- Champion statistics and builds are rebuildable aggregates. CHERRY persistence
  rules are in [`docs/arena-build`](../arena-build/README.md).
- `match_facets` contains one lean participant document per game; its identity,
  rebuild and operational index are documented in the
  [projection contract](15-match-facets-projection.md).

## Migration and maintenance

Owner-only migration and regeneration commands are implemented in
`MongoMigration` and the existing test command. The main operations are:

- `!test migrate rankprogress [runId]` — normalize and rebuild Mongo rank history.
- `!test migrate fix-tracked` — rebuild tracked rank history from Mongo.
- `!test export-match-search` — rebuild `match_facets` from canonical matches.
- `!test regenerate profiles`, `records`, `champions` or `indexables` — rebuild
  the selected derived data.
- `!test fix-timeline <puuid>` or `!test fix-timeline-patch <patch>` — restore
  missing timeline events from Riot.

Mongo rank-history migrations use bounded batches and checkpoints; the facets
rebuild uses idempotent upserts. Review the
command implementation before running an operation; these commands write data.

## Indexes and queries

Mongo initialization creates missing collections. Secondary indexes are
managed operationally by the database operator; runtime code and migrations do
not create, alter or drop them. Apply index changes create-first, verify the
query plans, then remove superseded indexes. The profile identity index
preflights missing and duplicate keys before its operational creation.

See [query inventory](08-query-inventory.md), [profile record indexes](12-profile-record-indexes.md),
[contextual ranking](13-contextual-ranking.md) and
[match index consolidation](14-match-index-consolidation.md) for query and
index owners.
Use `explain("executionStats")` to verify query plans; do not infer live index
state from these files.
The current match rollout retains the five measured v3 secondary indexes and
adds two player selector/time indexes. The v4 script linked from the match
document creates only those additions; loading it performs no writes. It
replaces the earlier v2 script with nine player permutations.

## Configuration

`rsc/settings.json` holds the server-level Mongo URI; the URI does not name the
application database. Do not log or commit real credentials. Tests use the
database selected by `App.isTesting()` and current `MongoDB` constants.

`match_events` uses Mongo's native WiredTiger Zstandard compression. Structured
models remain BSON; event payloads are stored separately from matches.
