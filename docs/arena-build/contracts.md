# Arena analytics definitive contract

Status: definitive contract from the user's final requirement on 2026-10-01;
Phase 2 parser/model/accumulator realigned on 2026-10-02. [Schema](schema.md) gives
the implemented Java shape and a serializer-generated fixture example;
[phases](phases.md) records passed offline gates and pending operational integration.

## Ownership and precedence

The final requirement supersedes these earlier Arena proposals: core identity as
the entire non-Prismatic purchase sequence, `220007` as a build/core item, a
separate Arena statistics collection, different schemas for decision/fallback
cores, synthetic default builds and automatic backend sample-threshold backoff.

Reuse shared `Build`, `StatisticalLeaf`, choice/slot/timing primitives,
`ArenaItemCatalog`, coverage counters and `FirstPrismaticResolver`. Arena data
belongs to the build model in `lol.model`; do not create a competing statistics
root or a Spring success DTO. `ArenaBuildData` denotes the Arena-specific payload
of the shared Build. Java helper types may be nested/reused rather than creating
unnecessary top-level classes or services.

Keep Arena aggregation logically separate from standard aggregation while using
the same collection. The standard generator, eligibility, capped options,
constructors, serialized fields, APIs and presentation retain their current
behavior under ADR-0006/0012. Obsolete Arena aggregate schemas are unsupported;
discard and regenerate them rather than implementing compatibility. The current endpoint still uses the standard
generator, including existing `queue=CHERRY` requests. Phase 2 changes no
endpoint. Do not amend accepted ADRs implicitly when exposing Arena later.

The accumulator is pure: detached match/parser/catalog inputs in, detached
snapshots out. MongoDB owns persistence; ChampionService and the existing
provider/queue owners own loading, refresh and orchestration. No Redis, Mongo,
scheduler, catalog fetch or Riot call belongs inside the accumulator. Participant
fields and Tracker writers are unchanged. No new collection or independent
service is required by the final model.

## Parsed facts

`ParsedArenaGame` describes a participant-game, not frontend concepts. It contains
at least placement, win, boots, ordered equipment path, ordered augment path and
coverage. Core keys and conditioned steps are derived by the accumulator.

Equipment is one ordered sequence of Prismatic and Legendary/completed normal
items, for example `P1 → L1 → P2 → L2`. This notation is illustrative: it does
not require alternating kinds. Each equipment observation retains:

- `kind` and numeric `id`;
- general `position` in the equipment path;
- `typePosition`, counted independently as P1/P2 or L1/L2;
- nullable `timestampMillis` and nullable `tooltipSlot`;
- `orderSource`: `TIMELINE`, `FIRST_PRISMATIC_RESOLVER` or `TOOLTIP_FALLBACK`.

Boots are separate from that equipment path. Augments form their own ordered
path A1..A6. Preserve available source positions and internal gaps; do not
fabricate missing augments, promote A2 to A1, or invent timestamps. If ingestion
has already lost raw Riot slot gaps, the retained Participant list is the available
order and that limitation must be reported. Never merge equipment and augments
into a chronological timeline without evidence.

## First Prismatic and equipment order

Actually call `FirstPrismaticResolver` in the analytics parser flow. Retain its
resolution type and reason: `PRE_ANVIL_EVENT`, `SINGLE_CANDIDATE`, `ACCOUNTING`,
`TOOLTIP_FALLBACK` or `NOT_FOUND`. A fallback contributes valid reconstructed
facts and aggregates but never becomes exact evidence merely because its source
is a resolver. An unresolved effective P1 prevents a boots+P1 core/observed build, not
independent boots, augment, outcome or item statistics. The raw resolver can
report `NOT_FOUND` when multiple pre-anvil candidates exist, while a complete,
fully timed acquisition path independently proves the earliest P1. Retain both
the raw result and the effective P1 quality; never relabel fallback as exact.

Use a partial-order reconstruction:

1. Attributed, surviving timeline evidence imposes hard order constraints.
2. The resolved P1 imposes the first-Prismatic constraint when compatible with
   stronger evidence. A conflicting resolver result is recorded; it cannot
   overwrite timeline order.
3. Final tooltip slots `item0..item5` supply fallback order and stable tie breaks
   where stronger constraints are absent.

