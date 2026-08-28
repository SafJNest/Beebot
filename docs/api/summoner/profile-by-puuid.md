# Scope: summoner — Profile by PUUID

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}`

`shard` è un `LeagueShard` e `puuid` è il PUUID Riot canonico del summoner.

## Risposta `200`

Restituisce `SummonerView`. `overview.statistics` è un dataset di foglie
aggregabili, non una pagina già precalcolata: il consumer costruisce totale,
queue, posizione e medie dai dati ricevuti. `overview.masteries`,
`overview.champions` e `overview.recentMatches` restano parte della response.

```json
{
  "overview": {
    "statistics": {
      "timeStart": 1711929600000,
      "timeEnd": 1714521600000,
      "lastUpdate": 1714521600000,
      "champions": {
        "157": {
          "RANKED_SOLO": {
            "TOP": {
              "games": 42,
              "wins": 24,
              "kills": 286,
              "deaths": 198,
              "assists": 512,
              "damage": 684321,
              "damageTaken": 501223,
              "championLevelTotal": 756,
              "lpGain": 286,
              "playtime": 110880000
            }
          },
          "ARENA": {
            "UNKNOWN": {
              "games": 2,
              "arenaPlacementSum": 5
            }
          }
        }
      },
      "pings": {},
      "spellOne": {},
      "spellTwo": {}
    }
  }
}
```

Non vengono restituiti né salvati `total`, `queueStats`, `laneStats`,
`championStats`, `reference`, `context`, `winrate`, `kda` o campi `avg*`.

Le queue del dataset sono `CanonicalQueue`, non enum Riot: per esempio
`RANKED_SOLO`, `RANKED_FLEX`, `NORMAL_DRAFT`, `ARAM`, `ARENA` e `SWIFTPLAY`.
Una posizione assente o non applicabile è sempre `UNKNOWN`.

Un campo metrico omesso significa che il dato non era disponibile nel raw
storico; uno `0` presente è un valore raccolto e realmente nullo. Il livello
è esclusivamente `championLevelTotal`, cioè la somma dei champion level finali.

Le medie sono derivate dal consumer: `avgKills = kills / games`,
`avgChampionLevel = championLevelTotal / games`, e il placement Arena usa
`arenaPlacementSum / games` della sola foglia `ARENA → UNKNOWN`. I campi
Arena non sono presenti nelle foglie delle altre queue.

Se rank/mastery o statistics non sono pronti, la response mantiene gli stati
`PARTIAL`/`202` documentati dal contratto `ApiResult`; nessuna GET effettua
una chiamata Riot sincrona.
