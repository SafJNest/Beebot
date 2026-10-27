# Profile statistics

## Ownership

`ProfileService` owns profile composition, cache, Mongo reads/writes and refresh
coordination. `ProfileAnalyzer` owns pure aggregation. Mongo is the durable
source; Redis and page views are disposable projections. Discord and HTTP
compose the same canonical models and retain their existing presentation.

## Identity and filter

An aggregate is identified by `{ puuid, filterKey }`. `filterKey` is the full
URL-safe Base64 encoding of `Filter.toSummonerKey()`, which includes champion,
lane, queue, rank and behavior, patch, region, opponent, duo and time bounds.
Use the same complete `Filter` for Mongo selection, post-read matching, Redis
keys and job deduplication. `Filter.toKey()` is for champion/build data and is
not interchangeable with the profile key.

The unique Mongo index `profile_statistics_identity` protects
`{ puuid, filterKey }`. `_id` is a generated storage identity. A refresh updates
the existing pair with an upsert; it does not replace the document by PUUID or
season alone.

## Stored data

`profile_statistics` stores aggregate leaves under
`champion × canonical queue × position`, plus ping and spell counters. Totals,
rates, averages and view-specific rankings are derived by consumers. Activity
and matchups are separate rebuildable projections in `profile_activity` and
`profile_matchups`, with the same `{ puuid, filterKey }` identity. Match lists
return paginated complete `Match` values, including bans and participants but
excluding events. The single-match detail adds the stored timeline. Neither
response is reconstructed from aggregates (ADR-0003, amendment 2026-10-27).

`isOtp` is derived from the queue's champion leaves. A champion qualifies when
there are at least 20 games, its share is at least `0.50 + 0.30 * exp(-N/250)`,
and its lead over the second champion is at least `0.15`. The analyzer clears
and recomputes the classification on each rebuild.

## Read and refresh

Reads use Redis, then Mongo, then enqueue a deduplicated compute job on a miss.
Computation streams filtered matches, updates the analyzer, and writes Mongo
before refreshing Redis and invalidating the overview cache. `lastUpdate` is
set after computation completes. Failed persistence must not be reported as a
successful refresh.

Stale profile statistics are returned while a background refresh is queued.
Activity and matchups refresh their own projections. Missing profile components
keep the established partial/pending response behavior documented in
[`docs/api`](../api/lol-api.md).

## Change checklist

- Include every dataset-changing field in `Filter.toSummonerKey()`.
- Keep Mongo, Redis and job identity aligned on PUUID plus the complete filter.
- Keep aggregation pure in `ProfileAnalyzer`; keep persistence and cache work
  in `ProfileService`.
- Review the API and Mongo docs when behavior or storage changes. Preserve
  command, embed and response presentation unless it is explicitly requested.

## Owners

- `lol/model/Filter.java`
- `lol/model/statistics/ProfileStatistics.java`
- `lol/service/ProfileService.java` and `ProfileAnalyzer.java`
- `nosql/MongoDB.java`
- `lol/queue/QueueHandler.java` and `ComputeScheduler.java`
- [ADR-0012](adr/0012-profile-and-champion-analysis-facades.md)
- [Mongo query inventory](../mongo/08-query-inventory.md)
