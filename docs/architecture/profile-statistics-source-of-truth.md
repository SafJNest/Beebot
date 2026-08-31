# Profile statistics: unica fonte di verità

- Stato: implementato staticamente; verifica runtime Mongo ed explain ancora pendenti
- Ultimo aggiornamento: 2026-08-20
- Scope: `SummonerOverview`, `SummonerProfile`, `ProfileMatchups`, `!summoner`, profilo HTTP e statistiche Mongo LoL
- Owner di cache, persistenza e composizione: `ProfileService`
- Owner del calcolo puro: `ProfileAnalyzer`
- Owner del refresh asincrono: `lol.queue.DatabaseTracker`

Questo documento è il riferimento operativo per il flusso delle statistiche profilo. In caso di nuovo lavoro cercare questi termini: `ProfileStatistics`, `ProfileMatchups`, `Filter`, `ActivityFilter`, `toSummonerKey`, `puuid + filterKey`, `recentMatches`, `lastUpdate`, `DatabaseTracker.startProfileStatistics`, `DatabaseTracker.startProfileMatchups`.

## Regola principale

Per uno stesso account, tutte le statistiche filtrate sono identificate dalla coppia:

```text
PUUID + Filter completo
```

Il PUUID identifica l'account Riot. Il `Filter` identifica esattamente il dataset da aggregare. Non esistono più una statistica “profile”, una statistica “overview” e una statistica “champion” calcolate separatamente per lo stesso caso: overview, profile e comando generico leggono lo stesso `ProfileStatistics`.

`recentMatches` non fa parte dell'aggregato. È una proiezione leggera caricata separatamente usando lo stesso PUUID e lo stesso filtro.

## Profile records

I record sono una proiezione distinta in `profile_records`, con identità
`puuid + filterKey + metric`. `ProfileRecordService` è owner della lettura e
del calcolo; `ProfileRecordAnalyzer` è puro; `ComputeScheduler` esegue il job
deduplicato `profile-records:<puuid>:<filterKey>` sul worker PROFILE. Il
calcolo usa lo stesso filtro completo delle statistiche, ma legge
`match_events` soltanto nel proprio pass batchato: il refresh normale delle
statistiche non materializza timeline.

I record finali usano i campi flat del participant. I record timeline usano
`champion_kills` e `monster_events`. L'assenza di eventi esclude solo quelle
metriche e non produce valori zero. I record TEAM/MATCH conservano una riga
per ogni partecipante pertinente e `gameShared=true`; i record PARTICIPANT
omettono il campo. Il rank/LP/MMR è lo snapshot del participant nel match, mai
il rank corrente del summoner.

## Refresh esplicito del profilo

`POST /api/lol/{shard}/profile/{puuid}/refresh` aggiorna prima Account,
summoner, rank e mastery con `R4JQueue` e persiste ogni componente. Solo dopo
la verifica riuscita aggiorna il campo interno Mongo `summoner.lastSeenAt` e
accoda un unico `IMMEDIATE profile-refresh:<puuid>` su `DatabaseTracker`.

Il batch legge tutti i match del PUUID/shard una sola volta, in ordine
`timeStart`, con cursor Mongo senza materializzare `List<Match>`, e rigenera
da zero soltanto le tre varianti canoniche: statistics e
matchups e activity sul filtro canonical della season corrente, senza
patch/queue/lane/champion. I filtri derivati restano on-demand. Il breakdown champion del profilo è incluso in
`ProfileStatistics`; il refresh non avvia statistiche globali champion e non
richiede né modifica la matchlist.

## Activity profile

L'endpoint `GET /api/lol/{shard}/profile/{puuid}/activity` usa soltanto i
parametri `start`, `end`, `queue` e `champion`. Il controller costruisce un
`Filter.canonical()` quando `start` e `end` sono entrambi omessi; con un bound
esplicito usa `Filter.summoner(start, end)`. Normalizza `queue=ALL` a queue
nulla e usa `0` come valore neutro per champion.

Il servizio legge i match con `MongoDB.findProfileStatisticsMatches`, quindi
riusa lo stesso `buildMatchFilter` e la stessa verifica completa del filtro
usata dalle statistiche profilo. `ProfileActivity.from(...)` percorre il
risultato una sola volta e aggiorna nello stesso passaggio totale, celle
`7x24`, aggregati giornalieri/orari, queue, sessioni e finestre temporali.

