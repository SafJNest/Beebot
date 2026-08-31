# Profile record indexes

`profile_records` is a projection rebuildable from the canonical
`match` and `match_events` documents. Each document has an `_id` ObjectId generated once,
but the business identity is `{ puuid, filterKey, metric }`.

```javascript
db.profile_records.createIndex(
  {puuid: 1, filterKey: 1, metric: 1},
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
```

Indexes are applied by the operator. The runtime only creates the
missing collection and does not create, modify, or remove indexes.

The global list uses `filterKey + metric`, sort `score DESC, occurredAt ASC,
puuid ASC`; the regional one adds `region`. The `/records` overview performs
a limited read of the leader for each metric on the same index. Position
is not persisted. Any Redis rank cache is a discardable projection
and is not part of the initial Mongo contract.
