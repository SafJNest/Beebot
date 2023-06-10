package com.safjnest.SlashCommands.LOL;

import java.util.Arrays;

import java.awt.Color;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.Commands.CommandsLoader;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.safjnest.Utilities.LOL.MobalyticsHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;




/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class ChampionSlash extends SlashCommand {
 
    /**
     * Constructor
     */
    public ChampionSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "champ", "Champion Name", true),
            new OptionData(OptionType.STRING, "lane", "Champion Lane", true)
                .addChoice("Top Lane", "TOP")
                .addChoice("Jungle", "JUNGLE")
                .addChoice("Mid Lane", "MID")
                .addChoice("ADC", "ADC")
                .addChoice("Support", "SUPPORT"));
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        
        String champName = event.getOption("champ").getAsString();
        String lane = event.getOption("lane").getAsString();
        String laneFormatName =  "";
        switch(lane){
            case "TOP":
                laneFormatName = "Top Lane";
                break;
            case "JUNGLE":
                laneFormatName = "Jungle";
                break;
            case "MID":
                laneFormatName = "Mid Lane";
                break;
            case "ADC":
                laneFormatName = "ADC";
                break;
            case "SUPPORT":
                laneFormatName = "Support";
                break;
        }
        
        if(champName.equalsIgnoreCase("nunu"))
            champName+="willump";

        champName = RiotHandler.findChampion(champName);
    
           
        String json = MobalyticsHandler.getChampioStats(champName, lane);
        if(json == null){
            event.getHook().editOriginal("Could be some problem with our database or lack of data due to new patch. Try again later.").queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder(); 
        eb = new EmbedBuilder(); 
        eb.setTitle(":sparkles:Beebot Rune Command"); 
        eb.setDescription("**Highest Win Rate** info for " + RiotHandler.getFormattedEmoji(event.getJDA(), champName) + " " + champName + " " + RiotHandler.getFormattedEmoji(event.getJDA(), laneFormatName) + " **" + laneFormatName + "**");
        eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl()); 
        
        String[] runes = MobalyticsHandler.getRunes(json);
        String[] roots = MobalyticsHandler.getRunesRoot(json);
        String[] skills = MobalyticsHandler.getSkillsOrder(json);
        String[] spells = MobalyticsHandler.getSummonerSpell(json);
        String[] starter = MobalyticsHandler.getBuild(json, 0);
        String[] core = MobalyticsHandler.getBuild(json, 2);
        String[] fullBuild = MobalyticsHandler.getBuild(json, 3);
        String[] situational = MobalyticsHandler.getBuild(json, 4);
        


        /*
        *  Skill Order
        */
        String msg = "​";
        for(int i = 0; i < 18; i++){
            switch (skills[i]){
                case "1":
                    msg += "Q -> ";
                    break;
                case "2":
                    msg += "W -> ";
                    break;
                case "3":  
                    msg +=  "E -> ";
                    break;
                case "4":      
                    msg += " R -> ";
                    break;

            }
        }
        
        eb.addField("**Skills Order**", msg.substring(0, msg.length()-4), false);
        /*
        *  Summoner Spells
        */
         eb.addField("**Summoner Spells**", RiotHandler.getFormattedEmoji(event.getJDA(), spells[0] + "_") + " " + RiotHandler.getFormattedEmoji(event.getJDA(), spells[1] + "_"), false);


        /*
        * First Runes Root
        */
        msg = "​\n";
        for(int i = 0; i < 4; i++){
            msg += RiotHandler.getFormattedEmoji(event.getJDA(), runes[i]) + " " + RiotHandler.getRunesHandler().get(roots[0]).getRune(runes[i]).getName() + "\n";
        }
        String support = RiotHandler.getRunesHandler().get(roots[0]).getName();
        eb.addField(RiotHandler.getFormattedEmoji(event.getJDA(), support) + " " + support, msg, true);

        /*
         * Second Runes Root
        */
        msg = "​\n";
        for(int i = 4; i < 6; i++){
            msg += RiotHandler.getFormattedEmoji(event.getJDA(), runes[i]) + " " + RiotHandler.getRunesHandler().get(roots[1]).getRune(runes[i]).getName() + "\n";
        }
        support = RiotHandler.getRunesHandler().get(roots[1]).getName();
        eb.addField(RiotHandler.getFormattedEmoji(event.getJDA(), support) + " " + support, msg, true);

        /*
        * Stats
        */
        msg = "​\n";
        msg += RiotHandler.getFormattedEmoji(event.getJDA(), runes[6]) + " Offense\n";
        msg += RiotHandler.getFormattedEmoji(event.getJDA(), runes[7]) + " Flex\n";
        msg += RiotHandler.getFormattedEmoji(event.getJDA(), runes[8]) + " Defense\n";
        eb.addField("**Shard**", msg, true);


        /*
        * Starter Items
        */
        msg = "​";
        for(int i = 0; i < starter.length; i++){
            msg += RiotHandler.getFormattedEmoji(event.getJDA(), starter[i]) + " " + RiotHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(starter[i])).getName() + "\n";
        }
        eb.addField("**Starter Items**", msg, true);

        /*
        * Core Items
        */
        msg = "​";
        for(int i = 0; i < core.length; i++){
            msg += RiotHandler.getFormattedEmoji(event.getJDA(), core[i])  + " " + RiotHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(core[i])).getName() + "\n";
        }
        eb.addField("**Core Items**", msg, true);

        /*
        * Full Build
        */

        msg = "​";
        for(int i = 0; i < fullBuild.length; i++){
            msg += RiotHandler.getFormattedEmoji(event.getJDA(), fullBuild[i])  + " " + RiotHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(fullBuild[i])).getName() + "\n";
        }

        eb.addField("**Full Build**", msg, true);

        /*
        * Situational Items
        */
        msg = "​";
        for(int i = 0; i < situational.length; i++){
            msg += RiotHandler.getFormattedEmoji(event.getJDA(), situational[i])  + " " + RiotHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(situational[i])).getName() + "\n";
        }
        eb.addField("**Situational Items**", msg, true);


        eb.setColor(Color.decode(
            BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
        ));
        
        
        champName = RiotHandler.transposeChampionNameForDataDragon(champName);
        eb.setThumbnail(RiotHandler.getChampionProfilePic(champName));
        eb.setFooter("We analyze thousands of game everyday to suggest you the best builds!", null); 

        
        event.getHook().editOriginalEmbeds(eb.build()).queue();
	}


    
    
}
