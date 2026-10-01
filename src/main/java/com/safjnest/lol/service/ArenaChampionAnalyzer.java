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
import com.safjnest.lol.arena.ParsedArenaGame.Observation;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ArenaChampionStatistics;
import com.safjnest.lol.model.statistics.ArenaChampionStatistics.Distribution;
import com.safjnest.lol.model.statistics.StatisticalLeaf;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class ArenaChampionAnalyzer {

    private static final int SCHEMA_VERSION = 1;
    private static final int AGGREGATION_VERSION = 2;

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
        private final Map<Key, Group> global = new HashMap<>();
        private final Map<List<Integer>, Branch> branches = new HashMap<>();
        private final Map<String, Long> missing = new HashMap<>();
        private final Map<String, Long> ambiguous = new HashMap<>();
        private final Map<String, Long> rejected = new HashMap<>();

        private Accumulator(int championId, String patch, ArenaItemCatalog catalog) {
            if (championId <= 0 || !validPatch(patch)) throw new IllegalArgumentException("Champion and full patch required");
            this.championId = championId;
            this.patch = patch;
            this.catalog = java.util.Objects.requireNonNull(catalog);
        }

        public boolean accept(Match match) {
            return accept(match, true);
        }

        public boolean accept(Match match, boolean completeItemHistory) {
            if (match == null || match.gameId == null || !match.gameId.matches("[A-Z0-9]+_[0-9]+"))
                throw new IllegalArgumentException("Full match ID required");
            if (match.queue != GameQueueType.CHERRY || !patch.equals(match.patch)) return false;
            if (matches.contains(match.gameId)) throw new IllegalArgumentException("Duplicate match: " + match.gameId);
            validateParticipants(match.participants);
            List<ParsedArenaGame> games = new ArrayList<>();
            for (Participant participant : match.participants) {
                if (participant.champion == championId)
                    games.add(ArenaGameParser.parse(match, participant, catalog, completeItemHistory));
            }
            if (games.isEmpty()) return false;
            matches.add(match.gameId);
            for (ParsedArenaGame game : games) {
                overall.add(game);
                merge(missing, game.coverage().missing());
                merge(ambiguous, game.coverage().ambiguous());
                merge(rejected, game.coverage().rejected());
                addChoices(global, game);
                if (game.core() != null) {
                    Branch branch = branches.computeIfAbsent(game.core().itemIds(), ignored -> new Branch());
                    branch.stats.add(game);
                    addChoices(branch.choices, game);
                    addSlots(branch, game);
                }
            }
            return true;
        }

        public ArenaChampionStatistics finish() {
            List<Build> builds = new ArrayList<>();
            List<List<Integer>> keys = new ArrayList<>(branches.keySet());
            keys.sort(ArenaChampionAnalyzer::compareIds);
            long coreGames = 0;
            for (List<Integer> key : keys) {
                Branch branch = branches.get(key);
                coreGames += branch.stats.games;
                List<Build.Choice> entries = new ArrayList<>();
                for (int i = 0; i < key.size(); i++) {
                    Group group = branch.choices.get(new Key(Kind.ITEM, key.get(i)));
                    entries.add(group.positions.get(i + 1).finish(Kind.ITEM, key.get(i), i + 1, branch.stats.games));
                }
                StatisticalLeaf stats = branch.stats.finish();
                builds.add(Build.arena(new Build.Core(entries, stats), slots(branch, Kind.ITEM),
                    slots(branch, Kind.AUGMENT), List.of(new Build.Path(entries, stats))));
            }
            return new ArenaChampionStatistics(SCHEMA_VERSION, AGGREGATION_VERSION, championId, patch,
                overall.finish(), builds, distributions(global, Kind.BOOTS, overall.games),
                distributions(global, Kind.ITEM, overall.games), distributions(global, Kind.PRISMATIC, overall.games),
                distributions(global, Kind.AUGMENT, overall.games),
                new ArenaChampionStatistics.Coverage(matches.size(), overall.games, coreGames, missing, ambiguous, rejected));
        }
    }

    // ============================================================================

    private static boolean validPatch(String patch) {
        return patch != null && patch.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)*");
    }

    private static void validateParticipants(List<Participant> participants) {
        if (participants == null) throw new IllegalArgumentException("Participants required");
        Set<Integer> ids = new HashSet<>();
        Set<String> puuids = new HashSet<>();
        for (Participant participant : participants) {
            if (participant == null || participant.id <= 0 || participant.puuid == null || participant.puuid.isBlank()
                    || !ids.add(participant.id) || !puuids.add(participant.puuid))
                throw new IllegalArgumentException("Invalid or duplicate participant identity");
        }
    }

    private static void merge(Map<String, Long> target, Map<String, Long> source) {
        for (var entry : source.entrySet()) target.merge(entry.getKey(), entry.getValue(), Long::sum);
    }

    private static void addChoices(Map<Key, Group> target, ParsedArenaGame game) {
        Map<Key, List<Observation>> choices = new HashMap<>();
        for (Observation observation : game.choices())
            choices.computeIfAbsent(new Key(observation.kind(), observation.id()), ignored -> new ArrayList<>()).add(observation);
        for (var entry : choices.entrySet()) {
            Group group = target.computeIfAbsent(entry.getKey(), ignored -> new Group());
            Long earliest = null;
            boolean positioned = false;
            Set<Integer> positions = new HashSet<>();
            for (Observation choice : entry.getValue()) {
                Long time = choice.timestampMillis();
                if (time != null && (earliest == null || time < earliest)) earliest = time;
                if (choice.position() != null) {
                    positioned = true;
                    if (positions.add(choice.position()))
                        group.positions.computeIfAbsent(choice.position(), ignored -> new ChoiceStats()).add(game, time, true);
                }
            }
            group.overall.add(game, earliest, positioned);
        }
    }

    private static void addSlots(Branch branch, ParsedArenaGame game) {
        Set<SlotKey> seen = new HashSet<>();
        for (Observation choice : game.choices()) {
            if (choice.position() == null || (choice.kind() != Kind.ITEM && choice.kind() != Kind.AUGMENT)) continue;
            SlotKey key = new SlotKey(choice.kind(), choice.position());
            if (seen.add(key)) branch.slots.computeIfAbsent(key, ignored -> new Stats()).add(game);
        }
    }

    private static List<Build.Slot> slots(Branch branch, Kind kind) {
        List<SlotKey> keys = new ArrayList<>();
        for (SlotKey key : branch.slots.keySet()) if (key.kind == kind) keys.add(key);
        keys.sort(Comparator.comparingInt(SlotKey::position));
        List<Build.Slot> result = new ArrayList<>();
        for (SlotKey key : keys) {
            List<Build.Choice> options = new ArrayList<>();
            for (var entry : branch.choices.entrySet()) {
                if (entry.getKey().kind != kind) continue;
                ChoiceStats choice = entry.getValue().positions.get(key.position);
                if (choice != null) options.add(choice.finish(kind, entry.getKey().id, key.position, branch.stats.games));
            }
            options.sort(Comparator.comparingInt(Build.Choice::id));
            result.add(new Build.Slot(kind, key.position, branch.slots.get(key).finish(), options));
        }
        return List.copyOf(result);
    }

    private static List<Distribution> distributions(Map<Key, Group> source, Kind kind, long denominator) {
        List<Key> keys = new ArrayList<>();
        for (Key key : source.keySet()) if (key.kind == kind) keys.add(key);
        keys.sort(Comparator.comparingInt(Key::id));
        List<Distribution> result = new ArrayList<>();
        for (Key key : keys) {
            Group group = source.get(key);
            List<Integer> positions = new ArrayList<>(group.positions.keySet());
            positions.sort(Integer::compareTo);
            List<Build.Choice> options = new ArrayList<>();
            for (int position : positions) options.add(group.positions.get(position).finish(kind, key.id, position, denominator));
            result.add(new Distribution(group.overall.finish(kind, key.id, null, denominator), options));
        }
        return List.copyOf(result);
    }

    private static int compareIds(List<Integer> left, List<Integer> right) {
        for (int i = 0; i < Math.min(left.size(), right.size()); i++) {
            int compared = Integer.compare(left.get(i), right.get(i));
            if (compared != 0) return compared;
        }
        return Integer.compare(left.size(), right.size());
    }

    private record Key(Kind kind, int id) {}
    private record SlotKey(Kind kind, int position) {}

    private static final class Branch {
        private final Stats stats = new Stats();
        private final Map<Key, Group> choices = new HashMap<>();
        private final Map<SlotKey, Stats> slots = new HashMap<>();
    }

    private static final class Group {
        private final ChoiceStats overall = new ChoiceStats();
        private final Map<Integer, ChoiceStats> positions = new HashMap<>();
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

        private Build.Choice finish(Kind kind, int id, Integer position, long denominator) {
            return new Build.Choice(kind, id, position, stats.finish(), new Build.Timing(timeSum, timeCount),
                denominator, unpositionedGames);
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

        private StatisticalLeaf finish() {
            return new StatisticalLeaf(games, wins, placementSum, placementGames, placements);
        }
    }
}
