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
- [Profile by Riot ID](summoner/profile-by-name.md) — `GET /api/lol/{shard}/profile-by-name/{gameName}/{tagLine}`
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

- Tutti gli endpoint sono `GET`.
- Enum e valori testuali sono case-insensitive e vengono sottoposti a `trim()`.
- I success payload usano i modelli canonici in `com.safjnest.lol.model`.
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
| `405` | Metodo HTTP non supportato. |
| `500` | Errore inatteso del server. |

I `202` usano sempre lo stesso formato, con codice e messaggio specifici dello
scope. Per esempio:

```json
{
  "status": 202,
  "code": "champion_data_pending",
  "message": "Champion data is being prepared"
}
```

## Tipi condivisi dei parametri

| Parametro | Tipo | Valori o vincoli |
|---|---|---|
| `shard` / `region` | enum `LeagueShard` | `BR1`, `EUN1`, `EUW1`, `JP1`, `KR`, `LA1`, `LA2`, `NA1`, `OC1`, `TR1`, `RU`, `PBE1`, `SG2`, `PH2`, `ID1`, `VN2`, `TH2`, `TW2`, `ME1`. `UNKNOWN` è rifiutato. |
| `rank` | enum `TierType` | `CHALLENGER`, `GRANDMASTER`, `MASTER`, `DIAMOND`, `EMERALD`, `PLATINUM`, `GOLD`, `SILVER`, `BRONZE`, `IRON`, `UNRANKED`. |
| `queue` | enum `GameQueueType` | Usa il nome della costante R4J; il default pubblico è `TEAM_BUILDER_RANKED_SOLO`, normalizzato internamente a `RANKED_SOLO_5X5` dove richiesto. |
| `role` | enum `LaneType` | `TOP`, `JUNGLE`, `MID`, `BOT`, `UTILITY`. |
| `page` | integer | 1-based, `>= 1`. Default `1`. |
| `limit` | integer | Da `1` a `50`. Default `50`. |
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
