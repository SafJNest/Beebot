# ADR-0009: MongoDB persistence and LoL migration

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-17
- Approved: 2026-07-18, main-agent approval after the full implementation request

## Amendment 2026-07-27

Il documento `match` usa il full Riot match ID direttamente in `_id` e `region` come unico campo di shard. `fullGameId`, `gameId`, `game_id` e `leagueShard` sono residui di mapping e non vengono persistiti. `patch` conserva la versione completa, mentre `patchMajor` conserva i primi due segmenti, per esempio `14.2`, ed è il campo usato dai filtri Mongo. La migration `raw-v6-match-schema` normalizza anche i match già presenti senza riscrivere participant o eventi. Il contratto HTTP resta invariato: `Match` continua a essere il modello canonico della response.

## Amendment 2026-07-26

La leaderboard mantiene `summoner.ranks[]` come unica sorgente canonica dei rank e non salva righe duplicate o pagine materializzate. Mongo può però mantenere la collection derivata `leaderboard_aggregates` per rank distribution e top-region: ogni documento contiene solo il risultato aggregato e il filtro della chiave, ed è sempre ricostruibile dai summoner. Gli snapshot materializzati vengono ricostruiti ogni 12 ore; i nuovi filtri vengono costruiti lazy alla prima lettura. La pagina e il totale restano derivati dai rank embedded; Redis viene invalidato insieme al rebuild. Il contratto HTTP di `LeaderboardPage`, `SummonerLeaderboard`, distribuzione, top-region e status `202` resta invariato.

Il bootstrap Mongo possiede anche la policy degli indici secondari dichiarati in `MongoDB.java`. `ensureIndexes(MongoDatabase)` viene eseguito dopo `ensureCollections(MongoDatabase)`, crea soltanto gli indici mancanti, usa nomi stabili e non esegue mai `dropIndex` o modifiche automatiche. Un indice esistente con stesso key pattern e opzioni compatibili viene riutilizzato; un conflitto di key pattern, nome, `unique` o partial filter interrompe il bootstrap con errore esplicito. Prima di creare `profile_statistics_identity`, il bootstrap verifica duplicati e identità mancanti su `{puuid, filterKey}` e non esegue cleanup automatici.

## Context

La persistenza LoL storica è concentrata in `LeagueDB`, una classe statica che contiene query SQL e mapping per summoner, rank, mastery, match e participant. Il runtime deve essere separato dal backfill MariaDB.

Il repository contiene già modelli canonici LoL, Redis come cache e servizi che usano il flusso `Redis -> database -> Riot`. La migrazione deve introdurre Mongo senza creare un secondo contratto HTTP o perdere dati durante il passaggio.

## Decisione

La prima migrazione copre solo `league_of_legends`. Gli altri domini MariaDB verranno trattati in ADR separati dopo il cutover LoL.

La strategia operativa è:

1. `MongoMigration` legge MariaDB con checkpoint e high-water mark;
2. il runtime LoL legge e scrive esclusivamente MongoDB;
3. Redis resta solo cache;
4. Riot API resta sorgente esterna per fallback e refresh;
5. `LeagueDB` resta un adapter SQL usato esclusivamente da `MongoMigration`.

Non esistono query MariaDB, mirror, fallback SQL, outbox o proxy dual-write nel runtime LoL.

Mongo userà:

- `puuid` come `_id` di `summoner`;
- Riot match ID completo come `_id` di `match`;
- rank e mastery incorporate nel summoner;
- champion statistics e build in collection aggregate separate;
- participant incorporati nel match;
- collection separate solo per dati derivati che richiedono un access pattern autonomo; la leaderboard usa direttamente `summoner.ranks[]` per le righe e mantiene soltanto gli snapshot aggregati in `leaderboard_aggregates`;
- nessun identificativo numerico MariaDB viene scritto nei documenti Mongo; le chiavi canoniche sono PUUID, full Riot match ID, queue e championId.
- gli eventi match sono separati in `match_events` e compressi da WiredTiger con Zstandard; match e masteries restano BSON normale.

## Boundary

