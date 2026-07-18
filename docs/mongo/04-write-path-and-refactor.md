# Fase 4: write path e refactor applicativo

## Obiettivo

Fare in modo che ogni scrittura LoL abbia un solo punto applicativo e aggiorni MariaDB e Mongo secondo la strategia dual-write.

## Dipendenze

- [`README.md`](README.md);
- [`01-db-structure.md`](01-db-structure.md);
- [`02-document-dtos.md`](02-document-dtos.md);
- [`03-query-migration.md`](03-query-migration.md);
- ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0008;
- ADR-0009 approvata.

## Regola fondamentale

Ogni scrittura LoL MariaDB deve avere una funzione applicativa che si occupa anche del mirror Mongo.

Non è sufficiente aggiungere una seconda query in modo casuale accanto all'SQL. Il dato deve passare da un input canonico unico:

```text
consumer
  -> LeagueService o servizio LoL
    -> LeagueStore.write(...)
      -> MariaDB primary write
      -> Mongo mirror upsert
      -> cache invalidation
      -> retry/outbox in caso di errore
```

Nessun consumer può eseguire direttamente `INSERT`, `UPDATE`, `DELETE`, `defaultQuery` o query BSON.

## Boundary applicativo

### `LeagueService`

Resta il boundary cache-aware per profilo, match, search e Riot:

```text
Redis -> LeagueStore -> Riot
```

Non deve conoscere dettagli BSON o collection.

### `LeagueStore`

Espone operazioni di dominio/persistenza tipizzate. Non ritorna `QueryResult` o `QueryRecord`.

Implementazioni previste:

- `MariaDbLeagueStore`: adapter MariaDB transitorio;
- `MongoLeagueStore`: implementazione Mongo;
- `DualWriteLeagueStore`: MariaDB primaria + mirror Mongo;
- `ShadowReadLeagueStore`: confronto tra risultato primario e risultato Mongo.

### `Tracker`

Resta owner di:

- scrittura match;
- arricchimento participant;
- aggiornamento rank match;
- aggiornamento eventi;
- refresh asincroni;
- invalidazione delle cache correlate.

Non deve scrivere direttamente collection Mongo.

## Mappatura delle scritture

| Scrittura attuale | Funzione unica futura | Documento Mongo | Cache/invarianti |
|---|---|---|---|
| `addLOLAccount` | `saveSummoner` | `lol_summoners` | identity by PUUID |
| `deleteLOLaccount` | `detachSummonerUser` | `lol_summoners` | tracking off, user detached |
| `trackSummoner` | `setSummonerTracking` | `lol_summoners` | invalidare account list |
| `updateSummonerEntries` | `saveRanks` | `ranks[]`, leaderboard projection | rank cache |
| `updateSummonerMasteries` | `saveMasteries` | `masteries[]` | profile cache |
| `saveMatch` | `saveMatch` | `lol_matches` | detail cache |
| `setMatchRank` | `updateMatchRank` | match rank | detail cache |
| `setMatchEvent` | `updateMatchEvents` | match events | detail cache |
| `setSummonerData` | `saveParticipant` | match participants | profile/match cache |
| `saveProfileStatistics` | `saveProfileStatistics` | `lol_profile_statistics` | profile statistics Redis |
| `saveChampionBuild` | `saveChampionBuild` | `lol_champion_builds` | champion cache |
| `saveChampionStats` | `saveChampionStatistics` | `lol_champion_stats` | champion cache |
| leaderboard rebuild | `rebuildLeaderboard` | entries + distribution | leaderboard cache |

La funzione applicativa deve ricevere un DTO/input tipizzato e non una stringa SQL.

## Ordine dual-write

Durante la transizione:

1. validare l'input;
2. eseguire la scrittura MariaDB nella transazione esistente;
3. confermare MariaDB;
4. eseguire `upsert` Mongo con chiave deterministica;
5. aggiornare le projection derivate;
6. invalidare Redis;
7. registrare retry se il mirror non è riuscito.

MariaDB resta la fonte primaria finché il cutover della relativa capability non è approvato.

Le operazioni Mongo devono essere idempotenti. Ripetere la stessa funzione con lo stesso input non deve creare duplicati.

## Outbox temporaneo

Non esiste una transazione atomica tra MariaDB e Mongo.

