# Arena analytics contracts

Status: proposed. These contracts translate the product brief into implementable boundaries. Open product and data-quality decisions below must be resolved before the first implementation phase is approved.

## 1. Ownership and reuse

| Need | Existing owner / capability | Decision |
|---|---|---|
| General Arena profile stats | `ProfileLeafStats` already accumulates `arenaPlacementSum` and placement counts; `LeafStats` owns common `games` and `wins` | Reuse the additive-statistics pattern and Arena placement semantics after checking field meaning. Keep a small Arena statistical leaf; do not put Arena fields into `LeafStats` or reuse `ProfileLeafStats` as the aggregate model. |
| Standard champion stats | `ChampionAnalyzer` + `ChampionStatsProvider` + `ChampionStatsDocument` / `ChampionStatsScope`, persisted in `champion_stats` | Keep unchanged. Its scope contains all champions and its lane/rank model is not the requested champion/patch Arena document. |
| Standard builds | `ChampionBuildProvider`, `ChampionBuildTimelineUtils`, `BuildSignature`, `ChampionBuildEngine`, `Build`, `champion_builds` | Reuse and generalize the shared core/slot/choice containers. Keep item-event normalization and aggregation patterns. Replace the current independent core/slots projection with branches grouped by core; Arena adds augment slots and a different core signature. |
| Match/timeline stream | `MongoDB.forEachChampionRawMatchEventBatch` + `ChampionStatsProvider` | Reuse the bounded `_id` cursor and match/`match_events` join. Generalize the source boundary or add an Arena view over it; do not duplicate batch release and join code without first checking whether the common reader can emit the data Arena needs. |
| Timeline persistence | `match_events` stores a compact JSON timeline, joined to bounded match batches by `MongoDB`; the documented compact form retains the nearest available frame to minute 15 and event counts through minute 15 | Add an explicit Arena evidence contract before aggregation. Current compact history is not evidence that early-round participant frame transitions are retained. Do not guess missing first Prismatic values. |
| API/page | `ChampionService` / `ChampionController` expose standard champion data as `ChampionView`; docs are under `docs/api/champion` | Do not append Arena shape to `ChampionView` by default. Add a dedicated canonical Arena read model and API only after the read contract is agreed; synchronize controller, model and endpoint docs together. |

## 2. Shared build containers

The user's clarified model is the working design direction: `Build`, `Core` and `Slot` are containers; item, augment and Prismatic entries share one normalized ID/position/time/stat shape. A consumer gets the meaning from the typed parent/entry kind. Arena changes which entry kinds and slots are present, and places item and augment slots under a core branch. Preserve each observed choice time in the parsed observation and aggregate it as additive timing data (`timeSum`, `timeCount`, with a derived mean); time is not part of core/choice identity. If the source has no reliable time for an entry, keep its ID and position and leave timing coverage absent.

```text
Builds response / Arena statistics root
  builds[]: Build                  one element per normalized core

Build
  core: Core                       container of normalized choices
    entries[]: BuildOption         e.g. standard items; Arena first Prismatic + boots
    stats: StatisticalLeaf
  itemSlots[]: BuildSlot            position + BuildOption[]
  augmentSlots[]: BuildSlot         position + BuildOption[]
  paths[]: BuildPath                ordered entries + StatisticalLeaf

BuildOption
  kind                             ITEM | PRISMATIC | AUGMENT | ...
  id                               normalized numeric id
  stats: StatisticalLeaf
  timing: TimingStats              timeSum/timeCount; mean derived at projection

BuildSlot
  kind                             ITEM or AUGMENT (can also be derived from the field)
  position                         build order or augment slot, scoped to its parent
  options[]: BuildOption
```

