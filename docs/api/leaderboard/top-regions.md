# Scope: leaderboard — Top regions

## Endpoint

`GET /api/lol/leaderboard/top-regions`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard/top-regions' \
  --data-urlencode 'rank=DIAMOND' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `rank` | query | enum `TierType` | yes | — | Exact tier to aggregate. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue to aggregate. |

## `200` response

`LeaderboardDistribution`. `entries[].key` is the shard and entries are ordered
by descending player count, then by name.

The HTTP payload does not change; internally the result may be read from the
Mongo snapshot `leaderboard_aggregates` or regenerated from `competitive`.

```json
{
  "entries": [
    {"key": "EUW1", "players": 18240},
    {"key": "KR", "players": 16110},
    {"key": "NA1", "players": 12480},
    {"key": "EUN1", "players": 9320},
    {"key": "BR1", "players": 6840}
  ]
}
```

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Distribution by region available. |
| `400` | `invalid_request` | Missing/invalid `rank` or invalid `queue`. |
| `404` | `not_found` | Endpoint not found. |

## Owner

- Controller: [`LeaderboardController`](../../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java)
- Service: [`LeaderboardService`](../../../src/main/java/com/safjnest/lol/service/LeaderboardService.java)
- Success model: [`LeaderboardDistribution`](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardDistribution.java)
