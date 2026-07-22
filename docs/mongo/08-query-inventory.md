# Inventario query LoL

La controparte runtime vive in `MongoDB.java`; i percorsi caldi usano projection tipizzate o `QueryRecord` detached.

| Area | Query Mongo target | Budget applicativo | Consumer |
|---|---|---:|---|
| search/autocomplete | una `find` su `summoner` con prefix `region + riotSearch`, projection base + `ranks`, rank Solo incluso | 1 | LeagueService |
| profile | una `find` su `summoner` con projection `Summoner + ranks + masteries`; statistiche Redis prima, Mongo dopo | 2 | ProfilePageService |
| leaderboard | `$facet` per `total` e pagina, batch summoner/masteries, statistics già batch | 3 | LeaderboardService |
| profile statistics batch | `_id: {$in: [puuid:seasonStart...]}`, projection `statistics` | 1 | ProfileStatisticsService |
| history | participant filter in un unico `$elemMatch`, projection/paging limitati; `countDocuments` diretto | 1 + eventi batch | LeagueMessage |
| match results | projection dei soli campi necessari ai `MatchResult` e partecipanti | 1 | profile/tracker |
| match events | `_id: {$in: [...]}` su `match_events` | 1 | match detail/history |
| champion | match id con projection; build e statistiche leggono solo participant richiesti; batch raw senza `Match -> Participant` completo | 2 per batch (+ count/trend) | Champion services |
| leaderboard aggregates | `$group` per distribuzioni, regioni e rebuild | 1 | LeaderboardService |
| writes | update atomici, pipeline participant, bulk unordered per build/statistiche/summoner | 1 per update/batch | MongoDB/tracker |

## Projection e filtri

La search restituisce direttamente il payload che serve a search e autocomplete: `Summoner` e rank `RANKED_SOLO_5X5` sono letti nella stessa projection. Non esiste più il ciclo `findRank` per PUUID.

Profilo e leaderboard usano campi BSON strutturati. I filtri champion e lane vengono applicati allo stesso elemento di `participants` tramite un unico `$elemMatch`; non possono più soddisfare champion e lane su due partecipanti diversi.

Le query paginated sono limitate a 100 match, 50 righe leaderboard, 25 risultati search e 2.000 ID per batch. I cursori dei batch lunghi devono essere chiusi esplicitamente.

## Invarianti

PUUID è l'identità summoner e `_id` del documento; il Riot match ID completo è l'identità match; enum R4J usa `name()`; bans usa BLUE e RED; participant resta flat; upsert/update/delete sono idempotenti; letture e scritture applicative Mongo-only; errori di lettura Mongo espliciti.

MariaDB conserva JSON UTF-8 in `champion_builds.data`, `champion_stats.data` e `profile_statistics.data`. Mongo conserva `build` e `statistics` come BSON strutturato, mai come stringa opaca. Non vengono letti o convertiti payload Kryo e non viene creato alcun `legacyPayload`; dati vecchi o corrotti sono invalidi e vanno rimossi manualmente dall'operatore prima della rigenerazione.

## Explain richiesti

Prima dell'accettazione eseguire su un database con dati rappresentativi:

```javascript
db.summoner.find({region: "EUW1", riotSearch: /^name/}, {riotId: 1, ranks: 1}).sort({riotId: 1}).limit(25).explain("executionStats")
db.match.find({participants: {$elemMatch: {puuid: "puuid", champion: 1}}, leagueShard: "EUW1", queue: "RANKED_SOLO_5X5"}).sort({timeStart: -1}).limit(100).explain("executionStats")
db.leaderboard_entries.find({queue: "RANKED_SOLO_5X5", region: "EUW1"}).sort({mmr: -1, puuid: 1}).limit(50).explain("executionStats")
```

L'accettazione richiede `IXSCAN` e assenza di `COLLSCAN` sui percorsi principali. Conservare `executionTimeMillis`, `totalKeysExamined`, `totalDocsExamined` e l'indice scelto insieme alla baseline di `collStats`.
