# Scope: summoner — Profile by PUUID

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard del profilo. |
| `puuid` | path | string | sì | — | PUUID Riot canonico del summoner. |

## Risposta `200`

`SummonerView`. `overview.statistics`, `overview.masteries`, `overview.champions`
e `overview.recentMatches` fanno parte della risposta completa; i valori
temporali sono Unix epoch in millisecondi.

```json
{
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
      "tier": "DIAMOND_II",
      "lp": 74,
      "wins": 128,
      "losses": 112
    },
    {
      "queue": "RANKED_FLEX_SR",
      "tier": "PLATINUM_I",
      "lp": 31,
      "wins": 44,
      "losses": 38
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
      "queueStats": [
        {
          "reference": "TEAM_BUILDER_RANKED_SOLO",
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
        }
      ],
      "laneStats": [
        {
          "reference": "MID",
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
        }
      ],
      "championStats": [
        {
          "reference": 157,
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
        }
      ],
      "matchups": {
        "412": {
          "reference": 412,
          "games": 6,
          "wins": 4,
          "kills": 31,
          "deaths": 20,
          "assists": 62,
          "damage": 92000,
          "damageBuilding": 12000,
          "damageTaken": 65000,
          "healing": 2000,
          "vision": 180,
          "ward": 45,
          "wardKilled": 22,
          "cs": 1180,
          "gold": 79000,
          "lpGain": 42,
          "level": 108,
          "doubles": 0,
          "triples": 0,
          "quadruples": 0,
          "pentas": 0,
          "q": 16,
          "w": 12,
          "e": 9,
          "r": 6,
          "d": 2,
          "f": 2,
          "arenaFirst": 0,
          "arenaSecond": 0,
          "arenaThird": 0,
          "arenaPlacementSum": 0,
          "playtime": 15840000,
          "lastPlayedAt": 1714518000000,
          "killParticipationSum": 380.0,
          "killParticipationGames": 6,
          "deathShareSum": 0.0,
          "deathShareGames": 0,
          "winrate": 66.67,
          "kda": 4.65,
          "avgKills": 5.17,
          "avgDeaths": 3.33,
          "avgAssists": 10.33,
          "avgDamage": 15333.33,
          "avgDamageBuilding": 2000.0,
          "avgDamageTaken": 10833.33,
          "avgHealing": 333.33,
          "avgVision": 30.0,
          "avgWard": 7.5,
          "avgWardKilled": 3.67,
          "avgCs": 196.67,
          "avgGold": 13166.67,
          "avgLpGain": 7.0,
          "avgLevel": 18.0,
          "avgArenaPlacement": 0.0,
          "avgKillParticipation": 63.33,
          "avgDeathShare": null
        }
      },
      "duoStats": {},
      "pings": {
        "danger": 12,
        "onMyWay": 8
      },
      "spellOne": {
        "4": 36,
        "12": 6
      },
      "spellTwo": {
        "14": 38,
        "4": 4
      }
    },
    "masteries": [
      {
        "championId": 157,
        "level": 25,
        "points": 482310
      }
    ],
    "champions": {
      "157": {
        "name": "Yasuo",
        "image": "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/Yasuo.png"
      }
    },
    "form": "WLW",
    "mostPlayed": {
      "name": "Yasuo",
      "image": "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/Yasuo.png"
    },
    "recentMatches": [
      {
        "gameId": "EUW1_6789012345",
        "queue": "TEAM_BUILDER_RANKED_SOLO",
        "timeStart": 1714514400000,
        "timeEnd": 1714521600000,
        "win": true,
        "kda": "8/3/11",
        "championId": 157,
        "lane": "MID",
        "damage": 18432,
        "cs": 241,
        "gold": 15321,
        "vision": 27,
        "teamKills": 32,
        "items": [3031, 6673, 3006, 3036, 0, 0, 3363],
        "summonerSpells": [4, 14],
        "participants": [
          {
            "id": 1,
            "summonerId": 12345678,
            "matchId": 6789012345,
            "win": true,
            "kda": "8/3/11",
            "champion": 157,
            "lane": "MID",
            "team": "BLUE",
            "roleQuestId": 0,
            "rank": "DIAMOND_II",
            "lp": 74,
            "gain": 21,
            "damage": 18432,
            "damageTaken": 14987,
            "damageBuilding": 4210,
            "healing": 912,
            "cs": 241,
            "goldEarned": 15321,
            "ward": 7,
            "wardKilled": 3,
            "visionScore": 27,
            "pings": {"danger": 2},
            "subTeam": 0,
            "subTeamPlacement": 0,
            "puuid": "Qx7m2vW8-example-puuid",
            "riotId": "Player",
            "riotTag": "EUW",
            "level": 18,
            "doubles": 1,
            "triples": 0,
            "quadruples": 0,
            "pentas": 0,
            "item0": 3031,
            "item1": 6673,
            "item2": 3006,
            "item3": 3036,
            "item4": 0,
            "item5": 0,
            "item6": 3363,
            "turretKills": 4,
            "q": 5,
            "w": 5,
            "e": 5,
            "r": 3,
            "d": 2,
            "f": 2,
            "summonerSpell1": 4,
            "summonerSpell2": 14,
            "primaryRunes": [8010, 9111, 9104, 8014],
            "secondaryRunes": [8347, 8304],
            "statsRunes": [5005, 5008, 5001],
            "skillOrder": [1, 3, 2, 1, 1, 4],
            "augments": [],
            "starterItems": [1055],
            "buildPath": [3031, 6673],
            "boots": 3006,
            "supportItem": 0
          }
        ]
      }
    ]
  }
}
```

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `202` | `profile_pending` | Summoner, rank o mastery non sono ancora pronti; il caricamento parte in background e gli eventuali calcoli vengono accodati. |
| `400` | `invalid_request` | `shard` o `puuid` mancanti/non validi. |
| `404` | `not_found` | Profilo non trovato. |

Un profilo con statistiche aggregate ancora in generazione può comunque essere
restituito `200` come payload `PARTIAL` quando i componenti base sono pronti.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`ProfilePageService`](../../../src/main/java/com/safjnest/lol/service/ProfilePageService.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
