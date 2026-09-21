# Scope: summoner — Live game

## Endpoint

- `GET /api/lol/{shard}/livegame/{puuid}`
- `GET /api/lol/{shard}/livegame-by-name/{gameName}/{tagLine}`

The second route resolves the Riot ID to PUUID and uses the same spectator flow
as the first.

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/livegame/Qx7m2vW8-example-puuid'
curl 'http://localhost:8080/api/lol/EUW1/livegame-by-name/Player/EUW'
```

## Parameters

| Name | Position | Type | Required | Description |
|---|---|---|---:|---|
| `shard` | path | enum `LeagueShard` | yes | Riot shard of the summoner. |
| `puuid` | path | string | first route | Canonical Riot PUUID. |
| `gameName` | path | string | second route | Part before `#` in the Riot ID. |
| `tagLine` | path | string | second route | Part after `#` in the Riot ID. |

Path segments must be URL-encoded when they contain reserved characters.

## `200` response

`LiveGame` exposes only required spectator data: game identifier and start time,
queue/mode/type/map, bans per team and participants. Timestamps are Unix
epoch milliseconds. Each participant includes champion, team, spell, rune and an
optional `profileOverview`. The latter exists only for profiles already present
in Redis/Mongo and contains `summoner`, `ranks`, `masteries` and three
`championStats`: the champion in the current game plus the two most-played distinct champions. The
read goes through the same profile-page entry point: a stale statistic
remains in the response and queues its refresh; no Riot fetch is performed
for the other participants.

In spectator mode, a participant without a PUUID remains in the roster with `championId`
and `riotId` equal to the champion name, plus `team`; all other participant
fields are `null`.

```json
{
  "notInGame": false,
  "gameId": 123456789,
  "startedAt": 1714521600000,
  "gameLength": 120,
  "platform": "EUW1",
  "queue": "RANKED_SOLO_5X5",
  "mode": "CLASSIC",
  "type": "MATCHED_GAME",
  "map": "SUMMONERS_RIFT",
  "bans": {"BLUE": [157, 238, 432], "RED": [266, 64, 412]},
  "participants": [{
    "puuid": "Qx7m2vW8-example-puuid",
    "riotId": "Player#EUW",
    "championId": 157,
    "icon": 29,
    "team": "BLUE",
    "summonerSpell1": 4,
    "summonerSpell2": 14,
    "runes": {
      "primaryTree": 8000,
      "keystone": 8005,
      "primaryRunes": [9111, 9104, 8014],
      "secondaryTree": 8300,
      "secondaryRunes": [8345, 8347],
      "statShards": [5008, 5008, 5010]
    },
    "profileOverview": {
      "summoner": {"puuid": "Qx7m2vW8-example-puuid", "riotId": "Player#EUW", "region": "EUW1", "level": 527, "icon": 29},
      "ranks": {
        "RANKED_SOLO_5X5": {"rank": "DIAMOND_I", "lp": 50, "wins": 10, "losses": 5}
      },
      "masteries": [{"championId": 157, "level": 7, "points": 200000}],
      "championStats": {
        "157": {"games": 42, "wins": 24, "kills": 286, "deaths": 198, "assists": 512},
        "238": {"games": 31, "wins": 18, "kills": 185, "deaths": 143, "assists": 204},
        "64": {"games": 22, "wins": 10, "kills": 124, "deaths": 118, "assists": 176}
      }
    }
  }]
}
```

When the summoner exists but is not in a game, the response remains `200`:

```json
{
  "notInGame": true,
  "gameId": null,
  "startedAt": null,
  "gameLength": null,
  "platform": null,
  "queue": null,
  "mode": null,
  "type": null,
  "map": null,
  "bans": {},
  "participants": []
}
```

## Cache and refresh

The active spectator result is stored for 60 seconds in the
`SPECTATOR_CURRENT` key; extending it to five minutes remains planned. The
`notInGame` case is not cached. `POST /profile/{puuid}/refresh` invalidates both
the Redis and R4J spectator caches; the next livegame GET
re-requests it from Riot.

Each spectator roster immediately queues a Mongo write with PUUID, Riot ID,
shard and icon. An R4J Summoner read is also queued for every participant: the HTTP response does not wait for either operation and does not
call Account API, because the Riot ID is already contained in the spectator
response. Once the queue completes, the Mongo profile is updated with
level and other canonical Summoner data.

## Errors

| HTTP | Description |
|---:|---|
| `400` | Invalid shard, PUUID, game name or tag line. |
| `404` | Summoner does not exist. |

## Owner

`LolController`, `SummonerService`, `lol.queue.scheduler.RiotScheduler` (via `QueueHandler`), `RedisKey.SPECTATOR_CURRENT`
and `LiveGame`.
