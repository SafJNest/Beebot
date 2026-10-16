# Contextual ranking operational notes

`competitive` remains the Mongo source of truth for leaderboard ranking. It
stores numeric MMR plus `score = wins - losses` from the ranked queue entry:
the Riot `tier` is not persisted there. More played games increase the weight
of a positive win rate because the score is cumulative rather than a raw rate.
Every exact-division operation derives its MMR interval at the query boundary;
for example, `GOLD_IV` is `[1200, 1300)`.
`RankService` persists `summoner.ranks`, `CompetitiveService` alone writes this
projection, and `LeaderboardService` alone writes its Redis index. A valid rank
always creates a competitive row; absent `primary` and `otpChampionId` fields
are omitted and an existing known value is retained when a later refresh cannot
derive a replacement. Existing rows must be regenerated with `!test regenerate competitive`
before contextual ranking is enabled.

Redis keys are derived only:

```text
beebot:lol:los:leaderboard:<queue>:GLOBAL|<region>:<exact-tier>
beebot:lol:los:leaderboard:counts:<queue>:GLOBAL|<region>
beebot:lol:los:leaderboard:segments
beebot:lol:los:leaderboard:access
beebot:lol:los:leaderboard:building:<build-id>
beebot:lol:los:ranking:<filterKey>:<metric>:GLOBAL|<region>
beebot:lol:los:ranking:segments
beebot:lol:los:ranking:access
beebot:lol:los:ranking:building:<build-id>
```

Leaderboard ZSET score is `competitive.mmr`; record ZSET score is
`profile_records.score`. Leaderboard members contain the sortable signed
`competitive.score` followed by the 128-bit SHA-256 competitive identity. This
makes reverse ZSET ordering use MMR then score while retaining MMR as the
numeric ZSET score. The identity suffix only keeps Redis members unique; exact
MMR-and-score ties have no added Mongo ordering criterion. Record members
remain binary 128-bit SHA-256 hashes. The global and regional exact-rank count
hashes are rebuilt by assigning each
`competitive.mmr` to its exact MMR interval.
The segment registry and access hash are Redis-only observability metadata.

`GET /api/lol/leaderboard?summoner=...` uses the requested queue rank to
select a tier and keeps the requested region. If each division segment needed
for the page is resident, the service combines exact-division counts with
`ZREVRANK`, then reads only the page's Redis member ranges. Members remain the
sortable score prefix plus competitive identity; Mongo resolves those IDs to
PUUIDs and the existing summoner projection hydrates the response. This path
does not read or write the one-day page cache.

When a required segment is absent, Mongo uses the player's `competitive` row
as its seek anchor. An indexed count of rows with a greater `(mmr, score)`
tuple determines the page; bounded queries fetch the nearest rows before and
after it. No deep `skip` is used. Existing exact MMR/score ties have no Mongo
tie-breaker and can move between pages. The compound global/regional indexes
above cover the filter and order, but the count can still examine many index
keys in Gold/Emerald. Verify with `explain("executionStats")` on representative
data before deciding to prewarm these lower divisions. The fallback issues no
Riot API search: Riot ID matching is exact against stored `summoner.riotId`.

Apply these operator-managed indexes after checking existing names and running
the corresponding `explain("executionStats")` commands.
Replace the existing contextual index definitions with these compound keys;
Mongo does not change an existing index definition when its name is reused.

```javascript
db.competitive.createIndex(
  {queue: 1, mmr: -1, score: -1},
  {name: "competitive_contextual_global"}
)

db.competitive.createIndex(
  {queue: 1, region: 1, mmr: -1, score: -1},
  {name: "competitive_contextual_regional"}
)
```

The existing `profile_records_global` and `profile_records_regional` indexes
already cover record-segment rebuilds. Redis loss does not alter Mongo:
`LeaderboardService.warmupIndexAsync()` clears registered leaderboard segments
to remove members written with the prior encoding, rebuilds permanent Master+
segments and all count hashes at startup, while lazy leaderboard and record
segments rebuild on the next matching request. After deploying the new
projection, run `!test regenerate competitive` once so existing Mongo rows gain
their score.

Run `!test ranking` to print separate leaderboard and record index status:
resident segment count, cardinality, Redis `MEMORY USAGE`, idle TTL, last access
and in-progress state. Use it after a real `!test regenerate competitive` rebuild to record
the required MASTER+, global/regional `[1200, 1300)` and representative record
measurements.
