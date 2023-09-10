package com.safjnest.SlashCommands.Audio.Soundboard;


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
 * @since 3.0
 */
public class SoundboardRemoveSlash extends SlashCommand{

    public SoundboardRemoveSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Name of soundboard to change", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "sound_remove", "Sound to remove", true).setAutoComplete(true));
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        
        String sound = event.getOption("sound_remove").getAsString();
        String id = event.getOption("name").getAsString();
        String query = "SELECT count(sound_id) as cont FROM soundboard_sounds WHERE id = '" + id + "'";
        int n = Integer.parseInt(DatabaseHandler.getSql().getString(query, "cont"));

        query = "DELETE FROM soundboard_sounds WHERE id = '" + id + "' AND sound_id = '" + sound + "'";
        if(!DatabaseHandler.getSql().runQuery(query)){
            event.deferReply(false).addContent("Error adding sound. Check if the sound has already been added or got deleted.").queue();
            return;
        }
        if(n == 1){
            query = "DELETE FROM soundboard WHERE id = '" + id + "'";
            DatabaseHandler.getSql().runQuery(query);
            event.deferReply(false).addContent("The soundboard has been deleted because was empty.").queue();
            return;
        }
        event.deferReply(false).addContent("Sound deleted correctly.").queue();

    }    
}
