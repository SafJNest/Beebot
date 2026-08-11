# LoL HTTP API

Indice della documentazione HTTP pubblica LoL. La documentazione è organizzata
per scope e ogni endpoint ha un file dedicato con lo stesso template:

1. endpoint e fetch `curl`;
2. parametri con posizione, tipo, obbligatorietà e default;
3. risposta `200` con JSON completo della struttura;
4. stati alternativi ed errori;
5. owner nel codice.

Gli esempi usano `http://localhost:8080` come base URL.

## Indice per scope

### Summoner

- [Search](summoner/search.md) — `GET /api/lol/{shard}/search`
- [Profile by PUUID](summoner/profile-by-puuid.md) — `GET /api/lol/{shard}/profile/{puuid}`
- [Profile refresh](summoner/profile-refresh.md) — `POST /api/lol/{shard}/profile/{puuid}/refresh`
- [Profile by Riot ID](summoner/profile-by-name.md) — `GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}`
- [Match list](summoner/matches.md) — `GET /api/lol/{shard}/profile/{puuid}/matches`
- [Activity](summoner/activity.md) — `GET /api/lol/{shard}/profile/{puuid}/activity`
- [Matchups](summoner/matchups.md) — `GET /api/lol/{shard}/profile/{puuid}/matchups`
- [Profile indexables](summoner/indexables.md) — `GET /api/lol/profile/indexables`

### Match

- [Match detail](match/detail.md) — `GET /api/lol/{shard}/match/{gameId}`

### Champion

- [Champion page](champion/page.md) — `GET /api/lol/champion/{champion}`
- [Champion indexables](champion/indexables.md) — `GET /api/lol/champion/indexables`

### Leaderboard

- [Paginated leaderboard](leaderboard/page.md) — `GET /api/lol/leaderboard`
- [Rank distribution](leaderboard/rank-distribution.md) — `GET /api/lol/leaderboard/rank-distribution`
- [Top regions](leaderboard/top-regions.md) — `GET /api/lol/leaderboard/top-regions`

## Contratto comune

- Gli endpoint sono `GET`, salvo il refresh esplicito del profilo che usa `POST`.
- Enum e valori testuali sono case-insensitive e vengono sottoposti a `trim()`.
- I success payload usano i modelli canonici in `com.safjnest.lol.model`.
- Le response root oggetto o paginate espongono `metadata` sullo stesso root,
  senza envelope `data`. Le quattro chiavi sono sempre presenti: `pagination`,
  `lastUpdate`, `refresh` e `filter`; i valori non applicabili sono `null`.
  Le liste pure, search e indexables restano array invariati.
- Gli errori usano sempre questo envelope:

```json
{
  "status": 400,
  "code": "invalid_request",
  "message": "Invalid queue: must be one of: ..."
}
```

| HTTP | Significato |
|---:|---|
| `200` | Risposta pronta. Include anche i payload `PARTIAL` del profilo. |
| `202` | Il dato manca, il refresh è stato accodato in background e la richiesta va ripetuta. |
| `400` | Parametro mancante, tipo non valido, enum sconosciuto o combinazione non supportata. |
| `404` | Risorsa o endpoint inesistente. |
| `204` | Refresh completato, oppure ignorato dal cooldown. |
| `405` | Metodo HTTP non supportato. |
| `500` | Errore inatteso del server. |

I `202` usano sempre lo stesso formato, con codice e messaggio specifici dello
scope. Per esempio:

```json
{
  "status": 202,
  "code": "champion_data_pending",
  "message": "Champion data is being prepared",
  "metadata": {
    "pagination": null,
    "lastUpdate": null,
    "refresh": true,
    "filter": {}
  }
}
```

`refresh=true` significa che il job deduplicato è stato accodato. Una response
pronta usa `refresh=false`. `lastUpdate` è epoch millis, `pagination` contiene
solo i campi applicabili (`page`, `pageSize`, `limit`, `offset`, `total`,
`pages`, `hasMore`).

## Tipi condivisi dei parametri

| Parametro | Tipo | Valori o vincoli |
|---|---|---|
| `shard` / `region` | enum `LeagueShard` | `BR1`, `EUN1`, `EUW1`, `JP1`, `KR`, `LA1`, `LA2`, `NA1`, `OC1`, `TR1`, `RU`, `PBE1`, `SG2`, `PH2`, `ID1`, `VN2`, `TH2`, `TW2`, `ME1`. `UNKNOWN` è rifiutato. |
| `rank` | enum `TierType` | `CHALLENGER`, `GRANDMASTER`, `MASTER`, `DIAMOND`, `EMERALD`, `PLATINUM`, `GOLD`, `SILVER`, `BRONZE`, `IRON`, `UNRANKED`. |
| `queue` | enum `GameQueueType` | Usa il nome della costante R4J; il default pubblico è `TEAM_BUILDER_RANKED_SOLO`, normalizzato internamente a `RANKED_SOLO_5X5` dove richiesto. |
| `role` | enum `LaneType` | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`. |
| `page` | integer | 1-based, `>= 1`. Default `1`. |
| `limit` | integer | Default e massimo dipendono dallo scope: leaderboard `1`-`50` (default `50`), match list `1`-`100` (default `20`). |
| `q`, `puuid`, `gameId`, `gameName`, `tagLine`, `champion` | string | Non vuota; i segmenti path devono essere URL-encoded quando contengono caratteri riservati. |

`region` omesso significa aggregato globale interno; il valore pubblico non è
`GLOBAL`. `role` è disponibile solo per champion e viene rifiutato se la queue
non supporta una lane.

## Source of truth

- [AGENTS.md](../../AGENTS.md) — regole di sincronizzazione API e documentazione;
- [LoL architecture](../architecture/README.md) — ownership e ADR;
- [ADR-0005](../architecture/adr/0005-lol-api-json-contract.md) — JSON canonico;
- [ADR-0006](../architecture/adr/0006-champion-api-contract.md) — champion page;
- [ADR-0007](../architecture/adr/0007-unified-api-result-and-parameters.md) — parametri e status;
- [ADR-0008](../architecture/adr/0008-endpoint-cache-and-async-lookups.md) — cache e flussi asincroni.
