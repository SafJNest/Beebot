# ADR-0013: Champion tier-list projection

- Status: Accepted
- Owner: Main agent
- Date: 2026-08-11

## Decision

Expose `GET /api/lol/champions/tier-list` through `ChampionController` and
`ChampionService`. The request accepts the existing champion-stat dimensions:
patch, queue, minimum rank and region. For queues with lanes, one response
contains the five role lists; queues without lanes expose one list with a null
role.

`champion_stats` remains the sole persistence owner. Mongo reads the selected
ready `ChampionStatsDocument` for the scope and derives every role from each
champion's raw `lanes.<lane>` leaf; no overview or tier-list projection is persisted.
`ChampionTierAnalyzer` calculates the score in Java after clustering every
champion-role pair from the requested lane buckets. The two standardized
features are `log1p(picksInRole)` and `logit(picksInRole /
totalPicksAcrossRoles)`; the cluster whose centroid is higher on both features
owns the eligible roles. Only eligible champions contribute to the role
distribution: the score is Z-score adjusted win rate (50%), pick rate (45%)
and ban rate (5%). The adjusted win rate is shrunk toward the role-wide win
rate with a prior strength equal to the eligible-pick median; its Z-score
includes posterior variance. Counter and strong lists use the per-champion
median matchup games among opponent champions already eligible in the same
role. That median is both shrinkage strength and minimum relative reliability,
then the adjusted matchup delta determines direction. No absolute game,
pick-rate or matchup-game threshold is part of the projection. The response
exposes only the reliable directional counter and strong subsets.
An exact role share of one is bounded using the smallest positive off-role
volume observed in the same matrix, avoiding an infinite logit without adding
an absolute eligibility threshold. The Redis key includes an algorithm version
so cached projections cannot survive a scoring or eligibility change.

Redis stores only complete tier-list responses, keyed by the base filter with
no lane. Champion stats refresh invalidates the related tier-list cache key.
A missing or stale role returns a `PARTIAL` payload with `refresh=true`, starts
the existing patch-and-queue matrix refresh, and is never cached.

## Invariants

- The HTTP request never scans raw matches or calculates champion statistics.
- No Mongo tier-list collection or persisted score is introduced.
- Tier scores are compared only inside the same patch, queue, rank, region and
  role bucket.
- Existing champion page, build, stats and refresh contracts remain unchanged.
