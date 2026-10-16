# Scope: summoner — Search

## Endpoint

`GET /api/lol/search?q={riotId}`

```bash
curl --get 'http://localhost:8080/api/lol/search' --data-urlencode 'q=Player#EUW'
curl --get 'http://localhost:8080/api/lol/search' --data-urlencode 'q=Player#EUW' --data-urlencode 'shard=EUW1'
```

| Name | Position | Type | Required | Description |
|---|---|---|---:|---|
| `shard` | query | `LeagueShard` | no | Optional shard filter. Omit it to search across all active shards. `UNKNOWN` is rejected. |
| `q` | query | string | yes | Riot ID or prefix; `#` must be URL-encoded. |

The response is a list of up to 25 `SummonerView`. With no `shard`, Mongo
searches all active shards in one bounded query; with `shard`, it searches only
that shard. Mongo first selects the matching Riot ID prefix; those bounded
results are then ordered by descending Solo Queue MMR, calculated from the
canonical rank tier and LP. Unranked summoners are last; ties are ordered by
Riot ID, then PUUID. Each result retains its `region`. For search,
`overview` is a lightweight projection: `statistics` may be empty, but when
present it uses `champions` leaves, never precomputed aggregates or derived
fields. Full detail is in
[Profile by PUUID](profile-by-puuid.md).

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | List, including empty. |
| `400` | `invalid_request` | Invalid `shard` or empty query. |
| `404` | `not_found` | Endpoint not found. |

## Owner

- Controller: [`SummonerSearchController`](../../../src/main/java/com/safjnest/spring/controller/SummonerSearchController.java)
- Parser: [`LolApiParameters`](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
