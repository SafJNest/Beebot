# Arena champion analytics

The [contract](contracts.md) and [phases](phases.md) describe Arena analytics separately from standard champion statistics/builds. The contract was reconciled with the Phase 2 request on 2026-10-01: core identity is ordered relevant purchases, including 220007; boots and Prismatics remain independent distributions. The earlier first-Prismatic/boots core and standard response replacement proposals are superseded.

Phase 1 supplied a strict evidence parser but its Tracker writer/repair sidecar was removed at the user's request. No current writer persists `arena_evidence`; final inventory does not prove acquisition order. The manual `ArenaFirstPrismaticManual` / `FirstPrismaticResolver` diagnostic keeps its slot fallback isolated from analytics.

Phase 2 provides shared optional Build containers, a pure Arena parser/accumulator, raw outcome/placement statistics, timed coverage, boots and Prismatic positions 1–6, and JSON/BSON-compatible snapshots. It leaves Participant, Tracker, standard generator, persistence, scheduler, endpoints and presentation unchanged. Phase 3 and production rollout still require completeness/source validation, BSON sizing, retention and a bounded provider with missing-timeline delivery.

The source audit is code-derived. No production timelines, Mongo writes, backfill or representative BSON size measurement are part of Phase 2. Validation results are recorded in [phases.md](phases.md).
