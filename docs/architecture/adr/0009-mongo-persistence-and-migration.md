# ADR-0009: MongoDB persistence and LoL migration

- Status: Proposed
- Owner: Main agent
- Date: 2026-07-17

## Context

La persistenza LoL attuale è concentrata in `LeagueDB`, una classe statica che contiene query SQL, mapping, aggregazioni e scritture per summoner, rank, mastery, match, participant, statistiche, leaderboard e champion data.

Il repository contiene già modelli canonici LoL, Redis come cache e servizi che usano il flusso `Redis -> database -> Riot`. La migrazione deve introdurre Mongo senza creare un secondo contratto HTTP o perdere dati durante il passaggio.

## Decisione proposta

La prima migrazione copre solo `league_of_legends`. Gli altri domini MariaDB verranno trattati in ADR separati dopo il cutover LoL.

La strategia operativa è:

1. MariaDB primaria;
2. dual-write verso Mongo;
3. shadow-read e confronto dei modelli canonici;
4. cutover progressivo per capability;
5. rollback su MariaDB fino alla conclusione del periodo di osservazione.

Mongo userà:

- `puuid` come `_id` di `lol_summoners`;
- Riot match ID completo come `_id` di `lol_matches`;
- rank, mastery e metriche champion incorporate nel summoner;
- participant incorporati nel match;
- collection separate per dati derivati e aggregate;
- `legacySummonerId` e `legacyMatchId` solo per backfill e riconciliazione.

## Boundary

`LeagueService` resta il boundary LoL cache-aware. La persistenza viene esposta tramite `LeagueStore`, che nasconde MariaDB e Mongo e restituisce modelli canonici o risultati tipizzati.

Spring continua a possedere solo controller, configurazione HTTP ed error model. `MongoRecord` e i codec sono interni alla persistenza e non diventano success DTO pubblici. Gli oggetti complessi usano i modelli LoL già esistenti.

## Regole di serializzazione

- gli enum R4J vengono salvati come stringhe prodotte da `name()`;
- i ban usano `BLUE` e `RED`, mai ordinali numerici;
- i participant non hanno un mega-oggetto `build` annidato;
- gli eventi JSON legacy vengono convertiti in BSON strutturato quando possibile;
- un payload non convertibile viene conservato raw con versione e stato di conversione;
- `null` e `[]` mantengono semantiche distinte.

## Write path

Ogni mutazione LoL che oggi scrive MariaDB deve passare da una funzione tipizzata che:

1. aggiorna MariaDB;
2. aggiorna Mongo con un'operazione idempotente;
3. registra un retry durevole se il mirror fallisce;
4. invalida le cache correlate.

Nessun consumer può mantenere una `INSERT`, `UPDATE` o `DELETE` LoL indipendente.

## Configurazione

La configurazione Mongo viene letta da `rsc/settings.json` come stringa URI di connessione. Il database applicativo viene scelto dal codice:

```json
"mongo": "mongodb://<user>:<password>@safjnest.com:27017/"
```

`App.isTesting() == false` usa `beebot`; `App.isTesting() == true` usa `beebot_test`. Le collection LoL usano lo stesso prefisso `lol_` in entrambi i database.

Il codice possiede anche il bootstrap dello schema: ogni collection dichiara i propri indici con nomi e specifiche stabili, li crea se mancanti e fallisce su conflitti incompatibili. Il bootstrap è idempotente e non esegue drop automatici.

## Compatibilità API

Questa migrazione non modifica implicitamente il contratto HTTP. I modelli canonici restano quelli di `lol.model`.

Gli eventuali campi pubblici numerici legacy restano compatibili fino a una futura ADR API esplicita; internamente non sono più chiavi di lookup.

## Conseguenze

### Positive

- profile e match detail eliminano join caldi;
- participant e dati di profile possono essere letti con access pattern naturali;
- le projection locali possono usare `MongoRecord`;
- gli oggetti complessi riusano i modelli canonici, senza DTO Mongo duplicati;
- il cutover è reversibile;
- la stessa infrastruttura Mongo potrà essere riusata dagli altri domini.

### Negative

- durante il dual-write esistono due storage da monitorare;
- alcune projection, come leaderboard, devono essere mantenute;
- il backfill richiede checkpoint, checksum e gestione dei payload corrotti;
- non esiste una transazione atomica MariaDB/Mongo e serve un outbox temporaneo.

## Gate

Questa ADR deve essere approvata prima dell'implementazione Java. Ogni conflitto con gli ADR LoL esistenti deve essere segnalato e non risolto implicitamente dal macro-task.
