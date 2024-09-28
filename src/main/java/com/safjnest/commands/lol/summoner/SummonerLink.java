package com.safjnest.commands.lol.summoner;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.UserData;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerLink extends SlashCommand {
    
    /**
     * Constructor
     */
    public SummonerLink(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to link", true).setAutoComplete(true),
            LeagueHandler.getLeagueShardOptions()
        );
        commandData.setThings(this);
    }

    /**
     * This method is called every time a member executes the command.
     */
    @Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = LeagueHandler.getSummonerByArgs(event);

        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag").queue();
            return;
        }

        String name = event.getOption("summoner").getAsString();

        UserData data = Bot.getUserData(event.getMember().getId());
        if(data.getRiotAccounts().containsKey(s.getAccountId())){
            event.getHook().editOriginal("This account is already connected to your profile.").queue();
            return;
        }

        if (DatabaseHandler.getUserIdByLOLAccountId(s.getAccountId(), s.getPlatform()) != null) {
            event.getHook().editOriginal("This account is already connected to another profile.\nIf you think someone has linked your account please write to our discord server support or use /bug").queue();
            return;
        }

       

        if (!data.addRiotAccount(s)) {
            event.getHook().editOriginal("Something went wrong while connecting your account.").queue();
            return;
        }

        event.getHook().editOriginal("Connected " + name + " to your profile.").queue();

	}

}
