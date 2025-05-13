package com.safjnest.commands.lol;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.LeagueDBHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.CustomBuildData;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;




/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Build extends SlashCommand {
 
    /**
     * Constructor
     */
    public Build(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "custom-build", "Build Name", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();

        CustomBuildData build = LeagueDBHandler.getCustomBuild(event.getOption("custom-build").getAsString());
        
        String champName = build.getChampion().getName();
        
        String laneFormatName =  "";
        switch(build.getLane().ordinal()){
            case 0:
                laneFormatName = "Top Lane";
                break;
            case 1:
                laneFormatName = "Jungle";
                break;
            case 2:
                laneFormatName = "Mid Lane";
                break;
            case 3:
                laneFormatName = "ADC";
                break;
            case 4:
                laneFormatName = "Support";
                break;
        }

        
        EmbedBuilder eb = new EmbedBuilder(); 
        eb = new EmbedBuilder(); 
        eb.setTitle(build.getName()); 
        if (!build.getUserId().isEmpty())
            eb.setAuthor(event.getJDA().getUserById(build.getUserId()).getName(), "https://github.com/SafJNest", event.getJDA().getUserById(build.getUserId()).getAvatarUrl());   
        else
            eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());       
        
        eb.setDescription(build.getDescription());


        

        String msg = "";
        for(int i = 0; i < 18 && i < build.getSkillOrder().size(); i++){
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
        
        eb.addField("**Suggested Lane**", laneFormatName + " " + CustomEmojiHandler.getFormattedEmoji(laneFormatName), true);
        eb.addField("**Skill Order**", msg.substring(0, msg.length()-2), true);
        eb.addField("**Summoner Spells**", CustomEmojiHandler.getFormattedEmoji(build.getD() + "_") + " " + CustomEmojiHandler.getFormattedEmoji(build.getF() + "_") + "\n​\n", true);//DO NOT TOUCH \N\N THERE IS AN INVISIBLEAR CHARFATERT

        
        /*
        * First Runes Root
        */
        msg = "";
        List<String> runes = build.getPrimaryRunes();
        for (int i = 1; i < runes.size(); i++) {
            String rune = runes.get(i);
            msg += CustomEmojiHandler.getFormattedEmoji(rune) + " " + LeagueHandler.getRunesHandler().get(build.getPrimaryRunesRoot()).getRune(rune).getName() + "\n";
        }
        String support = LeagueHandler.getRunesHandler().get(build.getPrimaryRunesRoot()).getName();
        eb.addField(CustomEmojiHandler.getFormattedEmoji(support) + " " + support, msg, true);

        /*
         * Second Runes Root
        */
        msg = "";
        List<String> secondaryRunes = build.getSecondaryRunes();
        for (int i = 1; i < secondaryRunes.size(); i++) {
            String rune = secondaryRunes.get(i);
            msg += CustomEmojiHandler.getFormattedEmoji(rune) + " " + LeagueHandler.getRunesHandler().get(build.getSecondaryRunesRoot()).getRune(rune).getName() + "\n";
        }
        support = LeagueHandler.getRunesHandler().get(build.getSecondaryRunesRoot()).getName();
        eb.addField(CustomEmojiHandler.getFormattedEmoji(support) + " " + support, msg, true);

        /*
        * shard
        */
        msg = "";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getOffense()) + " Offense\n";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getFlex()) + " Flex\n";
        msg += CustomEmojiHandler.getFormattedEmoji(build.getDefense()) + " Defense\n";
        eb.addField("**Shard**", msg, true);


        for (String label : build.getBuildMap().keySet()) {
            List<String> items = build.getBuildMap().get(label); 
            msg = "";
            for (String item : items) 
                msg += CustomEmojiHandler.getFormattedEmoji(item)  + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(item)).getName() + "\n";
        
            eb.addField("**" +  label+ "**", msg, true);
        }


        eb.setColor(Bot.getColor());
        
        
        champName = LeagueHandler.transposeChampionNameForDataDragon(champName);
        eb.setThumbnail(LeagueHandler.getChampionProfilePic(champName));
        eb.setFooter("Do not run it down mid!", "https://cdn.discordapp.com/emojis/776346468700389436.png"); 

        event.getHook().editOriginalEmbeds(eb.build()).queue();
	}


    
    
}
