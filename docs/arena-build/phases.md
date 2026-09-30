# Arena analytics implementation phases

Status: proposed delivery sequence. Each phase has an explicit gate. Do not start a later phase while its contract or data prerequisite is unresolved.

## Phase 0 — settle semantics and prove source availability

**Work**

- Decide `wins`, placement meaning, champion-game identity, full-patch key, and missing-data denominators.
- Decide the boot snapshot boundary and whether later sales/upgrades can change the core.
- Verify which Arena rounds expose the free Prismatic transition in Riot timeline `participantFrames` for real matches.
- Inspect representative persisted `match_events` to determine whether the needed frames exist. Current docs only promise a compact minute-15 frame and selected event counts.
- Define the source event rules for `UNDO`, `SELL`, `DESTROYED`, item transformations, duplicate acquisitions and uncertain event attribution.
- Confirm whether Arena augment source data includes an acquisition/round time. Preserve `time` when observed; if the upstream source has no time, represent that absence explicitly without excluding the augment from its slot counts.
- Define the normalized choice identity and core key. Entry `kind`, `id` and slot/order position identify the choice; observed time is metadata and must not split otherwise identical cores or paths.
- Measure BSON size/cardinality using representative champion/patch samples. Resolve the 16 MiB feasibility gate and retention/partition choice.
- Review the current `BuildSignature`/`ChampionBuildEngine` behavior and every consumer of `Build` before changing the shared response; preserve any in-progress checkout work.

**Gate**: approved written semantics, proven first-Prismatic source or a documented unknown-data fallback, and measured single-document fit with headroom or an accepted partition/retention design.

## Phase 1 — source evidence and deterministic parser

**Work**

- Add the chosen versioned Arena timeline evidence to the `match_events` producer and timeline-repair writer, or compute an exact first-Prismatic observation during those writes.
- Build the Arena parsing seam with placement, selected augment order, first-Prismatic evidence, boot snapshot and normalized post-core item sequence. Keep the parsed input as a nested/package-private type unless a separate top-level type materially improves ownership or testing.
- Reuse `ChampionBuildTimelineUtils` only for transitions proven identical; keep Arena transformation and free-Prismatic interpretation in the Arena parser.
- Keep subsequent 220007 results out of `firstPrismaticId`, first-Prismatic global stats and core.
- Add reason counters for missing/ambiguous first Prismatic, unknown boots, partial placement, item event conflicts and missing augment slots.

**Gate**: deterministic parser contract covers before/after frames, all listed item event types and Arena transformations; historical refetch gaps are counted and never filled with guesses.

## Phase 2 — shared build contract and pure accumulators

**Work**

- Generalize the existing build model so normal and Arena responses expose `builds[]: Build`; each `Build` contains one normalized core plus its typed slots and paths. Normal builds populate item slots; Arena also populates augment slots.
- Normalize choices to a shared compact representation containing `kind`, `id`, `position`, observed `time` and additive stats. Preserve null/unknown time where the source cannot provide it; do not invent timestamps or use time in choice/core identity.
- Keep the Arena statistics root and a reusable additive `StatisticalLeaf` separate from standard champion statistics. Migrate the standard build producer and all affected consumers/API serialization together, or provide a temporary compatibility projection with a removal phase.
- Add a mutable Arena accumulator with maps for normalized cores, core-conditioned item/augment slots and paths, global first Prismatics, global items and global augments. Its `finish()` returns immutable shared `Build` elements inside the Arena root.
- Update all applicable maps exactly once per participant-game and per observed choice/position, using distinct denominators.
- Derive rates/means from counts in the final projection. Keep paths secondary and follow the Phase 0 retention policy.
- Add invariants for population relationships and duplicate-game rejection.

**Gate**: existing standard build response semantics are preserved or the approved new response is synchronized across consumers; synthetic cases validate shared branch/slot behavior, Arena aggregation invariants and duplicate-game rejection without Mongo, Redis or scheduler dependencies.

## Phase 3 — bounded provider, persistence and rebuild orchestration

**Work**

- First try to generalize/reuse the existing bounded champion match/event stream so Arena can observe matches both with and without event documents while standard statistics retain their current exclusion rules. Add a separate Arena adapter only if it can use the shared lower-level batch/join mechanics. Choose batch size from measured payload and heap cost.
- Add `arena_champion_statistics` persistence keyed by champion + patch, complete-document upsert and `_id` lookup. Store schema/aggregation versions, coverage and update metadata.
- Verify BSON encoded size before each write and refuse writes over the approved ceiling without replacing the previous good document.
- Add queue registration through `QueueHandler`; decide route/channel in the relevant ADR. Report current patch/champion and total/completed/missing/failed work in existing job progress.
- Release decoded event trees after every batch and make rebuild retry/dedup behavior explicit.

**Gate**: Mongo test-database round trip, idempotent rebuild, byte-limit guard, cursor explain plan and bounded-memory evidence pass.

## Phase 4 — API/read contract (if requested for the first release)

**Work**

- Add a dedicated Arena read endpoint and canonical Arena statistics root. The build subtree uses the shared generalized build response; update standard build API docs and consumers in the same migration if its response changes.
- Return all stored candidate leaves in raw support/count form so consumers can sort by games, win rate or placement without duplicated `bestBy...` lists.
- Document winner semantics, placement, denominator/coverage, core definition, slot positions, sortability, pending/error states and version behavior.
- Add cache only if a real read path needs it; keep its key and invalidation tied to champion + patch + aggregation version.

**Gate**: controller, canonical `lol.model`, API index and endpoint docs agree; tests cover ready, absent and queued/pending states.

## Phase 5 — operational rollout and documentation closure

**Work**

- Rebuild a canary patch/champion set, compare source candidate counts with stored counters, inspect missing-data and event-rejection rates, and verify sampled response values manually.
- Rebuild a full patch only after the canary satisfies coverage and document-size thresholds.
- Update `docs/architecture/README.md`, the accepted Arena ADR, `docs/HANDBOOK.md`, Mongo collection/query/index docs and API docs as applicable.
- Record CodeGraph `status`, `explore` and `impact` outcomes after implementation changes; the current environment only exposes `codegraph_explore`, so status/impact need their supported CLI or tools restored before the code-change gate can be claimed complete.
- State exactly which static checks, tests, Mongo explain/round trips, API checks and production-like size checks ran. Do not report unrun evidence.

**Gate**: architecture/docs/API synchronization, approved BSON headroom, successful canary/full rebuild and operational progress visibility.

## Implementation boundaries

- Do not adapt standard `ChampionStatistics` / `ChampionStatsDocument` or `champion_stats` into the Arena statistics root.
- Generalize the shared `Build` model and producer so `builds[]` contains one `Build` per core, with dynamically sized typed slots, paths and normalized choices carrying ID/position/time. Reuse those containers for normal and Arena responses; an Arena `Build` may have augment slots and the Arena root owns Arena-specific placement and global aggregates.
- Keep the Arena statistics accumulator and persistence dedicated. Generalize the shared match/event source where that reduces duplicated stream handling without changing standard statistics eligibility or output behavior.
- Do not amend an accepted ADR silently. Record conflicts or route/collection decisions for the main architecture owner to accept.
- Preserve any in-progress checkout edits in `BuildSignature.java`, `ChampionBuildEngine.java` and related tests unless a separate, explicit task authorizes changing them.
