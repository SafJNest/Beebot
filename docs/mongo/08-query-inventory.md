# Inventario query LoL

La controparte runtime vive in `MongoDB.java`; i percorsi caldi usano projection tipizzate o `QueryRecord` detached.

| Area | Query Mongo target | Budget applicativo | Consumer |
|---|---|---:|---|
| search/autocomplete | una `find` su `summoner` con prefix `region + riotSearch`, projection base + `ranks`, rank Solo incluso | 1 | SummonerService |
| linked accounts by userId | `find({userId})` ordinato per `_id`; mappa a `Summoner` canonico (`region` come `LeagueShard`) | 1 | UserData / Discord |
| profile | una `find` su `summoner` con projection `Summoner + ranks + masteries`; statistiche Redis prima, Mongo dopo | 2 | ProfileService |
| leaderboard | `find` su `competitive` con filtro queue/tier/regione/ruolo/OTP, sort MMR e PUUID limitati; un `$in` su `summoner._id` carica la pagina; total separato Redis → aggregate → `countDocuments` | 2 per pagina + count solo su cache miss | LeaderboardService |
| profile statistics batch | `{puuid: {$in: [...]}, filterKey}`, flat root projection, unique identity index | 1 | ProfileService |
| history | participant filter in un unico `$elemMatch`, projection/paging limitati; `countDocuments` diretto | 1 + eventi batch | LeagueMessage |
| match results | projection dei soli campi necessari ai `MatchResult` e partecipanti | 1 | profile/tracker |
| match events | `_id: {$in: [...]}` su `match_events` | 1 | match detail/history |
| champion | match id con projection; build e statistiche leggono solo participant richiesti; batch raw senza `Match -> Participant` completo | 2 per batch (+ count/trend) | Champion services |
| leaderboard aggregates | snapshot Mongo `leaderboard_aggregates` per filtro; rebuild ogni 12 ore e `$match` + `$group` sul path `summoner.ranks.<QUEUE>` per nuovo filtro | 1 | LeaderboardService |
| writes | update atomici, pipeline participant, bulk unordered per build/statistiche/summoner; unique `{puuid, filterKey}` | 1 per update/batch | MongoDB/tracker |

## Projection e filtri

La search restituisce direttamente il payload che serve a search e autocomplete: `Summoner` e rank `RANKED_SOLO_5X5` sono letti nella stessa projection. Non esiste più il ciclo `findRank` per PUUID.

Profilo e leaderboard usano campi BSON strutturati. I filtri champion e lane vengono applicati allo stesso elemento di `participants` tramite un unico `$elemMatch`; non possono più soddisfare champion e lane su due partecipanti diversi.

Le query paginated sono limitate a 100 match, 50 summoner leaderboard, 25 risultati search, 500.000 chiavi summoner per pagina e 50.000 chiavi match per pagina. I dati completi dei summoner vengono letti e scritti in sotto-batch da 20.000; i match e gli eventi restano in sotto-batch da 1.000. I cursori dei batch lunghi devono essere chiusi esplicitamente.

## Invarianti

PUUID è l'identità summoner e `_id` del documento; il Riot match ID completo è l'identità match; enum R4J usa `name()`; bans usa BLUE e RED; participant resta flat; upsert/update/delete sono idempotenti; letture e scritture applicative Mongo-only; errori di lettura Mongo espliciti.

MariaDB conserva JSON UTF-8 in `champion_builds.data`, `champion_stats.data` e `profile_statistics.data`. Mongo conserva `build` come BSON strutturato; `profile_statistics` salva direttamente i timestamp e le sole foglie `champions.<championId>.<canonicalQueue>.<position>`, oltre a `pings`, `spellOne` e `spellTwo`, mai sotto un campo `statistics`. I matchup vivono soltanto nella collection `profile_matchups`; non esistono `matchups` o `duoStats` root. Non vengono letti o convertiti payload Kryo e non viene creato alcun `legacyPayload`; i documenti legacy vengono rigenerati con il nuovo `puuid + filterKey`.

`profile_matchups` è una collection separata: il suo payload `matchups` conserva esclusivamente `champions.<championId>.<canonicalQueue>.<position>.matchups.<opponentId>`. Non salva righe aggregate per champion o matchup fuori dalla foglia.

Il dettaglio del formato di `filterKey`, del motivo dell'indice composto e della differenza tra aggregato e `recentMatches` è in [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

## Policy degli indici

Gli indici sono gestiti dall'operatore del database, non dal runtime né dalla
migration. Devono seguire le query effettive:

