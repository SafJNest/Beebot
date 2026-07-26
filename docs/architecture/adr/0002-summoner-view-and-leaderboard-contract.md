# ADR-0002: Summoner view and leaderboard contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The profile and leaderboard currently create different summoner structures. The leaderboard also performs mapping, overview assembly and calculations inside one service.

## Decision

Use one canonical chain:

```text
Summoner -> SummonerView -> SummonerLeaderboard
```

`SummonerView` is the complete profile projection. `SummonerLeaderboard` contains leaderboard position data and exactly one `SummonerView`; it must not duplicate summoner identity fields.

`SummonerView.from(...)` is the single factory for assembling profile data. Overview parsing is centralized in `SummonerOverview.from(...)` or `LeaderboardSummonerOverview.from(...)`, according to the existing data contract. The factory consumes existing `ProfileStatistics`, `Rank`, mastery and champion data.

The leaderboard reuses the complete view in the first phase. If a view section is not ready, `DatabaseTracker` submits the missing statistics refresh to the two-worker database queue; the leaderboard request returns `PENDING` until the complete page can be assembled. The profile request keeps the available view as `PARTIAL`; neither flow performs sequential Riot fetches during the request.

## Data rules

- Champion names and images come from `ChampionUtils`.
- Lane and queue labels come from the existing lane and queue utilities.
- Win rate, KDA and rounded values come from the statistics model or its existing utilities.
- `LeaderboardService` does not contain `toSummoner`, `overview`, `mostPlayed`, `ratio`, `rounded` or local Riot ID parsing.

## JSON shape

Profile and leaderboard expose the same nested `summoner` representation. The leaderboard adds page metadata and row position around that shared model.

## Rejected alternatives

- A lightweight leaderboard summoner would create a second projection too early.
- A mapper-specific nested `Summoner` record would duplicate the canonical view.
- Building the complete view with Riot calls in the HTTP request would make latency depend on page size.

## Acceptance criteria

- One summoner identity structure is serialized in profile and leaderboard responses.
- One page construction path exists in `LeaderboardService`.
- Missing statistics are refreshed immediately in the background, never synchronously rebuilt by the request.
