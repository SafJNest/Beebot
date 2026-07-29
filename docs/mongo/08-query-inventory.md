# Inventario query LoL

La controparte runtime vive in `MongoDB.java`; i percorsi caldi usano projection tipizzate o `QueryRecord` detached.

| Area | Query Mongo target | Budget applicativo | Consumer |
|---|---|---:|---|
| search/autocomplete | una `find` su `summoner` con prefix `region + riotSearch`, projection base + `ranks`, rank Solo incluso | 1 | LeagueService |
| profile | una `find` su `summoner` con projection `Summoner + ranks + masteries`; statistiche Redis prima, Mongo dopo | 2 | ProfilePageService |
| leaderboard | `$match` preliminare con `$elemMatch` + `$unwind` + `$match` esatto + `$facet` per `total` e pagina; projection summoner già filtrata | 2 | LeaderboardService |
| profile statistics batch | `{puuid: {$in: [...]}, filterKey}`, flat root projection, unique identity index | 1 | ProfileStatisticsService |
| history | participant filter in un unico `$elemMatch`, projection/paging limitati; `countDocuments` diretto | 1 + eventi batch | LeagueMessage |
| match results | projection dei soli campi necessari ai `MatchResult` e partecipanti | 1 | profile/tracker |
| match events | `_id: {$in: [...]}` su `match_events` | 1 | match detail/history |
| champion | match id con projection; build e statistiche leggono solo participant richiesti; batch raw senza `Match -> Participant` completo | 2 per batch (+ count/trend) | Champion services |
| leaderboard aggregates | snapshot Mongo `leaderboard_aggregates` per filtro; rebuild ogni 12 ore e `$match` preliminare + `$unwind` + `$match` esatto + `$group` su `summoner.ranks[]` per nuovo filtro | 1 | LeaderboardService |
| writes | update atomici, pipeline participant, bulk unordered per build/statistiche/summoner; unique `{puuid, filterKey}` | 1 per update/batch | MongoDB/tracker |

## Projection e filtri

La search restituisce direttamente il payload che serve a search e autocomplete: `Summoner` e rank `RANKED_SOLO_5X5` sono letti nella stessa projection. Non esiste più il ciclo `findRank` per PUUID.

Profilo e leaderboard usano campi BSON strutturati. I filtri champion e lane vengono applicati allo stesso elemento di `participants` tramite un unico `$elemMatch`; non possono più soddisfare champion e lane su due partecipanti diversi.

Le query paginated sono limitate a 100 match, 50 summoner leaderboard, 25 risultati search, 500.000 chiavi summoner per pagina e 50.000 chiavi match per pagina. I dati completi dei summoner vengono letti e scritti in sotto-batch da 20.000; i match e gli eventi restano in sotto-batch da 1.000. I cursori dei batch lunghi devono essere chiusi esplicitamente.

## Invarianti

PUUID è l'identità summoner e `_id` del documento; il Riot match ID completo è l'identità match; enum R4J usa `name()`; bans usa BLUE e RED; participant resta flat; upsert/update/delete sono idempotenti; letture e scritture applicative Mongo-only; errori di lettura Mongo espliciti.

MariaDB conserva JSON UTF-8 in `champion_builds.data`, `champion_stats.data` e `profile_statistics.data`. Mongo conserva `build` come BSON strutturato; `profile_statistics` salva direttamente i campi `timeStart`, `timeEnd`, `lastUpdate`, `total`, `queueStats`, `laneStats`, `championStats` con il relativo contesto queue/lane, `matchups`, `duoStats`, `pings` e gli aggregati collegati, mai sotto un campo `statistics`. Non vengono letti o convertiti payload Kryo e non viene creato alcun `legacyPayload`; i documenti legacy vengono rigenerati con il nuovo `puuid + filterKey`.

Il dettaglio del formato di `filterKey`, del motivo dell'indice composto e della differenza tra aggregato e `recentMatches` è in [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

## Policy degli indici

`MongoDB.ensureIndexes()` applica un registry create-only con nomi stabili. Un
indice già presente con key pattern e opzioni compatibili viene riutilizzato;
un conflitto interrompe il bootstrap e non viene corretto con `dropIndex`. Gli
indici seguono le query effettive:

| Collection | Indice | Query coperta |
|---|---|---|
| `summoner` | `summoner_search_prefix` | search/autocomplete per regione e prefix, con sort `riotId` |
| `summoner` | `summoner_riot_id` | fallback exact/case-insensitive di `findPuuid` |
| `summoner` | `summoner_user_accounts` | account Discord per `userId`, ordinati per `_id` |
| `summoner` | `summoner_tracking_true` | tracker e account con `tracking=true` |
| `summoner` | `summoner_leaderboard_region`, `summoner_leaderboard_global` | `$match` iniziale dei rank embedded regionale/globale |
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

`profile_statistics_identity` viene creato solo dopo il preflight di identità
mancanti e duplicati. Il preflight interrompe il bootstrap e richiede cleanup
manuale, senza cancellare o fondere documenti.

## Explain richiesti

Prima dell'accettazione eseguire su un database con dati rappresentativi:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}, {riotId: 1, ranks: 1}).sort({riotId: 1}).limit(25).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid", champion: 1}}, region: "EUW1", queue: "RANKED_SOLO_5X5", patchMajor: "14.2"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.summoner.aggregate([
  {$unwind: "$ranks"},
  {$match: {region: "EUW1", "ranks.queue": "RANKED_SOLO_5X5"}},
  {$sort: {"ranks.mmr": -1, _id: 1}},
  {$limit: 50}
]).explain("executionStats")
```

Gli explain devono verificare `executionTimeMillis`, `totalKeysExamined`,
`totalDocsExamined`, `nReturned`, `winningPlan`, `indexName` e l'assenza di
`COLLSCAN`; per aggregation registrare anche eventuali stage `SORT` e
`usedDisk`. Confrontare la baseline prima/dopo con `collStats` e
`indexSizes`. La leaderboard resta parzialmente applicativa: dopo `$unwind` e
`$facet`, il sort su `ranks.mmr` non è promesso come covered dall'indice.
