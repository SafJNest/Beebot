# Scope: match — Detail

## Endpoint

`GET /api/lol/{shard}/match/{gameId}`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/match/EUW1_6789012345'
```

## Parameters

| Name | Position | Type | Required | Default | Description |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | yes | — | Shard used for the lookup. |
| `gameId` | path | string | yes | — | Full Riot ID, for example `EUW1_6789012345`. |

The complete Riot match ID is used for the Mongo lookup; the path shard
remains the reference region. A miss follows `Redis → Mongo → Riot`
and does not perform a synchronous Riot fetch in the HTTP request. Fetched
non-remake matches are persisted regardless of split age. New Mongo match
inserts also synchronously upsert Mongo `match_facets` participant documents; the
endpoint still reads Mongo and its response contract is unchanged.

## `200` response

Complete `Match`. The example contains two representative participants; the
actual response contains all available participants for the match.

```json
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
  "events": {
    "participants": {
      "1": "Qx7m2vW8-example-puuid",
      "2": "opponent-puuid"
    },
    "champion_kills": [
      {
        "timestamp": 184000,
        "killer": 1,
        "victim": 2,
        "assists": [],
        "killer_team": "BLUE",
        "bounty": 300,
        "shutdown_bounty": 0
      }
    ],
    "item_events": [
      {
        "timestamp": 60000,
        "event": "ITEM_PURCHASED",
        "participant": 1,
        "item": 1055,
        "before": 0,
        "after": 0,
        "gold_gain": 0
      }
    ],
    "skill_events": [
      {
        "timestamp": 30000,
        "participant": 1,
        "skill_slot": 3
      }
    ],
    "level_events": [
      {
        "timestamp": 30000,
        "participant": 1,
        "level": 2
      }
    ],
    "monster_events": [
      {
        "timestamp": 600000,
        "monster": "DRAGON",
        "subtype": "FIRE_DRAGON",
        "killer": 1,
        "killer_team": "BLUE",
        "assists": []
      }
    ],
    "building_events": [],
    "ward_events": [],
    "turret_plate_events": [],
    "objective_events": [],
    "game_events": [],
    "snapshots": [
      {
        "timestamp": 300000,
        "participants": {
          "1": {
            "total_gold": 2300,
            "current_gold": 500,
            "cs": 40,
            "xp": 2000,
            "level": 5
          }
        }
      }
    ],
    "dragon_soul": null
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
```

`participant.rankProgress` is the only rank contract for the participant. It contains
the current snapshot (`rank`, `lp`), the optional gain and the optional previous snapshot.
An untracked match inserted from stored rank records may contain only the current
snapshot; `gain` and the previous snapshot remain unset until the tracker completes
rank history. Both initial insertion and missing-snapshot completion select the
Flex rank for `RANKED_FLEX_SR`, the Premade rank for `RANKED_PREMADE_5X5`,
and the Solo/Duo rank for every other queue (including its enum alias).
When a stored rank map exists but has no entry for that selected ranked queue,
the current snapshot is `UNRANKED` with `lp: 0`; when the rank map is absent or null,
the rank is unknown and the snapshot remains unset. Match insertion and
missing-snapshot completion read local ranks only; they never start a Riot rank
refresh. Explicit refresh/profile flows and the tracker retain rank-refresh ownership.
The former top-level `rank`, `lp` and `gain` participant fields
have been intentionally removed from the public JSON.

## Shared match contract and timeline

The payload has the same match fields, bans and complete participants as one
`items[]` entry of the [match list](../summoner/matches.md). Only the detail adds
`events`. The requested profile's statistics are never copied to the match root.
All stored canonical participant fields are returned, including augments,
`starterItems`, `buildPath`, `skillOrder`, `boots`, `supportItem`, subteam and
placement. These lists describe IDs, not purchase timestamps or Arena rounds.

`events` is the entire stored `match_events` payload, decoded without a response
projection. Timestamp values are elapsed milliseconds within the match. Numeric
participant references (`participant`, `killer`, `victim`, `creator`, `assists`)
resolve through `events.participants`, whose keys are participant IDs and values
are PUUIDs. It contains the groups available in storage:

| Field | Contents |
|---|---|
| `participants` | Participant ID to PUUID mapping. |
| `champion_kills` | Killer, victim, assists, bounty, first blood/multikill information when available. |
| `item_events` | Purchases, sales, destroys and undo with item/before/after IDs and timestamp. |
| `skill_events`, `level_events` | Ability upgrades and champion level changes. |
| `monster_events`, `building_events` | Monsters, structures and their team/participant references. |
| `ward_events`, `turret_plate_events` | Ward actions and turret plates. |
| `objective_events`, `game_events` | Recorded objective and game events. |
| `snapshots` | Per-participant gold, CS, XP and level, normally sampled every five minutes plus final and available 15-minute frames. |
| `dragon_soul` | Recorded soul information or the existing last-non-elder-dragon inference with its `source`. |

Absent timeline storage returns `events: {}` while retaining match data and bans.
Missing groups in older timelines are not fabricated. Invalid stored encoding,
size, checksum or JSON remains an explicit read failure. A persisted match with
no timeline is not re-fetched on every detail request; existing owner timeline
repair commands retain that responsibility. Bans, participant fields and timeline
values are limited to data actually stored; this contract change does not
backfill missing data or add rune performance metrics or Arena round metadata.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `202` | `match_pending` | Match not yet persisted or analyzed; work has been queued. |
| `400` | `invalid_request` | Missing/invalid `shard` or `gameId`. |
| `404` | `not_found` | Match searched and marked as not found. |

```json
{
  "status": 202,
  "code": "match_pending",
  "message": "Match analysis is pending"
}
```

The `200` response adds root `metadata` with the match `lastUpdate` and
`refresh=false`; pagination and filter are `null`.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`MatchService`](../../../src/main/java/com/safjnest/lol/service/MatchService.java) — `getDetail`
- Success model: [`Match`](../../../src/main/java/com/safjnest/lol/model/match/Match.java)
