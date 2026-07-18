# Fase 5 — migrazione dati

## Runner

Il runner unico è MongoMigration.migrateAll(), con MongoMigration.Options per dry-run, batch, run id, resume e high-water mark. Non parte automaticamente all'avvio.

MongoMigration legge MariaDB in batch e usa MongoDB.upsertDocument con chiavi deterministiche.

## Checkpoint

lol_migration_runs contiene run, fase, high-water mark, checksum, processed e timestamp. Un rerun con lo stesso runId e resume=true salta le righe già processate. Gli upsert sono idempotenti.

## Fasi

- summoner: puuid come _id, region e ricerca normalizzata;
- match: Riot match ID completo, bans BLUE/RED, participant flat ed eventi strutturati;
- profile statistics: decode Kryo, documento statistics strutturato e legacyPayload temporaneo.

Custom builds e summoner.metrics sono esclusi. Un payload non convertibile interrompe il run con fase e id espliciti; non viene marcato come documento Mongo valido.

## Runbook

1. verificare URI e database scelto da App.isTesting();
2. eseguire un batch piccolo in dry-run;
3. controllare checksum e high-water mark;
4. eseguire il batch reale;
5. ripetere con resume per verificare idempotenza.

Gli errori Mongo del mirror runtime non annullano MariaDB; il backfill invece fallisce esplicitamente per non nascondere conversioni incomplete.
