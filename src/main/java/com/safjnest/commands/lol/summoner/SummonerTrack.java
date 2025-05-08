package com.safjnest.commands.lol.summoner;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.UserData;
import com.safjnest.sql.LeagueDBHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerTrack extends SlashCommand {
    

    public SummonerTrack(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "personal_summoner", "Accont to unlink", true)
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, "track", "Enable or disable tracking", false)
        );
        commandData.setThings(this);
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(SlashCommandEvent event) {
        String account_id = event.getOption("personal_summoner") != null ? event.getOption("personal_summoner").getAsString() : "";
        boolean track = event.getOption("track") != null ? event.getOption("track").getAsBoolean() : true;
        if (account_id.isEmpty()) {
            event.deferReply(false).addContent("You dont have a Riot account connected, for more information use /help summoner").queue();
            return;
        }

        UserData data = UserCache.getUser(event.getMember().getId());
        if (!data.getRiotAccounts().containsKey(account_id)) {
            event.deferReply(false).addContent("This account is not connected to your profile.").queue();
            return;
        }

        LeagueDBHandler.trackSummoner(event.getMember().getId(), account_id, track);
        
        String response = track ? "Tracking enabled" : "Tracking disabled";
        event.deferReply(false).addContent(response).queue();
	}

}
