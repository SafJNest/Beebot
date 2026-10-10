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
import com.safjnest.lol.model.ArenaBuildData.AugmentOption;
import com.safjnest.lol.model.ArenaBuildData.Core;
import com.safjnest.lol.model.ArenaBuildData.ItemOption;
import com.safjnest.lol.model.ArenaBuildData.PrismaticOption;
import com.safjnest.lol.model.ArenaBuildData.Stats;
import com.safjnest.lol.model.Build.Kind;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
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
                throw new IllegalArgumentException("Champion and full patch required");
            this.championId = championId;
            this.patch = patch;
            this.catalog = java.util.Objects.requireNonNull(catalog);
        }

        public boolean accept(Match match) {
            if (match == null || match.gameId == null || !MATCH_ID_PATTERN.matcher(match.gameId).matches())
                throw new IllegalArgumentException("Full match ID required");
            if (match.queue != GameQueueType.CHERRY || !patch.equals(match.patch)) return false;
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
                coreValues.add(entry.getValue().finish(entry.getLongKey()));
            coreValues.sort(Comparator.comparingInt(Core::bootsId)
                .thenComparingInt(Core::firstPrismaticId));

            List<PrismaticOption> prismaticValues = new ArrayList<>();
            for (var entry : prismatics.int2ObjectEntrySet())
                prismaticValues.add(entry.getValue().prismatic(entry.getIntKey()));
            prismaticValues.sort(prismaticOrder());

            List<AugmentOption> augmentValues = new ArrayList<>();
            for (var entry : augments.long2ObjectEntrySet())
                augmentValues.add(entry.getValue().augment(entry.getLongKey()));
            augmentValues.sort(augmentOrder());

            return new ArenaBuildData(ArenaBuildData.SCHEMA_VERSION, ArenaBuildData.AGGREGATION_VERSION,
                overall.finish(), coreValues, prismaticValues, augmentValues,
                new ArenaBuildData.Coverage(matches, overall.games - missingCoreGames, missingCoreGames,
                    firstPrismaticFallbackGames, buildTimelineGames));
        }

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
            cores.computeIfAbsent(key, ignored -> new CoreCounter()).add(key, game);
        }

        // ============================================================================

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

        private static Comparator<PrismaticOption> prismaticOrder() {
            return Comparator.comparingDouble(PrismaticOption::winRate).reversed()
                .thenComparing(Comparator.comparingLong(PrismaticOption::games).reversed())
                .thenComparingInt(PrismaticOption::prismaticId);
        }

        private static Comparator<AugmentOption> augmentOrder() {
            return Comparator.comparingInt(AugmentOption::position)
                .thenComparing(Comparator.comparingDouble(AugmentOption::winRate).reversed())
                .thenComparing(Comparator.comparingLong(AugmentOption::games).reversed())
                .thenComparingInt(AugmentOption::augmentId);
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
        private final Long2ObjectOpenHashMap<StatsCounter> items = new Long2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<StatsCounter> prismatics = new Int2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<StatsCounter> augments = new Long2ObjectOpenHashMap<>();

        private void add(long key, ParsedArenaGame game) {
            stats.add(game.win());
            for (int index = 0; index < game.legendaryItems().size(); index++) {
                int itemId = game.legendaryItems().get(index);
                long itemKey = longKey(index + 1, itemId);
                items.computeIfAbsent(itemKey, ignored -> new StatsCounter()).add(game.win());
            }
            for (int itemId : game.prismatics()) if (itemId != lowInt(key))
                prismatics.computeIfAbsent(itemId, ignored -> new StatsCounter()).add(game.win());
            for (ParsedArenaGame.Augment augment : game.augments()) {
                long augmentKey = longKey(augment.id(), augment.position());
                augments.computeIfAbsent(augmentKey, ignored -> new StatsCounter()).add(game.win());
            }
        }

        private Core finish(long key) {
            List<ItemOption> itemValues = new ArrayList<>();
            for (var entry : items.long2ObjectEntrySet())
                itemValues.add(entry.getValue().item(entry.getLongKey()));
            itemValues.sort(Comparator.comparingInt(ItemOption::position)
                .thenComparingInt(ItemOption::itemId));

            List<PrismaticOption> prismaticValues = new ArrayList<>();
            for (var entry : prismatics.int2ObjectEntrySet())
                prismaticValues.add(entry.getValue().prismatic(entry.getIntKey()));
            prismaticValues.sort(Accumulator.prismaticOrder());

            List<AugmentOption> augmentValues = new ArrayList<>();
            for (var entry : augments.long2ObjectEntrySet())
                augmentValues.add(entry.getValue().augment(entry.getLongKey()));
            augmentValues.sort(Accumulator.augmentOrder());

            return new Core(highInt(key), lowInt(key), stats.finish(), itemValues, prismaticValues, augmentValues);
        }
    }

    private static final class StatsCounter {
        private long games;
        private long wins;

        private void add(boolean win) {
            games++;
            if (win) wins++;
        }

        private Stats finish() { return new Stats(games, wins); }
        private ItemOption item(long key) { return new ItemOption(highInt(key), lowInt(key), games, wins); }
        private PrismaticOption prismatic(int id) { return new PrismaticOption(id, games, wins); }
        private AugmentOption augment(long key) { return new AugmentOption(highInt(key), lowInt(key), games, wins); }
    }
}
