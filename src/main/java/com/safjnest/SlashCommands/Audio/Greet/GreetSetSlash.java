package com.safjnest.SlashCommands.Audio.Greet;


import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class GreetSetSlash extends SlashCommand{

    public GreetSetSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to use", true)
                        .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, "global", "true for global, false or null for guildonly", false));
    }

	@Override
	public void execute(SlashCommandEvent event) {
        String sound = event.getOption("sound").getAsString();

        boolean global = false;
        if(event.getOption("global") != null){
            global = event.getOption("global").getAsBoolean();
        }
        
        String guildId = event.getGuild().getId();
        if(global){
            guildId = "0";
        }

        String query = "INSERT INTO greeting (user_id, guild_id, bot_id, sound_id) VALUES ('" + event.getUser().getId() + "', '" + guildId + "', '" + event.getJDA().getSelfUser().getId() + "', '" + sound + "') ON DUPLICATE KEY UPDATE sound_id = '" + sound + "';";
        System.out.println(query);
        DatabaseHandler.getSql().runQuery(query);

        event.deferReply(false).addContent("Greet has been set").queue();
    }

    

    

    
   
}
