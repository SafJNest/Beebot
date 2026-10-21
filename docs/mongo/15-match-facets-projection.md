# Match facets projection

`match` remains the canonical source for complete matches and the current
match-history API. `match_facets` is a rebuildable Mongo projection with one
document per participant/game. It replaces MariaDB's retired
`player_match_search` table; no LoL runtime or historical migration accesses
MariaDB anymore.

## Stored fields

| Field | Value |
|---|---|
| `_id` | BSON Binary, complete SHA-256 of UTF-8 `gameId + ":" + puuid` |
| `gameId` | Complete Riot match ID, equal to `match._id` |
| `puuid` | Participant PUUID |
| `region` | `LeagueShard.name()` |
| `timeStart`, `timeEnd` | Match start/end, epoch milliseconds |
| `queue` | Persisted `GameQueueType.name()`, nullable |
| `champion` | Numeric champion ID |
| `lane` | Persisted `LaneType.name()`, nullable |
| `patchMajor` | Normalized match patch, nullable |

Mongo creates the collection with WiredTiger Zstandard compression when it is
missing. Existing collections are not modified. The collection contains flat
participant documents, not per-player arrays or precomputed counters.

## Writes and rebuild

`MongoDB.insertFacets(Match)` submits one bulk replace/upsert per valid
participant and returns the number of submitted documents, including existing
documents replaced. Blank PUUIDs are skipped. The deterministic `_id` prevents
duplicates when the tracker or owner import processes a game again.

`MongoDB.insertMatch` calls it after inserting a new canonical match and its
events. An insert attempt for an already existing match remains a no-op.
`MongoDB.upsertMatchDocument` also calls it after replacing the canonical match,
covering tracker updates. Match and facet writes are separately acknowledged;
an error propagates and the owner rebuild repairs missing facet documents.

`!test export-match-search` streams the narrow match projection in batches of
50,000 and calls `insertFacets` for each match. It starts the dedicated
`match-search-export` thread directly, retaining the owner command's start,
completion, failure messages and total/completed/missing/participantRows
counters. Reruns upsert existing documents; stale documents are not deleted.
No import or database cleanup is triggered automatically by this change.

## Search

`GET /api/lol/{shard}/profile/{puuid}/facets` calls
`FacetsService.get(puuid, MatchFacetQuery)` and
`MongoDB.searchFacets(puuid, MatchFacetQuery)`.
Its canonical `MatchFacets` response contains total, queue/lane/champion/patch
options with selected flags, season alternatives and time range extrema.

One aggregation first matches PUUID and shard, then runs a single `$facet`.
The total and categorical branches apply the selected period; each categorical
branch applies all selected dimensions except its own. Values within one
dimension use `$in` (OR), and dimensions combine with AND. The season branch
counts current, previous and all using the other selected dimensions, without
the selected period. The range branch calculates earliest `timeStart` and
latest `timeEnd` under those same categorical selections, also without the
selected period. An explicit time range and `season` are mutually exclusive.
Empty results return total zero and null range endpoints. Requested categorical
values remain visible as selected options with total zero; unselected values
missing from the projection are omitted.

The outer PUUID/shard match is shared by every branch so Mongo can use one
player-scope index scan. Branch-local match stages do not use separate indexes.
The application does not create indexes or pin a hint.
`FacetsService.get` caches the full query result in Redis for 10 seconds under
a key scoped by region, shard, PUUID and encoded query.

## Operational index

Create the index manually on the intended application database:

```javascript
db.match_facets.createIndex(
  { puuid: 1, timeStart: 1 },
  { name: "facets_player_time" }
);

db.match_facets.createIndex(
  { puuid: 1, region: 1, timeStart: 1 },
  { name: "facets_player_region_time" }
);
```

The first index is the documented baseline; the second is a candidate that
adds the endpoint's equality shard predicate before `timeStart`. Compare both
with the same representative PUUID, shard and complete aggregation using
`explain("executionStats")`; keep the candidate only if examined keys and
latency improve for the deployed data. The aggregation starts with PUUID and
shard because season and range branches must inspect matches outside the
selected period. Do not infer live index coverage from source configuration.
Runtime code does not create secondary indexes.

After creating the candidate index on the intended database, compare the
initial player scope for the same profile with each hint:

```javascript
const pipeline = [
  { $match: { puuid: "<puuid>", region: "EUW1" } },
  { $facet: {
    total: [{ $count: "total" }],
    range: [{ $group: { _id: null, min: { $min: "$timeStart" }, max: { $max: "$timeEnd" } } }]
  } }
];

db.match_facets.explain("executionStats").aggregate(pipeline, { hint: "facets_player_time" });
db.match_facets.explain("executionStats").aggregate(pipeline, { hint: "facets_player_region_time" });
```

Repeat the un-explained aggregate with each hint and compare client latency as
well as `executionTimeMillis`, `totalKeysExamined` and `totalDocsExamined`.
This probe verifies the shared PUUID/shard scan; branch filters still run
inside `$facet` and cannot use branch-specific indexes.

## API and presentation

The dedicated facets endpoint is documented in `docs/api/summoner/match-facets.md`
and the API index. `/matches` also embeds the `facets` object in its existing
`MatchPage` response, using the same `FacetsService` and 10-second Redis cache.
The page lookup against `match` and the facet lookup run concurrently; the
facet aggregation also supplies the page's root `total`, removing the separate
match count query. `FacetsService.count(puuid, query)` exposes only the aggregate
total for callers that need a count; the unfiltered overload counts documents
in `match_facets`, the same projection used by the endpoint. Match pagination
and presentation of each match item remain unchanged. This does not change match persistence or rebuild
behavior. Since `FacetsService` caches the aggregate for 10 seconds, `/matches`
total and facets can be up to 10 seconds stale after projection writes.