La response è una proiezione dedicata e non modifica `SummonerView` o
`overview.recentMatches`. `recentSessions` contiene tutte le sessioni del
periodo in una sola response, senza cursor. Le celle della heatmap sono
ordinate per `day * 24 + hour`, con Monday `0` e Sunday `6`.

La persistenza segue lo stesso read-through delle statistiche, ma su una
collection derivata dedicata: `Redis SUMMONER_ACTIVITY(PUUID, filterKey)`, poi
Mongo `profile_activity` con `{ puuid, filterKey }`. Un valore assente restituisce `202
profile_activity_pending` e viene accodato `NORMAL`. Un valore stale resta
un `200` con il payload persistito e `metadata.refresh=true`, poi accoda solo
l'activity in `BACKGROUND`; non viene calcolato nella request.
Il valore `filter` della response è il `Filter` canonico, non un record
parallelo.

## Profile matchups

`GET /api/lol/{shard}/profile/{puuid}/matchups` usa `ActivityFilter`, che
estende `Filter` con `minGames`. `queue` omessa o `ALL` significa tutte le
queue, `role` omesso significa tutti i ruoli. Se `start` è presente senza
`end`, la fine viene impostata alle `23:59:59.999` della giornata corrente nel
timezone del server, così la chiave resta stabile durante la giornata; se viene
passato solo `end`, resta il limite inferiore aperto. Quando almeno uno dei due bound è
presente, definisce il periodo e prevale su `patch`; se mancano entrambi,
`patch` è il fallback mentre il periodo resta quello della season canonical.
`minGames` ha default 5 e filtra solo le righe matchup della response; non
partecipa a `Filter.toSummonerKey()`.

`ProfileMatchups` ha un contratto separato da `ProfileStatistics`: salva solo
le foglie `champions.<championId>.<CanonicalQueue>.<position>`. Ogni foglia
contiene gli accumulatori base e `matchups.<opponentChampionId>` per gli
avversari incontrati nella stessa posizione. Non salva aggregate per champion,
queue o lane, né `reference`, `winrate`, `kda` o `avg*`; questi valori vengono
calcolati dal consumer. `UNKNOWN` conserva le partite senza posizione valida e
le queue Riot vengono canonicalizzate all'ingestion.

`ProfileMatchups` è l'unica sorgente persistita dei matchup. Un consumer che
serve una vista globale la ricostruisce sommando le foglie; `ProfileStatistics`
non conserva `matchups` né `duoStats`. `ProfileMatchups` ha un proprio
read-through Redis/Mongo:

```text
Redis SUMMONER_MATCHUPS(PUUID, filterKey)
  -> Mongo profile_matchups { puuid, filterKey }
  -> DatabaseTracker profile-matchups:<puuid>:<filterKey>
  -> Mongo.findProfileStatisticsMatches(..., Filter, 0, 0)
  -> ProfileAnalyzer.matchups(...)
  -> Mongo upsert e Redis cache
```

Il calcolo non avviene durante la request. Un miss restituisce `202`; un
aggregato stale resta `200` con `metadata.refresh=true` e accoda soltanto il
refresh matchup in bassa priorità. Il refresh viene eseguito dal worker database generale, condiviso con gli altri
refresh non-build; il worker build resta dedicato ai soli calcoli build.
Il JSON del profilo esistente non cambia.

## Freshness stale

Un aggregato profile è stale oltre `30 giorni + jitter deterministico 0-14
giorni`, derivato dal PUUID. La GET accoda il backstop `BACKGROUND` solo se
`lastSeenAt` è negli ultimi 60 giorni; il campo resta interno al documento
`summoner`, non appartiene a `Summoner` né al JSON/API. Lo stale non accoda mai
il refresh completo: overview accoda solo statistics, activity solo activity e
matchups solo matchups.

## Il filtro canonico

`Filter` è l'oggetto che deve essere passato senza perdere campi tra UI, servizio, query Mongo, cache e persistenza. I campi che partecipano al filtro sono:

