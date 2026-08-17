# Audit 07 — flusso statistiche globali e build champion

- Data: 2026-07-20
- Tipo: audit statico e decisione di flusso
- Stato: implementato staticamente, validazione Mongo runtime ancora necessaria
- Scope: `ChampionService`, `ChampionAnalyzer`, Mongo streaming e `DatabaseTracker`

## Decisione

Adottiamo il flusso globale per le statistiche e lazy per le build.

Le statistiche sono condivise da tutti i champion dello stesso filtro globale:

```text
patch + queue + rank + region + lane
```

La generazione massiva parte esclusivamente da `patch + queue`. Per ogni coppia
vengono create tutte le combinazioni tra filtro globale, regioni attive, soglie
rank cumulative (`IRON+`, `BRONZE+`, ecc.) e lane applicabili alla queue. Ogni
combinazione mantiene il proprio `Filter.genericKey()` e produce un solo
mega-documento `champion_stats` con una voce `statistics.<championId>` per ogni
champion presente. I match vengono letti una sola volta dalla query base
`patch + queue` e distribuiti nei bucket compatibili; un match di Challenger
contribuisce quindi a tutte le soglie inferiori. Le combinazioni già pronte
vengono saltate e quelle senza match ricevono `ready=true` con `statistics={}`.
I champion senza dati non generano una voce vuota nel mega-documento: il filtro
pronto resta sufficiente per restituire `200` vuoto quando il champion richiesto
non è presente.

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
  │    └─ mancanti → avvia un solo champion-stats matrix job
  └─ verifica build Thresh
       └─ mancante → avvia build-Thresh job

risposta HTTP: 202 pending
```

I due job possono partire in parallelo. La pagina diventa pronta solo quando sono disponibili sia le statistiche globali per Thresh sia la build di Thresh.

Il job globale:

1. legge una sola volta tutti i match del filtro globale;
2. calcola le statistiche di tutti i champion presenti;
3. persiste un solo documento aggregato per filtro, completo di tutti i champion;
4. scrive `ready=true` nello stesso documento solo dopo il completamento dell'accumulo.

Quando una pagina richiede una stats mancante, `DatabaseTracker` accoda la
matrice per la stessa patch e queue richiesta, ignorando il ruolo come sorgente
della matrice. La build resta un job
separato e specifico per champion.

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
1 champion-stats matrix job
1 build-Thresh job
1 build-Jhin job
```

Non sono ammessi due calcoli globali identici.

## Evidenze nel codice implementato

`ChampionService` non calcola statistiche durante la request: se stats o build mancano, restituisce `PENDING` e chiama `DatabaseTracker.startChampionData` indicando quali risorse mancano. Per una matrice in attesa, il tracker unisce le build richieste nello stesso job; dopo l'avvio, una build nuova è un job build-only deduplicato.

Quando un refresh termina correttamente senza giochi validi, non lascia più la
risorsa in stato mancante: `BuildService` persiste un aggregate build con
`games=0` e liste vuote, mentre `ChampionDataRefreshService` persiste per il
filtro un documento `champion_stats` vuoto con `ready=true`. Se un champion
valido non è presente in un filtro pronto, il read costruisce
`ChampionStatistics.empty(filter)` e restituisce `200`; `202` resta riservato
al periodo in cui il filtro non esiste o non è ancora pronto.

`ChampionDataRefreshService` espone ora refresh separati: `refreshBuild(filter)` mantiene il champion, mentre `refreshStats(filter)` costruisce il filtro globale senza champion.

`DatabaseTracker` usa due chiavi distinte: `champion-stats-matrix:<patch>:<queue>` e `champion-build:<filter.toKey()>`. Prima di accodare il job matrice viene verificata la presenza della statistica richiesta tramite cache e projection Mongo su `filterKey` e `statistics.<championId>`. Due champion diversi condividono quindi una sola scansione globale, ma mantengono build indipendenti. Il job stats usa il worker generale e il job build usa il worker dedicato: possono essere eseguiti in parallelo, mentre le build restano seriali tra loro.

Al termine della matrice, ogni combinazione riuscita viene salvata con `ready`
nel documento aggregato, anche quando non contiene match: questo impedisce che
il polling di una pagina senza dati rilanci la stessa scansione. In caso di
eccezione il documento pronto non viene scritto e la chiave in-flight viene
rimossa, quindi la combinazione resta ritentabile. Il refresh completo accodato dallo scheduler usa la chiave
`champion-data-refresh:<patch>` e non può sovrapporsi a un altro refresh dello
stesso patch.

