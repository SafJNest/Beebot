# Arena champion analytics

This folder is the planning source of truth for the Arena-only champion analytics described in the pasted product brief. It is a design proposal, not an accepted ADR and not an implementation contract yet.

## Goal

Produce one Arena statistics document per champion and patch, with general champion statistics, core-conditioned build and augment distributions, plus champion-global first-Prismatic, item and augment aggregates. Keep the Arena statistics root, accumulator and Mongo collection separate from standard champion statistics, while sharing and generalizing the build/container response model with standard builds.

## Documents

- [`contracts.md`](contracts.md) — candidate model, Mongo shape, populations, event interpretation, read/API boundary and open decisions.
- [`phases.md`](phases.md) — implementation phases, gates, owners to inspect and documentation/API synchronization.

## Current assessment

The reusable parts are stronger than the first draft stated: `ChampionBuildTimelineUtils.itemEvents` already normalizes attributed purchases, undo, sales and destruction; `MongoDB.forEachChampionRawMatchEventBatch` already performs bounded match/event joins; and `ChampionStatsProvider` already consumes that stream and can expose the full raw match document to its coalesced build path. These are candidates to share or generalize. The user's clarified direction also favors a shared build vocabulary: `builds[]` contains one `Build` per core, each with typed slots, while item, augment and Prismatic choices share an ID, position, time and stats representation.

The aggregate result still needs a distinct Arena root because its identity, placement stats, populations and persistence shape differ. The build hierarchy itself should be shared: each build entry represents one normalized core and owns its core stats, item slots, augment slots and paths. A normal build uses core + item slots; Arena uses core + item slots + augment slots. Keep a single generic option/leaf representation, with a kind discriminator where Java generics are erased in persisted JSON. Do not create Arena-only versions of every container. A separate Arena provider/game record is optional; decide after the raw-source gap is resolved.

The existing `LeafStats` contains standard champion KDA fields and is not a clean Arena leaf. `ProfileLeafStats` already calculates Arena placement metrics for profile views, but it is a broad profile projection and must not become the Arena aggregate model.

The current champion statistics path owns standard queue/rank/region scopes and stores all champions together. Keep that statistics document separate. The standard build path has useful containers, timeline and accumulation techniques, but its current independent core/slot projection, fixed slot limits and lack of Arena placement sufficient stats do not match the target. Generalize the shared build response and analyzer so both modes produce `Build` elements grouped by core; do not embed Arena global stats into the standard champion statistics document.

There is a documentation mismatch to resolve during the implementation handoff: ADR-0006 and `docs/mongo/08-query-inventory.md` name the live stats collection `champion_stats`, while the HANDBOOK §6 reference table says `champion_statistics`. The Arena plan uses the new `arena_champion_statistics` name and does not settle that standard collection discrepancy.

## Status

- Requirements read and reviewed: complete.
- Code and documentation inventory: complete; see the evidence and caveats in the two documents.
- Contract proposal: drafted around shared build containers, with data-dependent and product decisions called out.
- Phase 0: not passed overall. The win rule is defined as `participant.win == true || subTeamPlacement >= 3`, observed `subTeamPlacement` positions must be retained, and the user approved strict evidence only with `UNKNOWN` when direct Prismatic/core evidence is absent. Current timelines have no item inventory snapshots around selection, and augment choice times are unavailable. Population semantics and the BSON fit/retention gate remain open.
- Phase 1: parser implemented; the `Tracker` evidence writer, repair integration and augment sidecar were removed at the user's request. The parser accepts strict v1 evidence supplied in `match_events`, preserves placement and existing augment-list order, and counts missing or ambiguous inputs without inference. No current writer persists this evidence, so the Phase 1 gate is not passed.
- A separate manual first-Prismatic diagnostic now reads the persisted match and `match_events` via `MongoDB.findMatch`; its slot-order fallback and `ItemUtils.isPrismatic` classification (excluding `447111`) are diagnostic only and do not change the strict-evidence parser or statistics.
- Validation: parser-focused tests passed after the removal. Resolver tests cover candidate selection, `220007` timestamp deduplication, pre-anvil events, accounting, undo and the `447111` exception. The full `TrackerTest`, `MongoDBTest` and Maven suite were not run against this final state. CodeGraph was checked for the final state. No production timeline inspection or representative BSON measurement was performed.
