# Audit 01 — flusso di scrittura match

## Percorso runtime attuale

Il tracker usa MongoDB come unica persistenza LoL:

```text
Riot LOLMatch
  -> MatchService.persistRaw(...)
  -> MongoDB.createRawMatch(full Riot match id, Match, tracked=false)
  -> participant summoner seed
  -> MongoDB.upsertMatchEvents(...)
```

Il percorso non passa da `LeagueDB`, non usa un id numerico MariaDB e non esegue mirror. `LeagueDB` resta disponibile esclusivamente per `MongoMigration`.

## Contratto Mongo

Per ogni match devono esistere:

- `match._id = REGION_gameId`;
- `region` e `game_id` derivati dal full Riot match id;
- `bans.BLUE` e `bans.RED` come array BSON;
- participant flat dentro `participants`, incluso soltanto `rankProgress` quando disponibile;
- snapshot OP.GG aggiornato solo su `tracked:false`; gain/storico solo dal tracker;
- eventi nella collection `match_events`, referenziati dal full match id.

## Implementazione

`MatchService` possiede la persistenza raw create-only. `Tracker` completa solo
il RankProgress autorevole e committa `tracked:true`; nessun flusso raw può
declassare un match già completo.

Il controllo di esistenza per i match già processati usa `MongoDB.hasMatch`. Le vecchie chiamate `saveMatch`, `setSummonerData`, `setMatchRank`, `setMatchEvent` e `updateSummonerEntries` di `LeagueDB` non fanno più parte del runtime.

## Verifica

La verifica statica deve trovare query MariaDB soltanto in `MongoMigration` e nell’adapter `LeagueDB` usato dalla migration. Il test `LeagueDbRuntimeGuardTest` impedisce nuove importazioni runtime di `LeagueDB`.

Per una verifica runtime con un match noto:

```javascript
db.match.findOne({ _id: "<REGION>_<GAME_ID>" })
db.match_events.find({ _id: "<REGION>_<GAME_ID>" })
```

Il confronto deve controllare anche il numero dei participant e la presenza degli eventi separati.
