# Implemented Arena Java and JSON schema

Status: pure Phase 2 implemented on 2026-10-02. The nullable `Build.arena`
payload below is real Java, serialized through existing JsonCodec; it is not
currently served by the champion endpoint. See [contracts](contracts.md) and
[phases](phases.md) for semantics, test evidence and pending integration gates.

## Java shape

The shared Build preserves its current standard behavior. `arena` is omitted
when null. Obsolete orderedCore/orderedItems/orderedAugments/paths fields and
their constructor/factory and unused Core/Path types have been removed. The accumulator returns Build;
its standard option lists are empty for this projection. The former internal
ArenaChampionStatistics root and purchase-sequence Branch map were removed.

The complete Arena-specific type is
[ArenaBuildData.java](../../src/main/java/com/safjnest/lol/model/ArenaBuildData.java):

```java
public record ArenaBuildData(int schemaVersion, int aggregationVersion, StatisticalLeaf stats,
        Positions positions, List<ObservedBuild> builds, List<Core> cores, Coverage coverage) {

    public ArenaBuildData {
        builds = List.copyOf(builds);
        cores = List.copyOf(cores);
    }

    public record Positions(List<Build.Choice> boots, List<Build.Slot> augments,
            List<Build.Slot> prismatics, List<Build.Slot> legendaryItems,
            List<Build.Choice> membership, List<Build.Choice> unpositioned) {
        public Positions {
            boots = List.copyOf(boots);
            augments = List.copyOf(augments);
            prismatics = List.copyOf(prismatics);
            legendaryItems = List.copyOf(legendaryItems);
            membership = List.copyOf(membership);
            unpositioned = List.copyOf(unpositioned);
        }
    }

    public record ObservedBuild(int bootsId, int firstPrismaticId, List<Integer> legendaryIds,
            StatisticalLeaf stats, long denominator, Map<String, Long> orderQuality) {
        public ObservedBuild {
            legendaryIds = List.copyOf(legendaryIds);
            orderQuality = Map.copyOf(orderQuality);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public enum AnchorKind { AUGMENT, PRISMATIC }

    public record CoreKey(int bootsId, AnchorKind anchorKind, int anchorId) {}

    public record Core(CoreKey key, StatisticalLeaf stats, long denominator, List<Step> steps) {
        public Core { steps = List.copyOf(steps); }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public record Step(Context context, StatisticalLeaf stats, Choices choices) {}

    public record Context(int bootsId, List<EquipmentKey> equipment, List<AugmentKey> augments) {
        public Context {
            equipment = List.copyOf(equipment);
            augments = List.copyOf(augments);
        }
    }

    public record EquipmentKey(Build.Kind kind, int id, Integer position, Integer typePosition) {}

    public record AugmentKey(int id, int position) {}

    public record Choices(List<Build.Choice> legendary, List<Build.Choice> prismatics,
            List<Build.Slot> augments) {
        public Choices {
            legendary = List.copyOf(legendary);
            prismatics = List.copyOf(prismatics);
            augments = List.copyOf(augments);
        }
    }

    public record Coverage(long matches, long participantGames, long decisionCoreGames, long fallbackCoreGames,
            long observedBuildGames, Map<String, Long> missing, Map<String, Long> ambiguous,
            Map<String, Long> rejected, Map<String, Long> firstPrismaticResolutionTypes,
            Map<String, Long> equipmentOrder, Map<String, Long> firstPrismaticQuality) {
        public Coverage {
            missing = Map.copyOf(missing);
            ambiguous = Map.copyOf(ambiguous);
            rejected = Map.copyOf(rejected);
            firstPrismaticResolutionTypes = Map.copyOf(firstPrismaticResolutionTypes);
            equipmentOrder = Map.copyOf(equipmentOrder);
            firstPrismaticQuality = Map.copyOf(firstPrismaticQuality);
        }
    }
}
```