`builds[]` is the serialized list field; each element is a `Build`, representing one normalized core and its related slots and paths. There is no separate `BuildBranch` concept. This is one shared model family used by normal builds and Arena, not a mandate to add a class for every nested shape. Reuse/evolve the existing `Build` and nested option types where that gives a smaller migration. Prefer a discriminator and one compact option representation over parallel ItemOption/AugmentOption/PrismaticOption classes. Type containers in Java where useful, but persist an explicit kind when the serialized shape cannot retain the generic parameter. IDs share a numeric representation only if item and augment catalogs have distinct kinds; never merge namespaces by integer alone.

The root Arena document remains Arena-specific: `ArenaChampionStatistics` owns champion/patch overall statistics, `builds[]: Build` and global aggregates. Standard champion build output uses the same `Build` element type and containers. The Arena root, statistics accumulator and Mongo collection remain separate from standard champion statistics.

This direction means **generalize and migrate**, not build a second Arena build hierarchy from zero. The existing `Build` has a broad call graph (42 callers), old constructors and persistence/API uses. The target makes each `Build` one core-specific element in a `builds[]` list; the response change must therefore migrate standard consumers and API docs together or provide a temporary compatibility projection. The user's instruction to change the response authorizes designing this payload change; it does not make those call sites disappear.

`ArenaChampionGame` is not required by default: the analyzer can accept a small parsed input type nested/package-private or consume a normalized sequence at its boundary. Add a top-level provider only if a shared/generic provider cannot express Arena's filter, missing-event behavior and raw fields without compromising standard provider behavior.

Do not create `ArenaChampionService` automatically. If Arena is added under the existing champion read/API lifecycle, extend `ChampionService` to orchestrate the Arena analyzer and new collection while keeping the persisted model and accumulator separate. Add a dedicated service only if Arena has an independent API, cache, schedule, or operational lifecycle that justifies its own owner.

### Existing types worth reusing

- **`ChampionBuildTimelineUtils.itemEvents`**: already sorts participant-attributed item events and handles `ITEM_PURCHASED`, `ITEM_UNDO`, `ITEM_SOLD` and `ITEM_DESTROYED`, including purchase `before`/`after` transformations. Its `ItemEvent` (`itemId`, timestamp) is a useful low-level input for the Arena normalizer. It does not identify the free first Prismatic from participant frames and does not encode Arena-specific rules, so keep those in the Arena logic.
- **Mongo match/event batching**: `MongoDB.forEachChampionRawMatchEventBatch` performs a bounded match-ID scan, batch fetches projected match docs and matching `match_events`, attaches decoded event JSON, calls the consumer and releases documents. `ChampionStatsProvider` adapts this stream into `RawMatchRead`; its `forEachMatchWithBuild` also exposes the current raw `Document` to the composed stats/build analysis.
- **`ChampionStatsData.RawMatch`**: useful reference for detached per-match values, but it currently omits Arena participant fields (placement, final items, augments) and is a standard stats input. Avoid adding Arena-only concerns to it just to save one record; share/generalize only if that model is intentionally renamed or made domain-neutral.
- **`Build.Option` / `Build.CoreBuildOption`**: these are the nearest existing containers: option entries already carry match/win rates and optional timing, core options hold lists of item ids, and slots are nested lists. They lack Arena placement sufficient statistics, explicit slot positions, and per-core child distributions. Evolve these containers into the shared branch/slot/choice model; keep old JSON compatibility only at an adapter boundary during migration.
- **`ChampionBuildEngine`**: its mutable maps and `accept → finish` flow are reusable. Replace its fixed-size output loops with dynamic slot maps and group item/augment slot counts by the normalized core key. Keep its extra standard categories (runes, spells, skill order) as typed build choices or adjacent build metadata; do not copy the complete engine into an Arena-specific fork.
- **`LeafStats` / `ProfileLeafStats`**: reuse additive counter principles and validate `subTeamPlacement` semantics. `LeafStats` also includes kills/deaths/assists; `ProfileLeafStats` contains many profile fields. Do not extend either base class for Arena.

### Source-provider choice

