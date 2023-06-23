package com.safjnest.SlashCommands.Settings.Leave;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DatabaseHandler;

public class LeavePreviewSlash extends SlashCommand{

    public LeavePreviewSlash(){
        this.name = "preview";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String query = "SELECT message_text FROM left_message WHERE discord_id = '" + event.getGuild().getId()
                            + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String message = DatabaseHandler.getSql().getString(query, "message_text");
        if(message == null){
            event.deferReply(false).addContent("You have not set a leave message yet.").queue();
            return;
        }

        query = "SELECT channel_id FROM left_message WHERE discord_id = '" + event.getGuild().getId()
                            + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String channel = DatabaseHandler.getSql().getString(query, "channel_id");

        message = message.replace("#user", event.getMember().getAsMention());
        message += "\n\nThis is a preview of the message that will be sent to the channel <#" + channel + ">.";
        event.deferReply(false).addContent(message).queue();
    }
    
}
