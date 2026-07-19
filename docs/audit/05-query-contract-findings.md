# Audit 05 — matrice contratti query e backlog

## Matrice aggiornata dopo i fix

| MariaDB / consumer | Mongo attuale | Contratto atteso | Esito |
|---|---|---|---|
| `LeagueDB.getAdvancedLOLData` → `LeagueMessage.getSummonerEmbed` | `findAdvancedProfileProjections` | aggregato per champion con `lanes_played` | **fix applicato; runtime da verificare** |
| `LeagueDB.getSummonerData(puuid, shard)` → OP.GG | `findSummonerData` | participant rows con `game_id`, `rank`, `lp`, `gain`, `win` | **fix applicato; runtime da verificare** |
| `LeagueDB.getAllGamesForAccount` → profile | `getAllGamesForAccount` | `game_id`, `queue`, `win` | coerente, da verificare runtime |
| `LeagueDB.getMatchHistory/count` | `getMatchHistory/countMatchHistory` | match completi filtrati dal participant del summoner | coerente staticamente |
| `LeagueDB.getMatch` usato dal mirror | `MongoDB.mirrorMatch` + `upsertMatch` | `_id` full Riot, `region`, `game_id`, match completo | ack e dati mancanti ora producono errore di mirror loggato |
| `LeagueDB.setSummonerData` | `mirrorParticipant` + `upsertParticipant` | participant flat aggiornato nel match con `rank`, `lp`, `gain` | insert idempotente + update rank/lp/gain; ack e dati mancanti loggati |
| `LeagueDB.getProfileStatistics` | `findProfileStatistics` | DTO `ProfileStatistics` esistente | mapping presente, write owner ambiguo |
| `Tracker.analyzeChampionData` | `findMatchBans` | stringa JSON valida | **fix applicato con `Document.toJson()`** |
| `LeagueDB.saveChampionBuild/Stats` | `upsertChampionBuild/Statistics` | upsert aggregate idempotente | ack del replace ora verificato |

## Backlog prioritizzato

### P0 — Tracker e contratti di scrittura ancora aperti

1. fermare `Tracker.analyzeMatchHistory` quando `LeagueDB.saveMatch` restituisce `0`;
2. aggiungere un test di contratto che verifichi le chiavi consumate da `LeagueMessage`;
3. eseguire il caso reale con un match noto e verificare i participant dentro il documento Mongo.

### P1 — rendere affidabile il dual-write

1. decidere se `ProfileStatisticsService.refresh` deve scrivere MariaDB e specchiare Mongo oppure essere formalmente Mongo-primary;
2. correggere `Tracker` su queue, participant Riot null e queue Redis incoerente;
3. distinguere conversione bans/eventi fallita da payload vuoto.

### P2 — osservabilità e cache

1. invalidare le chiavi Redis `ADVANCED_LOL_DATA` e `SUMMONER_DATA` durante i test di riconciliazione;
2. aggiungere correlation id comune tra comando, task Tracker, MariaDB match id e `_id` Mongo;
3. registrare tempi e conteggi per ogni fase: SQL commit, mirror summoner, mirror match, participant count, rank/event update.

## Conclusione

I fix ora coprono il contratto del documento match, l’upsert participant e le due letture che alimentano profile e OP.GG. Gli insert possono ancora risultare incompleti quando il Tracker parte da un commit MariaDB fallito o quando una cache conserva il vecchio risultato; il prossimo fix deve quindi chiudere il controllo `saveMatch == 0` e la riconciliazione runtime.
