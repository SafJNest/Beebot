# Scope: champion — Indexables

## Endpoint

`GET /api/lol/champion/indexables`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/champion/indexables'
```

L’endpoint non accetta parametri e legge la proiezione della major patch
corrente. Se la proiezione non è stata ancora generata, la risposta è una
lista vuota; il refresh viene lanciato dal case owner `test championindexables`.

## Risposta `200`

La risposta è una lista di `ChampionIndexable`, ordinata per champion e, per
ogni champion, per importanza del ruolo in base al numero di game.

```json
[
  {
    "champion": 412,
    "role": "UTILITY",
    "games": 60,
    "indexable": true,
    "lastUpdate": 1710000000000
  },
  {
    "champion": 412,
    "role": "JUNGLE",
    "games": 30,
    "indexable": true,
    "lastUpdate": 1710000000000
  },
  {
    "champion": 412,
    "role": "TOP",
    "games": 2,
    "indexable": false,
    "lastUpdate": 1710000000000
  }
]
```

Un ruolo è `indexable=true` quando rappresenta almeno il 10% dei game del
champion nella major patch corrente. `lastUpdate` cambia soltanto quando
`indexable` passa da `false` a `true` o da `true` a `false`; un cambiamento di
`games` non aggiorna il timestamp.

## Storage

La collection derivata è `champions_indexable`. Ogni documento rappresenta una
coppia champion/ruolo e contiene anche `patchMajor`, usato per la lettura della
patch corrente. I ruoli non giocabili, incluso `NONE`/unknown, non vengono
salvati.

## Owner

- Controller: [`ChampionController`](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java)
- Service: [`ChampionIndexableService`](../../../src/main/java/com/safjnest/lol/service/ChampionIndexableService.java)
- Success model: [`ChampionIndexable`](../../../src/main/java/com/safjnest/lol/model/ChampionIndexable.java)