Timeline wins over conflicting tooltip order. Do not implement a comparator that
puts every timed item before every untimed item. For tooltip sequence
`P1,L1,P2,L3`, knowing only `L1@1000,L3@3000` allows that fallback sequence;
knowing `L3@2000,P2@3000` requires `P1,L1,L3,P2`. Preserve evidence quality for
each reconstructed element and the full path. Unresolved contradictory order
remains unknown, with coverage, rather than producing an exact path.

Use final item membership plus attributable historical acquisitions/possession
evidence, retaining sold items where history proves them. Unknown catalog items
are omitted and counted, never guessed to be Legendary or Prismatic. Classification
uses the existing catalog/classifier, including its existing special cases.

`220007` is only anvil evidence for Prismatic reconstruction/accounting. Exclude
it from equipment, Legendary statistics, observed builds, core keys, step context
and recommendations/choices. Purchase and destruction at the same timestamp
represent one observed anvil use; deduplicate that evidence. The existing
`1 free Prismatic + N distinct anvil uses` check is an evidence consistency rule,
not permission to assign an unknown event or invent an awarded item.

## Supported events, attribution and transition policy

The current compact parser supports `ITEM_PURCHASED`, `ITEM_SOLD`, `ITEM_UNDO`
and `ITEM_DESTROYED`, with numeric item/before/after, timestamp and participant
reference. This is a source audit, not inspection of production timelines.
Do not invent an autonomous transformation event.

- Accept only a direct positive participant ID or an unambiguous reference to
  the actual participant. Reject reference/`participantId` 0 even if a reference
  map resolves it to a PUUID. Missing/conflicting actors stay unattributed;
  no inventory or teammate inference may assign them.
- An attributed purchase establishes an acquired item. An explicit compatible
  before/after change records the acquired after-ID; it does not turn an unknown
  transition into an inferred transformation.
- Sales/destruction preserve a proven, committed historical acquisition. They
  do not create acquisitions. Independently proven possession can be input to
  the resolver under its documented resolution types.
- An unambiguous undo of a purchase removes that cancelled acquisition. Undo of
  a sale/destruction does not delete the original acquisition. Repeated,
  identity-free, mismatched or untimed undo must retain the existing conservative
  ambiguity handling, without guessing a target or restoring a cancelled item.
  The parser removes cancelled/ambiguous acquisition evidence before invoking
  the resolver. A same-time anvil purchase/destruction and its undo are filtered
  together. Resolver evidence cannot bypass strict attribution or undo checks.
- Remove exact duplicate events. Equipment identity is a distinct item ID in
  the reconstructed participant path: retain its earliest surviving acquisition,
  merge final membership/tooltip metadata, and collapse sale/repurchase or
  repeated purchases of that ID. These do not generate additional P/L slots.
  A later acquisition after an undone purchase can supply the retained time.
  Augments keep their actual source positions even when the same ID repeats;
  global membership still counts that ID once per participant-game.
- Unknown event types, malformed IDs/times and uncertain transitions are
  recorded in coverage. Only affected order/context is unavailable; valid
  independent final membership and outcomes survive.

For boots, prefer surviving attributed evidence and actual final membership.
The existing derived `Participant.boots` can be a fallback only when it does not
resurrect a purchase proven cancelled by the stream. Ambiguous boots identity
does not generate multiple cores by guessing one boot choice.

The optional source `arena_evidence.first_prismatic` sidecar, when present, is
additional validated evidence, not a replacement for actual resolver integration.
There is no current writer for this sidecar. No Participant or Tracker change
is necessary for this contract.

## Overall positional statistics

`ArenaBuildData.stats` holds overall participant-game outcomes. `positions`
contains boots, augments A1..A6, Prismatics P1..P6 and Legendary items L1..Ln.
Legendary positions are dynamic; do not impose an arbitrary six-slot cap there.
These distributions are independent of all core identities. A3 statistics must
be directly available without traversing a decision core.

