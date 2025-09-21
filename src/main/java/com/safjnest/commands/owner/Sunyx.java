package com.safjnest.commands.owner;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.BlacklistData;
import com.safjnest.model.guild.ChannelData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.MatchTracker;
import com.safjnest.util.lol.LeagueHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import org.json.JSONObject;

import java.util.*;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.3
 */
public class Sunyx extends Command {

  public Sunyx() {
    this.name = this.getClass().getSimpleName().toLowerCase();

    BotCommand commandData = CommandsLoader.getCommand(this.name);

    this.aliases = commandData.getAliases();
    this.help = commandData.getHelp();
    this.cooldown = commandData.getCooldown();
    this.category = commandData.getCategory();
    this.arguments = commandData.getArguments();

    this.ownerCommand = true;
    this.hidden = true;
    commandData.setThings(this);
  }

  @Override
  protected void execute(CommandEvent e) {
    String args[] = e.getArgs().split(" ", 2);
    switch (args[0].toLowerCase()) {
      case "13":
        tredici(e);
        break;
      case "14":
        quattordici(e);
        break;
      case "reloademoji":
        CustomEmojiHandler.loadEmoji();
        e.reply("Done");
        break;
      case "disablecommands":
        e.getGuild().getTextChannels().forEach(c -> {
          GuildCache.getGuildOrPut(e.getGuild()).getChannelData(c.getId()).setCommand(false);
        });
        break;
      case "disablecommand":
        GuildCache.getGuildOrPut(e.getGuild()).getChannelData(e.getTextChannel().getId()).setCommand(false);
        break;
      case "enablecommand":
        GuildCache.getGuildOrPut(e.getGuild()).getChannelData(e.getTextChannel().getId()).setCommand(true);
        break;
      case "enablecommands":
        e.getGuild().getTextChannels().forEach(c -> {
          GuildCache.getGuildOrPut(e.getGuild()).getChannelData(c.getId()).setCommand(true);
        });
        break;
      case "fixlol":
        fixLOL(e);
        break;
      case "lolqueue":
        System.out.println(MatchTracker.getMatchQueueCopy().size());
        break;
      case "pushlolqueue":
        ChronoTask task = () -> MatchTracker.popSet();
        task.queue();
        break;
      case "pushsamplegame":
        ChronoTask sampleTask = () -> MatchTracker.retriveSampleGames();
        sampleTask.queue();
        break;
      case "retriveallgames":
        ChronoTask retriveAllGames = () -> {
          MatchTracker.retriveMatchHistory(LeagueHandler.getSummonerByPuuid(args[1],
              GuildCache.getGuild(e.getGuild()).getChannelData(e.getChannel().getId()).getLeagueShard()));
        };
        retriveAllGames.queue();
        break;
    }
  }

  private void tredici(CommandEvent e) {
    HashMap<AlertKey<?>, AlertData> prova = GuildCache.getGuildOrPut(e.getGuild().getId()).getAlerts();
    String s = new JSONObject(prova).toString();
    e.reply("```json\n" + GuildCache.getGuildOrPut(e.getGuild().getId()).toString() + "```");
    e.reply("```json\n" + s + "```");
    BlacklistData bd = GuildCache.getGuildOrPut(e.getGuild().getId()).getBlacklistData();
    e.reply("```json\n" + bd.toString() + "```");
    HashMap<String, ChannelData> channels = GuildCache.getGuildOrPut(e.getGuild().getId()).getChannels();
    e.reply("```json\n" + new JSONObject(channels).toString() + "```");
    e.reply(
        "```json\n" + new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getMembers()).toString() + "```");
    e.reply("```json\n" + new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getActionsWithId()).toString()
        + "```");
  }

  private void quattordici(CommandEvent e) {
    for (Guild g : e.getJDA().getGuilds()) {
      GuildCache.getGuildOrPut(g.getId()).getAlerts();
      GuildCache.getGuildOrPut(g.getId()).getBlacklistData();
      for (GuildChannel cd : g.getChannels()) {
        GuildCache.getGuildOrPut(g.getId()).getChannelData(cd.getId());
      }
      for (Member m : g.getMembers()) {
        GuildCache.getGuildOrPut(g.getId()).getMemberData(m.getId());
        UserCache.getUser(m.getId());
      }
    }
    e.reply("Done");
  }

  private void fixLOL(CommandEvent e) {
    String query = "SELECT id, game_id, league_shard from `match` order by id desc";
    QueryResult result = LeagueDB.get().query(query);
    System.out.println("total match: " + result.size());
    int aaa = 0;
    for (QueryRecord row : result) {
      String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
      String game_id = region + "_" + row.get("game_id");
      // String account_id = row.get("account_id");
      // String summoner_id = row.get("summoner_id");
      LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI()
          .getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
      String puuid = "";

      LaneType lane = null;
      TeamType team = null;
      // Summoner su = LeagueHandler.getSummonerByPuuid(account_id,
      // LeagueShard.values()[row.getAsInt("league_shard")]);
      for (MatchParticipant participant : match.getParticipants()) {
        QueryRecord record = LeagueDB.get().lineQuery(
            "select s.id, p.build from summoner s left join participant p on s.id = p.summoner_id and p.match_id = "
                + row.get("id") + " where s.puuid = '" + participant.getPuuid() + "' and s.league_shard = "
                + row.getAsInt("league_shard") + ";");
        int sumId = record != null ? record.getAsInt("id") : 0;
        if (sumId == 0)
          continue;

        JSONObject build = new JSONObject(
            record.get("build") != null && !record.get("build").isEmpty() ? record.get("build") : "{}");
        HashMap<Integer, Integer> items = new HashMap<Integer, Integer>();
        items.put(0, participant.getItem0());
        items.put(1, participant.getItem1());
        items.put(2, participant.getItem2());
        items.put(3, participant.getItem3());
        items.put(4, participant.getItem4());
        items.put(5, participant.getItem5());
        items.put(6, participant.getItem6());
        build.put("items", items);

        query = "UPDATE participant SET build='" + build.toString() + "' WHERE summoner_id=" + sumId
            + " AND match_id=" + row.get("id") + ";";
        LeagueDB.get().query(query);
      }
      System.out.println("total match: " + aaa + "( " + row.get("id") + ") / " + result.size());
      aaa++;
    }
  }
}
