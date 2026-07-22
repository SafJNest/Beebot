# Audit 04 — profilo HTTP, bootstrap e statistiche

## Percorso

```text
GET /api/lol/{shard}/profile/{puuid}
  -> LolController
  -> ProfilePageService.get
  -> Redis PROFILE_PAGE
  -> MongoDB.findProfileProjection
     (summoner + ranks + masteries in one projection)
  -> ProfileStatisticsService Redis/Mongo
  -> SummonerView
```

Evidenza: [LolController.java](../../src/main/java/com/safjnest/spring/controller/LolController.java:45), [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:28) e [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:665).

## Parte coerente

Il percorso HTTP usa modelli canonici e legge base, rank e mastery con una sola projection Mongo; le statistiche passano prima dalla cache Redis e poi da Mongo. Lo stato `PENDING` viene restituito quando il summoner non è ancora presente e il bootstrap viene accodato. Se il documento esiste ma mancano le statistiche, il refresh viene avviato e il profilo viene restituito come `PARTIAL`.

## Rilievi

### Fix applicato — bootstrap con un solo owner

`ProfileBootstrapService` usa `LeagueService.upsertSummoner` e poi `MongoDB.upsertRanks`. Non esistono più due proprietari MariaDB/Mongo della stessa scrittura.

Evidenza: [ProfileBootstrapService.java](../../src/main/java/com/safjnest/lol/service/ProfileBootstrapService.java:44).

Il bootstrap ha quindi un solo owner operativo: MongoDB.

### Coerente — refresh statistiche Mongo

`ProfileStatisticsService.refresh` legge match da Mongo e salva il risultato con `MongoDB.upsertProfileStatistics`. È il comportamento previsto per il runtime Mongo-only.

Evidenza: [ProfileStatisticsService.java](../../src/main/java/com/safjnest/lol/service/ProfileStatisticsService.java:104).

MariaDB resta coinvolto solo nei percorsi espliciti di `MongoMigration`.

### P1 — cache `ready` dipende da dati aggregati già validi

La cache `PROFILE_PAGE` viene riutilizzata solo quando esistono rank e almeno cinque game nelle statistiche aggregate. Il primo caricamento, invece, restituisce `PARTIAL` quando le statistiche non sono ancora disponibili e avvia `Tracker.startProfileStatistics`; un payload corrotto viene trattato come dato assente e rigenerabile.

Evidenza: [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:45) e [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:96).

## Verifica runtime

Per un `puuid` non presente in Mongo:

1. chiamare l’endpoint due volte;
2. verificare il primo `202 profile_pending`;
3. osservare bootstrap e documento Mongo;
4. verificare rank e mastery nel documento summoner;
5. verificare la creazione di `profile_statistics` dopo il refresh;
6. verificare invalidazione di `PROFILE_PAGE` e risposta successiva `200`/`PARTIAL`.

Il test deve separare chiaramente `documento assente`, `documento presente ma incompleto` e `errore Mongo`.
