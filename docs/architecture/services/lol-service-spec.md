# lol-service specification

Status: `SafJNest/lol-service` now contains a standalone Java 25 Spring MVC
application and the first alpha (`0.1.0-alpha-1`) is published. Its shaded jar
builds with a manifest main class and service-owned Mongo/Redis settings. The
Beebot process has not yet switched to HTTP, and lol-service still uses S4B as a
direct implementation dependency. The service/repository extraction is
therefore in progress, not complete.

## Purpose and boundary

`lol-service` is the Spring application that owns Beebot's LoL domain and
public HTTP API. Its callers are Bbot and S4B is its upstream acquisition
provider:

```text
Bbot -> lol-service -> S4B -> Riot APIs / Data Dragon
            |-> Mongo
            |-> lol-service Redis
```

`lol-service` owns public API controllers, request validation and response
mapping, canonical domain services, Mongo persistence, profile/champion/build/
record/leaderboard calculations, tracking and refresh orchestration, Compute
and Sync jobs, and domain/projection caches. It owns every write to Mongo and
the `los:*` Redis projections. It does not own Riot credentials, Riot SDK
objects, external rate protection, S4B's raw acquisition cache, or a Riot
request queue.

Bbot consumes only the public `lol-service` API for LoL. It retains Discord
commands, permissions and presentation; no LoL service or data access is
introduced into the Bbot process. S4B consumes no lol-service API and never
calls back into it.

## Application and package layout

Spring-owned code is under `com.safjnest.spring` in Beebot and has been copied
to the separate Maven repository. The service now has an independent bootstrap,
configuration, logger, Mongo/Redis adapters, and excludes the legacy SQL
migration and Discord presentation. Beebot still contains its old Spring
sources during the transition; remove them only after its callers use the
service API and the bot builds against that boundary. The internal `S4BClient`
and HTTP boundary are not implemented yet.

```text
src/main/java/com/safjnest/spring/
    controller/          # public LoL endpoints and error mapping
    spring/lol/service/         # domain, projections and persistence orchestration
    lol/client/s4b/      # future typed HTTP client for internal S4B API
    spring/lol/queue/           # QueueHandler; composes the monolith Hive runtime
    spring/lol/scheduler/       # ComputeScheduler and SyncScheduler only
    spring/lol/tracker/         # tracker and periodic admission
    nosql/               # Mongo and Mongo writers
    status/              # application status composition
```

The root Beebot application remains the Bbot process during this tranche; its
LoL commands and owner operations still have direct in-process Spring callers.
They migrate to an HTTP client as a separate Bbot-boundary tranche.
The existing HTTP paths, JSON, errors, cache meaning, Mongo document shapes,
Discord presentation and command behavior remain compatible.

## S4B client contract

`S4BClient` is the only code in `lol-service` that knows S4B's base URL,
authentication header, transport error envelope or timeout settings. Domain
services call typed client methods for account, summoner, ranks, masteries,
spectator, match IDs/detail/timeline and static data. The client serializes
only R4B wire contracts; it never returns an SDK class or a service runtime
object.

Acquisition is request/response HTTP. S4B queues each Riot call inside its own
process and returns its result. `lol-service` does not submit Riot jobs to its
Hive runtime, wait on an S4B job ID, or share queue state. A long-running
domain operation may be a parent job in the local Sync/Compute runtime; its
S4B calls remain ordinary bounded HTTP requests. Do not execute expensive
domain calculations on an HTTP request thread.

The client converts network failures, timeouts and S4B HTTP errors into typed
application failures. Domain services distinguish not-found from upstream
unavailable and preserve current public API status/body behavior. An S4B
timeout does not imply that its physical Riot request was cancelled; do not
retry automatically unless the operation has an explicit safe retry policy.

## Queue and runtime ownership

`LolServiceApplication` explicitly starts one Hive runtime and registers
`ComputeScheduler` and `SyncScheduler` on a local registry. Compute owns the
four PROFILE, PROFILE_2, CHAMPION and MONGO serial workers and existing
insert-time placement policy. Sync owns direct virtual-thread execution and
logical shard/global routes. Domain parent/child work, progress, Mongo writes,
deduplication and shutdown remain local to this service.

There is no `RiotScheduler` registration in the lol-service runtime after the
S4B cutover. Retain `QueueHandler` as the service submission facade; never
share its registry with S4B or Bbot. Hive remains JDK-only and receives no
LoL-specific routing or persistence policy.

## Mongo and Redis ownership

Keep Mongo connections, entities, writers, indexes and write ordering inside
`lol-service`. It alone owns the canonical writer cascades for summoner ranks,
competitive data and contextual Redis projections, and the match tracking
transition rules. S4B provides acquisition results; it does not persist them.

`lol-service` uses a dedicated Redis client/configuration and writes domain
and projection keys only. Move `los:*` keys and status keys whose source is
Mongo/domain state here. Move Riot raw cache/cooldown/static keys to S4B.
Even on one physical Redis cluster, use service-scoped credentials and key
ownership. Preserve key format, TTL and invalidation behavior during cutover;
where a Spring write needs to invalidate raw acquisition data, call the
narrow S4B invalidation endpoint instead of deleting its Redis key directly.

## API, configuration and operations

Keep the current public endpoint contract, validation, metadata and error
mapping. S4B's internal error is translated at this boundary; never expose
internal URLs, credentials, queue controls or SDK messages in public payloads.
Only Bbot's client is admitted to public service routes; only
`lol-service`'s credential is admitted to S4B routes.

Configuration is service-local: HTTP port, Mongo, domain Redis, S4B base URL
and credential, client connect/request timeouts, Hive limits, tracker settings,
and feature/runtime settings. Startup and shutdown manage only resources owned
by lol-service. Health/readiness report its own required dependencies; status
aggregation across processes is a later explicit contract.

## Implementation sequence and gates

1. Keep domain, tracker, Mongo and API ownership under `com.safjnest.spring`
   while preserving the root POM, App bootstrap and Spring server.
2. Add typed `S4BClient` with bounded timeouts, authentication and typed error
   mapping; keep URL and transport concerns outside domain services.
3. Replace direct `RiotGateway` and `RiotScheduler` calls with `S4BClient`;
   remove Riot SDK and Riot credentials from the lol-service dependency graph.
4. Verify package ownership and contract fixtures, Mongo writer
   ownership, queue policy, HTTP timeout/error behavior, and no dependency on
   JDA, S4B implementation classes or direct Riot SDK dependencies.
5. Move Bbot LoL callers to public and authenticated internal HTTP APIs; only
   after the process boundaries and both clients work independently remove the
   transitional Spring source from Beebot.
