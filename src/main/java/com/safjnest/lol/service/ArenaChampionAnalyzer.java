package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.arena.ArenaGameParser;
import com.safjnest.lol.arena.ArenaItemCatalog;
import com.safjnest.lol.arena.ParsedArenaGame;
import com.safjnest.lol.arena.ParsedArenaGame.Equipment;
import com.safjnest.lol.arena.ParsedArenaGame.Observation;
import com.safjnest.lol.model.ArenaBuildData;
import com.safjnest.lol.model.ArenaBuildData.AnchorKind;
import com.safjnest.lol.model.ArenaBuildData.AugmentKey;
import com.safjnest.lol.model.ArenaBuildData.Context;
import com.safjnest.lol.model.ArenaBuildData.CoreKey;
import com.safjnest.lol.model.ArenaBuildData.EquipmentKey;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.StatisticalLeaf;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class ArenaChampionAnalyzer {

    private static final int SCHEMA_VERSION = 2;
    private static final int AGGREGATION_VERSION = 3;

    private ArenaChampionAnalyzer() {}

    public static Accumulator accumulator(int championId, String patch, ArenaItemCatalog catalog) {
        return new Accumulator(championId, patch, catalog);
    }

    public static final class Accumulator {
        private final int championId;
        private final String patch;
        private final ArenaItemCatalog catalog;
        private final Set<String> matches = new HashSet<>();
        private final Stats overall = new Stats();
        private final Choices global = new Choices();
        private final Map<ObservedKey, Observed> observed = new HashMap<>();
        private final Map<CoreKey, Core> cores = new HashMap<>();
        private final Map<String, Long> missing = new HashMap<>();
        private final Map<String, Long> ambiguous = new HashMap<>();
        private final Map<String, Long> rejected = new HashMap<>();
        private final Map<String, Long> resolutions = new HashMap<>();
        private final Map<String, Long> order = new HashMap<>();
        private final Map<String, Long> firstQuality = new HashMap<>();
        private long decisionGames;
        private long fallbackGames;
        private long observedGames;

        private Accumulator(int championId, String patch, ArenaItemCatalog catalog) {
            if (championId <= 0 || patch == null || !patch.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)*"))
                throw new IllegalArgumentException("Champion and full patch required");
            this.championId = championId;
            this.patch = patch;
            this.catalog = java.util.Objects.requireNonNull(catalog);
        }

        public boolean accept(Match match) { return accept(match, false); }

        public boolean accept(Match match, boolean completeItemHistory) {
            if (match == null || match.gameId == null || !match.gameId.matches("[A-Z0-9]+_[0-9]+"))
                throw new IllegalArgumentException("Full match ID required");
            if (match.queue != GameQueueType.CHERRY || !patch.equals(match.patch)) return false;
            if (matches.contains(match.gameId)) throw new IllegalArgumentException("Duplicate match: " + match.gameId);
            validateParticipants(match.participants);
            List<ParsedArenaGame> games = new ArrayList<>();
            for (Participant participant : match.participants) if (participant.champion == championId)
                games.add(ArenaGameParser.parse(match, participant, catalog, completeItemHistory));
            if (games.isEmpty()) return false;
            matches.add(match.gameId);
            for (ParsedArenaGame game : games) {
                overall.add(game);
                global.add(game.choices(), game);
                merge(missing, game.coverage().missing());
                merge(ambiguous, game.coverage().ambiguous());
                merge(rejected, game.coverage().rejected());
                resolutions.merge(game.firstPrismatic().type().name(), 1L, Long::sum);
                order.merge(game.orderQuality().name(), 1L, Long::sum);
                firstQuality.merge(game.firstPrismaticId() == null ? "UNRESOLVED"
                    : game.firstPrismaticExact() ? "EXACT" : "FALLBACK", 1L, Long::sum);
                if (game.boots() == null) continue;
                for (var augment : game.augments()) if (augment.position() == 1) {
                    addCore(new CoreKey(game.boots(), AnchorKind.AUGMENT, augment.id()), game);
                    decisionGames++;
                    break;
                }
                if (game.firstPrismaticId() != null) {
                    addCore(new CoreKey(game.boots(), AnchorKind.PRISMATIC, game.firstPrismaticId()), game);
                    fallbackGames++;
                    if (game.equipmentComplete()) {
                        List<Integer> legendary = new ArrayList<>();
                        for (Equipment item : game.equipment()) if (item.kind() == Kind.ITEM) legendary.add(item.id());
                        ObservedKey key = new ObservedKey(game.boots(), game.firstPrismaticId(), List.copyOf(legendary));
                        Observed build = observed.computeIfAbsent(key, ignored -> new Observed());
                        build.stats.add(game);
                        build.quality.merge(game.orderQuality().name(), 1L, Long::sum);
                        observedGames++;
                    }
                }
            }
            return true;
        }

        public Build finish() {
            List<ArenaBuildData.ObservedBuild> builds = new ArrayList<>();
            List<ObservedKey> buildKeys = new ArrayList<>(observed.keySet());
            buildKeys.sort(Comparator.comparingInt(ObservedKey::bootsId).thenComparingInt(ObservedKey::firstPrismaticId)
                .thenComparing(ObservedKey::legendaryIds, ArenaChampionAnalyzer::compareIds));
            for (ObservedKey key : buildKeys) {
                Observed value = observed.get(key);
                builds.add(new ArenaBuildData.ObservedBuild(key.bootsId, key.firstPrismaticId, key.legendaryIds,
                    value.stats.finish(), overall.games, value.quality));
            }
            List<ArenaBuildData.Core> coreValues = new ArrayList<>();
            List<CoreKey> coreKeys = new ArrayList<>(cores.keySet());
            coreKeys.sort(Comparator.comparingInt(CoreKey::bootsId).thenComparing(CoreKey::anchorKind)
                .thenComparingInt(CoreKey::anchorId));
            for (CoreKey key : coreKeys) {
                Core value = cores.get(key);
                List<ArenaBuildData.Step> steps = new ArrayList<>();
                List<Context> contexts = new ArrayList<>(value.steps.keySet());
                contexts.sort(ArenaChampionAnalyzer::compareContext);
                for (Context context : contexts) {
                    Step step = value.steps.get(context);
                    steps.add(new ArenaBuildData.Step(context, step.stats.finish(),
                        new ArenaBuildData.Choices(step.choices.options(Kind.ITEM, step.stats.games, false),
                            step.choices.options(Kind.PRISMATIC, step.stats.games, false),
                            step.choices.slots(Kind.AUGMENT, step.stats.games))));
                }
                coreValues.add(new ArenaBuildData.Core(key, value.stats.finish(), overall.games, steps));
            }
            ArenaBuildData data = new ArenaBuildData(SCHEMA_VERSION, AGGREGATION_VERSION, overall.finish(),
                new ArenaBuildData.Positions(global.options(Kind.BOOTS, overall.games, true),
                    global.slots(Kind.AUGMENT, overall.games), global.slots(Kind.PRISMATIC, overall.games),
                    global.slots(Kind.ITEM, overall.games), global.membership(overall.games),
                    global.unpositioned(overall.games)),
                builds, coreValues, new ArenaBuildData.Coverage(matches.size(), overall.games, decisionGames,
                    fallbackGames, observedGames, missing, ambiguous, rejected, resolutions, order, firstQuality));
            Filter filter = Filter.championBuild(championId, patch, GameQueueType.CHERRY);
            return Build.arena(filter, data);
        }

        private void addCore(CoreKey key, ParsedArenaGame game) {
            Core core = cores.computeIfAbsent(key, ignored -> new Core());
            core.stats.add(game);
            List<Equipment> equipment = game.equipmentComplete() ? game.equipment() : List.of();
            int start = 0;
            if (key.anchorKind() == AnchorKind.PRISMATIC) {
                List<Equipment> prefix = new ArrayList<>();
                for (Equipment item : game.equipment()) {
                    prefix.add(item);
                    if (item.id() == key.anchorId() && item.kind() == Kind.PRISMATIC) break;
                }
                if (!game.equipmentComplete()) {
                    prefix.clear();
                    for (Equipment item : game.equipment()) if (item.id() == key.anchorId() && item.kind() == Kind.PRISMATIC) {
                        prefix.add(item);
                        break;
                    }
                    equipment = List.copyOf(prefix);
                }
                start = prefix.size();
            }
            List<AugmentKey> augments = new ArrayList<>();
            for (var augment : game.augments()) augments.add(new AugmentKey(augment.id(), augment.position()));
            int augmentStart = key.anchorKind() == AnchorKind.AUGMENT ? 1 : 0;
            Set<Context> seen = new HashSet<>();
            for (int count = start; count <= equipment.size(); count++) {
                List<EquipmentKey> items = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Equipment item = equipment.get(i);
                    items.add(new EquipmentKey(item.kind(), item.id(), item.position(), item.typePosition()));
                }
                for (int augmentCount = augmentStart; augmentCount <= augments.size(); augmentCount++) {
                    Context context = new Context(key.bootsId(), items, augments.subList(0, augmentCount));
                    if (!seen.add(context)) continue;
                    Step step = core.steps.computeIfAbsent(context, ignored -> new Step());
                    step.stats.add(game);
                    List<Observation> continuations = new ArrayList<>();
                    Set<Kind> types = new HashSet<>();
                    if (game.equipmentComplete()) for (int i = count; i < equipment.size(); i++) {
                        Equipment item = equipment.get(i);
                        if (item.typePosition() != null && types.add(item.kind()))
                            continuations.add(new Observation(item.kind(), item.id(), item.typePosition(), item.timestampMillis()));
                    }
                    else if (key.anchorKind() == AnchorKind.AUGMENT && game.firstPrismaticId() != null) {
                        for (Equipment item : game.equipment()) if (item.id() == game.firstPrismaticId() && item.kind() == Kind.PRISMATIC)
                            continuations.add(new Observation(item.kind(), item.id(), 1, item.timestampMillis()));
                    }
                    for (int i = augmentCount; i < augments.size(); i++) {
                        AugmentKey augment = augments.get(i);
                        continuations.add(new Observation(Kind.AUGMENT, augment.id(), augment.position(), null));
                    }
                    step.choices.add(continuations, game);
                }
            }
        }
    }

    // ============================================================================

    private static void validateParticipants(List<Participant> participants) {
        if (participants == null) throw new IllegalArgumentException("Participants required");
        Set<Integer> ids = new HashSet<>();
        Set<String> puuids = new HashSet<>();
        for (Participant participant : participants) if (participant == null || participant.id <= 0
                || participant.puuid == null || participant.puuid.isBlank()
                || !ids.add(participant.id) || !puuids.add(participant.puuid))
            throw new IllegalArgumentException("Invalid or duplicate participant identity");
    }

    private static void merge(Map<String, Long> target, Map<String, Long> source) {
        for (var entry : source.entrySet()) target.merge(entry.getKey(), entry.getValue(), Long::sum);
    }

    private static int compareIds(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            int compare = Integer.compare(a.get(i), b.get(i));
            if (compare != 0) return compare;
        }
        return Integer.compare(a.size(), b.size());
    }

    private static int compareContext(Context a, Context b) {
        int compare = Integer.compare(a.equipment().size(), b.equipment().size());
        if (compare != 0) return compare;
        for (int i = 0; i < a.equipment().size(); i++) {
            EquipmentKey x = a.equipment().get(i), y = b.equipment().get(i);
            compare = x.kind().compareTo(y.kind());
            if (compare == 0) compare = Integer.compare(x.id(), y.id());
            if (compare == 0) compare = compareNullable(x.position(), y.position());
            if (compare == 0) compare = compareNullable(x.typePosition(), y.typePosition());
            if (compare != 0) return compare;
        }
        compare = Integer.compare(a.augments().size(), b.augments().size());
        if (compare != 0) return compare;
        for (int i = 0; i < a.augments().size(); i++) {
            AugmentKey x = a.augments().get(i), y = b.augments().get(i);
            compare = Integer.compare(x.position(), y.position());
            if (compare == 0) compare = Integer.compare(x.id(), y.id());
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static int compareNullable(Integer a, Integer b) { return Comparator.nullsLast(Integer::compareTo).compare(a, b); }

    private record ObservedKey(int bootsId, int firstPrismaticId, List<Integer> legendaryIds) {}
    private record ChoiceKey(Kind kind, int id, Integer position) {}
    private record SlotKey(Kind kind, int position) {}

    private static final class Core {
        private final Stats stats = new Stats();
        private final Map<Context, Step> steps = new HashMap<>();
    }

    private static final class Step {
        private final Stats stats = new Stats();
        private final Choices choices = new Choices();
    }

    private static final class Observed {
        private final Stats stats = new Stats();
        private final Map<String, Long> quality = new HashMap<>();
    }

    private static final class Choices {
        private final Map<ChoiceKey, ChoiceStats> values = new HashMap<>();
        private final Map<ChoiceKey, ChoiceStats> unknown = new HashMap<>();
        private final Map<SlotKey, Stats> populations = new HashMap<>();

        private void add(List<Observation> observations, ParsedArenaGame game) {
            Map<ChoiceKey, List<Observation>> groups = new HashMap<>();
            Set<ChoiceKey> seen = new HashSet<>();
            Set<SlotKey> slots = new HashSet<>();
            for (Observation observation : observations) {
                ChoiceKey overall = new ChoiceKey(observation.kind(), observation.id(), null);
                groups.computeIfAbsent(overall, ignored -> new ArrayList<>()).add(observation);
                if (observation.position() == null) continue;
                ChoiceKey key = new ChoiceKey(observation.kind(), observation.id(), observation.position());
                if (seen.add(key)) values.computeIfAbsent(key, ignored -> new ChoiceStats()).add(game, observation.timestampMillis(), true);
                SlotKey slot = new SlotKey(observation.kind(), observation.position());
                if (slots.add(slot)) populations.computeIfAbsent(slot, ignored -> new Stats()).add(game);
            }
            for (var entry : groups.entrySet()) {
                Long earliest = null;
                boolean positioned = entry.getKey().kind == Kind.BOOTS;
                for (Observation choice : entry.getValue()) {
                    if (choice.timestampMillis() != null && (earliest == null || choice.timestampMillis() < earliest))
                        earliest = choice.timestampMillis();
                    positioned |= choice.position() != null;
                }
                values.computeIfAbsent(entry.getKey(), ignored -> new ChoiceStats()).add(game, earliest, positioned);
                if (!positioned) unknown.computeIfAbsent(entry.getKey(), ignored -> new ChoiceStats()).add(game, earliest, false);
            }
        }

        private List<Build.Choice> options(Kind kind, long denominator, boolean overall) {
            List<ChoiceKey> keys = new ArrayList<>();
            for (ChoiceKey key : values.keySet()) if (key.kind == kind && (key.position == null) == overall) keys.add(key);
            keys.sort(Comparator.comparingInt(ChoiceKey::id).thenComparing(ChoiceKey::position, Comparator.nullsLast(Integer::compareTo)));
            List<Build.Choice> result = new ArrayList<>();
            for (ChoiceKey key : keys) result.add(values.get(key).finish(key, denominator));
            return List.copyOf(result);
        }

        private List<Build.Slot> slots(Kind kind, long denominator) {
            List<SlotKey> keys = new ArrayList<>();
            for (SlotKey key : populations.keySet()) if (key.kind == kind) keys.add(key);
            keys.sort(Comparator.comparingInt(SlotKey::position));
            List<Build.Slot> result = new ArrayList<>();
            List<Build.Choice> options = options(kind, denominator, false);
            for (SlotKey key : keys) {
                List<Build.Choice> selected = new ArrayList<>();
                for (Build.Choice choice : options) if (choice.position() == key.position) selected.add(choice);
                result.add(new Build.Slot(kind, key.position, populations.get(key).finish(), selected));
            }
            return List.copyOf(result);
        }

        private List<Build.Choice> membership(long denominator) {
            List<Build.Choice> result = new ArrayList<>();
            for (Kind kind : List.of(Kind.ITEM, Kind.PRISMATIC, Kind.AUGMENT)) result.addAll(options(kind, denominator, true));
            return List.copyOf(result);
        }

        private List<Build.Choice> unpositioned(long denominator) {
            List<ChoiceKey> keys = new ArrayList<>(unknown.keySet());
            keys.sort(Comparator.comparing(ChoiceKey::kind).thenComparingInt(ChoiceKey::id));
            List<Build.Choice> result = new ArrayList<>();
            for (ChoiceKey key : keys) result.add(unknown.get(key).finish(key, denominator));
            return List.copyOf(result);
        }
    }

    private static final class ChoiceStats {
        private final Stats stats = new Stats();
        private long timeSum;
        private long timeCount;
        private long unpositionedGames;

        private void add(ParsedArenaGame game, Long timestamp, boolean positioned) {
            stats.add(game);
            if (timestamp != null) { timeSum += timestamp; timeCount++; }
            if (!positioned) unpositionedGames++;
        }

        private Build.Choice finish(ChoiceKey key, long denominator) {
            return new Build.Choice(key.kind, key.id, key.position, stats.finish(),
                new Build.Timing(timeSum, timeCount), denominator, unpositionedGames);
        }
    }

    private static final class Stats {
        private long games;
        private long wins;
        private long placementSum;
        private long placementGames;
        private final Map<Integer, Long> placements = new HashMap<>();

        private void add(ParsedArenaGame game) {
            games++;
            if (game.win()) wins++;
            Integer placement = game.subTeamPlacement();
            if (placement != null && placement >= 1 && placement <= 8) {
                placementSum += placement;
                placementGames++;
                placements.merge(placement, 1L, Long::sum);
            }
        }

        private StatisticalLeaf finish() { return new StatisticalLeaf(games, wins, placementSum, placementGames, placements); }
    }
}