The existing raw event stream is the best reuse candidate, but is not directly sufficient. `processChampionRawMatchEventBatch` currently invokes consumers only when a `match_events` document joins; matches with no event document are left unprocessed. `ChampionStatsProvider.RawParticipant` also contains champion/lane/win/team/KDA/CS/gold/PUUID, not placement, items or augments. Arena must count base champion games even when timeline-derived fields are unavailable.

Before adding `ArenaChampionProvider`, compare two implementations:

1. **Generalize the shared stream** to deliver every match with optional events and sufficient participant source fields, while keeping current `ChampionStatsProvider.forEachMatch` behavior (timeline eligibility and standard outputs) unchanged. Arena consumes the richer stream once and keeps base-game counts separate from event-dependent leaves. This maximizes reuse and avoids repeating bounded-join/release mechanics, with a wider blast radius that needs regression checks.
2. **Add an Arena-specific adapter** over a shared lower-level Mongo batch/join primitive. Use it if keeping a generic source contract would entangle standard stats or make eligibility opaque. The adapter may parse Arena input, but should not duplicate cursor, join, and memory-release mechanics.

Do not proceed with a separate copied Mongo reader as the default. The provider decision should be made after checking `MongoDB.championRawProjection`, the timeline evidence contract and representative queries. Persistence itself belongs in new `MongoDB` methods for the Arena collection; no separate database class is needed.

`StatisticalLeaf` is the reusable generic stats structure for every normal/Arena build leaf (core, item, augment and path); there is no separate stats class per leaf type. It should hold additive sufficient values: distinct `games`, `wins`, `placementSum`, and `placementGames`. `winRate` and `avgPlacement` are derived from these counts at the response boundary. `pickRate` is derived from a named parent population. If the external contract requires derived fields inside persisted BSON, generate them only in the final projection from these source counts and version the calculation. Keep standard champion `LeafStats` separate unless its wire/persistence shape can be migrated deliberately as part of the response change.

The shared build accumulator is mutable and private to the analyzer flow. Its normalized per-game input is a compact sequence of typed entries (`kind`, `id`, `position`, `time`) plus outcome values; use a nested/package-private input first rather than a new top-level Arena game class. `accept(...)` updates `coreKey → stats + item slots + augment slots + paths`; `finish()` returns immutable `Build` elements. Do not perform Mongo, Redis, Riot, API, or queue operations inside the accumulator. Keep Arena timeline interpretation separate even though its result uses the same container model.

## 3. Candidate Mongo contract

Collection: `arena_champion_statistics`.

Identity: deterministic `_id = <championId>:<patch>`; query and upsert directly by `_id`. This is a separate collection and schema from both `champion_stats` and `champion_builds`.

Candidate persisted shape (derived rates omitted). It uses the shared `Build` element shape. `core.entries` identifies the core with kind, ID and position; timing is aggregated separately, so equal cores from different event/round times group together.

