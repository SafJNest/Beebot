# Arena champion analytics

Phase 3 uses the existing provider, MongoDB owner, ChampionService and
ComputeScheduler. The approved [contract](contracts.md) stores exactly one
`champion_builds` document per normal filterKey, with standard fields and
`build.arena` coexisting. No second identity, document, collection or service.

```text
champion_builds[_id = filterKey]
  standard root metadata
  build
    standard fields
    arena (schemaVersion=3, aggregationVersion=3)
      stats
      positions: boots, augments, prismatics, items, membership, unpositioned
      builds: actually observed boots + P1 + ordered Legendary items
      cores: boots + augment OR boots + prismatic
        complete-context steps with items, prismatics, augments choices
      coverage
```

The pure accumulator returns ArenaBuildData. Standard writes set only their
owned root fields and individual build paths; Arena writes set build.arena
atomically. Neither replaces the whole document or performs read-merge-replace.
The standard reader projects its original fields and ignores Arena, including
malformed Arena content. A document inserted by Arena alone is not standard-ready.

The Arena left join delivers all matching full-patch CHERRY matches, with or
without match_events, in batches of at most 100. Input and catalog are detached;
unknown source completeness remains false. Catalog is supplied explicitly by
the internal caller, responsible for using the requested patch. No current-patch
catalog fetch, API read integration or automatic scheduler hook is added.

Parser, strict attribution/undo, resolver quality, independent positions,
observed builds, generic cores and all observed steps retain the Phase 2
semantics. Equipment and augments stay separate. 220007 is evidence only.
No sample threshold, top-N pruning, synthetic build or core splitting is added.
JSON uses semantic core/items/augments/prismatics and item/boots/augment/prismatic
identity properties; generic kind/id remain internal. See [schema](schema.md).

The existing CHERRY endpoint continues to serve standard aggregates. Controller,
Participant, Tracker and presentation retain their contracts. No rebuild,
backfill, production operation or commit is executed by this implementation.
[Phases](phases.md) records exact test results and pending gates. Offline tests
and synthetic measurements do not certify live Mongo, index explains,
representative BSON/cardinality/heap or production readiness.
