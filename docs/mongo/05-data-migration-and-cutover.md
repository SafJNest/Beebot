# Fase 5 — migrazione dati raw

## Runner

Il runner unico è `MongoMigration.migrateAll()`. Non parte automaticamente all'avvio.
Le opzioni controllano dry-run, batch, run id, resume e high-water mark.

```java
MongoMigration.migrateAll(new MongoMigration.Options(
        false, 500_000, "raw-2026-07", true, 0));
```

La dimensione massima configurabile per la pagina `summoner` è 500.000 righe. Ogni pagina prima legge solo `id, puuid` e verifica gli `_id` già presenti in Mongo; i dati completi vengono richiesti a MariaDB esclusivamente per i summoner mancanti, in sotto-batch da 20.000. Rank e masteries vengono letti solo per quei summoner, a pagine da 10.000 righe, e la `List<QueryRecord>` viene rilasciata a ogni pagina. La fase `matches` usa pagine da massimo 50.000 identificativi, ma carica match e participant in sotto-batch da 1.000 senza espandere gli eventi; dopo l'inserimento dei match legge solo la colonna `events` per gli eventi mancanti, sempre a batch da 1.000. Ogni 10 sotto-batch viene richiesto un ciclo di garbage collection, oltre al cleanup a fine pagina e al termine della migrazione; la richiesta è best-effort e non sostituisce il rilascio esplicito dei riferimenti.

## Ordine e perimetro

Le fasi sono eseguite in questo ordine:

1. `summoners` → collection `summoner`, con `ranks{}` e `masteries[]` caricati nello stesso batch;
2. `matches` → collection `match`, con `participants[]` flat nel documento;
3. `match_events` → payload eventi separato, solo quando il documento evento non esiste.
4. `rank-progress-schema-v1` → sola scansione Mongo dei match: sposta
   `participants.rank`, `lp` e `gain` in `participants.rankProgress` e rimuove
   i campi plain;
5. `rank-progress-history-v1` → sola scansione Mongo per `(region, puuid)`,
   Solo/Duo ordinata `timeStart DESC, _id DESC`, che ricostruisce gli snapshot
   precedenti senza chiamate Riot o letture MariaDB.

La schema pass seleziona al massimo 10.000 soli `_id` di match che contengono
ancora `participants.rank`, `lp` o `gain`, quindi esegue un unico
`updateMany` con aggregation pipeline direttamente su Mongo: costruisce
`rankProgress` e rimuove i campi plain senza trasferire array `participants`
nel JVM. Su documenti già canonici la pass termina senza leggere i match. La
discovery history è un cursor di aggregation (`match → unwind participant →
group(region, puuid)`), quindi non materializza tutti i subject in memoria.
Per ogni subject la timeline è una query streaming che proietta soltanto
`_id`, `tracked` e il suo `rankProgress`; gli aggiornamenti sono `bulkWrite`
posizionali da 1.000 e non riscrivono l'intero array `participants`.
Per un match non tracked il predecessore viene collegato soltanto quando il
gain legacy coincide con quello rank-aware, eccetto le transizioni placement:
`UNRANKED → ranked` forza `gain = lp` corrente e `ranked → UNRANKED` forza
`gain = 0`, così corregge gli storici MariaDB che avevano registrato il gain
in modo errato. Questa tolleranza appartiene solo alla migration; il tracker
runtime continua a validare il suo snapshot corrente.

Sono dati raw. Il documento `summoner` usa `_id = puuid`, mentre il documento `match` usa `_id` come full Riot match ID e conserva solo `region` tra i dati di shard. Match, rank, mastery e participant non conservano identificativi numerici MariaDB. La migration normalizza i match già presenti rimuovendo i campi legacy duplicati e aggiungendo `patchMajor`; non modifica participant o eventi esistenti.

Gli eventi eventualmente presenti nel JSON MariaDB vengono scritti separatamente in `match_events` tramite `MongoDB.upsertMatchDocument()` e `MongoDB.upsertMatchEvents()`: prima viene sostituito il documento `match`, poi il payload JSON viene sostituito in `match_events`, la cui compressione è delegata a WiredTiger Zstandard livello 9. Il documento `match` non contiene più `events`.

