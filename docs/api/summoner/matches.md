# Scope: summoner — Match list

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/matches`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/matches?queue=RANKED_SOLO_5X5&champion=Ahri&lane=MID&patch=16.18&limit=20&offset=0&sort=timeStart:desc'
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
  "total": 5131,
  "hasMore": true
}
```

Sorting always uses `timeStart` and `_id` as a technical tie-breaker, so
offset and pages remain stable.

When one time bound is nonzero, the endpoint uses the requested range and a
zero on the other bound is unbounded. When both are zero, it uses the current season.
Unknown query parameters return `400`.

`total` is the number of persisted matches that satisfy the same filters as
`items` (`shard`, `queue`, `champion`, `lane`, `patch`, `timeStart` and `timeEnd`), before applying
`limit` and `offset`.

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

`MatchService.getPage(puuid, Filter, ...)`, `MongoDB.findMatchResults(puuid, Filter, ...)`
and `MongoDB.countMatches(puuid, Filter)`. The `Filter` carries the shard and match
selectors; pagination and ordering stay separate.
