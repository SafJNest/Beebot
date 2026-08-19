# Scope: summoner — Search

## Endpoint

`GET /api/lol/{shard}/search`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/search' \
  --data-urlencode 'q=Player#EUW'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard in cui cercare. `UNKNOWN` è rifiutato. |
| `q` | query | string | sì | — | Riot ID o prefisso. La normalizzazione rimuove spazi, `-` e `#`; il carattere `#` va codificato come `%23`. |

La ricerca è una prefix search limitata a 25 risultati nello shard richiesto.

## Risposta `200`

`List<SummonerView>`.

```json
[
  {
    "summoner": {
      "puuid": "Qx7m2vW8-example-puuid",
      "riotId": "Player#EUW",
      "region": "EUW1",
      "level": 527,
      "icon": 29
    },
    "ranks": [
      {
        "queue": "RANKED_SOLO_5X5",
        "tier": "DIAMOND_II",
        "lp": 74,
        "wins": 128,
        "losses": 112
      }
    ],
    "overview": {
      "statistics": {
        "timeStart": 0,
        "timeEnd": 0,
        "lastUpdate": 0,
        "oldestMatchAt": 0,
        "newestMatchAt": 0,
        "total": {
          "reference": null,
          "games": 0,
          "wins": 0,
          "kills": 0,
          "deaths": 0,
          "assists": 0,
          "damage": 0,
          "damageBuilding": 0,
          "damageTaken": 0,
          "healing": 0,
          "vision": 0,
          "ward": 0,
          "wardKilled": 0,
          "cs": 0,
          "gold": 0,
          "lpGain": 0,
          "level": 0,
          "doubles": 0,
          "triples": 0,
          "quadruples": 0,
          "pentas": 0,
          "q": 0,
          "w": 0,
          "e": 0,
          "r": 0,
          "d": 0,
          "f": 0,
          "arenaFirst": 0,
          "arenaSecond": 0,
          "arenaThird": 0,
          "arenaPlacementSum": 0,
          "playtime": 0,
          "lastPlayedAt": 0,
          "killParticipationSum": 0.0,
          "killParticipationGames": 0,
          "deathShareSum": 0.0,
          "deathShareGames": 0,
          "winrate": 0.0,
          "kda": 0.0,
          "avgKills": 0.0,
          "avgDeaths": 0.0,
          "avgAssists": 0.0,
          "avgDamage": 0.0,
          "avgDamageBuilding": 0.0,
          "avgDamageTaken": 0.0,
          "avgHealing": 0.0,
          "avgVision": 0.0,
          "avgWard": 0.0,
          "avgWardKilled": 0.0,
          "avgCs": 0.0,
          "avgGold": 0.0,
          "avgLpGain": 0.0,
          "avgLevel": 0.0,
          "avgArenaPlacement": 0.0,
          "avgKillParticipation": null,
          "avgDeathShare": null
        },
        "queueStats": [],
        "laneStats": [],
        "championStats": [],
        "matchups": {},
        "duoStats": {},
        "pings": {},
        "spellOne": {},
        "spellTwo": {}
      },
      "masteries": [],
      "champions": {},
      "form": "",
      "mostPlayed": null,
      "recentMatches": []
    }
  }
]
```

Per la search `overview` è una struttura vuota; il rank Solo/Duo viene incluso
quando disponibile.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `400` | `invalid_request` | `shard` mancante/non valido o `q` vuota dopo la normalizzazione. |
| `404` | `not_found` | Endpoint non trovato. |

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Parser: [`LolApiParameters`](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
