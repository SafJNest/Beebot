# Scope: summoner — Match facets

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/facets`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/facets?queue=TEAM_BUILDER_RANKED_SOLO&queue=RANKED_FLEX_SR&champion=Ahri&champion=238&lane=MID&season=current'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Riot shard for this profile. |
| `puuid` | path | string | yes | — | Canonical Riot PUUID of the summoner. |
| `queue` | query | repeated enum `GameQueueType` | no | all | Multiple values use OR. `ALL` is allowed only by itself. |
| `champion` | query | repeated champion name or ID | no | all | Multiple values use OR. |
| `lane` | query | repeated `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY` | no | all | Multiple values use OR. |
| `patch` | query | repeated `major.minor` | no | all | Multiple values use OR; values are normalized to the stored patch facet. |
| `season` | query | `current`, `previous`, `all` | no | `current` | Selects the time range used by `total` and categorical facets. Cannot be combined with `timeStart` or `timeEnd`. |
| `timeStart` | query | long | no | current season start | Inclusive Unix epoch ms lower bound. |
| `timeEnd` | query | long | no | current season end | Inclusive Unix epoch ms upper bound. |

Different filter dimensions combine with AND. `season` and explicit time
bounds are alternative ways to select the period, matching the match-list
contract. `all` uses the available match range after applying the categorical
filters and shard. With an explicit range, no named season is selected.

## `200` response

```json
{
  "total": 80,
  "facets": {
    "queue": [
      {"value": "TEAM_BUILDER_RANKED_SOLO", "total": 80, "selected": true},
      {"value": "RANKED_FLEX_SR", "total": 12, "selected": false}
    ],
    "lane": [
      {"value": "MID", "total": 80, "selected": true},
      {"value": "TOP", "total": 14, "selected": false}
    ],
    "champion": [
      {"value": 103, "total": 80, "selected": true},
      {"value": 238, "total": 40, "selected": false}
    ],
    "patch": [
      {"value": "16.20", "total": 46, "selected": false},
      {"value": "16.19", "total": 34, "selected": false}
    ],
    "season": [
      {"value": "current", "year": 2026, "total": 80, "selected": true},
      {"value": "previous", "year": 2025, "total": 44, "selected": false},
      {"value": "all", "year": null, "total": 124, "selected": false}
    ],
    "range": {"min": 1736899200000, "max": 1791763200000}
  },
  "metadata": {
    "pagination": {"page": null, "pageSize": null, "limit": null, "offset": null, "total": 80, "pages": null, "hasMore": null},
    "lastUpdate": null,
    "refresh": false,
    "filter": null
  }
}
```

Every categorical facet applies all selected categorical dimensions except
its own, then counts each available value. Thus each facet is disjunctive;
selected values in one dimension are ORed. `total` applies all selected
categorical filters and the selected period. Season totals apply the
categorical filters and each candidate season, while ignoring the selected
period. `range.min` and `range.max` are the earliest match start and latest
match end after the categorical filters and shard, ignoring the selected
period. Options are ordered by count descending, then value ascending.

The response is cached in Redis for 10 seconds per summoner, shard and complete
facet query. The query is encoded into the cache key so different selections
cannot reuse one another's response.

An empty result returns `total: 0` and null range extremes. Selected
categorical values remain in their facet with `total: 0`; unselected values
missing from the stored projection are omitted.

## Errors

| HTTP | Description |
|---:|---|
| `400` | Invalid filter, time range, season, or unsupported query parameter. |

## Owner

`LolController.facets`, `LolApiParameters.matchFacetQuery`,
`FacetsService.get` and `MongoDB.searchFacets` over the rebuildable
`match_facets` projection.
