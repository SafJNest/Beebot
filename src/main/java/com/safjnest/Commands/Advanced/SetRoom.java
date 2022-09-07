package com.safjnest.Commands.Advanced;

import com.safjnest.Utilities.JSONReader;
import com.safjnest.Utilities.PostgreSQL;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class SetRoom extends Command {
    private PostgreSQL sql;

    public SetRoom(PostgreSQL sql) {
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
        this.sql = sql;
    }

    @Override
    protected void execute(CommandEvent event) {
        String name = null;
        String channel = null;
        String argsArr[] = event.getArgs().split(" ",2);
        String args = event.getArgs();
        if(args.contains("<")){
            channel = args.substring(args.indexOf("<")+2, args.indexOf("<")+20);
        }else{
            if(event.getGuild().getVoiceChannelById(argsArr[0])==null){
                event.reply("ID del canale non valido");
                return;
            }
            channel = argsArr[0];
        }
        name = argsArr[1];
        String query = "INSERT INTO rooms_nickname(discord_id, room_id, room_name)"
                            + "VALUES('" + event.getGuild().getId() + "','" + channel +"','" + name + "');";
        if(sql.addElement(query))
            event.reply("Tutto okay capo");
        else
            event.reply("what faker is this?");
    }
}