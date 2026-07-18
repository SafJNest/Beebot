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
- collection LoL: prefisso `lol_`;
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
| `summoner` | `lol_summoners` | `_id = puuid` | documento aggregato |
| `rank` | `lol_summoners.ranks[]` | `queue + region` | embedded |
| `masteries` | `lol_summoners.masteries[]` | `championId` | embedded |
| `match` | `lol_matches` | `_id = fullGameId` | documento aggregato |
| `participant` | `lol_matches.participants[]` | `puuid` dentro il match | embedded |
| `profile_statistics` | `lol_profile_statistics` | `puuid + seasonStart` | separato |
| `leaderboard_distribution` | `lol_leaderboard_distribution` | `queue + rank + region` | aggregate |
| `rank` per ordinamento | `lol_leaderboard_entries` | `queue + region + puuid` | projection derivata |
| `champion` | `lol_champions` | `championId` | catalogo |
| `champion_builds` | `lol_champion_builds` | `filterKey + buildKey` | aggregate |
| `champion_stats` | `lol_champion_stats` | `filterKey + championId` | aggregate |

## `lol_summoners`

Esempio concettuale:

```json
{
  "_id": "puuid-value",
  "legacySummonerId": 123,
  "puuid": "puuid-value",
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

- `puuid` è `_id` e campo duplicato leggibile;
- `legacySummonerId` è obbligatorio durante la migrazione e può diventare opzionale dopo il cutover;
- rank e mastery non hanno collection operative separate;
- il rank identifica la coda tramite `queue`, non tramite un ID numerico;
- più regioni sono rappresentate da `region` nel rank quando il dataset lo richiede;
- non duplicare una seconda identità `Summoner` in wrapper o modelli di persistenza.

## `lol_matches`

Esempio concettuale:

```json
{
  "_id": "EUW1_134131",
  "legacyMatchId": 1945327,
  "gameId": "EUW1_134131",
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
  "events": {
    "championKills": [],
    "buildingEvents": [],
    "monsterEvents": [],
    "snapshots": []
  },
  "participants": []
}
```

### Regole match

- `_id` è sempre il Riot match ID completo;
- il solo numero Riot può essere accettato in input e normalizzato prima del lookup;
- `leagueShard`, `queue` e `rank` sono stringhe R4J;
- `bans` usa `BLUE` e `RED`, mai `0` e `1`;
- `events` è BSON strutturato quando il JSON è valido;
- eventi non convertibili mantengono `eventsRaw`, `eventsEncoding` e `eventsConversionStatus`;
- participant è embedded perché viene letto insieme al match;
- il documento deve essere controllato prima dell'upsert contro il limite BSON di 16 MB;
- se gli eventi rendono il documento troppo grande, vengono spostati in `lol_match_events` con `_id = matchId`.

## Participant embedded

Il participant mantiene i campi attuali ma senza un oggetto `build` generico:

```json
{
  "legacyParticipantId": 1,
  "puuid": "participant-puuid",
  "riotId": "GameName",
  "riotTag": "TAG",
  "win": true,
  "kda": "8/2/10",
  "champion": 157,
  "lane": "MIDDLE",
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

## Projection leaderboard

`lol_leaderboard_entries` è derivata da `ranks[]` e serve a evitare `$unwind` costosi sui documenti summoner durante l'ordinamento.

```json
{
  "_id": "RANKED_SOLO_5X5|EUW1|puuid-value",
  "puuid": "puuid-value",
  "queue": "RANKED_SOLO_5X5",
  "region": "EUW1",
  "rank": "EMERALD_II",
  "mmr": 1490,
  "lp": 90,
  "wins": 100,
  "losses": 80,
  "updatedAt": 1710000000000
}
```

Indici:

- `queue + region + rank + mmr DESC`;
- `queue + region + mmr DESC`;
- `queue + rank + mmr DESC`;
- `queue + mmr DESC`.

## Collection derivate e aggregate

Le collection di statistiche, build, distribution e metriche hanno chiavi composte stabili e payload strutturati. Non devono contenere stringhe Kryo come unica forma di verità.

Per compatibilità, durante la migrazione possono contenere:

- `legacyPayload`;
- `legacyEncoding`;
- `conversionStatus`;
- `convertedAt`.

## Indici minimi

### `lol_summoners`

- `_id` su `puuid`;
- `riotSearch + region`;
- `userId` sparse;
- `tracking + region`;

### `lol_matches`

- `participants.puuid + timeEnd DESC`;
- `leagueShard + queue + timeStart DESC`;
- `patch + queue`;
- `timeStart DESC`.

### Collection aggregate

- unique `puuid + seasonStart` per profile statistics;
- unique `filterKey + championId` per champion stats;
- `filterKey` per build;
- unique `queue + rank + region` per distribution.

## Nomi stabili degli indici

Il registry applicativo deve usare nomi stabili. La lista minima è:

| Collection | Nome indice | Specifica |
|---|---|---|
| `lol_summoners` | `summoners_riot_search_region` | `riotSearch ASC, region ASC` |
| `lol_summoners` | `summoners_user_id` | `userId ASC`, sparse |
| `lol_summoners` | `summoners_tracking_region` | `tracking ASC, region ASC` |
| `lol_matches` | `matches_participant_time` | `participants.puuid ASC, timeEnd DESC` |
| `lol_matches` | `matches_shard_queue_start` | `leagueShard ASC, queue ASC, timeStart DESC` |
| `lol_matches` | `matches_patch_queue` | `patch ASC, queue ASC` |
| `lol_matches` | `matches_start` | `timeStart DESC` |
| `lol_profile_statistics` | `profile_statistics_puuid_season` | `puuid ASC, seasonStart ASC`, unique |
| `lol_leaderboard_entries` | `leaderboard_queue_region_rank_mmr` | `queue ASC, region ASC, rank ASC, mmr DESC` |
| `lol_leaderboard_entries` | `leaderboard_queue_region_mmr` | `queue ASC, region ASC, mmr DESC` |
| `lol_champion_stats` | `champion_stats_filter_champion` | `filterKey ASC, championId ASC`, unique |
| `lol_champion_builds` | `champion_builds_filter` | `filterKey ASC` |
| `lol_leaderboard_distribution` | `distribution_queue_rank_region` | `queue ASC, rank ASC, region ASC`, unique |

L'indice `_id` è quello nativo Mongo e non va duplicato con un secondo indice equivalente.

## Schema e indici come codice

La struttura Mongo è posseduta dal codice applicativo. Non sono richiesti script manuali per creare collection o indici.

Il bootstrap previsto è:

```text
MongoClient
  -> databaseName = App.isTesting() ? "beebot_test" : "beebot"
  -> MongoSchemaInitializer.ensure(database)
  -> LeagueStore/repository
```

`MongoSchemaInitializer` mantiene una definizione versionata per ogni collection con:

- nome collection;
- chiave `_id`;
- indici richiesti;
- nome stabile di ogni indice;
- unique, sparse, collation e ordine quando applicabili.

Per ogni definizione il bootstrap deve:

1. creare la collection se non esiste;
2. leggere gli indici presenti;
3. creare quelli mancanti con `createIndex`;
4. considerare già valido un indice con stesso nome e stessa specifica;
5. fallire esplicitamente se uno stesso nome ha una specifica incompatibile;
6. non fare `drop` automatici in avvio.

La chiamata deve essere idempotente e sicura in caso di avvii concorrenti. Gli indici rimossi o modificati richiedono una migrazione schema esplicita, mai una modifica silenziosa del bootstrap.

Il codice è la fonte di verità operativa; questo documento descrive collection, chiavi e motivazione degli indici, ma non sostituisce le definizioni versionate nel registry.

## Acceptance criteria

- ogni tabella LoL ha una destinazione documentata;
- ogni collection ha `_id` e indici definiti;
- ogni collection ha un owner nel registry dello schema applicativo;
- il database testing è separato da quello production tramite `App.isTesting()`;
- il bootstrap degli indici è idempotente e non distruttivo;
- rank/mastery/metriche e participant hanno ownership esplicita;
- nessun participant usa un mega-oggetto `build`;
- nessun enum o team usa ordinali;
- ban e match ID seguono il formato canonico;
- esiste una strategia per eventi BSON oltre 16 MB.
