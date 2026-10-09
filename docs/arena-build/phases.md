# Arena analytics implementation phases

Status: Phase 2 realigned on 2026-10-02 to the final Arena requirement of
2026-10-01. The [contract](contracts.md) is authoritative; [schema](schema.md)
documents the implemented Java/JSON structure. Phase 3 internal implementation follows the approved disjoint-path write
contract; live operational gates and Phases 4–5 remain pending; the previous purchase-sequence Phase 2 is superseded.

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

### Historical Phase 2 validation — 2026-10-02

Before Phase 3, Maven offline compilation and 105 targeted tests passed with zero failures, errors
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

## Phase 3 — existing provider and same-document persistence

Approved operational contract: exactly one champion_builds document per normal
filterKey; Arena lives only in build.arena alongside standard fields. Standard
and Arena own disjoint paths. MongoDB standard single/bulk writes use `$set` of
root metadata and individual standard build.* fields; Arena sets its complete
subtree atomically. No whole build set, replacement, CAS, read-merge-replace,
new identity/discriminator/collection or duplicate service. Both refresh orders
preserve the other population; an Arena-first document is not standard-ready.

ArenaBuildData is the pure accumulator result, schemaVersion=3 and
aggregationVersion=3. The schema increment represents semantic JSON identities
and collections, not a change to stats/order/denominators. Build.arena factory
is removed. Standard Mongo read projection strips Arena before deserialization,
preserving API and standard readability even with malformed Arena content.

ChampionBuildProvider/MongoDB use a bounded left join of <=100 matches and
match_events. Missing timelines still reach Arena; standard eligibility remains
unchanged. Scope is exact full patch (at least three numeric segments), CHERRY,
neutral champion dimensions and canonical GREATER_OR_EQUAL rank behavior with
no rank. Source projection retains all participant identities. Batch/BSON data
are released; inputs and catalog are detached. Catalog is supplied by the internal
caller for that patch; no current-patch fetch is introduced. Completeness remains
false absent explicit source evidence; corrupt payloads fail rather than falling
back as missing timelines.

ChampionService and ComputeScheduler reuse QueueHandler/CHAMPION with internal
opt-in entrypoints, distinct dedup key, and heavy-job reservation. No endpoint,
command or automatic refresh calls Arena. Progress exposes current champion,
patch, shard=ALL and source total/completed/missing/failed in phase. Total counts
discovered candidates; missing overlaps completed. Structured progress follows
one aggregate item to avoid per-match retained maps. Write/source failures fail
the job and propagate exceptions.

Local BSON payload/update checks reserve 256 KiB below 16 MiB before Mongo access.
They do not measure the unknown existing standard subtree. The server's atomic
final-document size check preserves the old subtree on rejection. Test-database
round-trip and actual combined BSON/headroom/explain remain rollout gates.
No pruning, split cores, production operation, rebuild, backfill or commit.

### Current Phase 3 verification — 2026-10-02

**Internal implementation/offline gate passed; live operational gates open.**
Final coordinated Maven offline run: 151 tests across 19 suites, zero failures,
errors or skips, completed at 16:27 Europe/Rome. All main/test sources compiled
with source/target 25 on OpenJDK 26.0.2 (local openjdk@25 alias); actual JDK 25
execution remains untested. Existing deprecation/Lombok/Unsafe warnings remain.
Some preexisting Filter/static-data initialization logs unreachable Redis/Riot
requests; those do not establish any successful live service validation.

| Suite | Tests |
|---|---:|
| ArenaGameParserTest | 33 |
| ArenaChampionAnalyzerTest | 22 |
| FirstPrismaticResolverTest | 8 |
| BuildTest | 1 |
| BuildSignatureTest | 6 |
| ChampionBuildEngineTest | 10 |
| ChampionBuildProviderTest | 3 |
| ChampionBuildTimelineUtilsTest | 4 |
| MongoChampionBuildRecordTest | 4 |
| LolApiConfigTest | 5 |
| FilterTest | 4 |
| ChampionServiceMatrixTest | 4 |
| BuildUtilsTest | 2 |
| MongoChampionArenaSourceTest | 7 |
| MongoChampionArenaPersistenceTest | 8 |
| ChampionServiceArenaTest | 9 |
| ComputeSchedulerArenaTest | 5 |
| RouterTest | 14 |
| ChampionMatrixRequestTest | 2 |

