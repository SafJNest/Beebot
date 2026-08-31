# Agent workflow (current)

> Canonical in `.agents/agents/` — this file is only a historical index. To add new content see `docs/HANDBOOK.md`.

## Global agents (2) — use

| Agent | Location | When to use |
|---|---|---|
| `beebot-guardian` | `.agents/agents/guardian.md` → shimmed `.cursor/.claude/.codex/.opencode` | before every LoL change: `AGENTS.md` + ADR + `HANDBOOK.md` + `codegraph explore/impact` for owner/blast radius, blocks on second owner |
| `beebot-builder` | `.agents/agents/builder.md` + skill `beebot-handbook` | "new command/endpoint/service/model/queue/mongo/cache" → `codegraph` mandatory → `HANDBOOK.md` §5 template |

Skill: `beebot-handbook` (`.agents/skills/beebot-handbook/SKILL.md`) — dispatch §5.1-5.12 + checklist §7 + reference §6.

## Reading order (for both)

1. `AGENTS.md`
2. `docs/architecture/README.md`
3. Relevant ADRs
4. `docs/HANDBOOK.md` §5-§7
5. `codegraph status` → `codegraph explore <area>` + `codegraph impact <symbol>`

## Archive

- Macro-task `0000-0008` → `docs/agents/_archive/` (0000/0001/0003/0004/0007 `Implemented`, 0002/0005/0006/0008 implemented, see `HANDBOOK.md` §6)
- Proposal `rusted-java.md` + `champion-matchup-event-payload.md` → `docs/proposals/`
- Template → `docs/agents/macro-task-template.md` (kept for new plans, but use `HANDBOOK.md` for operational)

Historical detail of Mongo/guardian/verifier workflow: `docs/mongo/07-agent-strategy.md` (not active).