| Campo | Significato |
|---|---|
| `champion` | Champion del summoner; `0` significa tutti |
| `lane` | Lane del participant |
| `queue` | Queue della partita |
| `rank` | Tier richiesto |
| `rankBehavior` | `EXACT` oppure `GREATER_OR_EQUAL` |
| `patch` | Patch major; il match può avere anche il suffisso di versione |
| `region` | Shard League richiesto |
| `opponent` | Champion avversario richiesto |
| `duo` | Champion del duo richiesto |
| `timeStart` | Inizio del periodo, `0` senza limite |
| `timeEnd` | Fine del periodo, `0` senza limite |

Il profilo base, la leaderboard e le API senza filtri usano `Filter.canonical()`:

```text
champion = 0
lane = null
queue = null
rank = null
patch = null
region = null
opponent = 0
duo = 0
period = current season
```

I filtri espliciti modificano il periodo dello stesso oggetto. Queue, lane, champion e gli altri selettori producono un aggregato distinto.

### `toKey()` e `toSummonerKey()` non sono intercambiabili

- `Filter.toKey()` resta la chiave storica degli aggregate champion/build e non contiene il periodo completo del profilo.
- `Filter.toSummonerKey()` è la chiave dedicata a `profile_statistics`, Redis e DatabaseTracker.

`toSummonerKey()` costruisce questa stringa logica:

```text
champion|lane|queue|rank|rankBehavior|patch|region|opponent|duo|timeStart|timeEnd
```

I valori null o neutri vengono rappresentati con `*`. La stringa viene codificata con Base64 URL-safe senza padding. La forma effettiva è quindi:

```java
Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(rawFilter.getBytes(StandardCharsets.UTF_8));
```

Il valore completo, non solo il periodo o la queue, deve essere usato per la lettura. Se anche un solo campo cambia, il risultato è un altro aggregato e deve avere un altro documento.

## Documento Mongo

La collection è `profile_statistics`. Il documento target è flat e conserva soltanto le foglie aggregabili:

```json
{
  "_id": "ObjectId casuale stabile",
  "puuid": "Riot PUUID",
  "filterKey": "Filter.toSummonerKey()",
  "timeStart": 1710000000000,
  "timeEnd": 1710002100000,
  "lastUpdate": 1710002200000,
  "oldestMatchAt": 1710000000000,
  "newestMatchAt": 1710002100000,
  "champions": {
    "157": {
      "RANKED_SOLO": {
        "TOP": {
          "games": 42,
          "wins": 24,
          "blueGames": 20,
          "blueWins": 13,
          "redGames": 22,
          "redWins": 11,
          "championLevelTotal": 756
        }
      }
    }
  },
  "pings": {},
  "spellOne": {},
  "spellTwo": {}
}
```

Non deve esistere il campo root `statistics` per i nuovi documenti.
`champions` è la sola source of truth delle statistiche principali, alla
granularità `champion × CanonicalQueue × position`.
Ogni foglia conserva anche i contatori base per side (`blueGames`, `blueWins`,
`redGames`, `redWins`); winrate per queue, lane o side resta derivato da questi
contatori e non viene materializzato come campo separato.
Ogni game entra in una foglia; una posizione assente o non applicabile usa
sempre `UNKNOWN`.

Le queue Riot vengono normalizzate all'ingestion in `CanonicalQueue`
(`RANKED_SOLO`, `RANKED_FLEX`, `NORMAL_DRAFT`, `ARAM`, `ARENA`, ecc.). Non
vengono persistiti `total`, `queueStats`, `laneStats`, champion totals,
`context`, `reference`, `winrate`, `kda` o campi `avg*`. Discord può ricreare
queste viste in memoria, ma Mongo, Redis e HTTP espongono soltanto le foglie.
`pings`, `spellOne` e `spellTwo` restano strutture dedicate perché non
richiedono la stessa granularità. I matchup vivono soltanto in
`profile_matchups`; non esistono aggregate matchup o duo nel documento
principale.

`championLevelTotal` è la somma dei soli `MatchParticipant.getChampionLevel()`.
Un campo metrico assente significa raw storico non disponibile; `0` presente
è un valore raccolto. Le medie sono del consumer. I campi Arena esistono solo
nella foglia `ARENA → UNKNOWN`: `avgArenaPlacement` è
`arenaPlacementSum / games` di quella foglia.

