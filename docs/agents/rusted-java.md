# Rusted Java

## Obiettivo

Questa guida definisce come un agente deve analizzare, modificare e verificare
la pipeline di aggregazione `ChampionStatistics` senza cambiare il contratto
pubblico, il risultato numerico o la forma persistita. Il nome "Rusted Java"
indica un lavoro di riduzione della memoria in Java esistente: si interviene
per piccoli passi, si misura ogni fase e non si sostituisce un meccanismo
stabile con una riscrittura teorica.

La guida non autorizza automaticamente modifiche al codice. Ogni agente deve
seguire l'ordine di lettura e fermarsi davanti a un conflitto con un ADR, un
contratto API, una chiave persistita o un risultato non dimostrabilmente
equivalente.

## Autorita e ordine di lettura

1. `AGENTS.md`;
2. `docs/architecture/README.md`;
3. ADR-0012 e le decisioni accettate applicabili;
4. `docs/agents/macro-tasks/0007-champion-api.md`;
5. questa guida;
6. codice e test correnti.

In caso di conflitto prevale l'ordine precedente. Un agente non modifica ADR,
macro-task di un altro owner, API, DTO o schema Mongo per sbloccare il lavoro:
registra il conflitto e lo passa al main agent.

## Invarianti non negoziabili

- `Filter.genericKey()`, `Filter.toKey()` e `Filter.toStateKey()` restano
  invariati; includono identita con compatibilita gia persistita.
- `champion_stats` mantiene `_id = filterKey`, `filterKey`, `ready` e
  `statistics.<championId>`; build, cache Redis e documenti legacy restano
  compatibili.
- HTTP, DTO, JSON Spring, codici `200`/`202`/`206`/`404`, filtri disponibili,
  ready state, TTL e invalidazioni restano invariati.
- Queue, patch, region, rank, comportamento del rank e lane mantengono la
  semantica attuale. La matrice costruisce soglie rank cumulative: non la si
  segmenta per rank.
- Formula, ordine di operazioni delle metriche e precisione numerica restano
  invariati. In particolare non trasformare una media di rapporti in un
  rapporto di somme.
- Mongo continua a essere il proprietario della persistenza e Redis della
  cache. Nessun analyzer chiama Riot, esegue lavoro nella request HTTP o
  introduce dual-write.
- Un risultato vuoto completato viene persistito con `ready=true`; un errore
  non puo pubblicare un risultato parziale come pronto.

`rankBehavior` non e incluso in `genericKey()`. Questo e un limite di
compatibilita da documentare e non da correggere localmente: la matrice usa
solo `GREATER_OR_EQUAL`; aggiungere il campo alla chiave richiede una decisione
esplicita di migrazione, cache e documenti gia presenti.

## Flusso reale da ricostruire con CodeGraph

Prima di ogni modifica usare CodeGraph, con indice aggiornato, per ricostruire
il flusso completo e la blast radius dei simboli interessati.

```text
DatabaseTracker.enqueueChampionStatsMatrix
  -> ChampionService.refreshStatisticsMatrix
  -> matrixFilters / missingMatrixFilters
  -> ChampionAnalyzer.recomputeMatrix
  -> ChampionStatsProvider.forEachMatchWithBuild
  -> MongoDB.forEachChampionRawMatchWithBuild
  -> parse match e fan-out sugli accumulatori
  -> MongoDB.forEachChampionRawMatchEventBatch
  -> parse eventi e metriche dipendenti dagli eventi
  -> assemble + trend
  -> MongoDB.upsertChampionStatistics / upsertChampionBuilds
  -> Redis invalidation del chiamante
  -> rilascio delle strutture locali
```

Punti da verificare con CodeGraph:

- entry point, caller e deduplicazione nel worker `CHAMPION_WORKER`;
- identita di storage e cache dei filtri;
- `championMatchFilter`, proiezioni Mongo e batch size;
- fan-out di region/rank/lane e match tra filtro e raw match;
- tutte le metriche base/eventi, matchup, synergy, power curve e trend;
- writer Mongo, acknowledgement e consumer asincroni;
- test che coprono filtro, documento, matrice, tracker e JSON.

Non sostituire questa ricostruzione con una ricerca testuale. Usare
`codegraph_context`/`codegraph_explore`/`codegraph_trace` o l'equivalente CLI
per i collegamenti strutturali; usare `rg` solo per testo, documentazione o
stringhe specifiche.

## Proprietari e file di lavoro

| Area | Proprietario | File principali |
|---|---|---|
| Scheduling e deduplicazione | `DatabaseTracker` | `lol/tracker/DatabaseTracker.java` |
| Filtri matrice e cache pagina | `ChampionService` | `lol/service/ChampionService.java` |
| Analisi, accumulatori e assemble | `ChampionAnalyzer` | `lol/service/ChampionAnalyzer.java` |
| Materializzazione raw | `ChampionStatsProvider` | `lol/champion/ChampionStatsProvider.java` |
| Query, cursori, eventi e write | `MongoDB` | `nosql/MongoDB.java` |
| Build condivise con la matrice | `ChampionBuildEngine` | `lol/service/ChampionBuildEngine.java` |
| Contratto persistito/API | ADR-0012 e macro-task 0007 | `docs/architecture`, `docs/agents/macro-tasks` |

