# ADR-0018: R4B — Riot 4 Beebot

- Status: Accepted
- Owner: Main agent
- Date: 2026-10-10
- Amended: 2026-10-13 — S4B owns Riot SDK acquisition and raw Riot cache policy under `com.safjnest.s4b`
- Scope: shared data contracts, LoL value types/helpers and migration of local consumers
- Amends: model placement in ADR-0001/0002/0003/0005/0006/0007 and the shared-contract tranche of ADR-0016

## Decision

The user authorizes this extraction independently of the remaining service
separation proposal. The exact roots are `com.R4B.api.dto` for request/response
data, errors, metadata and serialized components, and `com.R4B.lol` for LoL
enums, constants and service-independent helpers. Only `R4B` remains uppercase. The user-selected `api.dto` and `lol`
replace the original uppercase roots from the 2026-10-10 brief; source and
consumer packages follow this later decision. Canonical names such as `Summoner`, `Rank`, `Mastery`, `Match`,
`Participant` and `SummonerView` remain unique; no parallel aliases or DTO copies
are retained in `com.safjnest.spring.lol.model`.

R4B compiles independently of the monolith, SDK R4J, Spring, JDA, Mongo/BSON,
Jedis, application loggers and bootstrap. Necessary JSON dependencies are
allowed. Existing generic Hive status contracts are reused without copying
the runtime or inventing a second job model. Canonical sources are built by
the standalone artifact and the monolith; build isolation must not require
duplicate sources.

## Owners after extraction

R4B owns data and pure LoL behavior. The producing application owns Mongo
reads/writes, dirty tracking, accumulators, response composition, runtime
defaults, tracking, domain rebuild/scheduling policies and conversion of stored
records. S4B owns Riot SDK acquisition, SDK conversion, Riot rate protection,
Riot acquisition queues and raw Riot cache policy under `com.safjnest.s4b`.
The current package tranche remains in the Beebot repository, root POM and
monolith process; the internal HTTP boundary and process extraction are later
tranches. Persistence operations cease to be superclass behavior of shared
contracts. Signatures consumed by other components use independent types. Raw
acquisition data may have a distinct contract when it represents
different data from an analyzed `Match`; it must not become a competing public
profile or match model.

Emoji, command choices, enabled-shard deployment configuration, Discord labels
and message/embed formatting stay in the bot. Generic readable names and pure
lookup/comparison/composition belong to their enums. Shared multi-concept pure
helpers may live in R4B; application utility wrappers must not duplicate their
logic.

## Compatibility

Java package changes do not authorize JSON or presentation changes. Preserve
field names/types, enum strings, IDs and aliases, null/omission behavior,
map keys, arrays and annotations. Preserve Mongo/Redis encodings and key/TTL
ownership, PUUID identity, `riotId: String`, full match IDs, filter validation,
rank formulas, API metadata and HTTP outcomes. READY/PARTIAL remain 200,
PENDING remains the 202 error shape, NOT_FOUND remains 404.

The HTTP JSON profile reproduces the existing HTTP mapper; it is not the
storage mapper. Fields excluded from HTTP, including internal build/statistics
filters and summoner tracking data, remain excluded. Lane and role remain
distinct concepts. Platform shard and routing region remain distinct, including
Account versus Match routing. Received queue, canonical game queue and
statistics queue retain their separate meanings. Historical ordinal encodings
must remain readable, and unknown or absent values must not be silently
converted to unrelated defaults.

## Scope limits

No HTTP client, request builder, new process, application-module split, broker,
queue rewrite, persistence schema migration or Discord redesign is authorized
by this decision. ADR-0016 remains Proposed beyond this tranche and Hive
ADR-0017. Runtime instances, four compute workers and job progress precedence
are unchanged.

## Acceptance gates

Maintain an operational inventory of endpoint closures, SDK types and consumers,
helpers, conversions and removed operational dependencies. Verify the actual
declared SDK version and its values/behavior. Compile R4B separately and verify
the compilation closure of migrated consumers without the SDK; import scans
alone do not prove independence. Characterize enum/alias/ID/routing semantics,
rank ordering, historical storage, parameter validation, SDK conversion and
HTTP JSON including nested components. Obtain independent integration and
semantics/structure reviews of the implementation and re-review corrections.

The implementation inventory and validation evidence belong in
`services/r4b-inventory.md` and `services/r4b-validation.md`. Live Mongo, Redis
and Riot validation is reported separately and never inferred from offline
tests. API and operational documentation must describe the final implementation.
