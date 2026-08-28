# Scope: leaderboard — Rank distribution

## Endpoint

`GET /api/lol/leaderboard/rank-distribution`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard/rank-distribution' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `region` | query | enum `LeagueShard` | no | tutti gli shard | Shard da aggregare; omesso significa aggregato globale. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue da aggregare. |

## Risposta `200`

`LeaderboardDistribution`. La distribuzione contiene i tier competitivi da
`CHALLENGER` a `IRON`; `UNRANKED` non è incluso. Le entry a zero possono essere
presenti quando la combinazione è stata seminata dal rebuild.

Il payload HTTP non cambia; internamente il risultato può essere letto dallo
snapshot Mongo `leaderboard_aggregates` oppure rigenerato da `summoner.ranks{}`.

```json
{
  "entries": [
    {"key": "CHALLENGER", "players": 200},
    {"key": "GRANDMASTER", "players": 650},
    {"key": "MASTER", "players": 4210},
    {"key": "DIAMOND", "players": 18240},
    {"key": "EMERALD", "players": 48310},
    {"key": "PLATINUM", "players": 70120},
    {"key": "GOLD", "players": 104220},
    {"key": "SILVER", "players": 128440},
    {"key": "BRONZE", "players": 92110},
    {"key": "IRON", "players": 14230}
  ]
}
```

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Distribuzione disponibile, anche con entry a zero. |
| `400` | `invalid_request` | `region` o `queue` non validi. |
| `404` | `not_found` | Endpoint non trovato. |

## Owner

- Controller: [`LeaderboardController`](../../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java)
- Service: [`LeaderboardService`](../../../src/main/java/com/safjnest/lol/service/LeaderboardService.java)
- Success model: [`LeaderboardDistribution`](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardDistribution.java)