```json
{
  "_id": "27:16.19",
  "schemaVersion": 1,
  "aggregationVersion": 1,
  "championId": 27,
  "patch": "16.19",
  "championGames": 180000,
  "placementGames": 179800,
  "placementSum": 540000,
  "coreCoverage": {
    "eligibleGames": 171000,
    "knownFirstPrismaticGames": 168000,
    "knownBootsGames": 163000,
    "knownCoreGames": 160000,
    "unknownFirstPrismaticGames": 3000,
    "ambiguousFirstPrismaticGames": 0
  },
  "builds": [
    {
      "core": {
        "entries": [
          { "kind": "PRISMATIC", "id": 447001, "position": 1, "stats": { "games": 12000, "wins": 7000, "placementSum": 36000, "placementGames": 12000 }, "timing": { "timeSum": 480000, "timeCount": 12000 } },
          { "kind": "ITEM", "id": 3009, "position": 2, "stats": { "games": 12000, "wins": 7000, "placementSum": 36000, "placementGames": 12000 }, "timing": { "timeSum": 720000, "timeCount": 11900 } }
        ],
        "stats": { "games": 12000, "wins": 7000, "placementSum": 36000, "placementGames": 12000 }
      },
      "itemSlots": [
        { "kind": "ITEM", "position": 1, "options": [ { "kind": "ITEM", "id": 6653, "stats": { "games": 8000, "wins": 4900, "placementSum": 23200, "placementGames": 7998 }, "timing": { "timeSum": 1280000, "timeCount": 8000 } } ] }
      ],
      "augmentSlots": [
        { "kind": "AUGMENT", "position": 1, "options": [ { "kind": "AUGMENT", "id": 5001, "stats": { "games": 1500, "wins": 930, "placementSum": 4200, "placementGames": 1500 }, "timing": { "timeSum": 36000, "timeCount": 1400 } } ] }
      ],
      "paths": [
        { "entries": [ { "kind": "ITEM", "id": 6653, "position": 1 }, { "kind": "ITEM", "id": 3116, "position": 2 }, { "kind": "ITEM", "id": 4633, "position": 3 } ], "stats": { "games": 3200, "wins": 2100, "placementSum": 8600, "placementGames": 3198 } }
      ]
    }
  ],
  "aggregates": {
    "firstPrismatics": [ { "itemId": 447001, "stats": { "games": 45000, "wins": 28000, "placementSum": 135000, "placementGames": 45000 } } ],
    "items": [
      { "itemId": 6653, "overall": { "games": 40000, "wins": 23000, "placementSum": 120000, "placementGames": 40000 }, "positions": [ { "position": 1, "stats": { "games": 28000, "wins": 17000, "placementSum": 84000, "placementGames": 28000 } } ] }
    ],
    "augments": [
      { "augmentId": "...", "overall": { "games": 45000, "wins": 26000, "placementSum": 135000, "placementGames": 45000 }, "positions": [ { "slot": 1, "stats": { "games": 18000, "wins": 11000, "placementSum": 54000, "placementGames": 18000 } } ] }
    ]
  },
  "updatedAt": 1790760000000
}
```

The example is illustrative and its counts are not internally asserted. The original `avgPosition` means outcome placement here (`avgPlacement`); entry/slot `position` is build or augment order. The augment ID is illustrative: retain the repository's actual augment ID type if it differs. Decide whether the wire model exposes derived rates and mean timing directly or derives them in the API mapper. Standard builds serialize the same `builds[]` / `core` / typed-slot containers, with only applicable slot kinds populated.

### Indexes and query shape

- Required hot lookup is `_id`; Mongo already indexes `_id` uniquely.
- Do not add champion/patch secondary indexes unless a concrete list/query needs them.
- Rebuild reads should be cursor-based and projected, joining `match_events` in bounded batches. The batch size is a measured resource decision, not copied blindly from another analyzer.
- Upsert the complete rebuilt document atomically by `_id`; include `schemaVersion`, `aggregationVersion`, `updatedAt`, source/coverage counts and the byte size observed during generation or verification.
- Run `explain("executionStats")` on the actual patch/queue match cursor and verify the selected index before acceptance.

## 4. Statistical populations

Use one observation per champion participant per Arena match. Deduplicate by full match id + participant id/PUUID before feeding the accumulator. `championGames` counts valid participant-game records for the patch, regardless of whether timeline-derived fields can be reconstructed. A leaf's `games` counts distinct participant-games for that leaf; if a source can produce repeated observations of the same leaf in one game, deduplicate before updating the leaf.

Every leaf reports its own observed `games` and its own valid-placement count. Never use zero as a substitute for missing placement. The planned counts intentionally differ by feature:

- Base champion placement stats: every Arena champion participant with a valid final placement.
- First Prismatic aggregate: participant-games with one exact first-Prismatic result.
- Core: participant-games with both exact first Prismatic and boots at the agreed snapshot.
- Core-conditioned build slot and path: games assigned to a valid core and with the requested normalized post-core position/path observed.
- Global item stats: post-core item observations, independent of core, and excluding first Prismatic and boots.
- Global augment stats: selected augment observations over all slots, independent of core; position leaf is the selected augment at that slot.
- Core-conditioned augment stats: selected augment observations in the selected core's population and slot.