Per garantire at-least-once, la funzione di scrittura deve poter registrare un evento in un outbox MariaDB temporaneo quando il mirror Mongo fallisce.

Campi minimi dell'outbox:

- `id`;
- `aggregateType`;
- `aggregateId` canonico;
- `operation`;
- `payload` versionato;
- `attempts`;
- `lastError`;
- `createdAt`;
- `nextAttemptAt`;
- `completedAt`.

L'outbox viene consumato da un worker idempotente. Non usare Redis come outbox durevole.

L'outbox può essere rimosso solo dopo il cutover, dopo aver verificato che nessun evento sia pending.

## Consumer da migrare

Devono smettere di chiamare direttamente `LeagueDB`:

- `LeagueService`;
- `ProfileStatisticsService`;
- `LeaderboardService`;
- `BuildService`;
- `ChampionStatsService`;
- `ChampionDataRefreshService`;
- `Tracker`;
- `LeagueMessage`;
- `LeagueHandler`;
- comandi summoner e tracker;
- eventuali test e comandi owner che usano dati LoL.

I consumer non-LoL possono restare su `BotDB`, `SpotifyDB` e `WebsiteDB`.

## Refactor per dipendenza

### Prima tranche

- introdurre `LeagueStore` e l'adapter MariaDB;
- sostituire le chiamate statiche nei service LoL principali;
- mantenere il comportamento attuale usando MariaDB;
- eliminare il ritorno di `QueryRecord` dai nuovi metodi.

### Seconda tranche

- introdurre `MongoRecord`, codec espliciti e `MongoLeagueStore`;
- collegare `DualWriteLeagueStore` tramite modalità configurabile;
- attivare outbox e retry;
- aggiungere shadow-read sui percorsi senza side effect.

### Terza tranche

- spostare le read capability una alla volta su Mongo;
- lasciare dual-write attivo;
- confrontare i modelli canonici, non i documenti BSON raw;
- disattivare SQL read solo dopo il gate della capability.

### Cleanup

- eliminare `LeagueDB` dal perimetro LoL;
- rimuovere query SQL LoL non più chiamate;
- rimuovere `QueryResult`/`QueryRecord` dai confini LoL;
- mantenere gli ID numerici solo dove richiesti dalla compatibilità transitoria;
- non rimuovere immediatamente campi pubblici già documentati senza ADR API separata.

## API e cache

Questa fase non modifica automaticamente i payload HTTP.

Se un refactor cambia:

- nomi dei campi;
- enum serializzati;
- formato ban;
- struttura participant;
- eventi;
- ID esposti;

deve aggiornare nello stesso task modello canonico, controller, documentazione API e consumer.

Quando cambia una serializzazione Redis o Kryo:

1. scegliere una versione di cache;
2. invalidare o migrare i valori esistenti;
3. verificare `200`, `202`, `PARTIAL` e `PENDING`;
4. impedire che un valore vecchio venga interpretato come payload nuovo.

## Feature flags

Le modalità operative documentate sono:

- `MARIADB`: read/write solo MariaDB;
- `DUAL_WRITE`: read MariaDB, write MariaDB + Mongo;
- `SHADOW_READ`: read MariaDB e confronto Mongo non esposto al client;
- `MONGO_READ`: read Mongo, write MariaDB + Mongo;
- `MONGO`: read/write Mongo dopo il periodo di stabilizzazione.

La modalità deve essere letta dalla configurazione applicativa e loggata all'avvio senza stampare credenziali.

## Log e metriche

Ogni mirror deve produrre metriche/log per:

- aggregate type;
- operation;
- success/failure;
- durata MariaDB;
- durata Mongo;
- retry count;
- mismatch shadow-read;
- outbox lag.

I log non devono contenere password, URI completi o payload sensibili non necessari.

## Acceptance criteria

- ogni scrittura LoL ha una funzione unica documentata;
- nessun consumer scrive direttamente SQL LoL dopo la migrazione del relativo modulo;
- Mongo mirror è idempotente;
- gli errori Mongo hanno retry durevole;
- `LeagueService` resta il boundary API/cache/Riot;
- `Tracker` resta owner del lavoro asincrono e delle scritture match;
- API e cache non cambiano implicitamente;
- `LeagueDB` è solo adapter transitorio e non owner parallelo.
- custom builds e `summoner.metrics` non rientrano nel target Mongo corrente.
