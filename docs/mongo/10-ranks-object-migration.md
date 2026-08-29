# Ranks object migration

`summoner.ranks` is an object keyed by the canonical `GameQueueType.name()`.
The value contains rank data only; `queue` is removed because the key owns that
identity. Runtime reads accept a legacy array only in `MongoDB.ranks(Document)`;
all writes, including MariaDB backfill, write the object form.

Run the following operation manually after deploying the dual-read/object-write
release. It uses an update pipeline available from MongoDB 4.2 and only uses
`$objectToArray`, `$filter`, `$map` and `$arrayToObject`; it deliberately does
not require `$unsetField` (MongoDB 5.0). The project uses MongoDB driver 5.8.0,
which supports this command shape, but the deployed server version must be at
least 4.2 before execution.

```javascript
db.summoner.updateMany(
  {ranks: {$type: "array"}},
  [
    {
      $set: {
        ranks: {
          $arrayToObject: {
            $map: {
              input: "$ranks",
              as: "rank",
              in: {
                k: "$$rank.queue",
                v: {
                  $arrayToObject: {
                    $filter: {
                      input: {$objectToArray: "$$rank"},
                      as: "field",
                      cond: {$ne: ["$$field.k", "queue"]}
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  ]
)
```

Validate before deleting the compatibility reader:

```javascript
db.summoner.countDocuments({ranks: {$type: "array"}})
```

The expected result is `0`. Indexes are intentionally outside this migration and
are managed separately by the database operator.

## Remove embedded MMR

After `!test competitive` has populated the `competitive` index, remove the
obsolete MMR field from every queue dynamically. This update pipeline is also
compatible with MongoDB 4.2:

```javascript
db.summoner.updateMany(
  {"ranks": {$type: "object"}},
  [
    {
      $set: {
        ranks: {
          $arrayToObject: {
            $map: {
              input: {$objectToArray: "$ranks"},
              as: "queue",
              in: {
                k: "$$queue.k",
                v: {
                  $arrayToObject: {
                    $filter: {
                      input: {$objectToArray: "$$queue.v"},
                      as: "field",
                      cond: {$ne: ["$$field.k", "mmr"]}
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  ]
)
```

Verify that no embedded MMR remains, then drop the obsolete indexes documented
in [`11-leaderboard-rank-indexes.md`](11-leaderboard-rank-indexes.md):

```javascript
db.summoner.countDocuments({
  $or: [
    {"ranks.RANKED_SOLO_5X5.mmr": {$exists: true}},
    {"ranks.RANKED_FLEX_SR.mmr": {$exists: true}}
  ]
})
```
