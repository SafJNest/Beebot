# Roadmap

> Guida operativa per nuove feature: [`docs/HANDBOOK.md`](docs/HANDBOOK.md) (§5 TOC + §6 reference indici/peso/RAM + §7 checklist).

Roadmap operativa del perimetro LoL (stato 2026-08-31: runtime Mongo-only, code `QueueHandler`/`RiotScheduler`/`ComputeScheduler`/`SyncScheduler` via ADR-0014). Il dettaglio delle decisioni resta negli ADR e in `docs/mongo` / `docs/audit`.

## Posizione attuale

Il branch `lol-api` ha definito il perimetro API LoL. Il branch `lol-api-mongo` ha portato il runtime LoL verso MongoDB e ha lasciato MariaDB nel solo percorso di migration. La fase attuale è la rifinitura del cutover LoL e della persistenza Mongo comune.

## Fase 1 — chiudere il cutover LoL Mongo

- [x] Portare tutti i consumer LoL su boundary `SummonerService`/`RankService`/`MasteryService`/`ProfileService`/`MatchService` → MongoDB/Riot (ex `LeagueService`, rimosso via ADR-0011/0014).
- [ ] Mantenere `LeagueDB` confinato alle query utilizzate da `MongoMigration`.
- [ ] Completare le scritture runtime con upsert tipizzati e invalidazione delle cache correlate.
- [x] Uniformare account, tracking, profile, leaderboard, match, participant, rank, mastery e champion aggregate (verifica `explain` ancora P1).
- [ ] Consolidare la gestione degli errori Mongo senza trasformare gli errori in dati vuoti.

Riferimenti: [ADR-0009](docs/architecture/adr/0009-mongo-persistence-and-migration.md), [write path](docs/mongo/04-write-path-and-refactor.md).

## Fase 2 — rendere operativo il backfill raw

- [ ] Eseguire il runner nell’ordine `summoners` → `match` → `match_events`.
- [ ] Conservare checkpoint e high-water mark per ogni fase e run.
- [ ] Mantenere i batch limitati e gli upsert ripetibili senza duplicare documenti.
- [ ] Gestire documenti già presenti e documenti mancanti senza riletture inutili da MariaDB.
- [ ] Rigenerare successivamente statistiche profilo, statistiche champion e build tramite i flussi applicativi.

Riferimento: [data migration and cutover](docs/mongo/05-data-migration-and-cutover.md).

## Fase 3 — completare gli aggregate LoL

- [ ] Completare l’aggregazione build in `MongoDB` usando `buildPath`.
- [ ] Includere nel percorso build le configurazioni rune ancora escluse.
- [ ] Mantenere separati i dati raw (`summoner`, `match`, `match_events`) dagli aggregate derivati.
- [ ] Rifinire la generazione lazy delle statistiche globali e delle build per champion.

Riferimenti: [champion stats/build flow](docs/audit/07-champion-stats-build-flow.md), [space optimization](docs/mongo/09-space-optimization.md).

## Fase 4 — rifinitura dei consumer e dell’esperienza LoL

- [ ] Chiudere il rendering OP.GG per tutti i tipi di queue e le varianti di participant.
- [ ] Gestire in modo coerente queue non standard e participant Riot non risolti.
- [x] Uniformare account, tracking e ownership `userId` sul nuovo modello Mongo.
- [ ] Mantenere le cache profile, advanced overview e OP.GG coerenti dopo gli aggiornamenti Tracker.
- [ ] Conservare il comportamento asincrono per profile, champion, leaderboard e match detail.

Riferimenti: [OP.GG flow](docs/audit/03-opgg-flow.md), [all LoL commands and Tracker](docs/audit/06-all-lol-commands-tracker.md).

## Fase 5 — storage e operatività Mongo

- [ ] Applicare gli indici dichiarati per search, tracking, match history, champion e leaderboard.
- [ ] Completare la configurazione della collection `match_events` con compressione WiredTiger Zstandard.
- [ ] Mantenere la separazione tra database applicativo e database di ambiente tramite configurazione.
- [ ] Conservare cleanup dei payload legacy come operazione manuale, fuori dal runtime.

Riferimenti: [Mongo README](docs/mongo/README.md), [space optimization](docs/mongo/09-space-optimization.md).

## Dopo il cutover LoL

- [ ] Definire ADR separati per Berbit, Spotify e Website.
- [ ] Riutilizzare l’infrastruttura Mongo comune solo dopo la chiusura del perimetro LoL.
- [ ] Valutare in una fase distinta `custom_build` e `summoner.metrics`.

## Fuori roadmap corrente

- `summoner.metrics` e `summoner_metric`.
- Custom builds e relative query autocomplete.
- Migrazione runtime degli altri domini MariaDB.
- Introduzione di DTO Mongo duplicati, outbox, proxy dual-write o fallback MariaDB.
