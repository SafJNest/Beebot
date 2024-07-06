package com.safjnest.commands.Owner;

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
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.model.UserData;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.BlacklistData;
import com.safjnest.model.guild.ChannelData;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertKey;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TableHandler;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.simple.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
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

    private GuildDataHandler gs;

    public Test(GuildDataHandler gs){
        this.name = "test";
        this.aliases = new String[]{"wip"};
        this.help = "";
        this.category = new Category("Owner");
        this.arguments = "faker";
        this.ownerCommand = true;
        this.hidden = true;
        this.gs = gs;
    }

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
                    + "| insertEpriaInBlacklist | insertAlert | insertUser | trackScheduler");
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
                for (Item item : RiotHandler.getRiotApi().getDDragonAPI().getItems().values()) {
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
                System.out.println(gs.getGuild(e.getGuild().getId()).getBlacklistData().toString());
                break;
            case "13":
                HashMap<AlertKey, AlertData> prova = gs.getGuild(e.getGuild().getId()).getAlerts();
                String s = new JSONObject(prova).toJSONString();
                e.reply("```json\n" + gs.getGuild(e.getGuild().getId()).toString() + "```");
                e.reply("```json\n" + s + "```");
                BlacklistData bd = gs.getGuild(e.getGuild().getId()).getBlacklistData();
                e.reply("```json\n" + bd.toString()+ "```");
                HashMap<Long, ChannelData> channels = gs.getGuild(e.getGuild().getId()).getChannels();
                e.reply("```json\n" + new JSONObject(channels).toJSONString() + "```");
                e.reply("```json\n" + new JSONObject(gs.getGuild(e.getGuild().getId()).getUsers()).toJSONString() + "```");
                break;
            case "14":
                for(Guild g : e.getJDA().getGuilds()) {
                    gs.getGuild(g.getId()).getAlerts();
                    gs.getGuild(g.getId()).getBlacklistData();
                    for(GuildChannel cd : g.getChannels()) {
                        gs.getGuild(g.getId()).getChannelData(cd.getId());
                    }
                    for(Member m : g.getMembers()){
                        gs.getGuild(g.getId()).getUserData(m.getId());
                        Bot.getUserData(m.getId());
                    }
                }
                e.reply("Done");
                break;
            case "getServer":
                String sss = new JSONObject(gs.getGuild(e.getGuild().getId()).getChannels()).toJSONString();
                e.reply("```json\n" + sss + "```");
                break;
            case "stats":
                query = "SELECT guild_id, room_id FROM room WHERE has_command_stats = 0";
                res = DatabaseHandler.safJQuery(query);
                for(ResultRow row : res){
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
                for(ResultRow row : res){
                    query = "INSERT INTO blacklist(guild_id, user_id) VALUES (" + row.get("id") + "," + PermissionHandler.getEpria() + ")";
                    DatabaseHandler.safJQuery(query);
                }
                break;
            case "insertalert":
                query = "SELECT guild_id, role_id, level, message_text FROM reward";
                res = DatabaseHandler.safJQuery(query);
                for(ResultRow row : res){
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
                        } catch(SQLException ee) {
                            System.out.println(ee.getMessage());
                        }
                        System.out.println(ex.getMessage());
                    }
                }
                break;
            case "insertuser":
            query = "SELECT user_id, guild_id, exp, level, messages FROM experience";
            res = DatabaseHandler.safJQuery(query);
            for(ResultRow row : res){
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
                    Bot.getGuildData(e.getGuild()).getChannelData(c.getId()).setCommand(false);
                });
                break;
            case "disablecommand":
                Bot.getGuildData(e.getGuild()).getChannelData(e.getTextChannel().getId()).setCommand(false);
                break;
            case "enablecommand":
                Bot.getGuildData(e.getGuild()).getChannelData(e.getTextChannel().getId()).setCommand(true);
                break;
            case "enablecommands":
                e.getGuild().getTextChannels().forEach(c -> {
                    Bot.getGuildData(e.getGuild()).getChannelData(c.getId()).setCommand(true);
                });
                break;
            case "cachesize":
                Bot.getGuildSettings().getGuilds().setMaxSize(Integer.parseInt(args[1]));
                e.reply("New cache max size: " + args[1]);
                break;
            case "userdata":
                try {
                    e.reply(Bot.getUserData(args[1]).toString());
                } catch (Exception e1) {
                    e.reply(Bot.getUserData(e.getAuthor().getId()).toString());
                }
                break;
            case "usersdata":
                String users = "";
                for (UserData ud : Bot.getUsers().values(false)) {
                    users += ud.getName() + "-";
                }
                e.reply(users);
                break;
            case "clearcache":
                Bot.getGuildSettings().getGuilds().clear();
                Bot.getUsers().clear();
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
                QueryResult res1 = DatabaseHandler.safJQuery(query);
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
                                ResultRow row = res1.get(j);
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
                    GuildData gd = Bot.getGuildData(g);
                    for (Member m : g.getMembers()) {
                        gd.getUserData(m.getId()).setUpdateTime(61);
                    }
                    for (TextChannel tc : g.getTextChannels()) {
                        gd.getChannelData(tc.getId()).setExpEnabled(true);
                    }
                }

                break;
            case "lolversion":
                RiotHandler.setVersion(args[1]);
                e.reply("new version: " + RiotHandler.getVersion());
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
            default:
                e.reply("Command does not exist (use list to list the commands).");
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
        QueryResult res = DatabaseHandler.safJQuery(query);
        
        for(ResultRow row : res){
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
