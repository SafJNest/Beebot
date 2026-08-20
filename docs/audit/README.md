# Audit flussi LoL e MongoDB

- Data: 2026-07-22
- Aggiornamento nomi/routing: 2026-08-20 (ADR-0010, `ProfileService`, `ChampionService`)
- Tipo: audit statico del codice e dei contratti
- Runtime Mongo: non eseguito in questo workspace; MariaDB resta autorizzato solo da `MongoMigration`
- Scope: scritture `LeagueDB`, tutti i comandi LoL, `Tracker`, profilo HTTP e query usate dai consumer

## Esito sintetico

Il runtime LoL è stato portato a Mongo-only; le query MariaDB restano confinate alla migration. La verifica end-to-end con server reali resta da eseguire:

| Severità | Flusso | Esito |
|---|---|---|
| P0 | Tracker / match insert | verificare con Mongo reale l’ack del match prima degli upsert participant |
| P1 | Tracker / queue | verificare la queue ricevuta e la struttura della coda Redis |
| P1 | Tracker / participant | verificare la gestione dei participant Riot non risolti |
| P1 | account/tracking | verificare add, unlink e tracking con ownership `userId` |
| P1 | champion stats | verificare aggregazione di `match` e `match_events` separati |

## Documenti

1. [01-write-flow-match.md](01-write-flow-match.md) — insert match, participant, rank ed eventi;
2. [02-summoner-profile-flow.md](02-summoner-profile-flow.md) — comando profile e aggregato `ProfileStatistics`;
3. [03-opgg-flow.md](03-opgg-flow.md) — comando OP.GG e storico LP;
4. [04-http-profile-flow.md](04-http-profile-flow.md) — endpoint HTTP profile/bootstrap/statistiche;
5. [05-query-contract-findings.md](05-query-contract-findings.md) — matrice dei contratti e backlog prioritizzato;
6. [06-all-lol-commands-tracker.md](06-all-lol-commands-tracker.md) — audit completo comandi LoL e Tracker.
7. [07-champion-stats-build-flow.md](07-champion-stats-build-flow.md) — statistiche globali condivise e build champion lazy.

La fonte di verità operativa per il flusso unificato `ProfileStatistics` è [`../architecture/profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md). Include encoding di `filterKey`, indice Mongo, cache, refresh asincrono e composizione Discord/API.

## Metodo di verifica runtime

Prima di correggere i consumer bisogna eseguire un caso reale con un `puuid` e un match noto, registrando:

1. full Riot match id;
2. documento Mongo dopo ogni write;
3. `acknowledged`, `matchedCount`, `modifiedCount` e `upsertedId` di ogni write;
4. numero e `puuid` dei participant nel documento match;
5. chiavi effettivamente presenti nella `List<QueryRecord>` Mongo consegnata al consumer;
6. contenuto delle cache Redis prima e dopo il test.

Il test Mongo reale resta necessario con `MONGO_TEST_URI`; l’assenza di questa variabile impedisce di distinguere i mismatch statici dai problemi di connessione, autenticazione o schema sul server.
