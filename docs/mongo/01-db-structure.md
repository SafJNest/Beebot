# Fase 1: struttura MongoDB

## Obiettivo

Definire collection, chiavi, embedding, riferimenti, naming, indici e trasformazioni prima di scrivere DTO o query.

## Dipendenze

- `AGENTS.md`;
- `docs/architecture/README.md`;
- ADR-0001, ADR-0002, ADR-0003, ADR-0005, ADR-0008;
- [`ADR-0009`](../architecture/adr/0009-mongo-persistence-and-migration.md);
- struttura attuale in `database/league_of_legends/`.

## Perimetro

Questa fase descrive solo la forma dei dati. Non modifica Java, SQL, `pom.xml` o `rsc/settings.json`.

## Naming

- database Mongo production: `beebot`;
- database Mongo testing: `beebot_test`;
- collection LoL: stesso nome della tabella MariaDB quando esiste, senza prefisso `lol_`;
- campi JSON: `camelCase`;
- enum: stringhe R4J esatte;
- date: epoch milliseconds;
- `null`: valore assente o non riconosciuto;
- `[]`: lista conosciuta ma vuota.

## Database runtime

La URI configurata identifica il server Mongo, non il database applicativo:

```text
mongodb://<user>:<password>@safjnest.com:27017/
```

Il nome del database viene scelto dal codice in base a `App.isTesting()`:

```text
App.isTesting() == false -> beebot
App.isTesting() == true  -> beebot_test
```

Le collection mantengono gli stessi nomi nei due database. Non aggiungere `_test` al nome della collection: l'isolamento è garantito dal database separato.

Un avvio in testing non deve mai aprire o scrivere `beebot`.

## Mappa delle collection

| Origine | Target | Chiave | Forma |
|---|---|---|---|
| `summoner` | `summoner` | `_id = puuid` | documento aggregato |
| `rank` | `summoner.ranks{}` | `queue key + region` | embedded |
| `masteries` | `summoner.masteries[]` | `championId` | embedded |
| `match` | `match` | `_id = full Riot match ID` | documento aggregato |
| `match.events` | `match_events` | `_id = full Riot match ID` | JSON separato, WiredTiger Zstandard |
| `participant` | `match.participants[]` | `puuid` dentro il match | embedded |
| `profile_statistics` | `profile_statistics` | `puuid + filterKey` | documento flat con foglie `champions`, `_id` casuale stabile |
| `profile_activity` | `profile_activity` | `puuid + filterKey` | aggregate derivato BSON |
| `profile_matchups` | `profile_matchups` | `puuid + filterKey` | foglie champion/queue/position/opponent BSON |
| `champion` | `champion` | `championId` | catalogo |
| `champion_builds` | `champion_builds` | `filterKey + buildKey` | aggregate, non migrato |
| `champion_stats` | `champion_stats` | `_id = filterKey` | mega-aggregate per filtro; documenti `filterKey + championId` legacy |
| champion indexables | `champions_indexable` | `_id = championId + role` | proiezione derivata della major patch corrente |
| profile indexables | `profiles_indexable` | `_id = puuid` | proiezione derivata per URL pubblici |
| leaderboard aggregates | `leaderboard_aggregates` | `_id = tipo + filtro` | snapshot derivato |
| migration checkpoints | `migration_runs` | `_id = runId` | operational state |

## `summoner`

Esempio concettuale:

```json
{
  "_id": "puuid-value",
  "riotId": "GameName#TAG",
  "region": "EUW1",
  "level": 500,
  "icon": 1234,
  "userId": "discord-user-id",
  "tracking": true,
  "lastUpdate": 1710000000000,
  "ranks": {
    "RANKED_SOLO_5X5": {
      "region": "EUW1",
      "rank": "EMERALD_II",
      "lp": 90,
      "mmr": 1490,
      "wins": 100,
      "losses": 80,
      "lastUpdate": 1710000000000
    }
  },
  "masteries": [
    {
      "championId": 157,
      "level": 30,
      "points": 250000,
      "lastPlayTime": 1710000000000
    }
  ]
}
```

### Regole summoner

