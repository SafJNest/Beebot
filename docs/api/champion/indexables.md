# Scope: champion — Indexables

## Endpoint

`GET /api/lol/champion/indexables`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/champion/indexables'
```

The endpoint accepts no parameters and reads the projection for the current
major patch. If the projection has not yet been generated, the response is an
empty list; refresh is triggered by the owner case `test championindexables`.

## `200` response

The response is a list of `ChampionIndexable`, ordered by champion and, for
each champion, by role importance based on game count.

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

A role is `indexable=true` when it represents at least 10% of the champion's games
in the current major patch. `lastUpdate` changes only when
`indexable` switches from `false` to `true` or from `true` to `false`; a change in
`games` does not update the timestamp.

## Storage

The derived collection is `champions_indexable`. Each document represents a
champion/role pair and also contains `patchMajor`, used for reading the
current patch. Non-playable roles, including `NONE`/unknown, are not
saved.

## Owner

- Controller: [`ChampionController`](../../../src/main/java/com/safjnest/spring/controller/ChampionController.java)
- Service: [`ChampionService`](../../../src/main/java/com/safjnest/lol/service/ChampionService.java)
- Success model: [`ChampionIndexable`](../../../src/main/java/com/safjnest/lol/model/ChampionIndexable.java)
