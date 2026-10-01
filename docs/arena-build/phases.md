# Arena analytics implementation phases

The user contract of 2026-10-01 authorizes Phase 2's pure computation independently of the earlier persistence/source feasibility gates. Accepted ADRs remain unchanged; standard behavior follows ADR-0006/0012. See [contracts.md](contracts.md) for the reconciled semantics.

## Phase 0 — source and rollout gates

The existing win rule and strict evidence fallback remain valid. Historical timelines have no verified inventory selection frames. Missing choices/positions/times stay unknown. Before persistence, inspect representative real timelines, validate item catalog coverage and complete-stream guarantees, measure BSON cardinality/headroom and agree retention/partitioning. These production gates remain open and do not authorize backfill.

## Phase 1 — parser source seam

The original optional v1 `arena_evidence.first_prismatic` signal remains supported. Its Tracker/repair writer and augment sidecar were removed at the user's request. Phase 2 replaces the snapshot-based core with relevant purchase history, includes 220007 and supports observed Prismatics in positions 1–6. No new ingestion writer or Participant fields are introduced. Existing augment list ordering is the available source; raw missing Riot slots cannot be restored.

## Phase 2 — shared model and pure accumulator

- Extend Build with optional shared ordered core, typed dynamic item/augment slots, choices, timing and paths. Keep every existing standard constructor, field, generator and consumer behavior; standard JSON omits the optional fields. Arena uses `builds[]: Build`.
- Add reusable StatisticalLeaf and the separate ArenaChampionStatistics root in `lol.model`.
- Parse actual supported timeline events with strict attribution and the documented purchase/sale/destruction/undo/transformation policy. Retain independent choices/outcomes when chronology is missing or partial.
- Accumulate global boots/items/Prismatics/augments, ordered Prismatic slots 1–6, and core-conditioned slots/paths. Deduplicate matches before mutation and choices within participant-games. Expose distinct outcome, position, timing and coverage denominators.
- Keep catalogs as supplied immutable data; no Mongo/Redis/scheduler access. Finish into detached immutable snapshots through the existing JSON/BSON codec.
- Test all requested transitions, coverage, duplicate/denominator invariants, serialization and standard regressions. Synchronize Arena/API/Mongo docs without changing existing endpoints or query owners.

**Gate: passed for pure Phase 2 computation.** Parser → accumulator → shared Build/Arena root is connected; standard producer and consumers are unchanged and regression-tested. No Phase 3/4 operational gate is implied.

**Validation (2026-10-01):** Maven offline compile and the final targeted suite passed with 67 tests, zero failures/errors/skips. The suite includes 22 Arena parser tests, 11 Arena accumulator tests, plus Build serialization, standard BuildSignature/ChampionBuildEngine/timeline regression tests, Spring JSON configuration and the isolated first-Prismatic diagnostic classifier tests. JSON and structured BSON codec round trips passed in memory; this is not a Mongo server round trip. BuildTest asserts the exact existing standard serialized field set and old optional-field deserialization.

Compilation used source/target 25 on the installed OpenJDK 26.0.2 runtime (the local `openjdk@25` alias resolves to 26). Runtime on an actual JDK 25 was not tested. Existing deprecation/Lombok compiler warnings remain.

An earlier wider targeted run failed one of six ChampionControllerTest cases during external static-data initialization with an unreachable network/Data Dragon request (`ExceptionInInitializerError` / R4J timeout). The final offline suite excludes that network-dependent class. The full Maven suite, actual JDK 25 execution, live API, Mongo/Redis integration, production source inspection, BSON cardinality/size measurements, query explain and heap measurement were not run. No production Mongo operation, backfill or commit was performed.

An independent read-only review found and resolved attribution/sidecar conflicts, untimed undo handling, timing preservation and stale derived boots after undo; each correction has a regression test. The final diff began from a clean checkout and is restricted to Arena computation, additive shared Build containers, targeted tests and synchronized documentation.

## Phase 3 — bounded provider, persistence, orchestration

Reuse/generalize the existing bounded match/event join, preserving standard eligibility while emitting Arena matches without timelines. Supply catalog and explicit completeness evidence. Add the separate Arena collection and atomic full-document upsert, measured BSON guard/headroom, versioning and query explain checks. Enqueue through QueueHandler with current champion/patch and total/completed/missing/failed progress. Mongo, Redis, scheduler and Tracker retain their respective ownership. Resolve route and retention decisions before rollout.

## Phase 4 — read/API contract

Only when requested, expose the canonical Arena root through an agreed dedicated read path. Document coverage, rates, sortability, versioning and pending/error states; test controller statuses and cache ownership. The existing ChampionView and standard build contract remain compatible.

## Phase 5 — operational rollout

Canary source coverage and measured size checks precede full rebuilds. No production operations are authorized by Phase 2. Complete Mongo round trip, explain, bounded heap, source inspection and operational progress gates before declaring rollout complete.

## Review

Keep shared model edits serial. Use a separate read-only reviewer after Phase 2 implementation to check the diff, consumer compatibility, API/docs and edge cases. The main agent resolves findings and reports actual checks without claiming production evidence.

## Phase 2 changed files

Computation and shared models:

- `src/main/java/com/safjnest/lol/arena/ArenaGameParser.java`
- `src/main/java/com/safjnest/lol/arena/ParsedArenaGame.java`
- `src/main/java/com/safjnest/lol/arena/ArenaItemCatalog.java`
- `src/main/java/com/safjnest/lol/service/ArenaChampionAnalyzer.java`
- `src/main/java/com/safjnest/lol/model/Build.java`
- `src/main/java/com/safjnest/lol/model/statistics/StatisticalLeaf.java`
- `src/main/java/com/safjnest/lol/model/statistics/ArenaChampionStatistics.java`

Tests:

- `src/test/java/com/safjnest/lol/arena/ArenaGameParserTest.java`
- `src/test/java/com/safjnest/lol/service/ArenaChampionAnalyzerTest.java`
- `src/test/java/com/safjnest/lol/model/BuildTest.java`

Synchronized documentation:

- `docs/arena-build/README.md`
- `docs/arena-build/contracts.md`
- `docs/arena-build/phases.md`
- `docs/architecture/README.md`
- `docs/HANDBOOK.md`
- `docs/api/lol-api.md`
- `docs/api/champion/page.md`
- `docs/mongo/README.md`
- `docs/mongo/08-query-inventory.md`
