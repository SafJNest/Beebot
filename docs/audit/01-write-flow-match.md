# Audit 01 — flusso di scrittura match

## Percorso analizzato

Il percorso principale è quello usato dal tracker:

```text
Riot LOLMatch
  -> Tracker.analyzeMatchHistory(...)
  -> LeagueDB.saveMatch(...)
  -> commit MariaDB
  -> MongoDB.mirrorMatch(legacyMatchId)
  -> LeagueDB.getMatch(region, gameId)
  -> MongoDB.upsertMatch(fullGameId, Match)
  -> LeagueDB.setSummonerData(...)
  -> MongoDB.mirrorParticipant(...)
  -> LeagueDB.setMatchRank(...)
  -> MongoDB.mirrorMatchRank(...)
  -> LeagueDB.setMatchEvent(...)
  -> MongoDB.mirrorMatchEvents(...)
```

Evidenza: `Tracker` salva prima il match e poi aggiorna account e participant in [Tracker.java](../../src/main/java/com/safjnest/lol/tracker/Tracker.java:358), mentre `LeagueDB` affianca i mirror in [LeagueDB.java](../../src/main/java/com/safjnest/sql/database/LeagueDB.java:1030) e [LeagueDB.java](../../src/main/java/com/safjnest/sql/database/LeagueDB.java:396).

## Comportamento atteso

Dopo il commit MariaDB devono esistere:

- `lol_matches._id = REGION_gameId`;
- `region = REGION` e `game_id = gameId` derivati dal full Riot id;
- `legacyMatchId` valorizzato;
- `bans.BLUE` e `bans.RED` come array BSON;
- participant flat dentro `participants`, inclusi `rank`, `lp` e `gain`;
- rank ed eventi aggiornati sullo stesso documento;
- mirror idempotenti e verificabili dal risultato Mongo.

## Rilievi

### Risolto — mirror con esito esplicito

`MongoDB.mirrorMatch` e `mirrorParticipant` ora trasformano match, participant o riga MariaDB mancanti in eccezioni del mirror. Il wrapper registra operation, collection, id e messaggio, senza falsificare il risultato MariaDB.

Evidenza: [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:1248) e [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:1258).

### Risolto — participant, rank ed eventi verificano il risultato

`mirrorParticipant` ora verifica il booleano di `upsertParticipant`; `mirrorMatchRank` e `mirrorMatchEvents` verificano allo stesso modo i relativi update. Un match mancante produce un log di mirror fallito.

Evidenza: [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:801) e [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:821).

### Risolto — `replaceOne` acknowledged

La funzione comune `replace` ora verifica `UpdateResult.wasAcknowledged()` e solleva un errore esplicito quando il server non conferma la write.

Evidenza: [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java:1557).

### Risolto — commit MariaDB e `QueryResult.success`

`AbstractDB.query` ora imposta `result.success = true` solo dopo il commit riuscito. `setSummonerData` mantiene inoltre l’inserimento idempotente e aggiorna `rank`, `lp` e `gain` quando la riga participant esiste già.

Evidenza: [AbstractDB.java](../../src/main/java/com/safjnest/sql/AbstractDB.java:29) e [LeagueDB.java](../../src/main/java/com/safjnest/sql/database/LeagueDB.java:1025).

## Verifica runtime necessaria

Per un match noto bisogna acquisire questa sequenza:

```sql
SELECT id, game_id, region, bans, rank, events
FROM match
WHERE game_id = '<GAME_ID>' AND region = '<REGION>';

SELECT match_id, summoner_id, puuid, champion, win, rank, lp, gain
FROM participant
WHERE match_id = <LEGACY_MATCH_ID>;
```

E poi verificare in Mongo:

```javascript
db.lol_matches.findOne({ _id: "<REGION>_<GAME_ID>" })
```

Il confronto deve controllare anche il numero dei participant e non solo l’esistenza del documento.

## Decisione

Restano da verificare runtime `saveMatch == 0` nel Tracker, la presenza di tutti i participant dopo il worker e la riconciliazione su un match reale.
