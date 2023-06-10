package com.safjnest.SlashCommands.LOL;

import java.util.Arrays;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.Commands.CommandsLoader;
import com.safjnest.Utilities.LOL.LOLHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.net.HttpURLConnection;
import java.net.URL;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;



/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class ChampionSlash extends SlashCommand {
 
    /**
     * Constructor
     */
    public ChampionSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "champ", "Champion Name", true),
            new OptionData(OptionType.STRING, "lane", "Champion Lane", true)
                .addChoice("Top Lane", "TOP")
                .addChoice("Jungle", "JUNGLE")
                .addChoice("Mid Lane", "MID")
                .addChoice("ADC", "ADC")
                .addChoice("Support", "SUPPORT"));
    }
    /*
    1 conque, letha...
    2 dark
    3 ecewfc
    4 fewf
    5 fwf
    */


	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();
        
        String champName = event.getOption("champ").getAsString();
        String lane = event.getOption("lane").getAsString();
        String laneFormatName =  "";
        switch(lane){
            case "TOP":
                laneFormatName = "Top Lane";
                break;
            case "JUNGLE":
                laneFormatName = "Jungle";
                break;
            case "MID":
                laneFormatName = "Mid Lane";
                break;
            case "ADC":
                laneFormatName = "ADC";
                break;
            case "SUPPORT":
                laneFormatName = "Support";
                break;
        }
        
        if(champName.equalsIgnoreCase("nunu"))
            champName+="willump";

        String[] champions = {"Aatrox", "Ahri", "Akali", "Alistar", "Amumu", "Anivia", "Annie", "Aphelios", "Ashe",
            "Aurelion Sol", "Azir", "Bard", "Blitzcrank", "Brand", "Braum", "Caitlyn", "Camille", "Cassiopeia",
            "Cho'Gath", "Corki", "Darius", "Diana", "Dr. Mundo", "Draven", "Ekko", "Elise", "Evelynn", "Ezreal",
            "Fiddlesticks", "Fiora", "Fizz", "Galio", "Gangplank", "Garen", "Gnar", "Gragas", "Graves", "Hecarim",
            "Heimerdinger", "Illaoi", "Irelia", "Ivern", "Janna", "Jarvan IV", "Jax", "Jayce", "Jhin", "Jinx",
            "Kai'Sa", "Kalista", "Karma", "Karthus", "Kassadin", "Katarina", "Kayle", "Kayn", "Kennen", "Kha'Zix",
            "Kindred", "Kled", "Kog'Maw", "LeBlanc", "Lee Sin", "Leona", "Lillia", "Lissandra", "Lucian", "Lulu",
            "Lux", "Malphite", "Malzahar", "Maokai", "Master Yi", "Milio","Miss Fortune", "Mordekaiser", "Morgana", "Nami",
            "Nasus", "Nautilus", "Neeko", "Nidalee", "Nocturne", "Nunu & Willump", "Olaf", "Orianna", "Ornn", "Pantheon",
            "Poppy", "Pyke", "Qiyana", "Quinn", "Rakan", "Rammus", "Rek'Sai", "Rell", "Renekton", "Rengar", "Riven",
            "Rumble", "Ryze", "Samira", "Sejuani", "Senna", "Seraphine", "Sett", "Shaco", "Shen", "Shyvana", "Singed",
            "Sion", "Sivir", "Skarner", "Sona", "Soraka", "Swain", "Sylas", "Syndra", "Tahm Kench", "Taliyah", "Talon",
            "Taric", "Teemo", "Thresh", "Tristana", "Trundle", "Tryndamere", "Twisted Fate", "Twitch", "Udyr", "Urgot",
            "Varus", "Vayne", "Veigar", "Vel'Koz", "Vi", "Viego","Viktor", "Vladimir", "Volibear", "Warwick", "Wukong", "Xayah",
            "Xerath", "Xin Zhao", "Yasuo", "Yone", "Yorick", "Yuumi", "Zac", "Zed", "Ziggs", "Zilean", "Zoe", "Zyra"};
        
        champName = findChampion(champName, champions);
    

        String msg = "";
        try {
           
            URL url = new URL("https://widget.mobalytics.gg/lol/graphql/v1/query");
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept", "application/json");
            con.setDoOutput(true);
            String jsonInputString = "{\r\n  \"query\": \"query ChampionQuery($champion: String! = \\\"akali\\\", $role: Rolename! = UNKNOWN) {\\n  lol {\\n    champion(filters: {slug: $champion, role: $role}) {\\n      build {\\n        championSlug\\n        items {\\n          type\\n          items\\n          __typename\\n        }\\n        name\\n        patch\\n        perks {\\n          IDs\\n          style\\n          subStyle\\n          __typename\\n        }\\n        role\\n        skillOrder\\n        spells\\n        stats {\\n          matchCount\\n          __typename\\n        }\\n        type\\n        __typename\\n      }\\n      stats {\\n        tier\\n        winRateHistory {\\n          x\\n          value\\n          __typename\\n        }\\n        __typename\\n      }\\n      __typename\\n    }\\n    __typename\\n  }\\n}\\n\",\r\n  \"operationName\": \"ChampionQuery\",\r\n  "+
                                        "\"variables\": { \"champion\": \""+champName+"\", \"role\": \""+lane+"\" }\r\n}";
            
            try(OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);			
            }

            String json = null;
            try(BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    json = response.toString();
                }
                
            
            EmbedBuilder eb = new EmbedBuilder(); 
            eb = new EmbedBuilder(); 
            eb.setTitle(":sparkles:Beebot Rune Command"); 
            eb.setDescription("**Highest Win Rate** info for " + LOLHandler.getFormattedEmoji(event.getJDA(), champName) + " " + champName + " " + LOLHandler.getFormattedEmoji(event.getJDA(), laneFormatName) + " **" + laneFormatName + "**");
            eb.setAuthor(event.getJDA().getSelfUser().getName(), "https://github.com/SafJNest",event.getJDA().getSelfUser().getAvatarUrl()); 
            
            String runes[] = getRunes(json);
            String roots[] = getRunesRoot(json);
            String skills[] = getSkillsOrder(json);
            String spells[] = getSummonerSpell(json);
            String[] starter = getBuild(json, 0);
            String[] core = getBuild(json, 2);
            String[] fullBuild = getBuild(json, 3);
            String[] situational = getBuild(json, 4);
            


            /*
             *  Skill Order
             */
            msg = "​";
            for(int i = 0; i < 18; i++){
                switch (skills[i]){
                    case "1":
                        msg += "Q -> ";
                        break;
                    case "2":
                        msg += "W -> ";
                        break;
                    case "3":  
                        msg +=  "E -> ";
                        break;
                    case "4":      
                        msg += " R -> ";
                        break;

                }
            }
            /*
             *  Summoner Spells
             */

            eb.addField("**Summoner Spells**", LOLHandler.getFormattedEmoji(event.getJDA(), spells[0] + "_") + " " + LOLHandler.getFormattedEmoji(event.getJDA(), spells[1] + "_"), false);


            eb.addField("**Skills Order**", msg.substring(0, msg.length()-4), false);
            /*
             * First Runes Root
             */
            msg = "​\n";
            for(int i = 0; i < 4; i++){
                msg += LOLHandler.getFormattedEmoji(event.getJDA(), runes[i]) + " " + LOLHandler.getRunesHandler().get(roots[0]).getRune(runes[i]).getName() + "\n";
            }
            String support = LOLHandler.getRunesHandler().get(roots[0]).getName();
            eb.addField(LOLHandler.getFormattedEmoji(event.getJDA(), support) + " " + support, msg, true);

            /*
             * Second Runes Root
             */
            msg = "​\n";
            for(int i = 4; i < 6; i++){
                msg += LOLHandler.getFormattedEmoji(event.getJDA(), runes[i]) + " " + LOLHandler.getRunesHandler().get(roots[1]).getRune(runes[i]).getName() + "\n";
            }
            support = LOLHandler.getRunesHandler().get(roots[1]).getName();
            eb.addField(LOLHandler.getFormattedEmoji(event.getJDA(), support) + " " + support, msg, true);

            /*
             * Stats
             */
            msg = "​\n";
            msg += LOLHandler.getFormattedEmoji(event.getJDA(), runes[6]) + " Offense\n";
            msg += LOLHandler.getFormattedEmoji(event.getJDA(), runes[7]) + " Flex\n";
            msg += LOLHandler.getFormattedEmoji(event.getJDA(), runes[8]) + " Defense\n";
            eb.addField("**Shard**", msg, true);


            /*
             * Starter Items
             */
            msg = "​";
            for(int i = 0; i < starter.length; i++){
                msg += LOLHandler.getFormattedEmoji(event.getJDA(), starter[i]) + " " + LOLHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(starter[i])).getName() + "\n";
            }
            eb.addField("**Starter Items**", msg, true);

            /*
             * Core Items
             */
            msg = "​";
            for(int i = 0; i < core.length; i++){
                msg += LOLHandler.getFormattedEmoji(event.getJDA(), core[i])  + " " + LOLHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(core[i])).getName() + "\n";
            }
            eb.addField("**Core Items**", msg, true);

            /*
             * Full Build
             */

            msg = "​";
            for(int i = 0; i < fullBuild.length; i++){
                msg += LOLHandler.getFormattedEmoji(event.getJDA(), fullBuild[i])  + " " + LOLHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(fullBuild[i])).getName() + "\n";
            }

            eb.addField("**Full Build**", msg, true);

            /*
             * Situational Items
             */
            msg = "​";
            for(int i = 0; i < situational.length; i++){
                msg += LOLHandler.getFormattedEmoji(event.getJDA(), situational[i])  + " " + LOLHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(situational[i])).getName() + "\n";
            }
            eb.addField("**Situational Items**", msg, true);


            eb.setColor(Color.decode(
                BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color
            ));
            
            
            champName = LOLHandler.transposeChampionNameForDataDragon(champName);
            eb.setThumbnail(LOLHandler.getChampionProfilePic(champName));
            eb.setFooter("There could be some issues with champions name, like Dr. Mundo, Aurelion Sol...Be careful when you digit it.", null); 

            
            event.getHook().editOriginalEmbeds(eb.build()).queue();
            
        } catch (Exception e) { 
            e.printStackTrace();
            event.getHook().editOriginal("Could be some problem with our database or lack of data due to new patch. Try again later.").queue();
        } 

	}


    public String[] getRunes(String json){
        JSONParser parser = new JSONParser(); 
        try {
            JSONObject file = (JSONObject) parser.parse(json); 
            JSONObject summary = (JSONObject) file.get("data"); 
            JSONObject keria = (JSONObject) summary.get("lol"); 
            JSONObject win = (JSONObject) keria.get("champion"); 
            JSONObject set = (JSONObject) win.get("build"); 
            JSONObject perks = (JSONObject) set.get("perks"); 
            JSONArray runes = (JSONArray) perks.get("IDs"); 
            String[] result = new String[runes.size()]; 
            for (int i = 0; i < runes.size(); i++) { 
                result[i] = String.valueOf(runes.get(i)); 
            } 
            return result;       
        } catch (Exception e) { 
            e.printStackTrace(); 
            return null; 
        }
    }

    public String[] getRunesRoot(String json){
        String[] roots = new String[2];
        JSONParser parser = new JSONParser(); 
        try {
            JSONObject file = (JSONObject) parser.parse(json); 
            JSONObject summary = (JSONObject) file.get("data"); 
            JSONObject keria = (JSONObject) summary.get("lol"); 
            JSONObject win = (JSONObject) keria.get("champion"); 
            JSONObject set = (JSONObject) win.get("build"); 
            JSONObject perks = (JSONObject) set.get("perks"); 
            roots[0] = perks.get("style").toString(); 
            roots[1] = perks.get("subStyle").toString(); 
            return roots;       
        } catch (Exception e) { 
            e.printStackTrace(); 
            return null; 
        }
    }

    public String[] getSummonerSpell(String json){
        String[] spells = new String[2];
        JSONParser parser = new JSONParser(); 
        try {
            JSONObject file = (JSONObject) parser.parse(json); 
            JSONObject summary = (JSONObject) file.get("data"); 
            JSONObject keria = (JSONObject) summary.get("lol"); 
            JSONObject win = (JSONObject) keria.get("champion"); 
            JSONObject set = (JSONObject) win.get("build"); 
            JSONArray spellsJson = (JSONArray) set.get("spells"); 
            for (int i = 0; i < spellsJson.size(); i++) { 
                spells[i] = String.valueOf(spellsJson.get(i)); 
            } 
            return spells;
        } catch (Exception e) { 
            e.printStackTrace(); 
            return null; 
        }
    }

     public String[] getSkillsOrder(String json){
        String[] skills = new String[18];
        JSONParser parser = new JSONParser(); 
        try {
            JSONObject file = (JSONObject) parser.parse(json); 
            JSONObject summary = (JSONObject) file.get("data"); 
            JSONObject keria = (JSONObject) summary.get("lol"); 
            JSONObject win = (JSONObject) keria.get("champion"); 
            JSONObject set = (JSONObject) win.get("build"); 
            JSONArray skillsJson = (JSONArray) set.get("skillOrder"); 
            for (int i = 0; i < skillsJson.size(); i++) { 
                skills[i] = String.valueOf(skillsJson.get(i)); 
            } 
            return skills;
        } catch (Exception e) { 
            e.printStackTrace(); 
            return null; 
        }
    }

    public String[] getBuild(String json, int index){
        JSONParser parser = new JSONParser(); 
        try {
            JSONObject file = (JSONObject) parser.parse(json); 
            JSONObject summary = (JSONObject) file.get("data"); 
            JSONObject keria = (JSONObject) summary.get("lol"); 
            JSONObject win = (JSONObject) keria.get("champion"); 
            JSONObject set = (JSONObject) win.get("build"); 
            JSONArray items = (JSONArray) set.get("items"); 
            JSONObject build = (JSONObject) items.get(index); 
            JSONArray starter = (JSONArray) build.get("items");
            String[] result = new String[starter.size()];
            for (int i = 0; i < starter.size(); i++) { 
                result[i] = String.valueOf(starter.get(i)); 
            }
            return result;
        } catch (Exception e) { 
            e.printStackTrace(); 
            return null; 
        }
    }


    public static String findChampion(String input, String[] champions) {
        double maxSimilarity = 0;
        String championName = "";
        
        for (String champion : champions) {
            double similarity = calculateSimilarity(input, champion);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                championName = champion;
            }
        }
        
        return championName;
    }
    
    private static double calculateSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }
    
    private static int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
    
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }
    
}
