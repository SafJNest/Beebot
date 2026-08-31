# Scope: summoner — Search

## Endpoint

`GET /api/lol/{shard}/search?q={riotId}`

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/search' --data-urlencode 'q=Player#EUW'
```

| Name | Position | Type | Required | Description |
|---|---|---|---:|---|
| `shard` | path | `LeagueShard` | yes | Shard for prefix search. `UNKNOWN` is rejected. |
| `q` | query | string | yes | Riot ID or prefix; `#` must be URL-encoded. |

The response is a list of up to 25 `SummonerView`. For search, `overview` is
a lightweight projection: `statistics` may be empty, but when present it uses
`champions` leaves, never precomputed
aggregates or derived fields. Full detail is in
[Profile by PUUID](profile-by-puuid.md).

## States and errors

| HTTP | `code` | When |
|---:|---|---|
| `200` | — | List, including empty. |
| `400` | `invalid_request` | Invalid shard or empty query. |
| `404` | `not_found` | Endpoint not found. |

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Parser: [`LolApiParameters`](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
