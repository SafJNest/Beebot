# Profile statistics: unica fonte di verità

- Stato: implementato staticamente; verifica runtime Mongo ed explain ancora pendenti
- Ultimo aggiornamento: 2026-07-28
- Scope: `SummonerOverview`, `SummonerProfile`, `ProfileMatchups`, `!summoner`, profilo HTTP e statistiche Mongo LoL
- Owner del calcolo e della persistenza: `ProfileStatisticsService`
- Owner del calcolo e della persistenza matchup: `ProfileMatchupsService`
- Owner del refresh asincrono: `DatabaseTracker`

Questo documento è il riferimento operativo per il flusso delle statistiche profilo. In caso di nuovo lavoro cercare questi termini: `ProfileStatistics`, `ProfileMatchups`, `Filter`, `ActivityFilter`, `toSummonerKey`, `puuid + filterKey`, `recentMatches`, `lastUpdate`, `DatabaseTracker.startProfileStatistics`, `DatabaseTracker.startProfileMatchups`.

## Regola principale

Per uno stesso account, tutte le statistiche filtrate sono identificate dalla coppia:

```text
PUUID + Filter completo
```

Il PUUID identifica l'account Riot. Il `Filter` identifica esattamente il dataset da aggregare. Non esistono più una statistica “profile”, una statistica “overview” e una statistica “champion” calcolate separatamente per lo stesso caso: overview, profile e comando generico leggono lo stesso `ProfileStatistics`.

`recentMatches` non fa parte dell'aggregato. È una proiezione leggera caricata separatamente usando lo stesso PUUID e lo stesso filtro.

## Activity profile

L'endpoint `GET /api/lol/{shard}/profile/{puuid}/activity` usa soltanto i
parametri `start`, `end`, `queue` e `champion`. Il controller costruisce un
`Filter.summoner(start, end)`, normalizza `queue=ALL` a queue nulla e usa `0`
come valore neutro per champion.

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
collection derivata dedicata: `Redis PROFILE_ACTIVITY(PUUID, filterKey)`, poi
Mongo `profile_activity` con `{ puuid, filterKey }`; su miss vengono letti i
match, calcolati tutti gli aggregati in una scansione e salvati prima in Mongo
e poi in Redis. Il valore `filter` della response è il `Filter` canonico, non
un record parallelo.

## Profile matchups

`GET /api/lol/{shard}/profile/{puuid}/matchups` usa `ActivityFilter`, che
estende `Filter` con `minGames`. `queue` omessa o `ALL` significa tutte le
queue, `role` omesso significa tutti i ruoli. Se `start` è presente senza
`end`, la fine viene impostata all'istante corrente; se viene passato solo
`end`, resta il limite inferiore aperto. Quando almeno uno dei due bound è
presente, definisce il periodo e prevale su `patch`; se mancano entrambi,
`patch` è il fallback mentre il periodo resta quello dello split corrente.
`minGames` ha default 5 e filtra solo le righe matchup della response; non
partecipa a `Filter.toSummonerKey()`.

La response viene costruita da `ProfileMatchups`: una riga per ogni champion
giocato, le cui statistiche generiche usano `Stats`, e una lista di matchup
per champion avversario. I matchup contano soltanto l'avversario sulla stessa
lane. Quando il role filter è omesso, le partite dello stesso champion in
ruoli diversi vengono aggregate nella stessa riga.

`ProfileStatistics.matchups` resta l'aggregato storico globale usato dal
profilo e da Discord; non è la sorgente della relazione champion giocato →
matchup. Il nuovo aggregato ha un proprio read-through Redis/Mongo:

```text
Redis PROFILE_MATCHUPS(PUUID, filterKey)
  -> Mongo profile_matchups { puuid, filterKey }
  -> DatabaseTracker profile-matchups:<puuid>:<filterKey>
  -> Mongo.findProfileStatisticsMatches(..., Filter, 0, 0)
  -> ProfileMatchups.from(...)
  -> Mongo upsert e Redis cache
```

Il calcolo non avviene durante la request. Un miss restituisce `202` e il
refresh viene eseguito dagli stessi due worker database del flusso profilo.
Il JSON del profilo esistente non cambia.

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

