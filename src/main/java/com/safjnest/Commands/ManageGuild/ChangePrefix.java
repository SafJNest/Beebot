package com.safjnest.Commands.ManageGuild;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.PostgreSQL;

import net.dv8tion.jda.api.Permission;

public class ChangePrefix extends Command{
    private PostgreSQL sql;
    
    public ChangePrefix(PostgreSQL sql){
        this.name = this.getClass().getSimpleName();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.sql = sql;
    }

    @Override
    protected void execute(CommandEvent event) {
        if(!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Only admins can change the prefix of the guild");
            return;
        }
        String query = "INSERT INTO guild_prefix(guild_id, prefix)" + "VALUES('" + event.getGuild().getId() + "','" + event.getArgs() +"');";
        if(sql.runQuery(query))
            event.reply("All set correctly");
        else
            event.reply("Error");
    }
}
