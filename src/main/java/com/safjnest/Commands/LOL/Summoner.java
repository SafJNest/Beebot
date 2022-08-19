package com.safjnest.Commands.LOL;

import java.awt.Color;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.JSONReader;
/* 
import net.rithms.riot.constant.Region;
import net.rithms.riot.dto.Summoner.Summoner;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
*/

import net.dv8tion.jda.api.EmbedBuilder;
import net.rithms.riot.api.ApiConfig;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.static_data.dto.Champion;
import net.rithms.riot.constant.Platform;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Summoner extends Command {
    
    /**
     * Constructor
     */
    public Summoner(){
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
        String name = event.getArgs();
        try {
            net.rithms.riot.api.endpoints.summoner.dto.Summoner player = api.getSummonerByName(Platform.EUW, name);
            EmbedBuilder eb = new EmbedBuilder();
            eb = new EmbedBuilder();
            eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl());
            eb.setTitle("Summoner: " + player.getName());
            eb.addField("Livello", String.valueOf(player.getSummonerLevel()), true);
            eb.setColor(new Color(255, 0, 0));
            eb.setFooter("*Questo non e' rhythm, questa e' perfezione cit. steve jobs (probabilmente)", null);
            //eb.setThumbnail(api.getDataProfileIcons(platform, locale, version));
            event.reply(eb.build());

        } catch (RiotApiException e) {
            e.printStackTrace();
            event.reply(e.getMessage());
        }
	}

}