`ChampionAnalyzer` esegue una scansione streaming dei match, aggrega overview, lane, matchup, synergy, metriche e power curve e, quando richiesta, alimenta nello stesso documento anche la build. Per il trend usa prima le statistiche persistite del patch precedente; la scansione raw precedente resta solo il fallback quando il dato persistito è incompleto.

La build non materializza più la lista completa di `QueryRecord`: `MongoDB.forEachChampionBuildRawBatch` mantiene il cursor aperto con `batchSize(100)` e il provider consegna a `BuildService` blocchi di massimo 100 record. `BuildService` aggrega e svuota ogni blocco prima di leggere il successivo; anche il blocco finale può essere parziale. Le statistiche globali usano una sola aggregation cursor con `$lookup` su `match_events` e `batchSize(1)`: il provider converte e parsea un solo match per volta, poi svuota i riferimenti Java a match ed eventi.

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
| matrice statistiche champion | `ChampionService` / `ChampionAnalyzer` | `champion-stats-matrix:<patch>:<queue>` |
| build champion | `ChampionService` / `ChampionAnalyzer` | `champion-build:<Filter.toKey()>` |
| pagina HTTP | `ChampionService` | chiave pagina esistente |

Il marker del job globale usa `Filter.genericKey()` e non deve usare la chiave pagina né la chiave completa del champion. Il marker della build resta specifico per champion con `Filter.toKey()`.

Lo stato `READY` del globale deve essere scritto solo dopo il salvataggio completo degli aggregati. In caso di errore il marker in-flight deve essere rimosso, così una richiesta successiva può ritentare il calcolo.

## Regole di lettura

1. Cache page pronta: restituire la pagina.
2. Stats globali pronte e build pronta: costruire la pagina e restituire `READY`.
3. Stats globali mancanti: verificare prima cache/Mongo per il champion richiesto; avviare il job matrice solo se la combinazione `patch + queue` non è già in esecuzione o completata.
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

- Prima richiesta Thresh: un champion-stats matrix job e un build-Thresh job.
- Seconda richiesta Jhin con stesso filtro globale: solo build-Jhin job.
- Thresh e Jhin concorrenti: un solo champion-stats matrix job complessivo.
- Nessun calcolo raw durante una request HTTP.
- Un errore globale libera il marker e consente un nuovo tentativo.
- Una scansione globale conclusa, anche senza righe per il champion richiesto, non viene rilanciata dai polling successivi.
- Gli aggregati globali vengono persistiti per tutti i champion prodotti dal job.
- La matrice genera una combinazione distinta per ogni regione attiva e soglia rank cumulativa a partire da patch e queue.
- Una singola scansione corrente alimenta tutti i bucket della matrice.
- I bucket senza match risultano pronti tramite marker persistente e non riavviano il calcolo.
- Le route e i modelli HTTP canonici non cambiano.
- I log di fase consentono di misurare l’assenza di scansioni globali duplicate; la conferma Mongo runtime e gli `explain("executionStats")` restano una verifica operativa.

## Fuori scope

La strategia di prewarming dello scheduler e l’audit `explain("executionStats")` sugli indici della collection `match` restano fuori scope operativo di questo fix.

## TODO futuro — build da timeline e ottimizzazione Rusted Java

Questo lavoro è separato dal refresh delle statistiche e non deve trasformare il
job stats in un calcolo congiunto stats+build. La build deve essere calcolata in
un momento distinto, con il proprio job, accumulatore, lifecycle e misura delle
risorse. Il calcolo deve usare gli `events` della partita come unica sorgente
dell’ordine d’acquisto; i campi derivati di `participants` (`starterItems`,
`buildPath`, `boots`, `supportItem`) non devono essere una seconda copia della
stessa informazione.

### Obiettivo funzionale

- Selezionare solo i game compatibili con il `Filter` della build e con il
  champion richiesto.
- Caricare gli `events` del game in streaming, senza materializzare la lista
  completa degli eventi in memoria.
- Eseguire il testing iniziale e la validazione del parser sulla collection
  Mongo `lol_testing`; il dataset production non è una prova valida per il
  primo ciclo di equivalenza.
