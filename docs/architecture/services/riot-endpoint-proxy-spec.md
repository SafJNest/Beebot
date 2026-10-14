# Riot endpoint proxy specification

Status: implementation contract after independent technical review.
Owner: service-boundary decision. Date: 2026-10-14.

## Goal and ownership

Expose the Riot routes needed by the current application with the same HTTP
method, route path, query parameters and typed R4B response contracts. S4B owns
the only R4J integration, Riot queue, rate-limit handling and raw Riot cache.
`lol-service` owns the public/domain API, tracker, Mongo and domain Redis. It
forwards Riot-shaped requests to S4B and does not depend on R4J or S4B
implementation classes. Beebot calls `lol-service` over HTTP and uses R4B
contracts.

```text
Beebot -> lol-service -> S4B -> R4J -> Riot / Data Dragon
                             |-> S4B queue and Redis
               |-> tracker, Mongo and domain Redis
```

This is an HTTP request/response proxy. It does not introduce a generic JSON
envelope, a remote job ID, polling, or shared Hive state.

## Addressing and route source

The Riot-shaped proxy uses the Riot route as the URI path. The host identifies
the routing scope:

```text
https://euw1.api.lol-service/<R4J route path>
https://euw1.api.s4b/<same R4J route path>
https://europe.api.lol-service/<regional R4J route path>
https://europe.api.s4b/<same regional R4J route path>
https://static.api.lol-service/<Data Dragon R4J route path>
https://static.api.s4b/<same Data Dragon R4J route path>
```

For example, a platform route keeps its R4J path after the platform host.
Regional endpoints use the corresponding `RegionShard` host. Static-data
routes use the fixed `static.api.*` host scope and retain their R4J path. The
deployed DNS names and TLS termination are deployment settings; they do not
change the route contract. `lol-service` maps this host to its configured
`S4B_STATIC_BASE_URL`; platform and region hosts are resolved from the
`LeagueShard`/`RegionShard` allowlists and configured S4B base URLs. No host
value from a request is concatenated into an arbitrary upstream URL.

`URLEndpoint` from the pinned R4J version is the sole source of Riot route
paths. S4B builds the runtime route matcher from those constants and allows
only the endpoint constants required by the application. It must not expose
unrelated R4J families by default. `lol-service` uses one catch-all proxy
mapping and forwards the incoming method, path, query and body; it does not
copy `URLEndpoint`, create a second path enum, or enumerate duplicate route
annotations.

The read-only control confirmed that the controller mapping alone is not the
complete route schema: R4J does not expose an introspectable HTTP method,
argument or body schema. S4B must keep a typed binding from each supported
`URLEndpoint` constant to its method, path arguments, allowed query arguments,
optional body and R4B response type. This is explicit operation binding, not
full route generation; it must refer to the R4J constant and must not repeat
the route string. Reject a route if R4J cannot safely build it from the
incoming request rather than forwarding an arbitrary URL.

## Required endpoint coverage

The initial allowlist covers the current `RiotGateway` uses:

| Route family | Required operation | R4B result |
|---|---|---|
| Account | by PUUID; by Riot ID | `RiotAccount` |
| Summoner | by PUUID | canonical `Summoner` |
| League | entries by PUUID; entries by queue/tier/page | list of `com.R4B.api.dto.riot.LeagueEntry` |
| Champion mastery | by PUUID | list of canonical `Mastery` |
| Spectator | current game by PUUID | `SpectatorGameInfo` |
| Match | IDs by PUUID with queue/pagination/time/type filters | list of match IDs |
| Match | detail by full match ID | `RiotMatchData` |
| Match | timeline by full match ID | `RiotTimelineData` |
| Static data | items, champions and summoner spells by version/locale as supported | keyed R4B maps |
| Data Dragon resource | `DDRAGON_PERKS` (`runesReforged.json`) | exact upstream JSON body; no envelope or reshaping |
| Data Dragon resource | `DDRAGON_VERSIONS` (`versions.json`) | exact upstream JSON body; no envelope or reshaping |

These are operation families, not a second path catalog. The exact allowlist
is represented with R4J endpoint constants. The two Data Dragon resources are
explicit static-resource exceptions to typed DTO conversion: preserve their
upstream JSON shape and media type, without wrapping them in a generic
envelope. `lol-service` parses `versions.json` into its existing patch
selection logic. S4B caches these raw resources; `lol-service` may keep its
own domain/presentation cache but never accesses S4B Redis. The implementation
agent must compare this list to all direct acquisition calls in `lol-service`
before considering coverage complete. New endpoint families require an
explicit change to this specification.

## Typed request and response boundary

