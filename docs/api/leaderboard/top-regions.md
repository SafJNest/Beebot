# Scope: leaderboard — Top regions

## Endpoint

`GET /api/lol/leaderboard/top-regions`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard/top-regions' \
  --data-urlencode 'rank=DIAMOND' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `rank` | query | enum `TierType` | sì | — | Tier esatto da aggregare. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue da aggregare. |

## Risposta `200`

`LeaderboardDistribution`. `entries[].key` è lo shard e le entry sono ordinate
per numero di player decrescente, poi per nome.

Il payload HTTP non cambia; internamente il risultato può essere letto dallo
snapshot Mongo `leaderboard_aggregates` oppure rigenerato da `summoner.ranks{}`.

```json
{
  "entries": [
    {"key": "EUW1", "players": 18240},
    {"key": "KR", "players": 16110},
    {"key": "NA1", "players": 12480},
    {"key": "EUN1", "players": 9320},
    {"key": "BR1", "players": 6840}
  ]
}
```

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Distribuzione per regione disponibile. |
| `400` | `invalid_request` | `rank` mancante/non valido o `queue` non valida. |
| `404` | `not_found` | Endpoint non trovato. |

## Owner

- Controller: [`LeaderboardController`](../../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java)
- Service: [`LeaderboardService`](../../../src/main/java/com/safjnest/lol/service/LeaderboardService.java)
- Success model: [`LeaderboardDistribution`](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardDistribution.java)
