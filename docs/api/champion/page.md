# Scope: champion — Page

## Endpoint

`GET /api/lol/champion/{champion}`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/champion/Thresh' \
  --data-urlencode 'patch=14.10' \
  --data-urlencode 'rank=EMERALD' \
  --data-urlencode 'region=EUW1' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'role=UTILITY'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `champion` | path | string | sì | — | Nome champion, case-insensitive; la ricerca usa la normalizzazione statica del champion. |
| `patch` | query | string `major.minor` | no | patch corrente | Patch del dataset. Il valore deve mantenere entrambe le componenti, ad esempio `14.10`. |
| `rank` | query | enum `TierType` | no | nessun filtro | Tier minimo del dataset; `EMERALD` include Emerald e tier superiori secondo il filtro. |
| `region` | query | enum `LeagueShard` | no | aggregato `GLOBAL` interno | Shard da aggregare. Non inviare `GLOBAL` o `UNKNOWN`. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue del dataset. |
| `role` | query | enum `LaneType` | no | nessun filtro | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`; richiede una queue con lane. |

## Risposta `200`

`ChampionView`. Il campo interno `filter` non fa parte del JSON HTTP.

Se il refresh è terminato ma il filtro non contiene giochi/build validi, la
stessa risposta `200` contiene overview a zero e liste vuote. Il frontend deve
renderizzare lo stato senza dati; non viene mantenuto un `202` indefinito.

```json
{
  "champion": {
    "id": 412,
    "name": "Thresh",
    "image": "https://ddragon.leagueoflegends.com/cdn/14.10.1/img/champion/Thresh.png"
  },
  "stats": {
    "overview": {
      "games": 12540,
      "picks": 12540,
      "bans": 1830,
      "wins": 6646,
      "winrate": 0.53,
      "pickrate": 0.084,
      "banrate": 0.012,
      "kda": 2.91,
      "csPerMinute": 1.21,
      "goldPerMinute": 312.4,
      "damageProfile": {
        "physical": 0.41,
        "magic": 0.52,
        "trueDamage": 0.07
      }
    },
    "laneStats": [
      {
        "lane": "UTILITY",
        "games": 12540,
        "winrate": 0.53
      }
    ],
    "matchups": {
      "MatchupKey[champion=157, lane=UTILITY]": {
        "champion": 157,
        "lane": "UTILITY",
        "matches": 312,
        "wins": 145,
        "winrate": 0.465,
        "deltaWinrate": -0.065,
        "goldDiffAt15": -84,
        "csDiffAt15": -1.4,
        "soloKillRate": 0.031,
        "killParticipation": 0.58,
        "opponentBanRate": 0.021,
        "metricGames": 286
      }
    },
    "laneSynergies": [
      {
        "allyChampion": 157,
        "allyLane": "MID",
        "matches": 488,
        "wins": 278,
        "winrate": 0.57,
        "pickrate": 0.039
      }
    ],
    "powerCurve": [
      {
        "durationBucket": "0-20",
        "games": 2140,
        "wins": 1113,
        "winrate": 0.52
      },
      {
        "durationBucket": "20-30",
        "games": 6940,
        "wins": 3750,
        "winrate": 0.54
      }
    ],
    "trend": {
      "previousPatch": "14.9",
      "games": 11880,
      "winrate": 0.525,
      "deltaWinrate": 0.005
    }
  },
  "build": {
    "games": 12540,
    "wins": 6646,
    "winrate": 0.53,
    "coreBuilds": [
      {
        "id": "3865-3100-3157",
        "items": [3865, 3100, 3157],
        "matches": 1820,
        "wins": 1010,
        "winrate": 0.555,
        "pickrate": 0.145
      }
    ],
    "coreItems": [
      {
        "id": "3100",
        "matches": 7420,
        "wins": 4010,
        "winrate": 0.54,
        "pickrate": 0.592,
        "spell1": 31,
        "spell2": 0
      }
    ],
    "starters": [
      {
        "id": "3865-2",
        "matches": 10400,
        "wins": 5560,
        "winrate": 0.535,
        "pickrate": 0.829,
        "spell1": 38,
        "spell2": 65
      }
    ],
    "boots": [
      {
        "id": "3117",
        "matches": 4320,
        "wins": 2390,
        "winrate": 0.553,
        "pickrate": 0.345,
        "spell1": 31,
        "spell2": 17
      }
    ],
    "supportItems": [
      {
        "id": "3871",
        "matches": 3860,
        "wins": 2150,
        "winrate": 0.557,
        "pickrate": 0.308,
        "spell1": 38,
        "spell2": 71
      }
    ],
    "slots": [
      [
        {
          "id": "3100",
          "matches": 7420,
          "wins": 4010,
          "winrate": 0.54,
          "pickrate": 0.592,
          "spell1": 31,
          "spell2": 0
        }
      ],
      [
        {
          "id": "3157",
          "matches": 5160,
          "wins": 2830,
          "winrate": 0.548,
          "pickrate": 0.411,
          "spell1": 31,
          "spell2": 57
        }
      ]
    ],
    "runes": [
      {
        "id": "rune-config-example",
        "configuration": {
          "primaryTree": 8400,
          "keystone": 8439,
          "primaryRunes": [8437, 8463, 8242],
          "secondaryTree": 8300,
          "secondaryRunes": [8347, 8304],
          "statShards": [5008, 5001, 5011]
        },
        "matches": 3180,
        "wins": 1740,
        "winrate": 0.547,
        "pickrate": 0.254
      }
    ],
    "summonerSpells": [
      {
        "id": "4-14",
        "matches": 8210,
        "wins": 4430,
        "winrate": 0.539,
        "pickrate": 0.655,
        "spell1": 4,
        "spell2": 14
      }
    ],
    "skillOrders": [
      {
        "id": "3-2-1-3-3-4",
        "order": [3, 2, 1, 3, 3, 4],
        "matches": 2210,
        "wins": 1220,
        "winrate": 0.552,
        "pickrate": 0.176
      }
    ],
    "prismatics": [],
    "augments": [
      [],
      [],
      [],
      []
    ]
  }
}
```

Le liste build sono categorie indipendenti e ogni categoria contiene al
massimo tre opzioni. `coreBuilds`, `coreItems` e `slots` includono solo item
validi di depth 3 presenti nell'inventario finale; i pezzi intermedi vengono
esclusi.

Le opzioni in `skillOrders` sono sequenze al livello massimo osservato: 18
quando disponibile, altrimenti la massima lunghezza disponibile. `matches` e
`wins` includono ogni game il cui ordine osservato è un prefisso della
sequenza, quindi anche i game conclusi prima di quel livello contribuiscono
alla combinazione compatibile.
starter, boots, support items, consumabili, trinket, prismatics e augment
mantengono le categorie e le esclusioni esistenti. `matchups` è una mappa con
chiavi serializzate come
`MatchupKey[champion=championId, lane=ROLE]`; le metriche non disponibili sono
`null`. Il frontend converte questa mappa in un array per la presentation layer.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `202` | `champion_data_pending` | Statistiche o build non sono ancora state generate; il refresh viene accodato in background. Un refresh completato senza dati produce `200` con aggregate vuoti. |
| `400` | `invalid_request` | Rank, region, queue o role non validi, oppure role incompatibile con la queue. |
| `404` | `not_found` | Champion sconosciuto. |

```json
{
  "status": 202,
  "code": "champion_data_pending",
  "message": "Champion data is being prepared"
}
```

## Owner

- Controller: [`ChampionController`](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java)
- Service: [`ChampionService`](../../../src/main/java/com/safjnest/lol/service/ChampionService.java)
- Success model: [`ChampionView`](../../../src/main/java/com/safjnest/lol/model/ChampionView.java)