`timeStart` e `timeEnd` nel payload descrivono l'intervallo/progresso dei dati aggregati. L'identità completa del filtro, inclusa la fine del periodo richiesto, è `filterKey`.

`lastUpdate` viene assegnato soltanto dopo aver terminato la scansione e il calcolo dei match. È il timestamp che Discord e API mostrano per indicare quando l'aggregato è stato calcolato.

## Identità Mongo: spiegazione operativa

Il runtime possiede l'indice unique `profile_statistics_identity` su `{ puuid,
filterKey }`. La chiave logica resta la coppia, mentre `_id` è l'identità
fisica del documento. Il bootstrap è create-only: prima di creare l'indice
verifica identità mancanti e duplicati e interrompe l'avvio senza cleanup.

### Perché queste due chiavi

La query applicativa è sempre:

```javascript
db.profile_statistics.findOne({
  puuid: "<PUUID>",
  filterKey: "<Filter.toSummonerKey()>"
})
```

Il PUUID da solo non basta: lo stesso summoner può avere statistiche per current split, previous split, all time, queue, lane, champion o matchup diversi. `filterKey` da solo non basta: lo stesso filtro viene calcolato per molti account. La coppia è la chiave logica unica del risultato.

### Perché la coppia è unica

Il flusso applicativo tratta come invariante:

```text
un solo ProfileStatistics per PUUID e filtro completo
```

un solo `ProfileStatistics` per PUUID e filtro completo. L'indice unique Mongo
protegge l'invariante anche quando due refresh concorrenti eseguono l'upsert.
Il lookup resta comunque esatto sulla coppia completa.

### Perché `_id` non è la chiave di lookup

`_id` è casuale (`ObjectId`) e viene generato solo al primo inserimento. Non contiene PUUID, periodo o filtro. La coppia `puuid + filterKey` è la chiave business; `_id` è soltanto l'identità fisica stabile del documento Mongo.

Il write path usa un upsert atomico:

```javascript
db.profile_statistics.updateOne(
  { puuid: "<PUUID>", filterKey: "<CANONICAL_KEY>" },
  {
    $set: {
      puuid: "<PUUID>",
      filterKey: "<CANONICAL_KEY>",
      timeStart: ..., timeEnd: ..., lastUpdate: ...,
      champions: { <championId>: { <canonicalQueue>: { <position>: <Stats> } } },
      pings: ..., spellOne: ..., spellTwo: ...
    },
    $setOnInsert: { _id: ObjectId() }
  },
  { upsert: true }
)
```

Conseguenze:

1. se la coppia non esiste, Mongo crea un documento con `_id` casuale;
2. se la coppia esiste, Mongo aggiorna lo stesso documento;
3. `_id` non viene riscritto perché è presente solo in `$setOnInsert`;
4. la coppia applicativa resta stabile anche durante refresh concorrenti;
5. non usare `replace` con un `_id` derivato da PUUID o stagione;
6. non cercare più per `{ puuid, seasonStart }`.

### Classificazione OTP

Per ogni CanonicalQueue esiste al massimo un champion OTP. I game del champion
sono sommati su tutte le posizioni giocabili della queue; con N game, p1 e p2
come share dei primi due champion, il primo è OTP quando:

```text
N >= 20
p1 >= 0.50 + 0.30 * exp(-N / 250)
p1 - p2 >= 0.15
```

La flag `isOtp: true` è salvata in ogni foglia giocabile del champion
vincente nella stessa queue; per ogni altro champion il campo è omesso. È una
classificazione derivata, non un contatore: ogni `finish()` la azzera e la
ricostruisce. UNKNOWN e le lane non giocabili non possono produrre un OTP.

### Bootstrap Mongo

Il bootstrap crea solo le collection e gli indici mancanti e non modifica o
rimuove indici secondari. `profile_statistics_identity` viene preceduto dal
preflight delle identità mancanti o duplicate; i documenti legacy devono essere
gestiti dalla migrazione/rigenerazione separata e non bisogna riutilizzare un
documento con un filtro diverso solo perché appartiene allo stesso PUUID.

Per diagnosticare un mismatch in Mongo:

```javascript
db.profile_statistics.getIndexes()
db.profile_statistics.find({ puuid: "<PUUID>" }, {
  _id: 1,
  puuid: 1,
  filterKey: 1,
  timeStart: 1,
  timeEnd: 1,
  lastUpdate: 1
})
```

La `filterKey` del documento deve essere confrontata byte per byte con `Filter.toSummonerKey()` generato dal comando. Non confrontare soltanto `timeStart`.

## Flusso di lettura e refresh

```text
Discord/API request
  -> risolve Summoner e PUUID
  -> costruisce un Filter completo
  -> ProfileService.get(PUUID, Filter)
       -> Redis SUMMONER_STATISTICS(PUUID, filterKey)
       -> Mongo {puuid, filterKey}
  -> hit: usa ProfileStatistics
  -> miss: DatabaseTracker.startProfileStatistics(Summoner, Filter)
       -> risposta parziale/pending, nessun calcolo sincrono
       -> coda PROFILE-logical (canale più scarico all’inserimento)
            -> Mongo match projection con lo stesso Filter
            -> ProfileStatistics.accumulate(match, puuid, filter)
            -> set lastUpdate dopo il calcolo
            -> upsert atomico {puuid, filterKey}
            -> cache ProfileStatistics
            -> invalida recent matches e profile page
