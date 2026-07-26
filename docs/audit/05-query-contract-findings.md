# Audit 05 — matrice contratti query e backlog

## Audit query Mongo — implementato staticamente 2026-07-20

| Percorso | Prima | Flusso attuale | Budget |
|---|---|---|---:|
| search/autocomplete | search + N query rank | projection summoner con rank Solo incorporato, condivisa dai due consumer | 1 |
| profile | base + ranks + masteries separati | projection `ProfileProjection`; statistiche Redis/Mongo separate per disponibilità | 2 |
| leaderboard | count + pagina + `findSummoner` per riga | `$match`/`$elemMatch` preliminare + `$unwind`/`$match` esatto su `summoner.ranks[]` + `$facet` total/pagina + batch statistics | 2 |
| profile statistics | loop di find singole | `{puuid, filterKey}` con documento flat e batch per filtro | 1 |
| history/count | hydration completa e filtro Java | `$elemMatch` sullo stesso participant, paging Mongo e `countDocuments` | 1 |
| champion raw | `Document -> Match -> Participant` | projection raw tipizzata per metadata e participant | 1 per batch |
| distributions | scansione e conteggio Java | snapshot `leaderboard_aggregates` per filtro, rebuild periodico e `$group` Mongo su nuovo filtro | 1 |

Il risultato HTTP resta canonico: `SummonerView` e `SummonerLeaderboard` non cambiano come modelli o route; la leaderboard valorizza `overview.masteries` dalla stessa projection summoner e non usa un modello intermedio o una collection di righe duplicate. Gli endpoint di distribuzione e top-region possono leggere snapshot derivati da `leaderboard_aggregates`, ricostruiti ogni 12 ore.

## Matrice aggiornata dopo i fix

| MariaDB / consumer | Mongo attuale | Contratto atteso | Esito |
|---|---|---|---|
| `ProfileStatisticsService` → overview/profile/`!summoner` | `findProfileStatistics` + refresh proiettato | aggregato flat per PUUID e `Filter`, con champion/lane/queue/matchup/ping | **implementato; runtime da verificare** |
| `LeagueDB.getSummonerData(puuid, shard)` → OP.GG | `findSummonerData` | participant rows con `game_id`, `rank`, `lp`, `gain`, `win` | **fix applicato; runtime da verificare** |
| `ProfileStatisticsService` → recent matches | `findProfileRecentMatches` | `MatchResult` leggero separato dallo stesso filtro | coerente, da verificare runtime |
| `LeagueDB.getMatchHistory/count` | `getMatches/countMatches` | `Filter` unico per participant, champion, lane, patch, rank e relazioni; match completi dopo paging; count diretto Mongo senza lookup summoner preliminare | **fix applicato; runtime da verificare** |
| `LeagueDB.getMatch` usato dalla migration | `MongoMigration` + `MongoDB.upsertMatchDocument` | `_id` full Riot, `region`, `game_id`, match completo | MariaDB letto solo dalla migration; runtime direct-write Mongo |
| `LeagueDB.setSummonerData` storico | `MongoDB.upsertParticipant` | participant flat aggiornato nel match con `rank`, `lp`, `gain` | il runtime usa conversione Riot e upsert idempotente direct-write Mongo |
| `LeagueDB.getProfileStatistics` | `findProfileStatistics` | `ProfileStatistics` BSON flat e batch `{puuid, filterKey}` | mapping presente, write owner `ProfileStatisticsService` |
| `Tracker.analyzeChampionData` | `findMatchBans` | stringa JSON valida | **fix applicato con `Document.toJson()`** |
| `LeagueDB.saveChampionBuild/Stats` storico | `upsertChampionBuild/Statistics` | JSON/BSON Mongo, bulk unordered per batch | ack del replace/bulk verificato |

## Backlog prioritizzato

### P0 — verifica runtime ancora aperta

1. eseguire gli `explain("executionStats")` con dati rappresentativi e misurare il costo delle `COLLSCAN` intenzionali;
2. aggiungere un test di contratto che verifichi le chiavi consumate da `LeagueMessage`;
3. eseguire il caso reale con un match noto e verificare i participant dentro il documento Mongo.

### P1 — rendere affidabili gli aggiornamenti Mongo

1. verificare con il runtime il writer effettivo di `ProfileStatisticsService.refresh`;
2. correggere `Tracker` su queue, participant Riot null e queue Redis incoerente;
3. distinguere conversione bans/eventi fallita da payload vuoto.

### P2 — osservabilità e cache

1. invalidare le chiavi Redis `PROFILE_STATISTICS`, `PROFILE_RECENT_MATCHES` e `SUMMONER_DATA` durante i test di riconciliazione;
2. aggiungere correlation id comune tra comando, task Tracker, MariaDB match id e `_id` Mongo;
3. registrare tempi e conteggi per ogni fase: upsert summoner, upsert match, participant count, rank/event update.

## Conclusione

I fix coprono il contratto del documento match, l'upsert participant atomico, le projection calde, il batch profile e la leaderboard embedded. Il runtime LoL è Mongo-only; restano la verifica runtime con Mongo reale, gli `explain` e la riconciliazione delle cache versionate.
