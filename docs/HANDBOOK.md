# Beebot developer handbook

Operational guide for changes to commands, LoL services, persistence and jobs.
Repository conventions are in [`AGENTS.md`](../AGENTS.md); accepted architecture
decisions are in [`architecture/adr`](architecture/README.md).

## 1. Rules and precedence

When documents conflict, follow `AGENTS.md`, then accepted ADRs, then this
handbook. Treat code as evidence of current implementation, not as an
architecture decision. Report conflicts rather than silently changing owners.

For LoL code changes, check CodeGraph status, sync a stale index, then inspect
the affected symbol and its impact before editing.

## 2. Core contracts

- Canonical LoL success models live in `lol.model`; Spring owns controllers,
  configuration and HTTP errors.
- `Summoner`, `Rank`, `Mastery`, `Match` and `Participant` are canonical. Reuse
  existing mapping utilities. Match pages contain complete `Match` values with
  bans and all participants; only the detail loads timeline `events`.
  `MatchResult` remains an internal statistics compatibility input.
- Mongo owns all LoL persistence, including the rebuildable `match_facets`
  projection maintained after match insert/upsert and rebuilt by
  `!test export-match-search`. LoL has no MariaDB runtime or historical
  migration path.
- Redis stores caches and derived indexes. It is not a durable queue.
- Changes to data or persistence preserve existing command and API presentation
  unless a presentation change is requested.

## 3. Ownership map

| Responsibility | Owner |
|---|---|
| Commands and registration | `commands/`, `rsc/commands.json` |
| HTTP routes and validation | `spring/controller/`, `LolApiParameters` |
| Domain models and behavior | `lol/model/`, `lol/service/` |
| Job lifecycle and routing | `lol/queue/QueueHandler`, schedulers |
| Periodic tracking | `lol/tracker/` |
| Mongo persistence and backfill | `nosql/MongoDB`, `MongoMigration` |
| Match facets projection writes and export | `MongoDB`, `!test export-match-search` |
| Match facets API reads | `LolController.facets`, `FacetsService.get`, `MongoDB.searchFacets` |
| Redis keys and invalidation | `redis/RedisKey`, `CacheInvalidationService` |

## 4. Shared data flow

Profile aggregates use `{ puuid, filterKey }`, where `filterKey` comes from the
complete `Filter.toSummonerKey()`. `Filter.toKey()` identifies champion/build
data and is not a substitute. Keep the filter consistent across Mongo, Redis
and job deduplication. See the [profile source of truth](architecture/profile-statistics-source-of-truth.md).

`ApiResult` maps `READY` and `PARTIAL` to `200`, `PENDING` to `202` and
`NOT_FOUND` to `404`. Use the existing response mapper and canonical models.
Cache misses enqueue work through `QueueHandler`; persistence success precedes
cache updates and invalidation.

Profile GETs return the available summoner view as `PARTIAL` with
`metadata.refresh=true` while missing rank, mastery or statistics work is queued.
They return `202` only while Riot ID resolution or the base summoner profile is
still in progress. When Riot ID resolution completes, base profile loading is
queued without requiring another request; the request does not wait for component
or aggregate work.

Match insertion and missing rank-snapshot completion use stored ranks only.
Missing local rank data leaves the snapshot unknown and must not trigger a Riot
rank request. Rank refreshes belong to explicit refresh/profile flows and the tracker.
This rule does not change public payloads or Discord presentation.

## 5. Operational steps

### 5.1 New Discord command

Register the command in `rsc/commands.json` and its `SlashCommand` class. Reuse
existing identity lookup, message types and rendering. Keep computation in the
owning service and preserve the existing presentation.

### 5.2 New HTTP endpoint

Discord dashboard authentication and its `/beebot` endpoints are documented in
[Beebot dashboard authentication](api/beebot-auth.md). `AuthDB` selects the
`auth` database and stores OAuth transactions and sessions. Redis is an optional
cache; protected requests accept the application API key plus user bearer token,
or a permanent personal API key linked to the issuing Discord user. Generate it
with `!test generate personalkey <name>` after manually applying
`database/auth/003-api-key-types.sql`; revoke it with `!test revoke personalkey <name>`.
Both key types live in `api_key`: `ApiKey` is the canonical typed key model and `AuthDB` owns persistence, generation and revocation. Authentication resolves either credential type into `DiscordAuthService.AuthContext`. Key generation and revocation are restricted to Untouchables and Epria; other `test` operations remain owner/co-owner only.