Il profilo base usa `Filter.summoner()`:

```text
champion = 0
lane = null
queue = null
rank = null
patch = null
region = null
opponent = 0
duo = 0
period = current split
```

I pulsanti `General`, `Current Split` e `Previous Split` modificano soltanto il periodo dello stesso oggetto. Queue, lane, champion e gli altri selettori modificano lo stesso filtro e producono un aggregato distinto.

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

La collection è `profile_statistics`. Il documento target è flat:

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
  "total": {},
  "queueStats": [],
  "laneStats": [],
  "championStats": [],
  "matchups": {},
  "duoStats": {},
  "pings": {},
  "spellOne": {},
  "spellTwo": {}
}
```

Non deve esistere il campo root `statistics` per i nuovi documenti. `ProfileStatistics` contiene gli aggregati esplosi:

- totale complessivo;
- aggregati per queue, lane e champion;
- matchup lane/champion;
- duo/champion alleato;
- pings;
- summoner spell;
- metriche di performance e timestamp di primo/ultimo match.

Ogni elemento di `championStats` mantiene le metriche aggregate del champion e
può includere `context`, una lista di mappe queue/lane. Le queue usano i nomi
canonici R4J; `TEAM_BUILDER_RANKED_SOLO` viene esposta come
`RANKED_SOLO_5X5` nel contesto. Le queue senza lane usano una sola chiave
`UNKNOWN`:

```json
"context": [
  {
    "RANKED_SOLO_5X5": {
      "TOP": {},
      "MID": {}
    }
  },
  {
    "CHERRY": {
      "UNKNOWN": {}
    }
  }
]
```

I valori sotto ogni lane hanno lo stesso set completo di metriche di `Stats`.
`context` non viene serializzato su `total`, `queueStats` o `laneStats` quando
è vuoto. I dati senza lane in una queue che normalmente supporta le lane non
entrano nel contesto, ma continuano a contribuire agli aggregati generici.

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
      total: ..., queueStats: ..., laneStats: ...,
      championStats: ..., matchups: ..., duoStats: ..., pings: ...
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
  -> ProfileStatisticsService.get(PUUID, Filter)
       -> Redis PROFILE_STATISTICS(PUUID, filterKey)
       -> Mongo {puuid, filterKey}
  -> hit: usa ProfileStatistics
  -> miss: DatabaseTracker.startProfileStatistics(Summoner, Filter)
       -> risposta parziale/pending, nessun calcolo sincrono
       -> coda FIFO e uno dei due virtual worker DB
            -> Mongo match projection con lo stesso Filter
            -> ProfileStatistics.add(match, puuid, filter)
            -> set lastUpdate dopo il calcolo
            -> upsert atomico {puuid, filterKey}
            -> cache ProfileStatistics
            -> invalida recent matches e profile page
```

Per activity il flusso sincrono è invece:

```text
API request
  -> costruisce Filter completo
  -> Redis PROFILE_ACTIVITY(PUUID, filterKey)
  -> Mongo profile_activity {puuid, filterKey}
  -> Mongo findProfileStatisticsMatches(..., Filter, 0, 0)
  -> ProfileActivity.from(...): una scansione, stats e accumulator condivisi
  -> Mongo upsertProfileActivity(PUUID, Filter, activity)
  -> Redis PROFILE_ACTIVITY(PUUID, filterKey)
```

La deduplicazione del lavoro asincrono usa la stessa identità logica:

```text
in-flight key = profile-statistics:puuid:filter.toSummonerKey()
```

Due richieste per lo stesso PUUID e lo stesso filtro condividono il Future mentre il job è in coda o in esecuzione. Due filtri diversi possono essere accodati separatamente, ma al massimo due calcoli DB sono eseguiti contemporaneamente. Il marker viene rimosso sia dopo successo sia dopo errore, così una richiesta successiva può ritentare.

## Calcolo e filtri

`ProfileStatisticsService` è l'unico owner del calcolo. `MongoDB.findProfileStatisticsMatches` usa il filtro completo e una projection dei match/participant necessari. `ProfileStatistics.matchesFilter` viene applicato anche dopo la lettura per garantire che i filtri relazionali non vengano soddisfatti da participant errati.

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

