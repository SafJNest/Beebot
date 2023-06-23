package com.safjnest.SlashCommands.Settings.LevelUp;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DatabaseHandler;

public class LevelUpPreviewSlash extends SlashCommand{

    public LevelUpPreviewSlash(){
        this.name = "preview";
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String query = "SELECT message_text FROM levelup_message WHERE discord_id = '" + event.getGuild().getId() + "';";
        String message = DatabaseHandler.getSql().getString(query, "message_text");
        if(message == null){
            event.deferReply(false).addContent("You have not set a LevelUp message yet.").queue();
            return;
        }
        message = message.replace("#user", event.getUser().getAsMention());
        message = message.replace("#level", String.valueOf(117));
        event.deferReply(false).addContent(message).queue();
        
    }
    
}