Every option uses `StatisticalLeaf` for additive `games`, `wins`, `placementSum`,
`placementGames` and `placements` histogram. Expose/derive `winrate`,
`averagePlacement`, explicit `denominator` and `pickrate` using the same primitive
where possible. Unknown placement/time means stay null, never fabricated zero.
The existing numeric rate primitive returns 0 only for an empty population
(games/denominator=0), whose counters remain explicit; this does not assert a
known missing placement, time or order. Retain timing sums/counts separately.

Core-independent membership without a defensible position remains available as
unpositioned membership with a named coverage population; never silently assign
P1/L1 from final inventory. Tooltip-derived positions are permitted but labelled
fallback. Timeline absence or partiality is not a blanket reason to drop all
positions, all cores or all builds when fallback/source facts are sufficient.

## Actually observed builds

`builds[]` contains distinct actually observed sequences with identity:

```text
bootsId + firstPrismaticId + ordered Legendary IDs
```

Exclude augments and later Prismatics from identity. Example equipment
`P1 Sword,L1 Collector,P2 Duskblade,L2 Infinity Edge,L3 LDR` projects to observed
build `boots,Sword,Collector,Infinity Edge,LDR`. The same sequence with another
augment or P2 contributes to the same build, regardless of timestamps.

Each observed build has its own outcome/placement statistics and denominator.
An observation can use documented fallback reconstruction; retain its quality.
Do not call an incomplete/unknown Legendary order a complete observed build.
Independently valid statistics survive when that sequence is unavailable.

The frontend can select a robust observed build. Earlier `defaultBuild` and
`mostPlayedBuilds` proposals are fulfilled by selecting/ranking actual `builds[]`,
optionally conditioned on boots+P1. No synthetic top-per-slot build or duplicate
fallback-build aggregate is required.

## Two generic core types

Both core types use the same `cores[]` schema:

```text
CoreKey { bootsId, anchorKind, anchorId }
anchorKind = AUGMENT | PRISMATIC
```

- Decision core: boots + A1 (`anchorKind=AUGMENT`). P1 is a later state/choice.
- Fallback core: boots + P1 (`anchorKind=PRISMATIC`). Its root pools games
  with different augments. More specific steps may condition on augments using
  the same generic schema.

A participant-game can feed both independently. Their counts must not be summed
as one population. Missing A1 can still permit the fallback core; unresolved P1
can still permit the decision core. Require boots and the specific anchor for a
core. Never require the triple boots+A1+P1 as the base key. Item/augment order,
not timestamps, identifies contexts and paths. The former
`Map<List<Integer>, Branch>` purchase-sequence core model has been removed.

## Steps and choices

Every core contains flat `steps[]`. A step stores its complete `context`:
`bootsId`, ordered equipment (including kind/type position), and augments with
their positions. A parent ID alone is insufficient. It also stores its own stats
and choices separated into `legendary[]`, `prismatics[]` and augment options
grouped by position (`augments[]`, each carrying `position`).

The context includes the core anchor: A1 for a decision root, P1 for a fallback
root. Augment-root context need not include any equipment; Prismatic-root
context need not include any augment. No boot is repeated as equipment.

Equipment prefixes `P1`, `P1+L1`, `P1+L1+P2`, `P1+L1+P2+L2` and augment
prefixes `A1`, `A1+A2`, `A1+A2+A3` support progressive branches. Typed equipment
choices describe observed continuations of their kind relative to the supplied
equipment context. Augment choices are indexed by position: the user may inspect
A3 directly, without selecting A2 or requiring a single next-augment field.

Combined equipment/augment context expresses observed co-occurrence and
conditioning. With missing augment timestamps it does not assert that these
augments occurred before an equipment purchase. Do not fabricate temporal
interleaving, cycles, unobserved paths or a giant recursive BSON tree. Reuse
observed prefix aggregates/flat context keys. One participant-game contributes
at most once to a given core, step and choice key even if multiple prefix
enumerations reach the same context.

Retain every supported step, including a step with one game. No backend threshold,
top-N truncation, pruning or automatic sparse-state substitution applies. The
frontend chooses exact state, a more generic compatible step, fallback core,
observed build or global positional data. Keep populations visible so that this
choice never silently mixes contexts.

## Denominators, deduplication and coverage

One accumulator input is a full match. Filter CHERRY, champion and the exact
patch scope; select distinct matching participants. Reject repeated full match
IDs and duplicate/ambiguous participant identities before mutation. Two players
of the same champion are two participant-games in one match.

