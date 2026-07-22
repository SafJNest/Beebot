# Audit 03 — comando `opgg`

## Percorso

```text
/opgg
  -> Opgg.execute
  -> LeagueHandler.getSummonerByArgs
  -> LeagueService.upsertSummoner
  -> LeagueMessage.build(OPGG)
  -> loadMatchesParallel
  -> LeagueService.getMatchList / Riot API
  -> LeagueService.getSummonerData
  -> MongoDB.findSummonerData
  -> getOpggEmbedMatch
```

Evidenza: [Opgg.java](../../src/main/java/com/safjnest/commands/lol/Opgg.java:58), [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:105) e [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:1295).

## Cosa legge davvero OP.GG

La lista delle partite viene ancora da Riot tramite `LeagueService.getMatchList` e `LeagueService.getMatch`. Il comando non usa `MongoDB.findMatchHistory` per costruire l’elenco visualizzato.

Per il blocco LP/rank chiama `LeagueService.getSummonerData`, che storicamente deve restituire righe participant con:

- `summoner_id`;
- `game_id`;
- `rank` come `TierDivisionType`;
- `lp`;
- `gain`;
- `win`;
- `time_start`, `time_end`, `patch`.

Il contratto è Mongo: [MongoDB.java](../../src/main/java/com/safjnest/mongo/MongoDB.java).

## Rilievo

### Fix applicato — il blocco LP riceve participant rows

`LeagueService.getSummonerData` usa ora `findSummonerData`, separata dal profile aggregate. La proiezione produce `game_id`, `rank`, `lp`, `gain`, `win`, `time_start`, `time_end` e `patch`, in ordine cronologico.

Il consumer confronta `row.getAsLong("game_id")` con l’id del match e legge `rank`, `lp` e `gain`.

Evidenza: [LeagueService.java](../../src/main/java/com/safjnest/lol/service/LeagueService.java:472) e [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:798).

Il confronto ora può trovare il match tramite `game_id`; resta da verificare il valore visualizzato con una sequenza rank reale e cache `SUMMONER_DATA` pulita.

## Persistenza asincrona durante il comando

Per ogni match visualizzato `getOpggEmbed`:

1. chiama `Tracker.queueMatch`, che salva l’id nella coda Redis;
2. pianifica `LeagueHandler.updateSummonerDB(match)`, che inserisce/aggiorna solo gli account summoner;
3. lascia al worker Tracker il successivo upsert Mongo del match, enrichment participant, rank ed eventi.

Evidenza: [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:1310) e [Tracker.java](../../src/main/java/com/safjnest/lol/tracker/Tracker.java:160).

Quindi è normale che subito dopo `/opgg` il documento match Mongo non esista ancora. Il problema è che non c’è un indicatore nel comando che distingua “dati Riot visualizzati, persistenza pending” da “match già persistito”.

## Verifica runtime

Durante un `/opgg` bisogna correlare:

- id Riot visualizzato;
- elemento Redis `TRACKER_PENDING_MATCH_LIST`;
- documento Mongo `match` dopo il worker;
- documento Mongo `match`;
- `List<QueryRecord>` di `getSummonerData` e sue chiavi.

Il test deve essere eseguito sia subito dopo il comando sia dopo il completamento del worker.
