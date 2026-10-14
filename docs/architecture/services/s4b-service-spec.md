# S4B service specification

Status: Maven package alpha published at commit `e4424d6`; HTTP service process,
Spring client and deployment remain unimplemented. S4B means **Stelar for Beebot**.

## Purpose and boundary

S4B is the Riot acquisition owner. The target topology gives it an internal
Spring HTTP API, called only by `lol-service`:

```text
Bbot -> lol-service -> S4B -> Riot APIs / Data Dragon
                         |-> S4B Redis
```

S4B owns Riot credentials, SDK initialization, platform/region routing, Riot
rate protection, acquisition queues, raw Riot response cache, static-data
cache, and conversion from SDK objects to SDK-independent R4B contracts.
`lol-service` owns the LoL public API, Mongo, domain calculations, tracking,
refresh orchestration, and profile/leaderboard projections. S4B never writes
Mongo, calls Bbot, or calls back into `lol-service`.

R4B DTOs and `com.hive` are dependencies, not shared runtime state. S4B and
`lol-service` create separate Hive instances, registries, executors, queue
deduplication scopes, Redis clients, and service configuration. No future,
thread-local context, or job object crosses HTTP.

## Published package alpha

The package lives in the standalone `SafJNest/S4B` repository and is built
with Java 25. Beebot pins the JitPack commit and no longer carries local S4B
sources or the direct R4J dependency. The alpha contains the Riot gateway,
SDK conversion, S4B configuration/cache adapters and the Riot scheduler.
Its current consumers still submit to that scheduler through Spring's
`QueueHandler`, which owns the shared monolith Hive registry. `S4B_REDIS_URL`
configures the S4B-owned Jedis pool; `RIOT_API_KEY` configures Riot credentials.

This package alpha is not yet the target service process. It has no private
HTTP controllers or independent server/bootstrap, and `lol-service` still
imports the S4B implementation directly. Those are the next service-boundary
steps. Hive and R4B remain dependencies; their sources are not copied.

## Internal HTTP contract

S4B exposes the Riot-shaped paths needed by the application. Platform or
regional routing scope is encoded in the service host, and the path is derived
from the pinned R4J `URLEndpoint` constants. For example,
`euw1.api.s4b/<R4J route path>` or `europe.api.s4b/<R4J route path>`. Static
data has a fixed service host scope. The exact proxy contract and initial
endpoint families are in
[the Riot endpoint proxy specification](riot-endpoint-proxy-spec.md).

The allowlist covers account by PUUID/Riot ID, summoner, ranks by PUUID and
tier/page, masteries, spectator, match IDs, match detail/timeline, typed static
items/champions/spells, and the Data Dragon `runesReforged.json` and
`versions.json` resources. Those two static-resource bodies preserve Riot's
JSON shape without a generic envelope. S4B must use R4J endpoint constants as the path source;
it must not maintain copied URL templates. A typed binding may associate an
R4J constant with parameter rules and its existing R4B result type, without
repeating the route string. Validate path, query, method, body and shard scope
before queue admission. Do not expose arbitrary hosts or unrelated R4J API
families.

This typed binding is the single source of truth for both the server route
matcher and the type-safe client surface consumed by `lol-service`. Generate
an authenticated `GET /.well-known/s4b/routes` manifest directly from the
bindings on the private control-plane base configured by `S4B_CONTROL_BASE_URL`;
do not expose it through the public Riot route proxy. Include schema version,
stable operation ID, method, generated path
template and typed path parameters, host/rate-limit scope, query allowlist and
types, optional request body contract, response media type and canonical R4B
DTO (or raw static-resource shape). Do not hand-maintain route templates in
`lol-service`, and do not make the manifest/client artifact depend transitively
on R4J or S4B server internals. `lol-service` validates and caches the manifest
with version/ETag revalidation and calls operations only by stable ID.

Responses use the existing SDK-independent `com.R4B.api.dto` and
`com.R4B.lol` contracts. R4J SDK types and generic map/JSON envelopes do not
cross HTTP. Acquisition remains request/response: no remote job ID or polling
API is introduced.