Keep authentication SQL in `AuthDB`. Key and session reads use
`QueryRecordParser.fromRow()` and map named columns to `ApiKey` and `AuthDB.Session`.
Full session reads and `SELECT 1` cache checks share the same validity conditions.

`GET /beebot/me` returns `{user, servers, greets}` from `MeController`; `POST /beebot/me/settings` belongs to the same controller. `GET /beebot/guilds` returns a plain `List<DiscordApi.Server>` (JSON array), with `/beebot/servers` retained as an alias. Records are nested in their owning API, service or controller rather than split into record-only files. `DiscordApi` owns Discord HTTP/JDA access and image URLs; `DiscordAuthService` shares the authorized list between these endpoints and single-guild GET/POST. `DiscordGuildCache` stores it for 60 seconds in Redis with process-local fallback, single-flight loading and invalidation fencing. Personal keys use JDA caches before any missing-member lookup; OAuth uses one `/users/@me/guilds?limit=200` request per successful cache load. SQL continues to decide credential/session revocation. Logout invalidates the guild list. Owner, Administrator and Manage Server grant access. Discord rate limits return `discord_rate_limited` with retry metadata; see the API page for error details.

`GuildController` owns `/guilds` and `/servers`; `DiscordAuthController` owns
only authorize, session and logout. `MeController` retains its profile routes.
Application failures use `com.safjnest.exception.Exception` with a nested category,
code, caller message and optional cause/retry. Keep Spring types out of auth,
models and storage failures. When importing that class, qualify generic Java
catches/signatures as `java.lang.Exception`. `ApiErrors` maps HTTP status, headers
and the existing Beebot/LoL envelopes; `ApiExceptionHandler` is the single advice,
and `AuthFilter` uses the same mapper, including `Retry-After` and retry seconds.
Routing/framework errors select their envelope by request scope. `LolApiResponses`
still owns `ApiResult` state mapping and uses `ApiErrors.LolApiError` for 202.

Guild dashboard reads and partial updates are documented in
[Beebot guild settings](api/beebot-guilds.md). Bot and API use the same
`GuildCache` objects. Under `GuildCache.withGuild`, call `deferEdit()` on the
specific model, apply setters and call `update()`. The private edit saves all
fields of that block through prepared `BotDB` queries in one transaction, then
publishes to the original object only after commit. Normal setters remain
immediately persistent. Failed or stale edits return `false`; create a new edit
for another update. General settings, blacklist, each channel and each alert are
separate units. `GuildData.deferEdit()` covers general settings only. Alert roles
and Twitch/reward fields share their alert's transaction. Response records and
copied settings snapshots live in `GuildData`; operational problems come from
`AlertData`. Own writes keep the existing cache object. Twitch registration runs
after commit and outside the guild lock. Warning actions are read-only here.

Use the existing scope controller where possible. Parse every path and query
parameter with `LolApiParameters`, call the owning service and return canonical
models through `LolApiResponses`. Update the endpoint page and API index when
the public contract changes.

User aliases and greets use `UserCache.withUser` and the persistent `UserData`
methods. Each greet scope is an independent transaction; API writes retain the
same object. `getAllGreets()` completes lazy settings loading and returns a copy.
Sound metadata writes use `SoundCache.withSound`, `deferEdit()`, setters and
`update()`; the API passes the authenticated actor to the update. `Sound` owns
votes, play increments and deletion; derived selection invalidation follows the
commit. SQL lives in `BotDB`, including the former `SoundDB` streaming/read-session
operations. `Sound` reads personal plays and votes through
`BotDB.readSoundUserPlays` and `readSoundInteraction`, never SQL literals. A
metadata refresh invalidates pending edits only when name, visibility or the tag
set changed; tag order, empty slots and play/vote counters do not count. Successful
metadata saves still consume the draft. `Tag` is immutable: an empty name is an
unused slot, ID zero with a name is new, and a positive ID with a name is stored.
Keep the five Discord slots and omit only empty slots from JSON; internal state
predicates are not API fields. These changes preserve HTTP and presentation.
For XP use `GuildCache.receiveExperience`: `MemberData` coordinates
cooldown, calculation, counts and persistence; inspect its saved/leveled-up result
before sending rewards. Keep Discord calls outside the coordinated operation.