`SummonerService`, `RankService`, `MasteryService`, `MatchService` and
`ProfileService` are the LoL cache-aware runtime boundaries (ADR-0011 / ADR-0012).
La persistenza runtime Mongo viene esposta direttamente tramite `MongoDB`; `LeagueDB` è confinato al percorso di lettura della migration.

Spring continua a possedere solo controller, configurazione HTTP ed error model. `QueryRecord` è il contenitore comune delle projection; gli oggetti complessi usano i modelli LoL già esistenti.

## Regole di serializzazione

- gli enum R4J vengono salvati come stringhe prodotte da `name()`;
- i ban usano `BLUE` e `RED`, mai ordinali numerici;
- i participant non hanno un mega-oggetto `build` annidato;
- gli eventi JSON vengono serializzati in `match_events` con `uncompressedBytes`, `checksum` e `encoding`; la compressione è nativa WiredTiger con livello server 9;
- il reader carica gli eventi separatamente e la history usa una query batch, senza N+1;
- `null` e `[]` mantengono semantiche distinte.

## Write path

Ogni mutazione LoL runtime passa direttamente da `MongoDB` con un'operazione idempotente. Gli aggiornamenti rank non invalidano gli snapshot; il task periodico ricostruisce gli aggregati Mongo e incrementa la versione Redis ogni 12 ore. Le query SQL sono ammesse solo nel percorso di lettura di `MongoMigration`; nessun consumer runtime può mantenere una `INSERT`, `UPDATE` o `DELETE` LoL.

## Configurazione

La configurazione Mongo viene letta da `rsc/settings.json` come stringa URI di connessione. Il database applicativo viene scelto dal codice:

```json
"mongo": "mongodb://<user>:<password>@safjnest.com:27017/"
```

`App.isTesting() == false` usa `beebot`; `App.isTesting() == true` usa `beebot_test`. Le collection usano gli stessi nomi delle tabelle MariaDB, senza prefisso `lol_`, in entrambi i database.

Il codice possiede il bootstrap delle collection e degli indici secondari dichiarati. Il bootstrap è idempotente e non esegue drop automatici; gli indici esistenti compatibili vengono riutilizzati, mentre quelli in conflitto richiedono una migrazione operativa esplicita. Il nuovo flusso non richiede cleanup automatici generali; la migration di schema del match normalizza soltanto i residui identificati da questa ADR. L'operatore rimuove manualmente gli altri payload obsoleti o duplicati prima della rigenerazione.

## Compatibilità API

Questa migrazione non modifica implicitamente il contratto HTTP. I modelli canonici restano quelli di `lol.model`.

L'amendment sugli snapshot `leaderboard_aggregates` è interno: non richiede modifiche a controller, modelli canonici o reference API.

I campi numerici dei modelli pubblici restano compatibili con il modello storico, ma non sono persistiti nei documenti Mongo e non sono chiavi di lookup.

## Conseguenze

### Positive

- profile e match detail eliminano join caldi;
- participant e dati di profile possono essere letti con access pattern naturali;
- le projection locali possono usare `QueryRecord` e `List<QueryRecord>`, anche annidate;
- gli oggetti complessi riusano i modelli canonici, senza DTO Mongo duplicati;
- MariaDB resta disponibile per il backfill e per gli altri domini;
- la stessa infrastruttura Mongo potrà essere riusata dagli altri domini.

### Negative

- il runtime non può usare MariaDB come fallback se Mongo è indisponibile;
- la leaderboard richiede `$unwind` e filtri su `summoner.ranks[]`; gli indici multikey riducono il `$match` iniziale, ma righe e totale vengono calcolati da Mongo e gli aggregati di distribuzione/top-region vengono persistiti in `leaderboard_aggregates`, ricostruiti ogni 12 ore e cacheati in Redis;
- il backfill richiede checkpoint, high-water mark e gestione dei payload corrotti;
- il backfill e il runtime devono essere verificati separatamente.

## Gate

Questa ADR è approvata per l'implementazione Java. Ogni conflitto con gli ADR LoL esistenti deve essere segnalato e non risolto implicitamente dal macro-task. Il vecchio `summoner.metrics` e le custom builds legacy restano fuori dal target corrente.
