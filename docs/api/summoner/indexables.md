# Scope: profile — Indexables

## Endpoint

`GET /api/lol/profile/indexables`

## Fetch

```bash
curl 'http://localhost:8080/api/lol/profile/indexables'
```

The endpoint accepts no parameters and reads the already-persisted projection. The
projection includes summoners with
`tracking=true` or with a `MASTER_I`, `GRANDMASTER_I` or
`CHALLENGER_I` rank in one of the stored queues.

## `200` response

The response contains only the fields required to build the profile URL:

```json
[
  {
    "riotId": "Player#EUW",
    "region": "EUW1"
  }
]
```

The refresh is triggered by the owner case `test profileindexables`. The internal
collection `profiles_indexable` uses PUUID as identity and sets `lastUpdate`
only when a profile is added; a change to `riotId` or `region` does not
update the timestamp. A profile removed from the condition is deleted and
receives a new timestamp if added again.

## Owner

- Controller: [`ProfileIndexableController`](../../../src/main/java/com/safjnest/spring/controller/ProfileIndexableController.java)
- Service: [`ProfileIndexableService`](../../../src/main/java/com/safjnest/lol/service/ProfileIndexableService.java)
- Success model: [`ProfileIndexable`](../../../src/main/java/com/safjnest/lol/model/ProfileIndexable.java)
