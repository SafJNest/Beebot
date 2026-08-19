# Changelog

Ricostruzione delle milestone LoL a partire dalla storia Git dei branch `lol-api` e `lol-api-mongo`. Le voci raggruppano i progressi funzionali e architetturali, non i singoli fix minori.

## 2026-08-19 — Discord summoner Mongo-first cutover

- Discord LoL commands risolvono l’identità sul modello canonico `Summoner` (Mongo/`SummonerService.get`), non più r4j per l’entry point.
- `UserData` tiene `Map<String, Summoner>` collegati; `Summoner.region` è `LeagueShard`; rimosso `summonerId` dal modello e dal JSON pubblico.
- Presentazione embed/button invariata; Tracker può ancora usare `getRiotSummoner` per il poll.

## 2026-07-23 — `lol-api-mongo`

- Aggiornato il rendering OP.GG con preview per match, queue speciali, team, KDA, item, rune, augment e informazioni temporali.
- Consolidato il passaggio dei risultati Mongo verso `QueryRecord` e `QueryRecordParser`, eliminando i contenitori legacy paralleli.
- Introdotta la base generica `AbstractEntity` / `NoSqlEntityExecutor` per aggiornamenti differiti, immediati e upsert tipizzati.
- Aggiornati `LeagueService`, `Tracker`, profilo e gestione match sulla persistenza Mongo condivisa.
- Rimossa la dipendenza applicativa dai vecchi percorsi SQL nei consumer LoL rimasti.

## 2026-07-18 — 2026-07-22 — migrazione `lol-api-mongo`

- Introdotta l’implementazione Mongo per il dominio `league_of_legends` con `MongoDB` e `MongoMigration`.
- Definite le identità canoniche: PUUID come `_id` di `summoner` e Riot match ID completo come `_id` di `match`.
- Portati in Mongo summoner, rank, mastery, match e participant con struttura BSON coerente ai modelli canonici.
- Separati gli eventi match nella collection `match_events`.
- Introdotti backfill a batch, paginazione keyset, checkpoint, resume, high-water mark e upsert idempotenti.
- Spostate le query LoL runtime da MariaDB a MongoDB, lasciando `LeagueDB` come adapter SQL della migration.
- Portati su Mongo i flussi di profile, search, leaderboard, match history, OP.GG, champion stats e build.
- Corretti i filtri champion/lane per applicarli allo stesso participant e aggiornati i percorsi di match history.
- Aggiornate cache, Tracker e invalidazioni per il nuovo boundary Redis → Mongo → Riot.
- Aggiunti inventario query e documentazione operativa della migrazione.

## 2026-07-18 — avvio della migrazione

- Definita ADR-0009 per la migrazione LoL MariaDB → MongoDB.
- Stabilito il perimetro iniziale limitato a `league_of_legends`.
- Stabilito il mantenimento di MariaDB come sorgente del backfill, senza fallback SQL nel runtime LoL.
- Definite le fasi per struttura dati, mapping, query, write path, backfill, policy dei risultati e strategia degli agenti.
- Esclusi dal primo perimetro `summoner.metrics`, `summoner_metric`, custom builds e gli altri domini del bot.

## 2026-07-10 — 2026-07-17 — branch `lol-api`

- Costruito il nuovo perimetro HTTP LoL con controller sottili, servizi applicativi e modelli canonici.
- Completato il flusso profilo con summoner, rank, mastery, statistiche, overview e match recenti.
- Aggiunto l’endpoint match con participant, lookup asincrono e gestione del dettaglio match.
- Aggiunto l’endpoint champion con statistiche, build aggregate e refresh lazy per filtro.
- Unificati i risultati API in `READY`, `PARTIAL`, `PENDING` e `NOT_FOUND`.
- Introdotte le risposte HTTP `202` per i dati LoL in generazione asincrona.
- Aggiornati leaderboard, MMR, queue ranked solo e ordinamento deterministico.
- Evoluti Tracker, code match e refresh asincroni su virtual thread con deduplicazione del lavoro.
- Migliorata la search summoner con il percorso full-text/autocomplete e il rank Solo nella stessa projection.
- Consolidati `SummonerView`, `Match`, `Participant`, `MatchResult`, `ChampionView` e gli altri modelli pubblici.
- Aggiornati API docs, ADR e macro-task per mantenere allineati contratti e responsabilità.
