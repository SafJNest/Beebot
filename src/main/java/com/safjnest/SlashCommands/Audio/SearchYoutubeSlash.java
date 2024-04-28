package com.safjnest.SlashCommands.Audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Bot;
import com.safjnest.Utilities.CommandsLoader;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeClientConfig;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 8.0
 */
public class SearchYoutubeSlash extends SlashCommand {
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();
    private final int resultNumber = 5;

    public SearchYoutubeSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "query", "Video name", true)
        );
    }

    public List<AudioTrackInfo> search(String query) {
        try {
            HttpInterface httpInterface = this.httpInterfaceManager.getInterface();

            String responseText;
            List<AudioTrackInfo> resultList;
            try {
                HttpPost post = new HttpPost(
                        "https://youtubei.googleapis.com/youtubei/v1/search?key=AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w");
                YoutubeClientConfig clientConfig = YoutubeClientConfig.ANDROID.copy().withRootField("query", query)
                        .withRootField("params", "EgIQAUICCAE=").setAttribute(httpInterface);
                StringEntity payload = new StringEntity(clientConfig.toJsonString(), "UTF-8");
                post.setEntity(payload);
                System.out.println(post.getEntity());
                CloseableHttpResponse response = httpInterface.execute(post);

                try {
                    HttpClientTools.assertSuccessWithContent(response, "search response");
                    responseText = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    JsonBrowser jsonBrowser = JsonBrowser.parse(responseText);
                    resultList = this.extractSearchPage(jsonBrowser);
                } catch (Throwable var2) {
                    if (response != null) {
                        try {
                            response.close();
                        } catch (Throwable var1) {
                            var2.addSuppressed(var1);
                        }
                    }

                    throw var2;
                }

                if (response != null) {
                    response.close();
                }
            } catch (Throwable var3) {
                if (httpInterface != null) {
                    try {
                        httpInterface.close();
                    } catch (Throwable var) {
                        var3.addSuppressed(var);
                    }
                }

                throw var3;
            }

            if (httpInterface != null) {
                httpInterface.close();
            }

            return resultList;
        } catch (Exception var4) {
            throw ExceptionTools.wrapUnfriendlyExceptions(var4);
        }
    }

    private List<AudioTrackInfo> extractSearchPage(JsonBrowser jsonBrowser) throws IOException {
        ArrayList<AudioTrackInfo> list = new ArrayList<AudioTrackInfo>();
        jsonBrowser.get("contents").get("sectionListRenderer").get("contents").values().forEach((content) -> {
            content.get("itemSectionRenderer").get("contents").values().forEach((jsonTrack) -> {
                AudioTrackInfo track = this.extractPolymerData(jsonTrack);
                if (track != null) {
                    list.add(track);
                }

            });
        });
        return list;
    }

    private AudioTrackInfo extractPolymerData(JsonBrowser json) {
        json = json.get("compactVideoRenderer");
        if (json.isNull()) {
            return null;
        } else {
            String title = json.get("title").get("runs").index(0).get("text").text();
            String author = json.get("longBylineText").get("runs").index(0).get("text").text();
            if (json.get("lengthText").isNull()) {
                return null;
            } else {
                long duration = DataFormatTools
                        .durationTextToMillis(json.get("lengthText").get("runs").index(0).get("text").text());
                String videoId = json.get("videoId").text();
                AudioTrackInfo info = new AudioTrackInfo(title, author, duration, videoId, false,
                        "https://www.youtube.com/watch?v=" + videoId);
                return info;
            }
        }
    }

    private EmbedBuilder getSearchEmbed(Member author, String query) {
        //String[] searchResult = new String[resultNumber];
        

        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Search by" + author.getEffectiveName(), "https://discord.com/users/" + author.getId(), author.getEffectiveAvatarUrl());
        eb.setTitle("Search for: " + query);
        //eb.setDescription();
        //eb.setThumbnail();
        eb.setColor(Bot.getColor());

        return eb;
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String query = event.getOption("query").toString();
        StringBuilder searchResult = new StringBuilder();
        search(query).forEach(trackInfo -> {
            searchResult.append(trackInfo.title).append(" ");
        });
        String finalResult = searchResult.toString();

        event.replyFiles(FileUpload.fromData(
                finalResult.getBytes(StandardCharsets.UTF_8),
                "search.txt")).queue();

    }    
}