# Profile record indexes

`profile_records` is rebuildable from canonical `match`, `match_events` and
`summoner.masteries` documents. Match metrics have one row per
`{puuid, filterKey, metric}`; mastery adds `championId`. Keep the existing unique
identity index `{puuid: 1, filterKey: 1, metric: 1, championId: 1}`.

Records store and sort their actual `value`, with `occurredAt ASC`
as the only tie breaker. Highest metrics sort descending; earliest-time metrics
sort ascending. Rows with equal value and occurrence time have no guaranteed
order and can move between offset-based pages. PUUID remains in the unique
identity index only: its prefix covers profile reads, upserts and deletions,
without a separate single-field PUUID index. Record rows have no `score`,
global/regional positions, Redis ZSETs or ranking jobs. Redis caches only the
paginated total, backed by durable
`aggregates`, for 60 seconds per
filter/metric/region/champion; row-count changes invalidate it.

## Value indexes

For a new installation, create the following sort indexes. For existing
indexes containing PUUID, follow the replacement procedure below after
deploying the query change. Indexes on the retired `score` field cannot provide
the value sort order. No index or query sorts by `_id`.

```javascript
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, value: -1, occurredAt: 1},
  {name: "profile_records_value_global"}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, region: 1, value: -1, occurredAt: 1},
  {name: "profile_records_value_regional"}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, championId: 1, value: -1, occurredAt: 1},
  {name: "profile_records_mastery_value_global"}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, championId: 1, region: 1, value: -1, occurredAt: 1},
  {name: "profile_records_mastery_value_regional"}
)

const earliestMetrics = [
  "FIRST_KILL_TIME", "FIRST_BLOOD_TIME", "FIRST_DRAKE_TIME",
  "FIRST_BARON_TIME", "FIRST_ELDER_TIME"
];
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, value: 1, occurredAt: 1},
  {name: "profile_records_earliest_global", partialFilterExpression: {metric: {$in: earliestMetrics}}}
)
db.profile_records.createIndex(
  {filterKey: 1, metric: 1, region: 1, value: 1, occurredAt: 1},
  {name: "profile_records_earliest_regional", partialFilterExpression: {metric: {$in: earliestMetrics}}}
)
```

The two ascending indexes cover only earliest-time metrics; they preserve the
same ascending occurrence-time tie breaker without indexing every mastery row twice.
Indexes are operator-managed; the runtime never creates or drops them.
Compare plans and keys/documents examined with `explain("executionStats")`.

## Replace existing PUUID sort indexes

Deploy the query change before replacing indexes: an old query still sorting by
PUUID cannot obtain its full order from the smaller indexes. Existing indexes
continue to support the new query until replacement.

In mongosh, select the intended database and run the following block. It keeps
`_id_`, the unique identity index and unrelated indexes (including
`profile_records_aggregate_counts`). Only the six known sort indexes containing
PUUID are rebuilt; names and earliest-metric partial filters are preserved.
The block is safe to rerun after a partial failure: it also creates a missing
replacement. Indexes are dropped and recreated one at a time; reads using that
index may slow down during its rebuild. No record regeneration is required.

```javascript
{
  const records = db.getCollection("profile_records");
  records.createIndex(
    {puuid: 1, filterKey: 1, metric: 1, championId: 1},
    {name: "profile_record_identity", unique: true}
  );
  const earliestMetrics = [
    "FIRST_KILL_TIME", "FIRST_BLOOD_TIME", "FIRST_DRAKE_TIME",
    "FIRST_BARON_TIME", "FIRST_ELDER_TIME"
  ];
  const replacements = [
    ["profile_records_value_global",
      {filterKey: 1, metric: 1, value: -1, occurredAt: 1}],
    ["profile_records_value_regional",
      {filterKey: 1, metric: 1, region: 1, value: -1, occurredAt: 1}],
    ["profile_records_mastery_value_global",
      {filterKey: 1, metric: 1, championId: 1, value: -1, occurredAt: 1}],
    ["profile_records_mastery_value_regional",
      {filterKey: 1, metric: 1, championId: 1, region: 1, value: -1, occurredAt: 1}],
    ["profile_records_earliest_global",
      {filterKey: 1, metric: 1, value: 1, occurredAt: 1}],
    ["profile_records_earliest_regional",
      {filterKey: 1, metric: 1, region: 1, value: 1, occurredAt: 1}]
  ];
  for (const [name, key] of replacements) {
    const existing = records.getIndexes().find(index => index.name === name);
    const options = {name};
    if (name.startsWith("profile_records_earliest_")) {
      options.partialFilterExpression = {metric: {$in: earliestMetrics}};
    }
    if (existing && "puuid" in existing.key) {
      records.dropIndex(name);
    }
    records.createIndex(key, options);
  }
  printjson(records.getIndexes());
}
```

Public routes, response models, totals and presentation remain unchanged; only
ordering among rows with equal value and occurrence time changes. Review
`explain("executionStats")` for global/regional, champion-specific and earliest
reads and compare `indexSizes` before and after replacement. Disk savings are
not estimated from key count because WiredTiger compresses index prefixes.

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

Persistence, explicit rebuilds and deployment steps: [aggregate snapshots](16-aggregates.md).
