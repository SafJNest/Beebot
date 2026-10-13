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

Use a versioned private base path `/internal/v1/riot`. The API exposes
acquisition operations, not Spring domain endpoints or SDK classes. All
requests validate `LeagueShard` and `RegionShard` separately; `UNKNOWN` is not
a valid Riot route. The initial read API is:

| Operation | Inputs | Result contract |
|---|---|---|
| account by PUUID | region, PUUID | `RiotAccount` |
| account by Riot ID | region, game name, tag line | `RiotAccount` |
| summoner | shard, PUUID | canonical `Summoner` |
| ranks | shard, PUUID | list of canonical `Rank` |
| ranks by tier | shard, queue, tier, page | list of `Rank` |
| masteries | shard, PUUID | list of canonical `Mastery` |
| current spectator game | shard, PUUID | `SpectatorGameInfo` or not found |
| match IDs | shard, PUUID, queue, index, count, start time, type | list of match IDs |
| match detail | region, match ID | `RiotMatchData` |
| match timeline | region, match ID | `RiotTimelineData` |
| static items/champions/spells | requested static-data version where applicable | keyed R4B maps |

Wire types come from `com.R4B.api.dto` and `com.R4B.lol`. SDK POJOs,
`SummonerView`, Mongo entities and application-specific aggregates never appear
in this API. Preserve compatible JSON field names and enum representations;
add golden JSON fixtures before changing an existing contract. The API does
not expose queue priority or arbitrary scheduler/key controls to callers.

Use ordinary request/response HTTP for acquisition: S4B enqueues locally,
waits for the job result, then returns it. The first version has no remote job
ID, polling endpoint, or distributed deduplication. Bulk or long-running
maintenance operations are separate contracts and are not disguised as a
single synchronous acquisition request.

Use existing `LolApiError` JSON with meaningful HTTP statuses: 400 for invalid
input, 404 for Riot data not found, 429 for admission/rate-limit rejection,
502/503 for upstream or S4B unavailability, and 504 for a bounded timeout.
Do not return HTTP 200 with `null` for operational failures. Preserve the
distinction between an absent Riot resource and an unsuccessful Riot request.

Only Spring may call the internal API. Bind it to the private service
network and require a configured service credential; do not expose Riot keys
or Redis credentials through the API. The concrete credential transport and
deployment binding are configuration decisions for the S4B bootstrap, not
fields in shared DTOs.

## Queue and request lifecycle

The S4B-local `RiotScheduler` is registered in the current monolith Hive
runtime by `QueueHandler`. It has one serial physical queue per
`LeagueShard`, preserving current route validation and SDK platform/region
mapping. It owns S4B worker names, logging and admission behavior. Redis
cooldowns/rate protection remain in S4B's acquisition path; Hive stays generic.

At the package stage, existing `lol` services submit typed jobs through
`QueueHandler` to the S4B scheduler. At the HTTP stage, S4B controllers call
acquisition services that submit typed jobs through the service facade. The
deduplication identity is scheduler + logical
shard + stable operation key, for example `match:<region>:<matchId>`. Cache
hits may return before queue submission. A caller timeout does not claim that
a running Riot request was physically cancelled. HTTP and queue wait limits
must be bounded and coordinated; saturation is reported explicitly rather
than creating an unbounded remote backlog. Do not add retries that can multiply
Riot traffic; retry policy must preserve the current SDK/rate-protection
behavior.

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
An internal HTTP server, service credential, readiness endpoint and independent
lifecycle remain deployment requirements for the next tranche.

## Implementation sequence and gates

1. Keep only Riot SDK runtime, gateway, conversion, Riot scheduling and raw
   Riot cache policy under `com.safjnest.s4b`; S4B does not own tracking, Mongo,
   public API, domain calculations or projections.
2. Spring owns domain callers and queue/runtime composition under
   `com.safjnest.spring`; this monolith stage preserves current in-process
   behavior.
3. Add the internal S4B endpoints and typed Spring client, then migrate Spring
   callers from direct S4B imports to HTTP.
4. Add the typed Spring client, then remove all direct Spring access to
   `RiotGateway`, `RiotScheduler` and S4B-owned Redis keys.
5. Give the independent S4B process its own Hive lifecycle and HTTP bootstrap;
   verify both service builds before deployment.
