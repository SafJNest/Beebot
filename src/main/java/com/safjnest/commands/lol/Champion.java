package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.MatchTracker;
import com.safjnest.util.lol.MobalyticsHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;




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
        int championId = 0;
        
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
        championId = LeagueHandler.getChampionByName(champName).getId();
    
        
        EmbedBuilder eb = new EmbedBuilder(); 
        eb = new EmbedBuilder(); 
        eb.setTitle(champName + " " + laneFormatName + " " + CustomEmojiHandler.getFormattedEmoji(laneFormatName)); 
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl()); 
        HashMap<String, String> champInfo = MatchTracker.analyzeChampionData(championId, laneType);
        


        String json = MobalyticsHandler.getChampioStats(champName, lane);
         if(json == null){
            event.getHook().editOriginal("Could be some problem with our database or lack of data due to new patch. Try again later.").queue();
            return;
        }
        
        
        String[] runes = MobalyticsHandler.getRunes(json);
        String[] roots = MobalyticsHandler.getRunesRoot(json);
        String[] skills = MobalyticsHandler.getSkillsOrder(json);
        String[] spells = MobalyticsHandler.getSummonerSpell(json);
        String[] starter = MobalyticsHandler.getBuild(json, 0);
        String[] core = MobalyticsHandler.getBuild(json, 2);
        String[] fullBuild = MobalyticsHandler.getBuild(json, 3);
        String[] situational = MobalyticsHandler.getBuild(json, 4);

        String patch = MobalyticsHandler.getPatch(json);
        String winRate = MobalyticsHandler.getWinRate(json).substring(0, MobalyticsHandler.getWinRate(json).indexOf(".") + 3);
        String matchCount = MobalyticsHandler.getMatchCount(json);
        
        /*
         * Description
         */
        //eb.setDescription("**Highest Win Rate** info for " + RiotHandler.getFormattedEmoji(event.getJDA(), champName) + " " + champName + " " + RiotHandler.getFormattedEmoji(event.getJDA(), laneFormatName) + " **" + laneFormatName + "**");
        eb.setDescription("Patch **" + patch + "** | Win rate **" + winRate + "%** based on **" + matchCount + "** matches");
        eb.setDescription("**" + champName + "** has a winrate of **" + champInfo.get("winrate") + "%** (**" + champInfo.get("pickrate") + "%** pickrate and **" + champInfo.get("banrate") + "%** banrate) over **" + champInfo.get("picks") + "** matches in **(" + patch + ")**");
        
        /*
        *  Skill Order
        */
        String msg = "";
        for(int i = 0; i < 18; i++){
            switch (skills[i]){
                case "1":
                msg +=  CustomEmojiHandler.getFormattedEmoji("q_") + " > ";
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

            }
        }
        
        eb.addField("**Skill Order**", msg.substring(0, msg.length()-2), false);
        /*
        *  Summoner Spells
        */
        eb.addField("**Summoner Spells**", CustomEmojiHandler.getFormattedEmoji(spells[0] + "_") + " " + CustomEmojiHandler.getFormattedEmoji(spells[1] + "_") + "\n​\n", false);//DO NOT TOUCH \N\N THERE IS AN INVISIBLEAR CHARFATERT

        /*
        * First Runes Root
        */
        msg = "";
        for(int i = 0; i < 4; i++){
            msg += CustomEmojiHandler.getFormattedEmoji(runes[i]) + " " + LeagueHandler.getRunesHandler().get(roots[0]).getRune(runes[i]).getName() + "\n";
        }
        String support = LeagueHandler.getRunesHandler().get(roots[0]).getName();
        eb.addField(CustomEmojiHandler.getFormattedEmoji(support) + " " + support, msg, true);

        /*
         * Second Runes Root
        */
        msg = "";
        for(int i = 4; i < 6; i++){
            msg += CustomEmojiHandler.getFormattedEmoji(runes[i]) + " " + LeagueHandler.getRunesHandler().get(roots[1]).getRune(runes[i]).getName() + "\n";
        }
        support = LeagueHandler.getRunesHandler().get(roots[1]).getName();
        eb.addField(CustomEmojiHandler.getFormattedEmoji(support) + " " + support, msg, true);

        /*
        * shard
        */
        msg = "";
        msg += CustomEmojiHandler.getFormattedEmoji(runes[6]) + " Offense\n";
        msg += CustomEmojiHandler.getFormattedEmoji(runes[7]) + " Flex\n";
        msg += CustomEmojiHandler.getFormattedEmoji(runes[8]) + " Defense\n";
        eb.addField("**Shard**", msg, true);


        /*
        * Starter Items
        */
        msg = "";
        for(int i = 0; i < starter.length; i++){
            msg += CustomEmojiHandler.getFormattedEmoji(starter[i]) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(starter[i])).getName() + "\n";
        }
        eb.addField("**Starter Items**", msg, true);

        /*
        * Core Items
        */
        msg = "";
        for(int i = 0; i < core.length; i++){
            msg += CustomEmojiHandler.getFormattedEmoji(core[i])  + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(core[i])).getName() + "\n";
        }
        eb.addField("**Core Items**", msg, true);

        /*
        * Full Build
        */

        msg = "";
        for(int i = 0; i < fullBuild.length; i++){
            msg += CustomEmojiHandler.getFormattedEmoji(fullBuild[i])  + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(fullBuild[i])).getName() + "\n";
        }

        eb.addField("**Full Build**", msg, true);

        /*
        * Situational Items
        */
        msg = "";
        for(int i = 0; i < 3; i++){
            msg += CustomEmojiHandler.getFormattedEmoji(situational[i])  + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(situational[i])).getName() + "\n";
        }
        eb.addField("**Situational Items**", msg, true);
        if(situational.length > 6){
            msg = "";
            for(int i = 3; i < 6; i++){
                msg += CustomEmojiHandler.getFormattedEmoji(situational[i])  + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(situational[i])).getName() + "\n";
            }   
            eb.addField(" ", msg, true);
        }
        


        eb.setColor(Bot.getColor());
        
        
        champName = LeagueHandler.transposeChampionNameForDataDragon(champName);
        eb.setThumbnail(LeagueHandler.getChampionProfilePic(champName));
        eb.setFooter("We are doing our best to analyze more game as possible everyday to suggest you the best builds!", "https://cdn.discordapp.com/emojis/776346468700389436.png"); 

        event.getHook().editOriginalEmbeds(eb.build()).queue();
	}


    
    
}