```

Il case owner `test highstats` esegue un rebuild esplicito delle statistiche
profilo per Challenger, Grandmaster e `tracking=true`, considerando tutte le
regioni attive e le due queue ranked per l'alta elo. Usa lo stesso
`Filter.canonical()` del frontend, forza `rebuild=true` anche quando l'aggregato
esiste già, deduplica i PUUID e processa una pagina alla volta attendendo il
completamento prima della pagina successiva. In questo modo la mole di lavoro
non riempie la FIFO né mantiene in memoria l'intero elenco high elo.

Il job rank entries, avviato da `pushhighelo` o `getallrank`, salva ogni
`LeagueEntry` nella sola queue a cui appartiene. High elo (Master+) e all
entries (sotto Master) condividono lo stesso job e non possono sovrapporsi;
una richiesta concorrente aggiunge la fascia complementare alla stessa
esecuzione. Per un summoner già persistito non esegue chiamate identity Riot e aggiorna
atomically soltanto il path `summoner.ranks.<QUEUE>`. Per un PUUID assente
risolve prima Summoner e, se il Riot ID non è già disponibile, Account, poi
persiste l'identità prima del rank. Il tracker avvia un worker per shard; il
rate limiting outbound resta di proprietà di `R4JQueue` per shard.

Il comando owner `tracker` legge on demand lo stato degli scheduler, dei game
in coda e dei due worker `DatabaseTracker`, senza aggiungere logging nel
percorso caldo dei refresh.

Per activity il flusso sincrono è invece:

```text
API request
  -> costruisce Filter completo
  -> Redis SUMMONER_ACTIVITY(PUUID, filterKey)
  -> Mongo profile_activity {puuid, filterKey}
  -> Mongo findProfileStatisticsMatches(..., Filter, 0, 0)
  -> ProfileActivity.from(...): una scansione, stats e accumulator condivisi
  -> Mongo upsertProfileActivity(PUUID, Filter, activity)
  -> Redis SUMMONER_ACTIVITY(PUUID, filterKey)