Un agente modifica solo il proprietario assegnato e le sue prove/documentazioni
dirette. Le modifiche multi-owner richiedono un piano e handoff esplicito.

## Procedimento obbligatorio

### Step 0 - Baseline

1. Controllare `git status --short` e non sovrascrivere modifiche estranee.
2. Controllare `codegraph status`; se l'indice non e aggiornato, sincronizzarlo
   prima di trarre conclusioni strutturali.
3. Annotare toolchain disponibile, comando di test, stato dei test e dati Mongo
   usati per ogni misura. Java 25 e Maven sono richiesti per dichiarare una
   verifica Java completa.
4. Salvare un campione di riferimento con filtro, conteggi, JSON/API e
   documento Mongo prima di una modifica comportamentale interna.

### Step 1 - Identita e contratti

1. Elencare per ogni `Filter` usato i campi che cambiano il risultato.
2. Tracciare ogni chiave verso Redis, deduplicazione e Mongo.
3. Separare identita pubblica, identita persistita e chiavi puramente interne.
4. Se due filtri semanticamente diversi condividono una chiave persistita,
   fermarsi: non cambiare la chiave senza un piano di compatibilita e migrazione.

### Step 2 - Profilare il flusso reale

Misurare e riportare separatamente:

- scan/query Mongo e winning plan;
- materializzazione match;
- lettura e decode `match_events`;
- parse JSON;
- aggregazione base;
- aggregazione eventi;
- trend fallback;
- assemble e serializzazione;
- write Mongo acknowledged;
- heap/high-water mark e cardinalita degli accumulatori.

Un `IXSCAN` da solo non prova che il job sia veloce: lookup, payload evento,
parse, pairing dei partecipanti e aggregazione possono essere il costo reale.

### Step 3 - Segmentare senza cambiare il risultato

L'ordine ammesso e:

```text
filtri globali
  -> base
  -> eventi
  -> trend
  -> assemble
  -> persistenza acknowledged
  -> release

prima regione attiva
  -> stesso ciclo completo

seconda regione attiva
  -> stesso ciclo completo
```

Regole:

- il segmento globale usa `region = null`; ogni altro segmento usa una sola
  regione attiva;
- ogni segmento conserva tutti i rank cumulativi e tutte le lane valide;
- l'ordine dei segmenti deve coincidere con quello gia prodotto da
  `matrixFilters` (globale, poi regioni);
- build regionali entrano nel segmento della propria regione; build globali nel
  segmento globale;
- un segmento precedente persistito resta valido se un segmento successivo
  fallisce;
- un filtro del segmento in errore non viene marcato pronto e deve poter essere
  rigenerato nel job successivo.

### Step 4 - Eliminare la lista globale di match ID

La fase base e quella eventi richiedono lo stesso universo di match, ma non
richiedono che tutti gli ID restino in heap. Il percorso ammesso e:

1. prima scansione: query filtrata + proiezione base; aggregare e rilasciare
   ogni match;
2. seconda scansione con gli stessi criteri e stesso batching: conservare al
   massimo un batch di ID;
3. per il batch, caricare i match raw necessari e `match_events` con `$in` solo
   sul batch; inviare soltanto le coppie realmente disponibili;
4. svuotare documenti, mappe di join e batch in `finally` prima del batch
   successivo.

Non usare `$in` globale, collection temporanee o spool persistente senza una
misura che dimostri che il nuovo costo e inferiore e senza un piano di recovery.
Mantenere l'API interna basata su lista se altri consumer la usano, ma la
matrice non deve costruirla.

### Step 5 - Rilasciare gli accumulatori

Dopo `upsert`/`replaceOne` acknowledged, in questo ordine:

1. scartare il `Document` e qualunque rappresentazione JSON/BSON temporanea;
2. svuotare il risultato assemblato quando non e piu necessario;
3. svuotare mappe, liste e array degli accumulatori raw;
4. rimuovere l'accumulatore dal contenitore del segmento;
5. svuotare trend e accumulatori build associati;
6. assicurare lo stesso cleanup in `finally` per eccezioni di query, parse,
   evento, trend, serializer o write.

Prima di liberare un valore, usare CodeGraph per verificare che nessuna lambda,
future, serializer o callback asincrona lo usi dopo il write. Non usare
`System.gc()` come prova di rilascio.

### Step 6 - Mappe ordinate, FastUtil e chiavi compatte

Applicare solo dopo aver completato e misurato gli step precedenti.

1. Per ogni `LinkedHashMap`, indicare se l'insertion order arriva in JSON/BSON,
   API o in una selezione finale. Se non e dimostrabile, conservarla.
2. Aggiungere FastUtil come dipendenza diretta solo se viene usata nel codice
   modificato.
