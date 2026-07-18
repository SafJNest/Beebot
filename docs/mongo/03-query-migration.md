# Fase 3: migrazione delle query

## Obiettivo

Sostituire le query MariaDB LoL con operazioni Mongo mantenendo comportamento API, cache, fallback Riot e il tipo di risultato corretto per ogni use case.

## Dipendenze

- [`README.md`](README.md);
- [`01-db-structure.md`](01-db-structure.md);
- [`02-document-dtos.md`](02-document-dtos.md);
- [`06-result-policy.md`](06-result-policy.md);
- ADR-0001, ADR-0002, ADR-0003, ADR-0005, ADR-0008;
- `LeagueService` come boundary LoL.

## Regola di ownership

`LeagueStore` è l'unico boundary di persistenza LoL.

I consumer non possono conoscere:

- `MongoCollection`;
- filtri BSON;
- SQL;
- `QueryResult`;
- `QueryRecord`;
- chiavi legacy numeriche.

Le operazioni del boundary devono essere nominate per comportamento e restituire il tipo minimo coerente con il caso d'uso:

```text
valore singolo       -> String/int/long/boolean/enum
projection locale    -> MongoRecord
lista scalare        -> List<T>
aggregate complesso  -> modello/DTO canonico esistente
```

Non aggiungere un DTO solo per rappresentare una projection già espressa da un modello esistente.

## Interfaccia concettuale

```text
findPuuid(query, shard)                         -> String
countMatches(puuid, filters)                    -> long
findSummaryProjection(puuid, filters)           -> MongoRecord
findSummoner(puuid, shard)                      -> Summoner
findSummonersByRiotId(query, shard, limit)      -> List<Summoner>
findTrackedSummoners(timeStart)                 -> List<Summoner>
findSummonerOverview(puuid, filters)            -> SummonerOverview
findProfileStatistics(puuid, seasonStart)       -> ProfileStatistics
findRecentMatches(puuid, after, until)          -> List<MatchResult>
findMatch(matchId, shard)                       -> Match
findMatchHistory(puuid, filters, page)          -> List<MatchResult>
findLeaderboard(filters, offset, limit)         -> LeaderboardPage
findLeaderboardDistribution(queue, region)      -> LeaderboardDistribution
findChampionBuild(filter)                       -> List<Build>
findChampionStatistics(filter, championId)      -> ChampionStatistics
```

Una query che legge solo `puuid` ritorna `String`. Una query con pochi campi usati localmente può ritornare `MongoRecord`. Il comando summoner overview usa `SummonerOverview` già esistente e non un DTO Mongo equivalente.

Le query massive usano un iteratore/cursore lazy di `MongoRecord` o di un tipo già mappato. Non caricare una collection completa in una `List` durante il backfill.

## Tabella query/read path

| Uso | Filtro Mongo | Indice | Risultato |
|---|---|---|---|
| profile base | `_id = puuid` | `_id` | `Summoner` |
| profile by Riot ID | `region + riotSearch prefix` | `region + riotSearch` | `List<Summoner>` limitata |
| projection locale | projection minima | indice della query | `MongoRecord` |
| rank profile | elemento `ranks.queue` | document lookup | `List<Rank>` |
| mastery profile | `masteries[]` | document lookup | `List<Mastery>` |
| recent matches | `participants.puuid + timeEnd` | compound index | `List<MatchResult>` |
| match detail | `_id = fullGameId` | `_id` | `Match` |
| match history | participant PUUID + filtri | participant/time indexes | `List<MatchResult>` |
| leaderboard | `queue + region + rank + mmr` | leaderboard projection | `LeaderboardPage` |
| rank distribution | `queue + region` | distribution key | `LeaderboardDistribution` |
| profile statistics | `puuid + seasonStart` | unique compound | `ProfileStatistics` |
| champion stats | `filterKey + championId` | unique compound | `ChampionStatistics` |
| champion build | `filterKey` | filter index | `List<Build>` |

## Search

La ricerca mantiene il valore normalizzato:

```text
riotSearch = lowercase(riotId senza spazi e senza #)
```

La query primaria è prefix search ancorata, non regex contains libera:

```text
region = shard
riotSearch >= prefix
riotSearch < prefixSuccessor
sort riotId
limit 25
```

Se viene richiesto substring search, serve una strategia separata con indice e cache versionata. Non aggiungere una regex libera come scorciatoia.

## Profile e overview

Il profile Mongo segue:

```text
Redis components -> LeagueStore -> Mongo -> Riot fallback
```

Rank e mastery embedded vengono convertiti direttamente in `Rank` e `Mastery`. Il comando summoner overview usa `SummonerOverview` già esistente, senza introdurre `SummonerOverviewDocument` o `SummonerOverviewDTO`.

Una projection temporanea con pochi campi può invece restare `MongoRecord` e non deve essere promossa a DTO.

## Match detail

Normalizzare sempre l'input:

```text
EUW1_134131 -> EUW1_134131
134131      -> shard + "_" + 134131
```

Il match detail segue:

```text
Redis detail -> Mongo match -> Tracker lookup -> Riot -> analisi match
```

Il risultato completo è `Match`. Le liste leggere usano `MatchResult`. I participant vengono convertiti in `Participant` quando richiesti dal modello.

Le ban devono essere lette come `BLUE` e `RED`; eventi e participant non devono essere ricostruiti nei controller.

## Match history e champion data

Il match contiene già i participant. La query non deve ricostruire un join summoner/participant/match.

Per payload leggeri:

- usare projection Mongo;
- convertire in `MatchResult`;
- includere solo i participant richiesti dal contratto;
- mantenere `Match` completo per il dettaglio.

Build e champion stats vengono letti per chiave di filtro. Il provider restituisce i modelli esistenti, non BSON o base64 al service.

Le query SQL legacy per custom builds non hanno una destinazione Mongo in questa migrazione e devono restare fuori scope fino a una decisione separata.

## Leaderboard

La leaderboard non deve eseguire `$unwind` completo di tutti i summoner per ogni richiesta.

Usare `lol_leaderboard_entries`:

```text
filter queue
filter rank quando presente
filter region quando presente
sort mmr DESC
skip offset
limit pageSize
```

I conteggi globali e le distribuzioni vengono letti da collection aggregate.

## Redis e versioning

Quando cambia il JSON serializzato:

1. aggiornare il codec;
2. aggiornare il contratto documentato;
3. versionare o invalidare Redis;
4. verificare `200`, `202`, `PARTIAL` e `PENDING`.

Mongo non sostituisce Redis come cache o coda effimera.

## Migrazione SQL

`QueryRecord` e `QueryResult` restano validi per MariaDB e per i domini non migrati. Il nuovo codice Mongo non deve importarli.

Non forzare conversioni inutili:

- query di un singolo dato → scalar;
- projection locale → `MongoRecord`;
- risposta applicativa complessa → modello esistente;
- lista di risultati complessi → `List<T>` del modello esistente.

## Acceptance criteria

- ogni metodo SQL LoL ha una destinazione Mongo;
- ogni risultato usa il tipo minimo sufficiente;
- nessun service dipende da BSON o SQL;
- overview e match usano modelli già presenti;
- scalar e projection locali non generano DTO inutili;
- search è indicizzata;
- profile evita letture rank/mastery separate;
- match detail usa Riot ID completo;
- Redis mantiene ownership di cache e stato asincrono.
