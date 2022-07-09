package com.safjnest.Commands.ManageGuild;

import java.util.List;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;

import net.dv8tion.jda.api.entities.Guild;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class ListGuild extends Command {

    public ListGuild(){
        this.name = this.getClass().getSimpleName();;
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

	@Override
	protected void execute(CommandEvent event) {
        List<Guild> guilds = event.getJDA().getGuilds();
        String list = "Il Bot è presente nei seguinti server:\n";
        for(Guild guild : guilds){
            list+="**"+guild.getName()+"** - ";
        }
        list = list.substring(0, list.length()-3);
        event.reply(list);
    }
}
