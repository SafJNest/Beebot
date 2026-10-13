# ADR-0001: Canonical LoL model boundaries

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14
- Amended: 2026-08-20; shared contract placement amended by ADR-0018 on 2026-10-10
- Ownership of LeagueService and ProfileStatisticsService: superseded by ADR-0011 and ADR-0012

## Context

Profile, search, leaderboard and match responses currently duplicate domain data in `spring.lol.model`, `spring.dto` and mapper-specific nested records.

## Decision

`com.R4B.api.dto` owns shared request, success/error models and serialized components; `com.R4B.lol` owns shared values and pure helpers (ADR-0018). Spring owns controllers, configuration, exception/status mapping, persistence and producer logic. `spring.lol.model` retains producer-only structures.

Historical application model packages before ADR-0018 were:

- `spring/lol/model/summoner` for summoner identity, ranks, mastery and views;
- `spring/lol/model/match` for complete matches, participants and lightweight results;
- `spring/lol/model/leaderboard` for pages and aggregates;
- `spring/lol/model/statistics` for reusable statistics aggregates.

Controllers return canonical models directly. A new DTO is allowed only when an accepted ADR documents an HTTP-only concern that cannot belong to the domain model.

## Naming invariants

- Use `Summoner` for the base identity model.
- Use `Rank`, never `SummonerRank`.
- Use `Mastery`, never `ChampionMastery`.
- Keep `riotId` as a `String`.
- Use `Match`, `Participant` and `MatchResult` as the only match data levels.

## Ownership

- `SummonerService` owns construction of base summoner data from service/database records.
- Producing services own application projection, assembly and static-data lookup; shared factories perform only pure data construction.
- `ProfileService` owns statistics persistence and refresh; `ProfileAnalyzer` is pure.
- `DatabaseTracker` owns asynchronous database refresh dispatch and in-flight deduplication; `Tracker` owns only match lookup and match analysis queues.
- `LeaderboardService` owns filtering, pagination, cache and distribution access.

## Rejected alternatives

- Keeping separate profile and leaderboard DTOs would preserve duplicate ownership.
- Parsing and mapping the same Riot data inside every service would make field behavior diverge.
- Moving domain models into Spring would couple the domain to HTTP serialization.

## Acceptance criteria

- No operational LoL success DTO remains under `spring.dto` after migration.
- No second owner exists for summoner, rank, mastery or match projections.
- Each macro-task identifies the canonical owner before changing code.
