# LoL architecture

This directory is the source of truth for the LoL domain and API refactor.

## Service separation proposal

The initial analysis for the `beebot-servicies` branch is in
[`services/README.md`](services/README.md): service boundaries, complete source
inventory, shared response contracts, generic jobs/queues and extraction plan.
The S4B service target and its first internal API contract are specified in
[`services/s4b-service-spec.md`](services/s4b-service-spec.md).
The corresponding public LoL service boundary is specified in
[`services/lol-service-spec.md`](services/lol-service-spec.md).
[ADR-0016](adr/0016-service-boundaries-and-shared-core.md) remains **proposed**
for separate services, HTTP clients and application modules. Hive is accepted
under [ADR-0017](adr/0017-hive-job-runtime.md); shared contracts and LoL types
are accepted independently as R4B under [ADR-0018](adr/0018-r4b-shared-contracts.md).

## Precedence

When documents disagree, use this order:

1. `AGENTS.md` for repository-wide implementation rules;
2. accepted ADRs for architectural decisions;
3. `docs/HANDBOOK.md` for operational scope and checklists;
4. current code only as evidence of the migration state.

An agent must report a contradiction. It must not resolve the contradiction by changing an ADR or expanding its task scope without approval from the main agent.

## Canonical package layout

Hive has the independent root `com.hive`. R4B has the exact independent roots
`com.R4B.api.dto` and `com.R4B.lol`. Shared response roots and serialized components belong in R4B,
while producer-only computation/storage structures remain `com.safjnest.spring.lol.model`.
Application API, domain, tracker, Mongo and service code belongs under
`com.safjnest.spring`; S4B owns only Riot integration. The current source
destinations and SDK conversions are recorded in
[`services/r4b-inventory.md`](services/r4b-inventory.md).

```text
com/R4B/api/dto/
  summoner/ Summoner, Rank, Mastery, SummonerOverview, SummonerView, SummonerLeaderboard
  match/ Match, Participant, MatchResult, MatchPage, LiveGame, RankHistory...
  statistics/ ProfileStatistics, ProfileActivity, ProfileMatchups, ProfileMatchupLeaf
  statistics/shared/ LeafStats, ProfileLeafStats
  leaderboard/ LeaderboardPage, LeaderboardDistribution
  record/ ProfileRecord, ProfileRecordPage, RecordPage, RecordsOverview
  status/ BotStatus, LeagueMetrics, JvmMetrics, SystemMetrics, RedisMetrics, MongoMetrics...
  riot/ SDK-independent acquisition data and nested components
  Filter, ActivityFilter, ApiResult, ResponseMetadata, LolApiError, TransportJson
  Build, ChampionStatistics, ChampionView, ChampionTierList, RuneSignature, indexables

com/R4B/lol/
  SDK-compatible LoL enums, CanonicalQueue, ordering/record/query values
  FilterCodec, KdaUtils, NumberUtils, map bounds

com/hive/
  Hive, HiveConfig, JobFuture, Registry, Router
  job/, scheduler/, worker/, status/

com/safjnest/spring/lol/
  model/ producer-only storage/computation structures
  service/ producers, analyzers, accumulators, projections, storage codecs, FilterDefaults
  queue/ Spring-owned QueueHandler composition/submission
  scheduler/ ComputeScheduler, SyncScheduler, DatabaseWorkerType, ChampionMatrixRequest
  tracker/ Tracker, TrackerScheduler

com/safjnest/s4b/
  RiotGateway
  scheduler/ RiotScheduler

com/safjnest/spring/nosql/
  MongoDB, application persistence infrastructure
  writer/ SummonerWriter, MatchWriter

com/safjnest/spring/status/
  sampling and operational response composition
```

R4B owns shared request, success/error data and LoL value types. Spring owns
controllers, configuration, exception/status mapping, persistence and response
production. Shared DTOs do not own Mongo operations, dirty tracking, SDK
conversion, accumulators, static lookup or application defaults. HTTP transport
serialization preserves the existing exclusions independently of storage.
`com.hive` owns the JDK-only runtime mechanism and generic status records.
`spring.lol.queue.QueueHandler` composes one explicitly initialized runtime in App and
is the application's sole submission facade. Independent Hive instances need
no monolith and share no registry, context or dedup entries. The monolith uses
one registry across Riot, compute and Sync to retain cross-scheduler trees.
Physical queues remain scheduler-owned; Sync uses direct execution.

Queue glossary:

- `com.safjnest.s4b.scheduler.RiotScheduler`: S4B-owned outbound Riot work, one serial queue per shard;
- `spring.lol.scheduler.ComputeScheduler`: four serial workers PROFILE, PROFILE_2,
  CHAMPION and MONGO, with domain placement/reservation policy;
- `spring.lol.scheduler.SyncScheduler`: immediate virtual-thread execution with
  shard/global logical routes and no physical queue.

