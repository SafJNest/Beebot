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

lol/model/leaderboard/
  LeaderboardPage
  LeaderboardDistribution

lol/model/statistics/
  ProfileStatistics
  Stats
  ProfileStatisticsRow

lol/model/
  ChampionView
```

Spring owns controllers, configuration and HTTP error models. It must not own operational LoL success DTOs.

## Statistics source of truth

The complete profile-statistics flow, filter encoding, Mongo document shape, compound index, cache ownership and Discord/API composition are documented in [`profile-statistics-source-of-truth.md`](profile-statistics-source-of-truth.md). Start there when a future task mentions `ProfileStatistics`, `SummonerOverview`, `filterKey`, `recentMatches` or `lastUpdate`.

## ADR index

- [ADR-0001: Canonical LoL model boundaries](adr/0001-canonical-lol-model-boundaries.md)
- [ADR-0002: Summoner view and leaderboard contract](adr/0002-summoner-view-and-leaderboard-contract.md)
- [ADR-0003: Match and match result models](adr/0003-match-and-match-result-models.md)
- [ADR-0004: Profile statistics asynchronous generation](adr/0004-profile-statistics-refresh-queue.md)
- [ADR-0005: LoL API JSON contract](adr/0005-lol-api-json-contract.md)
- [ADR-0006: Champion API contract](adr/0006-champion-api-contract.md)
- [ADR-0007: Unified API result and parameter parsing](adr/0007-unified-api-result-and-parameters.md)
- [ADR-0008: Component caches and asynchronous match lookups](adr/0008-endpoint-cache-and-async-lookups.md)
- [ADR-0009: MongoDB persistence and LoL migration](adr/0009-mongo-persistence-and-migration.md)
- [ADR-0010: Database refresh queue](adr/0010-database-refresh-queue.md)

## MongoDB migration

La documentazione operativa della migrazione LoL è in [`docs/mongo/`](../mongo/README.md). L'ADR-0009 è accettata; i gate Guardian restano obbligatori per ogni capability.

## Macro-task index

Implementation order and gates are maintained in [`docs/agents/`](../agents/README.md).
