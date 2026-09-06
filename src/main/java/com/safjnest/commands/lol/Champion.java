package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.SlotOption;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.utils.BuildUtils;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Champion extends SlashCommand {

    private static final ChampionService CHAMPION_SERVICE = new ChampionService();

    public Champion() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "champion", "Champion Name", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "role", "Champion Role", true)
                .addChoice("Top", LaneType.TOP.name())
                .addChoice("Jungle", LaneType.JUNGLE.name())
                .addChoice("Mid", LaneType.MID.name())
                .addChoice("ADC", LaneType.BOT.name())
                .addChoice("Support", LaneType.UTILITY.name()),
            new OptionData(OptionType.STRING, "queue", "Queue", false)
                .addChoice("Ranked Solo/Duo", GameQueueType.TEAM_BUILDER_RANKED_SOLO.name())
                .addChoice("Ranked Flex", GameQueueType.RANKED_FLEX_SR.name())
                .addChoice("Draft Pick", GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5.name())
                .addChoice("ARAM", GameQueueType.ARAM.name())
                .addChoice("Arena", GameQueueType.CHERRY.name()),
            new OptionData(OptionType.STRING, "opponent", "Opponent Champion", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "duo", "Duo Champion", false).setAutoComplete(true),
            PatchUtils.getAsOptions(),
            LeagueShardUtils.getAsOptions(),
            TierDivisionUtils.getAsOptions(false)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();

        StaticChampion champion = getChampion(event.getOption("champion").getAsString());
        if (champion == null) {
            event.getHook().editOriginal("Cannot find the champion you are looking for.").queue();
            return;
        }

        Filter filter = readFilter(event, champion);
        ChampionStatistics stats = CHAMPION_SERVICE.getStatistics(filter);
        Build build = CHAMPION_SERVICE.getBuild(filter);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(getTitle(champion, filter), "https://github.com/SafJNest", ChampionUtils.getChampionProfilePic(champion.getId()));
        eb.setDescription(buildDescription(stats, filter));
        addMatchups(eb, stats, filter);
        addBuild(eb, build, filter);
        finishEmbed(eb, champion);

        event.getHook().editOriginalEmbeds(eb.build()).queue();
    }

    private Filter readFilter(SlashCommandEvent event, StaticChampion champion) {
        GameQueueType queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        if (event.getOption("queue") != null)
            queue = GameQueueType.valueOf(event.getOption("queue").getAsString());

        LaneType lane = null;
        if (event.getOption("role") != null && GameQueueTypeUtils.hasLane(queue))
            lane = LaneType.valueOf(event.getOption("role").getAsString());

        Filter filter = new Filter()
            .setChampion(champion.getId())
            .setLane(lane)
            .setQueue(queue);

        if (event.getOption("rank") != null) {
            String rank = event.getOption("rank").getAsString();
            filter.setRank(rank.equals("ALL") ? null : TierType.valueOf(rank));
        }
        if (event.getOption("region") != null)
            filter.setRegion(LeagueShard.valueOf(event.getOption("region").getAsString()));
        if (event.getOption("patch") != null)
            filter.setPatch(event.getOption("patch").getAsString());
        if (event.getOption("opponent") != null)
            filter.setOpponent(getChampionId(event.getOption("opponent").getAsString()));
        if (event.getOption("duo") != null)
            filter.setDuo(getChampionId(event.getOption("duo").getAsString()));

        return filter;
    }

    private String buildDescription(ChampionStatistics stats, Filter filter) {
        StringBuilder desc = new StringBuilder();

        if (stats == null) {
            desc.append("Not enough champion stats for this filter yet.\n");
        }
        else {
            LaneStat laneStat = filter.lane() != null ? stats.getLaneStat(filter.lane()) : null;
            if (laneStat != null) {
                desc.append("General overview:\n")
                    .append(laneStat.prettyWinrate()).append(" winrate over ").append(laneStat.prettyGames()).append(" matches\n")
                    .append(laneStat.prettyPickrate(stats.games())).append(" pickrate and ").append(stats.prettyBanrate()).append(" banrate\n");
            }
            else {
                desc.append("General overview:\n")
                    .append(stats.prettyWinrate()).append(" winrate over ").append(stats.picks()).append(" matches\n")
                    .append(stats.prettyPickrate()).append(" pickrate and ").append(stats.prettyBanrate()).append(" banrate\n");
            }

            if (filter.opponent() != 0)
                appendOpponent(desc, stats, filter);
        }

        appendFilter(desc, filter);
        return desc.toString();
    }

    private void appendOpponent(StringBuilder desc, ChampionStatistics stats, Filter filter) {
        StaticChampion opponent = ChampionUtils.getChampion(filter.opponent());
        Matchup matchup = getOpponentMatchup(stats, filter.opponent(), filter.lane());
        if (opponent == null) return;

        desc.append("Against ")
            .append(CustomEmojiHandler.getFormattedEmoji(opponent.getName())).append(" ")
            .append(opponent.getName()).append(":\n");

        if (matchup == null) {
            desc.append("Not enough matchup data\n\n");
            return;
        }

        desc.append(matchup.prettyWinrate()).append(" winrate over ")
            .append(matchup.prettyMatches()).append(" matches\n\n");
    }

    private void appendFilter(StringBuilder desc, Filter filter) {
        if (filter.patch() != null)
            desc.append("Patch ").append(filter.patch()).append("\n");
        if (filter.region() != null)
            desc.append(LeagueShardUtils.getRegionFlag(filter.region())).append(" ").append(filter.region()).append("\n");
        if (filter.rank() != null)
            desc.append(CustomEmojiHandler.getFormattedEmoji(filter.rank().toString())).append(" ").append(SafJNest.capitalize(filter.rank().toString())).append("+\n");
        else
            desc.append("All ranks\n");
        if (filter.queue() != null)
            desc.append(GameQueueTypeUtils.getMapEmoji(filter.queue())).append(" ").append(GameQueueTypeUtils.prettyName(filter.queue())).append("\n");
        if (filter.lane() != null)
            desc.append(LaneTypeUtils.getLaneTypeEmoji(filter.lane())).append(" ").append(LaneTypeUtils.getPrettyName(filter.lane())).append("\n");
        if (filter.duo() != 0) {
            StaticChampion duo = ChampionUtils.getChampion(filter.duo());
            if (duo != null)
                desc.append("Duo ").append(CustomEmojiHandler.getFormattedEmoji(duo.getName())).append(" ").append(duo.getName()).append("\n");
        }
    }

    private void addMatchups(EmbedBuilder eb, ChampionStatistics stats, Filter filter) {
        if (stats == null || filter.lane() == null) return;

        eb.addField("Weak Against", formatMatchups(stats.weakAgainst(filter.lane())), true);
        eb.addField("Strong Against", formatMatchups(stats.strongAgainst(filter.lane())), true);
        eb.addField("Popular Matchups", formatMatchups(stats.popularMatchups(filter.lane())), true);
    }

    private void addBuild(EmbedBuilder eb, Build build, Filter filter) {
        if (build == null) {
            eb.addField("Build", "No aggregated build data for this filter yet.", false);
            return;
        }

        eb.addField("Build", build.games() + " games `" + String.format("%.2f", build.winrate() * 100) + "% WR`", false);
        addSummonerSpells(eb, build);

        if (GameQueueTypeUtils.isCherry(filter.queue())) {
            eb.addField("Augment Options", formatAugments(build.augments()), true);
            eb.addField("Prismatic Options", formatPrismatics(build.prismatics()), true);
            addItemFields(eb, build);
            return;
        }

        addSkillOrder(eb, build);
        addRunes(eb, build);
        addItemFields(eb, build);
    }

    private void addSkillOrder(EmbedBuilder eb, Build build) {
        StringBuilder msg = new StringBuilder();
        for (String skill : build.getSkillOrder()) {
            switch (skill) {
                case "1" -> msg.append(CustomEmojiHandler.getFormattedEmoji("q_")).append(" > ");
                case "2" -> msg.append(CustomEmojiHandler.getFormattedEmoji("w_")).append(" > ");
                case "3" -> msg.append(CustomEmojiHandler.getFormattedEmoji("e_")).append(" > ");
                case "4" -> msg.append(CustomEmojiHandler.getFormattedEmoji("r_")).append(" > ");
                default -> { }
            }
        }

        if (msg.length() < 3) return;
        eb.addField("Skill Order", msg.substring(0, msg.length() - 3), false);
    }

    private void addSummonerSpells(EmbedBuilder eb, Build build) {
        if (build.summonerSpells() == null || build.summonerSpells().isEmpty()) return;

        StringBuilder spellsList = new StringBuilder();
        StringBuilder spellsWinrate = new StringBuilder();
        StringBuilder spellsMatches = new StringBuilder();

        for (SlotOption opt : build.summonerSpells()) {
            int spell1 = opt.getSpell1();
            int spell2 = opt.getSpell2();

            if (spell2 > spell1) {
               int temp = spell1;
               spell1 = spell2;
               spell2 = temp;
            }

            spellsList.append(CustomEmojiHandler.getFormattedEmoji(spell1 + "_")).append(" ")
                .append(CustomEmojiHandler.getFormattedEmoji(spell2 + "_")).append("\n");
            spellsWinrate.append(opt.prettyWinrate()).append(" winrate\n");
            spellsMatches.append(opt.prettyMatches()).append(" games\n");
        }

        eb.addField("Summoner Spells", value(spellsList), true);
        eb.addField(" ", value(spellsWinrate), true);
        eb.addField(" ", value(spellsMatches), true);
    }

    private void addRunes(EmbedBuilder eb, Build build) {
        if (build.runes() == null) return;

        try {
            String primaryTree = String.valueOf(build.getPrimaryTree());
            String keystone = String.valueOf(build.getKeystone());
            List<Integer> runes = build.getPrimaryRunes();

            StringBuilder msg = new StringBuilder();
            msg.append(CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getRunesHandler().get(primaryTree).getName())).append(" ")
                .append(LeagueHandler.getRunesHandler().get(primaryTree).getName()).append("\n");
            msg.append(CustomEmojiHandler.getFormattedEmoji(keystone)).append(" ")
                .append(LeagueHandler.getRunesHandler().get(primaryTree).getRune(keystone).getName()).append("\n");

            for (int i = 1; i < runes.size(); i++) {
                String rune = String.valueOf(runes.get(i));
                msg.append(CustomEmojiHandler.getFormattedEmoji(rune)).append(" ")
                    .append(LeagueHandler.getRunesHandler().get(primaryTree).getRune(rune).getName()).append("\n");
            }
            eb.addField("Runes", value(msg), true);

            addSecondaryRunes(eb, build);
            addStatRunes(eb, build);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addSecondaryRunes(EmbedBuilder eb, Build build) {
        String secondaryTree = String.valueOf(build.getSecondaryTree());
        StringBuilder msg = new StringBuilder();
        msg.append(CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getRunesHandler().get(secondaryTree).getName())).append(" ")
            .append(LeagueHandler.getRunesHandler().get(secondaryTree).getName()).append("\n");

        for (Integer secondaryRune : build.getSecondaryRunes()) {
            String rune = String.valueOf(secondaryRune);
            msg.append(CustomEmojiHandler.getFormattedEmoji(rune)).append(" ")
                .append(LeagueHandler.getRunesHandler().get(secondaryTree).getRune(rune).getName()).append("\n");
        }
        eb.addField(" ", value(msg), true);
    }

    private void addStatRunes(EmbedBuilder eb, Build build) {
        if (build.getStatShards().size() < 3) return;

        StringBuilder msg = new StringBuilder();
        msg.append(CustomEmojiHandler.getFormattedEmoji(build.getOffense())).append(" Offense\n");
        msg.append(CustomEmojiHandler.getFormattedEmoji(build.getFlex())).append(" Flex\n");
        msg.append(CustomEmojiHandler.getFormattedEmoji(build.getDefense())).append(" Defense\n");
        eb.addField(" ", value(msg), true);
    }

    private void addItemFields(EmbedBuilder eb, Build build) {
        if (build.starter() != null && !build.starter().isEmpty())
            eb.addField("Starter Items", formatItems(build.starter()), true);
        if (build.core() != null && !build.core().isEmpty())
            eb.addField("Core Items", formatItems(build.core()), true);
        if (build.boots() != null && !build.boots().isEmpty())
            eb.addField("Boots", formatSlot(build.boots()), true);

        if (build.slots() == null) return;
        for (int i = 0; i < build.slots().size() && i < 3; i++)
            eb.addField((i + 4) + " Slot", formatSlot(build.slots().get(i)), true);
    }

    private String formatMatchups(List<Matchup> matchups) {
        StringBuilder string = new StringBuilder();
        for (Matchup matchup : matchups) {
            StaticChampion champion = ChampionUtils.getChampion(matchup.champion());
            if (champion == null) continue;
            string.append(CustomEmojiHandler.getFormattedEmoji(champion.getName())).append(" ").append(champion.getName())
                .append("\n`").append(matchup.prettyMatches()).append(" G ").append(matchup.prettyWinrate()).append(" WR`\n");
        }
        return value(string);
    }

    private String formatItems(List<Integer> items) {
        StringBuilder string = new StringBuilder();
        for (Integer item : items) {
            if (item == null || item == 0) continue;
            string.append(CustomEmojiHandler.getFormattedEmoji(item.toString())).append(" ").append(itemName(item)).append("\n");
        }
        return value(string);
    }

    private String formatSlot(List<SlotOption> slots) {
        StringBuilder string = new StringBuilder();
        if (slots != null) {
            for (SlotOption slot : slots) {
                if (slot.itemId() == 0) continue;
                string.append(CustomEmojiHandler.getFormattedEmoji(slot.itemId())).append(" ").append(itemName(slot.itemId())).append("\n")
                    .append("`").append(slot.prettyMatches()).append(" games ").append(slot.prettyWinrate()).append(" WR`\n");
            }
        }
        return value(string);
    }

    private String formatAugments(List<SlotOption> augments) {
        StringBuilder string = new StringBuilder();
        if (augments != null) {
            for (SlotOption augment : augments) {
                String name = BuildUtils.toAugmentName(augment.itemId()).replaceAll(", $", "");
                string.append(CustomEmojiHandler.getFormattedEmoji("a" + augment.itemId())).append(" ").append(name).append("\n")
                    .append("`").append(augment.prettyMatches()).append(" games ").append(augment.prettyWinrate()).append(" WR`\n");
            }
        }
        return value(string);
    }

    private String formatPrismatics(List<List<SlotOption>> prismatics) {
        StringBuilder string = new StringBuilder();
        if (prismatics != null) {
            for (List<SlotOption> group : prismatics) {
                for (SlotOption prismatic : group) {
                    if (prismatic.itemId() == 0) continue;
                    string.append(CustomEmojiHandler.getFormattedEmoji(prismatic.itemId())).append(" ").append(itemName(prismatic.itemId())).append("\n")
                        .append("`").append(prismatic.prettyMatches()).append(" games ").append(prismatic.prettyWinrate()).append(" WR`\n");
                }
            }
        }
        return value(string);
    }

    private Matchup getOpponentMatchup(ChampionStatistics stats, int opponent, LaneType lane) {
        Matchup matchup = stats.getOpponentMatchup(opponent, lane);
        if (matchup != null || lane != null) return matchup;

        int matches = 0;
        double wins = 0;
        for (Map.Entry<Integer, Matchup> entry : stats.matchups().entrySet()) {
            if (entry.getKey() != opponent) continue;
            matches += entry.getValue().matches();
            wins += entry.getValue().matches() * entry.getValue().winrate();
        }
        if (matches == 0) return null;
        return new Matchup(opponent, matches, wins / matches);
    }

    private StaticChampion getChampion(String name) {
        String championName = SafJNest.findSimilarWord(name, new ArrayList<>(ChampionUtils.getChampionsNames()));
        return ChampionUtils.getChampion(championName);
    }

    private int getChampionId(String name) {
        StaticChampion champion = getChampion(name);
        return champion != null ? champion.getId() : 0;
    }

    private String getTitle(StaticChampion champion, Filter filter) {
        String title = champion.getName();
        if (filter.lane() != null)
            title += " " + LaneTypeUtils.getPrettyName(filter.lane());
        else if (filter.queue() != null)
            title += " " + GameQueueTypeUtils.prettyName(filter.queue());
        return title;
    }

    private String itemName(int item) {
        try {
            var data = LeagueHandler.getRiotApi().getDDragonAPI().getItem(item);
            if (data != null) return data.getName();
        } catch (Exception e) { }
        return String.valueOf(item);
    }

    private String value(StringBuilder builder) {
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private void finishEmbed(EmbedBuilder eb, StaticChampion champion) {
        eb.setColor(Bot.getColor());
        eb.setThumbnail(ChampionUtils.getChampionProfilePic(champion.getId()));
        eb.setFooter("We are doing our best to analyze more game as possible everyday to suggest you the best builds!", "https://cdn.discordapp.com/emojis/776346468700389436.png");
    }
}
