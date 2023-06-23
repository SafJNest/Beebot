package com.safjnest.SlashCommands.Settings.Welcome;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.DatabaseHandler;
import com.safjnest.Utilities.SQL;
import com.safjnest.Utilities.Commands.CommandsLoader;

public class WelcomeDeleteSlash extends SlashCommand{
    
    public WelcomeDeleteSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        
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
