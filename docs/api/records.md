# Scope: Records — contextual positions

`GET /api/lol/{shard}/profile/{puuid}/records`, `GET /api/lol/records` and
`GET /api/lol/records/{metric}` retain their current filters, pagination and
payload shape. Every classifiable `ProfileRecord` can additionally expose:

```json
{
  "globalRanking": 391,
  "regionRanking": 82
}
```

Both values are nullable, one-based competition ranks in the record's exact
existing context. Global context is `filterKey + metric`; regional context adds
only the record's region. Equal `score` values share the same ranking position,
so two `31`-kill records are both `#1` and the next lower score is `#3`.

No route or context dimension is added by this contract. In particular, the
current record model has no independent champion, queue, lane or kill-type
filter: event-derived metrics such as first kill and baron kills are already
represented by `RecordMetric`.