Only `lol-service` may call S4B's Riot routes, over the private service network
with a configured service credential. S4B alone adds Riot credentials and
controls cache access. The host binding and credential transport are
deployment configuration, not fields in shared DTOs.

## Queue and request lifecycle

The independent S4B process starts and owns its Hive runtime and Riot
scheduler. Platform and regional hosts map to one `RegionShard` rate-limit
scope; application cooldowns apply to all Riot API work in that region,
method/service cooldowns gate their narrower scope, and Data Dragon is
scheduled separately. S4B owns queue names, logging, admission and rate-limit
behavior; Hive stays generic. `lol-service` does not submit Riot jobs to its
local Hive.

`lol-service` sends `X-S4B-Priority: IMMEDIATE|NORMAL|BACKGROUND`; S4B validates
it and maps it to its local queue. Spring derives the priority and overwrites
caller-provided values. Queue deduplication uses normalized endpoint identity
plus canonical arguments. Cache hits may return before queue submission.

A Riot `429` keeps the S4B job pending until Riot returns a non-429 result. S4B
reads `Retry-After` and `X-Rate-Limit-Type`, pauses the matching cooldown
bucket, and schedules the same logical job again without holding a queue
worker. The same HTTP exchange stays open while the job is pending; use
per-attempt upstream timeouts, not a total acquisition timeout. Queue admission
remains bounded and may reject saturation before the Riot request starts. Do
not introduce `202`, remote polling, or premature timeout responses for a
rate-limited request. After admission there is intentionally no application
wall-clock deadline for repeated 429s. Configure bounded total and per-region
pending counts; reject saturation with `503` and `LolApiError` before contacting
Riot. A downstream disconnect detaches that waiter without corrupting shared
deduplicated work.

R4J 2.8.1 internally retries both 429 and 5xx with one `maxSleep` policy and
does not expose the response headers needed by S4B. For supported proxy
operations, S4B owns a narrow JDK `HttpClient` transport adapter that derives
paths from the R4J `URLEndpoint` constants while typed bindings define method,
arguments, body and R4B result type. This lets S4B read upstream status and
headers and own 429 cooldown/requeue without using the opaque
`DataCallBuilder` or forking R4J. Keep 5xx retries finite by attempt count and
elapsed time; do not raise R4J's shared `maxSleep` as a workaround. Generate
the server matcher and the `lol-service` typed client/spec from the same
binding schema, with no R4J runtime dependency in the client artifact.

## Redis and cache ownership

S4B owns Riot acquisition cache, Riot cooldown and raw static-data cache.
The alpha owns a Jedis pool configured through `S4B_REDIS_URL`. The default
prefix preserves the current `beebot:lol:r4j:*` cache namespace; a deployment
may set `S4B_CACHE_PREFIX`. The pool is optional during this transition. The
Spring code still reads and writes some `r4j:*` keys directly; remove those
cross-owner accesses as part of the internal API cutover.

## Configuration and operations

Riot credentials and acquisition/cache policy belong to S4B. The alpha reads
`RIOT_API_KEY` with a monolith compatibility fallback to `rsc/settings.json`.
The HTTP service accepts `Authorization: Bearer <S4B_SERVICE_TOKEN>` over TLS;
the same configured secret is used by S4B and `lol-service`. An internal HTTP
server, readiness endpoint and independent lifecycle remain deployment
requirements for the next tranche.

## Implementation sequence and gates

1. The shared contract and read-only technical control are complete. Start the
   S4B and `lol-service` implementation agents in parallel against the same
   host/path/header/DTO contract.
2. S4B adds its independent HTTP/bootstrap lifecycle, JDK transport adapter,
   dynamically derived route matcher, typed binding schema, queue
   priority/cooldown and Redis ownership; it exports the client/spec generated
   from that schema.
3. `lol-service` adds the dynamic proxy, long-lived async response and typed
   clients against that contract, while removing direct S4B/R4J/cache access.
4. The control agent aligns both repositories during work and at handoff;
   report mismatches without silently changing either implementation. Beebot
   cleanup follows only after both service boundaries work.
