package com.safjnest.commands.League.slash;


import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.League.UltimateBravery;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class UltimateBraverySlash extends SlashCommand {
 
    /**
     * Constructor
     */
    public UltimateBraverySlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "champion", "Filter for a specific champion", false).setAutoComplete(true),
            new OptionData(OptionType.STRING, "lane", "Filter for a specific lane", false)
                .addChoice("Top lane", "0")
                .addChoice("Jungle", "1")
                .addChoice("Mid lane", "2")
                .addChoice("Bot lane", "3")
                .addChoice("Support", "4")
        );
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        
        String[] champions = RiotHandler.getRiotApi().getDDragonAPI().getChampions().values().stream().map(champ -> String.valueOf(champ.getId())).toArray(String[]::new);
        String[] roles = {"0", "1", "2", "3", "4"};

        if (event.getOption("champion") != null) {
            StaticChampion champ = RiotHandler.getChampionByName(event.getOption("champion").getAsString());
            champions = new String[]{String.valueOf(champ.getId())};
        }

        if (event.getOption("lane") != null) {  
            roles = new String[]{event.getOption("lane").getAsString()};
        }

        String json = RiotHandler.getBraveryBuildJSON(20, roles, champions);
        EmbedBuilder embed = UltimateBravery.createEmbed(json);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
	}



}
