---
name: beebot-handbook
description: Beebot operations — how to create a new command, endpoint, service, model, queue, mongo, cache, aggregate. Use when the user says "new command/endpoint/service/model/queue/collection/query/cache/aggregate/filter/tracker/test" or modifies LoL API/persistence.
---

# Beebot Handbook Skill

> Operational excerpt from `docs/HANDBOOK.md` §5-§7. For architectural decisions see `AGENTS.md` + ADRs.

## CodeGraph pre-check (mandatory before touching code)

```bash
codegraph status          # verify index is up-to-date (Files:480, Nodes:11k, Edges:29k)
codegraph sync            # if stale or missing
codegraph explore <area>  # e.g. codegraph explore "ProfileService"
codegraph impact <symbol> # blast radius before editing
```

## Quick dispatch

| Request | Go to | Key |
|---|---|---|
| new Discord command | §5.1 | `rsc/commands.json` + `commands/<cat>/Mycommand.java` + `LeagueMessage` |
| new HTTP endpoint | §5.2 | `spring/controller/*` + `LolApiParameters` + `LolApiResponses.from(ApiResult)` |
| new service | §5.3 | `lol/service/MyService.java` constants→fields→public→//====→private |
| new canonical model | §5.4 | `lol/model/<scope>/` only here, `Rank`/`Mastery`/`riotId:String`, no duplicate DTO |
| new queue job | §5.5 | `QueueHandler.immediate/normal/background(RiotScheduler\|ComputeScheduler\|SyncScheduler, route, key, name, job->)` |
| new collection/entity | §5.6 | `AbstractEntity` + `MongoDB.java` + `COLLECTION_NAMES` + create-only index `profile_statistics_identity` |
| new query | §5.7 | `Filters.eq/and/in` + `Projections.include` + cursor batch, `explain IXSCAN` |
| new cache | §5.8 | `RedisKey` `los:...:%s` + `RedisClient.get/set/delete`, 60s + breaker 30s |
| new aggregate | §5.9 | `MyAnalyzer.Accumulator.accept(Match)` pure + `forEachProfileStatisticsMatch` + `upsert` |
| Filter change | §5.10 | `Filter.java` + symmetric `toSummonerKey/toStateKey/toKey/genericKey` + `buildMatchFilter` |
| periodic tracker | §5.11 | `TrackerScheduler` + `SyncScheduler` per shard |
| new test | §5.12 | `src/test/java/...` + `beebot_test` + `explain` gate |

## Mandatory checklist (HANDBOOK §7)

- [ ] API sync: controller + `lol.model` + `docs/api/<scope>/*.md` in the same task
- [ ] Doc sync: `HANDBOOK.md` + ADR + `docs/mongo` + `docs/audit` or explicit `no-doc-change`
- [ ] Presentation stability: no embed/view restyle unless requested
- [ ] Indexes & `explain("executionStats")` → `IXSCAN`, no `COLLSCAN`
- [ ] Cache invalidation: `RedisKey` + `RedisClient.set/delete` on `puuid+filterKey`
- [ ] Queue gate: new work via `QueueHandler`, never free `thenApplyAsync`
- [ ] Naming: no `*Document`, no `Optional`, no Lombok operational

## Index/weight/RAM reference (HANDBOOK §6)

- `summoner._id=puuid`, `match._id=fullGameId`, `profile_statistics {puuid,filterKey}` unique, `match_events` zstd
- `match_events` 15-40KB zstd, `profile_statistics` 2-8KB, Redis 1-4KB JSON
- `ChampionStatistics` heap <30MB batch 100, Compute max 2 worker

## Canonical files

`Filter.java`, `ProfileStatistics.java`, `ProfileService.java`+`ProfileAnalyzer.java`, `MongoDB.java`, `QueueHandler.java`+`ComputeScheduler.java`, `RedisKey.java`+`RedisClient.java`, `LolController.java`+`LolApiParameters.java`
