# Scope: summoner — Search

## Endpoint

`GET /api/lol/{shard}/search?q={riotId}`

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/search' --data-urlencode 'q=Player#EUW'
```

| Nome | Posizione | Tipo | Obbligatorio | Descrizione |
|---|---|---|---:|---|
| `shard` | path | `LeagueShard` | sì | Shard della prefix search. `UNKNOWN` è rifiutato. |
| `q` | query | string | sì | Riot ID o prefisso; `#` va URL-encoded. |

La risposta è una lista fino a 25 `SummonerView`. Per la search `overview` è
una proiezione leggera: `statistics` può essere vuoto, ma quando presente usa
le foglie `champions`, mai aggregate
precalcolati o campi derivati. Il dettaglio completo è in
[Profile by PUUID](profile-by-puuid.md).

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Lista, anche vuota. |
| `400` | `invalid_request` | Shard non valido o query vuota. |
| `404` | `not_found` | Endpoint non trovato. |

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Parser: [`LolApiParameters`](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