`pickRate` must name its denominator. Candidate rules: global first-Prismatic/item/augment pick rate divides by `championGames`; a core-conditioned rate divides by `core.stats.games`; slot/path rates divide by the eligible games for that feature if the UI needs a rate for reachability. Do not call rates comparable when their denominators differ.

### Outcome and population semantics

1. **Win and placement:** the user-defined Arena win rule is `participant.win == true || subTeamPlacement >= 3`. Preserve the observed `subTeamPlacement` position in Arena statistics as its own position distribution, in addition to win counts/rate. Missing placement remains missing and does not become zero; `participant.win == true` can still count as a win when placement is absent.
2. **Core boots snapshot:** choose a deterministic event/frame boundary (recommended: the first valid post-selection snapshot after the free first Prismatic has been awarded). Decide how boots sold/replaced later affect the core; do not use final inventory without explicitly accepting that semantic.
3. **Incomplete data:** retain the game in `championGames`; increment coverage/missing counters and omit it only from leaves whose required inputs are unavailable.
4. **Bucket:** ADR-0009 defines full `patch` separately from `patchMajor`; define treatment of patch aliases/partial or missing version fields for the Arena bucket.

## 5. First Prismatic evidence and timeline contract

The free first Prismatic is not an ordinary `ITEM_PURCHASED` event. A parser must compare the participant's inventory immediately before and after the Arena selection round and accept the result only when the delta identifies exactly one valid Prismatic and the evidence is complete. Follow-up Prismatic selections through item 220007 are never candidates for the core or first-Prismatic aggregate.

The current Mongo docs describe a compact `match_events` timeline that keeps the available frame nearest minute 15 and selected event data; this does not establish that pre/post-selection Arena frames are stored. Before aggregation, choose and implement one evidence path:

1. extend the compact `match_events` payload with only the two necessary Arena frame snapshots per participant, plus capture window and evidence status; or
2. derive and persist a validated `firstPrismaticId`/status from the full Riot timeline at match ingestion and timeline repair.

The first option preserves replayability of the classifier; the second keeps the stored event payload smaller. Both require the ingestion and repair writers to agree on the same versioned contract. Existing historical compact timelines may need a Riot timeline refetch; matches whose raw timeline is no longer obtainable remain unknown. Never infer from final inventory or a later 220007 event and label it exact.

Candidate evidence states: `EXACT`, `AMBIGUOUS`, `UNKNOWN`. If the second storage option is chosen, retain enough provenance to distinguish exact frame comparison from derived/legacy absence. Additional inferred-Prismatic categories are out of the initial scope.

### Source audit — 2026-09-30

The current ingestion path fetches the Riot timeline through `MatchService`, normalizes it in `Tracker`, and persists `Match.eventData` through `MongoDB` into `match_events`. The stored payload includes timestamped participant-attributed item events, including `ITEM_PURCHASED`, `ITEM_SOLD`, `ITEM_UNDO` and `ITEM_DESTROYED`. Its `snapshots` contain scalar match progress such as gold, CS, XP and level; they do not contain participant item inventories. This does not prove the before/after inventory evidence required to classify the free first Prismatic or to select a boots/core snapshot.

The `fix-timeline` repair commands refetch the Riot timeline and replace `match_events` from that timeline. They do not recover participant augment metadata. The raw match has `playerAugment1..6`; the existing `Tracker.analyzeMatchBuild` path reads only slots 1..4 and drops zero IDs, so it can shift internal gaps and loses slots 5..6. The derived `Participant.augments` list is therefore not a reliable source of original Arena slot positions. The participant record also supplies `subTeamPlacement` and final `item0..item6` inventory, but final inventory is not historical core evidence. Augment slot IDs have no verified acquisition times.

`ChampionBuildTimelineUtils.itemEvents` already handles participant attribution, timestamp ordering, purchases, sales, destruction and undo. It returns normalized item events, not historical inventory snapshots, and does not identify the free first Prismatic. Reuse is limited to event rules proven identical to Arena. These findings are code-derived; no representative production timeline or Mongo document was inspected in this audit.

