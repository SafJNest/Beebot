# Arena analytics implementation phases

Status: Phase 2 realigned on 2026-10-02 to the final Arena requirement of
2026-10-01. The [contract](contracts.md) is authoritative; [schema](schema.md)
documents the implemented Java/JSON structure. Operational Phases 3–5 remain
pending; the previous purchase-sequence Phase 2 is superseded.

## Phase 0 — source audit and rollout gates

Verify the existing compact event shapes and resolver policies before changing
the parser. No invented transformations or participant attribution. Keep the
existing win rule explicit. Inspect representative real sources before claiming
source completeness, and measure BSON/cardinality/heap before persistence rollout.
Do not design core splitting, retention pruning or sample thresholds in advance.
No production timeline inspection, Mongo operation or backfill is authorized here.

## Phase 1 — reconstructed participant-game facts

- Keep `ParsedArenaGame` focused on placement, win, boots, equipment path, augment
  path and coverage. Remove frontend/core concepts from the parsed facts.
- Actually integrate `FirstPrismaticResolver`; preserve resolution types/reasons
  and ensure strict attribution/undo checks apply to all resolver evidence.
- Use timeline constraints, the P1 resolver and tooltip slots item0..item5 in that
  priority. Reconstruct a partial order; preserve source, timestamps, tooltip
  slots, general position and type position. Timeline overrides tooltip.
- Keep equipment and augments separate. Preserve available augment gaps and
  missing positions/times; do not infer chronology between the paths.
- Keep `220007` only as deduplicated anvil evidence. Preserve supported purchase,
  sale, destruction, explicit before/after and conservative undo behavior.
- Retain independent facts on missing/partial timelines, unknown classification,
  unresolved P1 and ambiguous order. Do not change Participant or Tracker.

## Phase 2 — shared Build model and pure accumulation

**Gate: passed for the pure parser/model/accumulator, offline.** Persistence,
provider loading, read dispatch and production readiness are not certified.

1. Reuse `StatisticalLeaf` and shared Build choice/slot/timing primitives.
   Use the current Arena payload described in [schema](schema.md), preserving
   standard behavior. Remove superseded Arena fields/constructors/types; no
   obsolete-document compatibility layer or regression-test gate.
2. Replace `Map<List<Integer>, Branch>` with generic core keys:
   boots+A1 or boots+P1. Both types use the same cores/steps structure.
3. Accumulate core-independent boots, A1..A6, P1..P6 and dynamic L1..Ln
   distributions, keeping explicit denominators, placement histograms, time
   samples and missing-position coverage.
4. Accumulate actually observed boots+P1+ordered-Legendary builds. Pool augment
   and later-Prismatic variations. Never synthesize a top-per-slot build.
5. Materialize observed progressive steps with full equipment/augment context.
   Separate Legendary, Prismatic and position-indexed augment choices. Support
   direct A3 inspection without selecting A2. Retain one-game exact steps.
6. Preserve raw, complete distributions; no top-N pruning, backend sample
   threshold or automatic fallback. Frontend owns context/backoff selection.
7. Deduplicate matches before mutation and each core/step/choice per participant-
   game. Keep exact/fallback/unresolved evidence and coverage reason counters.
8. Finish into detached snapshots through `JsonCodec`. Keep the accumulator
   independent of Mongo, Redis, catalog fetching and queues.
9. Replace the internal earlier Arena statistics-root projection with shared
   Build-owned Arena data, without adding unnecessary services/classes.

### Required test matrix

The targeted suite verifies the following final-contract cases. JSON/BSON tests
use in-memory structured documents; operational source and database gates remain
separate.

