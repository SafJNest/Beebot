# Audit 07 — flusso statistiche globali e build champion

- Data: 2026-07-20
- Tipo: audit statico e decisione di flusso
- Stato: implementato staticamente, validazione Mongo runtime ancora necessaria
- Scope: `ChampionPageService`, `ChampionDataRefreshService`, `ChampionStatsService`, `BuildService` e `DatabaseTracker`

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

## Evidenze nel codice implementato

`ChampionPageService` non calcola statistiche durante la request: se stats o build mancano, restituisce `PENDING` e chiama `DatabaseTracker.startChampionData`.

`ChampionDataRefreshService` espone ora refresh separati: `refreshBuild(filter)` mantiene il champion, mentre `refreshStats(filter)` costruisce il filtro globale senza champion.

`DatabaseTracker` usa due chiavi distinte: `champion-stats:<filter.genericKey()>` e `champion-build:<filter.toKey()>`. Prima di accodare il job globale viene verificata la presenza della statistica richiesta tramite cache e query Mongo su `filterKey + championId`. Due champion diversi condividono quindi una sola scansione globale, ma mantengono build indipendenti. I due job vengono accodati separatamente e possono essere eseguiti in parallelo dai due worker.

Al termine di una scansione globale riuscita, `CHAMPION_STATS_COMPLETED` conserva il filtro globale già elaborato, anche quando il risultato è vuoto o non contiene il champion richiesto. Questo impedisce che il polling di una pagina senza dati rilanci la stessa scansione. In caso di eccezione lo stato `COMPLETED` non viene scritto e la chiave in-flight viene rimossa, quindi il filtro resta ritentabile. Il refresh completo accodato dallo scheduler usa la chiave `champion-data-refresh:<patch>`, resetta lo stato nel worker e non può sovrapporsi a un altro refresh dello stesso patch.

`ChampionStatsService.compute` esegue una scansione streaming dei match, aggrega overview, lane, matchup, synergy, metriche e power curve e persiste tutti i champion prodotti dalla stessa scansione. Per il trend usa prima le statistiche persistite del patch precedente; la scansione raw precedente resta solo il fallback quando il dato persistito è incompleto.

La build non materializza più la lista completa di `QueryRecord`: `MongoDB.forEachChampionBuildRaw` mantiene il cursor aperto e consegna un record alla volta a `BuildService`. Anche le statistiche globali usano una sola aggregation cursor con `$lookup` su `match_events` e `batchSize(1)`: il provider converte e parsea un solo match per volta, poi svuota i riferimenti Java a match ed eventi. Il fallback trend precedente mantiene batch bounded da 100.

Il flusso registra tempi e contatori per lettura streaming dei match, lettura eventi, materializzazione raw, parsing, aggregazione, trend, assemblaggio e coda di persistenza. Gli indici Mongo non sono stati modificati: restano da valutare con `explain("executionStats")` su dati rappresentativi.

### Query di benchmark Mongo

Con una connessione al database di test e valori rappresentativi, eseguire almeno:

```javascript
const base = {
  queue: "TEAM_BUILDER_RANKED_SOLO",
  patch: /^15\.14(?:\.|$)/,
  rank: { $in: ["EMERALD_IV", "EMERALD_III", "EMERALD_II", "EMERALD_I"] },
  leagueShard: "EUW1"
};

db.match.countDocuments({
  ...base,
  participants: { $elemMatch: { champion: 412, lane: "UTILITY" } }
});

db.match.find(base)
  .sort({ _id: 1 })
  .limit(1000)
  .project({ _id: 1 })
  .explain("executionStats");

db.match.find({
  ...base,
  participants: { $elemMatch: { champion: 412, lane: "UTILITY" } }
})
  .project({
    _id: 1,
    "participants.champion": 1,
    "participants.lane": 1,
    "participants.win": 1,
    "participants.item0": 1,
    "participants.item1": 1,
    "participants.item2": 1,
    "participants.item3": 1,
    "participants.item4": 1,
    "participants.item5": 1
  })
  .limit(1000)
  .explain("executionStats");
```

Confrontare `executionTimeMillis`, `totalKeysExamined`, `totalDocsExamined`, `nReturned`, `winningPlan` e il nome dell’indice. La misura va ripetuta prima e dopo su stesso filtro e dataset; senza Mongo configurato in questo workspace non è stata eseguita in locale.

## Ownership e chiavi di deduplicazione

L’ownership deve essere separata:

| Risorsa | Owner | Chiave di deduplicazione |
|---|---|---|
| statistiche globali | `ChampionDataRefreshService` / `ChampionStatsService` | `global-stats:{patch}:{queue}:{rank}:{region}:{lane}` |
| build champion | `BuildService` | `build:{patch}:{queue}:{rank}:{region}:{lane}:{champion}` |
| pagina HTTP | `ChampionPageService` | chiave pagina esistente |

Il marker del job globale usa `Filter.genericKey()` e non deve usare la chiave pagina né la chiave completa del champion. Il marker della build resta specifico per champion con `Filter.toKey()`.

Lo stato `READY` del globale deve essere scritto solo dopo il salvataggio completo degli aggregati. In caso di errore il marker in-flight deve essere rimosso, così una richiesta successiva può ritentare il calcolo.

## Regole di lettura

1. Cache page pronta: restituire la pagina.
2. Stats globali pronte e build pronta: costruire la pagina e restituire `READY`.
3. Stats globali mancanti: verificare prima cache/Mongo per il champion richiesto; avviare il global-stats job solo se il filtro non è già in esecuzione o completato.
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
- Una scansione globale conclusa, anche senza righe per il champion richiesto, non viene rilanciata dai polling successivi.
- Gli aggregati globali vengono persistiti per tutti i champion prodotti dal job.
- Le route e i modelli HTTP canonici non cambiano.
- I log di fase consentono di misurare l’assenza di scansioni globali duplicate; la conferma Mongo runtime e gli `explain("executionStats")` restano una verifica operativa.

## Fuori scope

La strategia di prewarming dello scheduler e l’audit `explain("executionStats")` sugli indici della collection `match` restano fuori scope operativo di questo fix.