**Gate result:** the initial audit did not prove first-Prismatic evidence or a historical boots/core boundary. The user subsequently approved the strict fallback below: use only direct evidence and retain `UNKNOWN` when it is absent. Do not infer either value from final inventory, replayed events or a later `220007` event.

### Phase 1 strict-evidence payload

The approved Phase 1 policy is strict evidence only: never derive the first Prismatic or the core snapshot by replaying item events. The planned versioned `match_events` payload adds `arena_evidence` only for Arena matches. The current pure parser can consume this contract, but no `Tracker` producer or repair writer persists it:

```json
{
  "arena_evidence": {
    "version": 1,
    "participants": {
      "1": {
        "first_prismatic": {
          "status": "UNKNOWN",
          "reason": "NO_DIRECT_SELECTION_SIGNAL"
        },
        "core_snapshot": {
          "status": "UNKNOWN",
          "reason": "NO_DIRECT_INVENTORY_FRAME"
        }
      }
    }
  }
}
```

Each participant entry uses `EXACT`, `AMBIGUOUS` or `UNKNOWN`. `EXACT` first-Prismatic evidence carries the observed `item_id` and `timestamp`; `EXACT` core evidence carries the observed snapshot `timestamp`, `boots_id` and `item_ids`. The parser requires the snapshot not to precede the first Prismatic, requires a non-empty effective item list containing the boots, and rejects inconsistent values as ambiguous. `AMBIGUOUS` retains a reason; `UNKNOWN` carries a reason and no guessed value. Timeline event times remain metadata and never participate in core identity. Item `220007` is invalid as the first Prismatic and is excluded from core membership and post-core items. The current R4J timeline frames cannot populate exact core snapshots, so their evidence remains `UNKNOWN` until a direct source becomes available.

Augments are read from the existing ordered `Participant.augments` list and receive sequence positions from that list; the parser does not reconstruct empty raw Riot fields or their gaps. This order is not described as temporal selection order. An augment time is null/absent unless a direct source provides it. Raw placement is `subTeamPlacement`; Arena win is `participant.win == true || subTeamPlacement >= 3`. Persist the exact observed placement position in addition to win statistics.

The parser consumes the existing ordered `Participant.augments` list and assigns sequence positions from that list. An absent or empty list is counted as missing; it does not infer choices from another field. This preserves the existing participant model and Match API serialization. The list does not establish augment selection times, which remain null unless a direct source provides them.

### Manual first-Prismatic diagnostic

`ArenaFirstPrismaticManual` is an isolated debug runner: it calls `MongoDB.findMatch(matchId)`, which attaches the persisted `match_events`, and prints each participant's final item slots and relevant item events. `FirstPrismaticResolver` tries pre-anvil possession events, a single candidate, accounting, then the first classified Prismatic in final slot order with `TOOLTIP_FALLBACK`; it records resolution type/reason and missing/ambiguous counts. Its undo handling distinguishes cancelled purchases from undone sales/destruction; an undo without an identifiable target is counted ambiguous and does not cancel a guessed event. Classification currently reuses `ItemUtils.isPrismatic` and explicitly excludes `447111`. This inference and slot-order fallback are diagnostic only: they do not populate the strict-evidence Arena parser or production statistics and must not be described as exact first-Prismatic evidence.

## 6. Boots and build event contract

Build order is chronological item acquisition after the core, not physical inventory slots. A dedicated Arena normalizer should convert participant-attributed Riot item events into a deterministic sequence while applying `ITEM_PURCHASED`, `ITEM_UNDO`, `ITEM_SOLD`, `ITEM_DESTROYED` and Arena transformations.

Audit and reuse `ChampionBuildTimelineUtils` for the common event-state transitions. Add Arena-specific rules around the free Prismatic, core removal, item transformations and items whose identity changes without a normal purchase event. Use participant final inventory as a reconciliation signal, not as proof of missing chronology.

