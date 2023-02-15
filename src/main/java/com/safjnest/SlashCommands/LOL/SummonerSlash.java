package com.safjnest.SlashCommands.LOL;

import java.awt.Color;
import java.util.Arrays;
    
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.App;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.LOLHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerSlash extends SlashCommand {
 
    /**
     * Constructor
     */
    public SummonerSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.options = Arrays.asList(new OptionData(OptionType.STRING, "user", "Summoner name you want to get data", false));
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;
        event.deferReply(true).queue();
        if(event.getOption("user") == null){
            s = LOLHandler.getSummonerFromDB(event.getUser().getId());
            if(s == null){
                event.reply("You dont have connected a Riot account, for more information /help setUser");
                return;
            }
        }else{
            s = LOLHandler.getSummonerByName(event.getOption("user").getAsString());
            if(s == null){
                event.reply("Didn't find this user. ");
                return;
            }
            
        }
        
        
        try {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setAuthor(s.getName());
            builder.setColor(Color.decode(App.color));
            builder.setThumbnail("https://ddragon.leagueoflegends.com/cdn/12.22.1/img/profileicon/"+s.getProfileIconId()+".png");
            builder.addField("Level:", String.valueOf(s.getSummonerLevel()), false);
            builder.addField("5v5 Ranked Solo", LOLHandler.getSoloQStats(s), true);
            builder.addField("5v5 Ranked Flex Queue", LOLHandler.getFlexStats(s), true);
            String masteryString = "";
            for(int i = 1; i < 4; i++)
                masteryString += LOLHandler.getMastery(s, i) + "\n";
            
            builder.addField("Top 3 Champ", masteryString, false); 
            builder.addField("Activity", LOLHandler.getActivity(s), true);
            event.getHook().editOriginalEmbeds(builder.build()).queue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }

	}

}
