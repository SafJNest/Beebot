package com.safjnest.SlashCommands.Settings.Leave;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DatabaseHandler;

public class LeaveDeleteSlash extends SlashCommand{

    public LeaveDeleteSlash(){
        this.name = "delete";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String query = "DELETE from left_message WHERE discord_id = '" + event.getGuild().getId()
                           + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        DatabaseHandler.getSql().runQuery(query);
        event.deferReply(false).addContent("Left message disable successfully").queue();
    }
    
}
