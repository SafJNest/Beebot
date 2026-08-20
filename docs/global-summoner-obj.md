# Global summoner obj

Handoff for Codex review. Describes the Discord LoL summoner cutover completed on 2026-08-19: canonical `Summoner` becomes the global identity object for Discord, `UserData`, and related consumers.

## Goal

Stop Discord LoL commands from resolving identity through r4j `no.stelar7.api.r4j.pojo.lol.summoner.Summoner` (always hitting Riot). Route through the bot Mongo/canonical model instead, with mechanical call-site swaps only (no embed/style rewrite).

Source intent: former [`docs/TODO.md`](TODO.md) (now marked done).

## Resulting identity model

Canonical class: [`com.safjnest.lol.model.summoner.Summoner`](../src/main/java/com/safjnest/lol/model/summoner/Summoner.java)

| Field | Type | Notes |
|-------|------|--------|
| `puuid` | `String` | Sole identity key (`_id` in Mongo) |
| `riotId` | `String` | e.g. `Name#TAG` |
| `region` | `LeagueShard` | BSON/JSON still `"EUW1"` via `name()` |
| `level` | `int` | |
| `icon` | `int` | |

**Removed:** `summonerId` from canonical `Summoner` and public Summoner JSON.

**Kept unchanged:** `Participant.summonerId` (match participant field); MariaDB `summoner_id` used only by migration.

## Accessor mapping (mechanical swap)

| r4j | canonical |
|-----|-----------|
| `getPUUID()` | `puuid()` |
| `getPlatform()` | `region()` |
| `getSummonerLevel()` | `level()` |
| `getProfileIconId()` | `icon()` |

## Flows

### Linked user / empty args (`!sum`, `/summoner` without summoner option)

1. `LeagueHandler.getSummonerFromDB(userId)` / `getSummonerByUserData`
2. Read `UserData.getRiotAccounts()` → first entry (Mongo `_id` ascending order)
3. Return canonical `Summoner` (no Riot identity fetch when cache/Mongo has it)
4. Same embeds + left/center/right buttons when multiple accounts

### Explicit Riot ID (`!sum Name#TAG`)

1. Channel guild shard
2. `SummonerService.getPuuidByRiotId(name, tag, shard)` (Mongo then Account API)
3. `SummonerService.get(puuid, shard)` (Redis → Mongo → Riot Summoner API + persist on miss)
4. Canonical `Summoner` into `LeagueMessage`

### Miss path

Account by name/tag → summoner by puuid → Mongo upsert → re-read via `find`/`get` → same Discord flow.

## UserData / UserCache

[`UserData`](../src/main/java/com/safjnest/model/UserData.java):

- Was: `LinkedHashMap<String, String>` (`puuid → region`)
- Now: `LinkedHashMap<String, Summoner>` (canonical)
- Load: `MongoDB.findAccountsByUserId` → `MongoDB.read(row, Summoner.class)`
- `addRiotAccount(Summoner)` / `deleteRiotAccount(puuid)` update Mongo then local map + `SummonerService.invalidate`

`UserData` is a JVM mirror only. Owner of identity remains `SummonerService` (ADR-0011).

## Resolution entry points

[`LeagueHandler`](../src/main/java/com/safjnest/lol/LeagueHandler.java):

- `getSummonerByArgs(CommandEvent|SlashCommandEvent)` → canonical `Summoner`
- `getSummonerFromDB` / `getSummonerByUserData` → first cached account
- Discord helpers (`getSoloQStats`, `getMastery`, embeds pic, etc.) take canonical `Summoner`
- `clearCache` / `clearSummonerCache` / Tracker-facing `getFormattedSummonerName` still accept **r4j** `Summoner` (FQN) where Tracker polls

[`SummonerService`](../src/main/java/com/safjnest/lol/service/SummonerService.java):

- Discord identity: `get` / `find` / `getPuuidByRiotId`
- `upsert(Summoner, userId)` overload for canonical link ownership
- `getRiotSummoner` retained for Tracker and for Riot match-list bridge only

## Consumers migrated to canonical `Summoner`