S4B translates R4J SDK values into the existing SDK-independent DTOs in
`com.R4B.api.dto` and values in `com.R4B.lol`. The mapping binds each supported
R4J endpoint constant to its existing R4B response type. It does not return
R4J POJOs, `Map<String, Object>`, `JsonNode`, or an endpoint-generic response
envelope. If a needed R4B DTO is missing or does not preserve the required
fields, resolve that contract in R4B before implementing the route.

`lol-service` forwards a successful proxy body without reshaping it. Typed
domain client methods decode known routes into the same R4B DTOs. Their
type-safe client surface and the proxy route matcher must come from the same
S4B binding schema; generated client/spec artifacts may be used, but
`lol-service` must not handwrite a second route catalog or depend on R4J at
runtime. Existing Spring domain endpoints continue to return their current
canonical DTOs and preserve their current JSON and status behavior.

Invalid shard, unsupported route, missing/invalid route parameter and invalid
query values fail before queue admission. `LeagueShard`, `RegionShard` and
static-data scope are parsed as distinct types; `UNKNOWN` is not routable. The
host is resolved only against configured shard/region values and can never
select an arbitrary upstream host.

## Priority and queue lifecycle

`lol-service` sends `X-S4B-Priority: IMMEDIATE|NORMAL|BACKGROUND` on the
internal request to S4B. Spring derives it from its local operation policy and
overwrites any incoming header; callers cannot submit arbitrary Hive scheduler,
route or deduplication keys. S4B validates the enum and submits one typed local
job to its own Riot queue. Platform and regional hosts resolve to a Riot
`RegionShard` before admission; both hosts that map to the same Riot region
share rate-limit state. Riot application, method and service limits are scoped
per region, and the application limit is shared across Riot endpoints in that
region. S4B therefore coordinates these initial buckets:

* the application bucket covers every non-Data-Dragon request for the Riot
  API key and region;
* method and service cooldowns refine that region when Riot identifies their
  scope in response headers;
* if Riot reports a 429 without a usable limit type, pause the region
  conservatively;
* Data Dragon uses separate static/global scheduling and cache scopes.