Count each choice at most once per participant-game and logical choice key.
Global ID membership is deduplicated across positions; per-position choices use
`kind+id+position`. Repeated augment IDs keep their source positions without
multiplying global membership. Equipment IDs follow the distinct-acquisition
policy above. Observed-build, core and
step identities have their own per-participant-game deduplication.

- `winrate = wins / games` of that leaf.
- `averagePlacement = placementSum / placementGames`; only valid placements 1..8
  enter the sum/histogram. Histogram sum equals placementGames.
- Global positional/boots and observed-build prevalence uses overall champion
  participant-games. Expose position-observed coverage separately.
- Core prevalence uses overall champion participant-games. Root choices use core
  games; deeper choices use that step's games. Augment position coverage is
  separately visible, including gaps.
- Timing mean uses `timeSumMillis / timeCount`, only timed samples. Missing time
  is never zero.

Example: boots+A1 has 1,000 games and Sword P1 has 300: pickrate 300/1,000.
Its Sword step has 300 games and Collector has 150: 150/300. Boots+Sword fallback
has 5,000 games and Collector has 2,800: 2,800/5,000. Do not substitute one
denominator for another or normalize away missing choices.

Keep the existing win classification unless separately changed; the current
Phase 2 preserves `participant.win || subTeamPlacement >= 3`; realignment
does not redefine it. Store observed placement separately.

Coverage retains missing/ambiguous/rejected reason maps and distinguishes match
counts, participant-game counts, missing/partial timeline, untimed/unpositioned
membership, unresolved classification and undo conflicts. Preserve first-P1
resolution-type counts and exact/fallback/unresolved populations; preserve
full-order exact/mixed/fallback populations separately. Counters need not be
mutually exclusive unless their definitions explicitly say so. Unknown source
completeness cannot be turned into proven completeness by a default boolean.

## Single collection and current serialization

Reuse `champion_builds` only. The Arena document is one complete document per
champion + patch + `queue=CHERRY`, with no lane/rank/region/opponent/duo variants
in this initial population. Retain the existing filter-key conventions and
structured `build` envelope. The logical Arena payload is
`stats + positions + builds + cores + coverage`; [schema](schema.md) shows the
implemented nullable `Build.arena` payload.

Do not create `arena_champion_statistics`, `arena_summary`, `arena_state` or
`arena_fallback` collections. These are rejected former proposals, not planned
collections. Do not split summaries/core states into documents now. Only actual
BSON/cardinality measurements can justify later splitting cores into additional
documents in the same collection. Measure BSON/headroom and heap before rollout;
measurement is not permission to discard rare steps or silently truncate data.

Use the existing `JsonCodec` for JSON and structured BSON; no opaque JSON field,
new codec or DTO. Standard serialization omits the optional Arena payload and
retains the current standard field set. Arena is serialized only in its current
shape. Nullable facts and empty distributions stay distinct.

The Arena aggregate has one supported schema: `ArenaBuildData` inside Build,
with schemaVersion=2 and aggregationVersion=3. The former orderedCore/orderedItems/
orderedAugments/paths fields, their constructor/factory and unused Core/Path types
have been removed. There is no reader/adapter/migration or regression-test gate
for those obsolete aggregates. Discard and regenerate affected Arena aggregates
from matches when operational integration is implemented; preserve source data.
Standard CHERRY output is not an Arena aggregate. The existing persistence/read
owners will identify the current Arena payload; absent/obsolete Arena data must
be rebuilt, not converted or served as current. No production document was
deleted or regenerated in this task.

## Acceptance and scope

The implementation must pass the tests in [phases](phases.md), connect parser →
resolver → accumulator → shared Build payload, and later connect persistence
and read consumers through existing owners. No backend selection threshold,
advanced ranking/ML, frontend redesign, new HTTP route, Redis namespace,
production scheduler operation, backfill or production Mongo migration is part
of Phase 2. Implemented model, JSON example and current validation are linked
in [schema](schema.md) and [phases](phases.md). API presentation, Participant,
Tracker, existing provider/writer/query/cache owners and accepted ADRs are unchanged.
