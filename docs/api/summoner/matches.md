# Scope: summoner — Match list

## Endpoint

`GET /api/lol/{shard}/profile/{puuid}/matches`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/matches?queue=RANKED_SOLO_5X5&limit=20&offset=0&sort=timeStart:desc'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Default | Descrizione |
|---|---|---|---:|---|---|
| `shard` | path | enum `LeagueShard` | sì | — | Shard Riot del profilo. |
| `puuid` | path | string | sì | — | PUUID Riot canonico del summoner. |
| `queue` | query | enum `GameQueueType` | no | tutte | Filtra la queue. |
| `limit` | query | integer | no | `20` | Da `1` a `100`. |
| `offset` | query | integer | no | `0` | Offset zero-based, `>= 0`. |
| `timeStart` | query | long | no | `0` | Unix epoch ms inclusivo. |
| `timeEnd` | query | long | no | `0` | Unix epoch ms inclusivo. |
| `sort` | query | string | no | `timeStart:desc` | Solo `timeStart:asc` o `timeStart:desc`. |

## Risposta `200`

La lista legge esclusivamente i match già persistiti in Mongo: non avvia
chiamate Riot, lookup o rigenerazioni statistiche.

```json
{
  "items": [
    {
      "gameId": "EUW1_6789012345",
      "queue": "RANKED_SOLO_5X5",
      "timeStart": 1714514400000,
      "timeEnd": 1714516500000,
      "win": true,
      "kda": "8/2/11",
      "championId": 103,
      "participants": [
        {
          "puuid": "Qx7m2vW8-example-puuid",
          "rankProgress": {
            "rank": "DIAMOND_II",
            "lp": 74,
            "gain": 21,
            "previousRank": "DIAMOND_II",
            "previousLp": 53
          }
        }
      ],
      "primaryRunes": [8112, 8143, 8138, 8105],
      "secondaryRunes": [8347, 8304],
      "statsRunes": [5008, 5008, 5011]
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 5131,
  "hasMore": true
}
```

L'ordinamento usa sempre `timeStart` e `_id` come tie-breaker tecnico, così
offset e pagine restano stabili.

`total` è il numero di match persistiti che soddisfano gli stessi filtri di
`items` (`shard`, `queue`, `timeStart` e `timeEnd`), prima di applicare
`limit` e `offset`.

Ogni risultato espone le rune del summoner richiesto: la prima voce di
`primaryRunes` e `secondaryRunes` è l'albero, le altre sono le rune scelte;
`statsRunes` contiene i tre shard statistici. I partecipanti restano una
projection leggera e non includono queste configurazioni; quando disponibile,
ognuno include il `rankProgress` già persistito. Non esistono più campi
participant top-level `rank`, `lp` o `gain`.

La pagina mantiene i campi esistenti e aggiunge `metadata` root con
`pagination` (`limit`, `offset`, `total`, `hasMore`), il filtro richiesto e
`refresh=false`; `lastUpdate` è `null`.

## Errori

| HTTP | Descrizione |
|---:|---|
| `400` | Parametro, intervallo temporale, limit, offset o sort non validi. |

## Owner

`MatchService.getPage` e `MongoDB.findMatchResults`.
