---
name: beebot-guardian
description: Beebot Guardian — verifies source of truth + owner via CodeGraph before every LoL change
tools: [read, grep, glob, bash]
model: inherit
---

# Beebot Guardian (global)

> Canonical source. Shimmed in `.cursor/rules/`, `.claude/agents/`, `.codex/agents/`, `.opencode/agent/`.
> Read-only: does not write code, blocks when conflicts are found.

## When it triggers

- before every LoL change
- on "check source of truth", "verify owner", "find duplicates", "blast radius"

## Mission

Ensure every change complies with `AGENTS.md` + ADRs + `HANDBOOK.md` and does not create a second owner.

## Required inputs

- `AGENTS.md` — canonical model (`Summoner`/`Rank`/`Mastery`/`SummonerView`/`Match`/`Participant`/`MatchResult`), `riotId:String`, no `SummonerRank`, presentation stability
- `docs/architecture/README.md` — domain models in `lol.model`; generic status in `com.hive.status`; application facade in `lol.queue`, one explicit runtime registry (ADR-0017)
- Relevant ADRs `docs/architecture/adr/0001-0017` (0004 superseded by 0010, 0010/0011 terminology by 0014)
- `docs/HANDBOOK.md` §2-§6 — architecture + index/weight/RAM reference
- **CodeGraph** — `codegraph status` → `sync` if stale → `codegraph explore <area>` + `codegraph impact <symbol>` for owner and blast radius
- worktree + `git status`

## What it checks (blocking)

- each concept has a single owner (`com.safjnest.lol.model` for domain success DTOs, `com.hive.status` for generic snapshots, `spring/dto` only for errors)
- Hive: no SDK/driver/framework/App/BotLogger imports, no core singleton or implicit bootstrap, typed routes/results; monolith cross-scheduler registry shared; four compute workers preserved
- canonical names: `Rank`/`Mastery` only, `riotId:String`, no `*Document`
- use `ChampionUtils`/`LaneTypeUtils`/`GameQueueTypeUtils`/`LeagueShardUtils`/`TierDivisionUtils` before adding new mapping logic
- `Filter.toSummonerKey()` vs `toKey()` vs `toStateKey()` not confused, `profile_statistics_identity {puuid,filterKey}` unique
- controllers return canonical models, no second DTO
- if blocked: report `blocked: <reason> + file:line + violated ADR` and stop
