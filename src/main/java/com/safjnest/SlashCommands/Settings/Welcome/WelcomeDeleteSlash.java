package com.safjnest.SlashCommands.Settings.Welcome;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.SQL;

public class WelcomeDeleteSlash extends SlashCommand{
    
    public WelcomeDeleteSlash(){
        this.name = "delete";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        SQL sql = DatabaseHandler.getSql();
        String query = "DELETE FROM welcome_message WHERE discord_id = '" + event.getGuild().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        sql.runQuery(query);
        query = "DELETE FROM welcome_roles WHERE discord_id = '" + event.getGuild().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        sql.runQuery(query);
        event.deferReply(false).addContent("All deleted correctly").queue();
    }
    
}
