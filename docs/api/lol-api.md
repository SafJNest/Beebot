# LoL HTTP API

Index of the public LoL HTTP documentation. The documentation is organized
by scope and each endpoint has a dedicated file with the same template:

1. endpoint and `curl` fetch;
2. parameters with position, type, required flag and default;
3. `200` response with complete JSON structure;
4. alternative states and errors;
5. owner in the code.

Examples use `http://localhost:8080` as the base URL.

## Index by scope

### Summoner

- [Search](summoner/search.md) — `GET /api/lol/{shard}/search`
- [Profile by PUUID](summoner/profile-by-puuid.md) — `GET /api/lol/{shard}/profile/{puuid}`
- [Profile refresh](summoner/profile-refresh.md) — `POST /api/lol/{shard}/profile/{puuid}/refresh`
- [Profile by Riot ID](summoner/profile-by-name.md) — `GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}`
- [Live game](summoner/livegame.md) — `GET /api/lol/{shard}/livegame/{puuid}` and `GET /api/lol/{shard}/livegame-by-name/{gameName}/{tagLine}`
- [Match list](summoner/matches.md) — `GET /api/lol/{shard}/profile/{puuid}/matches`
- [Rank history](summoner/rank-history.md) — `GET /api/lol/{shard}/profile/{puuid}/rank-history`
- [Activity](summoner/activity.md) — `GET /api/lol/{shard}/profile/{puuid}/activity`
- [Matchups](summoner/matchups.md) — `GET /api/lol/{shard}/profile/{puuid}/matchups`
- [Profile indexables](summoner/indexables.md) — `GET /api/lol/profile/indexables`

### Match

- [Match detail](match/detail.md) — `GET /api/lol/{shard}/match/{gameId}`

### AI

- [Training dataset](ai/training.md) — `GET /api/lol/ai/training`

### Champion

- [Champion page](champion/page.md) — `GET /api/lol/champion/{champion}`
- [Champion tier list](champion/tier-list.md) — `GET /api/lol/champions/tier-list`
- [Champion indexables](champion/indexables.md) — `GET /api/lol/champion/indexables`

### Leaderboard

- [Paginated leaderboard](leaderboard/page.md) — `GET /api/lol/leaderboard`
- [Rank distribution](leaderboard/rank-distribution.md) — `GET /api/lol/leaderboard/rank-distribution`
- [Top regions](leaderboard/top-regions.md) — `GET /api/lol/leaderboard/top-regions`

### Status

- [Bot status](status.md) — `GET /api/status`

## Common contract

- Endpoints are `GET`, except for explicit profile refresh which uses `POST`.
- Enum and textual values are case-insensitive and are trimmed with `trim()`.
- Success payloads use canonical models in `com.safjnest.lol.model`.
- Object-root or paginated responses expose `metadata` on the same root,
  without a `data` envelope. The four keys are always present: `pagination`,
  `lastUpdate`, `refresh` and `filter`; non-applicable values are `null`.
  `LiveGame` is an exception: it uses the boolean root `notInGame` and does not expose
  metadata. `BotStatus` (`GET /api/status`) is another object-root exception:
  it is not a shard-scoped LoL resource, does not expose metadata and includes `league`,
  `dispatchers`, `process`, `system`, `redis` and `mongo`. Pure lists,
  search and indexables remain unchanged arrays.
- Errors always use this envelope:

```json
{
  "status": 400,
  "code": "invalid_request",
  "message": "Invalid queue: must be one of: ..."
}
```

| HTTP | Meaning |
|---:|---|
| `200` | Response ready. Also includes `PARTIAL` payloads for profile and champion tier list. |
| `202` | Data missing, refresh has been queued in the background and the request should be retried. |
| `400` | Missing parameter, invalid type, unknown enum or unsupported combination. |
| `404` | Resource or endpoint not found. |
| `204` | Refresh completed, or ignored due to cooldown. |
| `405` | HTTP method not supported. |
| `500` | Unexpected server error. |

`202` responses always use the same format, with scope-specific code
and message. For example:

```json
{
  "status": 202,
  "code": "champion_data_pending",
  "message": "Champion data is being prepared",
  "metadata": {
    "pagination": null,
    "lastUpdate": null,
    "refresh": true,
    "filter": {}
  }
}
```

`refresh=true` means the deduplicated job has been queued. A ready response
uses `refresh=false`. `lastUpdate` is epoch millis, `pagination` contains
only applicable fields (`page`, `pageSize`, `limit`, `offset`, `total`,
`pages`, `hasMore`).

## Shared parameter types

| Parameter | Type | Values or constraints |
|---|---|---|
| `shard` / `region` | enum `LeagueShard` | `BR1`, `EUN1`, `EUW1`, `JP1`, `KR`, `LA1`, `LA2`, `NA1`, `OC1`, `TR1`, `RU`, `PBE1`, `SG2`, `PH2`, `ID1`, `VN2`, `TH2`, `TW2`, `ME1`. `UNKNOWN` is rejected. |
| `rank` | enum `TierType` | `CHALLENGER`, `GRANDMASTER`, `MASTER`, `DIAMOND`, `EMERALD`, `PLATINUM`, `GOLD`, `SILVER`, `BRONZE`, `IRON`, `UNRANKED`. |
| `queue` | enum `GameQueueType` | Use the R4J constant name; the public default is `TEAM_BUILDER_RANKED_SOLO`, normalized internally to `RANKED_SOLO_5X5` where required. |
| `role` | enum `LaneType` | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`. |
| `page` | integer | 1-based, `>= 1`. Default `1`. |
| `limit` | integer | Default and maximum depend on scope: leaderboard `1`-`50` (default `50`), match list `1`-`100` (default `20`). |
| `q`, `puuid`, `gameId`, `gameName`, `tagLine`, `champion` | string | Non-empty; path segments must be URL-encoded when they contain reserved characters. |

If `region` is omitted it means internal global aggregate; the public value is not
`GLOBAL`. `role` is available for champion and leaderboard and is rejected if
the queue does not support a lane.

## Source of truth

- [AGENTS.md](../../AGENTS.md) — API and documentation synchronization rules;
- [LoL architecture](../architecture/README.md) — ownership and ADRs;
- [ADR-0005](../architecture/adr/0005-lol-api-json-contract.md) — canonical JSON;
- [ADR-0006](../architecture/adr/0006-champion-api-contract.md) — champion page;
- [ADR-0013](../architecture/adr/0013-champion-tier-list.md) — champion tier list;
- [ADR-0007](../architecture/adr/0007-unified-api-result-and-parameters.md) — parameters and status;
- [ADR-0008](../architecture/adr/0008-endpoint-cache-and-async-lookups.md) — cache and async flows.
