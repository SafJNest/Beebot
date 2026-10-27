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
      "id": 0,
      "gameId": "EUW1_6789012345",
      "leagueShard": "EUW1",
      "queue": "TEAM_BUILDER_RANKED_SOLO",
      "rank": "DIAMOND",
      "lastUpdate": 1714521600000,
      "bans": {
        "BLUE": [
          157,
          238,
          64,
          555,
          121
        ],
        "RED": [
          122,
          84,
          67,
          39,
          157
        ]
      },
      "timeStart": 1714514400000,
      "timeEnd": 1714521600000,
      "patch": "14.10",
      "participants": [
        {
          "id": 1,
          "summonerId": 0,
          "matchId": 0,
          "win": true,
          "kda": "8/3/11",
          "champion": 157,
          "lane": "MID",
          "team": "BLUE",
          "roleQuestId": 0,
          "rankProgress": {
            "rank": "DIAMOND_II",
            "lp": 74,
            "gain": 21,
            "previousRank": "DIAMOND_II",
            "previousLp": 53
          },
          "damage": 18432,
          "damageTaken": 14987,
          "damageBuilding": 4210,
          "healing": 912,
          "cs": 241,
          "goldEarned": 15321,
          "ward": 7,
          "wardKilled": 3,
          "visionScore": 27,
          "pings": {
            "danger": 2,
            "onMyWay": 1
          },
          "subTeam": 0,
          "subTeamPlacement": 0,
          "puuid": "Qx7m2vW8-example-puuid",
          "riotId": "Player",
          "riotTag": "EUW",
          "doubles": 1,
          "triples": 0,
          "quadruples": 0,
          "pentas": 0,
          "item0": 3031,
          "item1": 6673,
          "item2": 3006,
          "item3": 3036,
          "item4": 0,
          "item5": 0,
          "item6": 3363,
          "turretKills": 4,
          "q": 5,
          "w": 5,
          "e": 5,
          "r": 3,
          "d": 2,
          "f": 2,
          "summonerSpell1": 4,
          "summonerSpell2": 14,
          "primaryRunes": [
            8000,
            8010,
            9111,
            9104,
            8014
          ],
          "secondaryRunes": [
            8300,
            8347,
            8304
          ],
          "statsRunes": [
            5005,
            5008,
            5001
          ],
          "skillOrder": [
            1,
            3,
            2,
            1,
            1,
            4
          ],
          "augments": [],
          "starterItems": [
            1055
          ],
          "buildPath": [
            3031,
            6673
          ],
          "boots": 3006,
          "supportItem": 0,
          "championLevel": 18,
          "kills": 8,
          "deaths": 3,
          "assists": 11
        },
        {
          "id": 2,
          "summonerId": 0,
          "matchId": 0,
          "win": false,
          "kda": "3/8/4",
          "champion": 412,
          "lane": "UTILITY",
          "team": "RED",
          "roleQuestId": 2011,
          "rankProgress": {
            "rank": "DIAMOND_III",
            "lp": 52,
            "gain": -18,
            "previousRank": "DIAMOND_III",
            "previousLp": 70
          },
          "damage": 8432,
          "damageTaken": 22001,
          "damageBuilding": 1021,
          "healing": 120,
          "cs": 34,
          "goldEarned": 9234,
          "ward": 31,
          "wardKilled": 8,
          "visionScore": 68,
          "pings": {
            "danger": 5
          },
          "subTeam": 0,
          "subTeamPlacement": 0,
          "puuid": "opponent-puuid",
          "riotId": "Opponent",
          "riotTag": "EUW",
          "doubles": 0,
          "triples": 0,
          "quadruples": 0,
          "pentas": 0,
          "item0": 3870,
          "item1": 3190,
          "item2": 3107,
          "item3": 3110,
          "item4": 0,
          "item5": 0,
          "item6": 3364,
          "turretKills": 0,
          "q": 5,
          "w": 5,
          "e": 5,
          "r": 3,
          "d": 2,
          "f": 2,
          "summonerSpell1": 4,
          "summonerSpell2": 3,
          "primaryRunes": [
            8000,
            8360,
            8304,
            8347,
            8345
          ],
          "secondaryRunes": [
            8300,
            8210,
            8237
          ],
          "statsRunes": [
            5008,
            5001,
            5001
          ],
          "skillOrder": [
            3,
            2,
            1,
            3,
            3,
            4
          ],
          "augments": [],
          "starterItems": [
            3850
          ],
          "buildPath": [
            3107
          ],
          "boots": 3110,
          "supportItem": 3870,
          "championLevel": 17,
          "kills": 3,
          "deaths": 8,
          "assists": 4
        }
      ]
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 27,
  "hasMore": true,
  "facets": {
    "queue": [
      {
        "value": "RANKED_SOLO_5X5",
        "total": 80,
        "selected": true
      }
    ],
    "lane": [
      {
        "value": "MID",
        "total": 27,
        "selected": true
      },
      {
        "value": "TOP",
        "total": 14,
        "selected": false
      }
    ],
    "champion": [
      {
        "value": 103,
        "total": 27,
        "selected": true
      },
      {
        "value": 238,
        "total": 12,
        "selected": false
      }
    ],
    "patch": [
      {
        "value": "16.18",
        "total": 27,
        "selected": false
      }
    ],
    "season": [
      {
        "value": "current",
        "year": 2026,
        "total": 80,
        "selected": false
      },
      {
        "value": "previous",
        "year": 2025,
        "total": 27,
        "selected": true
      },
      {
        "value": "all",
        "year": null,
        "total": 107,
        "selected": false
      }
    ],
    "range": {
      "min": 1714514400000,
      "max": 1791763200000
    }
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

Each `items[]` element is a complete canonical `Match`, with general match
fields, bans and every stored `Participant` field. It uses the same model and
Mongo mapper as the [single-match detail](../match/detail.md); only the detail
loads the timeline under `events`. The list never reads `match_events` and omits
`events` entirely. Mongo `match` documents are read without a field projection.

The PUUID selects the matches; consumers locate their participant by
`participants[].puuid`. Requested-player `win`, `kda`, `championId`, `lane`,
`damage`, `cs`, `gold`, `vision`, `teamKills`, `items`, spells and rune fields
are no longer duplicated at the match root. Participant names remain canonical:
`champion`, `goldEarned`, `visionScore`, `item0`–`item6`, `summonerSpell1` and
`summonerSpell2`. Team kills and ratios can be calculated from the participants,
using `subTeam` where appropriate for Arena. This is a breaking list contract.

Full participants include IDs, champion level, rank progress, damage taken,
healing, wards, pings, spell counters, Arena subteam/placement, augments, starter
items, build path, boots, support item, skill order and all rune configurations.
The first primary/secondary rune entry is the tree and the remainder are the
selected runes. Timed purchases and skill upgrades remain in detail `events`.
No backfill, new Riot lookup, persistence schema change or Discord restyle is
part of this response change.

The page keeps existing fields and adds root `metadata` with
`pagination` (`limit`, `offset`, `total`, `hasMore`), the requested filter and
`refresh=false`; `lastUpdate` is `null`.

## Errors

| HTTP | Description |
|---:|---|
| `400` | Invalid or unsupported query parameter, champion, time range, limit, offset or sort. |

## Owner

`MatchService.getPage(puuid, Filter, ..., MatchFacetQuery)` composes the page
complete `Match` items from `MongoDB.findMatchPage` with total and facets from
`FacetsService.get`. The `Filter` and `MatchFacetQuery` carry the same shard and
match selectors; pagination and ordering stay separate.