### 5.3 New service

Place domain behavior in `lol/service`. Order members as constants, fields and
constructors, public methods, one separator, then private methods. Keep cache,
Mongo and scheduling in the existing service owner; put pure calculations in an
Analyzer.

### 5.4 New model

Place LoL success models in `lol.model`. Extend a canonical concept instead of
adding a duplicate DTO or persistence-only alias. Keep Spring DTOs for HTTP
errors and infrastructure representations.

### 5.5 New queued job

Submit through `QueueHandler` to `RiotScheduler`, `ComputeScheduler` or
`SyncScheduler`, according to the work. Use a stable logical key for
deduplication. Report phase and item progress through the job; do not add a
parallel queue or retry store.

### 5.6 New Mongo collection or entity

Use `AbstractEntity` and `NoSqlEntityExecutor` where applicable. Register the
collection in `MongoDB`; manage indexes according to the owning ADR and
`docs/mongo` inventory. Define identity and migration behavior before adding a
persisted shape. Do not add duplicate
`*Document` models or dual-write paths.

### 5.7 New Mongo query

Add the query to `MongoDB`, use typed filters and narrow projections, and stream
large result sets with a cursor. Add or reuse an index, inspect
`explain("executionStats")`, and update `docs/mongo/08-query-inventory.md`.
For match queries, reuse the participant/scope builders: PUUID and known
region first, optional equality filters next, time bounds last. Single
participant predicates use dotted paths; multiple conditions on the same
participant use one `$elemMatch`. Preserve deliberate global and ID-only
queries. The replacement catalog and operational rollout are in
`docs/mongo/14-match-index-consolidation.md`.
When an end bound is present, the shared time builder limits both `timeStart`
and `timeEnd` to it. This bounds the chronological index scan while preserving
the completed-match containment rule. Player count indexes put champion/lane
before time; keep the existing chronological index for unfiltered pages and
compare space and actual plans before dropping any index.
For the summoner leaderboard locator, measure the count-before scan on
representative Gold/Emerald data: an indexed count can still inspect many keys.

### 5.8 New Redis cache

Declare the key and TTL in `RedisKey`. Use `RedisClient` and invalidate through
`CacheInvalidationService` after the durable write succeeds. Keep cache values
rebuildable from Mongo. Expiring data caches temporarily use a one-minute TTL;
permanent keys, operational locks and the refresh cooldown retain their policies.
Existing Redis entries retain their original expiration until rewritten or expired.
The public API shapes and presentation are unchanged. Record reads use Mongo ordering by `value`; their only
Redis cache is the paginated total, backed by durable `aggregates`, with a 60-second TTL and invalidation of both stores on row-count changes.

### 5.9 New aggregate or analyzer

Put pure accumulation and finalization in an Analyzer. Let the owning service
read bounded Mongo batches, persist the completed aggregate, then update Redis.
Define the aggregate identity and include every dataset-changing filter in its
key. Indicative counts and distributions use `AggregateService`: Redis ->
`aggregates` -> calculate and persist before caching. Mongo snapshots do not
expire. `!test regenerate aggregates [type]` queues separate background MONGO
jobs with progress; see [aggregate snapshots](mongo/16-aggregates.md).

### 5.10 Filter change

Update all affected serialization methods, Mongo predicates, post-read matching,
cache keys and job keys together. A field that changes the dataset must be part
of `toSummonerKey()` or the relevant aggregate key. Review stored data and
indexes for migration needs.

### 5.11 Tracker or periodic job

