# Profile record indexes

`profile_records` is rebuildable from canonical `match`, `match_events` and
`summoner.masteries` documents. Match metrics have one row per
`{puuid, filterKey, metric}`; mastery adds `championId`. Keep the existing unique
identity index `{puuid: 1, filterKey: 1, metric: 1, championId: 1}`.

Records store and sort their actual `value`, with `occurredAt ASC, puuid ASC`
as tie breakers. Highest metrics sort descending; earliest-time metrics sort
ascending. Record rows have no `score`, global/regional positions, Redis ZSETs
or ranking jobs. Redis caches only the paginated total for 60 seconds per
filter/metric/region/champion; row-count changes invalidate it.

## Value indexes

Create these indexes before deploying the value-based queries. Existing
indexes on `score` cannot provide their sort order. No index or query sorts by `_id`.

```javascript
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, value: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_value_global"}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, region: 1, value: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_value_regional"}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, championId: 1, value: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_mastery_value_global"}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, championId: 1, region: 1, value: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_mastery_value_regional"}
)

const earliestMetrics = [
  "FIRST_KILL_TIME", "FIRST_BLOOD_TIME", "FIRST_DRAKE_TIME",
  "FIRST_BARON_TIME", "FIRST_ELDER_TIME"
];
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, value: 1, occurredAt: 1, puuid: 1},
  {name: "profile_records_earliest_global", partialFilterExpression: {metric: {$in: earliestMetrics}}}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, region: 1, value: 1, occurredAt: 1, puuid: 1},
  {name: "profile_records_earliest_regional", partialFilterExpression: {metric: {$in: earliestMetrics}}}
)
```

The two ascending indexes cover only earliest-time metrics; they preserve the
same ascending tie breakers without indexing every mastery row twice.
Indexes are operator-managed; the runtime never creates or drops them.
Compare plans and keys/documents examined with `explain("executionStats")`.

## Cleanup after deployment

On each affected database, after the new code and value indexes are active:

```javascript
for (const index of db.profile_records.getIndexes()) {
  if ("score" in index.key) db.profile_records.dropIndex(index.name);
}
db.profile_records.updateMany({}, {$unset: {score: "", globalRanking: "", regionRanking: ""}})
```

Existing documents remain readable before cleanup. Subsequent writes also
remove these retired fields without regenerating the records.

Remove only obsolete record ranking keys on the application Redis instance;
these commands keep leaderboard indexes and the new record count cache:

```bash
for record_prefix in beebot:lol beebot_test:lol; do
  redis-cli --scan --pattern "${record_prefix}:los:ranking:*" | xargs -r -n 500 redis-cli UNLINK
done
```

`!test audit mastery-records` remains read-only. `!test regenerate mastery-records`
rebuilds mastery rows from `summoner.masteries` when a data repair is needed.
