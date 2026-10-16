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
| `summoner` | string | — | PUUID or exact `Riot ID#tag`. Selects the tier from that player's requested queue rank and returns the page containing them. |
| `region` | `LeagueShard` | all | Shard to filter. |
| `queue` | `GameQueueType` | `TEAM_BUILDER_RANKED_SOLO` | Leaderboard queue. |
| `role` | `LaneType` | all | Primary role: `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`; excludes profiles without statistics/primary lane and requires a queue with lanes. |
| `otp` | integer | all | OTP champion ID; requires `competitive.otpChampionId = otp`. Can be combined with `role`. |
| `page` | integer | `1` | 1-based page, at least `1`. |
| `limit` | integer | `50` | From `1` to `50`. |

When `summoner` is present, `queue` (default Solo/Duo), `region` and `limit`
are retained. `rank`, `role`, `otp` and the requested `page` are ignored; the
page is computed from the player's rank and position. Riot IDs are matched
exactly against stored IDs, without autocomplete or fuzzy lookup. An absent
player, a player outside the requested region, or a player without a valid
rank in that queue returns `404 not_found`.

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

Every classified `Rank` inside the nested `summoner.ranks` may additionally
expose nullable `globalRanking` and `regionRanking`. They are one-based
positions for that rank's queue among all regions and its own region,
respectively. They do not replace row `position`: `position` remains the
position for the requested rank/region/role/OTP filters and pagination.

Internally the page reads `competitive` for MMR-range/role/OTP filtering, sorting and
pagination of PUUIDs. Rows sort by MMR descending, then ranked win/loss `score`
descending; then it reads only the summoners of the page with an `$in`
on `_id`. `total` is resolved from Redis, then (without role) from
`leaderboard_aggregates`, then with `countDocuments()` on `competitive`.
A `role` or `otp` filter uses the count on the projection directly.
Leaderboard page cache entries retain their existing one-day TTL and are not
invalidated when a competitive entry changes.
The `summoner` path bypasses this page cache. It uses a resident exact-division
Redis ZSET to compute the position and retrieve only the requested page; when
the needed segment is cold, an indexed count-before query and bounded Mongo
seek queries around the player's `competitive` tuple compute the same page
without loading or skipping preceding rows. Rows retain the `MMR DESC, score
DESC` order. Exact MMR-and-score ties still have no Mongo tie-breaker, so a
player in a tie may move between adjacent pages.
The `LeaderboardPage` data shape does not change; the `summoner` lookup fills
the existing metadata fields with its effective filter and computed pagination.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Page available. |
| `400` | `invalid_request` | Invalid queue/region/limit; without `summoner`, also invalid rank/role/OTP or `page < 1`. |
| `404` | `not_found` | Resource not found. |

`metadata.pagination` exposes `page`, `pageSize`, `total` and `pages`. For a
`summoner` lookup it also fills `limit`, the computed `offset`, and `hasMore`;
`metadata.filter` reports the effective queue, exact tier and requested region
(null when the lookup covers all regions). The tier is inferred from the
summoner's rank in that queue, while ignored `rank`, `role`, `otp` and `page`
parameters are not reflected in the filter.