`JsonProperty` is Jackson's annotation; `StatisticalLeaf` is the existing shared
primitive. Positions and choices reuse `Build.Choice` and `Build.Slot`; `ITEM`
means a classified completed normal/Legendary item, without renaming the
standard enum. Slot `population` is its position-observed population. Every option's
denominator remains global games or the parent core/step games, not slot games.
`membership` deduplicates IDs across positions; `unpositioned` contains only
samples lacking defensible positions. Missing timing uses timeCount=0 and
averageTimeMillis=null. Missing placement uses placementGames=0 and
averagePlacement=null. Rates/means are derived READ_ONLY JSON properties and
are ignored on deserialization; additive counters are the source of truth.

Parser-only facts remain in
[ParsedArenaGame.java](../../src/main/java/com/safjnest/lol/arena/ParsedArenaGame.java):

```java
record Equipment(Build.Kind kind, int id, Integer position, Integer typePosition,
    Long timestampMillis, Integer tooltipSlot, OrderSource orderSource) {}
record Augment(int id, int position) {}
enum OrderSource { TIMELINE, FIRST_PRISMATIC_RESOLVER, TOOLTIP_FALLBACK }
enum OrderQuality { EXACT, MIXED, FALLBACK, UNRESOLVED }
```

Parsed facts also hold placement/win/boots, the two separate paths, independent
membership choices, effective firstPrismaticId, raw FirstPrismaticResult,
firstPrismaticExact, equipmentComplete and reason counters. Context identity
excludes timestamps, tooltip slots and source; nullable positions remain nullable.
Coverage separates raw resolver type from effective EXACT/FALLBACK/UNRESOLVED P1
quality and EXACT/MIXED/FALLBACK/UNRESOLVED equipment-path quality.

## JSON payload example

This `ArenaBuildData` fragment was generated by the implemented accumulator and
JsonCodec from two synthetic fixtures. It is not a live endpoint or Mongo read.
The first game has timed P1/L1/P2/L2 and A1/A2/A3; the second has the same
boots/P1/L1/L2, a different A1, an A2 gap and A3, with no timeline. Thus one
observed build pools both games and the fallback core pools both A1 variants.
The example shows selected steps; the full aggregate retains all 46 observed
prefix contexts (15 + 8 decision, 23 fallback), with no pruning. P/A positions
not observed are absent, not fabricated as zero samples.

