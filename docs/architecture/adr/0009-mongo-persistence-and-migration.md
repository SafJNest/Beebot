# ADR-0009: MongoDB persistence and LoL migration

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-17
- Approved: 2026-07-18, main-agent approval after the full implementation request

## Amendment 2026-07-25

La decisione sulla leaderboard è aggiornata: il runtime non mantiene più projection o distribuzioni persistite. `summoner.ranks[]` è l'unica sorgente Mongo; pagina, totale, distribuzione e top-region derivano da aggregation filtrate. Il contratto HTTP di `LeaderboardPage`, `SummonerLeaderboard`, distribuzione, top-region e status `202` resta invariato. Le collection obsolete possono restare presenti fino a una pulizia operativa manuale.

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
- collection separate solo per dati derivati che richiedono un access pattern autonomo; la leaderboard usa direttamente `summoner.ranks[]` e non mantiene collection derivate;
- nessun identificativo numerico MariaDB viene scritto nei documenti Mongo; le chiavi canoniche sono PUUID, full Riot match ID, queue e championId.
- gli eventi match sono separati in `match_events` e compressi da WiredTiger con Zstandard; match e masteries restano BSON normale.

## Boundary

`LeagueService` resta il boundary LoL cache-aware. La persistenza runtime Mongo viene esposta direttamente tramite `MongoDB`; `LeagueDB` è confinato al percorso di lettura della migration.

Spring continua a possedere solo controller, configurazione HTTP ed error model. `QueryRecord` è il contenitore comune delle projection; gli oggetti complessi usano i modelli LoL già esistenti.

## Regole di serializzazione

- gli enum R4J vengono salvati come stringhe prodotte da `name()`;
- i ban usano `BLUE` e `RED`, mai ordinali numerici;
- i participant non hanno un mega-oggetto `build` annidato;
- gli eventi JSON vengono serializzati in `match_events` con `uncompressedBytes`, `checksum` e `encoding`; la compressione è nativa WiredTiger con livello server 9;
- il reader carica gli eventi separatamente e la history usa una query batch, senza N+1;
- `null` e `[]` mantengono semantiche distinte.

## Write path

Ogni mutazione LoL runtime passa direttamente da `MongoDB` con un'operazione idempotente e invalida le cache correlate. Le query SQL sono ammesse solo nel percorso di lettura di `MongoMigration`; nessun consumer runtime può mantenere una `INSERT`, `UPDATE` o `DELETE` LoL.

## Configurazione

La configurazione Mongo viene letta da `rsc/settings.json` come stringa URI di connessione. Il database applicativo viene scelto dal codice:

```json
"mongo": "mongodb://<user>:<password>@safjnest.com:27017/"
```

`App.isTesting() == false` usa `beebot`; `App.isTesting() == true` usa `beebot_test`. Le collection usano gli stessi nomi delle tabelle MariaDB, senza prefisso `lol_`, in entrambi i database.

Il codice possiede il bootstrap delle collection, ma non crea né gestisce indici secondari. Il bootstrap è idempotente e non esegue drop automatici; eventuali indici già presenti restano responsabilità operativa esterna al runtime. Il nuovo flusso non richiede cleanup automatici; l'operatore rimuove manualmente i payload obsoleti prima della rigenerazione.

## Compatibilità API

Questa migrazione non modifica implicitamente il contratto HTTP. I modelli canonici restano quelli di `lol.model`.

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
- la leaderboard richiede `$unwind` e filtri su `summoner.ranks[]`; il bootstrap non crea indici dedicati, mentre totale, pagina e distribuzioni vengono calcolati da Mongo e cacheati in Redis;
- il backfill richiede checkpoint, high-water mark e gestione dei payload corrotti;
- il backfill e il runtime devono essere verificati separatamente.

## Gate

Questa ADR è approvata per l'implementazione Java. Ogni conflitto con gli ADR LoL esistenti deve essere segnalato e non risolto implicitamente dal macro-task. Il vecchio `summoner.metrics` e le custom builds legacy restano fuori dal target corrente.
