# TODO

Indice operativo del lavoro aperto. I dettagli architetturali e le procedure restano nei documenti collegati.

## P0 — Chiusura migrazione Mongo LoL

- [ ] Eseguire un test d'integrazione con Mongo reale tramite `MONGO_TEST_URI` o `mongod` locale.
- [ ] Eseguire un dry-run del backfill con high-water mark ridotto.
- [ ] Eseguire il backfill reale di `summoner`, `match` e `match_events` su dati rappresentativi.
- [ ] Verificare `resume=true`, checkpoint e high-water mark dopo un'interruzione intenzionale.
- [ ] Eseguire la riconciliazione tra dati MariaDB sorgente e documenti Mongo migrati.
- [ ] Verificare che tutti i consumer LoL runtime leggano e scrivano solo MongoDB.
- [ ] Verificare che `LeagueDB` resti utilizzato esclusivamente da `MongoMigration`.
- [ ] Verificare write acknowledgement, idempotenza e comportamento esplicito degli errori Mongo.

Riferimenti: [current audit](docs/architecture/current-audit.md), [migrazione dati](docs/mongo/05-data-migration-and-cutover.md), [ADR-0009](docs/architecture/adr/0009-mongo-persistence-and-migration.md).

## P1 — Verifica query e runtime

- [ ] Eseguire `explain("executionStats")` su search, history e leaderboard.
- [ ] Confermare `IXSCAN` e assenza di `COLLSCAN` sulle query principali.
- [ ] Raccogliere baseline `collStats`, `indexSizes` e tempi di esecuzione.
- [ ] Verificare i contratti OP.GG con una sequenza rank reale e cache `SUMMONER_DATA` pulita.
- [ ] Verificare separatamente queue e participant Riot mancanti.
- [ ] Verificare il primo `202 profile_pending` e le tre Future async.

Riferimenti: [query inventory](docs/mongo/08-query-inventory.md), [space audit](docs/mongo/09-space-optimization.md), [query findings](docs/audit/05-query-contract-findings.md), [OP.GG flow](docs/audit/03-opgg-flow.md).

## P1 — Modifiche locali da revisionare

- [ ] Revisionare e validare il nuovo rendering preview OP.GG in `LeagueMessage.java`.
- [ ] Controllare il diff non committato prima del commit.
- [ ] Decidere se mantenere il TODO dell'aggregazione build in `MongoDB.java` oppure implementarlo.
- [ ] Valutare un job interno/scheduler parametrico per pre-generare la matrice champion stats da `patch + queue`.

## P1 — Accesso API e rate limit

- [ ] Implementare una session key temporanea per il frontend, con rate limit standard e scadenza per inattività.
- [ ] Implementare una developer key con quota e rate limit dedicati.
- [ ] Implementare una god key senza rate limit, riservata al proprietario e mai esposta al frontend.

## P2 — Build e accettazione

- [ ] Ripetere la build completa quando è disponibile la configurazione Java/JDA compatibile.
- [ ] Risolvere o documentare l'errore preesistente `setAudioModuleConfig`.
- [ ] Aggiornare questo indice e i documenti di audit dopo ogni gate verificato.

## TODO secondari del bot

- [ ] Rivalutare il nome di `statisticsEnabled` in `ChannelData`.
- [ ] Verificare se il webhook esiste prima dell'uso in `ChatHandler`.
- [ ] Aggiungere lo stop dell'autoreconnect in `OmegleStop`.
- [ ] Valutare la gestione premium per il limite streamers in `TwitchMenu`.

## Regola di aggiornamento

Quando un'attività viene iniziata, spostarla nella sezione corretta o aggiungere una nota di stato e mantenere il riferimento al documento tecnico proprietario. Questo file è l'indice; non sostituisce ADR, audit o runbook.
