package com.safjnest.commands.League.slash.graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.commands.League.Summoner;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SafJNest;
import com.safjnest.util.TimeConstant;
import com.safjnest.util.LOL.RiotHandler;

import io.quickchart.QuickChart;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class GraphLpSlash extends SlashCommand {
 
    public GraphLpSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "summoner", "Name and tag of the summoner you want to get information on", false),
            RiotHandler.getLeagueShardOptions(),
            new OptionData(OptionType.STRING, "period", "Period of time to get the LP from", false)
                .addChoice("Today", "today")
                .addChoice("Week", "week")
                .addChoice("Month", "month")
                .addChoice("Split", "split"),
            new OptionData(OptionType.STRING, "date-start", "Date to get the LP from", false),
            new OptionData(OptionType.STRING, "data-end", "Date to get the LP to", false)

        );
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String period = event.getOption("period") != null ? event.getOption("period").getAsString() : null;
        String dateStart = event.getOption("date-start") != null ? event.getOption("date-start").getAsString() : SafJNest.getFormattedDate(SafJNest.midnightMilli());
        String dateEnd = event.getOption("date-end") != null ? event.getOption("date-end").getAsString() : SafJNest.getFormattedDate(SafJNest.midnightMilli() + TimeConstant.DAY);

        long timeStart = 0;
        long timeEnd = 0;

        if (period != null) {
            switch (period) {
                case "today":
                    timeStart = SafJNest.midnightMilli();
                    timeEnd = timeStart + TimeConstant.DAY;
                    break;
                case "week":
                    timeStart = SafJNest.midnightMilli() - TimeConstant.WEEK;
                    timeEnd = SafJNest.midnightMilli() + TimeConstant.DAY;
                    break;
                case "month":
                    timeStart = SafJNest.firstDayOfMonthMilli();
                    timeEnd = SafJNest.midnightMilli() + TimeConstant.DAY;
                    break;
            }
        }
        else {
            timeStart = SafJNest.parseDate(dateStart);
            timeEnd = SafJNest.parseDate(dateEnd);
        }

        System.out.println(timeStart + "\n" + timeEnd);

        Button left = Button.primary("lol-left", "<-");
        Button right = Button.primary("lol-right", "->");
        Button center = Button.primary("lol-center", "f");
        
        no.stelar7.api.r4j.pojo.lol.summoner.Summoner s = null;

        User theGuy = null;
        event.deferReply(false).queue();

        if(event.getOption("summoner") == null && event.getOption("user") == null) theGuy = event.getUser();
        else if(event.getOption("user") != null) theGuy = event.getOption("user").getAsUser();
        
        s = RiotHandler.getSummonerByArgs(event);
        if(s == null){
            event.getHook().editOriginal("Couldn't find the specified summoner. Remember to specify the tag or connect an account using ```/summoner connect```").queue();
            return;
        }
        
        EmbedBuilder builder = createEmbed(s, timeStart, timeEnd);
        
        if(theGuy != null && RiotHandler.getNumberOfProfile(theGuy.getId()) > 1){
            RiotAccount account = RiotHandler.getRiotApi().getAccountAPI().getAccountByPUUID(RegionShard.EUROPE, s.getPUUID());
            center = Button.primary("lol-center-" + s.getPUUID() + "#" + s.getPlatform().name(), account.getName());
            center = center.asDisabled();

            WebhookMessageEditAction<Message> action = event.getHook().editOriginalEmbeds(Summoner.createEmbed(event.getJDA(), event.getJDA().getSelfUser().getId(),s).build());
            action.setComponents(ActionRow.of(left, center, right)).queue();
            return;
        }

        event.getHook().editOriginalEmbeds(builder.build()).queue();
        

	}

    public static EmbedBuilder createEmbed(no.stelar7.api.r4j.pojo.lol.summoner.Summoner s , long timeStart, long timeEnd) {
        EmbedBuilder builder = new EmbedBuilder();
        String chartUrl = createGraph(s, timeStart, timeEnd);

        try {
            String shortenedUrl = shortenUrl(chartUrl);
            builder.setImage(shortenedUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return builder;

    }

    private static boolean splitMonths(QueryResult result) {
        return result.size() > 60;
    }

    private static Set<String> getTimeLabels(QueryResult result) {
        Set<String> labels = new LinkedHashSet<>();
        if (splitMonths(result)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/yyyy").withZone(ZoneId.of("GMT"));
            for (ResultRow row : result) {
                Instant instant = Instant.ofEpochSecond(row.getAsEpochSecond("time"));
                ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));
                String formattedTime = formatter.format(gmtTime);
                labels.add(formattedTime);
            }
        }
        else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d/M/yyyy").withZone(ZoneId.of("GMT"));
            for (ResultRow row : result) {
                Instant instant = Instant.ofEpochSecond(row.getAsEpochSecond("time"));
                ZonedDateTime gmtTime = instant.atZone(ZoneId.of("GMT"));
                String formattedTime = formatter.format(gmtTime);
                labels.add(formattedTime);
            }
        }

        return labels;
    }

    private static List<Integer> getValues(QueryResult result, Map<Integer, Integer> tierDivisionMapReformed) {
        List<Integer> values = new ArrayList<>();

        if (splitMonths(result)) {
            Map<Long, Integer> dailyMaxValues = new HashMap<>();

            for (ResultRow row : result) {
                long t = SafJNest.firstDayOfMonth(row.getAsEpochSecond("time"));
                int cont = tierDivisionMapReformed.get(row.getAsInt("rank")) + Integer.parseInt(row.get("lp"));
                dailyMaxValues.put(t, Math.max(dailyMaxValues.getOrDefault(t, 0), cont));
            }

            values.addAll(dailyMaxValues.values());
        } else {
            for (ResultRow row : result) {
                int cont = tierDivisionMapReformed.get(row.getAsInt("rank")) + Integer.parseInt(row.get("lp"));
                values.add(cont);
            }
        }

        return values;
    }

    private static String createGraph(no.stelar7.api.r4j.pojo.lol.summoner.Summoner s, long timeStart, long timeEnd) {
        QueryResult result = DatabaseHandler.getSummonerData(s.getAccountId(), s.getPlatform(), timeStart, timeEnd);
        
        if (result.isEmpty()) return null;

        HashMap<Integer, Integer> tierDivisionList = RiotHandler.getTierDivision();

        Set<String> labels = getTimeLabels(result);
        List<Integer> values = getValues(result, tierDivisionList);

        double max = Math.floor((values.stream().max(Integer::compare).get() + 99) / 100.0) * 100;
        double min = Math.ceil(values.stream().min(Integer::compare).get() / 100) * 100;

        
        String labelsJson = new Gson().toJson(labels);
        String valuesJson = new Gson().toJson(values);

        Map<Integer, String> tierDivisionMap = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : tierDivisionList.entrySet()) {
            tierDivisionMap.put(entry.getValue(), TierDivisionType.values()[entry.getKey()].name());
        }
                        
        String tierDivisionJson = new Gson().toJson(tierDivisionMap);

        QuickChart chart = new QuickChart();
        chart.setWidth(500);
        chart.setHeight(300);
        chart.setVersion("2");
        chart.setBackgroundColor("#fff");


        String graph = "{"
                + "type: 'line',"
                + "data: {"
                + "labels: " + labelsJson +","
                + "datasets: [{"
                + "label: 'LPs',"
                + "backgroundColor: 'rgb(255, 99, 132)',"
                + "borderColor: 'rgb(255, 99, 132)',"
                + "radius:0,"
                + "data: " + valuesJson + ","
                + "fill: false"
                + "}]"
                + "},"
                + "options: {"
                + "backgroundColor: '#f0f0f0',"
                + "scales: {"
                + "xAxes: [{"
                + "display: true,"
                + "scaleLabel: {"
                + "display: true"
                + "}"
                + "}],"
                + "yAxes: [{"
                + "id: 'A',"
                + "type: 'linear',"
                + "position: 'left',"
                + "display: true,"
                + "scaleLabel: {"
                + "display: true,"
                + "},"
                + "ticks: {"
                + "min: " +  min + ","
                + "max: " + max + ","
                + "callback: function(value) {"
                + "var tiers = " + tierDivisionJson + ";"
                + "if (tiers[value]) {"
                + "return tiers[value];"
                + "}"
                + "return '';"
                + "}"
                + "}"
                + "}]"
                + "}"
                + "}"
                + "}";
        chart.setConfig(graph);
        System.out.println(graph);

        return chart.getUrl();
    }

        private static String shortenUrl(String longUrl) throws IOException {
        URL url = new URL("https://tinyurl.com/api-create.php?url=" + longUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }

        in.close();
        conn.disconnect();

        return content.toString();
    }

}