The new source tests prove detached projection/hydration helpers, absence/empty/
corrupt events, full-patch query shape and unchanged standard eligibility offline.
Persistence tests exercise the actual MongoDB writer against an isolated in-memory
collection proxy: both refresh orders, concurrent disjoint updates, bulk path,
true encoded BSON/JSON round-trip of the combined envelope, pre-write size and
serialization failures, standard isolation from unsupported Arena, acknowledgment
and simulated server rejection. The proxy proves operation shape/error handling,
not Mongo server atomicity, index use or final-document enforcement.

Service tests exercise the real pure pipeline with injected source/writer,
fallback and empty source, fail-closed invalid/duplicate source, write rejection,
filter snapshot and bounded Job item state. Scheduler tests use the real local
QueueHandler with synthetic work for CHAMPION/background routing, dedup/follower,
caller mutation, heavy reservation and retry. Test workers are shut down; no
actual data rebuild is submitted. Independent reviews: orchestration reviewed A,
provider reviewed model/B, Mongo reviewed C; fixes remained with their owners.
An initial test compilation signature error and status route-name expectation
were corrected before the final fully passing run.

Re-executing relevant Phase 2 parser/model/standard regressions was justified by
finish interface, JSON schema, provider, standard writer and reader changes.
Historical 105-test evidence above remains separately recorded.

Four isolated offline synthetic sizing runs on OpenJDK 26.0.2/-Xmx512m, batch100,
measured encoded BSON and GC-based retained heap. The [sizing report](phase3-sizing.md)
records fixtures, methodology and limits. Fixed contexts (1k/10k matches) encoded
the same 43,408-byte combined fixture; retained accumulator grew 0.26→1.29 MiB.
Unique Legendary-per-match fixtures (500/1000 matches) encoded 10,407,648 /
20,799,653 bytes and retained 19.10 / 37.82 MiB in the accumulator. The latter
exceeds 16 MiB and must be rejected; it is evidence of cardinality risk, not
permission to prune or implicitly split. Sampled allocation high-water is not
precise peak/RSS; catalog is included in baseline. Synthetic catalog/contexts,
empty standard option lists and absent timelines are not representative data.

Final CodeGraph status up to date, explore/impact reviewed and whitespace checks
pass. Arena Java shape, semantic JSON and placement histogram arithmetic checked.
All new local documentation links resolve; three missing links already present
at HEAD remain outside this implementation: architecture ADR-0004 link,
Mongo leaderboard-rank-indexes link and API rank-history link. No accepted ADR,
controller, exposed HTTP payload/status shape, Participant, Tracker or presentation changed.

No full Maven suite, live API/Redis validation, representative timeline audit,
live Mongo test-database round-trip, index options/explain, representative combined
BSON/cardinality/heap or rollout was performed. mongod/mongosh/docker were not
available in PATH; no isolated Mongo was demonstrated and the configured URI was
not used. Current runtime names are production=beebot_test and testing=
beebot_test_test; old ADR configuration names are stale and have not been amended.
No production operation, backfill, migration, pruning or commit occurred.

### Phase 3 files and reused owners

| Area | Files |
|---|---|
| Source | ChampionBuildProvider.java; MongoDB.java bounded left join/projection |
| Pure model | ArenaBuildData.java; ArenaChampionAnalyzer.java; Build.java removes ambiguous factory |
| Persistence | MongoDB.java standard single/bulk path updates, Arena subtree update/read, BSON guard and standard projection |
| Orchestration | ChampionService.java; ComputeScheduler.java; existing QueueHandler/Job/Registry reused unchanged |
| Tests | ChampionBuildProviderTest.java and ArenaChampionAnalyzerTest.java updated; MongoChampionArenaSourceTest.java, MongoChampionArenaPersistenceTest.java, ChampionServiceArenaTest.java, ComputeSchedulerArenaTest.java added |
| Documentation | Arena README/contracts/phases/schema, historical phase3-analysis and phase3-sizing; architecture README, handbook, Mongo README/query inventory and API champion/page/index notes synchronized for internal-only scope |

Standard root metadata remains standard-owned. Arena-first insert stores no
standard ready marker. Arena JSON uses schema3; unsupported old subpayloads
are missing/rebuilt rather than adapted. Internal caller supplies patch-correct
immutable catalog explicitly. No automatic Arena API, scheduler or cache hookup.

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

### Historical Phase 2 realignment files

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
is up to date and the final diff passes whitespace checks. In Phase 2, no source outside the
listed Arena/shared-model boundaries was modified. Phase 3 extends the provider,
Mongo/service/queue owners listed in its dedicated file table above.
