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
Account, summoner, rank, mastery e spectator, senza toccare la matchlist. Quindi
aggiorna in ordine Riot Account, summoner, rank e mastery tramite `R4JQueue`.
Ogni componente viene persistito in Mongo e le cache Redis appena ricostruite
rimangono disponibili.

Le GET profile non avviano il fetch Riot di rank o mastery quando i componenti
sono assenti; questo POST è l'unico flusso profilo che li aggiorna.

Dopo la verifica del profilo, la POST aggiorna internamente
`summoner.lastSeenAt`. `DatabaseTracker` riceve un unico job manuale ad alta
priorità, deduplicato `profile-refresh:<puuid>`, che rigenera da zero
statistics, activity, matchups e contesto champion del profilo canonico. I filtri canonici sono:
overview/matchups sullo split corrente senza patch, queue o lane; activity
senza intervallo, queue o champion. Il job legge i match una volta tramite
cursor Mongo e salva i tre documenti solo dopo il completamento dei tre
accumulatori.

Una chiave Redis atomica `SUMMONER_REFRESH_COOLDOWN` applica un cooldown di due
minuti per coppia `{shard, puuid}`. Una richiesta durante il cooldown non avvia
altre chiamate Riot e viene trattata come completata.

Il refresh non richiede, accoda o invalida la matchlist. Invalida spectator ma
non lo rifetchia nella POST: la successiva GET livegame lo recupera da Riot. Il
recupero delle partite recenti resta responsabilità di un endpoint dedicato.

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
