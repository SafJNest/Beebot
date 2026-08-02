# Scope: summoner — Profile refresh

## Endpoint

`POST /api/lol/{shard}/profile/{puuid}/refresh`

## Fetch

```bash
curl -X POST 'http://localhost:8080/api/lol/EUW1/profile/Qx7m2vW8-example-puuid/refresh'
```

## Parametri

| Nome | Posizione | Tipo | Obbligatorio | Descrizione |
|---|---|---|---:|---|
| `shard` | path | enum `LeagueShard` | sì | Shard Riot del profilo. |
| `puuid` | path | string | sì | PUUID Riot canonico del summoner. |

## Comportamento

Il refresh pulisce prima le cache R4J del summoner, quindi aggiorna in ordine
Riot Account, summoner, rank e mastery. Ogni componente viene persistito in
Mongo; solo dopo l'ultima scrittura vengono eliminate le cache Redis del
profilo e `PROFILE_PAGE`.

Una chiave Redis atomica `SUMMONER_REFRESH_COOLDOWN` applica un cooldown di due
minuti per coppia `{shard, puuid}`. Una richiesta durante il cooldown non avvia
altre chiamate Riot e viene trattata come completata.

Il refresh avvia separatamente il lookup degli ultimi cinque match mancanti su
Mongo. Non rigenera statistiche, activity, matchups o altri aggregati.

## Risposta `204`

Il refresh è completato o è stato ignorato dal cooldown. Dopo la risposta il
client può richiedere di nuovo `GET /profile/{puuid}`.

## Errori

| HTTP | Descrizione |
|---:|---|
| `400` | Shard o PUUID non validi. |
| `404` | Il profilo non è presente in Mongo. |

## Owner

`SummonerService.refreshAsync`, `SummonerService.refresh`, `R4JQueue` e
`Tracker.enqueueRecentMatches`.
