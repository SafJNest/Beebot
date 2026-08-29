# Scope: leaderboard — Paginated page

## Endpoint

`GET /api/lol/leaderboard`

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard' \
  --data-urlencode 'rank=DIAMOND' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'role=UTILITY' \
  --data-urlencode 'page=1' \
  --data-urlencode 'limit=50'
```

| Nome | Tipo | Default | Descrizione |
|---|---|---|---|
| `rank` | `TierType` | tutti | Tier e relative divisioni. |
| `region` | `LeagueShard` | tutti | Shard da filtrare. |
| `queue` | `GameQueueType` | `TEAM_BUILDER_RANKED_SOLO` | Queue della leaderboard. |
| `role` | `LaneType` | tutti | Ruolo primario: `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`; esclude i profili senza statistiche/lane primaria e richiede una queue con lane. |
| `page` | integer | `1` | Pagina 1-based, almeno `1`. |
| `limit` | integer | `50` | Da `1` a `50`. |

## Risposta `200`

`LeaderboardPage` contiene `page`, `pageSize`, `total`, `pages` e
`summoners[]`. Ogni riga è un `SummonerLeaderboard` con `position` e lo stesso
`SummonerView` usato dal profilo. Le statistiche profilo, quando disponibili,
seguono il contratto leaf-only:

```text
overview.statistics.champions.<championId>.<CanonicalQueue>.<position>
```

Non sono esposti aggregate duplicati (`total`, `queueStats`, `laneStats`,
`championStats`), `reference`, `context`, `winrate`, `kda` o `avg*`. La UI
calcola le proprie viste a partire dalle foglie; per la forma completa vedi
[Profile by PUUID](../summoner/profile-by-puuid.md).

Se le statistics non sono disponibili, la riga mantiene summoner e rank con
overview vuota e il refresh viene accodato; la pagina non diventa una cache di
un aggregate separato.

Internamente la pagina legge `competitive` per filtro MMR/tier/ruolo, sort e
paginazione dei PUUID; poi legge soltanto i summoner della pagina con un `$in`
su `_id`. Il `total` è risolto da Redis, poi (senza ruolo) da
`leaderboard_aggregates`, quindi con `countDocuments()` su `competitive`.
Il payload HTTP non cambia.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Pagina disponibile. |
| `400` | `invalid_request` | Enum non valido, `page < 1` o `limit` fuori da `1..50`. |
| `404` | `not_found` | Risorsa non trovata. |

`metadata.pagination` espone `page`, `pageSize`, `total` e `pages`.