```json
{
  "coverage": {
    "participantGames": 2,
    "fallbackCoreGames": 2,
    "firstPrismaticResolutionTypes": {"TOOLTIP_FALLBACK":1,"ACCOUNTING":1},
    "firstPrismaticQuality": {"FALLBACK":1,"EXACT":1},
    "rejected": {},
    "missing": {"ITEM_EVENTS_ABSENT":1,"ITEM_HISTORY_PARTIAL":1,"AUGMENT_ID_INVALID_OR_ABSENT":1,"EQUIPMENT_TIME_ABSENT":3},
    "observedBuildGames": 2,
    "ambiguous": {"FIRST_PRISMATIC_TOOLTIP_FALLBACK":1},
    "matches": 2,
    "decisionCoreGames": 2,
    "equipmentOrder": {"FALLBACK":1,"EXACT":1}
  },
  "aggregationVersion": 3,
  "schemaVersion": 2,
  "cores": [
    {"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"pickrate":0.5,"steps":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"context":{"bootsId":3006,"equipment":[],"augments":[{"id":101,"position":1}]},"choices":{"prismatics":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":2000,"averageTimeMillis":2000},"id":447001,"position":1,"pickrate":1,"denominator":1,"unpositionedGames":0}],"legendary":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":3000,"averageTimeMillis":3000},"id":4001,"position":1,"pickrate":1,"denominator":1,"unpositionedGames":0}],"augments":[{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":111,"position":2,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}},{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":121,"position":3,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":3,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}}]}},{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"context":{"bootsId":3006,"equipment":[{"kind":"PRISMATIC","typePosition":1,"id":447001,"position":1}],"augments":[{"id":101,"position":1}]},"choices":{"prismatics":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":5000,"averageTimeMillis":5000},"id":447002,"position":2,"pickrate":1,"denominator":1,"unpositionedGames":0}],"legendary":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":3000,"averageTimeMillis":3000},"id":4001,"position":1,"pickrate":1,"denominator":1,"unpositionedGames":0}],"augments":[{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":111,"position":2,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}},{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":121,"position":3,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":3,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}}]}},{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"context":{"bootsId":3006,"equipment":[{"kind":"PRISMATIC","typePosition":1,"id":447001,"position":1},{"kind":"ITEM","typePosition":1,"id":4001,"position":2}],"augments":[{"id":101,"position":1}]},"choices":{"prismatics":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":5000,"averageTimeMillis":5000},"id":447002,"position":2,"pickrate":1,"denominator":1,"unpositionedGames":0}],"legendary":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":6000,"averageTimeMillis":6000},"id":4003,"position":2,"pickrate":1,"denominator":1,"unpositionedGames":0}],"augments":[{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":111,"position":2,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}},{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":121,"position":3,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":3,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}}]}}],"key":{"bootsId":3006,"anchorKind":"AUGMENT","anchorId":101},"denominator":2},
    {"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"pickrate":0.5,"steps":[{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"context":{"bootsId":3006,"equipment":[],"augments":[{"id":102,"position":1}]},"choices":{"prismatics":[{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"PRISMATIC","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":447001,"position":1,"pickrate":1,"denominator":1,"unpositionedGames":0}],"legendary":[{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"ITEM","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":4001,"position":1,"pickrate":1,"denominator":1,"unpositionedGames":0}],"augments":[{"kind":"AUGMENT","options":[{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":122,"position":3,"pickrate":1,"denominator":1,"unpositionedGames":0}],"position":3,"population":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2}}]}}],"key":{"bootsId":3006,"anchorKind":"AUGMENT","anchorId":102},"denominator":2},
    {"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"pickrate":1,"steps":[{"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"context":{"bootsId":3006,"equipment":[{"kind":"PRISMATIC","typePosition":1,"id":447001,"position":1}],"augments":[]},"choices":{"prismatics":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":5000,"averageTimeMillis":5000},"id":447002,"position":2,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"legendary":[{"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":3000,"averageTimeMillis":3000},"id":4001,"position":1,"pickrate":1,"denominator":2,"unpositionedGames":0}],"augments":[{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":101,"position":1,"pickrate":0.5,"denominator":2,"unpositionedGames":0},{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":102,"position":1,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":1,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}},{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":111,"position":2,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}},{"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":121,"position":3,"pickrate":0.5,"denominator":2,"unpositionedGames":0},{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":122,"position":3,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":3,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}}]}}],"key":{"bootsId":3006,"anchorKind":"PRISMATIC","anchorId":447001},"denominator":2}
  ],
  "stats": {
    "wins": 1,
    "placementGames": 2,
    "averagePlacement": 1.5,
    "games": 2,
    "winrate": 0.5,
    "placements": {"1":1,"2":1},
    "placementSum": 3
  },
  "builds": [
    {"legendaryIds":[4001,4003],"bootsId":3006,"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"firstPrismaticId":447001,"pickrate":1,"orderQuality":{"FALLBACK":1,"EXACT":1},"denominator":2}
  ],
  "positions": {
    "prismatics": [
      {"kind":"PRISMATIC","options":[{"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":2000,"averageTimeMillis":2000},"id":447001,"position":1,"pickrate":1,"denominator":2,"unpositionedGames":0}],"position":1,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}},
      {"kind":"PRISMATIC","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":5000,"averageTimeMillis":5000},"id":447002,"position":2,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}}
    ],
    "unpositioned": [],
    "boots": [
      {"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"BOOTS","timing":{"timeCount":1,"timeSumMillis":1000,"averageTimeMillis":1000},"id":3006,"position":null,"pickrate":1,"denominator":2,"unpositionedGames":0}
    ],
    "legendaryItems": [
      {"kind":"ITEM","options":[{"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":3000,"averageTimeMillis":3000},"id":4001,"position":1,"pickrate":1,"denominator":2,"unpositionedGames":0}],"position":1,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}},
      {"kind":"ITEM","options":[{"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":6000,"averageTimeMillis":6000},"id":4003,"position":2,"pickrate":1,"denominator":2,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}}
    ],
    "membership": [
      {"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":3000,"averageTimeMillis":3000},"id":4001,"position":null,"pickrate":1,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"ITEM","timing":{"timeCount":1,"timeSumMillis":6000,"averageTimeMillis":6000},"id":4003,"position":null,"pickrate":1,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":2000,"averageTimeMillis":2000},"id":447001,"position":null,"pickrate":1,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"PRISMATIC","timing":{"timeCount":1,"timeSumMillis":5000,"averageTimeMillis":5000},"id":447002,"position":null,"pickrate":0.5,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":101,"position":null,"pickrate":0.5,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":102,"position":null,"pickrate":0.5,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":111,"position":null,"pickrate":0.5,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":121,"position":null,"pickrate":0.5,"denominator":2,"unpositionedGames":0},
      {"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":122,"position":null,"pickrate":0.5,"denominator":2,"unpositionedGames":0}
    ],
    "augments": [
      {"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":101,"position":1,"pickrate":0.5,"denominator":2,"unpositionedGames":0},{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":102,"position":1,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":1,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}},
      {"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":111,"position":2,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":2,"population":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1}},
      {"kind":"AUGMENT","options":[{"stats":{"wins":1,"placementGames":1,"averagePlacement":1,"games":1,"winrate":1,"placements":{"1":1},"placementSum":1},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":121,"position":3,"pickrate":0.5,"denominator":2,"unpositionedGames":0},{"stats":{"wins":0,"placementGames":1,"averagePlacement":2,"games":1,"winrate":0,"placements":{"2":1},"placementSum":2},"kind":"AUGMENT","timing":{"timeCount":0,"timeSumMillis":0,"averageTimeMillis":null},"id":122,"position":3,"pickrate":0.5,"denominator":2,"unpositionedGames":0}],"position":3,"population":{"wins":1,"placementGames":2,"averagePlacement":1.5,"games":2,"winrate":0.5,"placements":{"1":1,"2":1},"placementSum":3}}
    ]
  }
}
```

