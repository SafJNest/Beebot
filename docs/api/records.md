# Scope: Records

`GET /api/lol/{shard}/profile/{puuid}/records`, `GET /api/lol/records`,
`GET /api/lol/records/{metric}` and
`GET /api/lol/records/HIGHEST_MASTERY/{champion}` retain their filters and
pagination. Champion names use Data Dragon names such as `thresh`.

Record lists are read directly from Mongo and ordered by `value`: descending
for highest-value metrics, ascending for earliest-time metrics, then
`occurredAt ASC, puuid ASC`. Rows expose neither `score` nor `globalRanking`
nor `regionRanking`. There are no record ranking calculations, Redis sorted
sets, or `record-index` jobs. Other payload fields and presentation are unchanged.

`HIGHEST_MASTERY` persists one rebuildable row per canonical
`Summoner.masteries` entry, with `points` as `value` and `masteryLevel` from
`Mastery.level`. The overview returns its five highest rows; the champion
route additionally filters `championId`.

Only paginated totals are cached in Redis, for 60 seconds per
`filterKey + metric + GLOBAL|region + all|championId`, independently of limit
and offset. Successful writes invalidate totals whose row count changed;
value-only updates retain them. A cache miss runs `countDocuments` once and
stores the result, including zero.
