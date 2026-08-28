# Leaderboard rank indexes

Tier-filtered leaderboard pages filter on `ranks.<QUEUE>.rank` and sort on
`ranks.<QUEUE>.mmr DESC`. The MMR-only indexes documented in
[`01-db-structure.md`](01-db-structure.md) remain required for all-ranks pages;
these compound indexes cover tier-scoped pages such as `rank=SILVER`.

Run manually on each environment after deploy. The runtime does not create or
drop indexes.

## Create

```javascript
const soloRankMmr = "ranks.RANKED_SOLO_5X5.rank";
const soloMmr = "ranks.RANKED_SOLO_5X5.mmr";
const flexRankMmr = "ranks.RANKED_FLEX_SR.rank";
const flexMmr = "ranks.RANKED_FLEX_SR.mmr";

db.summoner.createIndex(
  { [soloRankMmr]: 1, [soloMmr]: -1 },
  {
    name: "summoner_leaderboard_solo_rank_mmr",
    partialFilterExpression: { [soloMmr]: { $exists: true } }
  }
);

db.summoner.createIndex(
  { region: 1, [soloRankMmr]: 1, [soloMmr]: -1 },
  {
    name: "summoner_leaderboard_solo_region_rank_mmr",
    partialFilterExpression: { [soloMmr]: { $exists: true } }
  }
);

db.summoner.createIndex(
  { [flexRankMmr]: 1, [flexMmr]: -1 },
  {
    name: "summoner_leaderboard_flex_rank_mmr",
    partialFilterExpression: { [flexMmr]: { $exists: true } }
  }
);

db.summoner.createIndex(
  { region: 1, [flexRankMmr]: 1, [flexMmr]: -1 },
  {
    name: "summoner_leaderboard_flex_region_rank_mmr",
    partialFilterExpression: { [flexMmr]: { $exists: true } }
  }
);
```

Keep the existing MMR-only indexes:

```javascript
{"ranks.RANKED_SOLO_5X5.mmr": -1}
{"region": 1, "ranks.RANKED_SOLO_5X5.mmr": -1}
{"ranks.RANKED_FLEX_SR.mmr": -1}
{"region": 1, "ranks.RANKED_FLEX_SR.mmr": -1}
```

## Validate

```javascript
db.summoner.getIndexes().filter(index =>
  index.name.startsWith("summoner_leaderboard_") && index.name.includes("rank")
);
```

Tier-filtered page (GLOBAL, Silver, page 2):

```javascript
db.summoner.find(
  {
    "ranks.RANKED_SOLO_5X5.mmr": { $exists: true },
    "ranks.RANKED_SOLO_5X5.rank": {
      $in: ["SILVER_IV", "SILVER_III", "SILVER_II", "SILVER_I"]
    }
  },
  {
    _id: 1,
    riotId: 1,
    region: 1,
    level: 1,
    icon: 1,
    "ranks.RANKED_SOLO_5X5": 1,
    masteries: 1
  }
)
.sort({ "ranks.RANKED_SOLO_5X5.mmr": -1 })
.skip(50)
.limit(50)
.explain("executionStats");
```

Expected: `winningPlan` uses `summoner_leaderboard_solo_rank_mmr`, low
`totalKeysExamined` relative to collection size, no blocking `SORT`.

Regional variant:

```javascript
db.summoner.find(
  {
    region: "EUW1",
    "ranks.RANKED_SOLO_5X5.mmr": { $exists: true },
    "ranks.RANKED_SOLO_5X5.rank": {
      $in: ["SILVER_IV", "SILVER_III", "SILVER_II", "SILVER_I"]
    }
  },
  {
    _id: 1,
    riotId: 1,
    region: 1,
    level: 1,
    icon: 1,
    "ranks.RANKED_SOLO_5X5": 1,
    masteries: 1
  }
)
.sort({ "ranks.RANKED_SOLO_5X5.mmr": -1 })
.limit(50)
.explain("executionStats");
```

Expected: `summoner_leaderboard_solo_region_rank_mmr`.

All-ranks page must keep using the MMR-only index:

```javascript
db.summoner.find(
  { "ranks.RANKED_SOLO_5X5.mmr": { $exists: true } },
  { _id: 1, "ranks.RANKED_SOLO_5X5": 1 }
)
.sort({ "ranks.RANKED_SOLO_5X5.mmr": -1 })
.limit(50)
.explain("executionStats");
```
