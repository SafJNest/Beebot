package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.build.Filter;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.Build.SlotOption;
import com.safjnest.lol.service.BuildService;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
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
                .addChoice("Support", "SUPPORT")
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
        for (String champion : LeagueHandler.getChampions()) {
            championsName.add(champion);
        }
        champName = SafJNest.findSimilarWord(champName, championsName);
        StaticChampion champion = LeagueHandler.getChampionByName(champName);
    
        
        EmbedBuilder eb = new EmbedBuilder(); 
        eb = new EmbedBuilder(); 
        eb.setTitle(champName + " " + laneFormatName + " " + CustomEmojiHandler.getFormattedEmoji(laneFormatName)); 
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl()); 
        HashMap<String, String> champInfo = Tracker.analyzeChampionData(champion.getId(), laneType);

        Filter filter = new Filter()
            .setChampion(champion.getId())
            .setLane(laneType)
            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
            .setPatch("16.8");

        Build build = new BuildService().getAll(filter).stream().sorted(Comparator.comparingDouble(Build::games).reversed()).findFirst().orElse(null);
        if (build != null) build.print();

        eb.setDescription("**" + champName + "** has a winrate of **" + champInfo.get("winrate") + "%** (**" + champInfo.get("pickrate") + "%** pickrate and **" + champInfo.get("banrate") + "%** banrate) over **" + champInfo.get("picks") + "** matches in **(" + LeagueHandler.getVersion() + ")**");

        if (build == null) {
            eb.addField("Build", "No aggregated build data for this filter yet.", false);
            eb.setColor(Bot.getColor());
            champName = LeagueHandler.transposeChampionNameForDataDragon(champName);
            eb.setThumbnail(LeagueHandler.getChampionProfilePic(champName));
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

        for (int index = 0; index < build.summonerSpells().size(); index++) {
            var opt = build.summonerSpells().get(index);
        
            int spell1 = opt.itemId() / 100;
            int spell2 = opt.itemId() % 100;
        
            String spell1Name = LeagueHandler.getSpellName(spell1);
            String spell2Name = LeagueHandler.getSpellName(spell2);
        
            String summonerSpells =
                CustomEmojiHandler.getFormattedEmoji(spell1 + "_") + " " + spell1Name + "\n" +
                CustomEmojiHandler.getFormattedEmoji(spell2 + "_") + " " + spell2Name + "\n" +
                "`" + opt.matches() + " games`\n`" + String.format("%.2f", opt.winrate()) + "% WR`\n";
        
            String title = index == 0 ? "**Summoner Spells**" : " ";
        
            eb.addField(title, summonerSpells, true);
        }

        /*
        msg = "";
        List<String> runes = build.getPrimaryRunes();
        for (int i = 1; i < runes.size(); i++) {
            String rune = runes.get(i);
            msg += CustomEmojiHandler.getFormattedEmoji(rune) + " " + LeagueHandler.getRunesHandler().get(build.getPrimaryRunesRoot()).getRune(rune).getName() + "\n";
        }
        String support = LeagueHandler.getRunesHandler().get(build.getPrimaryRunesRoot()).getName();
        eb.addField(CustomEmojiHandler.getFormattedEmoji(support) + " " + support, msg, true);


        msg = "";
        List<String> secondaryRunes = build.getSecondaryRunes();
        for (int i = 1; i < secondaryRunes.size(); i++) {
            String rune = secondaryRunes.get(i);
            msg += CustomEmojiHandler.getFormattedEmoji(rune) + " " + LeagueHandler.getRunesHandler().get(build.getSecondaryRunesRoot()).getRune(rune).getName() + "\n";
        }
        support = LeagueHandler.getRunesHandler().get(build.getSecondaryRunesRoot()).getName();
        eb.addField(CustomEmojiHandler.getFormattedEmoji(support) + " " + support, msg, true);
        

        msg = "";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getOffense()) + " Offense\n";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getFlex()) + " Flex\n";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getDefense()) + " Defense\n";
        eb.addField("**Shard**", msg, true);
        */

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
        
        
        champName = LeagueHandler.transposeChampionNameForDataDragon(champName);
        eb.setThumbnail(LeagueHandler.getChampionProfilePic(champName));
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