Schedule through `TrackerScheduler` and dispatch work through `QueueHandler`.
Respect shard routing and existing refresh ownership. Report `total`,
`completed` and `missing` through job progress when processing collections of
items. With tracking enabled, the champion refresh runs every evening at 23:00
in the server's timezone. `!test regenerate champion [patch]` (`champions` is also
accepted) starts the same deduplicated background `CHAMPION` job manually,
including when tracking is disabled. The default is the current patch;
`!test regenerate champion 16.20` targets only 16.20. It first queries distinct
queues in that patch's stored matches, retaining the champion build queues
(Solo/Duo, including its enum alias, Flex, Draft, ARAM and Arena). It forces all
statistics matrices for those queues, then rebuilds the discovered/stored build
filters for the same patch and queues. Statistics are replaced in place without deleting Mongo data; statistics,
page and tier-list Redis caches are invalidated after persistence. Job progress
shows `STATISTICS`/`BUILDS`, the current scope/filter and completed, missing or
failed items. A statistics failure stops the build phase; build failures are
reported per filter and remaining builds continue. Console logs use the compact
`[refresh build]:` prefix: statistics queue index/total with its name, batch
index/total, then build champion ID, queue index/total and filter index/total.
Detailed source/query/timing/scope summaries are removed; failures remain logged.
Only console logging changes here: queries, persistence, job progress, public API contracts and
Discord presentation remain unchanged; the queue discovery uses the existing
patch-prefix match index and adds no indexes.
Rank-entry scans use one LPTracker job per region and process that
region's ranks and pages sequentially; separate regions can run concurrently.
The owner all-rank scan pauses before each page and entry while summoner
tracking is active, including its descendant jobs. Its phase is
`WAITING_FOR_TRACKER` during the pause; high-elo scans are unaffected.
An empty page ends the current rank. All-rank progress is checkpointed per
region in Mongo `migration_runs` as `rank-entries:<region>` with the current
tier, next page and status. A page advances only after all its entries persist;
an interrupted or failed page is retried from its beginning. Three consecutive
page-fetch failures retry the same page and then pause that region with its checkpoint intact.
The scan excludes nonexistent division V; an existing V checkpoint resumes at
the corresponding division IV. A first run
without checkpoint documents is initialized from the latest operator-provided
regional progress snapshot; a later run starts from the beginning after every
region has completed. The latest operator-provided snapshot seeds BR1 at Silver III
page 37, EUN1 at Silver IV page 178, EUW1 at Bronze IV page 658, KR at Bronze I
page 36, LA1 at Silver I page 96 and NA1 at Bronze I page 92. These pages
are repeated. LA2, TR1, VN2 and previously completed regions stay completed.
The seeds apply only when no checkpoint documents exist and do not overwrite
existing Mongo checkpoints. This internal change leaves the public API and presentation unchanged. All-rank
jobs keep only the current page in queue status, not a growing item map for
every PUUID. High-elo single-page requests move to the next rank after one
failure.
When `RankService.saveEntry` imports a summoner missing from local storage, it
waits 350 ms before fetching the Riot identity. Existing summoners have no added
delay.

### 5.12 Tests and validation

Use unit tests for pure analyzers and focused integration tests for persistence
or HTTP behavior. Report exactly which checks ran; an offline test is not proof
of live Mongo, Redis, Riot or Discord behavior.

## 6. Quick reference

| Data | Owner | Storage |
|---|---|---|
| Summoner, ranks, masteries | `SummonerService`, `RankService`, `MasteryService` | `summoner` |
| Matches and events | `MatchService`, `Tracker` | `match`, `match_events` |
| Match facets projection and APIs | `MongoDB.insertFacets`, `FacetsService.get` / `count`, `!test export-match-search` | Mongo `match_facets`, Redis 60s cache; included in `/matches` and `/facets` |
| Profile statistics, activity, matchups | `ProfileService`, `ProfileAnalyzer` | `profile_statistics`, `profile_activity`, `profile_matchups` |
| Champion statistics and builds | `ChampionService`, `ChampionAnalyzer` | `champion_statistics`, `champion_builds` |
| Competitive ranks and leaderboard indexes | `CompetitiveService`, `LeaderboardService` | `competitive`, Redis |

`competitive.score` is the ranked queue's `wins - losses` balance. It breaks
equal-MMR leaderboard ties and is refreshed with the competitive projection;
existing rows require `!test regenerate competitive` after rollout.

The unique profile identity index is `profile_statistics_identity` on
`{ puuid, filterKey }`. Match `_id` is the complete Riot match ID; summoner
`_id` is the PUUID. Query and migration details are in [`docs/mongo`](mongo/README.md).

