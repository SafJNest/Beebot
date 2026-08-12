# Scope: summoner — Matchups

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/matchups`

## Fetch

```bash
curl --get 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/matchups' \
  --data-urlencode 'queue=TEAM_BUILDER_RANKED_SOLO' \
  --data-urlencode 'patch=14.10' \
  --data-urlencode 'role=BOT' \
  --data-urlencode 'minGames=5'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard del profilo. |
| `puuid` | path | string | sì | — | PUUID Riot canonico del summoner. |
| `start` | query | epoch millis | no | `0` | Inizio del periodo; se `end` manca, viene usata la fine della giornata corrente (`23:59:59.999`). Se presente, prevale su `patch`. |
| `end` | query | epoch millis | no | `0` | Fine del periodo; può essere usato da solo e deve essere maggiore o uguale a `start` quando `start` è presente. |
| `queue` | query | enum `GameQueueType` oppure `ALL` | no | `ALL` | Queue da filtrare; omissione e `ALL` aggregano tutte le queue. |
| `patch` | query | `major.minor` | no | nessun filtro | Fallback quando `start` e `end` sono entrambi assenti; se il periodo è presente viene ignorata. |
| `role` | query | enum `LaneType` | no | tutti i ruoli | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`. Non è valido con queue senza lane. |
| `minGames` | query | integer `>= 1` | no | `5` | Soglia applicata solo alle righe matchup; i champion giocati non vengono rimossi. |

## Risposta `200`

La response contiene una sola riga per ogni champion giocato. I champion
giocati in più ruoli vengono uniti quando `role` è omesso. Ogni matchup conta
solo l’avversario sulla stessa lane e usa il modello completo `Stats`.
L'esempio seguente mostra solo i campi principali di `Stats`; la response reale
mantiene tutti i campi del modello esistente.

```json
{
  "filter": {
    "timeStart": 1711929600000,
    "timeEnd": 1714521600000,
    "champion": 0,
    "lane": "BOT",
    "queue": "TEAM_BUILDER_RANKED_SOLO",
    "rank": null,
    "rankBehavior": "GREATER_OR_EQUAL",
    "patch": "14.10",
    "region": null,
    "opponent": 0,
    "duo": 0
  },
  "timeStart": 1711929600000,
  "timeEnd": 1714521600000,
  "lastUpdate": 1714521600000,
  "champions": [
    {
      "champion": 157,
      "stats": {
        "reference": 157,
        "games": 42,
        "wins": 24,
        "winrate": 57.14,
        "kda": 4.03,
        "avgCs": 200.5,
        "avgDamage": 16293.36,
        "avgKillParticipation": 64.63,
        "lastPlayedAt": 1714518000000
      },
      "matchups": [
        {
          "champion": 412,
          "stats": {
            "reference": 412,
            "games": 6,
            "wins": 3,
            "winrate": 50.0,
            "kda": 2.4,
            "avgCs": 188.0,
            "avgDamage": 15120.0,
            "avgKillParticipation": 58.2,
            "lastPlayedAt": 1714518000000
          }
        }
      ]
    }
  ]
}
```

`champion` è l’ID numerico del champion. Il consumer risolve nome e immagine
tramite i propri dati statici. La response contiene aggregati e non include
la lista dei singoli game.

Se viene passato `start` senza `end`, la fine del periodo è la fine della
giornata corrente (`23:59:59.999`, timezone del server), così il `filterKey`
resta stabile durante la giornata.
Se viene passato solo `end`, non viene applicato un limite inferiore. Quando
almeno uno tra `start` e `end` è presente, il periodo prevale e `patch` non
viene applicata; se mancano entrambi, `patch` filtra la patch mantenendo il
periodo dello split corrente.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Aggregato pronto. |
| `202` | `profile_matchups_pending` | Aggregato assente; il refresh on-demand è stato avviato in background. |
| `400` | `invalid_request` | Periodo start/end, queue, patch, role o `minGames` non validi. |
| `404` | — | Profilo non trovato. |

`metadata` è root sia nel `200` sia nell'errore `202`: include il filtro di
aggregazione richiesto, `lastUpdate` e `refresh`. Uno stale resta `200` con il
payload persistito e `refresh=true`, poi accoda soltanto il job matchup in
bassa priorità.

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Parametri: [`LolApiParameters`](../../../src/main/java/com/safjnest/spring/controller/LolApiParameters.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Modello: [`ProfileMatchups`](../../../src/main/java/com/safjnest/lol/model/statistics/ProfileMatchups.java)
- Redis: `PROFILE_MATCHUPS(puuid, filterKey)`, TTL 6 ore
- Mongo: collection `profile_matchups`, identità `{ puuid, filterKey }`
