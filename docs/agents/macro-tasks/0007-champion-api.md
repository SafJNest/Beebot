# Macro-task 0007: champion API

## Obiettivo

Esporre statistiche champion e most common build in una response HTTP unica, senza calcoli pesanti durante la request.

## Dipendenze

- ADR-0001, ADR-0005 e ADR-0006;
- `ChampionStatsService`, `BuildService` e `ChampionDataRefreshService` esistenti;
- coda asincrona `Tracker`.

## Perimetro

- `ChampionView`;
- `ChampionController`;
- `ChampionPageService`;
- letture persistite e fallback nello stesso overload `get(filter, allowCompute)`;
- un solo `ChampionPageService.get` con `compute` interno per l’API;
- `ApiResult` e `LolApiResponses` condivisi con match e leaderboard;
- parsing dei parametri centralizzato in `LolApiParameters`;
- code Profile Statistics e Champion Data in `Tracker`;
- comando owner `pushqueue` per il drain manuale in test;
- avvio esplicito e idempotente di `TrackerScheduler`;
- invalidazione cache dopo refresh;
- direct persisted lookup for the most-used build before any computation;
- test e documentazione del contratto;
- rimozione dei log e commenti temporanei di debug.

## Fuori perimetro

- nuove metriche KDA, CS/min, danni o power curve;
- nuove tabelle SQL;
- modifiche al payload leaderboard;
- redesign dei modelli `Build` o `ChampionStatistics`.

## Invarianti

- rank assente significa tutti i rank;
- region assente significa tutte le regioni;
- queue assente significa Solo/Duo ranked;
- role incompatibile con la queue significa 400;
- dati mancanti significano 202 e enqueue;
- nessuna computazione raw durante la request.
- in test lo scheduler non parte automaticamente;
- `pushqueue` non elabora la coda Redis dei match.

## Acceptance criteria

- response pronta con stats e build;
- `ChampionStatistics.filter` escluso dal JSON Spring ma mantenuto per Redis/Kryo;
- cache hit funzionante;
- enqueue deduplicato;
- refresh asincrono e drain manuale funzionanti;
- retry, conteggi e concorrenza delle code verificabili tramite `QueueStatus` e `QueueDrainResult`;
- test, build e `git diff --check` completati.
