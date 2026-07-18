# Fase 5: migrazione dati, riconciliazione e cutover

## Obiettivo

Definire il runner che migra tutti i dati LoL da MariaDB a MongoDB e il runbook per shadow-read, cutover e rollback.

## Dipendenze

- [`README.md`](README.md);
- [`01-db-structure.md`](01-db-structure.md);
- [`02-document-dtos.md`](02-document-dtos.md);
- [`03-query-migration.md`](03-query-migration.md);
- [`04-write-path-and-refactor.md`](04-write-path-and-refactor.md);
- ADR-0009 approvata;
- Mongo raggiungibile dalla configurazione `rsc/settings.json`.

## Runner

Il runner applicativo previsto è:

```java
LolMariaDbMongoMigration.migrateAll(MigrationOptions options)
```

Non deve partire automaticamente all'avvio del bot. Deve essere avviato esplicitamente da un comando owner, job controllato o entry point operativo equivalente.

## `MigrationOptions`

Campi minimi:

- `dryRun`;
- `batchSize`;
- `startPhase`;
- `startLegacyId`;
- `maxRecords`;
- `verifyOnly`;
- `rebuildIndexes`;
- `stopOnError`;
- `continueOnConversionError`.

Valori non validi devono fallire prima di aprire la migrazione.

## Stato e checkpoint

La collection `lol_migration_runs` conserva:

- `runId`;
- `phase`;
- `sourceTable`;
- `targetCollection`;
- `status`;
- `lastLegacyId`;
- `processed`;
- `inserted`;
- `updated`;
- `skipped`;
- `failed`;
- `conversionFailed`;
- `startedAt`;
- `updatedAt`;
- `completedAt`;
- `lastError`.

Il runner deve poter ripartire dall'ultimo checkpoint senza duplicare documenti.

## Ordine obbligatorio

### 1. Preflight

- verificare URI e accesso Mongo;
- verificare database `beebot` oppure `beebot_test` secondo `App.isTesting()`;
- verificare versione driver e capacità BSON;
- verificare connessione MariaDB;
- acquisire i conteggi iniziali delle tabelle;
- acquisire un high-water mark per ogni tabella migrata;
- verificare spazio disponibile e indici;
- non modificare dati sorgente.

### 2. Collection piccole e cataloghi

Migrare:

- `champion` → `lol_champions`;
- `leaderboard_distribution` → `lol_leaderboard_distribution`;

con upsert deterministici.

### 3. Summoner aggregato

Per batch ordinati per `summoner.id`:

1. leggere summoner;
2. leggere rank per intervallo di ID;
3. leggere masteries per intervallo di ID;
4. convertire enum e Riot ID;
5. costruire il documento Mongo tramite `MongoRecord` e il mapping del modello `Summoner`;
6. scrivere con `_id = puuid`;
7. aggiornare checkpoint.

Non eseguire una query per ogni summoner.

Se più righe SQL producono lo stesso PUUID, il runner deve registrare il conflitto e non scegliere silenziosamente una regione.

### 4. Match aggregato

Per batch ordinati per `match.id`:

1. leggere match;
2. leggere tutti i participant dell'intervallo di match;
3. risolvere `summoner_id -> puuid` tramite batch map;
4. convertire ban ordinali in `BLUE` e `RED`;
5. convertire eventi JSON in BSON;
6. appiattire i dati build nel participant;
7. costruire il documento Mongo tramite `MongoRecord` e il mapping del modello `Match`;
8. scrivere con `_id = Riot match ID completo`;
9. se il documento supera il limite BSON, separare gli eventi;
10. aggiornare checkpoint.

Un match senza participant rimane migrabile e viene marcato con `participantsStatus` esplicito.

### 5. Profile statistics

Per ogni riga `profile_statistics`:

1. risolvere il summoner legacy in PUUID;
2. decodificare il payload Kryo;
3. validare `ProfileStatistics`;
4. salvare struttura BSON normalizzata;
5. mantenere `legacyPayload` se necessario;
6. usare chiave `puuid|seasonStart`;
7. registrare stato conversione.

Un payload non decodificabile non deve essere scartato.

### 6. Champion data e custom data

Migrare:

- champion builds;
- champion stats;
- leaderboard projection;
- distribuzioni residue.

Le build devono avere chiavi deterministiche e non affidarsi a `ObjectId` casuali per l'idempotenza.

### 7. Projection leaderboard

Ricostruire `lol_leaderboard_entries` dai rank embedded e poi verificare:

- totale globale;
- totale per regione;
- totale per tier;
- ordinamento MMR;
- assenza di duplicati `queue + region + puuid`.

