package com.safjnest.commands.League.slash;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.UserData;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerUnlinkSlash extends SlashCommand {
    

    /**
     * Constructor
     */
    public SummonerUnlinkSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "personal_summoner", "Accont to unlink", true)
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

        UserData data = Bot.getUserData(event.getMember().getId());
        if (!data.getRiotAccounts().containsKey(account_id)) {
            event.deferReply(false).addContent("This account is not connected to your profile.").queue();
            return;
        }

        if (!data.deleteRiotAccount(account_id)) {
            event.deferReply(false).addContent("Something went wrong while disconnecting your account.").queue();
            return;
        }

        event.deferReply(false).addContent("Summoner removed").queue();
	}

}
