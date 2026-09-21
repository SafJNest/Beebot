# Profile record indexes

`profile_records` is a projection rebuildable from the canonical `match`,
`match_events` and `summoner.masteries` documents. Each document has an `_id`
ObjectId generated once. Match-derived metrics have one row per `{ puuid,
filterKey, metric }`; `HIGHEST_MASTERY` has one row per `{ puuid, filterKey,
metric, championId }`.

```javascript
db.profile_records.dropIndex("profile_records_identity")

db.profile_records.createIndex(
  {puuid: 1, filterKey: 1, metric: 1, championId: 1},
  {name: "profile_records_identity", unique: true}
)

db.profile_records.createIndex(
  {filterKey: 1, metric: 1, score: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_global"}
)

db.profile_records.createIndex(
  {filterKey: 1, metric: 1, region: 1, score: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_regional"}
)

db.profile_records.createIndex(
  {filterKey: 1, metric: 1, championId: 1, score: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_mastery_global"}
)

db.profile_records.createIndex(
  {filterKey: 1, metric: 1, championId: 1, region: 1, score: -1, occurredAt: 1, puuid: 1},
  {name: "profile_records_mastery_regional"}
)
```

Indexes are applied by the operator. The runtime only creates the
missing collection and does not create, modify, or remove indexes.

Apply the identity replacement before the first mastery projection rebuild:

```text
!test audit mastery-records
!test regenerate mastery-records
!test audit mastery-records
```

The audit is read-only. It reports the projected raw BSON for one record per
canonical mastery and the live collection/index sizes, so the second run shows
the actual footprint after the projection exists.

The global list uses `filterKey + metric`, sort `score DESC, occurredAt ASC,
puuid ASC`; the regional one adds `region`. `HIGHEST_MASTERY` may additionally
bind `championId`, using the mastery indexes above. The `/records` overview
performs a limited read of the leader for each metric on the same index.
Position is not persisted. Any Redis rank cache is a discardable projection
and is not part of the Mongo source-of-truth contract. Contextual record
ranking derives lazy Redis ZSETs from `filterKey + metric + GLOBAL|region` and,
for `HIGHEST_MASTERY`, optionally the exact champion ID.
