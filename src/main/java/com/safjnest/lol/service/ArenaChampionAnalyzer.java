package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import com.safjnest.lol.arena.ArenaGameParser;
import com.safjnest.lol.arena.ArenaItemCatalog;
import com.safjnest.lol.arena.ParsedArenaGame;
import com.safjnest.lol.model.ArenaBuildData;
import com.safjnest.lol.model.ArenaBuildData.Option;
import com.safjnest.lol.model.ArenaBuildData.Core;
import com.safjnest.lol.model.ArenaBuildData.FullBuild;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.utils.PatchUtils;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class ArenaChampionAnalyzer {

    private static final Pattern MATCH_ID_PATTERN = Pattern.compile("[A-Z0-9]+_[0-9]+");

    private ArenaChampionAnalyzer() {}

    public static Accumulator accumulator(int championId, String patch, ArenaItemCatalog catalog) {
        return new Accumulator(championId, patch, catalog);
    }

    public static final class Accumulator {
        private final int championId;
        private final String patch;
        private final ArenaItemCatalog catalog;
        private final Map<String, Map<Integer, LongOpenHashSet>> matchesByPlatform = new HashMap<>();
        private final Set<String> oversizedMatchIds = new HashSet<>();
        private final StatsCounter overall = new StatsCounter();
        private final Int2ObjectOpenHashMap<StatsCounter> prismatics = new Int2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<StatsCounter> augments = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<CoreCounter> cores = new Long2ObjectOpenHashMap<>();
        private long matches;
        private long missingCoreGames;
        private long firstPrismaticFallbackGames;
        private long buildTimelineGames;

        private Accumulator(int championId, String patch, ArenaItemCatalog catalog) {
            if (championId <= 0 || patch == null || !patch.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)*"))
                throw new IllegalArgumentException("Champion and patch required");
            this.championId = championId;
            this.patch = patch;
            this.catalog = java.util.Objects.requireNonNull(catalog);
        }

        public boolean accept(Match match) {
            if (match == null || match.gameId == null || !MATCH_ID_PATTERN.matcher(match.gameId).matches())
                throw new IllegalArgumentException("Full match ID required");
            boolean patchMatches = patch.equals(PatchUtils.patchMajor(patch))
                ? patch.equals(PatchUtils.patchMajor(match.patch)) : patch.equals(match.patch);
            if (match.queue != GameQueueType.CHERRY || !patchMatches) return false;
            MatchIdentity matchId = matchIdentity(match.gameId);
            if (containsMatch(matchId)) throw new IllegalArgumentException("Duplicate match: " + match.gameId);
            validateParticipants(match.participants);
            List<ParsedArenaGame> games = new ArrayList<>();
            for (Participant participant : match.participants) if (participant.champion == championId)
                games.add(ArenaGameParser.parse(match, participant, catalog));
            if (games.isEmpty()) return false;
            rememberMatch(matchId);
            matches++;
            for (ParsedArenaGame game : games) add(game);
            return true;
        }

        public ArenaBuildData finish() {
            List<Core> coreValues = new ArrayList<>();
            for (var entry : cores.long2ObjectEntrySet())
                coreValues.add(entry.getValue().finish(entry.getLongKey(), overall.games));
            coreValues.sort(Comparator.comparingDouble(Core::winrate).reversed()
                .thenComparing(Comparator.comparingLong(Core::matches).reversed()).thenComparing(Core::id));
            return new ArenaBuildData(overall.games, overall.wins, coreValues, augmentValues(augments),
                prismaticValues(prismatics),
                new ArenaBuildData.Coverage(matches, overall.games - missingCoreGames, missingCoreGames,
                    firstPrismaticFallbackGames, buildTimelineGames));
        }

        public void clear() {
            matchesByPlatform.clear();
            oversizedMatchIds.clear();
            prismatics.clear();
            augments.clear();
            cores.clear();
            overall.games = 0;
            overall.wins = 0;
            matches = 0;
            missingCoreGames = 0;
            firstPrismaticFallbackGames = 0;
            buildTimelineGames = 0;
        }

        // ============================================================================

        private void add(ParsedArenaGame game) {
            overall.add(game.win());
            if (game.firstPrismaticFallback()) firstPrismaticFallbackGames++;
            if (game.buildTimeline()) buildTimelineGames++;

            for (int id : game.prismatics())
                prismatics.computeIfAbsent(id, ignored -> new StatsCounter()).add(game.win());

            for (ParsedArenaGame.Augment augment : game.augments()) {
                long key = longKey(augment.id(), augment.position());
                augments.computeIfAbsent(key, ignored -> new StatsCounter()).add(game.win());
            }

            if (game.bootsId() == null || game.firstPrismaticId() == null) {
                missingCoreGames++;
                return;
            }

            long key = longKey(game.bootsId(), game.firstPrismaticId());
            cores.computeIfAbsent(key, ignored -> new CoreCounter()).add(game);
        }

        private boolean containsMatch(MatchIdentity matchId) {
            if (matchId.overflowId() != null) return oversizedMatchIds.contains(matchId.overflowId());
            Map<Integer, LongOpenHashSet> idsByLength = matchesByPlatform.get(matchId.platform());
            LongOpenHashSet ids = idsByLength == null ? null : idsByLength.get(matchId.digits());
            return ids != null && ids.contains(matchId.numericId());
        }

        private void rememberMatch(MatchIdentity matchId) {
            if (matchId.overflowId() != null) {
                oversizedMatchIds.add(matchId.overflowId());
                return;
            }
            matchesByPlatform.computeIfAbsent(matchId.platform(), ignored -> new HashMap<>())
                .computeIfAbsent(matchId.digits(), ignored -> new LongOpenHashSet())
                .add(matchId.numericId());
        }

        private static void validateParticipants(List<Participant> participants) {
            if (participants == null) throw new IllegalArgumentException("Participants required");
            Set<Integer> ids = new HashSet<>();
            Set<String> puuids = new HashSet<>();
            for (Participant participant : participants) if (participant == null || participant.id <= 0
                    || participant.puuid == null || participant.puuid.isBlank()
                    || !ids.add(participant.id) || !puuids.add(participant.puuid))
                throw new IllegalArgumentException("Invalid or duplicate participant identity");
        }

    }

    private static long longKey(int high, int low) {
        return ((long) high << Integer.SIZE) | (low & 0xffff_ffffL);
    }

    private static int highInt(long key) {
        return (int) (key >>> Integer.SIZE);
    }

    private static int lowInt(long key) {
        return (int) key;
    }

    private static MatchIdentity matchIdentity(String value) {
        int separator = value.lastIndexOf('_');
        String platform = value.substring(0, separator);
        String numeric = value.substring(separator + 1);
        try {
            return new MatchIdentity(platform, numeric.length(), Long.parseUnsignedLong(numeric), null);
        } catch (NumberFormatException exception) {
            return new MatchIdentity(null, 0, 0, value);
        }
    }

    private record MatchIdentity(String platform, int digits, long numericId, String overflowId) {}

    private static final class CoreCounter {
        private final StatsCounter stats = new StatsCounter();
        private final Map<FullBuildKey, StatsCounter> builds = new HashMap<>();
        private final Int2ObjectOpenHashMap<StatsCounter> prismatics = new Int2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<StatsCounter> augments = new Long2ObjectOpenHashMap<>();

        private void add(ParsedArenaGame game) {
            stats.add(game.win());
            List<Integer> fullBuildItems = new ArrayList<>(game.finalItems());
            for (int id : game.consumables()) if (!game.prismatics().contains(id)) fullBuildItems.add(id);
            fullBuildItems.sort(Integer::compareTo);
            if (!fullBuildItems.isEmpty())
                builds.computeIfAbsent(new FullBuildKey(fullBuildItems), ignored -> new StatsCounter()).add(game.win());
            for (int itemId : game.prismatics())
                prismatics.computeIfAbsent(itemId, ignored -> new StatsCounter()).add(game.win());
            for (ParsedArenaGame.Augment augment : game.augments())
                augments.computeIfAbsent(longKey(augment.id(), augment.position()), ignored -> new StatsCounter())
                    .add(game.win());
        }

        private Core finish(long key, long totalGames) {
            List<FullBuild> values = new ArrayList<>();
            for (var entry : builds.entrySet())
                values.add(new FullBuild(entry.getKey().items(), entry.getValue().games, entry.getValue().wins));
            values.sort(Comparator.comparingDouble(FullBuild::winrate).reversed()
                .thenComparing(Comparator.comparingLong(FullBuild::matches).reversed())
                .thenComparing(value -> BuildUtils.joinInts(value.items())));
            List<Integer> items = List.of(highInt(key), lowInt(key));
            return new Core(BuildUtils.joinInts(items), items, stats.games, stats.wins,
                totalGames == 0 ? 0 : (double) stats.games / totalGames,
                values, augmentValues(augments), prismaticValues(prismatics));
        }
    }

    private static List<Option> prismaticValues(Int2ObjectOpenHashMap<StatsCounter> source) {
        return optionValues(source);
    }

    private static List<Option> optionValues(Int2ObjectOpenHashMap<StatsCounter> source) {
        List<Option> result = new ArrayList<>();
        for (var entry : source.int2ObjectEntrySet())
            result.add(new Option(entry.getIntKey(), entry.getValue().games, entry.getValue().wins));
        result.sort(optionOrder());
        return result;
    }

    private record FullBuildKey(List<Integer> items) {
        private FullBuildKey {
            items = List.copyOf(items);
        }
    }

    private static List<List<Option>> augmentValues(Long2ObjectOpenHashMap<StatsCounter> source) {
        List<List<Option>> result = new ArrayList<>();
        for (int position = 1; position <= ArenaBuildData.MAX_AUGMENTS; position++) {
            List<Option> values = new ArrayList<>();
            for (var entry : source.long2ObjectEntrySet()) if (lowInt(entry.getLongKey()) == position)
                values.add(new Option(highInt(entry.getLongKey()), entry.getValue().games, entry.getValue().wins));
            values.sort(optionOrder());
            result.add(values);
        }
        return result;
    }

    private static Comparator<Option> optionOrder() {
        return Comparator.comparingDouble(Option::winrate).reversed()
            .thenComparing(Comparator.comparingLong(Option::matches).reversed()).thenComparingInt(Option::id);
    }

    private static final class StatsCounter {
        private long games;
        private long wins;

        private void add(boolean win) {
            games++;
            if (win) wins++;
        }
    }
}
