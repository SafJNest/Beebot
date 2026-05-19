package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.SlotOption;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.service.BuildService;
import com.safjnest.lol.service.ChampionStatsService;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueShardUtils;
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
 
    /**
     * Constructor
     */
    public Champion(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "champion", "Champion Name", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "role", "Champion Role", true)
                .addChoice("Top", "TOP")
                .addChoice("Jungle", "JUNGLE")
                .addChoice("Mid", "MID")
                .addChoice("ADC", "ADC")
                .addChoice("Support", "SUPPORT"),
            new OptionData(OptionType.STRING, "opponent", "Opponent Champion", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "duo", "Duo Champion", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "patch", "Patch", false)
                .addChoice("16.7", "16.7")
                .addChoice("16.8", "16.8")
                .addChoice("16.9", "16.9"),
            LeagueShardUtils.getAsOptions(),
            new OptionData(OptionType.STRING, "rank", "Rank (Empty for all)", false)
                .addChoice("IRON", "IRON")
                .addChoice("BRONZE", "BRONZE")
                .addChoice("SILVER", "SILVER")
                .addChoice("GOLD", "GOLD")
                .addChoice("PLATINUM", "PLATINUM")
                .addChoice("DIAMOND", "DIAMOND")
                .addChoice("MASTER", "MASTER")
                .addChoice("GRANDMASTER", "GRANDMASTER")
                .addChoice("CHALLENGER", "CHALLENGER")
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        
        String champName = event.getOption("champion").getAsString();
        
        String lane = event.getOption("role").getAsString();
        String laneFormatName =  "";
        LaneType laneType = null;
        switch(lane){
            case "TOP":
                laneFormatName = "Top Lane";
                laneType = LaneType.TOP;
                break;
            case "JUNGLE":
                laneFormatName = "Jungle";
                laneType = LaneType.JUNGLE;
                break;
            case "MID":
                laneFormatName = "Mid Lane";
                laneType = LaneType.MID;
                break;
            case "ADC":
                laneFormatName = "ADC";
                laneType = LaneType.BOT;
                break;
            case "SUPPORT":
                laneFormatName = "Support";
                laneType = LaneType.UTILITY;
                break;
        }

        ArrayList<String> championsName = new ArrayList<>();
        for (String champion : ChampionUtils.getChampionsNames()) {
            championsName.add(champion);
        }
        champName = SafJNest.findSimilarWord(champName, championsName);
        StaticChampion champion = ChampionUtils.getChampion(champName);
    
        
        EmbedBuilder eb = new EmbedBuilder(); 
        eb.setAuthor(champName + " " + laneFormatName, "https://github.com/SafJNest", ChampionUtils.getChampionProfilePic(champName)); 

        Filter filter = new Filter()
            .setChampion(champion.getId())
            .setLane(laneType)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO);

        if (event.getOption("rank") != null) {
            String rank = event.getOption("rank").getAsString();
            if (!rank.isEmpty()) filter.setRank(TierType.valueOf(rank));
        }
        if (event.getOption("region") != null) {
            String region = event.getOption("region").getAsString();
            if (!region.isEmpty()) filter.setRegion(LeagueShard.valueOf(region));
        }
        if (event.getOption("patch") != null) {
            String patch = event.getOption("patch").getAsString();
            if (!patch.isEmpty()) filter.setPatch(patch);
        }
        if (event.getOption("opponent") != null) {
            int opponent = ChampionUtils.getChampion(event.getOption("opponent").getAsString()).getId();
            if (opponent != 0) filter.setOpponent(opponent);
        }
        if (event.getOption("duo") != null) {
            int duo = ChampionUtils.getChampion(event.getOption("duo").getAsString()).getId();
            if (duo != 0) filter.setDuo(duo);
        }
        



        ChampionStatistics stats = new ChampionStatsService().get(filter);
        Build build = new BuildService().getMostUsed(filter);
        if (build != null) build.print();

        StringBuilder desc = new StringBuilder();
        desc.append("General overview:\n").append(stats.prettyWinrate()).append(" winrate over ").append(stats.prettyGames()).append(" matches\n")
            .append(stats.prettyPickrate()).append(" pickrate and ").append(stats.prettyBanrate()).append(" banrate\n");
        if (filter.opponent() != 0) {
            StaticChampion opponent = ChampionUtils.getChampion(filter.opponent());
            Matchup matchup = stats.getOpponentMatchup(filter.opponent(), filter.lane());
            desc.append("Lane against ").append(CustomEmojiHandler.getFormattedEmoji(" " + opponent.getName())).append(" " + opponent.getName()).append(":\n")
                .append(matchup.prettyWinrate()).append(" winrate over ").append(matchup.prettyMatches()).append(" matches\n\n");
        }

        if (filter.patch() != null)  desc.append("Patch ").append(filter.patch()).append("\n");
        if (filter.region() != null) desc.append(LeagueShardUtils.getRegionFlag(filter.region())).append(" " + filter.region()).append("\n");
        if (filter.rank() != null)   desc.append(CustomEmojiHandler.getFormattedEmoji(filter.rank().toString())).append(" " + SafJNest.capitalize(filter.rank().toString())).append("\n");
        if (filter.queue() != null)  desc.append(GameQueueTypeUtils.getMapEmoji(filter.queue())).append(" " + GameQueueTypeUtils.prettyName(filter.queue())).append("\n");
        
        eb.setDescription(desc.toString());

        String weakString = "";
        for (Matchup matchup : stats.weakAgainst(filter.lane())) {
            weakString += CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(matchup.champion()).getName()) + " " + ChampionUtils.getChampion(matchup.champion()).getName() + "\n`" + matchup.prettyMatches() + " G " + matchup.prettyWinrate() + " WR`\n";    
        }
        eb.addField("Weak Against", weakString, true);
        String strongString = "";
        for (Matchup matchup : stats.strongAgainst(filter.lane())) {
            strongString += CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(matchup.champion()).getName()) + " " + ChampionUtils.getChampion(matchup.champion()).getName() + "\n`" + matchup.prettyMatches() + " G " + matchup.prettyWinrate() + " WR`\n";
        }
        eb.addField("Strong Against", strongString, true);

        String popularString = "";
        for (Matchup matchup : stats.popularMatchups(filter.lane())) {
            popularString += CustomEmojiHandler.getFormattedEmoji(ChampionUtils.getChampion(matchup.champion()).getName()) + " " + ChampionUtils.getChampion(matchup.champion()).getName() + "\n`" + matchup.prettyMatches() + " G " + matchup.prettyWinrate() + " WR`\n";
        }
        eb.addField("Popular Matchups", popularString, true);
        
        if (build == null) {
            eb.addField("Build", "No aggregated build data for this filter yet.", false);
            eb.setColor(Bot.getColor());
            champName = ChampionUtils.sanitizeChampionName(champName);
            eb.setThumbnail(ChampionUtils.getChampionProfilePic(champName));
            eb.setFooter("We are doing our best to analyze more game as possible everyday to suggest you the best builds!", "https://cdn.discordapp.com/emojis/776346468700389436.png");
            event.getHook().editOriginalEmbeds(eb.build()).queue();
            return;
        }

        String msg = "";
        for (String skill : build.getSkillOrder()) {
            switch (skill) {
                case "1":
                    msg += CustomEmojiHandler.getFormattedEmoji("q_") + " > ";
                    break;
                case "2":
                    msg += CustomEmojiHandler.getFormattedEmoji("w_") + " > ";
                    break;
                case "3":
                    msg += CustomEmojiHandler.getFormattedEmoji("e_") + " > ";
                    break;
                case "4":
                    msg += CustomEmojiHandler.getFormattedEmoji("r_") + " > ";
                    break;
                default:
                    break;
            }
        }
        if (msg.length() >= 3)
            eb.addField("**Skill Order**", msg.substring(0, msg.length() - 2), false);
        else
            eb.addField("**Skill Order**", "—", false);


        String spellsList = "", spellsWinrate = "", spellsMatches = "";
        for (int index = 0; index < build.summonerSpells().size(); index++) {
            var opt = build.summonerSpells().get(index);
        
            int spell1 = opt.getSpell1();
            int spell2 = opt.getSpell2();

            if (spell2 > spell1) {
               int temp = spell1;
               spell1 = spell2;
               spell2 = temp;
            }
        
            spellsList +=
                CustomEmojiHandler.getFormattedEmoji(spell1 + "_") + " " + CustomEmojiHandler.getFormattedEmoji(spell2 + "_") + "\n";
            spellsWinrate += opt.prettyWinrate() + " winrate\n";
            spellsMatches += opt.prettyMatches() + " games\n";
        
        
        }
            
        eb.addField("**Summoner Spells**", spellsList, true);
        eb.addField(" ", spellsWinrate, true);
        eb.addField(" ", spellsMatches, true);
        
        msg = "";
        List<Integer> runes = build.getPrimaryRunes();
        String primaryTree = String.valueOf(build.getPrimaryTree());
        String keystone = String.valueOf(build.getKeystone());
        

        msg += CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getRunesHandler().get(primaryTree).getName()) + " " + LeagueHandler.getRunesHandler().get(primaryTree).getName() + "\n";
        msg += CustomEmojiHandler.getFormattedEmoji(keystone) + " " + LeagueHandler.getRunesHandler().get(primaryTree).getRune(keystone).getName() + "\n";

        for (int i = 1; i < runes.size(); i++) {
            String rune = String.valueOf(runes.get(i));
            msg += CustomEmojiHandler.getFormattedEmoji(rune) + " " + LeagueHandler.getRunesHandler().get(primaryTree).getRune(rune).getName() + "\n";
        }
        eb.addField("**Runes**", msg, true);

        String secondaryTree = String.valueOf(build.getSecondaryTree());
        List<Integer> secondaryRunes = build.getSecondaryRunes();
        msg = CustomEmojiHandler.getFormattedEmoji(LeagueHandler.getRunesHandler().get(secondaryTree).getName()) + " " + LeagueHandler.getRunesHandler().get(secondaryTree).getName() + "\n";
        for (int i = 0; i < secondaryRunes.size(); i++) {
            String rune = String.valueOf(secondaryRunes.get(i));
            msg += CustomEmojiHandler.getFormattedEmoji(rune) + " " + LeagueHandler.getRunesHandler().get(secondaryTree).getRune(rune).getName() + "\n";
        }
        eb.addField(" ", msg, true);
        

        msg = "";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getOffense()) + " Offense\n";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getFlex()) + " Flex\n";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getDefense()) + " Defense\n";
        eb.addField(" ", msg, true);
        

        String starters = "";

        for (Integer item : build.starter()) {
            starters += CustomEmojiHandler.getFormattedEmoji(item.toString()) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(item).getName() + "\n";
        }
        eb.addField("**Starter Items**", starters, true);

        String core = "";
        for (Integer item : build.core()) {
            core += CustomEmojiHandler.getFormattedEmoji(item.toString()) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(item).getName() + "\n";
        }
        eb.addField("**Core Items**", core, true);

        String boots = "";
        for (SlotOption boot : build.boots()) {
            boots += CustomEmojiHandler.getFormattedEmoji(boot.itemId()) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(boot.itemId()).getName() + "\n";
        }
        eb.addField("**Boots**", boots, true);

        String fourthSlot = "";
        fourthSlot = formatSlot(build.slots().get(0));
        eb.addField("**Fourth Slot**", fourthSlot, true);

        String fifthSlot = "";
        fifthSlot = formatSlot(build.slots().get(1));
        eb.addField("**Fifth Slot**", fifthSlot, true);

        String sixthSlot = "";
        sixthSlot = formatSlot(build.slots().get(2));
        eb.addField("**Sixth Slot**", sixthSlot, true);



        eb.setColor(Bot.getColor());
        
        
        champName = ChampionUtils.sanitizeChampionName(champName);
        eb.setThumbnail(ChampionUtils.getChampionProfilePic(champName));
        eb.setFooter("We are doing our best to analyze more game as possible everyday to suggest you the best builds!", "https://cdn.discordapp.com/emojis/776346468700389436.png"); 

        event.getHook().editOriginalEmbeds(eb.build()).queue();
	}

    private String formatSlot(List<SlotOption> slots) {
        String string = "";
        for (SlotOption slot : slots) {
            string += CustomEmojiHandler.getFormattedEmoji(slot.itemId()) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(slot.itemId()).getName() + "\n"
            + "`" + slot.matches() + " games " + String.format("%.2f", slot.winrate()) + "% WR`" + "\n";
        }
        return string;
    }


    
    
}