Routing, priorities and insert-time placement are defined by [ADR-0017](adr/0017-hive-job-runtime.md), preserving local policy from ADR-0010. A walkthrough of the current code is [`docs/new-queue.md`](../new-queue.md).
`MatchService` owns untracked match insertion and can only create a
`tracked=false` document; it cannot replace a completed match. `Tracker` owns
the normal RankProgress history completion and commits the single
`tracked=false -> true` transition. `%test fix-tracked` is the explicit Mongo
recovery exception: after rebuilding the stored ranked history, it marks each
repaired ranked match `tracked=true`. Non-tracker match insertion, including
OP.GG and sample-game imports, may persist a best-effort participant `{ rank, lp }`
snapshot from the canonical Mongo rank record while computing `match.rank`; a
missing queue rank in a present `ranks` map is stored as `{ rank: UNRANKED, lp: 0 }`;
missing or null `ranks` remains unknown. It never writes gain or predecessor data.
The package boundary is intentional: `job/` owns lifecycle data, `scheduler/`
owns route selection and physical queues, and `worker/` owns queue draining.
A job body receives the `Job` itself for phase/item reporting; simple bodies
ignore it. An async callback calls `QueueHandler.retain(job)` before the body
returns and `QueueHandler.resume(job, callback)` when it schedules or completes
its children.

## Response ranking ownership

Champion-page relations and profile matchup/synergy relations are ranked only
for HTTP responses by `MatchupRankingAnalyzer`. Stored aggregates and Redis
caches remain raw. `TierMathUtils` is the single owner of median priors,
shrinkage, Z-score standardization, posterior rate moments and the common
S+/S/A/B/C/D thresholds used both by this projection and
`ChampionTierAnalyzer`.

## Statistics source of truth

The complete profile-statistics flow, filter encoding, Mongo document shape, compound index, cache ownership and Discord/API composition are documented in [`profile-statistics-source-of-truth.md`](profile-statistics-source-of-truth.md). Start there when a future task mentions `ProfileStatistics`, `SummonerOverview`, `filterKey`, profile matches or `lastUpdate`.

## ADR index

- [ADR-0001: Canonical LoL model boundaries](adr/0001-canonical-lol-model-boundaries.md)
- [ADR-0002: Summoner view and leaderboard contract](adr/0002-summoner-view-and-leaderboard-contract.md)
- [ADR-0003: Match and match result models](adr/0003-match-and-match-result-models.md)
- [ADR-0004: Profile statistics asynchronous generation](adr/0004-profile-statistics-refresh-queue.md) — superseded by ADR-0010
- [ADR-0005: LoL API JSON contract](adr/0005-lol-api-json-contract.md)
- [ADR-0006: Champion API contract](adr/0006-champion-api-contract.md)
- [ADR-0007: Unified API result and parameter parsing](adr/0007-unified-api-result-and-parameters.md)
- [ADR-0008: Component caches and asynchronous match lookups](adr/0008-endpoint-cache-and-async-lookups.md)
- [ADR-0009: MongoDB persistence and LoL migration](adr/0009-mongo-persistence-and-migration.md)
- [ADR-0010: Database refresh queue](adr/0010-database-refresh-queue.md) — terminology superseded by ADR-0014
- [ADR-0011: Domain services and R4J queue](adr/0011-domain-services-and-r4j-queue.md) — terminology superseded by ADR-0014
- [ADR-0012: Profile and champion analysis facades](adr/0012-profile-and-champion-analysis-facades.md)
- [ADR-0013: Champion tier-list projection](adr/0013-champion-tier-list.md)
- [ADR-0014: Global job scheduler](adr/0014-global-job-scheduler.md) — lifecycle contract amended by ADR-0017 (`QueueHandler`/`Job`/`RiotScheduler`/`ComputeScheduler`/`SyncScheduler`)
- [ADR-0015: Contextual ranking projections](adr/0015-contextual-ranking.md) — **current ranking contract** (`RankService` → `CompetitiveService` → `LeaderboardService`, plus `ProfileRecordService` record indexes)
- [ADR-0016: Service boundaries and shared core](adr/0016-service-boundaries-and-shared-core.md) — **proposed**, analysis only

- [ADR-0017: Hive job runtime](adr/0017-hive-job-runtime.md) — **current infrastructure contract**, independent runtime and local adapters
- [ADR-0018: R4B shared contracts](adr/0018-r4b-shared-contracts.md) — **current shared-contract boundary**, exact namespaces, SDK-free consumers and compatible JSON

## Developer handbook

Operational guide for adding command / endpoint / service / model / queue / Mongo / Redis: [`docs/HANDBOOK.md`](../HANDBOOK.md).

## MongoDB migration

The operational documentation for the LoL migration is in [`docs/mongo/`](../mongo/README.md). ADR-0009 is accepted; Guardian gates remain mandatory for every capability.
