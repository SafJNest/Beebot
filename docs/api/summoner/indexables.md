# Scope: profile — Indexables

## Endpoint

`GET /api/lol/profile/indexables`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/profile/indexables'
```

L’endpoint non accetta parametri e legge la proiezione già persistita. La
proiezione include i summoner con
`tracking=true` oppure con un rank `MASTER_I`, `GRANDMASTER_I` o
`CHALLENGER_I` in una delle queue salvate.

## Risposta `200`

La risposta contiene soltanto i campi necessari per costruire l’URL del
profilo:

```json
[
  {
    "riotId": "Player#EUW",
    "region": "EUW1"
  }
]
```

Il refresh viene lanciato dal case owner `test profileindexables`. La collection
interna `profiles_indexable` usa il PUUID come identità e imposta `lastUpdate`
soltanto quando un profilo viene aggiunto; il cambio di `riotId` o `region` non
aggiorna il timestamp. Un profilo rimosso dalla condizione viene eliminato e
riceve un nuovo timestamp se viene aggiunto nuovamente.

## Owner

- Controller: [`ProfileIndexableController`](../../../src/main/java/com/safjnest/spring/controller/ProfileIndexableController.java)
- Service: [`ProfileIndexableService`](../../../src/main/java/com/safjnest/lol/service/ProfileIndexableService.java)
- Success model: [`ProfileIndexable`](../../../src/main/java/com/safjnest/lol/model/ProfileIndexable.java)
