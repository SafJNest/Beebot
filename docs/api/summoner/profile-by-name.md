# Scope: summoner — Profile by Riot ID

## Endpoint

`GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}`

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile-by-name/Player/EUW'
```

| Name | Position | Type | Required | Description |
|---|---|---|---:|---|
| `shard` | path | `LeagueShard` | yes | Shard in which to resolve the Riot ID. |
| `gameName` | path | string | yes | Part before `#`. |
| `tagLine` | path | string | yes | Part after `#`. |

Path segments must be URL-encoded when necessary. After
resolution, the response is the same `SummonerView` as
[Profile by PUUID](profile-by-puuid.md), including the leaf-only contract
`overview.statistics.champions.<championId>.<CanonicalQueue>.<position>`.

The consumer derives total, averages, winrate, KDA and queue/lane breakdowns from the
leaves. There are no `total`, `queueStats`, `laneStats`, `championStats`,
`reference`, `context`, `winrate`, `kda` or `avg*` fields in the
statistics payload. The only derived classification allowed in the leaf is
`isOtp: true`, omitted for all other champions in the same queue. Rank and mastery remain
Redis/Mongo reads; the GET does not perform
synchronous Riot calls.

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | Profile available; may be stale with `metadata.refresh=true`. |
| `202` | `profile_pending` | Riot ID resolution or base profile in progress. |
| `400` | `invalid_request` | Missing/invalid path parameters. |
| `404` | `not_found` | Riot ID or profile not found. |

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
