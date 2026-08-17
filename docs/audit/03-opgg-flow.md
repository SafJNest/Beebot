# Audit 03 — comando `opgg`

## Percorso

```text
/opgg
  -> Opgg.execute
  -> LeagueHandler.getSummonerByArgs
  -> LeagueMessage.build(OPGG)
  -> MatchService.getRecentIds / Riot API
  -> MatchService.get / Riot match API
  -> Tracker.queueMatch
  -> Mongo match write
  -> Mongo summoner seed from each participant
  -> low-priority forced rank refresh
  -> MatchService.getSummonerData / MongoDB.findSummonerData
  -> getOpggEmbedMatch
```

Evidenza: [Opgg.java](../../src/main/java/com/safjnest/commands/lol/Opgg.java:58), [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:105) e [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java:1295).

## Cosa legge davvero OP.GG

La lista delle partite viene da `MatchService.getRecentIds` a blocchi di 100,
cacheati in Redis per un’ora con una chiave costruita dagli stessi parametri del
builder R4J (`queue`, `batch index`, `count`, `startTime`, `type`). Le pagine da
cinque posizioni 1-20 riusano il blocco `start=0`; la pagina 21 apre il blocco
`start=100`, e così via. I dettagli restano read-through Redis/Mongo/Riot; un
dettaglio Riot viene subito passato al Tracker per la persistenza.

Per il blocco LP/rank chiama `MatchService.getSummonerData`, che restituisce
righe participant Mongo con:

- `summoner_id`;
- `game_id`;
- `rank` come `TierDivisionType`;
- `lp`;
- `gain`;
- `win`;
- `time_start`, `time_end`, `patch`.

Il contratto è Mongo: [MongoDB.java](../../src/main/java/com/safjnest/nosql/MongoDB.java).

## Rilievo

### Fix applicato — il blocco LP riceve participant rows

`MatchService.getSummonerData` usa `findSummonerData`, separata dal profile aggregate. La proiezione produce `game_id`, `rank`, `lp`, `gain`, `win`, `time_start`, `time_end` e `patch`, in ordine cronologico.

Il consumer confronta `row.getAsLong("game_id")` con l’id del match e legge `rank`, `lp` e `gain`.

Evidenza: [MatchService.java](../../src/main/java/com/safjnest/lol/service/MatchService.java) e [LeagueMessage.java](../../src/main/java/com/safjnest/lol/message/LeagueMessage.java).

Il confronto ora può trovare il match tramite `game_id`; resta da verificare il valore visualizzato con una sequenza rank reale e cache `SUMMONER_DATA` pulita.

## Persistenza e refresh durante il comando

Per ogni match Riot visualizzato il Tracker:

1. salva il match Mongo;
2. fa l’upsert di ogni summoner direttamente dal `MatchParticipant` Riot con
   PUUID, `riotId#riotTag`, shard, icona e livello;
3. accoda soltanto rank e mastery, in quest’ordine, nella priorità bassa R4J.

Questa fase non esegue chiamate Account API o Summoner API per i participant.
L’Account API può ancora servire alla risoluzione iniziale di un Riot ID che non
è noto localmente.

Il match e il seed summoner sono disponibili prima dell’enrichment basso; rank e
mastery possono quindi essere temporaneamente quelli già persistiti.

## Verifica runtime

Durante un `/opgg` bisogna correlare:

- id Riot visualizzato;
- elemento Redis `TRACKER_PENDING_MATCH_LIST`;
- documento Mongo `match` dopo il worker;
- documento Mongo `match`;
- `List<QueryRecord>` di `getSummonerData` e sue chiavi.

Il test deve essere eseguito sia subito dopo il comando sia dopo il completamento del worker.
