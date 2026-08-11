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

`R4JQueue` owns outbound Riot scheduling. It keeps one FIFO virtual-thread
executor per `LeagueShard` and deduplicates an in-flight request by shard,
operation and canonical resource id. A successful, null or failed request is
removed after completion; Redis and Mongo remain the only cache and
persistence owners.

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
- Synchronous command calls wait for the same futures used by asynchronous
  profile requests.