- `puuid` è `_id` e non viene duplicato in un secondo campo;
- gli identificativi numerici MariaDB e il campo applicativo `summonerId` non vengono scritti;
- in Java `Summoner.region` è `LeagueShard` e in BSON resta la stringa `name()` (es. `"EUW1"`);
- il nuovo flusso non pulisce automaticamente dati precedenti; l'operatore elimina manualmente i payload obsoleti;
- `tracking=false` e gli altri default/null non vengono persistiti;
- rank e mastery non hanno collection operative separate;
- la leaderboard non duplica le righe rank: `leaderboard_aggregates` contiene solo snapshot ricostruibili di distribuzione, top-region e count;
- il rank identifica la coda tramite la key canonica dell'object `ranks`, non tramite un ID numerico;
- più regioni sono rappresentate da `region` nel rank quando il dataset lo richiede;
- non duplicare una seconda identità `Summoner` in wrapper o modelli di persistenza.

## `match`

Esempio concettuale:

```json
{
  "_id": "EUW1_134131",
  "region": "EUW1",
  "queue": "TEAM_BUILDER_RANKED_SOLO",
  "rank": "EMERALD",
  "lastUpdate": 1710000000000,
  "timeStart": 1710000000000,
  "timeEnd": 1710002100000,
  "patch": "14.10.1",
  "patchMajor": "14.10",
  "bans": {
    "BLUE": [266, 157, 238, 517, 777],
    "RED": [64, 119, 238, 141, 875]
  },
  "participants": []
}
```

### Regole match

- `_id` è sempre il Riot match ID completo;
- il solo numero Riot può essere accettato in input e normalizzato prima del lookup;
- `region`, `queue` e `rank` sono stringhe R4J;
- `fullGameId`, `gameId`, `game_id` e `leagueShard` non vengono persistiti come duplicati;
- `patch` conserva la versione completa, mentre `patchMajor` conserva i primi due segmenti e viene usato per i filtri;
- `bans` usa `BLUE` e `RED`, mai `0` e `1`;
- gli eventi non sono embedded: vengono salvati come JSON in `match_events`, collection creata con `block_compressor=zstd` e checksum;
- participant è embedded perché viene letto insieme al match;
- il documento deve essere controllato prima dell'upsert contro il limite BSON di 16 MB;
- `findMatch` e le history caricano gli eventi separatamente; le history usano un caricamento batch.

## Participant embedded

Il participant mantiene i campi attuali ma senza un oggetto `build` generico:

```json
{
  "puuid": "participant-puuid",
  "riotId": "GameName",
  "riotTag": "TAG",
  "win": true,
  "kda": "8/2/10",
  "champion": 157,
  "lane": "MID",
  "team": "BLUE",
  "rank": "EMERALD_II",
  "lp": 90,
  "gain": 20,
  "damage": 24000,
  "damageBuilding": 3000,
  "healing": 1200,
  "cs": 220,
  "goldEarned": 14500,
  "ward": 12,
  "wardKilled": 4,
  "visionScore": 35,
  "pings": {
    "danger": 2,
    "on_my_way": 5
  },
  "level": 18,
  "doubles": 1,
  "triples": 0,
  "quadruples": 0,
  "pentas": 0,
  "item0": 1055,
  "item1": 6672,
  "item2": 3006,
  "item3": 3031,
  "item4": 3121,
  "item5": 3089,
  "item6": 0,
  "q": 20,
  "w": 10,
  "e": 15,
  "r": 4,
  "d": 10,
  "f": 8,
  "summonerSpell1": 4,
  "summonerSpell2": 7,
  "primaryRunes": [8005, 9111],
  "secondaryRunes": [8210, 8237],
  "statsRunes": [5005, 5008, 5001],
  "skillOrder": [1, 2, 3, 1],
  "augments": [],
  "starterItems": [1055],
  "buildPath": [6672, 3006],
  "boots": 3006,
  "supportItem": 0
}
```

## Query leaderboard

La leaderboard usa direttamente `summoner.ranks.<QUEUE>.mmr`. Mongo filtra il
path MMR selezionato per queue, tier e regione, quindi legge la pagina con una
`find()` ordinata e limitata. L'ordinamento è `ranks.<QUEUE>.mmr DESC`.

