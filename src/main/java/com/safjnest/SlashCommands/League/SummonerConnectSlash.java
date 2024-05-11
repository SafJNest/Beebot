package com.safjnest.SlashCommands.League;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerConnectSlash extends SlashCommand {
    
    /**
     * Constructor
     */
    public SummonerConnectSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name of the summoner you want to get information on", true),
            new OptionData(OptionType.STRING, "tag", "Tag of the summoner you want to get information on", false),
            RiotHandler.getLeagueShardOptions());
    }

    /**
     * This method is called every time a member executes the command.
     */
	@SuppressWarnings("unused")
    @Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = RiotHandler.getSummonerByArgs(event);

        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to use the tag or connect an account.").queue();
            return;
        }

        String name = event.getOption("summoner").getAsString();
        String tag = event.getOption("tag").getAsString();

        LeagueShard shard = s.getPlatform();

        DatabaseHandler.addLOLAccount(event.getMember().getId(), s.getSummonerId(), s.getAccountId(), shard);
        event.getHook().editOriginal("Connected " + name + " #" + tag + " to your profile.").queue();

	}

}
