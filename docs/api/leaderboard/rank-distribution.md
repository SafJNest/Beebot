# Scope: leaderboard — Rank distribution

## Endpoint

`GET /api/lol/leaderboard/rank-distribution`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard/rank-distribution' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `region` | query | enum `LeagueShard` | no | all shards | Shard to aggregate; omitted means global aggregate. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue to aggregate. |

## `200` response

`LeaderboardDistribution`. The distribution contains competitive tiers from
`CHALLENGER` to `IRON`; `UNRANKED` is not included. Zero entries may be
present when the combination was seeded by the rebuild.

The HTTP payload does not change; internally the result may be read from the
Mongo snapshot `leaderboard_aggregates` or regenerated from `competitive`.

```json
{
  "entries": [
    {"key": "CHALLENGER", "players": 200},
    {"key": "GRANDMASTER", "players": 650},
    {"key": "MASTER", "players": 4210},
    {"key": "DIAMOND", "players": 18240},
    {"key": "EMERALD", "players": 48310},
    {"key": "PLATINUM", "players": 70120},
    {"key": "GOLD", "players": 104220},
    {"key": "SILVER", "players": 128440},
    {"key": "BRONZE", "players": 92110},
    {"key": "IRON", "players": 14230}
  ]
}
```

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Distribution available, including with zero entries. |
| `400` | `invalid_request` | Invalid `region` or `queue`. |
| `404` | `not_found` | Endpoint not found. |

## Owner

- Controller: [`LeaderboardController`](../../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java)
- Service: [`LeaderboardService`](../../../src/main/java/com/safjnest/lol/service/LeaderboardService.java)
- Success model: [`LeaderboardDistribution`](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardDistribution.java)
