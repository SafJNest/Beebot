package com.safjnest.commands.owner;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

import com.github.twitch4j.eventsub.events.StreamOnlineEvent;
import com.github.twitch4j.eventsub.socket.IEventSubConduit;
import com.github.twitch4j.eventsub.socket.conduit.TwitchConduitSocketPool;
import com.github.twitch4j.eventsub.subscriptions.SubscriptionTypes;
import com.github.twitch4j.helix.domain.Stream;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.safjnest.core.Bot;
import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.ResultHandler;
import com.safjnest.core.audio.SafjAudioPlaylist;
import com.safjnest.core.audio.types.PlayTiming;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.core.cache.managers.UserCache;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.PlayerChampionStats;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.service.BuildService;
import com.safjnest.lol.service.ChampionStatsService;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.tracker.Tracker;
import com.safjnest.lol.tracker.TrackerScheduler;
import com.safjnest.lol.tracker.TrackerState;
import com.safjnest.lol.tracker.TrackerState.Priority;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.BlacklistData;
import com.safjnest.model.guild.ChannelData;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.PermissionHandler;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.log.BotLogger;
import com.safjnest.utils.twitch.TwitchClient;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.basic.constants.api.URLEndpoint;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.JSONObject;


import java.util.*;

import java.sql.Timestamp;
/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.3
 */
public class Test extends Command{

