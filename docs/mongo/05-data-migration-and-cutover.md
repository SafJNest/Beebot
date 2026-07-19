# Fase 5 — migrazione dati raw

## Runner

Il runner unico è `MongoMigration.migrateAll()`. Non parte automaticamente all'avvio.
Le opzioni controllano dry-run, batch, run id, resume e high-water mark.

```java
MongoMigration.migrateAll(new MongoMigration.Options(
        false, 125_000, "raw-2026-07", true, 0));
```

La dimensione massima configurabile per `summoner` è 125.000 righe. Ogni batch summoner carica anche rank e masteries relativi all'intervallo di id e li scrive con bulk Mongo da 2.000 documenti. La fase `matches` usa pagine da massimo 10.000 match: ogni pagina carica i participant con una query MariaDB bounded e viene liberata prima del giro successivo.

## Ordine e perimetro

Le fasi sono eseguite in questo ordine:

1. `summoners` → collection `summoner`, con `ranks[]` e `masteries[]` caricati nello stesso batch;
2. `matches` → collection `match`, con `participants[]` flat nel documento.

Sono dati raw. Il documento `summoner` usa `_id = puuid`, mentre match, rank, mastery e participant non conservano identificativi numerici MariaDB. La migrazione viene eseguita su un database Mongo vuoto: non esiste una fase applicativa di cleanup o conversione in-place.

Gli eventi eventualmente presenti nel JSON MariaDB vengono scritti separatamente in `match_events` tramite `MongoDB.upsertMatch()`: prima viene sostituito il documento `match`, poi il payload JSON viene sostituito in `match_events`, la cui compressione è delegata a WiredTiger Zstandard livello 9. Il documento `match` non contiene più `events`.

Non vengono migrati:

- `custom_build` e le collection/aggregate delle build;
- `profile_statistics`;
- `summoner_metric`, `metrics` e statistiche profilo/champion derivate;
- qualunque DTO o payload Mongo di build ricostruito durante il backfill.

I dati di build e le statistiche derivate verranno rigenerati dall'applicazione dopo la verifica dei dati raw.

## Paginazione

Le tabelle SQL sono lette con keyset pagination:

```sql
WHERE id > <highWaterMark>
ORDER BY id ASC
LIMIT <batchSize>
```

Non viene usato `OFFSET`, non viene materializzato il risultato completo e non viene eseguita una query senza `LIMIT`. La fase match usa `LeagueDB.getMatchesAfterId`, che legge una pagina di match e i relativi participant in una seconda query limitata alla pagina.

## Checkpoint e resume

`migration_runs` contiene run, fase, high-water mark, numero di righe processate, batch size, stato e timestamp. Gli stati sono `RUNNING`, `PAUSED` e `COMPLETED`.

Un rerun con lo stesso `runId` e `resume=true` riparte dall'ultimo id confermato della versione `raw-v4-bulk-no-checksum`. Gli upsert sono idempotenti; rank e masteries vengono fusi nell'array embedded usando rispettivamente `queue` e `championId` come chiavi stabili. Un checkpoint di una versione precedente non viene riutilizzato.

`highWaterMark > 0` permette di fermare intenzionalmente il backfill a un id. In quel caso il checkpoint resta `PAUSED` e può essere ripreso senza perdere la pagina già completata.

## Runbook ambiente test

1. verificare `MONGO_TEST_URI`/la URI in `settings.json` e `App.isTesting()`;
2. confermare che il database Mongo scelto sia `beebot_test`;
3. eliminare il database/collection target prima del run, così schema, indici e documenti partono puliti;
4. eseguire un dry-run con un high-water mark piccolo;
5. eseguire il backfill reale con batch 125.000 per `summoner` e 10.000 per `match`;
6. controllare `summoner`, `match`, `match_events`, `ranks[]`, `masteries[]`, `participants[]`;
7. ripetere con `resume=true` per verificare idempotenza e high-water mark;
8. costruire solo dopo build e profile statistics tramite i flussi applicativi.

Gli errori del backfill interrompono il run con fase e id espliciti. Gli errori del mirror runtime restano loggati senza falsificare il risultato MariaDB.
