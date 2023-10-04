package com.safjnest.SlashCommands.Audio.Greet;



import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SQL.DatabaseHandler;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.1
 */
public class GreetUnsetSlash extends SlashCommand{

    public GreetUnsetSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
    }

	@Override
	public void execute(SlashCommandEvent event) {
        
        String query = "DELETE from greeting where (guild_id = '" + event.getGuild().getId() + "' OR guild_id = '0') AND user_id = '" + event.getUser().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        System.out.println(query);
        DatabaseHandler.getSql().runQuery(query);

        event.deferReply(false).addContent("Greet has been unset").queue();
    }

    

    

    
   
}
