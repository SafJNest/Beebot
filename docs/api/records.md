# Scope: Records — contextual positions

`GET /api/lol/{shard}/profile/{puuid}/records`, `GET /api/lol/records` and
`GET /api/lol/records/{metric}` retain their current filters, pagination and
payload shape. `GET /api/lol/records/highest_mastery/{champion}` is the
champion-specific ladder, where `champion` is a Data Dragon champion name such
as `thresh` or `blitzcrank`. Every classifiable `ProfileRecord` can additionally expose:

```json
{
  "globalRanking": 391,
  "regionRanking": 82
}
```

Both values are nullable, one-based competition ranks in the record's exact
existing context. Global context is `filterKey + metric`; regional context adds
only the record's region. For a champion-scoped mastery request the exact
`championId` is part of both contexts. Equal `score` values share the same
ranking position, so two `31`-kill records are both `#1` and the next lower
score is `#3`.

`HIGHEST_MASTERY` is the sole champion-scoped metric. Mongo persists one
rebuildable row for every canonical `Summoner.masteries` entry with the same
`puuid`, `championId` and `points` as its `value`/`score`, plus `masteryLevel`
from `Mastery.level` (omitted on every other metric). The global overview
returns its five highest rows under `highest_mastery`; the champion route adds
the exact `championId` context. No queue, lane or kill-type record dimension is
introduced.
