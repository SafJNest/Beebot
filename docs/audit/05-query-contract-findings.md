# Audit 05 — matrice contratti query e backlog

## Audit query Mongo — implementato staticamente 2026-07-20

| Percorso | Prima | Flusso attuale | Budget |
|---|---|---|---:|
| search/autocomplete | search + N query rank | projection summoner con rank Solo incorporato, condivisa dai due consumer | 1 |
| profile | base + ranks + masteries separati | projection `ProfileProjection`; statistiche Redis/Mongo separate per disponibilità | 2 |
| leaderboard | count + pagina + `findSummoner` per riga | `$facet` total/pagina + batch summoner/masteries + batch statistics | 3 |
| profile statistics | loop di find singole | `_id $in` con projection `statistics` | 1 |
| history/count | hydration completa e filtro Java | `$elemMatch` sullo stesso participant, paging Mongo e `countDocuments` | 1 |
| champion raw | `Document -> Match -> Participant` | projection raw tipizzata per metadata e participant | 1 per batch |
| distributions | scansione e conteggio Java | `$group` su Mongo, bulk unordered per rebuild | 1 |

Il risultato HTTP resta canonico: `SummonerView` e `SummonerLeaderboard` non cambiano come modelli o route; la leaderboard ora valorizza anche `overview.masteries` nello stesso modo del profilo.

## Matrice aggiornata dopo i fix

| MariaDB / consumer | Mongo attuale | Contratto atteso | Esito |
|---|---|---|---|
| `LeagueDB.getAdvancedLOLData` → `LeagueMessage.getSummonerEmbed` | `findAdvancedProfileProjections` | aggregato per champion con `lanes_played` | **fix applicato; runtime da verificare** |
| `LeagueDB.getSummonerData(puuid, shard)` → OP.GG | `findSummonerData` | participant rows con `game_id`, `rank`, `lp`, `gain`, `win` | **fix applicato; runtime da verificare** |
| `LeagueDB.getAllGamesForAccount` → profile | `getAllGamesForAccount` | `game_id`, `queue`, `win` | coerente, da verificare runtime |
| `LeagueDB.getMatchHistory/count` | `getMatchHistory/countMatchHistory` | participant, champion e lane nello stesso `$elemMatch`; match completi solo dopo paging; count diretto Mongo senza lookup summoner preliminare | **fix applicato; runtime da verificare** |
| `LeagueDB.getMatch` usato dal mirror | `MongoDB.mirrorMatch` + `upsertMatch` | `_id` full Riot, `region`, `game_id`, match completo | ack e dati mancanti ora producono errore di mirror loggato |
| `LeagueDB.setSummonerData` | `mirrorParticipant` + `upsertParticipant` | participant flat aggiornato nel match con `rank`, `lp`, `gain` | insert idempotente + update rank/lp/gain; ack e dati mancanti loggati |
| `LeagueDB.getProfileStatistics` | `findProfileStatistics` | `ProfileStatistics` JSON/BSON strutturato e batch `_id $in` | mapping presente, write owner invariato |
| `Tracker.analyzeChampionData` | `findMatchBans` | stringa JSON valida | **fix applicato con `Document.toJson()`** |
| `LeagueDB.saveChampionBuild/Stats` | `upsertChampionBuild/Statistics` | JSON MariaDB, BSON Mongo, bulk unordered per batch | ack del replace/bulk verificato |

## Backlog prioritizzato

### P0 — verifica runtime ancora aperta

1. eseguire gli `explain("executionStats")` con dati rappresentativi e verificare assenza di `COLLSCAN`;
2. aggiungere un test di contratto che verifichi le chiavi consumate da `LeagueMessage`;
3. eseguire il caso reale con un match noto e verificare i participant dentro il documento Mongo.

### P1 — rendere affidabile il dual-write

1. verificare con il runtime il writer effettivo di `ProfileStatisticsService.refresh`;
2. correggere `Tracker` su queue, participant Riot null e queue Redis incoerente;
3. distinguere conversione bans/eventi fallita da payload vuoto.

### P2 — osservabilità e cache

1. invalidare le chiavi Redis `ADVANCED_LOL_DATA` e `SUMMONER_DATA` durante i test di riconciliazione;
2. aggiungere correlation id comune tra comando, task Tracker, MariaDB match id e `_id` Mongo;
3. registrare tempi e conteggi per ogni fase: SQL commit, mirror summoner, mirror match, participant count, rank/event update.

## Conclusione

I fix coprono il contratto del documento match, l'upsert participant atomico, la rimozione Kryo, le projection calde, il batch profile/leaderboard e le aggregazioni principali. Restano necessarie solo la verifica runtime con Mongo reale, gli `explain` e la riconciliazione delle cache.