The decision steps retain root → P1 → P1+L1, with direct A3 choices alongside
A2. The fallback root contains P1 and no augment constraint. Its first Legendary
option has denominator=2 while the one-game decision steps have denominator=1.
Both observed acquisitions of L2=4003 contribute to the same observed build
regardless of P2/A1/time variations. Equipment and augment context denotes
co-occurrence, not an inferred common clock. 220007 appears only as reconstruction
evidence, nowhere in the selectable payload. All enum/field/rate names shown
above are the actual serializer's names.

## Mongo envelope and regeneration

The existing owner uses the structured `champion_builds` envelope:

```text
_id / filterKey: normal filter-key encoding for champion + patch + CHERRY
buildVersion / lastUpdate: existing owner-managed metadata
build:
  current standard Build fields
  arena:
    schemaVersion: 2
    aggregationVersion: 3
    stats / positions / builds[] / cores[] / coverage
```

JSON and in-memory BSON round trips of the current Build.arena pass. There is
no support, adapter, migration or regression gate for superseded Arena aggregate
schemas. Discard affected obsolete Arena aggregates and regenerate them from
source matches at Phase 3 integration; keep raw matches/events. No production
data was deleted or regenerated here. Standard CHERRY output is not an Arena
aggregate; only the current Arena payload is served through the future Arena
read path. Do not change the standard generator or rewrite unrelated data.

`Filter.championBuild(champion, patch, CHERRY)` supplies a neutral lane/rank/region/
opponent/duo/period scope without fetching defaults. Catalog and completeness
come from the future loading owner; the accumulator owns no persistence/cache/
scheduler calls. Operational loading/writing/read dispatch is not connected yet.
Use one full document per champion/patch/queue in the same collection, measuring
BSON/headroom/cardinality/heap before rollout. Splitting cores remains a future
response to measured problems, never a reason to discard one-game steps now.
