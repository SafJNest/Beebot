# Scope: leaderboard — Paginated page

## Endpoint

`GET /api/lol/leaderboard`

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard' \
  --data-urlencode 'rank=DIAMOND' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'role=UTILITY' \
  --data-urlencode 'otp=40' \
  --data-urlencode 'page=1' \
  --data-urlencode 'limit=50'
```

| Name | Type | Default | Description |
|---|---|---|---|
| `rank` | `TierType` | all | Tier and its divisions. |
| `region` | `LeagueShard` | all | Shard to filter. |
| `queue` | `GameQueueType` | `TEAM_BUILDER_RANKED_SOLO` | Leaderboard queue. |
| `role` | `LaneType` | all | Primary role: `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`; excludes profiles without statistics/primary lane and requires a queue with lanes. |
| `otp` | integer | all | OTP champion ID; requires `competitive.otpChampionId = otp`. Can be combined with `role`. |
| `page` | integer | `1` | 1-based page, at least `1`. |
| `limit` | integer | `50` | From `1` to `50`. |

## `200` response

`LeaderboardPage` contains `page`, `pageSize`, `total`, `pages` and
`summoners[]`. Each row is a `SummonerLeaderboard` with `position` and the same
`SummonerView` used by the profile. Profile statistics, when available,
follow the leaf-only contract:

```text
overview.statistics.champions.<championId>.<CanonicalQueue>.<position>
```

No duplicated aggregates (`total`, `queueStats`, `laneStats`,
`championStats`), `reference`, `context`, `winrate`, `kda` or `avg*` are exposed. A
leaf may optionally expose `isOtp: true` only for the single OTP champion
of its queue; the negative value is omitted. The UI computes its
own views from the leaves; for the full shape see
[Profile by PUUID](../summoner/profile-by-puuid.md).

If statistics are not available, the row keeps summoner and rank with
an empty overview and the refresh is queued; the page does not become a cache for
a separate aggregate.

Internally the page reads `competitive` for MMR/tier/role/OTP filtering, sorting and
pagination of PUUIDs; then it reads only the summoners of the page with an `$in`
on `_id`. `total` is resolved from Redis, then (without role) from
`leaderboard_aggregates`, then with `countDocuments()` on `competitive`.
A `role` or `otp` filter uses the count on the projection directly.
The HTTP payload does not change.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Page available. |
| `400` | `invalid_request` | Invalid enum, `page < 1` or `limit` outside `1..50`. |
| `404` | `not_found` | Resource not found. |

`metadata.pagination` exposes `page`, `pageSize`, `total` and `pages`.
