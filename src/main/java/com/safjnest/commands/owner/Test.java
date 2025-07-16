package com.safjnest.commands.owner;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.BlacklistData;
import com.safjnest.model.guild.ChannelData;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.LeagueDBHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TableHandler;
import com.safjnest.util.lol.MatchTracker;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.twitch.TwitchClient;
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
import net.dv8tion.jda.api.utils.FileUpload;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.simple.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

    @SuppressWarnings({ "unchecked", "unused" })
    @Override
    protected void execute(CommandEvent e) {
        String[] bots = {"938487470339801169", "983315338886279229", "939876818465488926", "1098906798016184422", "1074276395640954942"};
        QueryCollection res;
        String query = "";

        String args[] = e.getArgs().split(" ", 2);

        //File soundBoard = new File("rsc" + File.separator + "SoundBoard");
        //File[] files = soundBoard.listFiles();
        switch (args[0].toLowerCase()){
            case "list":
                e.reply("timer | chart | members | prime | getInvites | createInvite | getGuildsWithInvites | getLolItems " 
                    + "| renameFile | renameFiles | closeDatabase | getBlacklist | printJson | cacheThings | getServer | stats"
                    + "| insertEpriaInBlacklist | insertAlert | insertUser | trackScheduler | playPlaylist");
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
                //     ResultRow res = DatabaseHandler.fetchJRow(query);
                //     String newName = res.get("new_id");
                //     file.renameTo(new File(soundBoard + File.separator + newName + "." + extension));

                // }
            break;
            case "closedatabase":
                try {
                    DatabaseHandler.getConnection().close();
                } catch (SQLException e1) {
                    e1.printStackTrace();
                }
            break;
            case "getBlacklist":
                System.out.println(GuildCache.getGuildOrPut(e.getGuild().getId()).getBlacklistData().toString());
                break;
            case "13":
                HashMap<AlertKey<?>, AlertData> prova = GuildCache.getGuildOrPut(e.getGuild().getId()).getAlerts();
                String s = new JSONObject(prova).toJSONString();
                e.reply("```json\n" + GuildCache.getGuildOrPut(e.getGuild().getId()).toString() + "```");
                e.reply("```json\n" + s + "```");
                BlacklistData bd = GuildCache.getGuildOrPut(e.getGuild().getId()).getBlacklistData();
                e.reply("```json\n" + bd.toString()+ "```");
                HashMap<String, ChannelData> channels = GuildCache.getGuildOrPut(e.getGuild().getId()).getChannels();
                e.reply("```json\n" + new JSONObject(channels).toJSONString() + "```");
                e.reply("```json\n" + new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getMembers()).toJSONString() + "```");
                e.reply("```json\n" + new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getActionsWithId()).toJSONString() + "```");
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
                String sss = new JSONObject(GuildCache.getGuildOrPut(e.getGuild().getId()).getChannels()).toJSONString();
                e.reply("```json\n" + sss + "```");
                break;
            case "stats":
                query = "SELECT guild_id, room_id FROM room WHERE has_command_stats = 0";
                res = DatabaseHandler.safJQuery(query);
                for(QueryRecord row : res){
                    for (String bot : bots) {
                        query = "INSERT INTO channel(guild_id, channel_id, bot_id, stats_enabled) VALUES (" + row.get("guild_id") + ", "+ row.get("room_id") +", " + bot + ", 0)";
                        DatabaseHandler.safJQuery(query);
                    }
                }
                e.reply("Done");
                break;
            case "insertepriainblacklist":
                query = "SELECT id FROM guilds";
                res = DatabaseHandler.safJQuery(query);
                for(QueryRecord row : res){
                    query = "INSERT INTO blacklist(guild_id, user_id) VALUES (" + row.get("id") + "," + PermissionHandler.getEpria() + ")";
                    DatabaseHandler.safJQuery(query);
                }
                break;
            case "insertalert":
                query = "SELECT guild_id, role_id, level, message_text FROM reward";
                res = DatabaseHandler.safJQuery(query);
                for(QueryRecord row : res){
                    int id = 0;
                    java.sql.Connection c = DatabaseHandler.getConnection();
                    try (Statement stmt = c.createStatement()) {
                        DatabaseHandler.runQuery(stmt, "INSERT INTO alert(guild_id, bot_id, message, channel, enabled, type) VALUES('" + row.get("guild_id") + "','" + "938487470339801169" + "','" + row.get("message_text") + "','" + null + "', 1, '" + AlertType.REWARD.ordinal() + "');");
                        id = DatabaseHandler.fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
                        DatabaseHandler.runQuery(stmt, "INSERT INTO alert_reward(alert_id, level, temporary) VALUES(" + id + "," + row.get("level") + "," + 0 + ");");
                        DatabaseHandler.runQuery(stmt, "INSERT INTO alert_role(alert_id, role_id) VALUES(" + id + "," + row.get("role_id") + ");");
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
            res = DatabaseHandler.safJQuery(query);
            for(QueryRecord row : res){
                for (String bot : bots) {
                    query = "INSERT INTO user(user_id, guild_id, experience, level, messages, bot_id) VALUES (" + row.get("user_id") + ", " + row.get("guild_id") + ", " + row.get("exp") + ", " + row.get("level") + ", " + row.get("messages") + ", " + bot + ")";
                    DatabaseHandler.safJQuery(query);
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
                    java.sql.Connection c = DatabaseHandler.getConnection();
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
                        DatabaseHandler.runQuery(query1);
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
                        DatabaseHandler.runQuery(query1);
                    }
                    System.out.println(i);
                }
            case "soundsgozzing":
                query = "SELECT id from sound";
                QueryCollection res1 = DatabaseHandler.safJQuery(query);
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
                        DatabaseHandler.runQuery(query); // Uncomment this line to execute the query
                    }
                    break;
                    //DatabaseHandler.safJQuery(query1);
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
                HashMap<Long, List<String>> map = DatabaseHandler.getQueryAnalytics();      
                HashMap<Long, Integer> queriesPerHour = new HashMap<>();
                
                for (Map.Entry<Long, List<String>> entry : map.entrySet()) {
                    long hours = TimeUnit.MILLISECONDS.toHours(entry.getKey());
                    int queriesCount = entry.getValue().size();
                    queriesPerHour.put(hours, queriesPerHour.getOrDefault(hours, 0) + queriesCount);
                }
                Map<Long, Integer> sortedQueriesPerHour = queriesPerHour.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
                String[][] data = new String[sortedQueriesPerHour.size()][2];
                int i = 0;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d-M-Y H:m:s");
                for (Map.Entry<Long, Integer> entry : sortedQueriesPerHour.entrySet()) {
                    System.out.println(TimeUnit.HOURS.toMillis(entry.getKey()));
                    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(TimeUnit.HOURS.toMillis(entry.getKey())), ZoneId.systemDefault());
                    String formattedDate = dateTime.format(formatter);
                    data[i][0] = formattedDate;
                    data[i][1] = entry.getValue().toString();
                    i++;
                }    
                String[] headers = new String[] {"Time", "Query"};
                String table = TableHandler.constructTable(data, headers);
                
                e.getChannel().sendFiles(FileUpload.fromData(
                    table.getBytes(StandardCharsets.UTF_8),
                    "table.txt"
                )).queue();
            
                break;
            case "sqlday":                
                HashMap<Long, List<String>> map2 = DatabaseHandler.getQueryAnalytics();
                HashMap<Long, Integer> queriesPerDay = new HashMap<>();

                for (Map.Entry<Long, List<String>> entry : map2.entrySet()) {
                    // Convert milliseconds to days
                    long days = TimeUnit.MILLISECONDS.toDays(entry.getKey());
                    int queriesCount = entry.getValue().size();
                    queriesPerDay.put(days, queriesPerDay.getOrDefault(days, 0) + queriesCount);
                }

                Map<Long, Integer> sortedQueriesPerDay = queriesPerDay.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                        Map.Entry::getKey, 
                        Map.Entry::getValue, 
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

                String[][] data2 = new String[sortedQueriesPerDay.size()][2];
                int j = 0;
                DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("d-M-Y");

                for (Map.Entry<Long, Integer> entry : sortedQueriesPerDay.entrySet()) {
                    // Convert days back to milliseconds for the start of each day
                    long millisForDay = TimeUnit.DAYS.toMillis(entry.getKey());
                    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millisForDay), ZoneId.systemDefault());
                    String formattedDate = dateTime.format(formatter2);
                    data2[j][0] = formattedDate;
                    data2[j][1] = entry.getValue().toString();
                    j++;
                }

                String[] headers2 = new String[] {"Time", "Query"};
                String table2 = TableHandler.constructTable(data2, headers2);

                e.getChannel().sendFiles(FileUpload.fromData(
                    table2.getBytes(StandardCharsets.UTF_8),
                    "table.txt"
                )).queue();
            
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
            query = "SELECT st.id, st.account_id, sm.game_id, sm.league_shard FROM summoner_tracking st JOIN summoner_match sm ON st.summoner_match_id = sm.id WHERE st.lane = 4 AND NOT JSON_CONTAINS_PATH(st.build, 'one', '$.build.support_item') AND st.id > 11940;";
            
                res = DatabaseHandler.safJQuery(query);
                for(QueryRecord row : res){
                    String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
                    String game_id = region + "_"+row.get("game_id");
                    String account_id = row.get("account_id");
                    //String summoner_id = row.get("summoner_id");
                    LOLMatch match = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
                    String puuid = "";

                    LaneType lane = null;
                    TeamType team = null;
                    Summoner su = LeagueHandler.getSummonerByPuuid(account_id, LeagueShard.values()[row.getAsInt("league_shard")]);
                    for (MatchParticipant partecipant : match.getParticipants()) {
                        if (partecipant.getSummonerId().equals(su.getSummonerId())) {
                            lane = partecipant.getChampionSelectLane();
                            team = partecipant.getTeam();
                            puuid = partecipant.getPuuid();
                        }
                    }
                    HashMap<String, HashMap<String, String>> matchData = MatchTracker.analyzeMatchBuild(match, match.getParticipants());
                    if (matchData.get(puuid) == null) continue;
                    if (matchData.get(puuid).get("items") == null || matchData.get(puuid).get("starter") == null || matchData.get(puuid).get("starter").isBlank()) {
                        continue;
                    }
                    String build = MatchTracker.createJSONBuild(matchData.get(puuid));

                    query = "UPDATE summoner_tracking SET lane = '" + lane.ordinal() + "', side = '" + team.ordinal() + "', build = '" + build + "' WHERE id = " + row.get("id") + ";";
                    System.out.println(row.get("id"));
                    DatabaseHandler.runQueryAsync(query);
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e1) {

                    }
                }
            break;
            case "fixlolna":
                query = "SELECT game_id, account_id from summoner_tracking where league_shard = 8";
                res = DatabaseHandler.safJQuery(query);
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
                    DatabaseHandler.runQuery(query);
                }
            break;
            case "summoners":
                query = "SELECT account_id, league_shard from summoner";
                res = DatabaseHandler.safJQuery(query);
                String ssss = "";
                for(QueryRecord row : res){
                    String account_id = row.get("account_id");
                    int league_shard = row.getAsInt("league_shard");
                    Summoner summoner = LeagueHandler.getSummonerByPuuid(account_id, LeagueShard.values()[league_shard]);
                    if (summoner == null) {
                        System.out.println("Summoner not found");
                        continue;
                    }
                    RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
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
                res = DatabaseHandler.safJQuery(query);
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
                    DatabaseHandler.runQuery(query);
                }
            break;
            case "playplaylist":
                int playlistId = Integer.valueOf(args[1]);
                QueryCollection tracks = DatabaseHandler.getPlaylistTracks(playlistId, null, null);

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
                DatabaseHandler.addTrackToPlaylist(2, track.getInfo().uri, PlayerManager.get().encodeTrack(track), null);
            break;
            case "loadtracksfromdb":
                List<AudioTrack> tracksFinal = new ArrayList<>();
                QueryCollection tracksToLoad = DatabaseHandler.getPlaylistTracks(Integer.parseInt(args[1]), null, null);
                for(QueryRecord trackToLoad : tracksToLoad) {
                    tracksFinal.add(PlayerManager.get().decodeTrack(trackToLoad.get("encoded_track")));
                }
                SafjAudioPlaylist playlist = new SafjAudioPlaylist("Custom Playlist", tracksFinal, null);
                (new ResultHandler(e, false, PlayTiming.LAST)).playlistLoaded(playlist);
            break;
            case "loadqueuedb":
                DatabaseHandler.addTrackToPlaylist(Integer.valueOf(args[1]), (List<AudioTrack>) PlayerManager.get().getGuildMusicManager(e.getGuild()).getTrackScheduler().getQueue(), null);
            break;
            case "fixloldb":
                query = "SELECT id, summoner_id, league_shard from summoner";
                res = DatabaseHandler.safJQuery(query);
                for (QueryRecord acc : res) {
                    String summoner_id = acc.get("summoner_id");
                    int league_shard = acc.getAsInt("league_shard");
                    Summoner summoner = LeagueHandler.getSummonerByPuuid(summoner_id, LeagueShard.values()[league_shard]);
                    if (summoner == null) {
                        System.out.println("Summoner not found");
                        continue;
                    }
                    RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
                    String query1 = "UPDATE summoner SET riot_id = '" + (account.getName() + "#" + account.getTag()) + "', account_id = '" + summoner.getAccountId() + "', puuid = '" + summoner.getPUUID() + "' WHERE id = " + acc.get("id");
                    System.out.println(query1);
                    DatabaseHandler.runQuery(query1);
                    try {
                        Thread.sleep(350);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                }
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
                res = DatabaseHandler.safJQuery(query);
                query = "";
                for (QueryRecord row : res) {
                    int times = row.getAsInt("times");
                    System.out.println(row.get("sound_id") + " " + row.get("user_id") + " " + times );
                    for (i = 0; i < times; i++) {
                        query += "(" + row.get("user_id") + ", " + row.get("sound_id") + "),";
                    }
                    //remove ,
                    if (query.isBlank()) continue;
                    
                }
                query = query.substring(0, query.length() - 1);
                query = "INSERT INTO sound_interactions2(user_id, sound_id) VALUES " + query;
                DatabaseHandler.runQuery(query);
            break;
            case "splitlike":
                query = "select * from sound_interactions";
                res = DatabaseHandler.safJQuery(query);
                query = "";
                for (QueryRecord row : res) {
                    int likevalue = row.getAsInt("like") == 1 ? 1 : 0;
                    likevalue = row.getAsInt("dislike") == 1 ? -1 : likevalue;
                    query += "(" + row.get("user_id") + ", " + row.get("sound_id") + ", " + likevalue + "),";
                    
                }
                query = query.substring(0, query.length() - 1);
                query = "INSERT INTO sound_interactions3(user_id, sound_id, value) VALUES " + query;
                DatabaseHandler.runQuery(query);
            break;
            case "testblob":
                query = "SELECT * FROM soundboard WHERE id = 23";
                QueryRecord row1 = DatabaseHandler.fetchJRow(query);
                String blobString = row1.get("thumbnail");
                if (blobString != null) {
                    try {
                        // Decode the base64 string to get the byte array
                        byte[] bytes = Base64.getDecoder().decode(blobString);
        
                        // Create a temporary file
                        File file = new File("thumbnail.png");
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            fos.write(bytes);
                        }
        
                        System.out.println("File written successfully: " + file.getAbsolutePath());
                    } catch (IOException ee) {
                        ee.printStackTrace();
                    }
                } else {
                    System.out.println("No BLOB data found for the specified column.");
                }
                
                break;
            case "converttwitchalert":
                query = "select * from twitch_subscription";
                res = DatabaseHandler.safJQuery(query);

                for (QueryRecord r : res) {
                    int id = 0;
                    Connection c = DatabaseHandler.getConnection();
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
            res = DatabaseHandler.safJQuery(query);
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
                res = DatabaseHandler.safJQuery(query);
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
                    System.out.println(LeagueDBHandler.setMatchData(m));
                }
                break;
            case "pushbuild":
                query = "SELECT st.id, sm.game_id, sm.league_shard, st.account_id, s.summoner_id FROM summoner_tracking st JOIN summoner_match sm ON st.summoner_match_id = sm.id JOIN summoner s ON st.account_id = s.account_id AND st.league_shard = s.league_shard ORDER BY st.id;";
                res = DatabaseHandler.safJQuery(query);
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
                    System.out.println(LeagueDBHandler.setMatchData(m));
                }
                break;
            case "trackoldgames":
                if (true) {
                    Summoner sum = LeagueHandler.getSummonerByPuuid(args[1], LeagueShard.EUW1);
                    //MatchTracker.retriveOldGames(sum).queue();
                }
            break;
            case "mergelol":
            query = "SELECT st.id, sm.game_id, sm.league_shard, st.account_id, s.summoner_id FROM summoner_tracking st JOIN summoner_match sm ON st.summoner_match_id = sm.id JOIN summoner s ON st.account_id = s.account_id WHERE st.id > 294 ORDER BY st.id;";
            
                res = DatabaseHandler.safJQuery(query);
                System.out.println(res.size());
                for(QueryRecord row : res){
                    String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
                    String game_id = region + "_"+row.get("game_id");
                    //String account_id = row.get("account_id");
                    String summoner_id = row.get("summoner_id");
                    LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
                    String puuid = "";
                    int summoner_match_id = LeagueDBHandler.setMatchData(m);

                    HashMap<String, HashMap<String, String>> matchData = MatchTracker.analyzeMatchBuild(m, m.getParticipants());

                    System.out.println(row.get("id"));
                    for (MatchParticipant partecipant : m.getParticipants()) {
                        Summoner toPush = LeagueHandler.getSummonerByPuuid(partecipant.getPuuid(), LeagueShard.values()[row.getAsInt("league_shard")]);
                        MatchTracker.pushSummoner(m, summoner_match_id, toPush, partecipant, matchData.get(partecipant.getPuuid())).complete();
                        try {
                            Thread.sleep(1000);
                        } catch (Exception eee) { eee.printStackTrace(); }
                    }

                }
                break;

            case "fixmatch":
                query = "select id, game_id, league_shard from summoner_match where bans = '{}' order by id";
                res = DatabaseHandler.safJQuery(query);
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
                    DatabaseHandler.runQuery(query);
                    System.out.println(row.get("id"));
                    try {
                        Thread.sleep(1500);
                    } catch (Exception e1) {
                        
                    }
                }
                break;
                case "lolqueue":
                    System.out.println(MatchTracker.getMatchQueueCopy().size());
                break;
                case "pushlolqueue":
                    ChronoTask task =  () -> MatchTracker.popSet();
                    task.queue();
                break;
                case "error":
                    List<String> a = new ArrayList<String>();
                    a.get(0);
                break;
                case "pushsamplegame":
                    ChronoTask sampleTask =  () -> MatchTracker.retriveSampleGames();
                    sampleTask.queue();
                break;
                case "fixaccountid":
                    query = "SELECT id, puuid, league_shard FROM summoner WHERE account_id IS NULL ORDER BY id DESC";
                    res = LeagueDBHandler.safJQuery(query);
                    ChronoTask fixaccountTask = () -> {
                        int n = 0;
                        for (QueryRecord sum : res) {
                            try {
                                Summoner sssss = LeagueHandler.getSummonerByPuuid(sum.get("puuid"), LeagueShard.values()[Integer.valueOf(sum.get("league_shard"))]);
                                String fixQuery = "UPDATE summoner SET account_id = '" + sssss.getAccountId() + "' WHERE id=" + sum.get("id");
                                LeagueDBHandler.runQuery(fixQuery);
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
                    query = "SELECT s.id, s.puuid, s.league_shard FROM summoner s LEFT JOIN rank r ON s.id = r.summoner_id LEFT JOIN masteries m ON s.id = m.summoner_id WHERE r.summoner_id IS NULL AND m.summoner_id IS NULL ORDER BY s.id DESC;";
                    res = LeagueDBHandler.safJQuery(query);
                    ChronoTask bullshit = () -> {
                        int n = 0;
                        for (QueryRecord sum : res) {
                            try {
                                Summoner sssss = LeagueHandler.getSummonerByPuuid(sum.get("puuid"), LeagueShard.values()[Integer.valueOf(sum.get("league_shard"))]);
                                int summonerId = LeagueHandler.updateSummonerDB(sssss);
                                try {
                                    Thread.sleep(500);
                                } catch (Exception ee) {
                                ee.printStackTrace();
                                }
                                LeagueDBHandler.updateSummonerMasteries(summonerId, sssss.getChampionMasteries());
                                LeagueDBHandler.updateSummonerEntries(summonerId, sssss.getLeagueEntry());
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
                        MatchTracker.retriveMatchHistory(LeagueHandler.getSummonerByPuuid(args[1], LeagueShard.EUW1), GameQueueType.TEAM_BUILDER_RANKED_SOLO);
                    };
                    retriveAllGames.queue();
                break;
                case "setmatchevent":
                    query = "SELECT id, game_id, league_shard FROM `match` WHERE events = '{}' ORDER BY id DESC";
                    res = LeagueDBHandler.safJQuery(query);
                    ChronoTask setMatchEvent = () -> {
                        int n = 0;
                        for (QueryRecord row : res) {
                            try {
                                String region = LeagueShard.values()[row.getAsInt("league_shard")].name();
                                String game_id = region + "_"+row.get("game_id");
                                LOLMatch m = LeagueHandler.getRiotApi().getLoLAPI().getMatchAPI().getMatch(LeagueShard.values()[row.getAsInt("league_shard")].toRegionShard(), game_id);
                                if (m == null) continue;
                                LeagueDBHandler.setMatchEvent(row.getAsInt("id"), MatchTracker.createJSONEvents(MatchTracker.analyzeMatchBuild(m, m.getParticipants()).get("match")));
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
        QueryCollection res = DatabaseHandler.safJQuery(query);
        
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
