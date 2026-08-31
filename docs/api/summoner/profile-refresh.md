# Scope: summoner — Profile refresh

## Endpoint

`POST /api/lol/{shard}/profile/{puuid}/refresh`

## Fetch

```bash
curl -X POST 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/refresh'
```

## Parameters

| Name | Position | Type | Required | Description |
|---|---|---|---:|---|
| `shard` | path | enum `LeagueShard` | yes | Riot shard of the profile. |
| `puuid` | path | string | yes | Canonical Riot PUUID of the summoner. |

## Behavior

The refresh first clears in a centralized way the R4J and Redis caches for Riot
Account, summoner, rank, mastery and spectator, without touching the matchlist. It then
updates in order Riot Account, summoner, rank and mastery via `RiotScheduler` (`QueueHandler.immediate(RiotScheduler.class, shard, ...)`).
Each component is persisted to Mongo and the freshly rebuilt Redis caches
remain available.

Profile GETs do not trigger Riot fetching of rank or mastery when the components
are missing; this POST is the only profile flow that updates them.

After profile verification, the POST internally updates
`summoner.lastSeenAt`. `ComputeScheduler` receives a single deduplicated `IMMEDIATE` job `profile-refresh:<puuid>` (`QueueHandler.immediate(ComputeScheduler.class, PROFILE, ...)`), which regenerates from scratch
statistics, activity, matchups and the canonical profile champion context. The canonical filters are:
overview, matchups and activity on the canonical season without patch, queue, lane
or champion. The job reads matches once via
Mongo cursor and saves the three documents only after all three
accumulators have completed.

An atomic Redis key `SUMMONER_REFRESH_COOLDOWN` enforces a two-minute
cooldown per `{shard, puuid}` pair. A request during cooldown does not start
additional Riot calls and is treated as completed.

The refresh does not request, queue or invalidate the matchlist. It invalidates spectator but
does not refetch it in the POST: the next livegame GET fetches it from Riot. The
fetching of recent matches remains the responsibility of a dedicated endpoint.

## `204` response

Refresh completed or ignored due to cooldown. After the response the
client can request `GET /profile/{puuid}` again.

## Errors

| HTTP | Description |
|---:|---|
| `400` | Invalid shard or PUUID. |
| `404` | Profile not present in Mongo. |

## Owner

`SummonerService.refreshAsync`, `SummonerService.refresh`,
`lol.queue.scheduler.RiotScheduler`, `ProfileService` and `lol.queue.scheduler.ComputeScheduler` (via `QueueHandler`). The
queue package refactor does not change endpoint, payload or presentation.
