# ADR-0009: MongoDB persistence and LoL migration

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-17
- Approved: 2026-07-18, main-agent approval after the full implementation request

## Context

La persistenza LoL attuale è concentrata in `LeagueDB`, una classe statica che contiene query SQL, mapping, aggregazioni e scritture per summoner, rank, mastery, match, participant, statistiche, leaderboard e champion data.

Il repository contiene già modelli canonici LoL, Redis come cache e servizi che usano il flusso `Redis -> database -> Riot`. La migrazione deve introdurre Mongo senza creare un secondo contratto HTTP o perdere dati durante il passaggio.

## Decisione proposta

La prima migrazione copre solo `league_of_legends`. Gli altri domini MariaDB verranno trattati in ADR separati dopo il cutover LoL.

La strategia operativa è:

1. MariaDB primaria;
2. mirror immediato verso Mongo dopo il commit;
3. letture applicative Mongo-only;
4. migrazione batch con checkpoint e high-water mark;
5. MariaDB resta writer compatibile finché il cutover non viene approvato.

Il mirror fallito viene loggato e non modifica il risultato MariaDB. Non esistono fallback di lettura, outbox o proxy dual-write.

Mongo userà:

- `puuid` come `_id` di `summoner`;
- Riot match ID completo come `_id` di `match`;
- rank e mastery incorporate nel summoner;
- champion statistics e build in collection aggregate separate;
- participant incorporati nel match;
- collection separate per dati derivati e aggregate;
- nessun identificativo numerico MariaDB viene scritto nei documenti Mongo; le chiavi canoniche sono PUUID, full Riot match ID, queue e championId.
- gli eventi match sono separati in `match_events` e compressi da WiredTiger con Zstandard; match e masteries restano BSON normale.

## Boundary

`LeagueService` resta il boundary LoL cache-aware. La persistenza Mongo viene esposta direttamente tramite `MongoDB`; `LeagueDB` resta il writer MariaDB compatibile e chiama `MongoDB` nello stesso metodo dopo il commit.

Spring continua a possedere solo controller, configurazione HTTP ed error model. `MongoRecord` è interno alle projection; gli oggetti complessi usano i modelli LoL già esistenti. Non esiste un `MongoResult` generico.

## Regole di serializzazione

- gli enum R4J vengono salvati come stringhe prodotte da `name()`;
- i ban usano `BLUE` e `RED`, mai ordinali numerici;
- i participant non hanno un mega-oggetto `build` annidato;
- gli eventi JSON vengono serializzati in `match_events` con `uncompressedBytes`, `checksum` e `encoding`; la compressione è nativa WiredTiger con livello server 9;
- il reader carica gli eventi separatamente e la history usa una query batch, senza N+1;
- `null` e `[]` mantengono semantiche distinte.

## Write path

Ogni mutazione LoL che oggi scrive MariaDB deve passare da una funzione tipizzata che:

1. aggiorna MariaDB;
2. aggiorna Mongo con un'operazione idempotente;
3. cattura e logga l'errore Mongo senza falsificare il risultato MariaDB;
4. invalida le cache correlate.

Nessun consumer può mantenere una `INSERT`, `UPDATE` o `DELETE` LoL indipendente.

## Configurazione

La configurazione Mongo viene letta da `rsc/settings.json` come stringa URI di connessione. Il database applicativo viene scelto dal codice:

```json
"mongo": "mongodb://<user>:<password>@safjnest.com:27017/"
```

`App.isTesting() == false` usa `beebot`; `App.isTesting() == true` usa `beebot_test`. Le collection usano gli stessi nomi delle tabelle MariaDB, senza prefisso `lol_`, in entrambi i database.

Il codice possiede anche il bootstrap dello schema: ogni collection dichiara i propri indici con nomi e specifiche stabili, li crea se mancanti e fallisce su conflitti incompatibili. Il bootstrap è idempotente e non esegue drop automatici. `summoner` usa un indice `region + riotSearch` e un indice parziale `tracking=true`. Il nuovo flusso non richiede né esegue cleanup automatici; l'operatore rimuove manualmente i payload obsoleti prima della rigenerazione.

## Compatibilità API

Questa migrazione non modifica implicitamente il contratto HTTP. I modelli canonici restano quelli di `lol.model`.

I campi numerici dei modelli pubblici restano compatibili per il writer MariaDB, ma non sono persistiti nei documenti Mongo e non sono chiavi di lookup.

## Conseguenze

### Positive

- profile e match detail eliminano join caldi;
- participant e dati di profile possono essere letti con access pattern naturali;
- le projection locali possono usare `MongoRecord`;
- gli oggetti complessi riusano i modelli canonici, senza DTO Mongo duplicati;
- il cutover è reversibile;
- la stessa infrastruttura Mongo potrà essere riusata dagli altri domini.

### Negative

- durante la transizione esistono due storage da monitorare;
- alcune projection, come leaderboard, devono essere mantenute;
- il backfill richiede checkpoint, high-water mark e gestione dei payload corrotti;
- non esiste una transazione atomica MariaDB/Mongo; il mirror è quindi best-effort e osservabile.

## Gate

Questa ADR è approvata per l'implementazione Java. Ogni conflitto con gli ADR LoL esistenti deve essere segnalato e non risolto implicitamente dal macro-task. Il vecchio `summoner.metrics` e le custom builds legacy restano fuori dal target corrente.
