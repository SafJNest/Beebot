# Contextual ranking operational notes

`competitive` remains the Mongo source of truth for leaderboard ranking. It now
stores the exact Riot `tier` (`TierDivisionType`) alongside the existing MMR
projection. Existing rows must be regenerated with `!test stats otp` before
contextual ranking is enabled.

Redis keys are derived only:

```text
beebot:lol:los:ranking:leaderboard:<queue>:GLOBAL|<region>:<exact-tier>
beebot:lol:los:ranking:leaderboard:counts:<queue>:GLOBAL|<region>
beebot:lol:los:ranking:records:<filterKey>:<metric>:GLOBAL|<region>
```

Leaderboard ZSET score is `competitive.mmr`; record ZSET score is
`profile_records.score`. Both use a binary 128-bit SHA-256 member. The global
and regional exact-rank count hashes are rebuildable from `competitive.tier`.
The segment registry and access hash are Redis-only observability metadata.

Apply these operator-managed indexes after checking existing names and running
the corresponding `explain("executionStats")` commands. `_id` is included as
the deterministic tie-break used by both Mongo and Redis.

```javascript
db.competitive.createIndex(
  {queue: 1, tier: 1, mmr: -1, _id: -1},
  {name: "competitive_contextual_global"}
)

db.competitive.createIndex(
  {queue: 1, tier: 1, region: 1, mmr: -1, _id: -1},
  {name: "competitive_contextual_regional"}
)
```

The existing `profile_records_global` and `profile_records_regional` indexes
already cover record-segment rebuilds. Redis loss does not alter Mongo: rebuild
permanent leaderboard segments through the competitive rebuild, and rebuild
lazy leaderboard/record segments on the next matching request.

Run `!test ranking` to print the resident segment count, cardinality, Redis
`MEMORY USAGE`, permanent/lazy state, idle TTL, last access and in-progress
status. Use it after a real `!test stats otp` rebuild to record the required
MASTER+, global/regional GOLD_IV and representative record measurements.
