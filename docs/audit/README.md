# Audit flussi LoL e MongoDB

- Data: 2026-07-19
- Tipo: audit statico del codice e dei contratti
- Runtime Mongo/MariaDB: non eseguito in questo workspace
- Scope: scritture `LeagueDB`, tutti i comandi LoL, `Tracker`, profilo HTTP e query usate dai consumer

## Esito sintetico

La migrazione non è ancora verificabile come funzionante end-to-end. Sono presenti almeno due mismatch certi tra il contratto restituito dalle query MariaDB e quello prodotto da MongoDB:

| Severità | Flusso | Esito |
|---|---|---|
| P0 | Tracker / match insert | il Tracker continua dopo `saveMatch == 0`, con possibile participant su `match_id = 0` |
| P1 | Tracker / queue | la queue ricevuta viene ignorata e la match list è sempre Solo/Duo |
| P1 | Tracker / participant | un participant Riot non risolto può interrompere il batch con null dereference |
| P1 | `/summoner track` | il comando conferma anche quando `LeagueDB.trackSummoner` fallisce |
| P1 | profile statistics refresh | il refresh scrive direttamente Mongo e bypassa il writer MariaDB previsto dal piano |

## Documenti

1. [01-write-flow-match.md](01-write-flow-match.md) — insert match, participant, rank ed eventi;
2. [02-summoner-profile-flow.md](02-summoner-profile-flow.md) — comando profile e advanced overview;
3. [03-opgg-flow.md](03-opgg-flow.md) — comando OP.GG e storico LP;
4. [04-http-profile-flow.md](04-http-profile-flow.md) — endpoint HTTP profile/bootstrap/statistiche;
5. [05-query-contract-findings.md](05-query-contract-findings.md) — matrice dei contratti e backlog prioritizzato;
6. [06-all-lol-commands-tracker.md](06-all-lol-commands-tracker.md) — audit completo comandi LoL e Tracker.

## Metodo di verifica runtime

Prima di correggere i consumer bisogna eseguire un caso reale con un `puuid` e un match noto, registrando:

1. id restituito da MariaDB;
2. documento Mongo dopo ogni mirror;
3. `acknowledged`, `matchedCount`, `modifiedCount` e `upsertedId` di ogni write;
4. numero e `puuid` dei participant nel documento match;
5. chiavi effettivamente presenti nel `QueryResult` consegnato al consumer;
6. contenuto delle cache Redis prima e dopo il test.

Il test Mongo reale resta necessario con `MONGO_TEST_URI`; l’assenza di questa variabile impedisce di distinguere i mismatch statici dai problemi di connessione, autenticazione o schema sul server.
