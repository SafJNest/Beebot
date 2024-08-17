package com.safjnest.commands.lol;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class UltimateBravery extends Command {
    
    /**
     * Constructor
     */
    public UltimateBravery(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    /**
     * This method is called every time a member executes the command.
     */
	@Override
	protected void execute(CommandEvent event) {
        
        String json = LeagueHandler.getBraveryBuildJSON();
        EmbedBuilder embed = createEmbed(json);
        event.reply(embed.build());
    
	}


    public static EmbedBuilder createEmbed(String json) {
        JSONParser parser = new JSONParser();

        String title = "";
        String champion = "";
        String seedID = "";
        String role = "";

        String firstSpell = "";

        String[] summonerSpells = new String[2];

        String runePrimaryStyle = "";
        String[] runePrimary = new String[4];

        String runeSecondaryStyle = "";
        String[] runeSecondary = new String[2];

        String[] startingItems = new String[6];
        String[] earlyItems = new String[6];
        String[] fullBuild = new String[6];




        try {
            JSONObject file = (JSONObject) parser.parse(json);
            JSONObject data = (JSONObject) file.get("data");

            title = (String) data.get("title");
            seedID = String.valueOf((Long) data.get("seedId"));

            JSONObject championData = (JSONObject) data.get("champion");
            champion = (String) championData.get("name");
            JSONObject championSpell = (JSONObject) championData.get("spell");
            firstSpell = (String) championSpell.get("key");

            role = (String) data.get("role");

            JSONObject itemSet = (JSONObject) data.get("itemSet");
            JSONArray blocks = (JSONArray) itemSet.get("blocks");


            JSONObject si = (JSONObject) blocks.get(0);
            JSONArray items = (JSONArray) si.get("items");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = (JSONObject) items.get(i);
                startingItems[i] = (String) item.get("id");
            }

            JSONObject ei = (JSONObject) blocks.get(1);
            items = (JSONArray) ei.get("items");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = (JSONObject) items.get(i);
                earlyItems[i] = (String) item.get("id");
            }

            JSONObject fb = (JSONObject) blocks.get(2);
            items = (JSONArray) fb.get("items");
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = (JSONObject) items.get(i);
                fullBuild[i] = (String) item.get("id");
            }


            JSONObject runes = (JSONObject) data.get("runes");
            runePrimaryStyle = LeagueHandler.convertRuneRootToId((String) runes.get("primaryStyle"));

            JSONObject primary = (JSONObject) runes.get("primary");
            int dioporco = 0;
            for (Object k : primary.keySet()) {
                runePrimary[dioporco] = (String) primary.get((String) k);
                dioporco++;
            }

            runeSecondaryStyle = LeagueHandler.convertRuneRootToId((String) runes.get("secondaryStyle"));
            JSONObject secondary = (JSONObject) runes.get("secondary");
            dioporco = 0;
            for (Object k : secondary.keySet()) {
                runeSecondary[dioporco] = (String) secondary.get(k);
                dioporco++;
            }

            JSONObject spells = (JSONObject) data.get("summonerSpells");
            dioporco = 0;
            for (Object k : spells.keySet()) {
                summonerSpells[dioporco] = LeagueHandler.convertSpellToId((String) k);
                dioporco++;
            }


            String laneFormatName =  "";
            switch(role){
                case "Top":
                    laneFormatName = "Top Lane";
                    break;
                case "Jungle":
                    laneFormatName = "Jungle";
                    break;
                case "Mid":
                    laneFormatName = "Mid Lane";
                    break;
                case "Bot":
                    laneFormatName = "ADC";
                    break;
                case "Support":
                    laneFormatName = "Support";
                    break;
            }
            
            StaticChampion champ = LeagueHandler.getChampionByName(champion);
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(Bot.getColor());
            champion = LeagueHandler.transposeChampionNameForDataDragon(champion);
            eb.setThumbnail(LeagueHandler.getChampionProfilePic(champion));

            eb.setTitle(":skull: Ultimate Bravery Build :skull:", "https://www.ultimate-bravery.net/Classic?s=" + seedID);
            eb.addField("**Title**", title, true);

            eb.addField("**Champion**", CustomEmojiHandler.getFormattedEmoji(champ.getName()) + " " +  champ.getName(), true);
            eb.addField("**Role**", CustomEmojiHandler.getFormattedEmoji(laneFormatName) + role, true);

            eb.addField("**First Ability**", CustomEmojiHandler.getFormattedEmoji(firstSpell.toLowerCase() + "_") + "\n​\n", true);
            eb.addField("**Summoner Spells**", CustomEmojiHandler.getFormattedEmoji(summonerSpells[0] + "_") + " " + CustomEmojiHandler.getFormattedEmoji(summonerSpells[1] + "_"), true);
            
            String buildString = "";
            for (int i = 0; i < 6; i++) {
                if (startingItems[i] != null && LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(startingItems[i])) != null) buildString += CustomEmojiHandler.getFormattedEmoji(startingItems[i]) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(startingItems[i])).getName() + "\n";
            }
            eb.addField("**Starting Items**", buildString, true);

            String runeString = "";
            for (String r : runePrimary) {
                r = r.split("\\.", 2)[0];
                runeString += CustomEmojiHandler.getFormattedEmoji(r) + " " + LeagueHandler.getRunesHandler().get(runePrimaryStyle).getRune(r).getName() + "\n";
            }
            String runeRoot = LeagueHandler.getRunesHandler().get(runePrimaryStyle).getName();
            eb.addField(CustomEmojiHandler.getFormattedEmoji(runeRoot) + " " + runeRoot, runeString, true);

            runeString = "";
            for (String r : runeSecondary) {
                r = r.split("\\.", 2)[0];
                runeString += CustomEmojiHandler.getFormattedEmoji(r) + " " + LeagueHandler.getRunesHandler().get(runeSecondaryStyle).getRune(r).getName() + "\n";
            }
            runeRoot = LeagueHandler.getRunesHandler().get(runeSecondaryStyle).getName();
            eb.addField(CustomEmojiHandler.getFormattedEmoji(runeRoot) + " " + runeRoot, runeString, true);

            buildString = "";
            for (int i = 0; i < 6; i++) {
                if (fullBuild[i] != null) buildString += CustomEmojiHandler.getFormattedEmoji(fullBuild[i]) + " " + LeagueHandler.getRiotApi().getDDragonAPI().getItem(Integer.parseInt(fullBuild[i])).getName() + "\n";
            }
            eb.addField("**Full Build**", buildString, true);
            return eb;
            
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        
    }

}