A cooldown pauses only the matching bucket. A `RegionShard` application
cooldown pauses all Riot API requests for that region, while other regions and
Data Dragon remain runnable. A wait or saturated bucket must not block
admission or execution in a different bucket. Cache hits may return without
queue admission. Dedup keys use the normalized endpoint identity and canonical
arguments, not raw URL text. This follows Riot's documented application,
method and service limits and their per-region scope; the application limit
does not include static-data API calls ([Riot rate-limit documentation](https://developer.riotgames.com/docs/portal#rate-limiting)).

The S4B job completes only when Riot returns a non-429 terminal result. A 429
is not an HTTP completion for Spring: S4B reads `Retry-After` and
`X-Rate-Limit-Type`, pauses the matching bucket, then requeues the same logical
job for dispatch after the cooldown. If `Retry-After` is absent, use an
explicitly configured conservative fallback delay and retry the same logical
job; the fallback defaults to 1 second and grows exponentially up to 30
seconds. Keep doing so until Riot returns a non-429 result. Do not retry the same
429 in parallel in both R4J and S4B. Keep the queue worker free while waiting,
so queued priorities can be re-evaluated when the bucket resumes and unrelated
regions remain runnable.

`lol-service` keeps the HTTP exchange open while S4B's request is pending. Its
servlet thread should be released while waiting, but the response connection
must remain attached to the future. Configure an upstream per-attempt timeout,
not a total acquisition timeout that expires during a 429 cooldown. There is
intentionally no application wall-clock deadline after S4B admission: repeated
429 responses continue to wait/requeue until Riot returns a non-429 result.
Queue admission is bounded through configured total and per-region pending
limits; saturation returns `503` before Riot is called. Do not return `202`, a
job ID, or a rate-limit `429` merely because the request must wait. Spring,
S4B, clients and ingress must not impose a shorter total/idle timeout on these
routes; if deployment infrastructure enforces one, it cannot guarantee the
open-connection requirement and must be configured before rollout. Client
disconnect detaches that waiter without corrupting a deduplicated job shared
by other waiters. Service shutdown may terminate outstanding exchanges.

The technical review confirmed that the pinned R4J 2.8.1 public builder cannot
provide the required retry ownership: `getURL` and `getResponse` are private,
response status/headers are not publicly available, and its `sleepAndRetry`
path handles both 429 and 5xx responses. The implementation must not increase
the shared `maxSleep` to wait for 429s, since that also extends 5xx retries.
S4B therefore owns a narrow JDK `HttpClient` transport adapter for the
supported typed bindings. It derives upstream paths from the pinned R4J
`URLEndpoint` constants, builds requests from explicit method/argument/query/
body bindings, and captures status and headers for S4B's own cooldown/requeue
logic. R4J remains the route-constant source and SDK-independent DTO mapping
remains in S4B; its opaque `DataCallBuilder` is not used for these proxied
calls. This avoids an R4J fork and keeps 429 ownership in S4B. Retry 5xx only
with configured finite attempt and elapsed-time bounds.

The binding schema is the single source for S4B's route matcher and the
type-safe client contract consumed by `lol-service`. S4B exposes an
authenticated metadata operation at `GET /.well-known/s4b/routes` on the
internal control-plane base configured by `S4B_CONTROL_BASE_URL`; that route
is not a Riot shard host and is never exposed through the public proxy. It returns
a versioned manifest generated directly from the runtime bindings, not a
separately maintained document. Each operation declares a stable operation ID,
HTTP method, generated path template, typed path parameters, host/rate-limit
scope, query allowlist and parameter types, optional request body contract,
and response media type plus canonical R4B DTO (or the explicit raw static
resource shape). The manifest contains no upstream URL, Riot key or arbitrary
host. S4B and `lol-service` use the same configured bearer authentication for
this internal operation. `lol-service` validates and caches the manifest by
schema version (using an ETag or equivalent revalidation) and invokes only
stable operation IDs through typed domain client methods; it never accepts an
arbitrary URL from the manifest caller or public request. This narrow
manifest/client contract has no R4J runtime dependency and avoids a second
handwritten route catalog.

## Cache and invalidation

S4B owns all Riot raw-response, rate-limit/cooldown and static-data cache keys
in its Redis namespace. `lol-service` may keep domain and projection caches,
but cannot read, write or delete S4B keys directly. If a domain operation
needs to invalidate a raw Riot entry, add a narrow authenticated S4B
invalidation operation keyed by a typed endpoint and canonical identifiers;
do not expose Redis key strings or a generic delete API.

## Security and observability

Only `lol-service` may call S4B's Riot proxy. Bind the S4B listener to the
service network and authenticate service-to-service traffic with
`Authorization: Bearer <S4B_SERVICE_TOKEN>` over TLS; configure the same secret
as `S4B_SERVICE_TOKEN` in each process. Spring creates this header itself and
does not forward caller `Authorization`. Strip hop-by-hop headers, caller
credentials and all internal headers at the public boundary. S4B alone owns
and adds the Riot API key. Neither service returns credentials, raw Redis keys
or stack traces.

Every non-success wire response uses the shared `com.R4B.api.dto.LolApiError`
body and `application/json` media type; the HTTP status matches its `status`
field. Use `400` for invalid route/parameters, `401`/`403` for service
authentication, `404` for an unsupported route, `503` for pre-admission queue
saturation, and the upstream terminal status for a Riot failure. Error
messages are sanitized and never include upstream bodies, identifiers,
credentials or SDK details. A 429 is retried internally and never emitted as a
terminal proxy response.

Log a correlation ID, normalized endpoint identity, shard/region, priority,
queue wait, cache hit/miss, R4J retry/wait information when exposed, and
terminal status. Avoid logging PUUID, Riot ID, full path identifiers or API
keys unless redacted. Queue snapshots and service health remain owned by each
process.

## Independent implementation and control

Both implementation agents start from this same contract after the control
pass. The S4B owner implements the local transport and binding schema; the
`lol-service` owner can implement the path-agnostic proxy and lifecycle in
parallel, then consume the generated client contract:

1. The S4B implementation agent adds the independent server/bootstrap,
   route matcher/bindings, R4B conversion, priority admission, retry wait and
   Redis ownership.
2. The `lol-service` implementation agent adds the dynamic host/path proxy,
   internal S4B client, async response lifetime, typed route clients for its
   current callers, and removes direct S4B/R4J/cache access.
3. A separate control agent checks both implementations during development and
   against this document at handoff, including host mapping, R4J/R4B versions,
   path derivation from the binding schema, static-resource handling,
   status/errors, priority and rate-limit lifecycle.
   It reports mismatches; it does not silently edit either implementation.
4. Integrate only after both independent reviews agree on the same wire
   contract. Beebot cleanup follows after the two service boundaries work.

The definition/control phase is complete: path derivation is feasible from
R4J constants, while typed operation bindings remain explicit; Data Dragon's
raw static resources are documented; queue scopes follow Riot's region-aware
limits; and the S4B-owned transport adapter provides the response metadata
required for rate-limit handling without forking R4J. No missing R4B result
types remain for the selected contract.