- cache Redis: `PROFILE_RECENT_MATCHES` con PUUID e `filterKey`;
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

| Dato | Chiave | TTL | Owner | Invalidazione |
|---|---|---:|---|---|
| profilo base | `PROFILE_BASE(shard, PUUID)` | 6h | `LeagueService` | dopo refresh del componente o `invalidateSummoner` |
| rank profilo | `PROFILE_RANKS(shard, PUUID)` | 6h | `LeagueService` | dopo refresh del componente o `invalidateSummoner` |
| mastery profilo | `PROFILE_MASTERIES(shard, PUUID)` | 6h | `LeagueService` | dopo refresh del componente o `invalidateSummoner` |
| statistiche aggregate | `PROFILE_STATISTICS(PUUID, filterKey)` | 6h | `ProfileStatisticsService` | aggiornamento dopo upsert |
| activity aggregate | `PROFILE_ACTIVITY(PUUID, filterKey)` | 6h | `ProfileActivityService` | aggiornamento dopo upsert |
| profile matchups | `PROFILE_MATCHUPS(PUUID, filterKey)` | 6h | `ProfileMatchupsService` | aggiornamento dopo upsert |
| recent matches | `PROFILE_RECENT_MATCHES(PUUID, filterKey)` | 1h | `ProfileStatisticsService` | dopo refresh statistiche |
| pagina profilo | `PROFILE_PAGE(shard, PUUID)` | 1h | `LeagueService`/`ProfilePageService` | dopo refresh statistiche o componenti profilo; non contiene `recentMatches` |
| match raw | chiavi match esistenti | secondo `RedisKey` | `LeagueService`/`Tracker` | secondo il flusso match |

Un aggregato Mongo privo di `championStats[*].context` viene trattato come
obsoleto e rigenerato da `DatabaseTracker`. I TTL delle chiavi Redis restano
definiti esclusivamente da `RedisKey`; la scadenza riduce la permanenza delle
proiezioni, ma non sostituisce l’invalidazione esplicita dopo un refresh
riuscito.

Non usare la cache della profile page come fonte di verità per le statistiche. La fonte è sempre `ProfileStatistics` letto con il filtro completo; la pagina è una composizione derivata.

## API e stati

L'API continua a restituire i modelli canonici `SummonerView` e `SummonerOverview`. Nel JSON pubblico:

- `overview.statistics` contiene l'aggregato filtrato;
- `overview.statistics.championStats[*].context` distingue ogni champion per queue canonica e lane;
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
6. il calcolo passa da `ProfileStatisticsService`, non da Discord/API/controller;
7. `recentMatches` resta separato;
8. activity usa lo stesso `Filter` e la query match condivisa, senza creare una seconda semantica per queue o periodo;
9. `lastUpdate` viene scritto solo dopo il calcolo;
10. overview, profile e `!summoner` leggono lo stesso oggetto;
11. la presentazione esistente resta invariata salvo richiesta esplicita di refactor dello style;
12. API docs, audit, documentazione Mongo e regole operative restano sincronizzati.

### File canonici da aprire per recuperare il contesto

- `src/main/java/com/safjnest/lol/model/Filter.java`
- `src/main/java/com/safjnest/lol/model/statistics/ProfileStatistics.java`
- `src/main/java/com/safjnest/lol/service/ProfileStatisticsService.java`
- `src/main/java/com/safjnest/nosql/MongoDB.java`
- `src/main/java/com/safjnest/lol/tracker/DatabaseTracker.java`
- `src/main/java/com/safjnest/lol/tracker/Tracker.java`
- `src/main/java/com/safjnest/lol/message/LeagueMessageParameter.java`
- `src/main/java/com/safjnest/lol/message/LeagueMessage.java`
- `src/main/java/com/safjnest/lol/model/summoner/SummonerOverview.java`
- `docs/mongo/01-db-structure.md`
- `docs/architecture/adr/0004-profile-statistics-refresh-queue.md`
- `docs/architecture/adr/0010-database-refresh-queue.md`
- `docs/architecture/adr/0008-endpoint-cache-and-async-lookups.md`
