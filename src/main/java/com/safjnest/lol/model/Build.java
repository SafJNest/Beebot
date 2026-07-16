package com.safjnest.lol.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.lol.build.RuneSignature;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.utils.KryoUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete build aggregate. The order of every option list is
 * presentation-neutral; callers choose the option they want to use.
 */
public record Build(
    Filter filter,
    int games,
    int wins,
    double winrate,
    List<CoreBuildOption> coreBuilds,
    List<Option> coreItems,
    @JsonProperty("starters") List<Option> starterOptions,
    @JsonProperty("boots") List<Option> bootOptions,
    @JsonProperty("supportItems") List<Option> supportItemOptions,
    @JsonProperty("slots") List<List<Option>> itemSlots,
    @JsonProperty("runes") List<RuneOption> runeOptions,
    @JsonProperty("summonerSpells") List<Option> summonerSpellOptions,
    List<SkillOrderOption> skillOrders,
    @JsonProperty("prismatics") List<Option> prismaticOptions,
    @JsonProperty("augments") List<List<Option>> augmentOptions
) {

    public record Option(String id, int matches, int wins, double winrate, double pickrate) {
        public int itemId() {
            try { return Integer.parseInt(id); }
            catch (Exception ignored) {
                try {
                    List<Integer> spells = BuildUtils.parseDashList(id);
                    return spells.size() >= 2 ? spells.get(0) * 100 + spells.get(1) : 0;
                } catch (RuntimeException ignoredAgain) {
                    return 0;
                }
            }
        }

        public int getSpell1() {
            return itemId() / 100;
        }

        public int getSpell2() {
            return itemId() % 100;
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }

        public String prettyMatches() {
            return String.format("%d", matches);
        }
    }

    public record CoreBuildOption(String id, List<Integer> items, int matches, int wins,
                                  double winrate, double pickrate) {}

    public record RuneOption(String id, RuneSignature configuration, int matches, int wins,
                             double winrate, double pickrate) {}

    public record SkillOrderOption(String id, List<Integer> order, int matches, int wins,
                                   double winrate, double pickrate) {}

    /** Compatibility shape used by the Discord command and old callers. */
    public record SlotOption(int itemId, int matches, double winrate) {
        public int getSpell1() {
            return itemId / 100;
        }

        public int getSpell2() {
            return itemId % 100;
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }

        public String prettyMatches() {
            return String.format("%d", matches);
        }
    }

    /** Compatibility constructor for persisted callers and non-HTTP consumers. */
    public Build(
        Filter filter,
        List<Integer> starter,
        List<SlotOption> boots,
        List<SlotOption> suppItems,
        List<Integer> core,
        List<List<SlotOption>> slots,
        List<List<SlotOption>> prismatics,
        List<SlotOption> summonerSpells,
        List<SlotOption> augments,
        List<Integer> spellOrder,
        RuneSignature runes,
        int games,
        double winrate
    ) {
        this(
            filter,
            games,
            winsFor(games, winrate),
            winrate,
            core.isEmpty() ? List.of() : List.of(new CoreBuildOption(
                BuildUtils.joinInts(core), core, games, winsFor(games, winrate), winrate, 1
            )),
            toItemOptions(core, games, winsFor(games, winrate)),
            toItemOptions(starter, games, winsFor(games, winrate)),
            toSlotOptions(boots, games),
            toSlotOptions(suppItems, games),
            toOptionSlots(slots),
            runes == null ? List.of() : List.of(new RuneOption(
                runes.toKey(), runes, games, winsFor(games, winrate), winrate, 1
            )),
            toSlotOptions(summonerSpells, games),
            spellOrder.isEmpty() ? List.of() : List.of(new SkillOrderOption(
                BuildUtils.joinInts(spellOrder), spellOrder, games, winsFor(games, winrate), winrate, 1
            )),
            flattenOptions(prismatics),
            augments.isEmpty() ? List.of() : List.of(toSlotOptions(augments, games))
        );
    }

    public String encode() {
        return KryoUtils.encode(this);
    }

    public static Build decode(String b64) {
        try {
            Build build = KryoUtils.decode(b64, Build.class);
            if (build == null) return null;
            if (build.filter() != null && !(build.filter() instanceof Filter)) return null;
            if (build.coreBuilds() == null || build.coreItems() == null || build.starterOptions() == null
                    || build.bootOptions() == null || build.supportItemOptions() == null
                    || build.itemSlots() == null || build.runeOptions() == null
                    || build.summonerSpellOptions() == null || build.skillOrders() == null
                    || build.prismaticOptions() == null || build.augmentOptions() == null) return null;
            return build;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @JsonIgnore
    public List<Integer> starter() {
        return ids(starterOptions);
    }

    @JsonIgnore
    public List<SlotOption> boots() {
        return toLegacy(bootOptions);
    }

    @JsonIgnore
    public List<SlotOption> suppItems() {
        return toLegacy(supportItemOptions);
    }

    @JsonIgnore
    public List<Integer> core() {
        return ids(coreItems);
    }

    @JsonIgnore
    public List<List<SlotOption>> slots() {
        return toLegacySlots(itemSlots);
    }

    @JsonIgnore
    public List<List<SlotOption>> prismatics() {
        return prismaticOptions.isEmpty() ? List.of() : List.of(toLegacy(prismaticOptions));
    }

    @JsonIgnore
    public List<SlotOption> summonerSpells() {
        return toLegacy(summonerSpellOptions);
    }

    @JsonIgnore
    public List<SlotOption> augments() {
        List<SlotOption> result = new ArrayList<>();
        for (List<Option> slot : augmentOptions) result.addAll(toLegacy(slot));
        return result;
    }

    @JsonIgnore
    public List<Integer> spellOrder() {
        return skillOrders.isEmpty() ? List.of() : skillOrders.get(0).order();
    }

    @JsonIgnore
    public List<String> getSkillOrder() {
        List<String> result = new ArrayList<>();
        for (int value : spellOrder()) result.add(String.valueOf(value));
        while (result.size() < 18) result.add("0");
        return result;
    }

    @JsonIgnore
    public RuneSignature runes() {
        return runeOptions.isEmpty() ? null : runeOptions.get(0).configuration();
    }

    @JsonIgnore
    public int getKeystone() {
        return runes() == null ? 0 : runes().keystone();
    }

    @JsonIgnore
    public int getPrimaryTree() {
        return runes() == null ? 0 : runes().primaryTree();
    }

    @JsonIgnore
    public int getSecondaryTree() {
        return runes() == null ? 0 : runes().secondaryTree();
    }

    @JsonIgnore
    public List<Integer> getPrimaryRunes() {
        return runes() == null ? List.of() : runes().primaryRunes();
    }

    @JsonIgnore
    public List<Integer> getSecondaryRunes() {
        return runes() == null ? List.of() : runes().secondaryRunes();
    }

    @JsonIgnore
    public List<Integer> getStatShards() {
        return runes() == null ? List.of() : runes().statShards();
    }

    @JsonIgnore
    public int getOffense() {
        return shard(0);
    }

    @JsonIgnore
    public int getFlex() {
        return shard(1);
    }

    @JsonIgnore
    public int getDefense() {
        return shard(2);
    }

    public void print() {
        System.out.printf("=== ChampionBuild === games=%d wins=%d winrate=%.1f%%%n", games, wins, winrate * 100);
        System.out.println("coreBuilds=" + coreBuilds);
        System.out.println("coreItems=" + coreItems);
        System.out.println("starters=" + starterOptions);
        System.out.println("boots=" + bootOptions);
        System.out.println("supportItems=" + supportItemOptions);
        System.out.println("slots=" + itemSlots);
        System.out.println("runes=" + runeOptions);
        System.out.println("summonerSpells=" + summonerSpellOptions);
        System.out.println("skillOrders=" + skillOrders);
        System.out.println("prismatics=" + prismaticOptions);
        System.out.println("augments=" + augmentOptions);
    }

    private int shard(int index) {
        List<Integer> shards = getStatShards();
        return index < shards.size() ? shards.get(index) : 0;
    }

    private static int winsFor(int games, double winrate) {
        return (int) Math.round(games * winrate);
    }

    private static List<Option> toItemOptions(List<Integer> ids, int matches, int wins) {
        List<Option> result = new ArrayList<>();
        for (Integer id : ids) result.add(new Option(String.valueOf(id), matches, wins, rate(wins, matches), 1));
        return result;
    }

    private static List<Option> toSlotOptions(List<SlotOption> options, int fallbackMatches) {
        List<Option> result = new ArrayList<>();
        for (SlotOption option : options) {
            int matches = option.matches() > 0 ? option.matches() : fallbackMatches;
            int wins = winsFor(matches, option.winrate());
            result.add(new Option(String.valueOf(option.itemId()), matches, wins, option.winrate(), 0));
        }
        return result;
    }

    private static List<List<Option>> toOptionSlots(List<List<SlotOption>> slots) {
        List<List<Option>> result = new ArrayList<>();
        for (List<SlotOption> slot : slots) result.add(toSlotOptions(slot, 0));
        return result;
    }

    private static List<Option> flattenOptions(List<List<SlotOption>> slots) {
        List<Option> result = new ArrayList<>();
        for (List<SlotOption> slot : slots) result.addAll(toSlotOptions(slot, 0));
        return result;
    }

    private static List<Integer> ids(List<Option> options) {
        List<Integer> result = new ArrayList<>();
        for (Option option : options) result.add(option.itemId());
        return result;
    }

    private static List<SlotOption> toLegacy(List<Option> options) {
        List<SlotOption> result = new ArrayList<>();
        for (Option option : options) result.add(new SlotOption(option.itemId(), option.matches(), option.winrate()));
        return result;
    }

    private static List<List<SlotOption>> toLegacySlots(List<List<Option>> slots) {
        List<List<SlotOption>> result = new ArrayList<>();
        for (List<Option> slot : slots) result.add(toLegacy(slot));
        return result;
    }

    private static double rate(int wins, int matches) {
        return matches > 0 ? (double) wins / matches : 0;
    }
}