- Filtrare gli eventi del participant champion interessato.
- Ordinare o rispettare l’ordine temporale degli acquisti e classificare ogni
  item secondo il suo ruolo nella build.
- Usare `skill_events` per ricostruire l’ordine delle spell e il timing di ogni
  level-up dello slot; lo slot resta la rappresentazione canonica e il mapping
  Q/W/E/R viene applicato solo in proiezione.
- Usare `item_events` per ricostruire la progressione reale degli item del
  participant, interpretando `ITEM_PURCHASED`, `ITEM_DESTROYED`, `ITEM_SOLD` e
  `ITEM_UNDO` insieme. I componenti consumati per una trasformazione non devono
  diventare item finali della build.
- Registrare per ogni item finale il timestamp di acquisizione e persistere la
  sua media di acquisto in minuti; il timestamp in millisecondi va convertito
  solo al confine dell’aggregazione/proiezione.
- Conservare il comportamento aggregato attuale dove è ancora valido:
  starter, boots, support item, core, slot, rune, spell order, prismatic e
  augment.
- Gestire correttamente il boots base nello starter e il boots completo finale:
  `1001` può appartenere allo starter, ma non deve essere salvato come boots
  finale; in ARAM/Arena un boots completo può appartenere anche allo starter.
- Mantenere invariati i risultati osservabili non esplicitamente rimossi e
  aggiornare nello stesso lavoro il contratto persistito/API se vengono tolti
  campi pubblici.

### Persistenza minima

- Per le opzioni di presenza persistere il solo totale `matches`: non salvare
  `wins`, `winrate` o `pickrate` quando sono valori ricostruibili o non richiesti
  dal nuovo contratto.
- Rimuovere i campi che possono essere ricostruiti direttamente dal totale o da
  altri valori già persistiti, inclusi i ranking/percentuali duplicate.
- Conservare invece medie e metriche non ricostruibili dal solo totale, come gli
  `avg` già previsti dal contratto, inclusi il minuto medio di acquisto degli
  item e il timing medio dei level-up delle spell. Il contratto dovrà esporre
  l’`avgPurchaseMinute` per le opzioni item e una sequenza di timing medi
  allineata all’ordine spell per le opzioni skill.
- Verificare esplicitamente l’impatto su `Build.Option`, `Build.CoreBuildOption`,
  JSON/BSON, Redis, API, test e documentazione prima di eliminare un campo.

### Piano Rusted Java

1. Misurare separatamente query Mongo, lettura/decode `match_events`, parsing
   JSON, classificazione item, aggregazione, serializzazione e write.
2. Usare cursor e batch piccoli; processare un game/evento alla volta e
   rilasciare subito JSON, mappe temporanee e riferimenti al game.
3. Evitare `QueryRecord` o liste globali di eventi; passare al parser solo il
   contesto minimo del participant richiesto.
4. Usare FastUtil per mappe e chiavi ad alta cardinalità degli accumulatori,
   con chiavi compatte reversibili e collision-free; non usare ordinali enum e
   non cambiare le chiavi pubbliche `Filter`.
5. Conservare `LinkedHashMap` o una struttura d’ordine separata quando l’ordine
   entra nel JSON/BSON o in una selezione osservabile.
6. Ridurre box, copie di stringhe e oggetti temporanei solo dopo aver
   identificato il punto caldo con una misura.
7. Svuotare gli accumulatori dopo la persistenza acknowledged e garantire lo
   stesso cleanup in `finally` per parse, query e write falliti.

### Sequenza di implementazione

1. Definire il parser timeline e testare la classificazione temporale degli
   item e delle spell senza cambiare ancora il contratto persistito.
2. Aggiungere il nuovo build job separato e confrontare il risultato con il
   calcolo attuale su un campione identico.
3. Misurare heap high-water mark, tempo di lettura eventi, parse e cardinalità
   delle mappe prima di applicare FastUtil e chiavi compatte.
4. Rimuovere i campi build derivati da `Tracker` e `participants` solo dopo la
   parità dimostrata e la verifica di tutti i consumer.
5. Ridurre il payload persistito ai totali e alle sole medie/metriche non
   ricostruibili, sincronizzando API, Mongo, Redis e documentazione.

Questo TODO modifica intenzionalmente la direzione descritta dall’ADR-0012
(build alimentata dalla scansione condivisa): prima dell’implementazione serve
un aggiornamento esplicito dell’ADR e del macro-task proprietario.