| Area | Required evidence |
|---|---|
| P1 resolver | timeline resolution, tooltip fallback, retained resolve type, NOT_FOUND preserves independent stats |
| Order | timeline overrides tooltip; P1/L1/P2/L2; both partial-order examples in contracts; ties and untimed facts |
| Positions | A1..A6, P1..P6, L1/L2/…; general/type positions; augment gaps; no invented augment timestamps |
| Boots | own winrate/sample denominator, missing boots, derived boots cannot resurrect undone purchase |
| Cores | boots+A1 and boots+P1 share generic structure; root pooling; no mandatory triple; independent missing anchors |
| Steps | equipment/augment prefix progression, full context, A3 directly, separate choice kinds, exact step with one game retained |
| Observed builds | boots+P1+ordered Legendary identity; augment/P2 variations pool; timestamps do not split identity; no synthetic combination |
| Anvil | 220007 excluded from equipment/build/core/context/choices; same-time purchase/destruction counts one use |
| Attribution | participantId/reference 0, missing/conflicting actors, duplicate identities; resolver cannot bypass rejection |
| Transitions | cancelled purchase removed; sale/destruction preserve historical acquisition; supported before/after; ambiguous/untimed undo; duplicate events/purchases |
| Incomplete data | missing/partial timeline keeps valid independent stats and labelled fallback; unknown position/time counts and denominators |
| Populations | choice counted once per participant-game/key; duplicate match rejected before updates; two matching participants count twice, match once |
| Serialization | current Arena/standard JSON and in-memory structured BSON round trips; detached snapshots; nullable facts |
| Standard behavior | current Build serialized field set/results; no Arena payload on standard; standard generator/producer/consumers unchanged |

### Current validation — 2026-10-02

Maven offline compilation and 105 targeted tests pass with zero failures, errors
or skips: ArenaGameParserTest (33), ArenaChampionAnalyzerTest (22),
FirstPrismaticResolverTest (8), BuildTest (1), BuildSignatureTest (6),
ChampionBuildEngineTest (10), ChampionBuildProviderTest (2),
ChampionBuildTimelineUtilsTest (4), MongoChampionBuildRecordTest (4),
LolApiConfigTest (5), FilterTest (4), ChampionServiceMatrixTest (4),
BuildUtilsTest (2). The full targeted run was repeated after removing obsolete
Arena fields/types/constructors and the old-document compatibility test.
No compatibility test for superseded Arena aggregates is required.

The tests cover timeline/resolver/tooltip ordering, all six P/A positions,
dynamic Legendary positions, both generic cores, progressive cross-prefix
contexts (including one-game steps), observed-build pooling, anvil exclusion,
strict attribution/undo, duplicate populations, conditional denominators,
incomplete histories, immutable snapshots and JSON/in-memory structured BSON.
Current standard serialized field-set/results remain unchanged.
Standard provider timeline eligibility,
Mongo source projections, Filter defaults, service matrices and JSON
configuration retain their existing behavior.

Source/target 25 compilation ran on installed OpenJDK 26.0.2 (the local
`openjdk@25` alias resolves to 26). Actual JDK 25 execution was not tested.
Existing deprecation/Lombok/Unsafe warnings remain. Some existing Filter/JSON
reader initialization logs unreachable external static-data requests; those
requests are not runtime validation and the tests still pass. The new pure
Filter factory used by `finish()` does not fetch static data.

No full Maven suite, network-dependent ChampionControllerTest, live API,
Mongo/Redis integration, production timeline inspection, BSON size/cardinality,
heap measurement or query explain was run. Mongo tests exercise detached
projection helpers only. No production operation, backfill or commit occurred.
The diff started from the prior documentation realignment; those edits were
preserved and updated to record current implementation. Participant, Tracker,
standard generator/provider, operational writers/queries/caches, presentation
and accepted ADRs are unchanged.

## Phase 3 — existing provider and same-collection persistence

Use existing provider/service/MongoDB owners, with a bounded match/event join
that delivers Arena matches even when timelines are missing. Preserve standard
timeline eligibility. Supply immutable catalog and explicit source-completeness
information outside the accumulator.

