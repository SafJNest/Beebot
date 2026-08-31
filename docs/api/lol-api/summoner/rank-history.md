# Scope: summoner — Rank history

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/rank-history`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/rank-history?queue=RANKED_SOLO_5X5&season=2025&timeStart=1760000000000&sort=timeStart:asc'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Riot shard of the profile. |
| `puuid` | path | string | yes | — | Canonical Riot PUUID of the summoner. |
| `queue` | query | enum `GameQueueType` | no | `RANKED_SOLO_5X5` | Solo/Duo (`RANKED_SOLO_5X5` or alias `TEAM_BUILDER_RANKED_SOLO`) or `RANKED_FLEX_SR`. |
| `view` | query | string | no | — | For now only accepts `profile`: the last 10 days, including possible season boundaries. |
| `season` | query | integer | no | current season | Season year: e.g. `2024`, `2025` or `2026`. |
| `patch` | query | string | no | — | Exact patch `major.minor`, e.g. `14.10`. |
| `timeStart` | query | long | no | `0` | Unix epoch ms inclusive. With `season`, the start date is truncated to the beginning of the season and the end remains that of the season. Without `season`, it uses the current season. |
| `timeEnd` | query | long | no | `0` | Unix epoch ms inclusive; without other selectors, it limits to the end of the current season. |
| `sort` | query | string | no | `timeStart:desc` | `timeStart:asc` or `timeStart:desc`. |

The response is not paginated. Without selectors it returns all persisted matches of the current season in the selected queue. Without `queue` it returns Solo/Duo only. Selectors `view`, `patch` and `season` are mutually exclusive; `timeStart` and `timeEnd` also cannot be used together. The only allowed combination is `season + timeStart`: the period is the intersection between `timeStart` and the selected season. For example, `season=2025&timeStart=Oct-10-2025` returns October–December 2025, even if the requested date exceeds the season boundary.

## `200` response

```json
{
  "items": [
    {
      "gameId": "EUW1_6789012345",
      "queue": "RANKED_SOLO_5X5",
      "patch": "15.20",
      "timeStart": 1767225600000,
      "timeEnd": 1767227400000,
      "win": true,
      "lane": "BOT",
      "puuid": "Qx7m2vW8-example-puuid",
      "champion": 22,
      "enemyChampion": 67,
      "enemyPuuid": "enemy-adc-puuid",
      "duoChampion": 40,
      "duoPuuid": "ally-support-puuid",
      "duoEnemyChampion": 12,
      "duoEnemyPuuid": "enemy-support-puuid",
      "rankProgress": {
        "rank": "MASTER_I",
        "lp": 549,
        "gain": 28,
        "previousRank": "MASTER_I",
        "previousLp": 521
      }
    }
  ],
  "total": 1,
  "metadata": {
    "view": null,
    "season": 2025,
    "patch": null,
    "requestedTimeStart": 1760000000000,
    "requestedTimeEnd": null,
    "filter": {
      "queue": "RANKED_SOLO_5X5",
      "timeStart": 1760000000000,
      "timeEnd": 1767916800000
    }
  }
}
```

`enemyChampion` and `enemyPuuid` identify the opponent in the same lane. For `BOT` and `UTILITY`, `duoChampion`/`duoPuuid` identify the ally in the complementary lane and `duoEnemyChampion`/`duoEnemyPuuid` the ally's opponent. Otherwise duo fields are `null`.

`metadata` always reports the requested selector and the effective `filter`: so the frontend knows when `timeStart` was truncated to the season boundary. The Redis cache contains the full projection of each season for `region`, `shard`, `puuid` and season, lasts one day and is invalidated in the season of the match when a match is persisted or when its `rankProgress` is updated.

## Errors

| HTTP | Description |
|---:|---|
| `400` | Each error identifies the parameter and explains the constraint: allowed queues, patch format, available seasons, supported views or incompatible combination. |
| `500` | Current season range not available. |

## Owner

`MatchService.getRankHistory`, `MongoDB.findRankHistoryMatches` and `RankHistoryMatch`.
