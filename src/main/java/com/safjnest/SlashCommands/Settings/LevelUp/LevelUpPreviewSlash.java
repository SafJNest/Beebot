package com.safjnest.SlashCommands.Settings.LevelUp;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.DatabaseHandler;

public class LevelUpPreviewSlash extends SlashCommand{

    public LevelUpPreviewSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
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
