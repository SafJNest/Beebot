# Hive: evidence and implementation gate

Date: 2026-10-10. Branch: `beebot-servicies`.
Initial user-requested snapshot commit: `a970531a` (service separation proposal).
Hive implementation is uncommitted; no push or PR was created.
Architecture: [ADR-0017](../adr/0017-hive-job-runtime.md).

## Definitive namespace and owners

Hive is the infrastructure/runtime name. An individual work item is `Job`.
The original brief named `com.safjnest.shared.hive`; the user's subsequent
clarification supersedes that placement. The final root is **`com.hive`**,
outside `com.safjnest`, without a `shared` wrapper. It is an independent
namespace, not a workaround for compiling the current monolith.

- Generic mechanism: `com.hive`, with job/scheduler/worker/status subpackages.
- Application composition/submission: `com.safjnest.spring.lol.queue.QueueHandler`.
- Riot adapter: `com.safjnest.s4b.scheduler.RiotScheduler`.
- Compute/Sync/domain merge: `com.safjnest.spring.lol.scheduler`.
- Monolith has one explicitly owned registry shared by all three schedulers;
  independent Hive instances share neither context nor deduplication.
- Standalone build: `hive/pom.xml`, coordinates `com.hive:hive:1.0`.
  It reuses the 19 canonical Java sources without copying them or packaging
  other Beebot classes. The artifact contains 24 class files, all under com/hive.

Compute retains four serial workers: PROFILE, PROFILE_2, CHAMPION, MONGO.
Profile placement is selected at insertion; champion matrix/build/refresh work
reserves CHAMPION and restricts profile placement to PROFILE/PROFILE_2.
Riot retains one serial queue per shard and existing SDK protection. Sync
uses direct virtual-thread execution and has no physical queue.

Explicit progress precedes child counters. Priority, FIFO, promotion without
preemption, logical followers, successful COMPLETED_WITH_ERRORS parents,
contextual callbacks, worker release separate from future completion, cleanup
and separate future/job cancellation are characterized by tests. Typed result
contracts reject incompatible scalar/list reuse. The core rejects follower
cycles and promotes sources even when reuse races with physical dispatch.

## API, storage and presentation gate

Domain success models remain in lol.model. Generic snapshots in com.hive.status
are the narrow package exception recorded in AGENTS/ADR-0017. The application
retains three complete status levels plus at most 100 jobs at the fourth and
20 queued jobs per worker; its total cap is Integer.MAX_VALUE to preserve the
complete levels. Generic standalone runtimes can configure a stricter total cap.

StatusController and `/api/status` wire fields, record field order and visible
scheduler/run names are preserved and tested. Other exposed domain endpoints
require no contract update. Writer ordering, persisted schemas, queries/indexes,
Redis namespaces/TTLs and Discord presentation are unchanged. No query/index
change requires a new Mongo explain. Audio/VoiceRSS have no diff.

## Final successful verification

All results below are from the final com.hive namespace, after clean builds.

| Check | Result |
|---|---|
| Main application compilation and focused tests, JDK 25 | PASS: 43 tests, zero failures/errors/skips |
| RouterTest | 14 tests |
| HiveLifecycleTest | 21 tests |
| ComputeSchedulerTest | 3 tests |
| ChampionMatrixRequestTest | 3 tests |
| StatusControllerTest, including JSON fixture | 2 tests |
| Standalone `mvn -o -f hive/pom.xml clean verify` | PASS: 35 core tests, core JAR built |
| Independent semantic reviewer, raw JDK/JUnit build | PASS: 35 core tests |
| Raw `javac --release 21`, core plus README example | PASS without application/dependency classpath; example prints 42 |
| `jdeps -s hive/target/hive-1.0.jar` | Only java.base |
| JAR inspection | 24 classes; none outside com/hive; coordinates com.hive:hive |
| Import/legacy reference checks | No external core imports, old singleton or operational old namespace |
| CodeGraph | Up to date; exploration/impact and namespace migration reviewed |
| `git diff --check` | PASS |

Main command actually executed:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  mvn -o clean '-Dtest=RouterTest,HiveLifecycleTest,ComputeSchedulerTest,ChampionMatrixRequestTest,StatusControllerTest' test
mvn -o -f hive/pom.xml clean verify
```

Core tests cover isolated/non-LoL runtimes, foreign jobs/registrations, invalid
routes, typed scalar/list results, dedup/follower propagation and cycles,
promotion before/after dispatch, priority/FIFO/no preemption, completed and
failed children, callback/context failure, enqueue/body failure, cleanup,
future/job cancellation, shutdown, progress and snapshot caps. Local tests
cover four-worker placement, champion reservation and matrix merge lifecycle.

## Independent reviews

**Integration/configuration reviewer: approved.** Read-only review checked
explicit bootstrap before Spring admission, one shutdown owner/shared registry,
all callers/imports, typed routes/results, four compute workers, context-aware
continuations, per-instance Riot logging, matrix cleanup tied to real termination
rather than caller-view cancellation, status bounds and final com.hive paths/POM.
This reviewer did not claim an independent test execution.

**Semantics/structure reviewer: approved.** All reported P1/P2 issues were fixed
and re-reviewed: unsafe supertype result tokens, cross-tree follower cycles,
waiting-parent cancellation, serial worker interruption, composed-future retention,
lock inversion, predispatch promotion and snapshot/observation races. The reviewer
independently compiled final com.hive and passed its 35 core tests.

No remaining blocking review finding. Reviewers made no repository edits.

## Broader suite and live limits

The broader application suite was attempted but cannot be reported as passing.
Several tests initialize remote Mongo/Riot/Data Dragon paths; this environment
encountered Mongo DNS and Data Dragon timeouts. Affected attempts included
LiveGameTest, ProfileServiceTest, ChampionControllerTest, MongoMetricsSamplerTest
and SummonerWarmingTest. No full application startup or live Mongo/Redis/Riot
integration success is claimed. Focused tests and independent core proofs are
reported separately above.

Three unrelated failures were reproduced in an isolated git archive of the
initial commit a970531a, without altering the working repository:

| Baseline test | Reproduced failure |
|---|---|
| PatchUtilsTest.returnsRecentPatchesInAscendingVersionOrder | UnsupportedOperationException; baseline report has 2 errors |
| ProfileMatchupsTest.serializesOnlyKeyedRawAccumulators | AssertionError; baseline 7 tests, 1 failure |
| MongoDBTest.matchDocumentsUseNamedBansAndFlatParticipants | AssertionError; baseline 15 tests, 1 failure |

These failures were not fixed as part of the job extraction. Historical stale
compiled tests were removed by the final clean builds; they are not part of the
43 successful focused-test result. No crash recovery, durable backlog,
distributed dedup, exactly-once execution or guaranteed interruption of a blocked
body is promised. Other service/process/DTO extraction remains proposed.
