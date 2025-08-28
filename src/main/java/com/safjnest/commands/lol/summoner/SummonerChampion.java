package com.safjnest.commands.lol.summoner;

import java.util.Arrays;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.LeagueMessage;
import com.safjnest.util.lol.LeagueMessageType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

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
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to link", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "champion", "Champion Name", true).setAutoComplete(true),
            new OptionData(OptionType.STRING, "role", "Champion Role", true)
                .addChoice("Top", "TOP")
                .addChoice("Jungle", "JUNGLE")
                .addChoice("Mid", "MID")
                .addChoice("ADC", "ADC")
                .addChoice("Support", "SUPPORT"),
            LeagueHandler.getLeagueShardOptions()
        );
        commandData.setThings(this);
    }


  /**
  * This method is called every time a member executes the command.
  */
  @Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply().queue();
        //22 iodid
        //50 sunyx
        //150067 uglydemon
        //5 primeegis

        //80 pantheon
        //412 thresh
        //555 pyke

        no.stelar7.api.r4j.pojo.lol.summoner.Summoner summoner = LeagueHandler.getSummonerByArgs(event);
        int summonerId = LeagueDB.getSummonerIdByPuuid(summoner.getPUUID());

        String championName = event.getOption("champion").getAsString();
        StaticChampion champion = LeagueHandler.getChampionByName(championName);

        long[] split = LeagueHandler.getCurrentSplitRange();
        long timeStart = split[0];
        long timeEnd = split[1];

        GameQueueType queue = GameQueueType.TEAM_BUILDER_RANKED_SOLO;

        String laneString = event.getOption("role").getAsString();
        LaneType laneType = null;
        switch(laneString){
            case "TOP":
                laneType = LaneType.TOP;
                break;
            case "JUNGLE":
                laneType = LaneType.JUNGLE;
                break;
            case "MID":
                laneType = LaneType.MID;
                break;
            case "ADC":
                laneType = LaneType.BOT;
                break;
            case "SUPPORT":
                laneType = LaneType.UTILITY;
                break;
        }

        LeagueMessage.sendChampionMessage(event.getHook(), event.getUser().getId(), LeagueMessageType.CHAMPION_GENERIC, summoner, summonerId, champion, timeStart, timeEnd, queue, laneType, true);
	}

}
