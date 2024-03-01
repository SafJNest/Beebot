package com.safjnest.Commands.Owner;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Guild.GuildSettings;
import com.safjnest.Utilities.Guild.Alert.AlertData;
import com.safjnest.Utilities.Guild.Alert.AlertType;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import no.stelar7.api.r4j.pojo.lol.staticdata.item.Item;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.sql.SQLException;
/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.3
 */
public class Test extends Command{

    private GuildSettings gs;

    public Test(GuildSettings gs){
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
        String[] args = e.getArgs().split(" ", 2);
        if(args.length == 0 || !SafJNest.intIsParsable(args[0])) return;


        //File soundBoard = new File("rsc" + File.separator + "SoundBoard");
        //File[] files = soundBoard.listFiles();
        switch (Integer.parseInt(args[0])){
            case 1:
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

            case 2:
                createAndSaveChartAsPNG();
            break;
            case 3:
                for(Member m : e.getJDA().getGuildById("943974473370062948").getMembers()){
                    System.out.println(m.getEffectiveName() + " " + m.getId());
                }
            break;
            case 4:
                e.reply(SafJNest.getRandomPrime(Integer.parseInt(args[1])).toString());
            break;
            case 5:
                String invites = "";
                for(Invite invite : e.getJDA().getGuildById(args[1]).retrieveInvites().complete()) {
                    invites += "code: " + invite.getCode() 
                        + " - max age: " + invite.getMaxAge() + "s"
                        + " - max uses: " + invite.getMaxUses() 
                        + " - uses: " + invite.getUses()
                        + ((invite.getChannel() != null) ? (" - channel: " + invite.getChannel().getName()) : "")
                        + ((invite.getGroup() != null) ? (" - group: " + invite.getGroup().getName()) : "")
                        + " - inviter: " + invite.getInviter().getGlobalName()
                        + " - target type: " + invite.getTargetType()
                        + ((invite.getTarget() != null && invite.getTarget().getUser() != null) ? (" - target user: " + invite.getTarget().getUser().getName()) : "")
                        + " - is temporary: " + invite.isTemporary()
                        + " - time created: " + "<t:" + invite.getTimeCreated().toEpochSecond() + ":d>" + "\n";
                }
                e.reply("here are the invites for " + e.getJDA().getGuildById(args[1]).getName() + " (" + e.getJDA().getGuildById(args[1]).getId() + "):\n" + invites);
            break;
            case 6:
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
            case 7:
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
            case 8:
                System.out.println("eee");
                String ss = "";
                for (Item item : RiotHandler.getRiotApi().getDDragonAPI().getItems().values()) {
                    System.out.println(item.getId());
                    if (item != null)
                        ss += RiotHandler.getFormattedEmoji(e.getJDA(), item.getId()) + "-";
                }
                System.out.println("efee");
                e.reply(ss);

            break;
            case 9:
                // for(File file : files){
                //     String name = file.getName().split("\\.")[0];
                //     String extension = file.getName().split("\\.")[1];
                //     String newName = String.valueOf(Integer.valueOf(name) + 1000);
                //     file.renameTo(new File(soundBoard + File.separator + newName + "." + extension));

                // }
            break;
            case 10:
                // for(File file : files){
                //     String name = file.getName().split("\\.")[0];
                //     String extension = file.getName().split("\\.")[1];

                //     String query = "SELECT * FROM sound WHERE id = " + name + ";";
                //     ResultRow res = DatabaseHandler.fetchJRow(query);
                //     String newName = res.get("new_id");
                //     file.renameTo(new File(soundBoard + File.separator + newName + "." + extension));

                // }
            break;
            case 11:
                try {
                    DatabaseHandler.getConnection().close();
                } catch (SQLException e1) {
                    e1.printStackTrace();
                }
            break;
            case 12:
                System.out.println(gs.getServer(e.getGuild().getId()).getBlacklistData().toString());
                break;
            case 13:
                HashMap<AlertType, AlertData> prova = gs.getServer(e.getGuild().getId()).getAlerts();
                for(AlertType at : prova.keySet()){
                    System.out.println(prova.get(at).toString());
                }
                break;
            default:
                e.reply("Command does not exist.");
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

}