Non vengono migrati:

- `custom_build` e le collection/aggregate delle build;
- `profile_statistics`;
- `summoner_metric`, `metrics` e statistiche profilo/champion derivate;
- qualunque DTO o payload Mongo di build ricostruito durante il backfill.

I dati di build e le statistiche derivate verranno rigenerati dall'applicazione dopo la verifica dei dati raw.

La rigenerazione delle statistiche profile deve usare il flusso applicativo canonico descritto in [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md): stesso `Filter`, stesso `filterKey` e upsert sulla coppia `puuid + filterKey`.

## Paginazione

Le tabelle SQL sono lette con keyset pagination:

```sql
WHERE id > <highWaterMark>
ORDER BY id ASC
LIMIT <batchSize>
```

Non viene usato `OFFSET`, non viene materializzato il risultato completo e non viene eseguita una query senza `LIMIT`. La fase match legge prima solo le chiavi MariaDB con keyset pagination, controlla gli `_id` mancanti in Mongo e carica i match/participant mancanti con `LeagueDB.getMatchesByIds` in sotto-batch limitati.

## Checkpoint e resume

`migration_runs` contiene run, fase, high-water mark, numero di righe processate, batch size, stato e timestamp. Gli stati sono `RUNNING`, `PAUSED` e `COMPLETED`.

Un rerun con lo stesso `runId` e `resume=true` riparte dall'ultimo id confermato della versione `raw-v6-match-schema`. Prima di ogni query pesante il runner ricontrolla gli `_id` presenti in Mongo; i match già presenti vengono inoltre normalizzati senza rilettura da MariaDB, mentre un match mancante viene caricato dal backfill. Gli upsert sono idempotenti; rank e masteries vengono fusi rispettivamente nell'object `ranks{}` tramite la key `queue` e nell'array masteries tramite `championId`. Un checkpoint di una versione precedente non viene riutilizzato.

Le due fasi RankProgress hanno checkpoint distinti per `runId`. Con lo stesso
`runId` e `resume=true`, un checkpoint `COMPLETED` non viene rieseguito. Un
nuovo `runId`, o `resume=false`, riscorre Mongo ed è idempotente: completa o
ricontrolla `rankProgress`, ma non ricarica i match già presenti da MariaDB.
Gli indici dichiarati, inclusi `match_rank_progress_history` e
`match_rank_progress_subjects`, devono esistere prima del job; il runner
RankProgress non li valida né li crea.

## Recupero mirato dei summoner tracked

`%test migrate-tracked` esegue una recovery esplicita, senza checkpoint, per i
soli documenti `summoner.tracking=true`. Per ogni PUUID legge la sua intera
history da MariaDB, crea soltanto i match Mongo assenti e i loro eventi
mancanti, quindi ripristina sui soli match `tracked != true` il base snapshot
del participant (`rank`, `lp`, `gain`) senza predecessore. Infine ricostruisce
`previousRank`, `previousLp` e gain rank-aware per le timeline Solo/Duo di quel
PUUID. Un match `tracked=true` resta la fonte runtime autorevole e non viene
sovrascritto dalla recovery.

`highWaterMark > 0` permette di fermare intenzionalmente il backfill a un id. In quel caso il checkpoint resta `PAUSED` e può essere ripreso senza perdere la pagina già completata.

## Runbook ambiente test

1. verificare `MONGO_TEST_URI`/la URI in `settings.json` e `App.isTesting()`;
2. confermare che il database Mongo scelto sia `beebot_test`;
3. eliminare il database/collection target prima del run, così schema, indici e documenti partono puliti;
4. eseguire un dry-run con un high-water mark piccolo;
5. eseguire il backfill reale con batch 500.000 per `summoner` e 50.000 per `match`;
6. controllare `summoner`, `match`, `match_events`, `ranks{}`, `masteries[]`, `participants[]`;
7. ripetere con `resume=true` per verificare idempotenza e high-water mark;
8. costruire solo dopo build e profile statistics tramite i flussi applicativi.

Gli errori del backfill interrompono il run con fase e id espliciti. Il runtime LoL non esegue mirror MariaDB/Mongo e non usa MariaDB come fallback.
