package com.safjnest.Commands.LOL;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;


/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Champ extends Command {
    
    /**
     * Constructor
     */
    public Champ(){
        this.name = this.getClass().getSimpleName();
        this.aliases = new JSONReader().getArray(this.name, "alias");
        this.help = new JSONReader().getString(this.name, "help");
        this.cooldown = new JSONReader().getCooldown(this.name);
        this.category = new Category(new JSONReader().getString(this.name, "category"));
        this.arguments = new JSONReader().getString(this.name, "arguments");
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(CommandEvent event) {
        /*
        ApiConfig config = new ApiConfig().setKey("RGAPI-9b579dd0-edeb-4afb-9804-7c31b066842f");
        RiotApi api = new RiotApi(config);
        try {
            Champion c = api.getDataChampion(Platform.EUW, 1);
            Summoner player = api.getSummonerByName(Platform.EUW, "Sunyxd117");
            event.reply(c.getPassive().getSanitizedDescription());

        } catch (RiotApiException e) {
            e.printStackTrace();
            event.reply(e.getMessage());
        }
         */
	}

}
