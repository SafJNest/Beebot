package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.User;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.0
 */
public class Disconnect extends Command {

    public Disconnect(){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        User theGuy = null;
        if(event.getMessage().getMentionedMembers().size() == 0){
            event.reply("Non hai menzionato nessuno");
            return;
        }else if(event.getArgs().equalsIgnoreCase("bot")){
            event.getGuild().getAudioManager().closeAudioConnection();
        }else{
            theGuy = event.getMessage().getMentionedMembers().get(0).getUser();
            event.getGuild().kickVoiceMember(event.getGuild().getMember(theGuy)).queue();
        }
		
	}
    


}