Recommended candidate semantics:

- Exclude exactly the free first Prismatic and the boots selected as core from post-core slots.
- Preserve the first effective acquisition time for each remaining item identity after undo/sell/destroy normalization; do not silently reorder by item id.
- Assign consecutive observed positions only after removing the core; record any unresolvable transition as a parser rejection/coverage reason.
- Count every item at its normalized position once per participant-game. `overall` includes only post-core items and sums the position populations.
- Count a `path` only when every sequence element in its configured maximum length is observed; cap path length and retention only after representative size/cardinality measurements.

The Phase 1 parser fails closed for unresolved transformations and any post-boundary purchase, sale or destruction involving an item in the direct core snapshot; it counts the sequence as ambiguous and omits it. These cases remain open for a future complete Arena normalizer, along with items lost through Arena mechanics. The standard champion build's current four-slot output is not the Arena slot contract.

## 7. Augment contract

Count the chosen augment once for each participant-game and awarded slot. Do not count offered choices, rerolls or rejected choices. Keep two distinct maps: champion-global `augmentId → slot → stats`, and `core → slot → augmentId → stats`. Determine slot identity from source ordering and preserve unknown/missing slots as coverage outcomes rather than shifting later choices. The champion-global augment `overall` leaf counts each participant-game once per augment id even if the same augment appears in multiple slots; each slot leaf counts games in that specific slot. Thus overall games are not required to equal the sum of slot games when duplicate augment ids are possible. If selection frequency is needed separately from distinct-game prevalence, add an explicit additive `selections` count rather than overloading `games`.

Slot lists are dynamic; no `AUGMENT_SLOT_COUNT = 4` assumption. Define behavior for partial/incomplete games. The overall's `games` measures distinct participant-games with that augment; slot `games` measures distinct participant-games with that augment in that slot. Position/slot leaves are not mutually exclusive when an augment can appear in more than one slot.

## 8. BSON growth gate

MongoDB rejects a BSON document above 16 MiB. Core combinations multiply by build slots, paths and augment slots, while rare full paths can grow combinatorially. The one-document target is therefore a hard feasibility gate, not an assumed safe design.

Before approving implementation, measure representative production-like champion/patch samples and estimate the maximum document after BSON serialization, including worst-cardinality champion, largest patch, and future growth. Define an operational ceiling with headroom (candidate ceiling: 12 MiB) and fail the rebuild before upsert if it is crossed; record the measured bytes and largest sections.

If unbounded complete paths or all long-tail core leaves exceed the ceiling, choose and document one of these before coding: (a) a disclosed support threshold/top-K only for secondary paths, or (b) multiple documents in the same Arena collection partitioned by champion/patch plus section/core. A single Mongo document cannot satisfy both unbounded retention and the 16 MiB limit. Do not silently truncate core stats or the primary per-position distributions.

## 9. Rebuild, API and docs

- Rebuild all champion/patch records from Mongo `match` + joined `match_events`; do not write to the normal `champion_stats` or `champion_builds` collection.
- Add a dedicated `ArenaChampionService`/compute entry point through `QueueHandler` and `ComputeScheduler`. Whether to add an `ARENA` database route or use an existing route is an ADR decision after reviewing worker budgets, deduplication and progress reporting. Expose current patch/champion plus total/completed/missing/failed progress.
- Keep the accumulator pure and batch data released after each batch, following the existing aggregate pattern. Do not materialize all matches/timelines in RAM.
- The initial implementation can be internal-only. If an HTTP/API contract is added, expose the canonical Arena model directly, document route, fields, denominators, sorting-ready raw options and pending behavior, and review caching/index invalidation. Do not change standard champion API payloads as a side effect.
- Update `docs/architecture/README.md`, an accepted Arena ADR, `docs/HANDBOOK.md`, `docs/mongo/README.md`, `docs/mongo/08-query-inventory.md`, and API docs only when the corresponding implementation/contract decision is approved. These planning notes do not amend existing accepted ADRs.
