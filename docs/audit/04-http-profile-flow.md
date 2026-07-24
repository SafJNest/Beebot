# Audit 04 — profilo HTTP, bootstrap e statistiche

## Percorso

```text
GET /api/lol/{shard}/profile/{puuid}
  -> LolController
  -> ProfilePageService.get
  -> Redis PROFILE_PAGE
  -> LeagueService.getAsyncSummoner()
     Redis -> Mongo -> Future Riot -> save Redis + Mongo
  -> LeagueService.getAsyncRanks()
     Redis -> Mongo -> Future Riot -> save Redis + Mongo
  -> LeagueService.getAsyncMasteries()
     Redis -> Mongo -> Future Riot -> save Redis + Mongo
  -> ProfileStatisticsService Redis/Mongo
  -> SummonerView
```

Evidenza: [LolController.java](../../src/main/java/com/safjnest/spring/controller/LolController.java:45), [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:28) e [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:90).

## Parte coerente

Il percorso HTTP usa modelli canonici e avvia i tre flussi async di `LeagueService` senza attendere Riot. Ogni flusso prova Redis, poi Mongo, quindi accoda o riusa una Future deduplicata per `shard:puuid`. Le statistiche passano prima da Redis e poi da Mongo. Lo stato `PENDING` viene restituito finché summoner, rank e mastery non sono pronti; se i tre componenti sono disponibili ma mancano le statistiche, il refresh viene avviato e il profilo viene restituito come `PARTIAL`.

## Rilievi

### Fix applicato — una fonte di verità per componente

`LeagueService` possiede i getter salvati, le fetch Riot, le Future condivise e le scritture dei componenti. Non esiste una logica parallela di bootstrap del profilo. I wrapper sync del bot attendono le stesse Future usate dagli endpoint async.

Evidenza: [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:251), [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:362) e [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:742).

`[]` viene persistito solo quando Riot restituisce esplicitamente una lista vuota; `null` o un errore Riot non diventano dati vuoti persistiti.

### Coerente — refresh statistiche Mongo

`ProfileStatisticsService.refresh` legge match proiettati da Mongo usando il `Filter` completo e salva il risultato flat con `MongoDB.upsertProfileStatistics` tramite `puuid + filterKey`. È il comportamento previsto per il runtime Mongo-only.

Evidenza: [ProfileStatisticsService.java](../../src/main/java/com/safjnest/lol/service/ProfileStatisticsService.java:104).

MariaDB resta coinvolto solo nei percorsi espliciti di `MongoMigration`.

### P1 — cache `ready` dipende da dati aggregati già validi

La cache `PROFILE_PAGE` viene riutilizzata solo quando esistono rank e almeno cinque game nelle statistiche aggregate. Il primo caricamento, invece, restituisce `PARTIAL` quando le statistiche non sono ancora disponibili e avvia `Tracker.startProfileStatistics`; un payload corrotto viene trattato come dato assente e rigenerabile.

Evidenza: [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:45) e [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:96).

## Verifica runtime

Per un `puuid` non presente in Redis/Mongo:

1. chiamare l'endpoint due volte;
2. verificare il primo `202 profile_pending` e le tre Future accodate;
3. osservare una sola fetch Riot per summoner, rank e mastery;
4. verificare rank e mastery in Redis e nel documento Mongo;
5. verificare la creazione di `profile_statistics` dopo il refresh;
6. verificare invalidazione di `PROFILE_PAGE` e risposta successiva `200`/`PARTIAL`.

Il test deve separare chiaramente cache Redis, fallback Mongo, miss con fetch async, wrapper sync, risultato Riot `[]` ed errore Riot.
