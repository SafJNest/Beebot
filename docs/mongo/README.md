# MongoDB LoL migration

Questa directory descrive l'implementazione lineare della migrazione MariaDB → MongoDB per LoL.

## Stato operativo

- MariaDB resta il writer compatibile.
- Dopo ogni commit MariaDB, LeagueDB chiama direttamente MongoDB.
- Le letture applicative LoL passano da MongoDB; non esiste fallback MariaDB.
- Un errore del mirror Mongo viene loggato e non annulla il risultato MariaDB.
- App.isTesting() seleziona beebot_test; altrimenti viene usato beebot.
- Custom builds e summoner.metrics sono fuori scope.
- Il backfill iniziale migra solo dati raw: `summoner`, `match` con participant, `rank` e `masteries`.
- `profile_statistics`, build e aggregate vengono costruiti successivamente dall'applicazione.
- Le collection usano i nomi delle tabelle (`summoner`, `match`, `profile_statistics`, ecc.) senza prefisso `lol_`.

## Struttura del codice

La persistenza Mongo LoL ha tre file:

- MongoDB.java: URI, database, schema, indici, query, mapping e write mirror;
- MongoRecord.java: wrapper leggero per projection e risultati locali;
- MongoMigration.java: backfill batchabile MariaDB → Mongo.

Non introdurre LeagueStore, package store o infrastructure, codec/mapper esterni, outbox, proxy dual-write, MongoResult o classi *Document.

## Ordine di lettura

1. 01-db-structure.md
2. 02-document-dtos.md
3. 03-query-migration.md
4. 04-write-path-and-refactor.md
5. 05-data-migration-and-cutover.md
6. 06-result-policy.md
7. 07-agent-strategy.md
8. 08-query-inventory.md
9. ADR-0009

## Regole BSON

- Summoner: _id = puuid.
- Match: _id = Riot match ID completo, per esempio EUW1_123.
- Enum R4J: name().
- Ban: bans.BLUE e bans.RED, sempre presenti anche se vuoti.
- Participant: campi flat; nessun campo build mega-nested.
- Eventi: BSON strutturato quando convertibile.
- Payload legacy: solo compatibilità temporanea e mai unica sorgente valida.

## Configurazione

rsc/settings.json contiene una URI server-level. La URI non deve contenere il database applicativo. MongoDB crea collection e indici mancanti in modo idempotente durante l'inizializzazione lazy. Credenziali e URI reali non devono comparire nei log, test o commit.

## Gate

Prima del completamento verificare massimo tre file Java sotto com.safjnest.mongo, nessun vecchio store/infrastructure/codec/mapper/outbox/proxy, letture LoL Mongo-only, chiamata Mongo visibile dopo ogni write MariaDB riuscita e test per database test, indici, bans, enum, participant flat, conversioni e migrazione resume/checksum.
