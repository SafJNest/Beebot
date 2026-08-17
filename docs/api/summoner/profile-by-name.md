# Scope: summoner — Profile by Riot ID

## Endpoint

`GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile-by-name/Player/EUW'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard su cui risolvere il Riot ID. |
| `gameName` | path | string | sì | — | Parte prima di `#` nel Riot ID. |
| `tagLine` | path | string | sì | — | Parte dopo `#` nel Riot ID. |

I segmenti path devono essere URL-encoded quando contengono caratteri
riservati. Dopo la risoluzione del Riot ID, la risposta usa esattamente lo
stesso `SummonerView` di [Profile by PUUID](profile-by-puuid.md).
Rank e mastery restano letture Redis/Mongo: se assenti sono liste vuote e non
avviano chiamate Riot dalla GET.

## Risposta `200`

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
    "recentMatches": []
  }
}
```

Il JSON mostra tutti i campi del contratto; liste e mappe possono essere vuote
quando il profilo è appena stato risolto. Gli elementi di
`overview.statistics.championStats` possono includere `context`, con una mappa
per ogni queue canonica e le relative lane; le queue senza ruoli usano una
sola lane `UNKNOWN`.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `202` | `profile_pending` | La risoluzione del Riot ID o del summoner base è in corso. |
| `400` | `invalid_request` | `shard`, `gameName` o `tagLine` mancanti/non validi. |
| `404` | `not_found` | Riot ID o profilo non trovati. |

La response root include `metadata` con filtro overview canonico e
`lastUpdate`. Se le statistics mancano o sono stale da una settimana, la
response disponibile resta `200` con `metadata.refresh=true` e header
`X-Profile-Refresh: true`.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
