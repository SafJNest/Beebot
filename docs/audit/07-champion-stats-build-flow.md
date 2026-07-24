# Audit 07 — flusso statistiche globali e build champion

- Data: 2026-07-20
- Tipo: audit statico e decisione di flusso
- Stato: decisione approvata, implementazione successiva
- Scope: `ChampionPageService`, `ChampionDataRefreshService`, `ChampionStatsService`, `BuildService` e `Tracker`

## Decisione

Adottiamo il flusso globale per le statistiche e lazy per le build.

Le statistiche sono condivise da tutti i champion dello stesso filtro globale:

```text
patch + queue + rank + region + lane
```

La build resta specifica per champion:

```text
patch + queue + rank + region + lane + champion
```

Il calcolo iniziale può essere lento, ma viene eseguito una sola volta per filtro globale. I risultati vengono poi riutilizzati da tutte le pagine champion compatibili.

## Flusso target

### Prima richiesta: Thresh

```text
GET champion/Thresh
  ├─ verifica statistiche globali
  │    └─ mancanti → avvia un solo global-stats job
  └─ verifica build Thresh
       └─ mancante → avvia build-Thresh job

risposta HTTP: 202 pending
```

I due job possono partire in parallelo. La pagina diventa pronta solo quando sono disponibili sia le statistiche globali per Thresh sia la build di Thresh.

Il job globale:

1. legge una sola volta tutti i match del filtro globale;
2. calcola le statistiche di tutti i champion presenti;
3. persiste un `ChampionStatistics` per champion;
4. marca il filtro globale come pronto solo dopo il completamento di tutte le scritture.

### Richiesta successiva: Jhin

```text
GET champion/Jhin
  ├─ statistiche globali già pronte
  └─ build Jhin mancante → avvia solo build-Jhin job

risposta HTTP: 202 pending
```

Non deve essere eseguita una seconda scansione globale dei match.

### Richieste concorrenti

Thresh e Jhin richiesti contemporaneamente devono produrre:

```text
1 global-stats job
1 build-Thresh job
1 build-Jhin job
```

Non sono ammessi due calcoli globali identici.

## Evidenze nel codice attuale

`ChampionPageService` non calcola statistiche durante la request: se stats o build mancano, restituisce `PENDING` e chiama `Tracker.startChampionData`.

`ChampionDataRefreshService.refresh(filter)` calcola la build usando il filtro con champion, ma costruisce `statsFilter` senza champion e quindi ricalcola tutte le statistiche del filtro globale a ogni refresh.

Il marker attuale `CHAMPION_DATA_RUNNING` usa `filter.toKey()`, che include anche il champion. Di conseguenza due champion diversi possono avviare due refresh globali duplicati.

`ChampionStatsService.compute` esegue una scansione a batch dei match, aggrega overview, lane, matchup, synergy, metriche e power curve. Quando il filtro è sul patch corrente, esegue inoltre una seconda scansione del patch precedente per il trend.

## Ownership e chiavi di deduplicazione

L’ownership deve essere separata:

| Risorsa | Owner | Chiave di deduplicazione |
|---|---|---|
| statistiche globali | `ChampionDataRefreshService` / `ChampionStatsService` | `global-stats:{patch}:{queue}:{rank}:{region}:{lane}` |
| build champion | `BuildService` | `build:{patch}:{queue}:{rank}:{region}:{lane}:{champion}` |
| pagina HTTP | `ChampionPageService` | chiave pagina esistente |

Il marker del job globale non deve usare la chiave pagina né la chiave completa del champion. Il marker della build deve invece restare specifico per champion.

Lo stato `READY` del globale deve essere scritto solo dopo il salvataggio completo degli aggregati. In caso di errore il marker in-flight deve essere rimosso, così una richiesta successiva può ritentare il calcolo.

## Regole di lettura

1. Cache page pronta: restituire la pagina.
2. Stats globali pronte e build pronta: costruire la pagina e restituire `READY`.
3. Stats globali mancanti: avviare il global-stats job se non già in esecuzione.
4. Build mancante: avviare il build job del champion se non già in esecuzione.
5. Se una delle due risorse manca: restituire `PENDING` senza calcolo raw nella request.

La lettura di un champion non deve mai invocare direttamente il recompute globale in modo sincrono.

## Benefici attesi

- una sola scansione globale per filtro condiviso;
- nessuna duplicazione quando vengono aperte pagine di champion diversi;
- build calcolate solo quando richieste;
- riuso immediato delle statistiche per matchup, trend e overview;
- comportamento HTTP invariato: `READY` quando completo, `PENDING` durante la generazione;
- possibilità di eseguire il refresh globale preventivamente tramite scheduler.

## Acceptance criteria

- Prima richiesta Thresh: un global-stats job e un build-Thresh job.
- Seconda richiesta Jhin con stesso filtro globale: solo build-Jhin job.
- Thresh e Jhin concorrenti: un solo global-stats job complessivo.
- Nessun calcolo raw durante una request HTTP.
- Un errore globale libera il marker e consente un nuovo tentativo.
- Gli aggregati globali vengono persistiti per tutti i champion prodotti dal job.
- Le route e i modelli HTTP canonici non cambiano.
- L’instrumentation Mongo conferma l’assenza di scansioni globali duplicate.

## Fuori scope

Questo audit non modifica ancora il codice, non decide la strategia di prewarming dello scheduler e non sostituisce il successivo audit `explain("executionStats")` sugli indici della collection `match`.
