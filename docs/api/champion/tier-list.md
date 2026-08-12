# Scope: champion — Tier list

## Endpoint

`GET /api/lol/champions/tier-list`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/champions/tier-list' \
  --data-urlencode 'patch=14.10' \
  --data-urlencode 'rank=CHALLENGER' \
  --data-urlencode 'region=KR' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---:|---:|---|---|
| `patch` | query | string `major.minor` | no | patch corrente | Patch del dataset. |
| `rank` | query | enum `TierType` | no | nessun filtro | Tier minimo del dataset; `CHALLENGER` contiene solo Challenger. |
| `region` | query | enum `LeagueShard` | no | aggregato `GLOBAL` interno | Shard da aggregare. Non inviare `GLOBAL` o `UNKNOWN`. |
| `queue` | query | enum `GameQueueType` | no | `TEAM_BUILDER_RANKED_SOLO` | Queue del dataset. |

## Risposta `200`

Le queue con lane restituiscono sempre TOP, JUNGLE, MID, BOT e UTILITY. Una
queue senza lane restituisce una sola entry con `role: null`.

```json
{
  "roles": [
    {
      "role": "UTILITY",
      "champions": [
        {
          "champion": {
            "id": 412,
            "name": "Thresh",
            "image": "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/412.png"
          },
          "eligibleForRole": true,
          "tier": "S",
          "tierScore": 1.34,
          "stats": {
            "games": 12540,
            "picks": 12540,
            "bans": 1830,
            "wins": 6646,
            "winrate": 0.53,
            "pickrate": 0.084,
            "banrate": 0.012
          },
          "counters": [
            {
              "champion": { "id": 157, "name": "Yasuo", "image": "..." },
              "games": 312,
              "wins": 145,
              "losses": 167,
              "winrate": 0.465,
              "adjustedWinrate": 0.473,
              "weightedDelta": -0.057
            }
          ],
          "strongAgainst": []
        }
      ]
    }
  ],
  "metadata": {
    "pagination": null,
    "lastUpdate": 1710000000000,
    "refresh": false,
    "filter": {
      "champion": 0,
      "lane": null,
      "queue": "TEAM_BUILDER_RANKED_SOLO",
      "rank": "CHALLENGER",
      "patch": "14.10",
      "region": "KR"
    }
  }
}
```

La tier list espone solo champion con `eligibleForRole: true`. L'eligibility è
calcolata congiuntamente sui bucket di ruolo della response. Per ogni coppia
champion-ruolo si calcola `roleShare = picksInRole / totalPicksAcrossRoles`,
quindi un clustering a due gruppi usa `x = log1p(picksInRole)` e
`y = logit(roleShare)`. Il cluster con il centroide alto su entrambe le
dimensioni identifica i ruoli realmente giocati; uno stesso champion può
quindi risultare eligible in più ruoli. Non viene applicata alcuna soglia
assoluta di game, pick o pick rate. Per le queue senza lane il singolo bucket
non richiede classificazione tra ruoli e mantiene tutti i champion con pick.
Quando `roleShare` è esattamente `1`, il limite finito del logit usa come
risoluzione il minimo volume off-role positivo osservato nello stesso dataset:
in questo modo pochi pick concentrati in un solo ruolo non diventano un valore
infinito e non vengono confusi con un ruolo realmente consolidato.

Il `tierScore` combina gli Z-score della sola population eligible: 50% adjusted
win rate, 45% pick rate e 5% ban rate. L'adjusted win rate conserva il
`winrate` raw nella response, ma usa il win rate complessivo del ruolo come
prior: `(wins + priorStrength * roleAverageWinrate) / (picks + priorStrength)`,
dove `priorStrength` è la mediana dei pick eligible. La deviazione dello
Z-score include inoltre la varianza posteriore, per non trasformare un 1/1 in
un outlier di tier. I bucket sono `S+ >= 2`, `S >= 1`, `A >= 0.25`,
`B >= -0.25`, `C >= -1` e `D < -1`.

`counters` e `strongAgainst` contengono al massimo tre matchup. Il loro ordine
usa `weightedDelta = adjustedMatchupWinRate - adjustedChampionWinRate`.
Prima della mediana, entrambi gli elenchi rimuovono gli opponent che non sono
`eligibleForRole` nello stesso ruolo della response.
Il `matchupPriorStrength` è la mediana dei game dei matchup disponibili per il
singolo champion e vale anche come soglia relativa: entra nelle liste soltanto
un matchup con `matchupGames >= matchupPriorStrength`. Il matchup eleggibile
viene quindi corretto con `(matchupWins + matchupPriorStrength *
adjustedChampionWinRate) / (matchupGames + matchupPriorStrength)` prima di
calcolare il delta. Non esiste una soglia matchup globale.

## Stato parziale

Se uno o più ruoli non sono pronti o sono stale, l’endpoint risponde comunque
`200` con i soli ruoli pronti, `metadata.refresh=true` e avvia la matrice stats
deduplicata per patch e queue. La response parziale non viene messa in cache.

## Storage e cache

`champion_stats` resta l’unica fonte persistita. Mongo restituisce una
projection stretta di overview e matchup; score, tier e liste counter/strong
sono calcolati in Java. La response completa è cacheata in Redis e viene
invalidata quando il refresh stats sostituisce uno dei bucket sorgente. La
chiave cache è versionata insieme all'algoritmo, così una formula nuova non può
riutilizzare payload prodotti dalla versione precedente. Non esiste una
collection Mongo tier-list.

## Owner

- Controller: [`ChampionController`](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java)
- Service e cache: [`ChampionService`](../../../src/main/java/com/safjnest/lol/service/ChampionService.java)
- Analyzer: [`ChampionTierAnalyzer`](../../../src/main/java/com/safjnest/lol/service/ChampionTierAnalyzer.java)
- Source projection: [`MongoDB`](../../../src/main/java/com/safjnest/nosql/MongoDB.java)
- Success model: [`ChampionTierList`](../../../src/main/java/com/safjnest/lol/model/ChampionTierList.java)
