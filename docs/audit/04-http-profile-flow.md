# Audit 04 — profilo HTTP, bootstrap e statistiche

## Percorso

```text
GET /api/lol/{shard}/profile/{puuid}
  -> LolController
  -> ProfilePageService.get
  -> Redis PROFILE_PAGE
  -> LeagueService.getProfileBaseFromDatabase
  -> MongoDB.findSummoner
  -> ProfileStatisticsService Redis/Mongo
  -> LeagueService.getProfileRanks / getProfileMasteries
  -> MongoDB
  -> SummonerView
```

Evidenza: [LolController.java](../../src/main/java/com/safjnest/spring/controller/LolController.java:45), [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:28) e [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:204).

## Parte coerente

Il percorso HTTP usa modelli canonici e, a differenza del vecchio comando Discord, legge profile base, rank, mastery e statistiche da Mongo con cache Redis davanti. Lo stato `PENDING` viene restituito quando il summoner non è ancora presente e il bootstrap viene accodato.

## Rilievi

### P1 — bootstrap con due proprietari della stessa scrittura

`ProfileBootstrapService` chiama `LeagueDB.addLOLAccount`, che già esegue il mirror Mongo, e subito dopo chiama direttamente `MongoDB.upsertSummoner`. Inoltre `LeagueDB.updateSummonerEntries` esegue il mirror rank e il bootstrap richiama ancora direttamente `MongoDB.upsertRanks`.

Evidenza: [ProfileBootstrapService.java](../../src/main/java/com/safjnest/lol/service/ProfileBootstrapService.java:44).

Il secondo write non è necessariamente errato, ma rende difficile capire quale risultato sia quello valido e può sovrascrivere campi preservati dal mirror precedente. Va deciso un solo owner operativo.

### P1 — refresh statistiche bypassa MariaDB

`ProfileStatisticsService.refresh` legge match da Mongo e salva il risultato con `MongoDB.upsertProfileStatistics`. Non passa da `LeagueDB.saveProfileStatistics`, quindi questo flusso non è dual-write MariaDB → Mongo come previsto dal piano.

Evidenza: [ProfileStatisticsService.java](../../src/main/java/com/safjnest/lol/service/ProfileStatisticsService.java:104).

Il comportamento può essere intenzionale per rendere Mongo il writer delle nuove statistiche, ma è una deviazione architetturale da registrare prima del cutover.

### P1 — risultato `ready` dipende da dati aggregati già validi

`ProfilePageService` considera pronto il profilo solo se esistono rank e almeno cinque game nelle statistiche aggregate. Se il flusso `findMatchResults` o `readProfileStatistics` restituisce vuoto per un mapping incompleto, la risposta diventa `PARTIAL`/`PENDING` senza indicare se il problema è assenza dati o errore di persistenza.

Evidenza: [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:45) e [ProfilePageService.java](../../src/main/java/com/safjnest/lol/service/ProfilePageService.java:96).

## Verifica runtime

Per un `puuid` non presente in Mongo:

1. chiamare l’endpoint due volte;
2. verificare il primo `202 profile_pending`;
3. osservare bootstrap, riga MariaDB e documento Mongo;
4. verificare rank e mastery nel documento summoner;
5. verificare la creazione di `profile_statistics` dopo il refresh;
6. verificare invalidazione di `PROFILE_PAGE` e risposta successiva `200`/`PARTIAL`.

Il test deve separare chiaramente `documento assente`, `documento presente ma incompleto` e `errore Mongo`.
