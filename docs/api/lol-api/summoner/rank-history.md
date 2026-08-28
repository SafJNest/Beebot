# Scope: summoner — Rank history

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/rank-history`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/rank-history?queue=RANKED_SOLO_5X5&season=2025&timeStart=1760000000000&sort=timeStart:asc'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard Riot del profilo. |
| `puuid` | path | string | sì | — | PUUID Riot canonico del summoner. |
| `queue` | query | enum `GameQueueType` | no | `RANKED_SOLO_5X5` | Solo/Duo (`RANKED_SOLO_5X5` o alias `TEAM_BUILDER_RANKED_SOLO`) oppure `RANKED_FLEX_SR`. |
| `view` | query | string | no | — | Per ora accetta solo `profile`: gli ultimi 10 giorni, inclusi eventuali confini tra season. |
| `season` | query | integer | no | season corrente | Anno della season: ad esempio `2024`, `2025` o `2026`. |
| `patch` | query | string | no | — | Patch esatta `major.minor`, ad esempio `14.10`. |
| `timeStart` | query | long | no | `0` | Unix epoch ms inclusivo. Con `season`, la data iniziale viene troncata all'inizio della season e la fine resta quella della season. Senza `season`, usa la season corrente. |
| `timeEnd` | query | long | no | `0` | Unix epoch ms inclusivo; senza altri selettori, limita la fine della season corrente. |
| `sort` | query | string | no | `timeStart:desc` | `timeStart:asc` o `timeStart:desc`. |

La risposta non è paginata. Senza selettori restituisce tutti i match persistiti della season corrente nella queue selezionata. Senza `queue` restituisce esclusivamente Solo/Duo. I selettori `view`, `patch` e `season` sono mutuamente esclusivi; anche `timeStart` e `timeEnd` non possono essere usati insieme. L'unica combinazione ammessa è `season + timeStart`: il periodo è l'intersezione tra `timeStart` e la season selezionata. Per esempio, `season=2025&timeStart=10-ott-2025` restituisce ottobre--dicembre 2025, anche se la data richiesta supera il confine della season.

## Risposta `200`

```json
{
  "items": [
    {
      "gameId": "EUW1_6789012345",
      "queue": "RANKED_SOLO_5X5",
      "patch": "15.20",
      "timeStart": 1767225600000,
      "timeEnd": 1767227400000,
      "win": true,
      "lane": "BOT",
      "puuid": "Qx7m2vW8-example-puuid",
      "champion": 22,
      "enemyChampion": 67,
      "enemyPuuid": "enemy-adc-puuid",
      "duoChampion": 40,
      "duoPuuid": "ally-support-puuid",
      "duoEnemyChampion": 12,
      "duoEnemyPuuid": "enemy-support-puuid",
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
    "view": null,
    "season": 2025,
    "patch": null,
    "requestedTimeStart": 1760000000000,
    "requestedTimeEnd": null,
    "filter": {
      "queue": "RANKED_SOLO_5X5",
      "timeStart": 1760000000000,
      "timeEnd": 1767916800000
    }
  }
}
```

`enemyChampion` e `enemyPuuid` identificano l'avversario nella stessa lane. Per `BOT` e `UTILITY`, `duoChampion`/`duoPuuid` identificano l'alleato nella lane complementare e `duoEnemyChampion`/`duoEnemyPuuid` il suo avversario. Negli altri casi i campi duo sono `null`.

`metadata` riporta sempre il selettore richiesto e il `filter` effettivo: quindi il frontend sa quando `timeStart` è stato troncato al confine della season. La cache Redis contiene la projection completa di ogni season per `region`, `shard`, `puuid` e season, dura un giorno ed è invalidata nella season del match quando un match viene persistito o quando il suo `rankProgress` viene aggiornato.

## Errori

| HTTP | Descrizione |
|---:|---|
| `400` | Ogni errore identifica il parametro e spiega il vincolo: queue consentite, formato patch, season disponibili, view supportate o combinazione incompatibile. |
| `500` | Il range della season corrente non è disponibile. |

## Owner

`MatchService.getRankHistory`, `MongoDB.findRankHistoryMatches` e `RankHistoryMatch`.
