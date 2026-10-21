# Scope: summoner — Match list

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/matches`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/matches?queue=RANKED_SOLO_5X5&champion=Ahri&lane=MID&season=previous&limit=20&offset=0&sort=timeStart:desc'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Riot shard of the profile. |
| `puuid` | path | string | yes | — | Canonical Riot PUUID of the summoner. |
| `queue` | query | enum `GameQueueType` | no | all | Filter by queue. |
| `champion` | query | champion name or ID | no | all | Only matches where this profile played the selected champion. |
| `lane` | query | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY` | no | all | Filter by the profile's lane. |
| `patch` | query | `major.minor` | no | all | Filter by patch major version, for example `16.18`. |
| `season` | query | `current`, `previous`, `all` | no | current | Select a configured season range; `all` removes the time bounds. Cannot be combined with `timeStart` or `timeEnd`. |
| `limit` | query | integer | no | `20` | From `1` to `100`. |
| `offset` | query | integer | no | `0` | Zero-based offset, `>= 0`. |
| `timeStart` | query | long | no | current season start | Unix epoch ms inclusive. |
| `timeEnd` | query | long | no | current season end | Unix epoch ms inclusive. |
| `sort` | query | string | no | `timeStart:desc` | Only `timeStart:asc` or `timeStart:desc`. |

## `200` response

The list reads exclusively from matches already persisted in Mongo: it does not start
Riot calls, lookups or statistic regenerations.

```json
{
  "items": [
    {
      "gameId": "EUW1_6789012345",
      "queue": "RANKED_SOLO_5X5",
      "timeStart": 1714514400000,
      "timeEnd": 1714516500000,
      "win": true,
      "kda": "8/2/11",
      "championId": 103,
      "participants": [
        {
          "puuid": "Qx7m2vW8-example-puuid",
          "rankProgress": {
            "rank": "DIAMOND_II",
            "lp": 74,
            "gain": 21,
            "previousRank": "DIAMOND_II",
            "previousLp": 53
          }
        }
      ],
      "primaryRunes": [8112, 8143, 8138, 8105],
      "secondaryRunes": [8347, 8304],
      "statsRunes": [5008, 5008, 5011]
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 27,
  "hasMore": true,
  "facets": {
    "queue": [
      { "value": "RANKED_SOLO_5X5", "total": 80, "selected": true }
    ],
    "lane": [
      { "value": "MID", "total": 27, "selected": true },
      { "value": "TOP", "total": 14, "selected": false }
    ],
    "champion": [
      { "value": 103, "total": 27, "selected": true },
      { "value": 238, "total": 12, "selected": false }
    ],
    "patch": [
      { "value": "16.18", "total": 27, "selected": false }
    ],
    "season": [
      { "value": "current", "year": 2026, "total": 80, "selected": false },
      { "value": "previous", "year": 2025, "total": 27, "selected": true },
      { "value": "all", "year": null, "total": 107, "selected": false }
    ],
    "range": { "min": 1714514400000, "max": 1791763200000 }
  }
}
```

`facets` uses the same queue, champion, lane, patch and season/time filters as
the page. Each categorical facet excludes its own selected dimension while
applying the others, so the options show disjunctive counts. `range` follows
the categorical filters and shard while ignoring the selected period. Facet
values and counts come from the cached `FacetsService` result, shared with the
dedicated `/facets` endpoint; the page's root `total` remains the exact count
for the selected filters. The page lookup and facet lookup run concurrently;
the endpoint combines their results into one asynchronous HTTP response. Since
the facets result is cached for 10 seconds, root `total` may also be up to 10
seconds behind a newly written or rebuilt match projection.

Sorting uses `timeStart`; matches with the same start time have no guaranteed
relative order, so they may move between offset pages.

When `season` is supplied, `current` and `previous` are converted to the
configured season's inclusive `timeStart` and `timeEnd`; `all` uses zero for
both bounds, which means unbounded. `season` cannot be combined with explicit
`timeStart` or `timeEnd`. When `season` is omitted, one nonzero time bound uses
the requested range and a zero on the other bound is unbounded; when both are
zero, the current season is used. Unknown query parameters return `400`.
The effective lower bound applies to match start and the upper bound to match
end. Persistence also caps match start at the upper bound to restrict the
chronological index scan; valid matches start no later than they end, so this
does not change the selected matches or the exposed filter metadata.

`total` comes from the same `match_facets` aggregation as `facets`. It counts
matches that satisfy the same filters as `items` (`shard`, `queue`, `champion`,
`lane`, `patch` and the effective `timeStart`/`timeEnd` range selected directly
or through `season`), before applying `limit` and `offset`. The page items are
read from `match`; the page lookup and facet aggregation run concurrently.

Each result exposes the requested summoner's runes: the first entry of
`primaryRunes` and `secondaryRunes` is the tree, the others are the chosen runes;
`statsRunes` contains the three stat shards. Participants remain a
lightweight projection and do not include these configurations; when available,
each includes the already-persisted `rankProgress`. Top-level participant
fields `rank`, `lp` or `gain` no longer exist.

The page keeps existing fields and adds root `metadata` with
`pagination` (`limit`, `offset`, `total`, `hasMore`), the requested filter and
`refresh=false`; `lastUpdate` is `null`.

## Errors

| HTTP | Description |
|---:|---|
| `400` | Invalid or unsupported query parameter, champion, time range, limit, offset or sort. |

## Owner

`MatchService.getPage(puuid, Filter, ..., MatchFacetQuery)` composes the page
items from `MongoDB.findMatchResults` with total and facets from
`FacetsService.get`. The `Filter` and `MatchFacetQuery` carry the same shard and
match selectors; pagination and ordering stay separate.
