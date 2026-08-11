# Audit 04 — profilo HTTP, bootstrap e statistiche

## Percorso

```text
GET /api/lol/{shard}/profile/{puuid}
  -> LolController
  -> ProfileService.get
  -> Redis PROFILE_PAGE
  -> SummonerService.getAsync()
     Redis -> Mongo -> Future Riot -> save Redis + Mongo
  -> RankService.getAsync()
     Redis -> Mongo -> Future Riot -> save Redis + Mongo
  -> MasteryService.getAsync()
     Redis -> Mongo -> Future Riot -> save Redis + Mongo
  -> ProfileService statistics Redis/Mongo
  -> Mongo recent MatchResult projection (max 5, senza events)
  -> SummonerView
```

Evidenza: [LolController.java](../../src/main/java/com/safjnest/spring/controller/LolController.java), [ProfileService.java](../../src/main/java/com/safjnest/lol/service/ProfileService.java), [ProfileAnalyzer.java](../../src/main/java/com/safjnest/lol/service/ProfileAnalyzer.java), [SummonerService.java](../../src/main/java/com/safjnest/lol/service/SummonerService.java), [RankService.java](../../src/main/java/com/safjnest/lol/service/RankService.java) e [MasteryService.java](../../src/main/java/com/safjnest/lol/service/MasteryService.java).

## Parte coerente

Il percorso HTTP usa modelli canonici e avvia i tre flussi async di `SummonerService`, `RankService` e `MasteryService` senza attendere Riot. Ogni flusso prova Redis, poi Mongo, quindi accoda o riusa una Future deduplicata in `R4JQueue`; la coda esegue una sola richiesta Riot alla volta per shard. Le statistiche passano prima da Redis e poi da Mongo. Lo stato `PENDING` viene restituito finché summoner, rank e mastery non sono pronti; se i tre componenti sono disponibili ma mancano le statistiche, il refresh viene avviato e il profilo viene restituito subito come `PARTIAL` con `recentMatches` vuoti. I cinque `MatchResult` vengono letti con una projection senza events solo dopo che l'aggregato è disponibile.

## Rilievi

### Fix applicato — una fonte di verità per componente

I service di dominio possiedono getter salvati, fetch Riot e scritture dei rispettivi componenti. `R4JQueue` possiede le Future condivise per shard. Non esiste una logica parallela di bootstrap del profilo. I wrapper sync del bot attendono le stesse Future usate dagli endpoint async.

Evidenza: [SummonerService.java](../../src/main/java/com/safjnest/lol/service/SummonerService.java), [RankService.java](../../src/main/java/com/safjnest/lol/service/RankService.java), [MasteryService.java](../../src/main/java/com/safjnest/lol/service/MasteryService.java) e [R4JQueue.java](../../src/main/java/com/safjnest/lol/service/R4JQueue.java).

`[]` viene persistito solo quando Riot restituisce esplicitamente una lista vuota; `null` o un errore Riot non diventano dati vuoti persistiti.

### Coerente — refresh statistiche Mongo

`ProfileService.refreshStatistics` legge match proiettati da Mongo usando il `Filter` completo, delega il calcolo a `ProfileAnalyzer` e salva il risultato flat con `MongoDB.upsertProfileStatistics` tramite `puuid + filterKey`. `DatabaseTracker` accoda il refresh sulla FIFO del worker DB generale, separata dalla FIFO champion. È il comportamento previsto per il runtime Mongo-only.

Evidenza: [ProfileService.java](../../src/main/java/com/safjnest/lol/service/ProfileService.java).

MariaDB resta coinvolto solo nei percorsi espliciti di `MongoMigration`.

### P1 — cache `ready` dipende da dati aggregati già validi

La cache `PROFILE_PAGE` viene riutilizzata solo quando esistono rank e almeno cinque game nelle statistiche aggregate. La pagina salvata non contiene i `recentMatches`: quando l'aggregato è pronto, a ogni request vengono interrogati separatamente gli ultimi cinque match leggeri. Il primo caricamento restituisce subito `PARTIAL` con `recentMatches` vuoti quando le statistiche non sono ancora disponibili e accoda `DatabaseTracker.startProfileStatistics`; un payload corrotto viene trattato come dato assente e rigenerabile.

Evidenza: [ProfileService.java](../../src/main/java/com/safjnest/lol/service/ProfileService.java).

## Verifica runtime

Per un `puuid` non presente in Redis/Mongo:

1. chiamare l'endpoint due volte;
2. verificare il primo `202 profile_pending` solo se una delle tre componenti base non è pronta;
3. osservare una sola fetch Riot per summoner, rank e mastery;
4. verificare rank e mastery in Redis e nel documento Mongo;
5. verificare la creazione di `profile_statistics` dopo il refresh;
6. verificare invalidazione di `PROFILE_PAGE`, assenza della query recent match nel primo `PARTIAL`, query separata dei recent match dopo il refresh e risposta successiva `200`/`PARTIAL`.

Il test deve separare chiaramente cache Redis, fallback Mongo, miss con fetch async, wrapper sync, risultato Riot `[]` ed errore Riot.
