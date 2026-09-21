# ADR-0011: Domain services and R4J queue

- Status: Accepted
- Owner: Main agent
- Date: 2026-08-01
- Amended: 2026-08-19
- Terminology: superseded by ADR-0014 (`RiotScheduler`, `Job` and `QueueHandler`)

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
- `MatchService` owns match, R4J match, match-list, background match fetch and
  match-derived Mongo reads;
- `ProfileService` owns profile-page composition and its `SUMMONER_OVERVIEW` cache.

`R4JQueue`, under `com.safjnest.lol.queue`, owns outbound Riot scheduling. It
extends `AbstractQueueScheduler` with one channel and virtual-thread worker per
`LeagueShard`. Shared priorities are `IMMEDIATE` and `BACKGROUND` (former high
and low). Callers build a `QueueRequest` via `R4JQueue.request(...)` and submit
through `R4JQueue.schedule`. Shared `QueueTask` instances carry the key,
readable name, shard route, priority, supplier and completion future.
`R4JQueue` deduplicates an in-flight request by shard, operation and canonical
resource id. An immediate request runs before queued background work but never
interrupts a request already in flight. A successful, null or failed request is
removed after completion; Redis and Mongo remain the only cache and persistence
owners.

The canonical match identifier is always the full Riot ID, for example
`EUW1_6789012345`. Numeric IDs are neither accepted nor exposed by the match
service, model, Mongo projection or Redis cache keys.

Profile composition starts a summoner Future only when its base identity is
absent. It reads rank and mastery from Redis/Mongo without starting Riot work;
their forced Riot refresh is exclusive to `POST /profile/{puuid}/refresh`.
The queue serializes that external R4J work for the selected shard.

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
- `QueueTask` and `AbstractQueueScheduler` are shared with `DatabaseTracker`, but their
  registries, priority rules and workers remain separate.
- Queue diagnostics are disabled by default; the owner-only `ptest log` toggle
  enables `BotLogger` entries for request reuse, enqueue, start and terminal
  completion or failure.
- A fetched match is persisted before its participant summoners are seed-upserted
  from the R4J payload. The seed contains PUUID, Riot ID, shard, icon and level
  and requires neither Account nor Summoner API.
- A spectator roster is seed-persisted from its PUUID, Riot ID, shard and icon
  before its background enrichment. Both match and spectator seeds enqueue only
  a background forced rank refresh; masteries are fetched only by their
  cache/Mongo-miss flow, and participants are never hydrated through Account or
  Summoner API.
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