La pagina proietta soltanto identità summoner, il rank selezionato e le masteries;
`LeaderboardService` aggiunge le statistiche già presenti in cache o Mongo e
costruisce il modello canonico `LeaderboardPage`. Il totale è indipendente dalla
pagina e risolve Redis, poi il count snapshot in `leaderboard_aggregates`, poi
`countDocuments()`; quest'ultimo ripopola entrambi i livelli. Distribuzione,
top-region e count vengono salvati come snapshot e ricostruiti ogni 12 ore. I
filtri mai materializzati vengono calcolati lazy al successivo accesso. Non
vengono persistite righe o pagine leaderboard.

Ogni snapshot usa un `_id` stabile per tipo e filtro: distribuzione e top-region
contengono `entries`, mentre `page-count` contiene il valore `count`; tutti
mantengono queue, scope e tier quando applicabile. La collection è derivata e
può essere cancellata e ricostruita senza perdita dei rank.

Gli indici della pagina sono gestiti operativamente fuori dal runtime. Non
includono `_id`.

Pagine all-ranks (`rank` omesso): sort MMR senza filtro divisione.

```javascript
{"ranks.RANKED_SOLO_5X5.mmr": -1}
{"region": 1, "ranks.RANKED_SOLO_5X5.mmr": -1}
{"ranks.RANKED_FLEX_SR.mmr": -1}
{"region": 1, "ranks.RANKED_FLEX_SR.mmr": -1}
```

Pagine tier-scoped (`rank=SILVER`, ecc.): filtro su `ranks.<QUEUE>.rank` e sort
MMR. Creazione manuale in [`11-leaderboard-rank-indexes.md`](11-leaderboard-rank-indexes.md).

```javascript
{"ranks.RANKED_SOLO_5X5.rank": 1, "ranks.RANKED_SOLO_5X5.mmr": -1}
{"region": 1, "ranks.RANKED_SOLO_5X5.rank": 1, "ranks.RANKED_SOLO_5X5.mmr": -1}
{"ranks.RANKED_FLEX_SR.rank": 1, "ranks.RANKED_FLEX_SR.mmr": -1}
{"region": 1, "ranks.RANKED_FLEX_SR.rank": 1, "ranks.RANKED_FLEX_SR.mmr": -1}
```

Ogni indice rank usa `partialFilterExpression` su `ranks.<QUEUE>.mmr`.

## Collection derivate e aggregate

Le collection di statistiche e build hanno chiavi composte stabili e payload strutturati. `profile_statistics` salva `ProfileStatistics` direttamente a root con le sole foglie `champions.<championId>.<canonicalQueue>.<position>`; `champion_stats` salva un solo mega-aggregato per filtro e `build` mantiene la propria struttura. Un refresh completato senza giochi validi salva `champions={}`: il documento è comunque valido e impedisce di confondere il caso "nessun dato" con "refresh ancora pendente". I champion senza dati non vengono inseriti nella mappa `champions`; se il filtro è pronto, la lettura di un champion valido assente restituisce quindi un aggregate vuoto con `200`. Le build usano `filter.toKey()` per `_id` e `filterKey`; le profile stats usano `filter.toSummonerKey()` per `filterKey`. I documenti legacy senza la mappa `champions` vengono rigenerati. Non esiste un campo `metrics` nel documento summoner e non esiste una sorgente Kryo o `legacyPayload` nel nuovo documento profile.

### `profile_statistics`: chiave e indice

La chiave logica è `{ puuid, filterKey }`, con `filterKey = Filter.toSummonerKey()`. `filterKey` include champion, lane, queue, rank, rank behavior, patch, region, opponent, duo e periodo. Il runtime usa questa coppia per lookup e upsert applicativi e la protegge con l'indice unique `profile_statistics_identity`. Il PUUID da solo non identifica il documento, perché uno stesso account può avere più filtri; `_id` è un ObjectId casuale stabile e non è usato per il lookup. Il write path usa `$setOnInsert` per generarlo solo al primo upsert.

