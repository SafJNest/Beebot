# LoL architecture

This directory is the source of truth for the LoL domain and API refactor.

## Precedence

When documents disagree, use this order:

1. `AGENTS.md` for repository-wide implementation rules;
2. accepted ADRs for architectural decisions;
3. the assigned macro-task plan for implementation scope;
4. current code only as evidence of the migration state.

An agent must report a contradiction. It must not resolve the contradiction by changing an ADR or expanding its task scope without approval from the main agent.

## Canonical package layout

```text
lol/model/summoner/
  Summoner
  Rank
  Mastery
  SummonerOverview
  SummonerView
  SummonerLeaderboard

lol/model/match/
  Match
  Participant
  MatchResult
  LiveGame

lol/model/leaderboard/
  LeaderboardPage
  LeaderboardDistribution

lol/model/statistics/
  ProfileStatistics
  ProfileActivity
  shared/LeafStats
  shared/ProfileLeafStats
  shared/ChampionLeafStats
  ChampionStatsDocument
  ProfileStatisticsRow

lol/model/
  ChampionView

lol/model/status/
  BotStatus
  LeagueMetrics
  SchedulerStatus
  QueueStatus
  WorkerStatus
  RunStatus
  JobStatus
  JobProgress
  JvmMetrics
  SystemMetrics
  RedisMetrics

lol/queue/
  QueueHandler
  Registry
  Router
  job/
    Job
    JobPriority
    JobState
  scheduler/
    AbstractScheduler
    RiotScheduler
    ComputeScheduler
    SyncScheduler
    DatabaseWorkerType
    ChampionMatrixRequest
  worker/
    JobQueue
    JobWorker
    WorkerState

lol/tracker/
  Tracker
  TrackerScheduler

status/
  StatusService
  SystemMetricsSampler
  LeagueMetricsStore
```

Spring owns controllers, configuration and HTTP error models. It must not own operational LoL success DTOs.
`lol.queue` owns the global in-memory registry plus the shared physical
scheduler infrastructure. `QueueHandler` is the sole public submission API.
`Job` is the registered data object. Riot,
compute and Sync retain distinct workers and route-local priority queues; the
registry tracks their logical parent/child tree and does not merge their queues.

Queue glossary:

- `RiotScheduler` — outbound Riot work, one queue per `LeagueShard`;
- `ComputeScheduler` — Mongo compute work, routes `PROFILE` and `CHAMPION`;
- `SyncScheduler` — tracking, rank, match, sample and participant refresh workflows, one queue per shard.

Routing, priorities and insert-time placement are defined by [ADR-0010](adr/0010-database-refresh-queue.md). A walkthrough of the current code is [`docs/new-queue.md`](../new-queue.md).
`MatchService` owns untracked match insertion and can only create a
`tracked=false` document; it cannot replace a completed match. `Tracker` owns
the subsequent RankProgress history completion and commits the single
`tracked=false -> true` transition. OP.GG may persist a best-effort participant
`{ rank, lp }` snapshot, but never gain or predecessor data.
The package boundary is intentional: `job/` owns lifecycle data, `scheduler/`
owns route selection and physical queues, and `worker/` owns queue draining.
A job body receives the `Job` itself for phase/item reporting; simple bodies
ignore it. An async callback calls `QueueHandler.retain(job)` before the body
returns and `QueueHandler.resume(job, callback)` when it schedules or completes
its children.

## Statistics source of truth

The complete profile-statistics flow, filter encoding, Mongo document shape, compound index, cache ownership and Discord/API composition are documented in [`profile-statistics-source-of-truth.md`](profile-statistics-source-of-truth.md). Start there when a future task mentions `ProfileStatistics`, `SummonerOverview`, `filterKey`, `recentMatches` or `lastUpdate`.

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
- [ADR-0014: Global job scheduler](adr/0014-global-job-scheduler.md) — **current queue contract** (`QueueHandler`/`Job`/`RiotScheduler`/`ComputeScheduler`/`SyncScheduler`)

## Developer handbook

Operational guide for adding command / endpoint / service / model / queue / Mongo / Redis: [`docs/HANDBOOK.md`](../HANDBOOK.md).

## MongoDB migration

The operational documentation for the LoL migration is in [`docs/mongo/`](../mongo/README.md). ADR-0009 is accepted; Guardian gates remain mandatory for every capability.

## Macro-task index

Archived in [`docs/agents/_archive/`](../agents/_archive/) — completed: `0000`, `0001`, `0003`, `0004`, `0007`; under review: `0002`, `0005`, `0006`, `0008` (see `HANDBOOK.md` for status). Current order and gates in [`docs/agents/README.md`](../agents/README.md).
