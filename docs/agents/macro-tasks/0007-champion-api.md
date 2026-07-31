# Macro-task 0007: champion API

## Obiettivo

Esporre statistiche champion e un unico aggregato build/stats in una response HTTP unica, senza calcoli pesanti durante la request.

## Dipendenze

- ADR-0001, ADR-0005 e ADR-0006;
- `ChampionStatsService`, `BuildService` e `ChampionDataRefreshService` esistenti;
- coda asincrona `DatabaseTracker`.

## Perimetro

- `ChampionView`;
- `ChampionController`;
- `ChampionPageService`;
- letture persistite e fallback nello stesso overload `get(filter, allowCompute)`;
- un solo `ChampionPageService.get` con `compute` interno per l’API;
- `ApiResult` e `LolApiResponses` condivisi con match e leaderboard;
- parsing dei parametri centralizzato in `LolApiParameters`;
- avvio immediato dei refresh Profile Statistics e Champion Data in `DatabaseTracker`;
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
- scansione globale in due fasi: tutti i match vengono proiettati, aggregati e rilasciati senza eventi; poi gli stessi ID vengono risolti con match context leggero ed eventi a blocchi di 100, calcolando e rilasciando ogni evento prima del successivo;
- build tramite cursor Mongo `batchSize(100)`, provider a blocchi e `BuildService` che svuota ogni batch prima del successivo;
- aggregazione nel solo match corrente e rilascio delle strutture raw dopo ogni game e dopo ogni cursor item;
- liste build indipendenti con massimo tre opzioni per categoria;
- augment aggregati per slot, con ordine conservato;
- chiavi stats Redis per champion richiesto e documenti Mongo aggregati per `filterKey`, con projection `statistics.<championId>`;
- rigenerazione dei payload JSON/BSON mancanti o corrotti.
- matrice champion stats generata esclusivamente da `patch + queue`, con tutte le
  regioni attive e tutte le soglie rank cumulative;
- la scansione base della coppia `patch + queue` è distribuita negli
  accumulatori regione/rank e persistita per ogni champion; la fase eventi usa
  gli stessi ID in batch e aggiorna solo metriche dipendenti dagli eventi;

## Invarianti

- rank assente significa tutti i rank;
- region assente significa tutte le regioni;
- queue assente significa Solo/Duo ranked;
- role incompatibile con la queue significa 400;
- dati mancanti significano 202 e accodamento immediato del refresh;
- un refresh completato senza dati persiste aggregate vuoti (`games=0` e liste
  vuote) e non riaccoda indefinitamente la stessa richiesta;
- nessuna computazione raw durante la request.
- le statistiche globali condividono `Filter.genericKey()`, mentre le build usano `Filter.toKey()` e vengono accodate indipendentemente;
- build e statistiche champion usano sempre il worker 2 e una sola sequenza FIFO; i refresh profilo usano il worker 1 e possono essere aiutati dal worker 2 soltanto quando la coda champion è vuota; la deduplicazione resta condivisa;
- per una queue delle ultime tre patch, le matrici stats vengono accodate dalla più vecchia alla più nuova e il fallback trend legge solo la proiezione partecipanti in batch da 100, senza caricare eventi;
- in test lo scheduler non parte automaticamente;
- la coda Redis dei match resta separata e invariata;
- le combinazioni già pronte vengono saltate e quelle senza match vengono
  marcate pronte con aggregate vuoti;

## Acceptance criteria

- response pronta con stats e build;
- `ChampionStatistics.filter` escluso dal JSON Spring ma mantenuto per Redis e storage interno;
- cache hit funzionante;
- refresh asincrono deduplicato e avvio immediato funzionanti;
- fallimenti che liberano il marker per un nuovo tentativo;
- verifica manuale tramite request champion e controllo della response JSON; test focalizzati Jackson e chiavi di deduplicazione inclusi.