    public Test(){
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

    @SuppressWarnings({ "unused" })
    @Override
    protected void execute(CommandEvent e) {
        String[] bots = {"938487470339801169", "983315338886279229", "939876818465488926", "1098906798016184422", "1074276395640954942"};
        QueryResult res;
        String query = "";

        String args[] = e.getArgs().split(" ", 2);

        //File soundBoard = new File("rsc" + File.separator + "SoundBoard");
        //File[] files = soundBoard.listFiles();
        switch (args[0].toLowerCase()){
            case "list":
                e.reply("timer | chart | members | prime | getInvites | createInvite | getGuildsWithInvites | getLolItems " 
                    + "| renameFile | renameFiles | closeDatabase | getBlacklist | printJson | cacheThings | getServer | stats"
                    + "| insertEpriaInBlacklist | insertAlert | insertUser | trackScheduler | playPlaylist | fixmmr");
            break;
            case "timer":
                Timer timer = new Timer();
                /* 
                LocalDate currentDate = LocalDate.now();
                LocalDate nextMonth = currentDate.withDayOfMonth(1).plusMonths(1);
                LocalTime midnight = LocalTime.MIDNIGHT;

                LocalDateTime scheduledDateTime = LocalDateTime.of(nextMonth, midnight);

                long initialDelay = Duration.between(LocalDateTime.now(), scheduledDateTime).toMillis();
                long period = Duration.ofDays(30).toMillis(); 

                timer.schedule(new MonthlyTask(), initialDelay, period);
                */
                LocalDate currentDate = LocalDate.now();
                LocalTime currentTime = LocalTime.now();
                LocalTime eventTime = currentTime.plusMinutes(1); // Un minuto da adesso

                LocalDateTime scheduledDateTime = LocalDateTime.of(currentDate, eventTime);

                long initialDelay = Duration.between(LocalDateTime.now(), scheduledDateTime).toMillis();
                long period = Duration.ofDays(30).toMillis(); // Ripetizione ogni 30 giorni
                System.out.println(initialDelay);
                System.out.println(scheduledDateTime.getDayOfMonth());
                timer.schedule(new MonthlyTask(), initialDelay, period);
            break;

            case "chart":
                createAndSaveChartAsPNG();
            break;
            case "members":
                for(Member m : e.getJDA().getGuildById(args[1]).getMembers()){
                    System.out.println(m.getEffectiveName() + " " + m.getId());
                }
            break;
            case "prime":
                e.reply(SafJNest.getRandomPrime(Integer.parseInt(args[1])).toString());
            break;
            case "getinvites":
                Guild guildd = e.getJDA().getGuildById(args[1]);
                StringBuilder invites = new StringBuilder();
                for(Invite invite : guildd.retrieveInvites().complete()) {
                    invites.append("code: " + invite.getCode() 
                              + " - max age: " + invite.getMaxAge() + "s"
                              + " - max uses: " + invite.getMaxUses() 
                              + " - uses: " + invite.getUses()
                            + ((invite.getChannel() != null) 
                             ? (" - channel: " + invite.getChannel().getName()) : "")
                            + ((invite.getGroup() != null) 
                             ? (" - group: " + invite.getGroup().getName()) : "")
                              + " - inviter: " + invite.getInviter().getGlobalName()
                              + " - target type: " + invite.getTargetType()
                            + ((invite.getTarget() != null && invite.getTarget().getUser() != null) 
                             ? (" - target user: " + invite.getTarget().getUser().getName()) : "")
                              + " - is temporary: " + invite.isTemporary()
                              + " - time created: " + "<t:" + invite.getTimeCreated().toEpochSecond() + ":d>" + "\n");
                }

                e.reply("here are the invites for " + guildd.getName() + " (" + guildd.getId() + "):\n" + invites);
            break;
            case "createinvite":
                String invitess = "";
                for(Invite invite : e.getJDA().getGuildById(args[1]).retrieveInvites().complete()) {
                    invitess += invite.getUrl() + "\n";
                    e.reply("here are the invites:\n" + invitess);
                }
                if(invitess.equals("")) {
                    invitess = e.getJDA().getGuildById(args[1]).getDefaultChannel().createInvite().complete().getUrl();
                    e.reply("here is the created invite:\n" + invitess);
                }
            break;
            case "getguildswithinvites":
                User self = e.getJDA().getSelfUser();
                List<Guild> guilds = new ArrayList<>(e.getJDA().getGuilds());
                guilds.sort((g1, g2) -> {
                    return Long.compare(g1.getMember(self).getTimeJoined().toEpochSecond(), g2.getMember(self).getTimeJoined().toEpochSecond());
                });
                String guildlist = "";
                for(Guild guild : guilds){
                    if(guild.getName().startsWith("BeebotLOL") || !guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER))
                        continue;

                    List<Invite> guildinvites = guild.retrieveInvites().complete();
                    if(!guildinvites.isEmpty()) {
                        guildlist += "<t:" + guild.getMember(self).getTimeJoined().toEpochSecond() + ":d> - **" + guild.getName() + "** (" + guild.getId() + ")";
                        guildlist += " - " + guildinvites.get(0).getCode() + " - " + guildinvites.get(0).getMaxAge() + " - " + guildinvites.get(0).getMaxUses();
                        guildlist += "\n";
                    }
                }
                e.reply("Guilds with invites:\n" + guildlist);
            break;
            case "getlolitems":
                System.out.println("eee");
                String ss = "";
                for (Item item : LeagueHandler.getRiotApi().getDDragonAPI().getItems().values()) {
                    System.out.println(item.getId());
                    if (item != null)
                        ss += CustomEmojiHandler.getFormattedEmoji(item.getId()) + "-";
                }
                System.out.println("efee");
                e.reply(ss);

            break;
            case "renamefile":
                // for(File file : files){
                //     String name = file.getName().split("\\.")[0];
                //     String extension = file.getName().split("\\.")[1];
                //     String newName = String.valueOf(Integer.valueOf(name) + 1000);
                //     file.renameTo(new File(soundBoard + File.separator + newName + "." + extension));

                // }
            break;
            case "renamefiles":
                // for(File file : files){
                //     String name = file.getName().split("\\.")[0];
                //     String extension = file.getName().split("\\.")[1];

                //     String query = "SELECT * FROM sound WHERE id = " + name + ";";
                //     ResultRow res = BotDB.lineQuery(query);
                //     String newName = res.get("new_id");
                //     file.renameTo(new File(soundBoard + File.separator + newName + "." + extension));

                // }
            break;
            case "closedatabase":
                // try {
                //     BotDB.get().getConnection().close();
                // } catch (SQLException e1) {
                //     e1.printStackTrace();
                // }
            break;
            case "getBlacklist":
                System.out.println(GuildCache.getGuildOrPut(e.getGuild().getId()).getBlacklistData().toString());
                break;
            case "13":
                HashMap<AlertKey<?>, AlertData> prova = GuildCache.getGuildOrPut(e.getGuild().getId()).getAlerts();
                String s = new JSONObject(prova).toString();
                e.reply("```json\n" + GuildCache.getGuildOrPut(e.getGuild().getId()).toString() + "```");
                e.reply("```json\n" + s + "```");
                BlacklistData bd = GuildCache.getGuildOrPut(e.getGuild().getId()).getBlacklistData();
                e.reply("```json\n" + bd.toString()+ "```");
                HashMap<String, ChannelData> channels = GuildCache.getGuildOrPut(e.getGuild().getId()).getChannels();
                e.reply("```json\n" + new JSONObject(channels).toString() + "```");
                e.reply("```json\n" + new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getMembers()).toString() + "```");
                e.reply("```json\n" + new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getActionsWithId()).toString() + "```");
                break;
            case "14":
                for(Guild g : e.getJDA().getGuilds()) {
                    GuildCache.getGuildOrPut(g.getId()).getAlerts();
                    GuildCache.getGuildOrPut(g.getId()).getBlacklistData();
                    for(GuildChannel cd : g.getChannels()) {
                        GuildCache.getGuildOrPut(g.getId()).getChannelData(cd.getId());
                    }
                    for(Member m : g.getMembers()){
                        GuildCache.getGuildOrPut(g.getId()).getMemberData(m.getId());
                        UserCache.getUser(m.getId());
                    }
                }
                e.reply("Done");
                break;
            case "getServer":
                String sss = new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getChannels()).toString();
                e.reply("```json\n" + sss + "```");
                break;
            case "stats":
                query = "SELECT guild_id, room_id FROM room WHERE has_command_stats = 0";
                res = BotDB.get().query(query);
                for(QueryRecord row : res){
                    for (String bot : bots) {
                        query = "INSERT INTO channel(guild_id, channel_id, bot_id, stats_enabled) VALUES (" + row.get("guild_id") + ", "+ row.get("room_id") +", " + bot + ", 0)";
                        BotDB.get().query(query);
                    }
                }
                e.reply("Done");
                break;
            case "insertepriainblacklist":
                query = "SELECT id FROM guilds";
                res = BotDB.get().query(query);;
                for(QueryRecord row : res){
                    query = "INSERT INTO blacklist(guild_id, user_id) VALUES (" + row.get("id") + "," + PermissionHandler.getEpria() + ")";
                    BotDB.get().query(query);;
                }
                break;
            case "insertalert":
                query = "SELECT guild_id, role_id, level, message_text FROM reward";
                res = BotDB.get().query(query);;
                for(QueryRecord row : res){
                    int id = 0;
                    java.sql.Connection c = BotDB.get().getConnection();
                    try (Statement stmt = c.createStatement()) {
                        BotDB.get().query(stmt, "INSERT INTO alert(guild_id, bot_id, message, channel, enabled, type) VALUES('" + row.get("guild_id") + "','" + "938487470339801169" + "','" + row.get("message_text") + "','" + null + "', 1, '" + AlertType.REWARD.ordinal() + "');");
                        id = BotDB.get().lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
                        BotDB.get().query(stmt, "INSERT INTO alert_reward(alert_id, level, temporary) VALUES(" + id + "," + row.get("level") + "," + 0 + ");");
                        BotDB.get().query(stmt, "INSERT INTO alert_role(alert_id, role_id) VALUES(" + id + "," + row.get("role_id") + ");");
                        c.commit();
                    } catch (SQLException ex) {
                        try {
                            if(c != null) c.rollback();
                        } catch(SQLException ee) {}
                        System.out.println(ex.getMessage());
                    }
                }
                break;
            case "insertuser":
            query = "SELECT user_id, guild_id, exp, level, messages FROM experience";
            res = BotDB.get().query(query);;
            for(QueryRecord row : res){
                for (String bot : bots) {
                    query = "INSERT INTO user(user_id, guild_id, experience, level, messages, bot_id) VALUES (" + row.get("user_id") + ", " + row.get("guild_id") + ", " + row.get("exp") + ", " + row.get("level") + ", " + row.get("messages") + ", " + bot + ")";
                    BotDB.get().query(query);;
                }
            }
            e.reply("Done");
                break;
            case "trackscheduler":
                String status = PlayerManager.get().getGuildMusicManager(e.getGuild()).getTrackScheduler().toString();
                System.out.println(status);
                e.reply(status);
                break;

            case "getrawmessage":
                e.reply(e.getChannel().getIterableHistory().complete().get(1).getContentRaw());
                break;

            case "getrawembed":
                e.reply(e.getChannel().getIterableHistory().complete().get(1).getEmbeds().get(0).toData().toString());
                break;

            case "twitch":
                IEventSubConduit conduit = null;
                try {
                    conduit = TwitchConduitSocketPool.create(spec -> {
                        spec.clientId("***REMOVED***");
                        spec.clientSecret("***REMOVED***");
                        spec.poolShards(1);
                    });
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
                
                conduit.register(SubscriptionTypes.STREAM_ONLINE, b -> b.broadcasterUserId("126371014").build());

                conduit.getEventManager().onEvent(StreamOnlineEvent.class, System.out::println);
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
            case "cachesize":
                //Bot.getGuildSettings().getGuilds().setMaxSize(Integer.parseInt(args[1]));
                e.reply("New cache max size: " + args[1]);
                break;
            case "userdata":
                try {
                    e.reply(UserCache.getUser(args[1]).toString());
                } catch (Exception e1) {
                    e.reply(UserCache.getUser(e.getAuthor().getId()).toString());
                }
                break;
            case "usersdata":
                String users = "";
                for (UserData ud : UserCache.getInstance().values()) {
                    users += ud.getName() + "-";
                }
                e.reply(users);
                break;
            case "clearcache":
                //GuilddataCache.getGuilds().clear();
                //Bot.getUsers().clear();
                e.reply("Cache cleared");
                break;
            case "spotify":
                String csvFile = "rsc/testing/spotify.csv";
                CSVReader reader = null;
                try {
                    reader = new CSVReader(new FileReader(csvFile));
                    String[] line;
                    int cont = 0;
                    java.sql.Connection c = BotDB.get().getConnection();
                    while ((line = reader.readNext()) != null) {
                        String nome_song = line[2];
                        Timestamp time = getRandomTimestamp();
                        String user_id = new String[] {"440489230968553472", "383358222972616705"}[(int) (Math.random() * 1)];
                        try (PreparedStatement pstmt = c.prepareStatement("INSERT INTO sound(name, guild_id, user_id, extension, public, time) VALUES (?, ?, ?, ?, ?, ?)")) {
                            pstmt.setString(1, nome_song);
                            pstmt.setString(2, "474935164451946506");
                            pstmt.setString(3, user_id);
                            pstmt.setString(4, "mp3");
                            pstmt.setInt(5, 1);
                            pstmt.setTimestamp(6, time);
                            pstmt.executeUpdate();
                            c.commit();
                        } catch (SQLException ex) {
                            System.out.println("Error: " + nome_song + " " + time + " " + user_id);
                            try {
                                if(c != null) c.rollback();
                            } catch(SQLException eee) {
                                System.out.println(eee.getMessage());
                            }
                            System.out.println(ex.getMessage());
                        }
                        cont++;
                        System.out.println(cont);
                    }

                } catch (IOException | CsvValidationException ee) {
                    ee.printStackTrace();
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException ee) {
                            ee.printStackTrace();
                        }
                    }
                }
                break;
            case "dizionario":
                try (BufferedReader br = new BufferedReader(new FileReader("rsc/testing/dictionary_ita.txt"))) {
                    String word;
                    while ((word = br.readLine()) != null) {
                        String query1 = "INSERT INTO tag(name) VALUES ('" + word + "');";
                        BotDB.get().query(query1);
                    }
                } catch (IOException ee) {}
                
                
                break;
            case "tagsounds":
                int max_sound = 32767;
                int max_tag = 98111;
                for (int i = 1; i <= max_sound; i++) {
                    for (int j = 0; j < 5; j++) {
                        int tag_id = (int) (Math.random() * max_tag) + 1;
                        String query1 = "INSERT INTO tag_sounds(sound_id, tag_id) VALUES (" + i + ", " + tag_id + ");";
                        BotDB.get().query(query1);
                    }
                    System.out.println(i);
                }
            case "soundsgozzing":
                query = "SELECT id from sound";
                QueryResult res1 = BotDB.get().query(query);;
                System.out.println(res1.size());
                for (Guild g : e.getJDA().getGuilds()) {
                    System.out.println(g.getName());
                    int batchSize = 50000; // Batch size of 10k
                    for (int i = 0; i < res1.size(); i += batchSize) {
                        List<String> batchValues = new ArrayList<>();
                        // Calculate the end index for the current batch
                        int end = Math.min(i + batchSize, res1.size());
                        for (Member m : g.getMembers()) {
                            for (int j = i; j < end; j++) { // Iterate over each batch
                                QueryRecord row = res1.get(j);
                                batchValues.add("(" + m.getId() + ", " + row.get("id") + ", " + 1 + ")");
                            }
                        }
                        query = "INSERT INTO play(user_id, sound_id, times) VALUES " 
                                       + String.join(", ", batchValues) 
                                       + " ON DUPLICATE KEY UPDATE times = times + 1;";
                        // Execute the query for the current batch
                        System.out.println(i);
                        BotDB.get().query(query); // Uncomment this line to execute the query
                    }
                    break;
                    //BotDB.query(query1);
                }
                break;
            case "dbsgozz":
                for (Guild g : e.getJDA().getGuilds()) {
                    GuildData gd = GuildCache.getGuildOrPut(g);
                    for (Member m : g.getMembers()) {
                        gd.getMemberData(m.getId()).setUpdateTime(61);
                    }
                    for (TextChannel tc : g.getTextChannels()) {
                        gd.getChannelData(tc.getId()).enableExperience(true);
                    }
                }

                break;
            case "sql":                
                // HashMap<Long, List<String>> map = BotDB.getQueryAnalytics();      
                // HashMap<Long, Integer> queriesPerHour = new HashMap<>();
                
                // for (Map.Entry<Long, List<String>> entry : map.entrySet()) {
                //     long hours = TimeUnit.MILLISECONDS.toHours(entry.getKey());
                //     int queriesCount = entry.getValue().size();
                //     queriesPerHour.put(hours, queriesPerHour.getOrDefault(hours, 0) + queriesCount);
                // }
                // Map<Long, Integer> sortedQueriesPerHour = queriesPerHour.entrySet().stream()
                //     .sorted(Map.Entry.comparingByKey())
                //     .collect(Collectors.toMap(
                //         Map.Entry::getKey, 
                //         Map.Entry::getValue, 
                //         (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                // String[][] data = new String[sortedQueriesPerHour.size()][2];
                // int i = 0;
                // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-M-Y H:m:s");
                // for (Map.Entry<Long, Integer> entry : sortedQueriesPerHour.entrySet()) {
                //     System.out.println(TimeUnit.HOURS.toMillis(entry.getKey()));
                //     LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(TimeUnit.HOURS.toMillis(entry.getKey())), ZoneId.systemDefault());
                //     String formattedDate = dateTime.format(formatter);
                //     data[i][0] = formattedDate;
                //     data[i][1] = entry.getValue().toString();
                //     i++;
                // }    
                // String[] headers = new String[] {"Time", "Query"};
                // String table = TableHandler.constructTable(data, headers);
                
                // e.getChannel().sendFiles(FileUpload.fromData(
                //     table.getBytes(StandardCharsets.UTF_8),
                //     "table.txt"
                // )).queue();
            
                break;
            case "sqlday":                
                // HashMap<Long, List<String>> map2 = BotDB.getQueryAnalytics();
                // HashMap<Long, Integer> queriesPerDay = new HashMap<>();

                // for (Map.Entry<Long, List<String>> entry : map2.entrySet()) {
                //     // Convert milliseconds to days
                //     long days = TimeUnit.MILLISECONDS.toDays(entry.getKey());
                //     int queriesCount = entry.getValue().size();
                //     queriesPerDay.put(days, queriesPerDay.getOrDefault(days, 0) + queriesCount);
                // }

                // Map<Long, Integer> sortedQueriesPerDay = queriesPerDay.entrySet().stream()
                //     .sorted(Map.Entry.comparingByKey())
                //     .collect(Collectors.toMap(
                //         Map.Entry::getKey, 
                //         Map.Entry::getValue, 
                //         (oldValue, newValue) -> oldValue, LinkedHashMap::new));

                // String[][] data2 = new String[sortedQueriesPerDay.size()][2];
                // int j = 0;
                // DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("d-M-Y");

                // for (Map.Entry<Long, Integer> entry : sortedQueriesPerDay.entrySet()) {
                //     // Convert days back to milliseconds for the start of each day
                //     long millisForDay = TimeUnit.DAYS.toMillis(entry.getKey());
                //     LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millisForDay), ZoneId.systemDefault());
                //     String formattedDate = dateTime.format(formatter2);
                //     data2[j][0] = formattedDate;
                //     data2[j][1] = entry.getValue().toString();
                //     j++;
                // }

                // String[] headers2 = new String[] {"Time", "Query"};
                // String table2 = TableHandler.constructTable(data2, headers2);

                // e.getChannel().sendFiles(FileUpload.fromData(
                //     table2.getBytes(StandardCharsets.UTF_8),
                //     "table.txt"
                // )).queue();
            
                break;
            case "twitchuser":
                com.github.twitch4j.helix.domain.User streamer = TwitchClient.getStreamerByName(args[1]);
                if(streamer.getId() == null){
                    e.reply("Streamer not found");
                    return;
                }

                EmbedBuilder eb = new EmbedBuilder();
                eb.setColor(Bot.getColor());
                eb.setAuthor(streamer.getDisplayName(), TwitchClient.getStreamerUrl(streamer.getLogin()), streamer.getProfileImageUrl());
                eb.setThumbnail(streamer.getProfileImageUrl());
                eb.setFooter("twitch.tv/" + streamer.getLogin());
                eb.setDescription(streamer.getDescription());
                
                String buttonLabel = null;

                Stream stream = TwitchClient.getStream(streamer.getId());
                if(stream == null) {
                    eb.appendDescription("\n\n`⚫OFFLINE`\n");
                    if(streamer.getOfflineImageUrl() != null && !streamer.getOfflineImageUrl().isBlank()) {
                        eb.setImage(streamer.getOfflineImageUrl());
                    }

                    buttonLabel = "Visit profile";
                }
                else {
                    eb.appendDescription("\n\n`🔴LIVE`\n");
                    eb.appendDescription("\n" + stream.getTitle() + "\n");
                    eb.setImage(stream.getThumbnailUrl(400, 225));
                    eb.addField("Started", "<t:" + stream.getStartedAtInstant().getEpochSecond() + ":R>", true);
                    eb.addField("Viewer count", stream.getViewerCount().toString(), true);
                    buttonLabel = "Watch stream";
                }

                eb.addField("Channel created", "<t:" + streamer.getCreatedAt().getEpochSecond() + ":R>", false);

                Button streamerButtonLink = Button.link(TwitchClient.getStreamerUrl(streamer.getLogin()), buttonLabel);
                
                e.getChannel().sendMessageEmbeds(eb.build()).setComponents(ActionRow.of(streamerButtonLink)).queue();
                break;
            case "fixlol":
                ChronoTask fixlol = () -> {
                    String q = "SELECT id, game_id, region from `match` where queue = 'CHERRY' order by id desc";
                    QueryResult r = LeagueDB.get().query(q);
                    System.out.println("total match: " + r.size());
                    int aaa = 0;
                    HashMap<String, List<QueryRecord>> sharded = new HashMap<>();
                    for (QueryRecord row : r){
                        String region = row.getAsLeagueShard("region").name();
                        if (!sharded.containsKey(region)) {
                            sharded.put(region, new ArrayList<>());
                        }
                        sharded.get(region).add(row);
                    }

                    for(String region : sharded.keySet()) {
                        ChronoTask fixlolRegion = () -> {
                            int bbb = 0;
                            for(QueryRecord row : sharded.get(region)){
                                String game_id = region + "_" + row.get("game_id");
                                try {
                                    boolean exists = LeagueHandler.isMatchLocallyCached(game_id, row.getAsLeagueShard("region"));
            
                                    LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(row.getAsLeagueShard("region").toRegionShard(), game_id);
                                    LOLTimeline timeline = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getTimeline(row.getAsLeagueShard("region").toRegionShard(), game_id);
                                    if (timeline == null) {
                                        System.out.println("Timeline not found");
                                        continue;
                                    }
                                    HashMap<String, HashMap<String, String>> matchData = Tracker.analyzeMatchBuild(match, match.getParticipants());
        
                                    for (MatchParticipant participant : match.getParticipants()) {    
                                        int sumId = LeagueService.getSummonerIdByPuuid(participant.getPuuid(), match.getPlatform());
                                        String build = Tracker.createJSONBuild(matchData.get(participant.getPuuid()));
                                        String q1 = "UPDATE participant SET build = '" + build  + "' WHERE match_id = " + row.get("id") + " AND summoner_id = " + sumId + ";";
                                        LeagueDB.get().query(q1);
                                    }
                                    bbb++; 
                                    System.out.println("total match: " + bbb + " / " + sharded.get(region).size() + " (" + row.get("id") + " - " + game_id + ")");
                                    if (!exists) {
                                        Thread.sleep(400);
                                        LeagueHandler.clearMatchCache(game_id, row.getAsLeagueShard("region"));
                                    }
                                } catch (Exception eeeee) {
                                    eeeee.printStackTrace();
                                    BotLogger.error("Match not found: " + game_id + " " + row.getAsLeagueShard("region").toRegionShard());
                                }
            
                            }
                        };
                        fixlolRegion.queue();
                    }

                };
                fixlol.queue();
            break;
            case "fixlolna":
                query = "SELECT game_id, account_id from summoner_tracking where league_shard = 8";
                res = BotDB.get().query(query);;
                for(QueryRecord row : res){
                    String game_id = "NA1_"+row.get("game_id");
                    String account_id = row.get("account_id");

                    LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(RegionShard.AMERICAS, game_id);
                    try {
                        Thread.sleep(200);
                    } catch (Exception eee) { eee.printStackTrace(); }
                    if (match == null) {
                        System.out.println("Match not found");
                        continue;
                    }

                    long time_start = match.getGameStartTimestamp();
                    long time_end = match.getGameEndTimestamp();

                    query = "UPDATE summoner_tracking SET league_shard = " + match.getPlatform().ordinal() + ",time_start = '" + new Timestamp(time_start) + "', time_end = '" + new Timestamp(time_end) + "' WHERE game_id = '" + row.get("game_id") + "' AND account_id = '" + account_id + "';";
                    System.out.println(query);
                    BotDB.get().query(query);
                }
            break;
            case "summoners":
                query = "SELECT account_id, league_shard from summoner";
                res = BotDB.get().query(query);;
                String ssss = "";
                for(QueryRecord row : res){
                    String account_id = row.get("account_id");
                    int league_shard = row.getAsInt("league_shard");
                    Summoner summoner = LeagueService.getSummonerByPuuid(account_id, LeagueShard.values()[league_shard]);
                    if (summoner == null) {
                        System.out.println("Summoner not found");
                        continue;
                    }
                    RiotAccount account = LeagueService.getRiotAccountFromSummoner(summoner);
                    if (account == null) {
                        System.out.println("Account not found");
                        continue;
                    }
                    ssss += account.getName() + "(" + summoner.getAccountId() + ")\n";

                }
                e.reply(ssss);
            break;
            case "match":
                String match_id = "5079311964" ;
                String shard = "8";
                LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[Integer.valueOf(shard)].toRegionShard(), match_id);
                if (match == null) {
                    e.reply("Match not found");
                    return;
                }
                String matchdata = "";
                for (MatchParticipant p : match.getParticipants()) {
                    matchdata += CustomEmojiHandler.getFormattedEmoji(p.getChampionId()) + " " + p.getKills() + "/" + p.getDeaths() + "/" + p.getAssists() + "\n";
                }
                e.reply(matchdata);
            break;
            case "fixlolsum":
                query = "SELECT game_id, account_id from summoner_tracking where league_shard = 3 AND account_id = '" + args[1] + "'";
                res = BotDB.get().query(query);;
                System.out.println(res.size());
                for(QueryRecord row : res){
                    String game_id = "EUW1_"+row.get("game_id");
                    String account_id = row.get("account_id");

                    LOLMatch match1 = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(RegionShard.EUROPE, game_id);
                    if (match1 == null) {
                        System.out.println("Match not found");
                        continue;
                    }

                    try {
                        Thread.sleep(200);
                    } catch (Exception eee) { eee.printStackTrace(); }

                    long time_start = match1.getGameStartTimestamp();
                    long time_end = match1.getGameEndTimestamp();
                    
                    query = "UPDATE summoner_tracking SET league_shard = " + match1.getPlatform().ordinal() + ", time_start = '" + new Timestamp(time_start) + "', time_end = '" + new Timestamp(time_end) + "' WHERE game_id = '" + row.get("game_id") + "' AND account_id = '" + account_id + "';";
                    System.out.println(query);
                    BotDB.get().query(query);
                }
            break;
            case "playplaylist":
                int playlistId = Integer.valueOf(args[1]);
                QueryResult tracks = BotDB.getPlaylistTracks(playlistId, null, null);

                List<String> URIs = new ArrayList<String>();
                for(QueryRecord track : tracks) {
                    URIs.add(track.get("uri"));
                }
                PlayerManager.get().loadPlaylist(e.getGuild(), URIs, new ResultHandler(e, false, PlayTiming.LAST));

            break;
            case "encodetrack":
                PlayerManager.get().encodeTrack(PlayerManager.get().createTrack(e.getGuild(), args[1]));
            break;
            case "addtrackplaylist":
                AudioTrack track = PlayerManager.get().createTrack(e.getGuild(), args[1]);
                PlayerManager.get().encodeTrack(track);
                BotDB.addTrackToPlaylist(2, track.getInfo().uri, PlayerManager.get().encodeTrack(track), null);
            break;
            case "loadtracksfromdb":
                List<AudioTrack> tracksFinal = new ArrayList<>();
                QueryResult tracksToLoad = BotDB.getPlaylistTracks(Integer.parseInt(args[1]), null, null);
                for(QueryRecord trackToLoad : tracksToLoad) {
                    tracksFinal.add(PlayerManager.get().decodeTrack(trackToLoad.get("encoded_track")));
                }
                SafjAudioPlaylist playlist = new SafjAudioPlaylist("Custom Playlist", tracksFinal, null);
                (new ResultHandler(e, false, PlayTiming.LAST)).playlistLoaded(playlist);
            break;
            case "loadqueuedb":
                BotDB.addTrackToPlaylist(Integer.valueOf(args[1]), (List<AudioTrack>) PlayerManager.get().getGuildMusicManager(e.getGuild()).getTrackScheduler().getQueue(), null);
            break;
            case "fixloldb":
                ChronoTask fixlolDB = () -> {
                    String q = "SELECT id, puuid, region from summoner order by id desc";
                    QueryResult r = LeagueDB.get().query(q);;
                    int bbb = 0;
                    for (QueryRecord acc : r) {
                        String puuid = acc.get("puuid");
                        LeagueShard region = acc.getAsLeagueShard("region");
                        Summoner summoner = LeagueService.getSummonerByPuuid(puuid, region);
                        if (summoner == null) {
                            System.out.println("Summoner not found");
                            continue;
                        }
                        LeagueDB.updateSummonerEntries(acc.getAsInt("id"), summoner.getLeagueEntry(), region);
                        String query1 = "UPDATE summoner SET level = '" + summoner.getSummonerLevel() + "', icon = '" + summoner.getProfileIconId() + "' WHERE id = " + acc.get("id") + ";";
                        System.out.println("total summoner: " + bbb + " ( " + acc.get("id")  + ") / " + r.size());
                        bbb++;
                    }
                };
                fixlolDB.queue();
                e.reply("Done");
            break;
            case "getprivatehistory":
                User user = PermissionHandler.getMentionedUser(e, args[1]);
                PrivateChannel dm = user.openPrivateChannel().complete();
                List<Message> messages = dm.getHistory().retrievePast(10).complete();
                String messagesString = null;
                for(Message msg : messages) {
                    messagesString = msg.getContentDisplay() + "\n";
                }
                e.reply(messagesString);
            break;
            case "splitsoundplays":
                query = "select * from sound_interactions";
                res = BotDB.get().query(query);;
                query = "";
                for (QueryRecord row : res) {
                    int times = row.getAsInt("times");
                    System.out.println(row.get("sound_id") + " " + row.get("user_id") + " " + times );
                    for (int i = 0; i < times; i++) {
                        query += "(" + row.get("user_id") + ", " + row.get("sound_id") + "),";
                    }
                    //remove ,
                    if (query.isBlank()) continue;
                    
                }
                query = query.substring(0, query.length() - 1);
                query = "INSERT INTO sound_interactions2(user_id, sound_id) VALUES " + query;
                BotDB.get().query(query);
            break;
            case "splitlike":
                query = "select * from sound_interactions";
                res = BotDB.get().query(query);;
                query = "";
                for (QueryRecord row : res) {
                    int likevalue = row.getAsInt("like") == 1 ? 1 : 0;
                    likevalue = row.getAsInt("dislike") == 1 ? -1 : likevalue;
                    query += "(" + row.get("user_id") + ", " + row.get("sound_id") + ", " + likevalue + "),";
                    
                }
                query = query.substring(0, query.length() - 1);
                query = "INSERT INTO sound_interactions3(user_id, sound_id, value) VALUES " + query;
                BotDB.get().query(query);
            break;
            case "testblob":
                query = "SELECT * FROM soundboard WHERE id = 23";
                QueryRecord row1 = BotDB.get().lineQuery(query);
                try {
                    java.sql.Blob blob = row1.getAsBlob("thumbnail");
                    if (blob != null) {
                        byte[] bytes = blob.getBytes(1, (int) blob.length());
                        File file = new File("thumbnail.png");
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(bytes);
                        }
                        System.out.println("File written successfully: " + file.getAbsolutePath());
                    } else {
                        System.out.println("No BLOB data found for the specified column.");
                    }
                } catch (Exception ee) {
                    ee.printStackTrace();
                }
                break;
            case "converttwitchalert":
                query = "select * from twitch_subscription";
                res = BotDB.get().query(query);;

                for (QueryRecord r : res) {
                    int id = 0;
                    Connection c = BotDB.get().getConnection();
                    query = "INSERT INTO alert(guild_id, message, channel, enabled, type, send_type) VALUES(?, ?, ?, 1, ?, ?);";
                    try (PreparedStatement pstmt = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                        pstmt.setString(1, r.get("guild_id"));
                        pstmt.setString(2, r.get("message"));
                        pstmt.setString(3, r.get("channel_id"));
                        pstmt.setInt(4, AlertType.TWITCH.ordinal());
                        pstmt.setInt(5, AlertSendType.CHANNEL.ordinal());
        
                        pstmt.executeUpdate();
        
                        // Retrieve the generated alert ID
                        try (ResultSet rs = pstmt.getGeneratedKeys()) {
                            if (rs.next()) {
                                id = rs.getInt(1);
                            }
                        }
        
                        // Insert into alert_twitch table
                        String alertTwitchQuery = "INSERT INTO alert_twitch(alert_id, streamer_id) VALUES(?, ?);";
                        try (PreparedStatement alertTwitchStmt = c.prepareStatement(alertTwitchQuery)) {
                            alertTwitchStmt.setInt(1, id);
                            alertTwitchStmt.setString(2, r.get("streamer_id"));
                            alertTwitchStmt.executeUpdate();
                        }
        
                        c.commit();
                    } catch (SQLException ex) {
                        try {
                            if (c != null) c.rollback();
                        } catch (SQLException ee) {
                            ee.printStackTrace();
                        }
                        System.out.println(ex.getMessage());
                    } finally {
                        try {
                            if (c != null) c.close();
                        } catch (SQLException eee) {
                            eee.printStackTrace();
                        }
                    }
                }

                

            break;
            case "updateconduit":
            query = "SELECT streamer_id from twitch_subscription";
            res = BotDB.get().query(query);;
            for (QueryRecord r : res) {
                TwitchClient.registerSubEvent(r.get("streamer_id"));
            }
            System.out.println("Done");   
            break;
            default:
                e.reply("Command does not exist (use list to list the commands).");
            break;
            case "createmuterole":
                Role role = e.getGuild().createRole().setName("Berbit-Muted").complete();
                for (TextChannel tc : e.getGuild().getTextChannels()) {
                    tc.getManager().putRolePermissionOverride(role.getIdLong(), null, Collections.singleton(Permission.MESSAGE_SEND)).queue();
                
                }
                for (VoiceChannel vc : e.getGuild().getVoiceChannels()) {
                    vc.getManager().putPermissionOverride(role, null, Collections.singleton(Permission.VOICE_SPEAK)).queue();
                }
                e.reply("Role created");
            break;
            case "movematch":
                query = "SELECT game_id, league_shard, id from summoner_tracking where game_id in(select game_id from summoner_tracking where game_id not in (select game_id from summoner_match))";
                res = BotDB.get().query(query);;
                System.out.println(res.size());
                for (QueryRecord r : res) {
                    System.out.println(r.get("id") + " - " + r.get("game_id"));
                    String game_id = r.get("game_id");
                    int league_shard = r.getAsInt("league_shard");
                    String region = LeagueShard.values()[league_shard].name();
                    LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[league_shard].toRegionShard(), region + "_"+game_id);
                    if (m == null) {
                        System.out.println("Match not found");
                        continue;
                    }
                    System.out.println(LeagueDB.saveMatch(m));
                }
                break;
            case "pushbuild":
                query = "SELECT st.id, sm.game_id, sm.league_shard, st.account_id, s.summoner_id FROM summoner_tracking st JOIN summoner_match sm ON st.summoner_match_id = sm.id JOIN summoner s ON st.account_id = s.account_id AND st.league_shard = s.league_shard ORDER BY st.id;";
                res = BotDB.get().query(query);;
                System.out.println(res.size());
                for (QueryRecord r : res) {
                    System.out.println(r.get("id") + " - " + r.get("game_id"));
                    String game_id = r.get("game_id");
                    int league_shard = r.getAsInt("league_shard");
                    String region = LeagueShard.values()[league_shard].name();
                    LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[league_shard].toRegionShard(), region + "_"+game_id);
                    if (m == null) {
                        System.out.println("Match not found");
                        continue;
                    }
                    System.out.println(LeagueDB.saveMatch(m));
                }
                break;
            case "trackoldgames":
                if (true) {
                    Summoner sum = LeagueService.getSummonerByPuuid(args[1], LeagueShard.EUW1);
                    //MatchTracker.retriveOldGames(sum).queue();
                }
            break;
            case "mergelol":
            query = "SELECT st.id, sm.game_id, sm.league_shard, st.account_id, s.summoner_id FROM summoner_tracking st JOIN summoner_match sm ON st.summoner_match_id = sm.id JOIN summoner s ON st.account_id = s.account_id WHERE st.id > 294 ORDER BY st.id;";
            
                res = BotDB.get().query(query);;
                System.out.println(res.size());
                for(QueryRecord row : res){
                    String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
                    String game_id = region + "_"+row.get("game_id");
                    //String account_id = row.get("account_id");
                    String summoner_id = row.get("summoner_id");
                    LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
                    String puuid = "";
                    int summoner_match_id = LeagueDB.saveMatch(m);

                    HashMap<String, HashMap<String, String>> matchData = Tracker.analyzeMatchBuild(m, m.getParticipants());

                    System.out.println(row.get("id"));
                    for (MatchParticipant partecipant : m.getParticipants()) {
                        Summoner toPush = LeagueService.getSummonerByPuuid(partecipant.getPuuid(), LeagueShard.values()[row.getAsInt("league_shard")]);
                        Tracker.pushSummoner(m, summoner_match_id, toPush, partecipant, matchData.get(partecipant.getPuuid()));
                        try {
                            Thread.sleep(1000);
                        } catch (Exception eee) { eee.printStackTrace(); }
                    }

                }
                break;

            case "fixmatch":
                query = "select id, game_id, league_shard from summoner_match where bans = '{}' order by id";
                res = BotDB.get().query(query);;
                for (QueryRecord row : res) {
                    String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
                    String game_id = region + "_"+row.get("game_id");
                    LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
                    
                    JSONObject bans = new JSONObject();
                    for (MatchTeam team : m.getTeams()) {
                        String teamID = team.getTeamId().ordinal() + "";
                        List<Integer> list = new ArrayList<>();
                        for (ChampionBan champion : team.getBans()) {
                            if (champion.getChampionId() != -1) list.add(champion.getChampionId());
                        }
                        bans.put(teamID, list);
                    }
                    query = "UPDATE summoner_match SET bans = '" + bans.toString() + "' WHERE id = " + row.get("id");
                    BotDB.get().query(query);
                    System.out.println(row.get("id"));
                    try {
                        Thread.sleep(1500);
                    } catch (Exception e1) {
                        
                    }
                }
                break;
                case "lolqueue":
                    System.out.println(Tracker.copyQueue().size());
                break;
                case "pushlolqueue":
                    ChronoTask task =  () -> TrackerScheduler.popSet();
                    task.queue();
                break;
                case "error":
                    List<String> a = new ArrayList<String>();
                    a.get(0);
                break;
                case "pushsamplegame":
                    ChronoTask sampleTask =  () -> TrackerScheduler.retriveSampleGames(GameQueueType.TEAM_BUILDER_RANKED_SOLO);
                    sampleTask.queue();
                break;
                case "pushsamplegamecherry":
                    ChronoTask sampleTaskCherry =  () -> TrackerScheduler.retriveSampleGames(GameQueueType.CHERRY);
                    sampleTaskCherry.queue();
                break;
                case "pushsamplegamearam":
                    ChronoTask sampleTaskAram =  () -> TrackerScheduler.retriveSampleGames(GameQueueType.ARAM);
                    sampleTaskAram.queue();
                break;
                case "pushhighelo":
                    ChronoTask master =  () -> TrackerScheduler.retriveHighEloEntries();
                    master.queue();
                break;
                case "fixaccountid":
                    query = "SELECT id, puuid, league_shard FROM summoner WHERE account_id IS NULL ORDER BY id DESC";
                    res = LeagueDB.get().query(query);
                    ChronoTask fixaccountTask = () -> {
                        int n = 0;
                        for (QueryRecord sum : res) {
                            try {
                                Summoner sssss = LeagueService.getSummonerByPuuid(sum.get("puuid"), LeagueShard.values()[Integer.valueOf(sum.get("league_shard"))]);
                                String fixQuery = "UPDATE summoner SET account_id = '" + sssss.getAccountId() + "' WHERE id=" + sum.get("id");
                                LeagueDB.get().query(fixQuery);
                                try {
                                    Thread.sleep(500);
                                } catch (Exception ee) {
                                ee.printStackTrace();
                                }
                            } catch (Exception eeee) {
                               eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + res.size());
                        }
                    };
                    fixaccountTask.queue();
                break;
                case "insertbullshit":
                    query = "SELECT s.id, s.puuid, s.region FROM summoner s LEFT JOIN masteries m ON s.id = m.summoner_id WHERE m.summoner_id IS NULL ORDER BY s.id DESC;";
                    res = LeagueDB.get().query(query);
                    ChronoTask bullshit = () -> {
                        int n = 0;
                        for (QueryRecord sum : res) {
                            try {
                                Summoner sssss = LeagueService.getSummonerByPuuid(sum.get("puuid"), sum.getAsLeagueShard("region"));
                                int summonerId = LeagueHandler.updateSummonerDB(sssss);
                                try {
                                    Thread.sleep(400);
                                } catch (Exception ee) {
                                ee.printStackTrace();
                                }
                                LeagueDB.updateSummonerMasteries(summonerId, sssss.getChampionMasteries());
                            } catch (Exception eeee) {
                                eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + res.size());
                        }
                    };
                    bullshit.queue();
                break;
                case "retriveallgames":
                    ChronoTask retriveAllGames = () -> {
                        System.out.println(args[1]);
                        try {
                            Tracker.retriveMatchHistory(LeagueService.getSummonerByPuuid(args[1], GuildCache.getGuild(e.getGuild()).getLeagueShard(e.getChannel().getId())));
                        } catch (Exception eee) { eee.printStackTrace(); }
                    };
                    retriveAllGames.queue();
                break;
                case "retriveallgamesfast":
                    ChronoTask retriveAllGamesFast = () -> {
                        System.out.println(args[1]);
                        for (GameQueueType queueType : GameQueueType.values()) {
                            Tracker.retriveMatchHistory(LeagueService.getSummonerByPuuid(args[1], GuildCache.getGuild(e.getGuild()).getLeagueShard(e.getChannel().getId())), queueType);
                        }
                    };
                    retriveAllGamesFast.queue();
                break;
                case "setmatchevent":
                    query = "SELECT id, game_id, league_shard FROM `match` WHERE events = '{}' ORDER BY id DESC";
                    res = LeagueDB.get().query(query);
                    ChronoTask setMatchEvent = () -> {
                        int n = 0;
                        for (QueryRecord row : res) {
                            try {
                                String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
                                String game_id = region + "_"+row.get("game_id");
                                LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
                                if (m == null) continue;
                                LeagueDB.setMatchEvent(row.getAsInt("id"), Tracker.createJSONEvents(Tracker.analyzeMatchBuild(m, m.getParticipants()).get("match")));
                                try {
                                    Thread.sleep(400);
                                } catch (Exception ee) {
                                ee.printStackTrace();
                                }
                            } catch (Exception eeee) {
                                eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + res.size());
                        }
                    };
                    setMatchEvent.queue();
                    break;
                case "fixordinal":
                    String queryMatch = "SELECT id, league_shard, game_type FROM `match` ORDER BY id DESC";
                    String querySum = "select id, league_shard from summoner ORDER BY id DESC";
                    String queryPart = "select id, lane_o, team_o, rank_o from participant order by id desc";
                    String queryRank = "select id, rank_o, game_type, lp from `rank` order by id desc";
                    QueryResult matches = LeagueDB.get().query(queryMatch);
                    QueryResult summoners = LeagueDB.get().query(querySum);
                    QueryResult participants = LeagueDB.get().query(queryPart);
                    QueryResult ranks = LeagueDB.get().query(queryRank);
                    ChronoTask fixOrdinalMatch = () -> {
                        int n = 0;
                        for (QueryRecord row : matches) {
                            try {
                                String q = "UPDATE `match` SET queue = '" + GameQueueType.values()[row.getAsInt("game_type")] + "', region = '" + LeagueShard.values()[row.getAsInt("league_shard")] + "' WHERE id = " + row.get("id");
                                LeagueDB.get().query(q);
                            } catch (Exception eeee) {
                                eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + matches.size());
                        }
                    };
                    ChronoTask fixOrdinalSummoner = () -> {
                        int n = 0;
                        for (QueryRecord row : summoners) {
                            try {
                                String q = "UPDATE summoner SET region = '" + LeagueShard.values()[row.getAsInt("league_shard")] + "' WHERE id = " + row.get("id");
                                LeagueDB.get().query(q);
                            } catch (Exception eeee) {
                                eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + summoners.size());
                        }
                    };
                    ChronoTask fixOrdinalParticipant = () -> {
                        int n = 0;
                        for (QueryRecord row : participants) {
                            try {
                                LaneType lane = LaneType.values()[row.getAsInt("lane_o")];
                                TeamType team = TeamType.values()[row.getAsInt("team_o")];
                                TierDivisionType rank = TierDivisionType.values()[row.getAsInt("rank_o")];
                                String q = "UPDATE participant SET team = '" + team + "', lane = '" + lane + "', rank = '" + rank + "' WHERE id = " + row.get("id");
                                LeagueDB.get().query(q);
                            } catch (Exception eeee) {
                                eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + participants.size());
                        }
                    };
                    ChronoTask fixOrdinalRank = () -> {
                        int n = 0;
                        for (QueryRecord row : ranks) {
                            try {
                                TierDivisionType rank = TierDivisionType.values()[row.getAsInt("rank_o")];
                                GameQueueType gameType = GameQueueType.values()[row.getAsInt("game_type")];
                                int mmr = TierDivisionUtils.getMmr(rank, row.getAsInt("lp"));
                                String q = "UPDATE `rank` SET rank = '" + rank + "', queue = '" + gameType + "', mmr = " + mmr + " WHERE id = " + row.get("id");
                                LeagueDB.get().query(q);
                            } catch (Exception eeee) {
                                eeee.printStackTrace();
                            }
                            n++;
                            System.out.println(n + "/" + ranks.size());
                        }
                    };
                    fixOrdinalRank.queue();
                    fixOrdinalParticipant.queue();
                    fixOrdinalSummoner.queue();
                    fixOrdinalMatch.queue();
                break;
            case "fixmmr":
                ChronoTask fixMmr = () -> {
                    QueryResult ranks2 = LeagueDB.get().query("SELECT id, `rank`, lp FROM `rank` ORDER BY id");
                    int total = ranks2.size();
                    int processed = 0;
                    int failed = 0;
                    System.out.println("fixmmr total: " + total);
                    for (QueryRecord row : ranks2) {
                        try {
                            TierDivisionType division = TierDivisionType.UNRANKED;
                            String rankValue = row.get("rank");
                            if (rankValue != null && !rankValue.isBlank()) {
                                try {
                                    division = TierDivisionType.valueOf(rankValue);
                                } catch (IllegalArgumentException ignored) { }
                            }

                            int mmr = TierDivisionUtils.getMmr(division, row.getAsInt("lp"));
                            String update = "UPDATE `rank` SET mmr = " + mmr + " WHERE id = " + row.getAsInt("id");
                            LeagueDB.get().query(update);
                            processed++;
                        } catch (Exception exception) {
                            failed++;
                            exception.printStackTrace();
                        }

                        int current = processed + failed;
                        System.out.println("fixmmr row: " + current + "/" + total
                            + " | remaining: " + (total - current)
                            + " | id: " + row.get("id"));
                    }
                    System.out.println("fixmmr completed: total=" + total
                        + " | updated=" + processed + " | failed=" + failed);
                };
                fixMmr.queue();
                e.reply("fixmmr queued");
                break;
            case "getrank":
                ChronoTask getRank = () -> {
                    TrackerScheduler.retriveHighEloEntries();
                };
                getRank.queue();
                break;
            case "fixrank":
                ChronoTask fixRank = () -> {
                    String q = "SELECT m.id, GROUP_CONCAT(p.rank) AS ranks FROM `match` m JOIN participant p ON m.id = p.match_id GROUP BY m.id ORDER BY m.id DESC";
                    QueryResult resRanks = LeagueDB.get().query(q);
                    for (QueryRecord row : resRanks) {
                        try {
                            String[] ranksString = row.get("ranks").split(",");
                            List<TierDivisionType> ranksT = new ArrayList<TierDivisionType>();
                            for (String rank : ranksString) {
                                ranksT.add(TierDivisionType.valueOf(rank));
                            }
                            TierType newRank = TierDivisionUtils.getAvarageRank(ranksT);
                            String updateQuery = "UPDATE `match` SET rank = '" + newRank + "' WHERE id = " + row.get("id");
                            LeagueDB.get().query(updateQuery);
                        } catch (Exception eeee) {
                            eeee.printStackTrace();
                        }
                    }
                };
                fixRank.queue();
                break;
            case "getallrank":
                ChronoTask retriveAllEntries = () -> TrackerScheduler.retriveAllEntries();
                retriveAllEntries.queue();
            break;
            case "finalstats":
                query = "SELECT id from summoner ORDER BY id ASC";
                res = LeagueDB.get().query(query);
                ChronoTask finalStats = () -> {
                    int n = 0;
                    LeagueMessageParameter param = new LeagueMessageParameter(LeagueMessageType.LIVEGAME);
                    param.setQueueType(GameQueueType.TEAM_BUILDER_RANKED_SOLO);
                    for (QueryRecord row : res) {
                        try {
                            List<Match> ms = LeagueDB.getMatchHistory(row.getAsInt("id"), param);
                            HashMap<Integer, PlayerChampionStats> championStats = new HashMap<>();
    
                            HashMap<String, Set<Integer>> unique = new HashMap<>();
    
                            unique.put("champion", new HashSet<>());
    
    
                            for (Match m : ms) {
                                for (Participant participant : m.participants) {
                                    if (participant.summonerId != row.getAsInt("id")) continue;
    
                                        unique.getOrDefault("champion", new HashSet<>()).add(participant.champion);
                                        String kda = participant.kda;
                                        int kills = Integer.parseInt(kda.split("/")[0]);
                                        int deaths = Integer.parseInt(kda.split("/")[1]);
                                        int assists = Integer.parseInt(kda.split("/")[2]);
                                        championStats.computeIfAbsent(participant.champion, p -> new PlayerChampionStats(participant.champion)).add(kills, deaths, assists, participant.gain, participant.win);
                                }
                            }
                            if (!championStats.isEmpty()) {
                                String q = "INSERT INTO summoner_metric(summoner_id, champion, games, wins, losses, kills, deaths, assists, lp, score) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE games = games + VALUES(games), wins = wins + VALUES(wins), losses = losses + VALUES(losses), kills = kills + VALUES(kills), deaths = deaths + VALUES(deaths), assists = assists + VALUES(assists), lp = lp + VALUES(lp), score = VALUES(score);";
                                try (Connection c = LeagueDB.get().getConnection();
                                     PreparedStatement pstmt = c.prepareStatement(q)) {

                                    for (PlayerChampionStats stat : championStats.values()) {
                                        pstmt.setInt(1, row.getAsInt("id"));
                                        pstmt.setInt(2, stat.getChampion());
                                        pstmt.setInt(3, stat.getGames());
                                        pstmt.setInt(4, stat.getWins());
                                        pstmt.setInt(5, stat.getLosses());
                                        pstmt.setInt(6, stat.getKills());
                                        pstmt.setInt(7, stat.getDeaths());
                                        pstmt.setInt(8, stat.getAssists());
                                        pstmt.setInt(9, stat.getLp());
                                        pstmt.setInt(10, stat.getScore());
                                        pstmt.addBatch();
                                    }

                                    pstmt.executeBatch();
                                    c.commit();

                                } catch (SQLException eeeee) {
                                    eeeee.printStackTrace();
                                }
                            }                        } catch (Exception eeee) {
                            // TODO: handle exception
                        }
                        n++;
                        System.out.println(n + "/" + res.size());
                    }
                };                
                finalStats.queue();
                break;
            case "clearmatch":
                query = "select region, game_id from `match` WHERE time_start >= UNIX_TIMESTAMP(NOW() - INTERVAL 2 MONTH);";
                res = LeagueDB.get().query(query);
                for (QueryRecord r : res) {
                    try {
                        String gameId = r.get("region") + "_" + r.get("game_id");
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("platform", r.getAsLeagueShard("region").toRegionShard());
                        data.put("gameid", gameId);   
                        DataCall.getCacheProvider().clear(URLEndpoint.V5_MATCH, data);
                    } catch (Exception eeeee) {
                        eeeee.printStackTrace();
                    }
                }
                break;
            case "sleep":
                try {
                    Thread.sleep(Long.parseLong(args[1]));
                } catch (InterruptedException eeeee) {
                    eeeee.printStackTrace();
                }
                e.reply("Done");
                break;
            case "pausetracker":
                TrackerState.acquire(Priority.HIGH);
                break;
            case "resumetracker":
                TrackerState.release(Priority.HIGH);
                break;
            case "champ":
                ChronoTask champ = () -> {
                    for (String patch : Arrays.asList("16.7", "16.8", "16.9")) {
                        for (LeagueShard region : LeagueShardUtils.getActives()) {  
                            for (TierType rank : Arrays.asList(TierType.IRON, TierType.BRONZE, TierType.SILVER, TierType.GOLD, TierType.PLATINUM, TierType.DIAMOND, TierType.MASTER, TierType.GRANDMASTER, TierType.CHALLENGER)) {
                            Filter filter = new Filter()
                            .setChampion(27)
                            .setLane(LaneType.TOP)
                            .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
                            .setRegion(region)
                            .setRank(rank)
                            .setPatch(patch);

                            for (StaticChampion champion : LeagueHandler.getRiotApi().getDDragonAPI().getChampions().values()) {
                                    Filter championFilter = new Filter()
                                    .setChampion(champion.getId())
                                    .setLane(LaneType.TOP)
                                    .setQueue(GameQueueType.TEAM_BUILDER_RANKED_SOLO)
                                    .setRegion(region)
                                    .setRank(rank)
                                    .setPatch(patch);
                                    Build build = new BuildService().getMostUsed(championFilter);
                                    Build buildHighWinrate = new BuildService().getHighWinrate(championFilter);
                                    System.out.println("Region: " + region + " Rank: " + rank + " Patch: " + patch + " Champion: " + champion.getId());
                            }


                            
                            ChampionStatistics stats = new ChampionStatsService().get(filter);
                    
                            //System.out.println(filter.toKey());
                    

                            }
                            //stats.print();
                        }
                    }
                    System.out.println("Done");
                };
                champ.queue();
                break;
        }
    }  

    static class MonthlyTask extends TimerTask {
        @Override
        public void run() {
            // Inserisci qui il codice da eseguire ogni primo del mese a mezzanotte
            System.out.println("Evento mensile eseguito!");
        }
    }


     private static void createAndSaveChartAsPNG() {
        JFreeChart chart = createChart(createDataset());
        BufferedImage chartImage = chart.createBufferedImage(800, 600);

        try {
            File outputFile = new File("chart.png");
            ImageIO.write(chartImage, "png", outputFile);
            System.out.println("Grafico salvato come " + outputFile.getName());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JFreeChart createChart(DefaultCategoryDataset dataset) {
        return ChartFactory.createLineChart(
                "Esempio di Grafico a Barre",
                "Categorie",
                "Valori",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
    }

    private static DefaultCategoryDataset createDataset() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        String query = "select time, count(name) as count from command_analytic where MONTH(time) = 8 group by DAY(time);";
        QueryResult res = BotDB.get().query(query);;
        
        for(QueryRecord row : res){
            System.out.println(row.get("time") + " " + row.get("count"));
            dataset.addValue(Integer.parseInt(row.get("count")), "Comandi", row.get("time"));
        }

        return dataset;
    }

    private static Timestamp getRandomTimestamp() {
        long startOf2022 = LocalDateTime.of(2022, 1, 1, 0, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        long now = Instant.now().toEpochMilli();
    
        long randomTime = ThreadLocalRandom.current().nextLong(startOf2022, now);
        return new Timestamp(randomTime);
    }

}
