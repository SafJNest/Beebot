package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.PermissionHandler;

import net.dv8tion.jda.api.entities.Member;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Disconnect extends Command {

    public Disconnect(){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        if(event.getArgs() == ""){
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