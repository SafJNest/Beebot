package com.safjnest.commands.audio.search;

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
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.core.audio.ResultHandler;
import com.safjnest.core.audio.types.PlayTiming;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeClientConfig;
import com.sedmelluq.discord.lavaplayer.tools.DataFormatTools;
import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

/**
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 8.0
 */
public class SearchYoutube extends SlashCommand {
    private final HttpInterfaceManager httpInterfaceManager = HttpClientTools.createCookielessThreadLocalManager();

    public SearchYoutube(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "query", "Video name", true)
        );

        commandData.setThings(this);
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

    private EmbedBuilder getSearchEmbed(Member author, String query, List<AudioTrackInfo> searchResultList) {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Search by" + author.getEffectiveName(), "https://discord.com/users/" + author.getId(), author.getEffectiveAvatarUrl());
        eb.setTitle("Search for: " + query);

        for(int i = 0; i < searchResultList.size(); i++) {
            eb.appendDescription("`" + (i+1) + "` " + searchResultList.get(i).title + "\n");
        }

        eb.setColor(Bot.getColor());

        return eb;
    }

    private StringSelectMenu.Builder getMenu(Member author, List<AudioTrackInfo> searchResultList) {
        String menuId = "menu:" + author.getId() + "-" + SafJNest.getJSalt(4);
        StringSelectMenu.Builder mb = StringSelectMenu.create(menuId);

        for(int i = 0; i < searchResultList.size(); i++) {
            String label = (i+1) + " - " + searchResultList.get(i).title;
            label = PermissionHandler.ellipsis(label, 100);
            mb.addOption(label, searchResultList.get(i).identifier);
        }
        
        return mb;
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String query = event.getOption("query").getAsString();
        List<AudioTrackInfo> searchResultList = search(query);
        StringSelectMenu.Builder menu = getMenu(event.getMember(), searchResultList);
        
        MenuListener fileListener = new MenuListener(event, menu.getId());
        event.getJDA().addEventListener(fileListener);

        event.replyEmbeds(getSearchEmbed(event.getMember(), query, searchResultList).build())
             .addComponents(ActionRow.of(menu.build())).queue();
    }    
    private class MenuListener extends ListenerAdapter {
    
        private SlashCommandEvent slashEvent;
        private String menuID;
    
        public MenuListener(SlashCommandEvent slashEvent, String menuID) {
            this.slashEvent = slashEvent;
            this.menuID = menuID;
        }
    
        @Override
        public void onStringSelectInteraction(StringSelectInteractionEvent event) {
            if (event.getComponentId().equals(menuID)) {
                Guild guild = event.getGuild();
            
                AudioChannel myChannel = event.getMember().getVoiceState().getChannel();
                AudioChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
    
                if(myChannel == null){
                    event.deferReply(true).addContent("You need to be in a voice channel to use this command.").queue();
                    return;
                }
        
                if(botChannel != null && (myChannel != botChannel)){
                    event.deferReply(true).addContent("The bot is already being used in another voice channel.").queue();
                    return;
                }
    
                List<String> selected = event.getValues();
                
                event.deferEdit().queue();
                
                PlayerManager pm = PlayerManager.get();
    
                event.getHook().editOriginalComponents().queue();
    
                pm.loadItemOrdered(guild, selected.get(0),
                        new ResultHandler(slashEvent, false, selected.get(0), PlayTiming.LAST, ReplyType.MODIFY));
                
                event.getJDA().removeEventListener(this);
            }
        }
    }
}
