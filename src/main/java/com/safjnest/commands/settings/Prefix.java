package com.safjnest.commands.settings;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.1
 */
public class Prefix extends Command{
    private GuildDataHandler gs;
    
    public Prefix(GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.userPermissions = new Permission[]{Permission.ADMINISTRATOR};

        commandData.setThings(this);
        this.gs = gs;
    }

    @Override
    protected void execute(CommandEvent event) {
        String prefix = event.getArgs();
        if(prefix.equals("")) {
            event.reply("Write the new prefix.");
            return;
        }
        String guildId = event.getGuild().getId();
        
        if(gs.getGuild(guildId).setPrefix(prefix)){
            event.reply("The new prefix is: " + prefix);
        }
        else
            event.reply("Couldn't change the prefix due to an unknown error, please try again later or report this with /bugsnotifier.");
    }
}