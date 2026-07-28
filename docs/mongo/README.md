# MongoDB LoL migration

Questa directory descrive l'implementazione lineare della migrazione MariaDB → MongoDB per LoL.

## Stato operativo

- MongoDB è l'unico storage runtime LoL.
- MariaDB viene letto esclusivamente da MongoMigration per il backfill.
- Le letture applicative LoL passano da MongoDB; non esiste fallback MariaDB.
- Un errore Mongo è esplicito nel runtime e non attiva fallback MariaDB.
- App.isTesting() seleziona beebot_test; altrimenti viene usato beebot.
- Custom builds e summoner.metrics sono fuori scope.
- Il backfill iniziale migra solo dati raw: prima `summoner` con `ranks[]` e `masteries[]` nello stesso batch, poi `match` con participant.
- `profile_statistics`, `profile_activity`, `profile_matchups`, build e `leaderboard_aggregates` vengono costruiti successivamente dall'applicazione; gli ultimi contengono solo snapshot ricostruibili di distribuzione e top-region.
- Il flusso completo di `profile_statistics`, inclusa la chiave applicativa `puuid + filterKey`, è documentato in [`docs/architecture/profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).
- Le collection usano i nomi delle tabelle (`summoner`, `match`, `profile_statistics`, `profile_activity`, `profile_matchups`, ecc.) senza prefisso `lol_`.
- Il documento `summoner` usa `_id = puuid`; gli identificativi numerici MariaDB e il campo duplicato `puuid` non vengono scritti.
- Il documento `match` usa `_id` come full Riot match ID e `region` come unico campo di shard; `fullGameId`, `gameId`, `game_id` e `leagueShard` non vengono scritti. `patch` mantiene la versione completa e `patchMajor` i primi due segmenti per i filtri.
- La migration normalizza i residui del documento `match`; gli altri documenti legacy e i vecchi payload Kryo restano fuori dal cleanup automatico e vengono rimossi manualmente prima della rigenerazione.
- I reader usano `_id` come fallback solo per compatibilità difensiva con documenti esterni alla migrazione pulita.
- Gli eventi non sono nel documento `match`: vivono in `match_events` come JSON e la collection usa WiredTiger Zstandard nativo.

## Struttura del codice

La persistenza Mongo/NoSQL LoL vive nel package `com.safjnest.nosql` e ha questi file principali:

- `src/main/java/com/safjnest/nosql/MongoDB.java`: URI, database, schema, registry degli indici, query, mapping e write runtime;
- `src/main/java/com/safjnest/nosql/MongoMigration.java`: backfill batchabile MariaDB → Mongo;
- `src/main/java/com/safjnest/nosql/AbstractEntity.java` e `NoSqlEntityExecutor.java`: infrastruttura comune per le entity persistite in NoSQL.

Gli adapter SQL usati esclusivamente dal backfill restano separati nel package `com.safjnest.sql`:

- `src/main/java/com/safjnest/sql/QueryRecordParser.java`: parser detached comune per righe MariaDB e documenti Mongo;
- `src/main/java/com/safjnest/sql/database/LeagueDB.java`: adapter SQL ridotto alle query necessarie a `MongoMigration`.

Non introdurre LeagueStore, package store o infrastructure, codec/mapper esterni, outbox, proxy dual-write o classi *Document.

## Ordine di lettura

1. 01-db-structure.md
2. 02-document-dtos.md
3. 03-query-migration.md
4. 04-write-path-and-refactor.md
5. 05-data-migration-and-cutover.md
6. 06-result-policy.md
7. 07-agent-strategy.md
8. 08-query-inventory.md
9. 09-space-optimization.md
10. ADR-0009

## Regole BSON

- Summoner: _id = puuid.
- Match: _id = Riot match ID completo, per esempio EUW1_123.
- Match: `region` è l'unico campo di shard; `patchMajor` è derivato da `patch` e usato nei filtri.
- Enum R4J: name().
- Ban: bans.BLUE e bans.RED, sempre presenti anche se vuoti.
- Participant: campi flat; nessun campo build mega-nested.
- Eventi: collection `match_events`, payload JSON con checksum e dimensione originale; la collection viene creata con `block_compressor=zstd`.
- Build e statistiche: `build` è BSON strutturato; `profile_statistics` è un documento flat con gli aggregati direttamente a root, mai una stringa opaca e mai `legacyPayload`.
- Activity: `profile_activity` salva il payload `ProfileActivity` strutturato con identità `{ puuid, filterKey }`, separata da `profile_statistics`.
- Matchups: `profile_matchups` salva il payload `ProfileMatchups` strutturato con identità `{ puuid, filterKey }`, separata da `profile_statistics`.
- MariaDB mantiene i dati storici letti dalla migration; il runtime LoL non li interroga.
- Redis: usa lo stesso codec Jackson condiviso e resta cache, senza migrazione dati.

Per `profile_statistics`, `profile_activity` e `profile_matchups`, `_id` non è una chiave business: il lookup e l'upsert usano sempre `{ puuid, filterKey }`. `$setOnInsert` genera un ObjectId casuale solo alla prima scrittura e gli aggiornamenti successivi mantengono lo stesso `_id`; i rispettivi indici unique proteggono l'unicità della coppia.

## Indici e spazio

Durante il backfill le collection vengono create e poi ricevono il registry di indici dichiarato in `MongoDB.java`. Ogni pagina esegue prima un preflight degli `_id` Mongo: i dati completi MariaDB vengono letti solo per i summoner e match mancanti, mentre gli eventi mancanti di match già presenti richiedono solo la colonna `events`. I summoner vengono inviati con bulk unordered da 20.000 documenti; i match restano in sotto-batch da 1.000.

L'inizializzazione è create-only e idempotente: crea gli indici mancanti, riusa quelli compatibili e interrompe il bootstrap su conflitti di nome, key pattern o opzioni. Non esegue `dropIndex` e il preflight dell'indice unique `profile_statistics_identity` interrompe l'avvio su identità mancanti o duplicate senza modificare i dati. `MongoDB.spaceAudit(sampleSize)` raccoglie `collStats`, `indexSizes`, BSON medio/massimo campionato, presenza di `userId`, tracking e regioni.

La compressione applicativa è disabilitata: `match_events` usa la compressione nativa WiredTiger. Il server Mongo deve usare `zstdCompressionLevel: 9`; match, summoner, masteries, build e statistiche restano documenti BSON strutturati e vengono compressi dal server.

## Configurazione

rsc/settings.json contiene una URI server-level. La URI non deve contenere il database applicativo. MongoDB crea collection e indici mancanti in modo idempotente durante l'inizializzazione lazy; gli indici esistenti incompatibili richiedono una migrazione operativa esplicita. Credenziali e URI reali non devono comparire nei log, test o commit.

## Gate

Prima del completamento verificare letture e scritture LoL Mongo-only, nessuna importazione runtime di LeagueDB, nessun mirror/outbox/proxy dual-write e test per database test, registry/idempotenza/preflight degli indici, bans, enum, participant flat, conversioni e migrazione resume/high-water mark.
