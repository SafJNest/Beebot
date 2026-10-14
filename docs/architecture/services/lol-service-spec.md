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

## Riot-shaped proxy and S4B client

The shared [Riot endpoint proxy specification](riot-endpoint-proxy-spec.md)
defines the route host/path, typed R4B response contracts, priority header and
rate-limit lifecycle. The public/domain APIs remain owned by `lol-service`;
the Riot-shaped routes are an additional acquisition surface and proxy to S4B.

`lol-service` uses a catch-all proxy mapping for those routes. It matches the
incoming method/path against the cached S4B manifest, then forwards only a
known operation's method, path, query and body to the matching configured S4B
service host. It does so without carrying R4J, duplicating `URLEndpoint`, or
keeping a second path catalog. The manifest is fetched from the private
`S4B_CONTROL_BASE_URL` and is never exposed through the public proxy. Typed domain
client methods decode the current acquisition operations into their existing
R4B DTOs using stable operation IDs from S4B's authenticated
`GET /.well-known/s4b/routes` manifest, generated from its typed bindings. The
manifest schema is validated and cached by version/ETag; domain methods pass
typed arguments to the client and never handwrite Riot URI templates here. The
proxy does not create a generic JSON request/response envelope.

Spring sets `X-S4B-Priority: IMMEDIATE|NORMAL|BACKGROUND` from local operation
policy on the private request to S4B, replacing any caller-supplied value. S4B
owns actual queue admission, Riot rate limiting, retries and cache behavior.
`lol-service` does not register `RiotScheduler`, wait on remote job IDs or share
Hive state with S4B.

If Riot rate-limits an admitted request, S4B keeps its job pending and
`lol-service` keeps the HTTP exchange open until a terminal result arrives.
Spring must use asynchronous request handling so the waiting exchange does not
occupy a servlet worker. Configure upstream per-attempt timeouts, not a total
acquisition timeout that expires during a 429 cooldown. After S4B admits the
request, there is intentionally no application wall-clock deadline; the
service client and ingress must not impose a shorter total/idle timeout. Do not
translate that wait to
`202`, a job ID, an early `429`, or a premature `504`. Queue saturation can
reject a request before it is admitted. Terminal errors become typed
application failures; existing public domain API status/body behavior remains
unchanged. The runes and versions static resources preserve the exact Data
Dragon JSON response shape without a generic envelope.

The internal client owns S4B's service URL and authentication. It strips
hop-by-hop and untrusted internal headers, and never forwards Riot credentials.
It authenticates with `Authorization: Bearer <S4B_SERVICE_TOKEN>` over TLS,
setting the token from service configuration rather than copying the caller's
authorization header.
If a Spring-side domain write must invalidate an S4B cache entry, call a narrow
authenticated invalidation operation using typed endpoint identity and
canonical identifiers; never touch S4B Redis keys directly.

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
and credential, connection and long-wait policy, Hive limits, tracker settings,
and feature/runtime settings. Startup and shutdown manage only resources owned
by lol-service. Health/readiness report its own required dependencies; status
aggregation across processes is a later explicit contract.

## Implementation sequence and gates

1. Start in parallel with S4B against the approved shared proxy contract.
2. Implement the catch-all proxy, internal S4B client, asynchronous response
   lifetime and typed client methods against the route/DTO contract.
3. Replace direct `RiotGateway`, `RiotScheduler` and S4B cache access; remove
   S4B implementation classes and direct Riot SDK dependencies from the
   lol-service graph.
4. The independent control agent checks host/path forwarding, headers, DTOs,
   long rate-limit waits, domain ownership and public API compatibility during
   work and at handoff against the same contract used by S4B.
5. Move Beebot LoL callers to lol-service HTTP and remove transitional Spring
   sources only after the service boundary is working.
