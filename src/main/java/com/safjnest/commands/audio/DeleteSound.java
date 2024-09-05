package com.safjnest.commands.audio;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * The command lets you delete a sound from the server.
 * <p>You have to be a server admin to use the command.</p>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class DeleteSound extends SlashCommand{
    
    public DeleteSound(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to delete", true)
        );

        commandData.setThings(this);
    }
    
	@Override
	protected void execute(SlashCommandEvent event) {
        String fileName = event.getOption("sound").getAsString();

        QueryResult sounds = fileName.matches("[0123456789]*") 
                           ? DatabaseHandler.getSoundsById(fileName, event.getGuild().getId(), event.getMember().getId()) 
                           : DatabaseHandler.getSoundsByName(fileName, event.getGuild().getId(), event.getMember().getId());

        if(sounds.isEmpty()) {
            event.reply("Couldn't find a sound with that name/id.");
            return;
        }

        if(sounds.size() > 1) {
            StringBuilder toSend = new StringBuilder("Two or more sounds with that name have been found, please use IDs.\n");
            for(ResultRow sound : sounds)
                toSend.append("**Sound:** " + sound.get("name") + " (ID: " + sound.get("id") + ") | **Guild:** " + event.getJDA().getGuildById(sound.get("guild_id")).getName() + " | **Author:** " + event.getGuild().getMemberById(sound.get("user_id")).getAsMention() + " | **Can you delete this:** " + ((!event.getUser().getId().equals(sound.get("user_id")) && !(event.getMember().hasPermission(Permission.ADMINISTRATOR) && event.getGuild().getId().equals(sound.get("guild_id")))) ? "no" : "yes") + "\n");
            event.deferReply(true).addContent(toSend.toString()).queue();
            return;
        }

        ResultRow toDelete = sounds.get(0);

        if(!event.getUser().getId().equals(toDelete.get("user_id")) && !(event.getMember().hasPermission(Permission.ADMINISTRATOR) && event.getGuild().getId().equals(toDelete.get("guild_id")))) {
            event.deferReply(true).addContent("You don't have permission to delete this sound.").queue();
            return;
        }

        DatabaseHandler.deleteSound(toDelete.get("id"));

        event.deferReply(false).addContent(name + " (ID: " + toDelete.get("id") +  ") has been deleted.").queue();
	}
}