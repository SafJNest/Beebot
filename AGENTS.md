# Beebot agent instructions

## Repository context

This repository contains Shopify integrations, Java services, GraphQL integrations, Algolia, MySQL/MariaDB, Redis, Express and LoL API code.

Java code uses the `com.safjnest` package. LoL domain models belong under `com.safjnest.lol.model`; Spring owns controllers, configuration and HTTP errors only.

Primary stack:

- Shopify Liquid, CSS, vanilla JavaScript and Custom Elements;
- Java backend;
- GraphQL Storefront API;
- Algolia;
- MySQL/MariaDB and Redis with Jedis 5.1.0;
- Express and Next.js for LoL dashboards.

## JavaScript style

- Treat Custom Elements as Java-like classes with explicit structure.
- Use the member order: static constants, getters/setters, static factory, constructor, lifecycle callbacks, other methods.
- Do not use private `#` fields.
- Prefer inline conditionals with `&&` and `!condition &&` when they remain readable.
- Use `async`/`await`, never `.then()`.
- Use `try/catch` in `connectedCallback` when lifecycle work is asynchronous.
- Fetch with `res.text()` and `DOMParser` where the existing integration requires parsed HTML.
- Keep magic numbers in static class constants.
- Build `parts[]` with one `innerHTML` assignment instead of repeated DOM appends.
- Prefer CSS selectors and pseudo-classes for state; use JavaScript only for dynamic numeric CSS custom properties.

## Java style

- Use switch expressions with `->` and grouped cases.
- Use static utility classes for stateless utilities.
- Prefer static factories for conversions and model construction.
- Use `final` for immutable fields.
- Use `List.of` or `Arrays.asList` for static constants.
- Do not use Lombok in operational classes.
- Do not introduce dependency injection frameworks.
- Do not wrap values in `Optional`.
- Prefer a loop when it is clearer than a stream.
- Avoid unnecessary refactors and comments.

## Service layout

In every modified service:

1. constants;
2. fields and constructors;
3. public methods;
4. one large separator comment;
5. private methods.

The separator is the only structural comment required. Remove comments that do not explain a necessary invariant or non-obvious external behavior.

## Canonical LoL data

- `Summoner` is the canonical identity model.
- `Rank` is the canonical ranked-queue model. Do not introduce `SummonerRank` or keep it as an alias.
- `Mastery` is the canonical champion mastery model. Do not introduce `ChampionMastery` or keep it as an alias.
- `riotId` remains a `String`. Do not add a `RiotId` record or class unless an accepted ADR explicitly requires it.
- `SummonerView` is the complete profile projection.
- `SummonerLeaderboard` wraps one `SummonerView` with leaderboard position data.
- `Match` is the complete match model.
- `Participant` is the complete global participant model.
- `MatchResult` is the lightweight match projection used by lists and overviews.

Use existing utilities before adding new mapping logic, especially `ChampionUtils`, `LaneTypeUtils`, `GameQueueTypeUtils`, `LeagueShardUtils`, `TierDivisionUtils` and `LeagueHandler`.

## API boundaries

- Domain and API success models belong in `lol.model`.
- Spring DTOs are restricted to HTTP errors and infrastructure-specific representations.
- Do not create a second DTO for a concept already represented by a canonical model.
- Controllers should return canonical models directly unless an accepted ADR documents an HTTP-only wrapper.

## Agent workflow

Read these files before changing LoL architecture:

1. this file;
2. `docs/architecture/README.md`;
3. the relevant accepted ADR;
4. the relevant macro-task plan under `docs/agents/macro-tasks/`.

The source-of-truth agent proposes and maintains architecture decisions. Macro-task agents implement only their assigned scope. The main agent reviews ownership, boundaries, tests and the handoff before approving the next task.

Agents must stop and report a conflict instead of silently changing an ADR, another macro-task, or an unrelated owner.
