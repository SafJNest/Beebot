# Scope: summoner — Rank history

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/rank-history`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/rank-history?queue=RANKED_SOLO_5X5&timeStart=1767225600000&timeEnd=1775340000000&sort=timeStart:asc'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard Riot del profilo. |
| `puuid` | path | string | sì | — | PUUID Riot canonico del summoner. |
| `queue` | query | enum `GameQueueType` | no | tutte | Stesso filtro della match list. |
| `timeStart` | query | long | no | `0` | Unix epoch ms inclusivo, limitato all'inizio della season corrente. |
| `timeEnd` | query | long | no | `0` | Unix epoch ms inclusivo, limitato alla fine della season corrente. |
| `sort` | query | string | no | `timeStart:desc` | `timeStart:asc` o `timeStart:desc`. |

La risposta non è paginata: restituisce tutti i match persistiti della season corrente che soddisfano il filtro. Il range effettivo è l'intersezione tra i parametri richiesti e `SeasonUtils.getCurrentSeasonRange()`. Se non esiste intersezione, `items` è vuoto e `total` è `0`.

## Risposta `200`

```json
{
  "items": [
    {
      "gameId": "EUW1_6789012345",
      "queue": "RANKED_SOLO_5X5",
      "timeStart": 1770000000000,
      "timeEnd": 1770001800000,
      "win": true,
      "lane": "BOT",
      "champion": 22,
      "enemy": 67,
      "duo": 40,
      "duoEnemy": 12,
      "rankProgress": {
        "rank": "MASTER_I",
        "lp": 549,
        "gain": 28,
        "previousRank": "MASTER_I",
        "previousLp": 521
      }
    }
  ],
  "total": 1,
  "metadata": {
    "pagination": null,
    "lastUpdate": null,
    "refresh": false,
    "filter": {
      "queue": "RANKED_SOLO_5X5",
      "timeStart": 1767225600000,
      "timeEnd": 1775340000000
    }
  }
}
```

`enemy` è il champion avversario nella stessa lane. Per `BOT` e `UTILITY`, `duo` è il champion alleato nella lane complementare e `duoEnemy` quello avversario. Negli altri casi i due campi sono `null`.

La cache Redis contiene la projection completa della season per `region`, `shard`, `puuid` e season, dura un giorno ed è invalidata quando un match viene persistito o aggiornato.

## Errori

| HTTP | Descrizione |
|---:|---|
| `400` | Parametro, queue, intervallo temporale o sort non validi. |
| `500` | Il range della season corrente non è disponibile. |

## Owner

`MatchService.getRankHistory`, `MongoDB.findRankHistoryMatches` e `RankHistoryMatch`.
