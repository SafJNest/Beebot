# Macro-task 0007: champion API

## Obiettivo

Esporre statistiche champion e un unico aggregato build/stats in una response HTTP unica, senza calcoli pesanti durante la request.

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
- avvio immediato dei refresh Profile Statistics e Champion Data in `Tracker`;
- avvio esplicito e idempotente di `TrackerScheduler`;
- invalidazione cache dopo refresh;
- build aggregate persistito senza selezione automatica most-used/highest-winrate;
- documentazione del contratto;
- mantenimento temporaneo dei log diagnostici durante la verifica manuale; la rimozione resta manuale.

## Perimetro aggiuntivo del contratto aggregato

- overview con KDA, CS/min, gold/min e damage profile nullable;
- power curve e trend patch quando i dati sono disponibili;
- tutti i matchup validi con metriche @15/eventi disponibili;
- tutte le lane synergy valide;
- caricamento keyset dei match in batch sequenziali da 1.000, con metadata/events, participant e summoner separati;
- merge nel solo batch corrente e rilascio delle strutture raw dopo ogni game e dopo ogni batch;
- liste build indipendenti con massimo tre opzioni per categoria;
- augment aggregati per slot, con ordine conservato;
- chiavi stats Redis/DB con lane quando il ruolo è specificato;
- rigenerazione dei payload Kryo incompatibili.

## Invarianti

- rank assente significa tutti i rank;
- region assente significa tutte le regioni;
- queue assente significa Solo/Duo ranked;
- role incompatibile con la queue significa 400;
- dati mancanti significano 202 e avvio immediato del refresh;
- nessuna computazione raw durante la request.
- in test lo scheduler non parte automaticamente;
- la coda Redis dei match resta separata e invariata;

## Acceptance criteria

- response pronta con stats e build;
- `ChampionStatistics.filter` escluso dal JSON Spring ma mantenuto per Redis/Kryo;
- cache hit funzionante;
- refresh asincrono deduplicato e avvio immediato funzionanti;
- fallimenti che liberano il marker per un nuovo tentativo;
- verifica manuale tramite request champion e controllo della response JSON; i test automatici restano fuori piano.