```

La deduplicazione del lavoro asincrono usa la stessa identità logica:

```text
in-flight key = profile-statistics:puuid:filter.toSummonerKey()
```

Due richieste per lo stesso PUUID e lo stesso filtro condividono il Future mentre il job è in coda o in esecuzione. Due filtri diversi possono essere accodati separatamente; il worker generale esegue un solo refresh non-build alla volta e può lavorare in parallelo con il worker build. Il marker viene rimosso sia dopo successo sia dopo errore, così una richiesta successiva può ritentare.

## Calcolo e filtri

`ProfileService` è l'unico owner di cache, query e persistenza; `ProfileAnalyzer` è l'unico owner del calcolo puro. `MongoDB.findProfileStatisticsMatches` usa il filtro completo e una projection dei match/participant necessari. `ProfileStatistics.matchesFilter` viene applicato anche dopo la lettura per garantire che i filtri relazionali non vengano soddisfatti da participant errati.

Devono essere rispettati tutti questi campi:

- queue;
- region/shard;
- champion del summoner;
- lane del summoner;
- patch major;
- rank e comportamento del rank;
- opponent sulla lane avversaria;
- duo sul team alleato;
- periodo `timeStart/timeEnd`.

Durante l'aggregazione vengono prodotti nello stesso passaggio totale, queue, lane, champion, matchup, duo, ping e spell. Non introdurre un servizio separato per pings, matchup o champion overview.

## `recentMatches` e dati raw

`recentMatches` è una responsabilità separata:

- cache Redis: `SUMMONER_RECENT_MATCHES` con PUUID e `filterKey`;
- query Mongo separata con projection `MatchResult`;
- invalidazione dopo un refresh riuscito delle statistiche;
- nessun campo `recentMatches` dentro `ProfileStatistics` o nel documento `profile_statistics`.

Le viste che richiedono eventi o match completi, come timeline e dettagli OP.GG, continuano a leggere i match raw e gli eventi dalla loro collection. Non devono usare l'aggregato per ricostruire eventi.

## Composizione applicativa

### SummonerOverview, SummonerProfile e `!summoner`

Tutti usano lo stesso `ProfileStatistics` per il PUUID e il filtro corrente come fonte dati. La composizione e la presentazione restano però separate: un cambiamento al modello o alla sorgente non autorizza una modifica dell'embed esistente.

`SummonerOverview.from(...)` compone:

```text
ProfileStatistics + ranks + masteries + recentMatches
  -> SummonerOverview
  -> SummonerView
