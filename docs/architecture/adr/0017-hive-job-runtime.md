# ADR-0017: Hive job runtime

- Status: Accepted
- Owner: Main agent
- Date: 2026-10-10
- Scope: generic job/queue extraction and current monolith wiring only
- Supersedes: infrastructure/package and inaccurate compute/progress clauses of ADR-0014; corresponding historical topology clauses of ADR-0010/0011

## Namespace clarified by the user

The original implementation brief specified `com.safjnest.shared.hive`.
The user subsequently requested an independent service namespace outside
`com.safjnest`, so the definitive package is **`com.hive`**, with no `shared`
wrapper. Hive names the infrastructure/runtime; a unit of work is still Job.
This placement is not a workaround for compiling the monolith. The standalone
artifact remains independently buildable. Other application code is not
transferred into Hive.

## Decision before moving code

Extract the JDK-only mechanism to `com.hive`, with `job`,
`scheduler`, `worker` and `status` subpackages. A runtime explicitly owns its
Registry, Router, schedulers and executors. No core singleton, application
bootstrap, SDK, database, Redis, Spring, JDA or BotLogger dependency is allowed.
Loading a class must not start workers or instantiate concrete schedulers.

Keep the application submission facade at `com.safjnest.lol.queue.QueueHandler`.
It composes one runtime explicitly from App and rejects submission before
bootstrap or after shutdown. It delegates to the same infrastructure used by
standalone Hive instances; there is no second dispatcher. All three monolith
schedulers share its registry to preserve cross-scheduler parent/child trees.
Standalone runtimes have independent PID sequences, context and deduplication.

The standalone `hive/pom.xml` builds the core artifact from its canonical
source directory without packaging or depending on monolith classes. No
source copy, process split or full multi-module migration is required.

The public core submission API relates scheduler route type to the submitted
route and carries an invariant result contract (`Class<T>` scalar shortcut or
`ResultType<T>`, with typed element contracts for lists). A conflicting active key must reject an
incompatible result contract before attaching a follower. Dedup identity is
scheduler + logical route + key, never physical placement or concatenated text
that can collide. Application compatibility methods may delegate after typed
scheduler lookup; no domain interpretation belongs in the core.

Local implementation destinations, chosen by responsibility:

- `com.safjnest.r4j.scheduler.RiotScheduler`: outbound shard queues, names and
  BotLogger hooks. Existing SDK rate protection and Redis owners stay local.
- `com.safjnest.lol.scheduler.ComputeScheduler`, `DatabaseWorkerType` and
  `ChampionMatrixRequest`: Mongo/domain placement, heavy champion reservation,
  filter snapshots, merge and compute/persistence calls.
- `com.safjnest.lol.scheduler.SyncScheduler`: shard/global route policy over
  generic direct virtual-thread execution. Sync has no physical queue.
- `com.safjnest.status`: application aggregation of generic Hive snapshots.
  Compatibility run classification is the local SyncScheduler projection hook;
  Hive accepts a classifier without interpreting any LoL key.

Generic snapshot records move to `com.hive.status` without changing their
wire fields. BotStatus and process/database metrics remain application models.

## Baseline behavior retained

The actual compute topology is four serial workers: PROFILE, PROFILE_2,
CHAMPION and MONGO. Profile placement selects load at insertion; heavy champion
matrix/build/refresh reservation restricts profile balancing to the two profile
workers. Already inserted jobs do not migrate. Historical documents describing
two workers are amended as stale, not implemented as a concurrency reduction.

Explicit Job item/progress reporting has precedence over completed direct-child
counters. Those counters are the fallback when explicit progress is absent.
Child priority is the more urgent of requested and parent priority, so an
IMMEDIATE child can exceed a BACKGROUND parent. Ordinary lanes are FIFO;
promotion puts queued work at the head of the more urgent lane without
preemption or duplicate execution.

Registry owns futures, follower links, body/child/callback completion and
context. Body return releases the worker independently of logical completion.
A successful parent with failed children retains COMPLETED_WITH_ERRORS and its
successful future result. Body/enqueue/callback failures remain exceptional.
Retain/resume restores context and releases the retained reference on error.

Future cancellation, registry job cancellation, body interruption and runtime
shutdown are separate operations. Cancellation of a waiting result is not a
promise that its physical body stopped. Shutdown closes admission and owned
executors and terminalizes active registry jobs; a body ignoring interruption
or a JVM crash cannot guarantee prompt physical completion.

## Explicit scope limits

No process split, inter-service API, domain DTO migration, broker, Redis Streams,
persistent backlog or distributed deduplication is added. ADR-0016 remains
proposed for those future changes. Writer ordering, Mongo schema, Redis key
names/TTLs, API payloads and Discord/audio presentation remain unchanged.

## Acceptance gates

Compile Hive with only the JDK, check dependency output, compile the application,
run deterministic lifecycle/policy/status tests and obtain independent integration
and semantics reviews. Record live Mongo/Redis/Riot verification separately from
offline checks. Final evidence belongs in `services/hive-validation.md`.
