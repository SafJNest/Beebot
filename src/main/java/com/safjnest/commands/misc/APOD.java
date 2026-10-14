package com.safjnest.commands.misc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.dv8tion.jda.api.utils.FileUpload;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.SettingsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class APOD extends SlashCommand {
    private String nasaApiKey;

    public APOD() {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "date", "The date of the APOD (YYYY-MM-DD).", false)
        );

        commandData.setThings(this);

        this.nasaApiKey = SettingsLoader.getSettings().getJsonSettings().getNasaApiKey();
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(true).setEphemeral(false).queue();
        JSONObject jsonResponse = null;
        int responseCode = 0;
        try {
            String urlString = "https://science.nasa.gov/wp-json/wp/v2/apod-basic?api_key=" + nasaApiKey;
            if(event.getOption("date") != null) {
                urlString += "&date=" + URLEncoder.encode(event.getOption("date").getAsString(), "UTF-8");
            }

            URL url = new URI(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            InputStream inputStream;
            if ((responseCode = connection.getResponseCode()) == 200)
                inputStream = connection.getInputStream();
            else
                inputStream = connection.getErrorStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            JSONParser jsonParser = new JSONParser();
            Object parsedObj = jsonParser.parse(response.toString());
            
            if (parsedObj instanceof org.json.simple.JSONArray) {
                org.json.simple.JSONArray jsonArray = (org.json.simple.JSONArray) parsedObj;
                if (!jsonArray.isEmpty()) {
                    jsonResponse = (JSONObject) jsonArray.get(0);
                } else {
                    event.getHook().editOriginal("No APOD data found.").queue();
                    return;
                }
            } else if (parsedObj instanceof JSONObject) {
                jsonResponse = (JSONObject) parsedObj;
            }

        } catch (IOException | org.json.simple.parser.ParseException | URISyntaxException e) {
            e.printStackTrace();
            event.getHook().editOriginal("Something went wrong.").queue();
            return;
        }

        if(responseCode != 200 || jsonResponse == null) {
            String errorMessage = "Error " + responseCode + ": Unable to fetch APOD data.";
            if (jsonResponse != null && jsonResponse.containsKey("message")) {
                errorMessage = "Error: " + jsonResponse.get("message").toString();
            }
            event.getHook().editOriginal(errorMessage).queue();
            return;
        }

        String type = jsonResponse.get("media_type") != null ? jsonResponse.get("media_type").toString() : "image";
        
        String title = jsonResponse.get("title") != null ? jsonResponse.get("title").toString() : "Astronomy Picture of the Day";
        
        String rawExplanation = "";
        if (jsonResponse.get("explanation") != null) {
            rawExplanation = jsonResponse.get("explanation").toString();
        } else if (jsonResponse.containsKey("content")) {
            Object contentObj = jsonResponse.get("content");
            if (contentObj instanceof JSONObject) {
                JSONObject contentJson = (JSONObject) contentObj;
                if (contentJson.get("rendered") != null) {
                    rawExplanation = contentJson.get("rendered").toString();
                }
            }
        }

        String explanation = cleanHtml(rawExplanation);
        if (explanation.isEmpty()) {
            explanation = "No explanation provided.";
        }

        String date = jsonResponse.get("date") != null ? jsonResponse.get("date").toString() : "";
        
        String apodUrl = jsonResponse.get("permalink") != null 
            ? jsonResponse.get("permalink").toString() 
            : (!date.isEmpty() ? "https://apod.nasa.gov/apod/ap" + date.replace("-", "").substring(2) + ".html" : "https://science.nasa.gov/apod");
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor("Astronomy Picture of the Day");
        eb.setTitle(title, apodUrl);
        eb.setDescription(explanation);
        eb.setColor(Bot.getColor());

        FileUpload imageUpload = null;
        String mediaUrl = "";
        if (jsonResponse.get("hdurl") != null) {
            mediaUrl = jsonResponse.get("hdurl").toString();
        } else if (jsonResponse.get("url") != null) {
            mediaUrl = jsonResponse.get("url").toString();
        }

        if(type.equals("image") && !mediaUrl.isEmpty() && mediaUrl.startsWith("http")) {
            try {
                InputStream imageStream = new URI(mediaUrl).toURL().openStream();
                byte[] imageBytes = imageStream.readAllBytes();
                imageUpload = FileUpload.fromData(imageBytes, "apod.jpg");
                eb.setImage("attachment://apod.jpg");
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
                eb.setImage(mediaUrl);
            }
        } else if(type.equals("video")) {
            eb.appendDescription("\n\n**The apod is a video, click the link in the title to watch it**");
            Pattern pattern = Pattern.compile("(?:https://)?(?:www\\.)?youtube\\.com/embed/([A-Za-z0-9_-]+)\\?");
            Matcher matcher = pattern.matcher(mediaUrl);
            if(matcher.find()) {
                eb.setImage("https://img.youtube.com/vi/" + matcher.group(1) + "/hqdefault.jpg");
            }
        }
        
        if (!date.isEmpty()) {
            eb.setFooter("Date: " + date);
        }

        if (imageUpload != null) {
            event.getHook().sendMessageEmbeds(eb.build()).addFiles(imageUpload).queue();
        } else {
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        }
    }

    private String cleanHtml(String html) {
        if (html == null) return "";

        Pattern linkPattern = Pattern.compile("<a\\s+(?:[^>]*?\\s+)?href=\"([^\"]*)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = linkPattern.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String href = matcher.group(1);
            String text = matcher.group(2).replaceAll("<[^>]*>", ""); // remove inner tags if any
            matcher.appendReplacement(sb, "[" + text + "](" + href + ")");
        }
        matcher.appendTail(sb);
        html = sb.toString();

        String text = html.replaceAll("<[^>]*>", "");

        if (text.startsWith("Explanation:")) {
            text = text.substring("Explanation:".length()).trim();
        }

        text = text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#8217;", "'")
                   .replace("&#8220;", "\"")
                   .replace("&#8221;", "\"")
                   .replace("&nbsp;", " ");
        
        return text.trim();
    }
}