```

Il comando generico `!summoner` non usa più un percorso statistico separato: legge lo stesso aggregato dell'overview, ma mantiene il precedente formato dell'embed. Mostra quindi i campi già presenti nella vista generica, alimentati dal nuovo `ProfileStatistics`, più `lastUpdate`; non deve mostrare automaticamente ogni nuovo campo aggiunto all'aggregato.

L'overview base mantiene il proprio formato storico e include i ping nel blocco già esistente. Matchup e lista completa dei champion restano nelle rispettive viste dedicate, usando lo stesso `ProfileStatistics`. `recentMatches` è composto separatamente dal profilo HTTP e non viene caricato da `LeagueMessage.getSummonerEmbed`.

`lastUpdate` viene formattato nel layer Discord come data/ora leggibile e timestamp Discord relativo. Il valore persistito resta sempre un timestamp numerico in millisecondi.

### Menu Discord

`OVERVIEW_PING` e `OVERVIEW_OBJECTIVES` non sono più flussi attivi. I ping sono già dentro l'overview base. Gli objectives non vengono più calcolati né persistiti. I valori legacy possono restare nell'enum solo per normalizzare vecchi component/button state, ma non devono essere esposti da menu, pulsanti o dispatcher.

## Cache e invalidazione

Le chiavi Redis sono separate per namespace: `beebot:lol:r4j:*` identifica i
payload Riot4J, mentre `beebot:lol:ls:*` identifica le proiezioni e le code
League OS. Le chiavi applicative legate a un account mettono i valori reali
prima della risorsa: `beebot:lol:ls:<region>:<shard>:<puuid>:summoner` oppure
`beebot:lol:ls:<region>:<shard>:<puuid>:summoner:statistics:<filterKey>`, così la scansione per
PUUID ritrova le proiezioni del summoner senza un token letterale `puuid`.

| Dato | Chiave | TTL | Owner | Invalidazione |
|---|---|---:|---|---|
| summoner base | `SUMMONER(region, shard, PUUID)` | 6h | `SummonerService` | dopo refresh del componente o `SummonerService.invalidate` |
| rank summoner | `SUMMONER_RANKS(region, shard, PUUID)` | 6h | `RankService` | dopo refresh del componente o `SummonerService.invalidate` |
| mastery summoner | `SUMMONER_MASTERIES(region, shard, PUUID)` | 6h | `MasteryService` | dopo refresh del componente o `SummonerService.invalidate` |
| statistiche aggregate | `SUMMONER_STATISTICS(region, shard, PUUID, filterKey)` | 6h | `ProfileService` | aggiornamento dopo upsert |
| activity aggregate | `SUMMONER_ACTIVITY(region, shard, PUUID, filterKey)` | 6h | `ProfileService` | aggiornamento dopo upsert |
| summoner matchups | `SUMMONER_MATCHUPS(region, shard, PUUID, filterKey)` | 6h | `ProfileService` | aggiornamento dopo upsert |
| recent matches | `SUMMONER_RECENT_MATCHES(region, shard, PUUID, filterKey)` | 1h | `ProfileService` | dopo refresh statistiche |
| overview summoner | `SUMMONER_OVERVIEW(region, shard, PUUID)` | 1h | `ProfileService` | dopo refresh statistiche o componenti summoner; non contiene `recentMatches` |
| match raw | chiavi match esistenti | secondo `RedisKey` | `MatchService`/`Tracker` | secondo il flusso match |

Un aggregato Mongo privo di `champions` viene trattato come obsoleto e
rigenerato da `ComputeScheduler`. I TTL delle chiavi Redis restano
definiti esclusivamente da `RedisKey`; la scadenza riduce la permanenza delle
proiezioni, ma non sostituisce l’invalidazione esplicita dopo un refresh
riuscito.

Non usare la cache della profile page come fonte di verità per le statistiche. La fonte è sempre `ProfileStatistics` letto con il filtro completo; la pagina è una composizione derivata.

## API e stati

L'API continua a restituire i modelli canonici `SummonerView` e `SummonerOverview`. Nel JSON pubblico:

- `overview.statistics` contiene le foglie filtrate in `champions`;
- `overview.statistics.champions[championId][canonicalQueue][position]` distingue champion, queue e lane;
- `overview.recentMatches` contiene la lista leggera separata;
- `overview.statistics.lastUpdate` indica il completamento del calcolo;
- `Match` completo resta riservato a dettagli e timeline.

Se identity, rank e mastery sono pronti ma manca `ProfileStatistics`, il profilo HTTP restituisce subito il profilo disponibile come `PARTIAL` con `recentMatches` vuoti e accoda il refresh; la query dei recent match parte soltanto quando l'aggregato è disponibile. Se mancano componenti base, mantiene il comportamento `202 profile_pending`. Discord mostra il messaggio di preparazione soltanto finché la coppia esatta PUUID/filtro non è disponibile.

## Checklist per un futuro intervento

Prima di modificare questo flusso verificare:

1. il nuovo campo appartiene a `Filter`, a `ProfileStatistics` o ai match raw;
2. il campo è incluso in `toSummonerKey()` se modifica il dataset;
3. Redis e Mongo usano la stessa chiave;
4. `MongoDB` legge e scrive `{puuid, filterKey}`;
5. la coppia `{ puuid, filterKey }` resta l'identità applicativa;
6. il calcolo passa da `ProfileAnalyzer` tramite `ProfileService`, non da Discord/API/controller;
7. `recentMatches` resta separato;
8. activity usa lo stesso `Filter` e la query match condivisa, senza creare una seconda semantica per queue o periodo;
9. `lastUpdate` viene scritto solo dopo il calcolo;
10. overview, profile e `!summoner` leggono lo stesso oggetto;
11. la presentazione esistente resta invariata salvo richiesta esplicita di refactor dello style;
12. API docs, audit, documentazione Mongo e regole operative restano sincronizzati.

### File canonici da aprire per recuperare il contesto

- `src/main/java/com/safjnest/lol/model/Filter.java`
- `src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`
- `src/main/java/com/safjnest/lol/service/ProfileService.java`
- `src/main/java/com/safjnest/lol/service/ProfileAnalyzer.java`
- `src/main/java/com/safjnest/nosql/MongoDB.java`
- `src/main/java/com/safjnest/lol/queue/DatabaseTracker.java`
- `src/main/java/com/safjnest/lol/tracker/Tracker.java`
- `src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`
- `src/main/java/com/safjnest/lol/message/LeagueMessage.java`
- `src/main/java/com/safjnest/lol/model/summoner/SummonerOverview.java`
- `docs/mongo/01-db-structure.md`
- `docs/architecture/adr/0004-profile-statistics-refresh-queue.md`
- `docs/architecture/adr/0010-database-refresh-queue.md`
- `docs/architecture/adr/0008-endpoint-cache-and-async-lookups.md`
