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
| `rank` | `summoner.ranks[]` | `queue + region` | embedded |
| `masteries` | `summoner.masteries[]` | `championId` | embedded |
| `match` | `match` | `_id = fullGameId` | documento aggregato |
| `match.events` | `match_events` | `_id = fullGameId` | JSON separato, WiredTiger Zstandard |
| `participant` | `match.participants[]` | `puuid` dentro il match | embedded |
| `profile_statistics` | `profile_statistics` | `puuid + filterKey` | documento flat, `_id` casuale stabile |
| `champion` | `champion` | `championId` | catalogo |
| `champion_builds` | `champion_builds` | `filterKey + buildKey` | aggregate, non migrato |
| `champion_stats` | `champion_stats` | `filterKey + championId` | aggregate, non migrato |
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
  "ranks": [
    {
      "queue": "RANKED_SOLO_5X5",
      "region": "EUW1",
      "rank": "EMERALD_II",
      "lp": 90,
      "mmr": 1490,
      "wins": 100,
      "losses": 80,
      "lastUpdate": 1710000000000
    }
  ],
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
- gli identificativi numerici MariaDB non vengono scritti;
- il nuovo flusso non pulisce automaticamente dati precedenti; l'operatore elimina manualmente i payload obsoleti;
- `tracking=false` e gli altri default/null non vengono persistiti;
- rank e mastery non hanno collection operative separate;
- la leaderboard non duplica le righe rank: `leaderboard_aggregates` contiene solo snapshot ricostruibili di distribuzione e top-region;
- il rank identifica la coda tramite `queue`, non tramite un ID numerico;
- più regioni sono rappresentate da `region` nel rank quando il dataset lo richiede;
- non duplicare una seconda identità `Summoner` in wrapper o modelli di persistenza.

## `match`

Esempio concettuale:

```json
{
  "_id": "EUW1_134131",
  "fullGameId": "EUW1_134131",
  "gameId": "134131",
  "leagueShard": "EUW1",
  "queue": "TEAM_BUILDER_RANKED_SOLO",
  "rank": "EMERALD",
  "lastUpdate": 1710000000000,
  "timeStart": 1710000000000,
  "timeEnd": 1710002100000,
  "patch": "14.10.1",
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
- `leagueShard`, `queue` e `rank` sono stringhe R4J;
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

La leaderboard usa direttamente `summoner.ranks[]`. Mongo esegue `$unwind`, filtra
lo stesso elemento per queue, tier e regione, quindi usa `$facet` per totale e
pagina. L'ordinamento è `ranks.mmr DESC, _id ASC`, dove `_id` è il PUUID.

La pagina proietta soltanto identità summoner, il rank selezionato e le masteries;
`LeaderboardService` aggiunge le statistiche già presenti in cache o Mongo e
costruisce il modello canonico `LeaderboardPage`. Distribuzione e top-region
vengono salvati come snapshot in `leaderboard_aggregates` e vengono ricostruiti
ogni 12 ore. I filtri mai materializzati vengono calcolati lazy al successivo
accesso. Non vengono persistite righe o pagine leaderboard.

Ogni snapshot usa un `_id` stabile per tipo e filtro, contiene `entries`, il
filtro canonico e la lista aggregata. La collection è derivata e può essere
cancellata e ricostruita senza perdita dei rank.

## Collection derivate e aggregate

Le collection di statistiche e build hanno chiavi composte stabili e payload strutturati. `profile_statistics` salva `ProfileStatistics` direttamente a root; `champion_stats` mantiene il proprio aggregato e `build` mantiene la propria struttura. Non esiste un campo `metrics` nel documento summoner e non esiste una sorgente Kryo o `legacyPayload` nel nuovo documento profile.

### `profile_statistics`: chiave e indice

La chiave logica è `{ puuid, filterKey }`, con `filterKey = Filter.toSummonerKey()`. `filterKey` include champion, lane, queue, rank, rank behavior, patch, region, opponent, duo e periodo. Il runtime usa questa coppia per lookup e upsert applicativi, senza creare un indice secondario Mongo. Il PUUID da solo non identifica il documento, perché uno stesso account può avere più filtri; `_id` è un ObjectId casuale stabile e non è usato per il lookup. Il write path usa `$setOnInsert` per generarlo solo al primo upsert.

Il documento è flat e non contiene un root `statistics`. `recentMatches` è una query `MatchResult` separata con lo stesso filtro e non viene salvato dentro `ProfileStatistics`. Per il flusso completo e il runbook di diagnosi vedere [`profile-statistics-source-of-truth.md`](../architecture/profile-statistics-source-of-truth.md).

MariaDB conserva gli stessi modelli come JSON UTF-8 in `longtext`. Mongo conserva BSON strutturato per consentire projection e aggregation. I dati precedenti non vengono convertiti automaticamente: l'operatore li rimuove manualmente.

## Indici

Il bootstrap applicativo non crea né gestisce indici secondari. Mongo mantiene
soltanto l'indice nativo `_id`; eventuali indici già presenti non vengono
modificati o droppati automaticamente.

## Schema come codice

La struttura Mongo è posseduta dal codice applicativo per quanto riguarda le
collection e il formato dei documenti. Non esiste più un registry applicativo
degli indici.

Il bootstrap previsto è:

```text
MongoClient
  -> databaseName = App.isTesting() ? "beebot_test" : "beebot"
  -> MongoDB.ensureCollections(database)
  -> MongoDB query/write methods
```

Il bootstrap crea soltanto le collection mancanti, in modo idempotente e
sicuro rispetto agli avvii concorrenti. La gestione di eventuali indici
secondari è responsabilità operativa esterna al runtime.

Il codice è la fonte di verità operativa; questo documento descrive collection, chiavi e motivazione degli indici, ma non sostituisce le definizioni versionate nel registry.

## Acceptance criteria

- ogni tabella LoL in scope ha una destinazione documentata;
- ogni collection ha `_id` nativo;
- ogni collection ha un owner nel registry dello schema applicativo;
- il database testing è separato da quello production tramite `App.isTesting()`;
- il bootstrap non crea né droppa indici secondari;
- rank/mastery, statistiche champion e participant hanno ownership esplicita;
- nessun participant usa un mega-oggetto `build`;
- nessun enum o team usa ordinali;
- ban e match ID seguono il formato canonico;
- esiste una strategia per eventi BSON oltre 16 MB.
