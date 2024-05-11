package com.safjnest.SlashCommands.League;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SQL.DatabaseHandler;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerDisconnectSlash extends SlashCommand {
    

    /**
     * Constructor
     */
    public SummonerDisconnectSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "personal_summoner", "Accont to disconnect", true)
                .setAutoComplete(true));
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        String account_id = event.getOption("personal_summoner") != null ? event.getOption("personal_summoner").getAsString() : ""; 
        if (account_id.isEmpty()) {
            event.deferReply(false).addContent("You dont have a Riot account connected, for more information use /help summoner").queue();
            return;
        }
        System.out.println(account_id);
        DatabaseHandler.deleteLOLaccount(event.getMember().getId(), account_id);
        event.deferReply(false).addContent("Summoner removed").queue();
	}

}
