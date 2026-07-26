# Inventario query LoL

La controparte runtime vive in `MongoDB.java`; i percorsi caldi usano projection tipizzate o `QueryRecord` detached.

| Area | Query Mongo target | Budget applicativo | Consumer |
|---|---|---:|---|
| search/autocomplete | una `find` su `summoner` con prefix `region + riotSearch`, projection base + `ranks`, rank Solo incluso | 1 | LeagueService |
| profile | una `find` su `summoner` con projection `Summoner + ranks + masteries`; statistiche Redis prima, Mongo dopo | 2 | ProfilePageService |
| leaderboard | `$match` preliminare con `$elemMatch` + `$unwind` + `$match` esatto + `$facet` per `total` e pagina; projection summoner già filtrata | 2 | LeaderboardService |
| profile statistics batch | `{puuid: {$in: [...]}, filterKey}`, flat root projection, uniqueness enforced by the application flow | 1 | ProfileStatisticsService |
| history | participant filter in un unico `$elemMatch`, projection/paging limitati; `countDocuments` diretto | 1 + eventi batch | LeagueMessage |
| match results | projection dei soli campi necessari ai `MatchResult` e partecipanti | 1 | profile/tracker |
| match events | `_id: {$in: [...]}` su `match_events` | 1 | match detail/history |
| champion | match id con projection; build e statistiche leggono solo participant richiesti; batch raw senza `Match -> Participant` completo | 2 per batch (+ count/trend) | Champion services |
| leaderboard aggregates | snapshot Mongo `leaderboard_aggregates` per filtro; rebuild ogni 12 ore e `$match` preliminare + `$unwind` + `$match` esatto + `$group` su `summoner.ranks[]` per nuovo filtro | 1 | LeaderboardService |
| writes | update atomici, pipeline participant, bulk unordered per build/statistiche/summoner | 1 per update/batch | MongoDB/tracker |

## Projection e filtri

La search restituisce direttamente il payload che serve a search e autocomplete: `Summoner` e rank `RANKED_SOLO_5X5` sono letti nella stessa projection. Non esiste più il ciclo `findRank` per PUUID.

Profilo e leaderboard usano campi BSON strutturati. I filtri champion e lane vengono applicati allo stesso elemento di `participants` tramite un unico `$elemMatch`; non possono più soddisfare champion e lane su due partecipanti diversi.

Le query paginated sono limitate a 100 match, 50 summoner leaderboard, 25 risultati search, 500.000 chiavi summoner per pagina e 50.000 chiavi match per pagina. I dati completi dei summoner vengono letti e scritti in sotto-batch da 20.000; i match e gli eventi restano in sotto-batch da 1.000. I cursori dei batch lunghi devono essere chiusi esplicitamente.

## Invarianti

PUUID è l'identità summoner e `_id` del documento; il Riot match ID completo è l'identità match; enum R4J usa `name()`; bans usa BLUE e RED; participant resta flat; upsert/update/delete sono idempotenti; letture e scritture applicative Mongo-only; errori di lettura Mongo espliciti.

MariaDB conserva JSON UTF-8 in `champion_builds.data`, `champion_stats.data` e `profile_statistics.data`. Mongo conserva `build` come BSON strutturato; `profile_statistics` salva direttamente i campi `timeStart`, `timeEnd`, `lastUpdate`, `total`, `queueStats`, `laneStats`, `championStats`, `matchups`, `duoStats`, `pings` e gli aggregati collegati, mai sotto un campo `statistics`. Non vengono letti o convertiti payload Kryo e non viene creato alcun `legacyPayload`; i documenti legacy vengono rigenerati con il nuovo `puuid + filterKey`.

Il dettaglio del formato di `filterKey`, del motivo dell'indice composto e della differenza tra aggregato e `recentMatches` è in [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

## Explain richiesti

Prima dell'accettazione eseguire su un database con dati rappresentativi:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}, {riotId: 1, ranks: 1}).sort({riotId: 1}).limit(25).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid", champion: 1}}, leagueShard: "EUW1", queue: "RANKED_SOLO_5X5"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.summoner.aggregate([
  {$unwind: "$ranks"},
  {$match: {region: "EUW1", "ranks.queue": "RANKED_SOLO_5X5"}},
  {$sort: {"ranks.mmr": -1, _id: 1}},
  {$limit: 50}
]).explain("executionStats")
```

Le scansioni senza indice sono intenzionali. Conservare `executionTimeMillis`, `totalKeysExamined`, `totalDocsExamined` e la baseline di `collStats`.