## 7. Completion checklist

- Review CodeGraph impact before LoL code changes and resolve ADR conflicts.
- Review affected API endpoints; update API docs only when their contract changes.
- Update the owning architecture or Mongo source-of-truth document when behavior
  or persistence changes.
- Preserve presentation for data-only changes.
- Report focused checks separately from live integration evidence.

## Arena builds

CHERRY uses the existing champion API and build flow. Its approved schema and
processing rules live in [`docs/arena-build`](arena-build/contracts.md) and
ADR-0012.

## Local bot caches

`bot.cache.CacheAdapter` owns the instance-local Caffeine backing and the shared
single/bulk loading protocol for guilds, users and sounds. `GenericCache` reuses
this base. Use `GuildCache.withGuild`, `UserCache.withUser` or
`SoundCache.withSound` for migrated mutable operations; callbacks retain the
entry and synchronize on the object. Plural `withGuilds`/`withUsers`/`withSounds`
retain every entry but require the individual owner's lock for mutations.
Keep Discord calls and response serialization outside the object's lock.

Bulk loads query only missing IDs and reuse in-flight loads. Guilds hydrate
channels/actions in batches; users hydrate aliases. The new SQL reads use
prepared statements and `QueryRecord`, with at most 500 IDs per statement.
Invalidation fences publication by an older load; active participants finish
before that entry is discarded. Cache map/list views do not expose the backing
for direct writes. Old raw object getters remain compatible but do not retain an
entry beyond the getter call. Guild settings, blacklist, channels and alerts now
use scoped operations and copied snapshots (plan step 2). Do not mutate maps
returned by `GuildData`; use its creation/deletion methods. User/greet and sound
writes now use those same scoped objects (step 3). Member XP uses the retained
guild entry and a coordinated operation on the member; authentication caches
retain their separate policies.
See the [implementation plan](architecture/repository-review.md).

## Audio API operations

Sounds HTTP routes use `SoundCache` for object loading, typed list responses and ID-only Redis selections, `SoundController` and `MeController` for HTTP parsing, `Sound`/`UserData` for shared writes, canonical `Sound` response copies, and `BotDB` prepared SQL (streamed history grouped in Java on one read session). They use ordinary membership rather than the administrative guild directory. `SoundFiles` coordinates local audio files and uses `R2Storage` for the CDN and object uploads. See [the audio API contract](api/beebot-sounds.md) and [R2 deployment settings](audio-storage-r2.md).


## Sound statistics

`com.safjnest.sound.SoundAnalytics` owns `getSummary`, `getGuildAnalytics`,
`getSoundAnalytics` and `getUserAnalytics`. `SoundCache` reuses these methods.
`SoundHistory` is a package-private in-memory accumulator: it indexes typed counts
by sound/guild and keeps only the newest 101 personal events per sound. Keep SQL
streaming in `BotDB`, cursor encoding in `SoundPeriod` and source resolution in
`AudioType.getFromOrdinal`. Response builders preserve the documented JSON and
privacy rules; add characterization checks before changing their behavior.


## Shared HTTP and parsing

`HttpUtils` owns reusable transports, GET/form request construction and sending.
`HttpUtils.Transport` returns the complete Java `HttpResponse<String>`; provider
code interprets status, headers and body. Sending restores an interrupted thread's
flag before rethrowing. Data Dragon/rune and Spotify facades retain their current
5-second connect / 10-second request timeouts and header-aware text decoding.
`DiscordApi` retains its 15-second timeouts, explicit UTF-8 and Discord error/retry
policy, using the same construction/sending code. Keep OAuth fields ordered.

`utils.ParseUtils` owns the numeric/enum conversions previously shared through
`lol.utils.NumberUtils` and `BuildUtils.parseAnyInt`. Use `parseIntExactOrNull`
where whitespace must remain invalid, `parseIntOrNull` where trim is allowed,
and the tolerant conversions only where fallback is part of the existing data
contract. `longValue` accepts only Number. `FilterCodec` owns wildcard/key meaning;
`LolApiParameters` owns HTTP validation and messages. Arena integrity/range rules
and QueryRecord SQL parsing keep their existing owners. These are internal changes:
HTTP contracts, persisted shapes and Discord presentation remain unchanged.
