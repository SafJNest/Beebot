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

Il refresh pulisce prima e in modo centralizzato le cache R4J e Redis di Riot
Account, summoner, rank e mastery, senza toccare spectator o matchlist. Quindi
aggiorna in ordine Riot Account, summoner, rank e mastery tramite `R4JQueue`.
Ogni componente viene persistito in Mongo e le cache Redis appena ricostruite
rimangono disponibili.

Solo dopo l'ultima persistenza il profilo invalida centralmente `PROFILE_PAGE`
e gli aggregati Redis derivati. `DatabaseTracker` riceve un unico job
deduplicato `profile-refresh:<puuid>` che elimina in Mongo e Redis tutti gli
aggregati profilo non canonici e rigenera da zero soltanto statistics, activity,
matchups e contesto champion del profilo canonico. I filtri canonici sono:
overview/matchups sullo split corrente senza patch, queue o lane; activity
senza intervallo, queue o champion.

Una chiave Redis atomica `SUMMONER_REFRESH_COOLDOWN` applica un cooldown di due
minuti per coppia `{shard, puuid}`. Una richiesta durante il cooldown non avvia
altre chiamate Riot e viene trattata come completata.

Il refresh non richiede, accoda o invalida la matchlist. Il recupero delle
partite recenti resta responsabilità di un endpoint dedicato.

## Risposta `204`

Il refresh è completato o è stato ignorato dal cooldown. Dopo la risposta il
client può richiedere di nuovo `GET /profile/{puuid}`.

## Errori

| HTTP | Descrizione |
|---:|---|
| `400` | Shard o PUUID non validi. |
| `404` | Il profilo non è presente in Mongo. |

## Owner

`SummonerService.refreshAsync`, `SummonerService.refresh`, `R4JQueue`,
`ProfileService` e `DatabaseTracker`.
