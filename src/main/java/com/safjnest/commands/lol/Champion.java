package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;
import com.safjnest.util.lol.BuildData;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.MatchTracker;
import com.safjnest.util.lol.MobalyticsHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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
        HashMap<String, String> champInfo = MatchTracker.analyzeChampionData(champion.getId(), laneType);
        


        String json = MobalyticsHandler.getChampioStats(champName, lane);
         if(json == null){
            event.getHook().editOriginal("Could be some problem with our database or lack of data due to new patch. Try again later.").queue();
            return;
        }

        BuildData build = new BuildData(champion, laneType, json);
                
        eb.setDescription("Patch **" + build.getPatch() + "** | Win rate **" + build.getWinrate() + "%** based on **" + build.getGames() + "** matches");
        eb.setDescription("**" + champName + "** has a winrate of **" + champInfo.get("winrate") + "%** (**" + champInfo.get("pickrate") + "%** pickrate and **" + champInfo.get("banrate") + "%** banrate) over **" + champInfo.get("picks") + "** matches in **(" + build.getPatch() + ")**");
        
        String msg = "";
        for(int i = 0; i < 18; i++){
            switch (build.getSkillOrder().get(i)){
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
        eb.addField("**Summoner Spells**", CustomEmojiHandler.getFormattedEmoji(build.getD() + "_") + " " + CustomEmojiHandler.getFormattedEmoji(build.getF() + "_") + "\n​\n", false);//DO NOT TOUCH \N\N THERE IS AN INVISIBLEAR CHARFATERT

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

        for (String label : build.getBuildMap().keySet()) {
            List<String> items = build.getBuildMap().get(label); 
            String title;
            switch(label) {
                case "starter": title = "**Starter Items**"; break;
                case "core": title = "**Core Items**"; break;
                case "fullbuild": title = "**Full Build Items**"; break;
                case "boots": title = "**Boots**"; break;
                case "situational": title = "**Situational Items**"; break;
                default: title = "**" + label.substring(0, 1).toUpperCase() + label.substring(1) + "**"; break;
            }

            if (label.equals("situational") && items.size() > 3) {
                String msg1 = "", msg2 = "";
                for (int i = 0; i < 3 && i < items.size(); i++) {
                    msg1 += CustomEmojiHandler.getFormattedEmoji(items.get(i)) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(items.get(i))).getName() + "\n";
                }
                for (int i = 3; i < 6 && i < items.size(); i++) {
                    msg2 += CustomEmojiHandler.getFormattedEmoji(items.get(i)) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(items.get(i))).getName() + "\n";
                }
                eb.addField(title, msg1, true);
                eb.addField(" ", msg2, true);
            } else {
                msg = "";
                for (String item : items) {
                    msg += CustomEmojiHandler.getFormattedEmoji(item) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(item)).getName() + "\n";
                }
                eb.addField(title, msg, true);
            }
        }
        


        eb.setColor(Bot.getColor());
        
        
        champName = LeagueHandler.transposeChampionNameForDataDragon(champName);
        eb.setThumbnail(LeagueHandler.getChampionProfilePic(champName));
        eb.setFooter("We are doing our best to analyze more game as possible everyday to suggest you the best builds!", "https://cdn.discordapp.com/emojis/776346468700389436.png"); 

        event.getHook().editOriginalEmbeds(eb.build()).queue();
	}


    
    
}
