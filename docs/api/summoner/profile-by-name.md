# Scope: summoner — Profile by Riot ID

## Endpoint

`GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}`

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile-by-name/Player/EUW'
```

| Nome | Posizione | Tipo | Obbligatorio | Descrizione |
|---|---|---|---:|---|
| `shard` | path | `LeagueShard` | sì | Shard in cui risolvere il Riot ID. |
| `gameName` | path | string | sì | Parte prima di `#`. |
| `tagLine` | path | string | sì | Parte dopo `#`. |

I segmenti path devono essere URL-encoded quando necessario. Dopo la
risoluzione, la response è lo stesso `SummonerView` di
[Profile by PUUID](profile-by-puuid.md), incluso il contratto leaf-only
`overview.statistics.champions.<championId>.<CanonicalQueue>.<position>`.

Il consumer ricava totale, medie, winrate, KDA e breakdown queue/lane dalle
foglie. Non esistono `total`, `queueStats`, `laneStats`, `championStats`,
`reference`, `context`, `winrate`, `kda` o campi `avg*` nel payload delle
statistics. Rank e mastery restano letture Redis/Mongo; la GET non esegue
chiamate Riot sincrone.

## Stati ed errori

| HTTP | `code` | Quando |
|---:|---|---|
| `200` | — | Profilo disponibile; può essere stale con `metadata.refresh=true`. |
| `202` | `profile_pending` | Risoluzione Riot ID o profilo base in corso. |
| `400` | `invalid_request` | Parametri path mancanti/non validi. |
| `404` | `not_found` | Riot ID o profilo non trovati. |

## Owner

- Controller: [`LolController`](../../../src/main/java/com/safjnest/spring/controller/LolController.java)
- Service: [`ProfileService`](../../../src/main/java/com/safjnest/lol/service/ProfileService.java)
- Success model: [`SummonerView`](../../../src/main/java/com/safjnest/lol/model/summoner/SummonerView.java)
