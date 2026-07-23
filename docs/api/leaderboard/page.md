# Scope: leaderboard — Paginated page

## Endpoint

`GET /api/lol/leaderboard`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/leaderboard' \
  --data-urlencode 'rank=DIAMOND' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'page=1' \
  --data-urlencode 'limit=2'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `rank` | query | enum `TierType` | no | tutti i rank | Seleziona il tier e tutte le sue divisioni. |
| `region` | query | enum `LeagueShard` | no | tutti gli shard | Shard da filtrare; omesso significa aggregato globale. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue della leaderboard, normalizzata a `RANKED_SOLO_5X5`. |
| `page` | query | integer | no | `1` | Pagina 1-based, `>= 1`. |
| `limit` | query | integer | no | `50` | Righe per pagina, da `1` a `50`. |

## Risposta `200`

`LeaderboardPage`. Ogni riga contiene lo stesso `SummonerView` del profilo.

```json
{
  "page": 1,
  "pageSize": 2,
  "total": 28431,
  "pages": 14216,
  "summoners": [
    {
      "position": 1,
      "summoner": {
        "summoner": {
          "summonerId": 12345678,
          "puuid": "Qx7m2vW8-example-puuid",
          "riotId": "Player#EUW",
          "region": "EUW1",
          "level": 527,
          "icon": 29
        },
        "ranks": [
          {
            "queue": "RANKED_SOLO_5X5",
            "tier": "DIAMOND_I",
            "lp": 98,
            "wins": 182,
            "losses": 151
          }
        ],
        "overview": {
          "statistics": {
            "timeStart": 1711929600000,
            "timeEnd": 1714521600000,
            "lastUpdate": 1714521600000,
            "oldestMatchAt": 1711933200000,
            "newestMatchAt": 1714518000000,
            "total": {
              "reference": null,
              "games": 42,
              "wins": 24,
              "kills": 286,
              "deaths": 198,
              "assists": 512,
              "damage": 684321,
              "damageBuilding": 91234,
              "damageTaken": 501223,
              "healing": 18342,
              "vision": 1210,
              "ward": 312,
              "wardKilled": 176,
              "cs": 8421,
              "gold": 558432,
              "lpGain": 286,
              "level": 756,
              "doubles": 6,
              "triples": 1,
              "quadruples": 0,
              "pentas": 0,
              "q": 118,
              "w": 91,
              "e": 67,
              "r": 42,
              "d": 18,
              "f": 16,
              "arenaFirst": 0,
              "arenaSecond": 0,
              "arenaThird": 0,
              "arenaPlacementSum": 0,
              "playtime": 110880000,
              "lastPlayedAt": 1714518000000,
              "killParticipationSum": 2714.5,
              "killParticipationGames": 42,
              "deathShareSum": 0.0,
              "deathShareGames": 0,
              "winrate": 57.14,
              "kda": 4.03,
              "avgKills": 6.81,
              "avgDeaths": 4.71,
              "avgAssists": 12.19,
              "avgDamage": 16293.36,
              "avgDamageBuilding": 2172.24,
              "avgDamageTaken": 11933.88,
              "avgHealing": 436.71,
              "avgVision": 28.81,
              "avgWard": 7.43,
              "avgWardKilled": 4.19,
              "avgCs": 200.5,
              "avgGold": 13296.0,
              "avgLpGain": 6.81,
              "avgLevel": 18.0,
              "avgArenaPlacement": 0.0,
              "avgKillParticipation": 64.63,
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
      },
      {
        "position": 2,
        "summoner": {
          "summoner": {
            "summonerId": 87654321,
            "puuid": "second-example-puuid",
            "riotId": "Second#EUW",
            "region": "EUW1",
            "level": 401,
            "icon": 12
          },
          "ranks": [
            {
              "queue": "RANKED_SOLO_5X5",
              "tier": "DIAMOND_I",
              "lp": 94,
              "wins": 175,
              "losses": 149
            }
          ],
          "overview": {
            "statistics": {
              "timeStart": 1711929600000,
              "timeEnd": 1714521600000,
              "lastUpdate": 1714521600000,
              "oldestMatchAt": 1711933200000,
              "newestMatchAt": 1714518000000,
              "total": {
                "reference": null,
                "games": 36,
                "wins": 20,
                "kills": 244,
                "deaths": 181,
                "assists": 430,
                "damage": 590000,
                "damageBuilding": 80100,
                "damageTaken": 480000,
                "healing": 15000,
                "vision": 1090,
                "ward": 280,
                "wardKilled": 154,
                "cs": 7200,
                "gold": 490000,
                "lpGain": 240,
                "level": 648,
                "doubles": 4,
                "triples": 0,
                "quadruples": 0,
                "pentas": 0,
                "q": 100,
                "w": 82,
                "e": 61,
                "r": 36,
                "d": 15,
                "f": 14,
                "arenaFirst": 0,
                "arenaSecond": 0,
                "arenaThird": 0,
                "arenaPlacementSum": 0,
                "playtime": 95040000,
                "lastPlayedAt": 1714510000000,
                "killParticipationSum": 2250.0,
                "killParticipationGames": 36,
                "deathShareSum": 0.0,
                "deathShareGames": 0,
                "winrate": 55.56,
                "kda": 3.73,
                "avgKills": 6.78,
                "avgDeaths": 5.03,
                "avgAssists": 11.94,
                "avgDamage": 16388.89,
                "avgDamageBuilding": 2225.0,
                "avgDamageTaken": 13333.33,
                "avgHealing": 416.67,
                "avgVision": 30.28,
                "avgWard": 7.78,
                "avgWardKilled": 4.28,
                "avgCs": 200.0,
                "avgGold": 13611.11,
                "avgLpGain": 6.67,
                "avgLevel": 18.0,
                "avgArenaPlacement": 0.0,
                "avgKillParticipation": 62.5,
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
      }
    ]
  }
```

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `202` | `leaderboard_pending` | Manca una statistica profilo; il refresh viene avviato e la pagina va richiesta di nuovo. |
| `400` | `invalid_request` | Enum non valido, `page < 1` o `limit` fuori da `1..50`. |
| `404` | `not_found` | Risorsa non trovata. |

## Owner

- Controller: [`LeaderboardController`](../../../src/main/java/com/safjnest/spring/controller/LeaderboardController.java)
- Service: [`LeaderboardService`](../../../src/main/java/com/safjnest/lol/service/LeaderboardService.java)
- Success model: [`LeaderboardPage`](../../../src/main/java/com/safjnest/lol/model/leaderboard/LeaderboardPage.java)
