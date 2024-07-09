package com.safjnest.commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Disconnect extends Command {

    public Disconnect() {
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        
        this.botPermissions = new Permission[]{Permission.VOICE_MOVE_OTHERS};
        this.userPermissions = new Permission[]{Permission.VOICE_MOVE_OTHERS};

        commandData.setThings(this);
    }

	@Override
	protected void execute(CommandEvent event) {
        if(event.getArgs().equals("")) {
            event.getGuild().getAudioManager().closeAudioConnection();
            return;
        }
        
        Member mentionedMember = PermissionHandler.getMentionedMember(event, event.getArgs());
        if(mentionedMember == null) {
            event.reply("Couldn't find the specified member, please mention or write the id of a member.");
            return;
        }
        
        event.getGuild().kickVoiceMember(mentionedMember).queue();
	}
}