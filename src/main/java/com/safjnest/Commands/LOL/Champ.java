package com.safjnest.Commands.LOL;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;
/* 
import net.rithms.riot.constant.Region;
import net.rithms.riot.dto.Summoner.Summoner;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
*/

import net.rithms.riot.api.ApiConfig;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.static_data.dto.Champion;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.Platform;

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
        ApiConfig config = new ApiConfig().setKey("RGAPI-20fc9d9e-5735-44ac-93b1-2d20d00892bb");
        RiotApi api = new RiotApi(config);
        try {
            Champion c = api.getDataChampion(Platform.EUW, 1);
            Summoner player = api.getSummonerByName(Platform.EUW, "Sunyxd117");
            event.reply(c.getPassive().getSanitizedDescription());

        } catch (RiotApiException e) {
            e.printStackTrace();
            event.reply(e.getMessage());
        }
	}

}
