package com.safjnest.commands.lol.summoner;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.message.LeagueMessage;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.lol.service.LeagueService;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class SummonerChampion extends SlashCommand {
    
    /**
     * Constructor
     */
    public SummonerChampion(String father){
      this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

      BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
      
      this.help = commandData.getHelp();
      this.cooldown = commandData.getCooldown();
      this.category = commandData.getCategory();

      this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

      this.options = Arrays.asList(
          new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to link", false).setAutoComplete(true),
          LeagueShardUtils.getAsOptions()
      );
      commandData.setThings(this);
    }

  @Override
	protected void execute(SlashCommandEvent event) {
    event.deferReply().queue();
    no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner = LeagueHandler.getSummonerByArgs(event);
    int summonerId = LeagueService.getSummonerIdByPuuid(summoner.getPUUID(), summoner.getPlatform());

    String userId = UserCache.getUser(event.getUser().getId()).getRiotAccounts().get(summoner.getPUUID()) != null ? event.getUser().getId() : null;
    LeagueMessage.send(event.getHook(), userId, summoner, summonerId, new LeagueMessageParameter(LeagueMessageType.OVERVIEW_CHAMPIONS));
	}

  @Override
	protected void execute(CommandEvent event) {
    no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner = LeagueHandler.getSummonerByArgs(event);
    int summonerId = LeagueService.getSummonerIdByPuuid(summoner.getPUUID(), summoner.getPlatform());

    String userId = UserCache.getUser(event.getAuthor().getId()).getRiotAccounts().get(summoner.getPUUID()) != null ? event.getAuthor().getId() : null;
    LeagueMessage.send(event, userId, summoner, summonerId, new LeagueMessageParameter(LeagueMessageType.OVERVIEW_CHAMPIONS));
	}

}