Il documento è flat e non contiene un root `statistics`. `recentMatches` è una query `MatchResult` separata con lo stesso filtro e non viene salvato dentro `ProfileStatistics`. Per il flusso completo e il runbook di diagnosi vedere [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

### `profile_activity`: chiave e payload

`ProfileActivity` usa una collection derivata separata, con la stessa identità
logica `{ puuid, filterKey }`. Il documento contiene `puuid`, `filterKey` e il
payload BSON strutturato `activity`; il filtro usato dal calcolo resta il
modello canonico `Filter`, mentre `filterKey` è la chiave applicativa per
lookup, Redis e Mongo. La collection può essere cancellata e ricostruita dai
match senza modificare `profile_statistics`.

### `profile_matchups`: chiave e payload

`ProfileMatchups` usa una collection derivata separata, con la stessa identità
logica `{ puuid, filterKey }`. Il payload BSON `matchups` conserva soltanto le foglie
`champions.<championId>.<canonicalQueue>.<position>.matchups.<opponentId>`.
`minGames` resta fuori dalla chiave perché è soltanto una soglia della response.
L'indice unique `profile_matchups_identity` protegge la cardinalità uno-a-uno;
i payload legacy senza la mappa `champions` vengono rigenerati.

### `champions_indexable`

La collection contiene una riga per ogni coppia champion/ruolo presente nella
major patch corrente. Il documento usa `_id = championId_ROLE` e mantiene
`patchMajor`, `games`, `indexable` e `lastUpdate`. Sono inclusi soltanto i
ruoli giocabili `TOP`, `JUNGLE`, `MID`, `BOT` e `UTILITY`; i ruoli unknown non
entrano né nel conteggio né nella proporzione.

`indexable` è true quando il ruolo raggiunge almeno il 10% dei game del
champion. La rigenerazione aggiorna `lastUpdate` soltanto quando cambia quel
booleano, non quando cambia il numero dei game. La collection è ricostruibile
dai documenti `match` filtrando esclusivamente `patchMajor`.

### `profiles_indexable`

La collection contiene i profili che devono essere indicizzati: summoner con
`tracking=true` oppure con rank `MASTER_I`, `GRANDMASTER_I` o `CHALLENGER_I`.
Il documento usa `_id = puuid`, conserva `riotId`, `region` e `lastUpdate`, ed
è aggiornato con `$setOnInsert` per non cambiare il timestamp a ogni refresh.
Quando il profilo non soddisfa più la condizione viene rimosso dalla
collection; una successiva aggiunta genera un nuovo `lastUpdate`.

MariaDB conserva gli stessi modelli come JSON UTF-8 in `longtext`. Mongo conserva BSON strutturato per consentire projection e aggregation. I dati precedenti non vengono convertiti automaticamente: l'operatore li rimuove manualmente.

## Indici

La gestione degli indici è esterna al runtime e alla migration. `MongoDB` crea
soltanto le collection necessarie: non crea, verifica, modifica o rimuove
indici. Gli indici devono seguire i filtri e gli ordinamenti effettivi descritti
in [`08-query-inventory.md`](08-query-inventory.md) e sono verificati
operativamente con `explain("executionStats")`.

## Schema come codice

La struttura Mongo è posseduta dal codice applicativo per quanto riguarda le
collection e il formato dei documenti. Gli indici sono gestiti dal database
operator. Il database viene selezionato prima del bootstrap tramite
`App.isTesting()`.

Il bootstrap previsto è:

```text
  MongoClient
  -> databaseName = App.isTesting() ? "beebot_test" : "beebot"
  -> MongoDB.ensureCollections(database)
  -> MongoDB query/write methods
```

Il bootstrap crea soltanto le collection mancanti, in modo idempotente e senza
modifiche agli indici.

Il codice è la fonte di verità per collection e documenti; questo documento
descrive i pattern query che guidano la gestione operativa degli indici.

## Acceptance criteria

- ogni tabella LoL in scope ha una destinazione documentata;
- ogni collection ha `_id` nativo;
- ogni collection ha un owner nel registry dello schema applicativo;
- il database testing è separato da quello production tramite `App.isTesting()`;
- il bootstrap crea soltanto gli indici secondari dichiarati e non esegue drop o modifiche automatiche;
- rank/mastery, statistiche champion e participant hanno ownership esplicita;
- nessun participant usa un mega-oggetto `build`;
- nessun enum o team usa ordinali;
- ban e match ID seguono il formato canonico;
- esiste una strategia per eventi BSON oltre 16 MB.
