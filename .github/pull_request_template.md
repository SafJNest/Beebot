# PR Checklist — Beebot LoL

> Mandatory gate from `AGENTS.md` + `HANDBOOK.md` §7. Check everything before requesting review. If an item is not applicable, write `N/A — reason`.

## CodeGraph (mandatory before modifying `lol/*`)

- [ ] `codegraph status` → up-to-date (if stale `codegraph sync`)
- [ ] `codegraph explore <area>` executed for each touched area
- [ ] `codegraph impact <symbol>` for each modified symbol — blast radius verified

## Functional gate

- [ ] **API sync:** controller + `lol.model` + `docs/api/<scope>/*.md` + `docs/api/lol-api.md` updated in same PR. If internal-only: `N/A — endpoints unchanged, verified`
- [ ] **Stable presentation:** no restyle of embed/view/field order/text/layout unless explicitly requested
- [ ] **Queue gate:** new work goes through `QueueHandler.immediate/normal/background` (no free `thenApplyAsync`)
- [ ] **Naming gate:** `Rank`/`Mastery` only, `riotId:String`, no `*Document`, no `Optional`, no operational Lombok, service layout `//====`

## Persistence / Cache / Indexes

- [ ] **Mongo indexes:** `explain("executionStats")` → `IXSCAN`, no `COLLSCAN` (paste stage or `N/A — no new query`)
- [ ] **Invalidation:** `RedisKey` + `RedisClient.set/delete` consistent with `puuid+filterKey` (or `N/A — no cache`)
- [ ] **Backfill compat:** `_id` / `filterKey` / `profile_statistics_identity` unchanged or migration documented

## Docs

- [ ] `docs/HANDBOOK.md` §5-§7 updated (or `N/A — no new collection/endpoint`)
- [ ] `docs/architecture/README.md` / ADR / `docs/mongo/*` / `docs/audit/*` updated (or `N/A — internal-only`)

## Verification

- [ ] `git diff --check` clean
- [ ] Tests: `mvn test -Dtest=<Area>Test` or `N/A — reason`
- [ ] No secrets in commits (`rsc/settings.json`, URI, credentials)

## Handoff

- Modified files:
- Risks / follow-up:
- `N/A` motivated above? yes/no
