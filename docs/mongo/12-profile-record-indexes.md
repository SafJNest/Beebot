# Profile record indexes

`profile_records` è una proiezione ricostruibile dai documenti canonici
`match` e `match_events`. Ogni documento ha `_id` ObjectId generato una sola
volta, ma la business identity è `{ puuid, filterKey, metric }`.

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

Gli indici sono applicati dall'operatore. Il runtime crea soltanto la
collection mancante e non crea, modifica o rimuove indici.

La lista globale usa `filterKey + metric`, sort `score DESC, occurredAt ASC,
puuid ASC`; quella regionale aggiunge `region`. La posizione non è persistita.
La cache di eventuali rank Redis è una proiezione eliminabile e non fa parte
del contratto Mongo iniziale.