3. Usare mappe primitive per lookup ad alta cardinalita; non sostituire una
   `HashMap<Long, V>` con una variante che continua a creare box e nodi.
4. Una chiave `long` deve essere reversibile, collision-free, con codici lane
   espliciti e mai basati su `enum.ordinal()`.
5. Un mapping `championId reale <-> indice compatto` e un dettaglio effimero
   del job: snapshot immutabile, inverso disponibile durante assemble, mai
   serializzato e mai usato come identita di dominio.
6. Ricostruire `MatchupKey`, `LaneSynergy` e champion ID reali soltanto in
   assemble. Preservare eventuali ordinamenti finali esistenti.

Non convertire milioni di `double[]` in milioni di normali oggetti Java solo
per rendere i campi piu descrittivi. Un accumulatore tipizzato o a colonne e
ammesso soltanto dopo una misura che dimostri che FastUtil e segmentazione non
bastano e dopo test di equivalenza numerica.

### Step 7 - Persistenza e serializzazione

Il percorso attuale `POJO -> JSON -> Document -> BSON` puo creare copie, ma non
si sostituisce senza una prova di parita. Serializer custom, map key e `null`
possono cambiare forma anche se il modello Java sembra uguale.

Prima di introdurre codec diretti, POJO codec, bulk write o un writer BSON:

- confrontare documento BSON, JSON storage e risposta API del campione;
- confermare schema, campi null, chiavi map e ordine osservabile;
- confermare acknowledgement, retry e ready state;
- aggiornare documentazione e test Mongo nello stesso task.

Senza questa prova, mantenere il serializer corrente.

### Step 8 - Query e cursori

Ogni variazione a projection, sort, batch size, query regionale o indice deve
mostrare `explain("executionStats")` rappresentativo: `winningPlan`, indice,
`totalKeysExamined`, `totalDocsExamined` e `executionTimeMillis`.

Non spostare automaticamente pairing partecipanti, eventi o matchup in Mongo:
`$unwind` e aggregazioni evento possono aumentare materializzazione e
complessita. I cursori lunghi devono essere chiusi con `try-with-resources` e
ogni documento deve essere rilasciato nel suo ciclo.

## Cosa non fare senza approvazione esplicita

- cambiare chiavi `Filter`, Redis, Mongo `_id` o `filterKey`;
- cambiare schema, API, DTO, ready state, filtri, TTL o invalidazioni;
- segmentare per rank o cambiare rank cumulativo in rank esatto;
- usare ordinali enum nelle chiavi persistite o compatte;
- introdurre `$in` enorme, collection temporanee, cache parallele, service
  facades, DTO duplicati o librerie non necessarie;
- sostituire tutte le mappe ordinate indiscriminatamente;
- dichiarare risolto un problema heap senza heap/profile o senza verificare che
  i riferimenti siano effettivamente rilasciati;
- eseguire build concorrenti che competono per lock o risultati di verifica.

## Piano di test e gate

### Test unitari richiesti

- generazione matrice: globale prima delle regioni, tutte le regioni attive,
  rank cumulativi e lane solo per queue che le supportano;
- `matchesMatrixFilter`: regione, `GREATER_OR_EQUAL`, `EXACT` e rank mancante;
- due scansioni eventi: piu batch, match senza evento, evento senza match,
  cleanup dopo eccezione;
- chiavi compatte: champion ID non consecutivi, tutte le lane supportate, lane
  assente, decode e collisioni;
- assemble: matchup, synergy, metriche, power curve e trend identici alla
  fixture di riferimento;
- documento Mongo: `_id`, `filterKey`, `ready`, `statistics.<championId>`,
  ready-empty e compatibilita legacy;
- endpoint/cache: stessi payload e stessi `200`/`202` per dati pronti, mancanti
  e completati vuoti.

### Verifica integrata richiesta

Con Maven e Java 25, eseguire test focalizzati e un refresh Mongo
rappresentativo. Confrontare prima/dopo:

- conteggi e tutti i campi numerici;
- JSON/API e BSON persistito;
- key Redis/Mongo e marker `ready`;
- query plan e timing per fase;
- high-water mark heap e cardinalita massima degli accumulatori.

Se Maven o Java 25 non sono disponibili, riportare chiaramente che i controlli
statici non sostituiscono la compilazione o la verifica Mongo live.

## Handoff obbligatorio

Ogni agente consegna al main agent:

1. obiettivo e segmento esatto analizzato/modificato;
2. output CodeGraph: entry point, caller, callee e blast radius;
3. file modificati e motivazione;
4. invarianti controllati, inclusi API, schema, chiavi e ready state;
5. test/comandi eseguiti con risultato e limiti della toolchain;
6. confronto prima/dopo di risultato, documento e memoria;
7. rischio residuo, dati mancanti e decisioni che richiedono approvazione.

Il main agent approva soltanto quando nessun contratto e cambiato, i risultati
sono equivalenti e la documentazione interessata e sincronizzata.
