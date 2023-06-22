package com.safjnest.SlashCommands.Settings.Welcome;

import java.util.ArrayList;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.SQL;

public class WelcomePreviewSlash extends SlashCommand{

    public WelcomePreviewSlash(){
        this.name = "preview";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        SQL sql = DatabaseHandler.getSql();
        
        String query = "SELECT message_text, channel_id FROM welcome_message WHERE discord_id = '" + event.getGuild().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';"; 
        ArrayList<String> list = sql.getSpecifiedRow(query, 0);
        if(list == null){
            event.deferReply(false).addContent("You need to set a welcome message first.").queue();
            return;
        }

        query = "SELECT role_id FROM welcome_roles WHERE discord_id = '" + event.getGuild().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        ArrayList<String> roles = sql.getAllRowsSpecifiedColumn(query, "role_id");

        String message = list.get(0).replace("#user", event.getUser().getAsMention());
        message = message + "\nThis message would be sent to <#" + list.get(1) + ">";

        if(roles != null){
            message = message + "\nRoles that would be given to the user:";
            for(String role : roles){
                message = message + "\n<" + event.getGuild().getRoleById(role).getName() + ">";
            }
        }

        event.deferReply(false).addContent(message).queue();
        
    }
    
}
