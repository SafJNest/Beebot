# ADR-0011: Domain services and R4J queue

- Status: Accepted
- Owner: Main agent
- Date: 2026-08-01

## Context

`LeagueService` concentrated summoner identity, rank, mastery, match, cache,
Mongo and Riot concerns. Its shared virtual-thread executor allowed duplicate
or concurrent Riot requests for the same shard.

## Decision

`LeagueService` is removed. Runtime ownership is split as follows:

- `SummonerService` owns summoner identity, Riot account, search, spectator
  state and summoner cache invalidation;
- `RankService` owns rank and raw league-entry reads and persistence;
- `MasteryService` owns mastery reads and persistence;
- `MatchService` owns match, raw Riot match, match-list and match-derived
  Mongo reads;
- `ProfileService` owns profile-page composition and its `PROFILE_PAGE` cache.

`R4JQueue` owns outbound Riot scheduling. It keeps one virtual-thread
executor per `LeagueShard`, with high and low priority queues, and deduplicates
an in-flight request by shard, operation and canonical resource id. A high
request runs before queued low work but never interrupts a request already in
flight. A successful, null or failed request is removed after completion;
Redis and Mongo remain the only cache and persistence owners.

The canonical match identifier is always the full Riot ID, for example
`EUW1_6789012345`. Numeric IDs are neither accepted nor exposed by the match
service, model, Mongo projection or Redis cache keys.

Profile composition still starts summoner, rank and mastery futures together.
The queue serializes only their external R4J work for the selected shard, so
the existing `202 profile_pending` behavior remains unchanged.

ADR-0012 supersedes the profile facade ownership above while preserving this
profile-future and R4J scheduling invariant.

## Superseded ownership

This decision supersedes the `LeagueService` ownership clauses in ADR-0001,
ADR-0008 and ADR-0009. Their canonical-model, cache-order, HTTP and Mongo
invariants remain in force.

## Consequences

- Match API consumers must provide a full Riot match ID; HTTP statuses, Redis
  key formats and TTLs remain unchanged.
- Every extracted Riot fetch passes through `R4JQueue`.
- Queue diagnostics are disabled by default; the owner-only `ptest log` toggle
  enables `BotLogger` entries for request reuse, enqueue, start and terminal
  completion or failure.
- A fetched match is persisted before its participant summoners are seed-upserted
  from the R4J payload. The seed contains PUUID, Riot ID, shard, icon and level
  and requires neither Account nor Summoner API.
- A spectator roster is seed-persisted from its PUUID, Riot ID, shard and icon
  before its background enrichment. Both match and spectator seeds enqueue only
  low-priority rank followed by mastery refreshes; they never hydrate each
  participant through Account or Summoner API.
- HTTP and Discord live-game consumers call `SummonerService.getLiveGame`; the
  Discord adapter renders its canonical model and does not access spectator
  state directly.
- Live-game profile overviews use the same `ProfileService` statistics entry
  point as profile pages; stale persisted aggregates are returned and their
  deduplicated Mongo refresh is queued without participant Riot reads. They
  also expose persisted masteries and at most three champion statistics: the
  played champion followed by the most-played distinct champions.
- Synchronous command calls wait for the same futures used by asynchronous
  profile requests.
