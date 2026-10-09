# Arena champion analytics

The [definitive contract](contracts.md), [Java/JSON schema](schema.md) and
[implementation phases](phases.md) describe the Arena model requested on
2026-10-01. Phase 2 was realigned on 2026-10-02: parser → resolver → pure
accumulator → shared `Build.arena` is implemented and verified offline.

Arena reuses `champion_builds`, the existing build collection, with one complete
document per champion + patch + `queue=CHERRY`. Its logical build payload contains:

```text
Build
  ArenaBuildData
    stats
    positions: boots, A1..A6, P1..P6, L1..Ln
    builds: actually observed boots + P1 + ordered Legendary sequences
    cores
      key: boots + A1 OR boots + P1
      stats
      steps: complete context + stats + typed choices
    coverage
```

Both core types use the same structure. Equipment and augment paths remain
separate. `220007` is reconstruction evidence only. The backend retains complete
raw aggregates, including steps with one game; the frontend chooses context,
backoff and an observed build when needed. No synthetic build is assembled from
the most popular item in each slot.

This supersedes the purchase-sequence core, inclusion of `220007` in builds,
separate Arena collection/root, separate decision/fallback schemas and mandatory
backend sample-threshold proposals. Standard aggregates, generator, serialized
fields, endpoints and presentation retain their existing contract. Accepted ADRs
are unchanged.

The accumulator returns `Build` with `ArenaBuildData` (schemaVersion=2,
aggregationVersion=3). The earlier purchase-sequence branch/root was removed.
Both generic cores, observed builds, independent positions, full prefix contexts
and conditional denominators share existing statistical/choice/slot primitives.
The parser actually calls `FirstPrismaticResolver` after strict attribution and
undo filtering. Raw resolver types and effective P1/order quality stay separate.

The 105 targeted offline tests passed, including JSON/in-memory BSON round trips and
standard generator/provider/Mongo projection regressions. See [phases](phases.md)
for the exact suite and runtime limits. Standard Build behavior is unchanged.
Arena uses only the current schema; obsolete Arena aggregates are discarded and
regenerated from source matches, with no compatibility adapter or migration.

Persistence belongs to MongoDB and orchestration to the existing service/queue
owners. BSON size and cardinality must be measured before rollout. Splitting
cores into documents in the same collection is only a future response to measured
limits; it is not part of the current design. No production operation, backfill,
new endpoint or commit was performed. Provider/persistence/read integration
remains in Phases 3–4; the current endpoint, including CHERRY, still runs the
standard generator. No production readiness or live Mongo result is claimed.
