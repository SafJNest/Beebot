package com.safjnest.commands.lol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.lol.LeagueHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class Item extends SlashCommand {

    public Item(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "item", "Item name", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        int itemId = event.getOption("item").getAsInt();

        no.stelar7.api.r4j.pojo.lol.staticdata.item.Item item = LeagueHandler.getRiotApi().getDDragonAPI().getItem(itemId);

        EmbedBuilder eb = new EmbedBuilder();

        eb.setColor(Bot.getColor());
        eb.setTitle(item.getName());
        String url = "http://ddragon.leagueoflegends.com/cdn/" + LeagueHandler.getVersion() +"/img/item/" + item.getId() + ".png";
        eb.setThumbnail(url);

        eb.addField("Gold Cost", CustomEmojiHandler.getFormattedEmoji("golds") + " " + String.valueOf(item.getGold().getTotal()), true);
        eb.addField("Sell Value", CustomEmojiHandler.getFormattedEmoji("golds") + " " + String.valueOf(item.getGold().getSell()), true);
        eb.addField("Consumable", item.isConsumed() ? "Yes" : "No", true);

        String statisticsTxt = buildStatisticsDescription(getStats(item.getDescription()));
        if (!statisticsTxt.isEmpty()) {
            eb.addField("Statistics", statisticsTxt, false);
        }
        
        String activeTxt = "";  
        HashMap<String, String> actives = getActive(item.getDescription());
        for (String key : actives.keySet()) {
            if (!actives.get(key).isBlank()) activeTxt += "**" + key + "**: " + sanitize(actives.get(key)) + "\n";
        }
        if (!activeTxt.isEmpty()) {
            eb.addField("Actives", activeTxt, false);
        }

        String passiveTxt = "";
        HashMap<String, String> passives = getPassive(item.getDescription());
        for (String key : passives.keySet()) {
            passiveTxt += "**" + key + "**: " + sanitize(passives.get(key)) + "\n";
        }
        if (!passiveTxt.isEmpty()) {
            eb.addField("Passives", passiveTxt, false);
        }

        event.replyEmbeds(eb.build()).queue();

	}

    private static String buildStatisticsDescription(HashMap<String, String> statistics) {
        String description = "";

        List<String> keys = new ArrayList<>(statistics.keySet());

        for (String key : keys) {
            switch (key.toLowerCase().replace(" ", "")) {
                case "health":
                    description += CustomEmojiHandler.getFormattedEmoji("health") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "healthregeneration":
                    description += CustomEmojiHandler.getFormattedEmoji("healthregeneration") + " " + statistics.get(key) + " " + key + "\n";
                    break;

                case "attackdamage":
                    description += CustomEmojiHandler.getFormattedEmoji("attackdamage") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "abilitypower":
                    description += CustomEmojiHandler.getFormattedEmoji("abilitypower") + " " + statistics.get(key) + " " + key + "\n";
                    break;

                case "mana":
                    description += CustomEmojiHandler.getFormattedEmoji("mana") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "manaregeneration":
                    description += CustomEmojiHandler.getFormattedEmoji("manaregeneration") + " " + statistics.get(key) + " " + key + "\n";
                    break;

                case "attackspeed":
                    description += CustomEmojiHandler.getFormattedEmoji("attackspeed") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "criticalstrikedamage":
                case "criticalstrikechance":
                    description += CustomEmojiHandler.getFormattedEmoji("criticalstrikechance") + " " + statistics.get(key) + " " + key + "\n";
                    break;

                case "armor":
                    description += CustomEmojiHandler.getFormattedEmoji("armor") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "magicresist":
                    description += CustomEmojiHandler.getFormattedEmoji("magicresist") + " " + statistics.get(key) + " " + key + "\n";
                    break;

                case "lifesteal":
                    description += CustomEmojiHandler.getFormattedEmoji("lifeSteal") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "spellvamp":
                    description += CustomEmojiHandler.getFormattedEmoji("spellvamp") + " " + statistics.get(key) + " " + key + "\n";
                    break;

                case "tenacity":
                    description += CustomEmojiHandler.getFormattedEmoji("tenacity") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "movespeed":
                case "movementspeed":
                    description += CustomEmojiHandler.getFormattedEmoji("movementspeed") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "cooldownreduction":
                case "abilityhaste":
                    description += CustomEmojiHandler.getFormattedEmoji("abilityhaste") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "armorpenetration":
                    description += CustomEmojiHandler.getFormattedEmoji("armorpenetration") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                case "magicpenetration":
                    description += CustomEmojiHandler.getFormattedEmoji("magicpenetration") + " " + statistics.get(key) + " " + key + "\n";
                    break;
                default:
                    description += key + " " + statistics.get(key) + "\n";
                    break;
            }
        }






        return description;
    }

    public static HashMap<String, String> getStats(String mainText) {
        HashMap<String, String> stats = new HashMap<>();
        // Extract the content within the <stats> tag first
        Pattern statsPattern = Pattern.compile("<stats>(.*?)</stats>", Pattern.DOTALL);
        Matcher statsMatcher = statsPattern.matcher(mainText);
        String statsContent = "";
        if (statsMatcher.find()) {
            statsContent = statsMatcher.group(1);
        }
    
        // Adjusted pattern to ensure it captures the last stat before the </stats> tag, even without a <br> tag
        Pattern pattern = Pattern.compile("<attention>(.*?)</attention>\\s*(.*?)(<br>|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(statsContent);
    
        while (matcher.find()) {
            String statValue = matcher.group(1).trim();
            String statName = matcher.group(2).trim();
            stats.put(statName, statValue);
        }
    
        return stats;
    }

    public static HashMap<String, String> getPassive(String mainText) {
        HashMap<String, String> passives = new HashMap<>();
        Pattern pattern = Pattern.compile("<passive>(.*?)</passive><br>(.*?)(?=<passive>|</mainText>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(mainText);
    
        while (matcher.find()) {
            String passiveName = matcher.group(1).trim();
            String passiveDescription = matcher.group(2).trim();
            passiveDescription = passiveDescription.replaceAll("<br>$", "").trim();
            passives.put(passiveName, passiveDescription);
        }
    
        return passives;
    }

    public static HashMap<String, String> getActive(String mainText) {
        HashMap<String, String> passives = new HashMap<>();
        // Corrected pattern to match passive abilities if needed or keep as is for active abilities
        Pattern pattern = Pattern.compile("<active>(.*?)</active>(.*?)(?=<active>|<rules>|</mainText>)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(mainText);
    
        while (matcher.find()) {
            String abilityName = matcher.group(1).trim();
            String abilityDescription = matcher.group(2).trim();
            abilityDescription = abilityDescription.replaceAll("^<br>|<br>$", "").trim();
    
            // Check directly after the current match for a <rules> tag without advancing the matcher
            int endOfMatch = matcher.end();
            if (mainText.substring(endOfMatch).startsWith("<rules>")) {
                // Find the end of the <rules> section
                int startOfNextActive = mainText.indexOf("<active>", endOfMatch);
                int endOfRules = startOfNextActive != -1 ? startOfNextActive : mainText.indexOf("</mainText>", endOfMatch);
                if (endOfRules == -1) endOfRules = mainText.length(); // In case </mainText> is missing
                String rulesText = mainText.substring(endOfMatch, endOfRules).trim();
                rulesText = rulesText.replaceAll("^<rules>|</rules>|^<br>|<br>$", "").trim();
                abilityDescription += " " + rulesText;
            }
    
            passives.put(abilityName, abilityDescription);
        }
    
        return passives;
    }


    private static String sanitize(String string) {
        return string.replaceAll("<.+?>", "");
    }
}