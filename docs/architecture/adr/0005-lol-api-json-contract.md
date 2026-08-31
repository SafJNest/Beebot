# ADR-0005: LoL API JSON contract

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

The current API serializes similar data through profile DTOs, leaderboard DTOs and mapper-specific nested records. The project accepts a public JSON cleanup and will update consumers.

## Decision

### Amendment 2026-08-27: profile-statistics leaf contract

`overview.statistics` serializes only the leaves
`champions.<championId>.<canonicalQueue>.<position>`. Queues are
canonicalized at ingestion and every game reaches a position, using
`UNKNOWN` when lane/position is missing or has no meaning. `total`, aggregates
per champion/queue/lane, `context`, `reference`, `winrate`, `kda` and all
`avg*` fields are not part of the persisted or HTTP payload; any legacy
views are rebuilt only in memory. Missing optional values are omitted,
not turned into zero. Arena fields are present only in the
`ARENA → UNKNOWN` leaf; `avgArenaPlacement` uses `arenaPlacementSum / games` from
that leaf. This amendment replaces the subsequent incompatible paragraphs
regarding champion rows.

`GET /profile/{puuid}/matchups` uses the distinct `profile_matchups`
collection and serializes only the leaves
`champions.<championId>.<canonicalQueue>.<position>.matchups.<opponentId>`.
This payload also contains no aggregates that remove queue/position, nor
`reference`, `winrate`, `kda` or `avg*`; the consumer composes its own views.
`ProfileStatistics` does not store a root aggregate `matchups` or `duoStats`.

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

## Amendment 2026-08-31: Records routes

`GET /api/lol/records` is the global Records overview. It returns the top five
rows for every available `RecordMetric`; `region` is optional and limits every
overview row to that League shard. `GET
/api/lol/records/{metric}` is the nested metric ladder and accepts the
same optional `region` plus `limit` and `offset` pagination. Both routes read
the canonical current-season record filter; only profile-record reads can
return 202 while their per-PUUID projection is generated.

HTTP controllers unwrap the domain-level `ApiResult<T>` through one shared
`LolApiResponses` mapper. `READY` and `PARTIAL` are successful JSON payloads;
`PENDING` is returned as the standard `LolApiError` envelope with HTTP 202.

Object-root and paginated responses add `metadata` at the same level
as the payload, without a `data` envelope. `ResponseMetadata` always contains
`pagination`, `lastUpdate`, `refresh` and `filter`, with `null` for non-applicable fields. `202`s report the same object inside `LolApiError`; search,
indexables and other pure lists remain unchanged arrays.

`LiveGame` is the object-root exception: its absent state uses `notInGame` on
the canonical payload and not `ResponseMetadata`, so an inactive game remains an
HTTP `200` success with null game fields and empty participants.

`BotStatus` (`GET /api/status`) is another object-root exception: it exposes operational
process metrics without `ResponseMetadata` and without a LoL envelope. Besides
`league`, `process`, `system` and `redis`, it includes `tracker` (`TrackerScheduler` jobs with in-memory progress),
`workers` (live snapshot of the two `DatabaseTracker` workers) and `riot` (full snapshot of `R4JQueue` per shard).

`ChampionStatistics.filter` remains part of the canonical object used by Redis
and the shared JSON codec, but the Spring mapper ignores it through a Jackson mixin because it
is an internal storage key and not part of the HTTP contract.

`AbstractEntity.isDirty()` is internal persistence state and is excluded from HTTP JSON. `Summoner`
serializes its five public identity fields (`puuid`, `riotId`, `region`, `level`, `icon`) explicitly so the canonical summoner shape remains complete
inside `SummonerView`, `SummonerLeaderboard` and `LeaderboardPage`. The former `summonerId` field is removed; PUUID is the only identity key. `region` is a `LeagueShard` enum that serializes as its `name()` string (for example `"EUW1"`).

## Compatibility

### Amendment 2026-08-30: records contract

`GET /profile/{puuid}/records` returns the canonical `ProfileRecordPage` for
the canonical filter. A missing projection returns the standard `202`
envelope and schedules asynchronous generation. `GET /api/lol/records` returns
the global overview; `GET /api/lol/records/{metric}` returns paginated
canonical `RecordPage` rows, optionally narrowed by region.
`ProfileRecord` is the success model in both responses; Spring owns no record
DTO. `gameShared` is omitted for individual records, rather than serialized as
`false`.

This is an intentional public JSON change. No compatibility aliases for old DTO class names are introduced. Consumers must migrate to the canonical field structure.

## Acceptance criteria

- Profile, search, leaderboard and match success responses do not require Spring DTOs.
- Profile and leaderboard share the same serialized summoner shape.
- Pagination, default region, queue defaults and aggregate endpoints remain explicit and tested.
- HTTP status mapping is centralized and storage-only fields are not exposed accidentally.