- Commands: profile, prefix summoner, overview, champion, link, unlink/track ownership checks, Opgg, Livegame
- [`LeagueMessage`](../src/main/java/com/safjnest/lol/message/LeagueMessage.java), [`LeagueEventHandler`](../src/main/java/com/safjnest/lol/message/LeagueEventHandler.java)
- [`EventModalInteractionHandler`](../src/main/java/com/safjnest/core/events/EventModalInteractionHandler.java) (champion modal refresh)
- [`MemberInfo`](../src/main/java/com/safjnest/commands/guild/MemberInfo.java), [`PrintCache`](../src/main/java/com/safjnest/commands/owner/PrintCache.java)

### Intentional r4j leftover on Discord surface

`LeagueMessage.getMatchIds` still calls `SummonerService.getRiotSummoner(puuid, region)` solely because `MatchService.getRecentIds` / match-list API require the r4j summoner object (`getLeagueGames()`). Identity into the command remains canonical.

## Mongo / persistence

[`MongoDB`](../src/main/java/com/safjnest/nosql/MongoDB.java):

- Stop projecting/writing `summonerId` / `setOnInsert(summonerId)`
- Hydrate `region` via `parseShard` → `LeagueShard`
- Persist `region` as `region.name()`
- Leaderboard page projection no longer includes `summonerId`

No automatic `$unset` of legacy BSON `summonerId` (manual cleanup policy).

## Tracker / DatabaseTracker

- Tracker live polling still uses `getRiotSummoner` (by design for this pass)
- Tracked accounts: `account.region()` is already `LeagueShard` (no `valueOf`)
- `lol.queue.DatabaseTracker` captures the profile PUUID, `LeagueShard`, filter
  snapshot and rebuild flag directly in the queued supplier; no parallel request
  carrier or legacy `summonerId` remains.

## Presentation

Unchanged: embeds, field order, button IDs/wiring, command option parsing. Data source only.

## Public API / JSON

Summoner identity JSON is five fields. Wire for `region` remains `"EUW1"`.

Updated:

- [`docs/architecture/adr/0005-lol-api-json-contract.md`](architecture/adr/0005-lol-api-json-contract.md)
- API examples under `docs/api/summoner/` and leaderboard (Summoner objects); **left** `Participant.summonerId` in match detail

## Documentation updated in the same task

- [`docs/TODO.md`](TODO.md) — done / current flow
- [`docs/audit/02-summoner-profile-flow.md`](audit/02-summoner-profile-flow.md)
- [`docs/audit/03-opgg-flow.md`](audit/03-opgg-flow.md)
- [`docs/audit/06-all-lol-commands-tracker.md`](audit/06-all-lol-commands-tracker.md)
- [`docs/mongo/01-db-structure.md`](mongo/01-db-structure.md), [`docs/mongo/08-query-inventory.md`](mongo/08-query-inventory.md)
- [`CHANGELOG.md`](../CHANGELOG.md)
- [`ROADMAP.md`](../ROADMAP.md) — Fase 4 account/ownership item checked

## Implementation style constraints (for reviewers)

- Mechanical type/accessor swap; do not introduce parallel Discord facades
- Do not redesign `LeagueMessage` or commands “while here”
- Do not treat `UserData` as a second owner of summoner identity
- Do not remove `getRiotSummoner` yet (Tracker + match-list bridge)
- Do not change `Participant.summonerId` or MariaDB migration keys

## Verification already run

- `mvn compile -DskipTests` — success
- `mvn -Dtest=LolApiConfigTest,MongoDBTest,AbstractEntityTest,ProfileServiceTest,LiveGameTest test` — success

## Suggested Codex review checklist

1. Discord identity path never returns r4j `Summoner` from `getSummonerByArgs*` / `UserData`
2. Linked-user path does not call Riot when Mongo/`UserData` already has the account
3. `region` is `LeagueShard` end-to-end on canonical `Summoner`; Mongo writes `.name()`
4. No `summonerId` on canonical `Summoner` / Summoner HTTP examples; Participant field untouched
5. Embeds/buttons structurally unchanged
6. Tracker still compiles against r4j `clearCache` / `getRiotSummoner`
7. Docs listed above match code

## Out of scope / follow-ups (not done)

- Migrating Tracker poll off `getRiotSummoner`
- `MatchService.getRecentIds(puuid, shard, …)` without r4j bridge
- Automatic Mongo `$unset` of legacy `summonerId` fields
- Renaming command class `commands.lol.summoner.Summoner` (name clash with model; local vars use FQN where needed)