Reuse `champion_builds`: one complete document per champion + patch + CHERRY.
Use the normal filter-key identity/envelope, with neutral lane/rank/region and
no opponent/duo variants for the initial Arena scope. Version/discriminate the
Arena payload so existing standard CHERRY documents are not mistaken for it.
Only the current Arena schema is supported. Discard obsolete Arena aggregates
and regenerate whole documents from source matches; do not add conversion,
compatibility readers or tests to preserve old Arena document shapes.

Do not create separate Arena collections, duplicate writers, independent
services, core documents or a migration/backfill now. Only measured BSON or
cardinality problems may justify future core splitting in the same collection.
Verify size/headroom, atomic replacement, existing identity/index usage, in-memory
and test-database round trips, explains and heap before rollout.

Submit operational work through existing QueueHandler owners. A future rebuild
must expose current champion/patch/shard as applicable plus total/completed/
missing/failed. No production scheduler or backfill run is part of Phase 2.

## Phase 4 — read/API integration when requested

Review the existing ChampionService/ChampionView contract before exposing Arena
data. Reuse canonical Build-owned data and established status/parameter/cache
owners. No dedicated endpoint or second success DTO is required by this model.
Document any explicitly authorized additive projection and test its consumers.
Until then, the existing champion endpoint, including CHERRY, keeps its current
generator/response. Frontend owns exact/generic/fallback/observed-build selection.

## Phase 5 — measured operational rollout

Canary source coverage and actual size checks precede rebuilds. Do not drop
rare steps to pass a size gate. Keep splitting into the same collection as a
future decision requiring measurements, not a predesigned branch of this plan.
Complete source, test-Mongo round trip, explain, heap, cache and progress gates
before claiming rollout. No production operation is authorized by Phase 2.

## Documentation and final review

Synchronize Arena, architecture, handbook, API and Mongo documentation with the
implemented model and the deferred operational hookup. Validate local Markdown
links and the serializer-generated JSON example; review diff boundaries against
preexisting documentation edits. Use CodeGraph explore/impact and verify the
index is current. Report Java schema, JSON, changed files, tests and remaining
limits; do not describe the current standard CHERRY endpoint as serving Arena.

### Files in the realignment

| Area | Files |
|---|---|
| Parsing/evidence | `src/main/java/com/safjnest/lol/arena/ArenaGameParser.java`, `ArenaItemCatalog.java`, `FirstPrismaticResolver.java`, `ParsedArenaGame.java` in the same package |
| Shared model | `src/main/java/com/safjnest/lol/model/ArenaBuildData.java` (new), `Build.java` (obsolete Arena fields/constructors/Core/Path removed), `Filter.java`; `src/main/java/com/safjnest/lol/model/statistics/StatisticalLeaf.java` |
| Accumulation | `src/main/java/com/safjnest/lol/service/ArenaChampionAnalyzer.java` |
| Removed competing root | `src/main/java/com/safjnest/lol/model/statistics/ArenaChampionStatistics.java` |
| Tests | `src/test/java/com/safjnest/lol/arena/ArenaGameParserTest.java`, `src/test/java/com/safjnest/lol/service/ArenaChampionAnalyzerTest.java`, `src/test/java/com/safjnest/lol/model/BuildTest.java` |
| Arena documentation | `docs/arena-build/README.md`, `contracts.md`, `phases.md`, `schema.md` in the same directory |
| Ownership/API/Mongo documentation | `docs/architecture/README.md`, `docs/HANDBOOK.md`, `docs/api/champion/page.md`, `docs/api/lol-api.md`, `docs/mongo/README.md`, `docs/mongo/08-query-inventory.md` |

The six shared documents retain their previous non-Arena content. Arena schema
and fixture JSON are synchronized with the actual model/serializer, with checked
placement histograms, denominators, rates and newly added local links. CodeGraph
is up to date and the final diff passes whitespace checks. No source outside the
listed Arena/shared-model boundaries was modified.