## Consistenza durante il backfill

Il dual-write viene attivato prima del backfill completo.

Per evitare che un record vecchio sovrascriva un aggiornamento più recente:

- ogni documento contiene `sourceUpdatedAt` o un equivalente monotono;
- l'upsert confronta la versione sorgente;
- le operazioni mirror riuscite sono idempotenti;
- gli errori vengono registrati nell'outbox MariaDB;
- alla fine del backfill viene eseguito un replay dell'outbox;
- viene eseguita una seconda riconciliazione.

## Riconciliazione

La verifica deve essere eseguita per collection e per campione semantico.

### Conteggi

Confrontare:

- righe MariaDB;
- documenti Mongo;
- documenti esclusi;
- documenti con conversione parziale;
- errori permanenti.

### Checksum

Il checksum deve ignorare solo:

- `_id` tecnici equivalenti;
- `legacy*` non presenti nel modello canonico;
- timestamp di conversione.

Deve invece confrontare:

- PUUID;
- Riot match ID;
- queue, region, rank e lane;
- ban BLUE/RED;
- participant;
- item, rune, spell, skill e augment;
- eventi;
- statistiche aggregate.

### Query shadow-read

Il confronto deve avvenire sui modelli canonici dopo decode, non sul testo BSON grezzo. Un mismatch deve riportare:

- aggregate ID;
- campo divergente;
- valore MariaDB;
- valore Mongo;
- versione sorgente;
- timestamp;
- azione suggerita.

## Cutover progressivo

Ordine consigliato:

1. summoner profile base;
2. rank/mastery/profile statistics;
3. match detail;
4. recent matches e match history;
5. leaderboard;
6. champion stats/build;
7. scritture Mongo primary.

Per ogni capability:

1. attivare `DUAL_WRITE`;
2. completare backfill;
3. verificare conteggi e checksum;
4. attivare `SHADOW_READ`;
5. osservare mismatch e latenza;
6. attivare `MONGO_READ`;
7. mantenere dual-write;
8. approvare il gate;
9. passare alla capability successiva.

## Rollback

Il rollback deve essere una modifica di modalità, non una riscrittura dati manuale:

```text
MONGO_READ -> SHADOW_READ -> DUAL_WRITE -> MARIADB
```

Durante il rollback:

- non cancellare Mongo;
- mantenere il mirror attivo se possibile;
- congelare il cutover successivo;
- correggere mismatch e outbox pending;
- ripetere la riconciliazione;
- riaprire il gate solo con evidenze.

MariaDB non viene dismessa finché:

- non esiste un backup verificato;
- l'outbox è vuoto;
- shadow-read è stabile;
- il periodo di osservazione è concluso;
- il rollback è stato provato;
- le query critiche hanno `explain` conforme.

## Failure policy

### Errore di connessione Mongo

- MariaDB rimane primaria;
- la richiesta non perde il successo già committato;
- viene creato retry outbox;
- viene emesso log operativo senza credenziali.

### Errore di conversione

- salvare payload raw;
- incrementare `conversionFailed`;
- continuare solo se `continueOnConversionError` è attivo;
- impedire che il documento venga marcato come completamente convertito.

### Duplicato PUUID

- fermare il batch interessato;
- registrare tutte le righe coinvolte;
- non scegliere automaticamente una regione;
- richiedere risoluzione esplicita prima del cutover summoner.

### Documento oltre 16 MB

- non troncare;
- spostare eventi o payload voluminosi nella collection dedicata;
- mantenere riferimento e checksum;
- segnare la conversione come completa solo dopo la verifica del riferimento.

## Verifiche obbligatorie

- test dry-run;
- test batch piccolo;
- test stop/resume;
- test rerun idempotente;
- test errore Mongo;
- test outbox e replay;
- test JSON evento corrotto;
- test ban ordinali e nominati;
- test participant flat;
- test documento oltre soglia BSON;
- conteggi e checksum;
- `explain("executionStats")`;
- integrazione con MariaDB e Mongo reali;
- build e test con JDK 25/Maven.

## Acceptance criteria

- tutti i dati LoL hanno una fase di migrazione;
- ogni fase è riprendibile;
- il runner è idempotente;
- nessun payload legacy viene perso silenziosamente;
- i ban risultano `BLUE`/`RED`;
- match e summoner usano le chiavi canoniche;
- dual-write e outbox sono osservabili;
- shadow-read confronta modelli canonici;
- cutover e rollback sono eseguibili senza modifica manuale dei documenti;
- MariaDB non viene dismessa prima del gate finale.
