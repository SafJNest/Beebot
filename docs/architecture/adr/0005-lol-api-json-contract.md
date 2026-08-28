# ADR-0005: LoL API JSON contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The current API serializes similar data through profile DTOs, leaderboard DTOs and mapper-specific nested records. The project accepts a public JSON cleanup and will update consumers.

## Decision

### Amendment 2026-08-27: profile-statistics leaf contract

`overview.statistics` serializza soltanto le foglie
`champions.<championId>.<canonicalQueue>.<position>`. Le queue sono
canonicalizzate all'ingestion e ogni partita raggiunge una posizione, usando
`UNKNOWN` quando lane/position manca o non ha significato. `total`, aggregati
per champion/queue/lane, `context`, `reference`, `winrate`, `kda` e tutti i
campi `avg*` non fanno parte del payload persistito o HTTP; eventuali viste
legacy sono ricostruite solo in memoria. I valori opzionali assenti sono omessi,
non trasformati in zero. I campi Arena sono presenti soltanto nella foglia
`ARENA → UNKNOWN`; `avgArenaPlacement` usa `arenaPlacementSum / games` di
quella foglia. Questo emendamento sostituisce i paragrafi
successivi incompatibili relativi alle champion rows.

`GET /profile/{puuid}/matchups` usa la collection distinta `profile_matchups`
e serializza le sole foglie
`champions.<championId>.<canonicalQueue>.<position>.matchups.<opponentId>`.
Anche questo payload non contiene aggregate che rimuovono queue/position, né
`reference`, `winrate`, `kda` o `avg*`; il consumer compone le proprie viste.
`ProfileStatistics` non conserva un aggregate root `matchups` o `duoStats`.

Success payloads use canonical models from `lol.model`. Spring retains only HTTP error models.

Profile exposes the complete `SummonerView` shape. Leaderboard exposes page metadata and rows of `SummonerLeaderboard`, each with the same nested `summoner` view.

Profile statistics expose the canonical leaf map described above.

The leaderboard contract remains:

```text
page
pageSize
total
pages
summoners[]
  position
  summoner
```

`region` remains optional with internal default `GLOBAL`. `queue` remains optional with the existing solo ranked default. Leaderboard pages remain fixed at 50 elements.

Rank distribution and top-regions remain non-paginated and continue using their persistent aggregate/cache flow.

HTTP controllers unwrap the domain-level `ApiResult<T>` through one shared
`LolApiResponses` mapper. `READY` and `PARTIAL` are successful JSON payloads;
`PENDING` is returned as the standard `LolApiError` envelope with HTTP 202.

Le response root oggetto e paginate aggiungono `metadata` allo stesso livello
del payload, senza envelope `data`. `ResponseMetadata` contiene sempre
`pagination`, `lastUpdate`, `refresh` e `filter`, con `null` per i campi non
applicabili. I `202` riportano lo stesso oggetto dentro `LolApiError`; search,
indexables e le altre liste pure restano array invariati.

`LiveGame` è l'eccezione object-root: il suo stato assente usa `notInGame` sul
payload canonico e non `ResponseMetadata`, così una partita non attiva resta un
successo HTTP `200` con campi game null e participants vuoti.

`BotStatus` (`GET /api/status`) è un'altra eccezione object-root: espone metriche
operative del processo senza `ResponseMetadata` e senza envelope LoL. Oltre a
`league`, `process`, `system` e `redis`, include `tracker` (job del
`TrackerScheduler` con progresso in memoria), `workers` (snapshot live dei
due worker `DatabaseTracker`) e `riot` (snapshot completo di `R4JQueue` per shard).

`ChampionStatistics.filter` remains part of the canonical object used by Redis
and the shared JSON codec, but the Spring mapper ignores it through a Jackson mixin because it
is an internal storage key and not part of the HTTP contract.

`AbstractEntity.isDirty()` is internal persistence state and is excluded from HTTP JSON. `Summoner`
serializes its five public identity fields (`puuid`, `riotId`, `region`, `level`, `icon`) explicitly so the canonical summoner shape remains complete
inside `SummonerView`, `SummonerLeaderboard` and `LeaderboardPage`. The former `summonerId` field is removed; PUUID is the only identity key. `region` is a `LeagueShard` enum that serializes as its `name()` string (for example `"EUW1"`).

## Compatibility

This is an intentional public JSON change. No compatibility aliases for old DTO class names are introduced. Consumers must migrate to the canonical field structure.

## Acceptance criteria

- Profile, search, leaderboard and match success responses do not require Spring DTOs.
- Profile and leaderboard share the same serialized summoner shape.
- Pagination, default region, queue defaults and aggregate endpoints remain explicit and tested.
- HTTP status mapping is centralized and storage-only fields are not exposed accidentally.
