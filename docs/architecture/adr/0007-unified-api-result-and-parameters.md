# ADR-0007: Unified API result and parameter parsing

- Status: Accepted
- Owner: Main agent
- Date: 2026-07-14

## Context

Champion, match and leaderboard endpoints previously defined their own result
types, HTTP status switches and parameter parsers. The same Redis/DB lookup
logic was also exposed through multiple service methods with different names.

## Decision

`com.safjnest.lol.model.ApiResult<T>` is the only result type shared by LoL
endpoints. It has four states:

- `READY`: complete payload, HTTP 200;
- `PARTIAL`: usable partial payload, HTTP 200;
- `PENDING`: asynchronous work was started in the background, HTTP 202;
- `NOT_FOUND`: no resource, HTTP 404.

`LolApiResponses` is the only HTTP mapping owner for these states. Controllers
only parse request parameters, invoke a service and delegate the result.

`LolApiParameters` is the only HTTP parser owner. It handles optional rank and
region, the Solo/Duo queue default, required shards/text, page numbers and
lane compatibility. Missing region is represented by `null`; the service uses
its internal global aggregate when it queries storage.

Services expose one storage/compute flow. The public command method delegates
to the same overload with `allowCompute=true`; API orchestration uses the
overload with `allowCompute=false`. No public read/lazy aliases or automatic
build ranking selectors are introduced.

## Ownership and invariants

- `ApiResult` belongs to `lol.model`.
- HTTP status mapping belongs to `spring.controller`.
- HTTP parameter parsing belongs to `spring.controller`.
- Redis and DB are read once per component before an optional compute fallback.
- API requests never compute match aggregates.
- `ChampionStatistics.filter` stays in Redis/JSON storage but is ignored by the Spring
  Jackson mapper.
- `GLOBAL` is not a request value; omitted region is the public representation.

## Alternatives rejected

- Keeping one result class per endpoint would preserve duplicated status logic.
- Adding a second `read` or lazy method would duplicate Redis/DB access paths.
- Adding HTTP DTOs for `ChampionStatistics` would split ownership and risk
  divergence from the Redis/JSON model.

## Impact

`ChampionController`, `LolController` and `LeaderboardController` now use the
same parameter and response helpers. `ChampionPageService`, `LeagueService`
and `LeaderboardService` return `ApiResult` where asynchronous or partial
states are meaningful.

## Acceptance criteria

- No local endpoint result or status enum remains.
- No controller contains a duplicate READY/PARTIAL/PENDING/NOT_FOUND switch.
- No public automatic build ranking selector remains in the Champion/Build
  storage flow.
- Missing Champion data returns 202 and starts work immediately.
- Missing leaderboard overview returns 202 while the missing profile statistics are generated.
- Missing profile statistics return the available profile as 200 `PARTIAL` while generation runs immediately.
- `ChampionStatistics.filter` is absent from HTTP JSON and remains available to
  Redis/JSON.