| Collection | Indice | Query coperta |
|---|---|---|
| `summoner` | `summoner_search_prefix` | search/autocomplete per regione e prefix, con sort `riotId` |
| `summoner` | `summoner_riot_id` | fallback exact/case-insensitive di `findPuuid` |
| `summoner` | `summoner_user_accounts` | account Discord per `userId`, ordinati per `_id` |
| `summoner` | `summoner_tracking_true` | tracker e account con `tracking=true` |
| `competitive` | queue/region/role/OTP/MMR con PUUID | pagina leaderboard, sort `mmr DESC`; indice specifico per scope |
| `match` | `match_participant_time` | history, profilo, OPGG, match recenti e dati LP |
| `match` | `match_shard_time`, `match_shard_patch_time`, `match_patch` | query temporali, region/patchMajor, bans e champion wins |
| `match` | `match_champion_filter` | batch champion con filtro equality-first e participant/lane |
| `match` | `match_champion_keyset` | `findChampionMatchIds` con paging keyset su `_id` |
| `profile_statistics` | `profile_statistics_identity` | lookup/upsert/delete/batch per `{puuid, filterKey}`, `unique` |
| `profile_statistics` | `profile_statistics_period` | projection su intervalli `timeStart`/`timeEnd` |
| `profile_activity` | `profile_activity_identity` | lookup/upsert per `{puuid, filterKey}`, `unique` |
| `profile_matchups` | `profile_matchups_identity` | lookup/upsert per `{puuid, filterKey}`, `unique` |
| `champion_builds` | `champion_builds_filter` | build aggregate per `filterKey` |
| `champion_stats` | `champion_stats_filter` | mega-aggregato per `filterKey`; projection `statistics.<championId>` |
| `champion_stats` | `champion_stats_filter_champion` | lettura compatibile dei vecchi documenti per `filterKey` e `championId` |

`match_events`, `leaderboard_aggregates`, `migration_runs`, `champion` e i
lookup diretti di match/summoner restano coperti da `_id`. Non vengono creati
indici su `masteries`, metriche participant o ogni combinazione possibile di
`Filter`; `opponent` e `duo` restano filtri relazionali applicati in Java.

L'unicità di `{puuid, filterKey}` richiede un controllo operativo di identità
mancanti e duplicati prima di applicare il relativo indice unique; il cleanup
resta manuale.

## Explain richiesti

Prima dell'accettazione eseguire su un database con dati rappresentativi:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}, {riotId: 1, ranks: 1}).sort({riotId: 1}).limit(25).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid", champion: 1}}, region: "EUW1", queue: "RANKED_SOLO_5X5", patchMajor: "14.2"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid"}}, region: "EUW1", queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}, timeStart: {$lt: 1714514400000}}).sort({timeStart: -1, _id: -1}).limit(1).explain("executionStats")
db.match.distinct("participants.puuid", {region: "EUW1", queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}})
db.match.aggregate([
  {$match: {queue: {$in: ["TEAM_BUILDER_RANKED_SOLO", "RANKED_SOLO_5X5"]}}},
  {$unwind: "$participants"},
  {$group: {_id: {region: "$region", puuid: "$participants.puuid"}}},
  {$sort: {"_id.region": 1, "_id.puuid": 1}}
], {allowDiskUse: true}).explain("executionStats")
db.competitive.find(
  {queue: "RANKED_SOLO_5X5", region: "EUW1", mmr: {$gte: 800, $lt: 1200}},
  {_id: 0, puuid: 1}
).sort({mmr: -1}).skip(50).limit(50).explain("executionStats")
db.competitive.find(
  {queue: "RANKED_SOLO_5X5", region: "EUW1", primary: "UTILITY", mmr: {$gte: 30000}},
  {_id: 0, puuid: 1}
).sort({mmr: -1}).limit(50).explain("executionStats")
db.summoner.find(
  {_id: {$in: ["<page-puuid-1>", "<page-puuid-2>"]}},
  {_id: 1, riotId: 1, region: 1, level: 1, icon: 1, ranks: 1, masteries: 1}
).explain("executionStats")
```

Gli explain devono verificare `executionTimeMillis`, `totalKeysExamined`,
`totalDocsExamined`, `nReturned`, `winningPlan`, `indexName` e l'assenza di
`COLLSCAN` e l'assenza di un `SORT` bloccante. Confrontare la baseline prima/dopo con `collStats` e
`indexSizes`. La leaderboard usa `competitive` per MMR/range/ruolo e poi un
`$in` sul primary key di `summoner`; gli indici richiesti sono descritti in
[`11-leaderboard-rank-indexes.md`](11-leaderboard-rank-indexes.md).
