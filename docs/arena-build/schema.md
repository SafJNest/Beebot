# Arena Java/JSON schema — Phase 3

ArenaBuildData is the pure accumulator result, stored only at `build.arena`.
The combined Build still contains the original standard fields. The unsafe
Build.arena factory was removed; Arena does not return an empty standard build.
SchemaVersion=3 increments the previous schema=2 for semantic JSON identities;
aggregationVersion=3 preserves the statistical semantics. No compatibility
reader/conversion for obsolete Arena shapes is added.

## Java shape

Canonical source: [ArenaBuildData.java](../../src/main/java/com/safjnest/lol/model/ArenaBuildData.java).
Shared StatisticalLeaf and Build.Timing remain unchanged. Internal `kind()/id()`
accessors derive identity from exactly one semantic field and are JsonIgnore.
Nullable position/time facts remain explicit. Core anchors and item identity
have one-of guards; immutable collections detach results from the accumulator.

```java
public record ArenaBuildData(int schemaVersion, int aggregationVersion, StatisticalLeaf stats,
        Positions positions, List<ObservedBuild> builds, List<Core> cores, Coverage coverage) {

    public static final int SCHEMA_VERSION = 3;
    public static final int AGGREGATION_VERSION = 3;

    public ArenaBuildData {
        builds = List.copyOf(builds);
        cores = List.copyOf(cores);
    }

    public record Positions(List<Choice> boots, List<Slot> augments,
            List<Slot> prismatics, @JsonProperty("items") List<Slot> legendaryItems,
            List<Choice> membership, List<Choice> unpositioned) {
        public Positions {
            boots = List.copyOf(boots);
            augments = List.copyOf(augments);
            prismatics = List.copyOf(prismatics);
            legendaryItems = List.copyOf(legendaryItems);
            membership = List.copyOf(membership);
            unpositioned = List.copyOf(unpositioned);
        }
    }

    public record ObservedBuild(@JsonProperty("boots") int bootsId,
            @JsonProperty("prismatic") int firstPrismaticId, @JsonProperty("items") List<Integer> legendaryIds,
            StatisticalLeaf stats, long denominator, Map<String, Long> orderQuality) {
        public ObservedBuild {
            legendaryIds = List.copyOf(legendaryIds);
            orderQuality = Map.copyOf(orderQuality);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public enum AnchorKind { AUGMENT, PRISMATIC }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CoreKey(@JsonProperty("boots") int bootsId, Integer augment, Integer prismatic) {
        public CoreKey {
            if (bootsId <= 0 || (augment == null) == (prismatic == null)
                    || augment != null && augment <= 0 || prismatic != null && prismatic <= 0)
                throw new IllegalArgumentException("Boots and one core anchor required");
        }

        public CoreKey(int bootsId, AnchorKind kind, int id) {
            this(bootsId, kind == AnchorKind.AUGMENT ? id : null, kind == AnchorKind.PRISMATIC ? id : null);
        }

        @JsonIgnore
        public AnchorKind anchorKind() { return augment != null ? AnchorKind.AUGMENT : AnchorKind.PRISMATIC; }

        @JsonIgnore
        public int anchorId() { return augment != null ? augment : prismatic; }
    }

    public record Core(@JsonProperty("core") CoreKey key, StatisticalLeaf stats, long denominator, List<Step> steps) {
        public Core { steps = List.copyOf(steps); }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public record Step(Context context, StatisticalLeaf stats, Choices choices) {}

    public record Context(@JsonProperty("boots") int bootsId, List<EquipmentKey> equipment, List<AugmentKey> augments) {
        public Context {
            equipment = List.copyOf(equipment);
            augments = List.copyOf(augments);
        }
    }

    public record EquipmentKey(@JsonInclude(JsonInclude.Include.NON_NULL) Integer item,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer prismatic, Integer position, Integer typePosition) {
        public EquipmentKey {
            if ((item == null) == (prismatic == null) || item != null && item <= 0 || prismatic != null && prismatic <= 0)
                throw new IllegalArgumentException("One equipment identity required");
        }

        public EquipmentKey(Build.Kind kind, int id, Integer position, Integer typePosition) {
            this(kind == Build.Kind.ITEM ? id : null, kind == Build.Kind.PRISMATIC ? id : null, position, typePosition);
        }

        @JsonIgnore
        public Build.Kind kind() { return item != null ? Build.Kind.ITEM : Build.Kind.PRISMATIC; }

        @JsonIgnore
        public int id() { return item != null ? item : prismatic; }
    }

    public record AugmentKey(int augment, int position) {
        @JsonIgnore
        public int id() { return augment; }
    }

    public record Choices(@JsonProperty("items") List<Choice> legendary, List<Choice> prismatics,
            List<Slot> augments) {
        public Choices {
            legendary = List.copyOf(legendary);
            prismatics = List.copyOf(prismatics);
            augments = List.copyOf(augments);
        }
    }

    public record Choice(@JsonInclude(JsonInclude.Include.NON_NULL) Integer item,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer boots,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer prismatic,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer augment, Integer position,
            StatisticalLeaf stats, Build.Timing timing, long denominator, long unpositionedGames) {
        public Choice {
            int identities = (item == null ? 0 : 1) + (boots == null ? 0 : 1)
                + (prismatic == null ? 0 : 1) + (augment == null ? 0 : 1);
            if (identities != 1 || item != null && item <= 0 || boots != null && boots <= 0
                    || prismatic != null && prismatic <= 0 || augment != null && augment <= 0)
                throw new IllegalArgumentException("One choice identity required");
        }

        public static Choice of(Build.Kind kind, int id, Integer position, StatisticalLeaf stats,
                Build.Timing timing, long denominator, long unpositionedGames) {
            return new Choice(kind == Build.Kind.ITEM ? id : null, kind == Build.Kind.BOOTS ? id : null,
                kind == Build.Kind.PRISMATIC ? id : null, kind == Build.Kind.AUGMENT ? id : null,
                position, stats, timing, denominator, unpositionedGames);
        }

        @JsonIgnore
        public Build.Kind kind() {
            if (item != null) return Build.Kind.ITEM;
            if (boots != null) return Build.Kind.BOOTS;
            return prismatic != null ? Build.Kind.PRISMATIC : Build.Kind.AUGMENT;
        }

        @JsonIgnore
        public int id() {
            if (item != null) return item;
            if (boots != null) return boots;
            return prismatic != null ? prismatic : augment;
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public double pickrate() { return denominator == 0 ? 0 : (double) stats.games() / denominator; }
    }

    public record Slot(int position, StatisticalLeaf population, List<Choice> options) {
        public Slot { options = List.copyOf(options); }
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

## Semantic JSON example

The following excerpt is selected from actual JsonCodec output for two synthetic
matches (one timed source and one fallback, with augment gaps). It is an offline
serializer fixture, not an endpoint or live Mongo response. Lists below show only
one position/membership option and one core/root step for readability; the actual
aggregate retains all 2 cores and 35 observed steps without pruning.
Stats/denominators in this excerpt are unmodified. Equipment remains one ordered
sequence with semantic item/prismatic properties plus general/type positions;
augments form a separate sequence. Generic kind/id keys never occur in Arena JSON.

```json
{
  "schemaVersion": 3,
  "aggregationVersion": 3,
  "stats": {
    "games": 2,
    "wins": 1,
    "placementSum": 5,
    "placementGames": 2,
    "placements": {
      "2": 1,
      "3": 1
    },
    "winrate": 0.5,
    "averagePlacement": 2.5
  },
  "builds": [
    {
      "boots": 3006,
      "prismatic": 447001,
      "items": [
        4001,
        4003
      ],
      "stats": {
        "games": 2,
        "wins": 1,
        "placementSum": 5,
        "placementGames": 2,
        "placements": {
          "2": 1,
          "3": 1
        },
        "winrate": 0.5,
        "averagePlacement": 2.5
      },
      "denominator": 2,
      "orderQuality": {
        "FALLBACK": 1,
        "EXACT": 1
      },
      "pickrate": 1.0
    }
  ],
  "coverage": {
    "matches": 2,
    "participantGames": 2,
    "decisionCoreGames": 1,
    "fallbackCoreGames": 2,
    "observedBuildGames": 2,
    "missing": {
      "AUGMENT_ID_INVALID_OR_ABSENT": 2,
      "EQUIPMENT_TIME_ABSENT": 3,
      "ITEM_HISTORY_PARTIAL": 1
    },
    "ambiguous": {
      "FIRST_PRISMATIC_TOOLTIP_FALLBACK": 2
    },
    "rejected": {},
    "firstPrismaticResolutionTypes": {
      "TOOLTIP_FALLBACK": 2
    },
    "equipmentOrder": {
      "FALLBACK": 1,
      "EXACT": 1
    },
    "firstPrismaticQuality": {
      "FALLBACK": 1,
      "EXACT": 1
    }
  },
  "positions": {
    "boots": [
      {
        "boots": 3006,
        "position": null,
        "stats": {
          "games": 2,
          "wins": 1,
          "placementSum": 5,
          "placementGames": 2,
          "placements": {
            "2": 1,
            "3": 1
          },
          "winrate": 0.5,
          "averagePlacement": 2.5
        },
        "timing": {
          "timeSumMillis": 0,
          "timeCount": 0,
          "averageTimeMillis": null
        },
        "denominator": 2,
        "unpositionedGames": 0,
        "pickrate": 1.0
      }
    ],
    "augments": [
      {
        "position": 1,
        "population": {
          "games": 1,
          "wins": 1,
          "placementSum": 3,
          "placementGames": 1,
          "placements": {
            "3": 1
          },
          "winrate": 1.0,
          "averagePlacement": 3.0
        },
        "options": [
          {
            "augment": 11,
            "position": 1,
            "stats": {
              "games": 1,
              "wins": 1,
              "placementSum": 3,
              "placementGames": 1,
              "placements": {
                "3": 1
              },
              "winrate": 1.0,
              "averagePlacement": 3.0
            },
            "timing": {
              "timeSumMillis": 0,
              "timeCount": 0,
              "averageTimeMillis": null
            },
            "denominator": 2,
            "unpositionedGames": 0,
            "pickrate": 0.5
          }
        ]
      }
    ],
    "prismatics": [
      {
        "position": 1,
        "population": {
          "games": 2,
          "wins": 1,
          "placementSum": 5,
          "placementGames": 2,
          "placements": {
            "2": 1,
            "3": 1
          },
          "winrate": 0.5,
          "averagePlacement": 2.5
        },
        "options": [
          {
            "prismatic": 447001,
            "position": 1,
            "stats": {
              "games": 2,
              "wins": 1,
              "placementSum": 5,
              "placementGames": 2,
              "placements": {
                "2": 1,
                "3": 1
              },
              "winrate": 0.5,
              "averagePlacement": 2.5
            },
            "timing": {
              "timeSumMillis": 1000,
              "timeCount": 1,
              "averageTimeMillis": 1000.0
            },
            "denominator": 2,
            "unpositionedGames": 0,
            "pickrate": 1.0
          }
        ]
      }
    ],
    "items": [
      {
        "position": 1,
        "population": {
          "games": 2,
          "wins": 1,
          "placementSum": 5,
          "placementGames": 2,
          "placements": {
            "2": 1,
            "3": 1
          },
          "winrate": 0.5,
          "averagePlacement": 2.5
        },
        "options": [
          {
            "item": 4001,
            "position": 1,
            "stats": {
              "games": 2,
              "wins": 1,
              "placementSum": 5,
              "placementGames": 2,
              "placements": {
                "2": 1,
                "3": 1
              },
              "winrate": 0.5,
              "averagePlacement": 2.5
            },
            "timing": {
              "timeSumMillis": 2000,
              "timeCount": 1,
              "averageTimeMillis": 2000.0
            },
            "denominator": 2,
            "unpositionedGames": 0,
            "pickrate": 1.0
          }
        ]
      }
    ],
    "membership": [
      {
        "item": 4001,
        "position": null,
        "stats": {
          "games": 2,
          "wins": 1,
          "placementSum": 5,
          "placementGames": 2,
          "placements": {
            "2": 1,
            "3": 1
          },
          "winrate": 0.5,
          "averagePlacement": 2.5
        },
        "timing": {
          "timeSumMillis": 2000,
          "timeCount": 1,
          "averageTimeMillis": 2000.0
        },
        "denominator": 2,
        "unpositionedGames": 0,
        "pickrate": 1.0
      }
    ],
    "unpositioned": []
  },
  "cores": [
    {
      "core": {
        "boots": 3006,
        "augment": 11
      },
      "stats": {
        "games": 1,
        "wins": 1,
        "placementSum": 3,
        "placementGames": 1,
        "placements": {
          "3": 1
        },
        "winrate": 1.0,
        "averagePlacement": 3.0
      },
      "denominator": 2,
      "steps": [
        {
          "context": {
            "boots": 3006,
            "equipment": [],
            "augments": [
              {
                "augment": 11,
                "position": 1
              }
            ]
          },
          "stats": {
            "games": 1,
            "wins": 1,
            "placementSum": 3,
            "placementGames": 1,
            "placements": {
              "3": 1
            },
            "winrate": 1.0,
            "averagePlacement": 3.0
          },
          "choices": {
            "items": [
              {
                "item": 4001,
                "position": 1,
                "stats": {
                  "games": 1,
                  "wins": 1,
                  "placementSum": 3,
                  "placementGames": 1,
                  "placements": {
                    "3": 1
                  },
                  "winrate": 1.0,
                  "averagePlacement": 3.0
                },
                "timing": {
                  "timeSumMillis": 2000,
                  "timeCount": 1,
                  "averageTimeMillis": 2000.0
                },
                "denominator": 1,
                "unpositionedGames": 0,
                "pickrate": 1.0
              }
            ],
            "prismatics": [
              {
                "prismatic": 447001,
                "position": 1,
                "stats": {
                  "games": 1,
                  "wins": 1,
                  "placementSum": 3,
                  "placementGames": 1,
                  "placements": {
                    "3": 1
                  },
                  "winrate": 1.0,
                  "averagePlacement": 3.0
                },
                "timing": {
                  "timeSumMillis": 1000,
                  "timeCount": 1,
                  "averageTimeMillis": 1000.0
                },
                "denominator": 1,
                "unpositionedGames": 0,
                "pickrate": 1.0
              }
            ],
            "augments": [
              {
                "position": 2,
                "population": {
                  "games": 1,
                  "wins": 1,
                  "placementSum": 3,
                  "placementGames": 1,
                  "placements": {
                    "3": 1
                  },
                  "winrate": 1.0,
                  "averagePlacement": 3.0
                },
                "options": [
                  {
                    "augment": 22,
                    "position": 2,
                    "stats": {
                      "games": 1,
                      "wins": 1,
                      "placementSum": 3,
                      "placementGames": 1,
                      "placements": {
                        "3": 1
                      },
                      "winrate": 1.0,
                      "averagePlacement": 3.0
                    },
                    "timing": {
                      "timeSumMillis": 0,
                      "timeCount": 0,
                      "averageTimeMillis": null
                    },
                    "denominator": 1,
                    "unpositionedGames": 0,
                    "pickrate": 1.0
                  }
                ]
              },
              {
                "position": 3,
                "population": {
                  "games": 1,
                  "wins": 1,
                  "placementSum": 3,
                  "placementGames": 1,
                  "placements": {
                    "3": 1
                  },
                  "winrate": 1.0,
                  "averagePlacement": 3.0
                },
                "options": [
                  {
                    "augment": 33,
                    "position": 3,
                    "stats": {
                      "games": 1,
                      "wins": 1,
                      "placementSum": 3,
                      "placementGames": 1,
                      "placements": {
                        "3": 1
                      },
                      "winrate": 1.0,
                      "averagePlacement": 3.0
                    },
                    "timing": {
                      "timeSumMillis": 0,
                      "timeCount": 0,
                      "averageTimeMillis": null
                    },
                    "denominator": 1,
                    "unpositionedGames": 0,
                    "pickrate": 1.0
                  }
                ]
              }
            ]
          }
        }
      ],
      "pickrate": 0.5
    }
  ]
}
```

## Persisted envelope and independent writes

`champion_builds[_id=filterKey]` contains standard root metadata, standard
`build.*` fields and optional `build.arena`. Standard writes set only root
standard metadata and individual standard build paths. Arena updates its entire
subtree atomically; it does not set `build`, standard root counts/version/date,
or change identity. filterKey is initialized on insert. Arena-first insertion
is not standard-ready; standard later creates its fields while retaining Arena.
No read-merge-replace, CAS or second document/collection/writer owner.

The standard reader strips Arena from the detached build projection before
JsonCodec deserialization: malformed/unsupported Arena does not alter standard
readability or the current HTTP/presentation. Internal Arena reads recognize
only current schema/aggregation versions. Both populations have their own counts;
root standard games/wins never become Arena participant-game totals.

Arena source is a bounded match/event left join (<=100), exact full patch and
complete participant identity projection. Missing timelines are delivered;
checksum/decoding errors fail explicitly. Catalog is immutable external input
provided by the internal caller for that exact patch. Complete item history
remains false unless source evidence proves it. No external I/O in accumulation.

Local guards encode Arena/update BSON before writing, leaving headroom; they do
not claim to measure the unknown existing standard subtree. The Mongo server's
atomic final-document size check remains authoritative and preserves the old
value on failure. Combined BSON/representative heap and live test-Mongo/explain
remain operational gates; no truncation/splitting is used to bypass them.
See [contract](contracts.md) and [phases](phases.md) for verification and limits.
