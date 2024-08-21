package com.safjnest.core.audio;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.types.EmbedType;
import com.safjnest.core.audio.types.ReplyType;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

public class QueueHandler {
    private static String formatTrack(int index, AudioTrack track) {
        //"**[" + (index + 1) + "]** " + "`-`"  + track.getInfo().title + " - " + "`" + SafJNest.formatDuration(track.getInfo().length) +  "`";
        return "`" + (index + 1) + "`" + "\u00A0\u00A0" + PermissionHandler.ellipsis(track.getInfo().title, 49);
    }

    public static String extractSoundcloudTrackId(String url) {
        Pattern pattern = Pattern.compile("\\btracks:(\\d+)\\b");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String extractBandcampTrackId(String session) {
        Pattern pattern = Pattern.compile("t\\d+");
        Matcher matcher = pattern.matcher(session);
        if (matcher.find()) {
            return matcher.group().substring(1);
        }
        return null;
    }

    public static String getSoundcloudThumbnailUrl(AudioTrack track) {
        String clientId = "SkNjMmSOqCCKdQohdskaTGJvEncaJpga";
        String trackId = extractSoundcloudTrackId(track.getIdentifier());
        String apiUrl = String.format("https://api-v2.soundcloud.com/tracks/%s?client_id=%s", trackId, clientId);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);

        if (!response.getStatusCode().is2xxSuccessful())
            return null;
        
        return new JSONObject(response.getBody()).getString("artwork_url");
    }

    public static String getBandcampThumbnailUrl(AudioTrack track) {
        String apiUrl = String.format(track.getIdentifier());

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.HEAD, null, String.class);

        if (!response.getStatusCode().is2xxSuccessful())
            return null;
        
        HttpHeaders responseHeaders = response.getHeaders();
        List<String> cookies = responseHeaders.get(HttpHeaders.SET_COOKIE);

        String sessionCookie = null;

        if (cookies != null) {
            for (String cookie : cookies) {
                java.net.HttpCookie httpCookie = java.net.HttpCookie.parse(cookie).get(0);
                if (httpCookie.getName().equals("session")) {
                    sessionCookie = cookie;
                    break;
                }
            }
        }

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        HttpEntity<String> entity = new HttpEntity<>(requestHeaders);

        response = restTemplate.exchange(apiUrl, HttpMethod.HEAD, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful())
            return null;
        
        responseHeaders = response.getHeaders();
        cookies = responseHeaders.get(HttpHeaders.SET_COOKIE);

        String session = null;

        if (cookies != null) {
            for (String cookie : cookies) {
                java.net.HttpCookie httpCookie = java.net.HttpCookie.parse(cookie).get(0);
                if (httpCookie.getName().equals("session")) {
                    session = httpCookie.getValue();
                    break;
                }
            }
        }

        String trackId = extractBandcampTrackId(session);

        apiUrl = String.format("http://bandcamp.com/api/mobile/24/tralbum_details?band_id=1&tralbum_type=t&tralbum_id=%s", trackId);

        response = restTemplate.getForEntity(apiUrl, String.class);

        String artId = String.valueOf(new JSONObject(response.getBody()).getInt("art_id"));

        String artFormat = "2"; //formats: 100:  3, 124:  8, 135:  15, 138:  12, 150:  7, 172:  11, 210:  9, 300:  4, 350:  2, 368:  14, 380:  13, 700:  5, 1200: 10, 1500: 1

        return "http://f4.bcbits.com/img/a" + artId + "_" + artFormat + ".jpg";
    }

    private static String getThumbnail(AudioTrack track) {
        String thumbnailURL = track.getUserData(TrackData.class).getThumbnailUrl();
        if(thumbnailURL != null) return thumbnailURL;
        
        thumbnailURL = track.getInfo().artworkUrl;
        if (thumbnailURL != null && !thumbnailURL.isEmpty()) return thumbnailURL;

        switch (track.getSourceManager().getSourceName()) {
            case "youtube":
                thumbnailURL = "https://img.youtube.com/vi/" + track.getIdentifier() + "/hqdefault.jpg";
                break;

            case "soundcloud":
                thumbnailURL = getSoundcloudThumbnailUrl(track);
                break;

            case "bandcamp":
                thumbnailURL = getBandcampThumbnailUrl(track);
                break;

            case "vimeo":
                thumbnailURL = "https://vumbnail.com/" + track.getIdentifier() + ".jpg";
                break;

            case "twitch":
                thumbnailURL = null;
                break;
        
            default:
                thumbnailURL = null;
                break;
        }

        track.getUserData(TrackData.class).setThumbnailUrl(thumbnailURL);

        return thumbnailURL;
    }



    public static EmbedBuilder getQueueEmbed(Guild guild) {
        return getQueueEmbed(guild, PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getIndex());
    }

    public static EmbedBuilder getQueueEmbed(Guild guild, int startIndex) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        eb.setAuthor("Queue");

        LinkedList<AudioTrack> queue = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getQueue();

        AudioTrack playingNow = null;

        int index = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getIndex();

        if(index != -1) playingNow = queue.get(index);

        if(index != -1 && index == startIndex) {
            startIndex = index + 1;
        }
        
        String queues = "";
        for(int i = startIndex, cont = 0; i < queue.size() && cont < 10 && i != index; i++, cont ++) {
            queues += formatTrack(i, queue.get(i)) + "\n";
        }

        if (playingNow != null) {
            eb.setTitle(formatTrack(index, playingNow), playingNow.getInfo().uri);
            eb.setDescription(playingNow.getInfo().author);
        }
        else {
            eb.setTitle("There is no song playing right now.");
        } 
        
        eb.addField(CustomEmojiHandler.getFormattedEmoji("playlist") + " Songs in queue ("  + (queue.size() - index - 1) + ")", queues, false);
        return eb;
    }

    public static EmbedBuilder getPlayerEmbed(Guild guild) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        eb.setAuthor("Player");

        AudioTrack playingNow = ts.getPlayer().getPlayingTrack();

        if (playingNow != null) {
            eb.setTitle("**" + playingNow.getInfo().title + "**", playingNow.getInfo().uri);
            eb.setDescription(playingNow.getInfo().author);

            eb.addField("", "", false);
            
            if(ts.isPaused()) {
                String position = SafJNest.formatDuration(playingNow.getPosition()) + " / " + SafJNest.formatDuration(playingNow.getInfo().length);
                eb.addField("Position", position, true);   
            }
            else {
                eb.addField("Length", SafJNest.formatDuration(playingNow.getInfo().length), true);
            }
            eb.addField("Queue", (ts.getIndex() + 1) + " / " + (ts.getQueue().size()), true);
            eb.setThumbnail(getThumbnail(playingNow));
        } 
        else {
            eb.setTitle("There is no song playing right now.");
        } 

        //eb.addField(RiotHandler.getFormattedEmoji("playlist") + " Songs in queue ("  + (queue.size() - index - 1) + ")", queues, false);
        return eb;
    }

    public static List<LayoutComponent> getQueueButtons(Guild guild) {
        return getQueueButtons(guild, PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getIndex());
    }
    
    public static List<LayoutComponent> getQueueButtons(Guild guild, int startIndex) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Button repeat = Button.secondary("queue-repeat", " ").withEmoji(CustomEmojiHandler.getRichEmoji("repeat"));
        Button previous = Button.primary("queue-previous" , " ").withEmoji(CustomEmojiHandler.getRichEmoji("previous"));
        Button play = Button.primary("queue-pause", " ").withEmoji(CustomEmojiHandler.getRichEmoji("pause"));
        Button next = Button.primary("queue-next", " ").withEmoji(CustomEmojiHandler.getRichEmoji("next"));
        Button shurima = Button.secondary("queue-shurima", " ").withEmoji(CustomEmojiHandler.getRichEmoji( "azir"));
        
        if(ts.isRepeat())
            repeat = repeat.withStyle(ButtonStyle.SUCCESS);
        
        if(ts.isShuffled())
            shurima = shurima.withStyle(ButtonStyle.SUCCESS);

        play = ts.isPaused() ? Button.primary("queue-play", " ").withEmoji(CustomEmojiHandler.getRichEmoji("play")).withStyle(ButtonStyle.SUCCESS) 
                             : Button.primary("queue-pause", " ").withEmoji(CustomEmojiHandler.getRichEmoji("pause"));

        buttonRows.add(ActionRow.of(
            repeat,
            previous,
            play,
            next,
            shurima
        ));

        Button previousPage = Button.secondary("queue-previouspage-", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button nextPage = Button.secondary("queue-nextpage-", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
 
        int previousIndex = startIndex - 11;
        int nextIndex = (startIndex < ts.getIndex() && (startIndex + 11) > ts.getIndex()) ? ts.getIndex() : startIndex + 11;

        if(previousIndex < 0) 
            previousIndex = 0;

        if (startIndex == 0) 
            previousPage = previousPage.asDisabled();

        if(nextIndex > ts.getQueue().size())
            nextPage = nextPage.asDisabled();

        nextPage = nextPage.withId("queue-nextpage-" + nextIndex);
        previousPage = previousPage.withId("queue-previouspage-" + previousIndex);

        Button playerButton = Button.secondary("queue-player", " ").withEmoji(CustomEmojiHandler.getRichEmoji("list")).withStyle(ButtonStyle.SUCCESS);

        buttonRows.add(ActionRow.of(
            playerButton,
            previousPage,
            Button.secondary("queue-blank1", " ").asDisabled().withEmoji(CustomEmojiHandler.getRichEmoji("blank")),
            nextPage,
            Button.secondary("queue-clear", " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin")).withStyle(ButtonStyle.DANGER)
        ));

        return buttonRows;
    }

    public static List<LayoutComponent> getPlayerButtons(Guild guild) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Button repeat = Button.secondary("player-repeat", " ").withEmoji(CustomEmojiHandler.getRichEmoji("repeat"));
        Button previous = Button.primary("player-previous" , " ").withEmoji(CustomEmojiHandler.getRichEmoji("previous"));
        Button play = Button.primary("player-pause", " ").withEmoji(CustomEmojiHandler.getRichEmoji("pause"));
        Button next = Button.primary("player-next", " ").withEmoji(CustomEmojiHandler.getRichEmoji("next"));
        Button shurima = Button.secondary("player-shurima", " ").withEmoji(CustomEmojiHandler.getRichEmoji( "shuffle"));
        
        if(ts.isRepeat())
            repeat = repeat.withStyle(ButtonStyle.SUCCESS);
        
        if(ts.isShuffled())
            shurima = shurima.withStyle(ButtonStyle.SUCCESS);

        play = ts.isPaused() ? Button.primary("player-play", " ").withEmoji(CustomEmojiHandler.getRichEmoji("play")).withStyle(ButtonStyle.SUCCESS) 
                             : Button.primary("player-pause", " ").withEmoji(CustomEmojiHandler.getRichEmoji("pause"));

        buttonRows.add(ActionRow.of(
            repeat,
            previous,
            play,
            next,
            shurima
        ));

        Button rewind = Button.secondary("player-rewind", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rewind10"));
        Button forward = Button.secondary("player-forward", " ").withEmoji(CustomEmojiHandler.getRichEmoji("fastforward30"));

        Button playerButton = Button.secondary("player-queue", " ").withEmoji(CustomEmojiHandler.getRichEmoji("list"));
        Button lyrics = Button.secondary("player-lyrics", " ").withEmoji(CustomEmojiHandler.getRichEmoji("microphone"));

        buttonRows.add(ActionRow.of(
            playerButton,
            rewind,
            Button.secondary("player-blank1", " ").asDisabled().withEmoji(CustomEmojiHandler.getRichEmoji("blank")),
            forward,
            lyrics
        ));

        return buttonRows;
    }

    public static void sendEmbed(CommandEvent event) {
        QueueMessage message = PlayerManager.get().getGuildMusicManager(event.getGuild()).getTrackScheduler().getMessage();
        EmbedType type = message != null ? message.getType() : EmbedType.PLAYER;

        sendEmbed(event, type);
    }

    public static void sendEmbed(CommandEvent event, EmbedType type) {
        Guild guild = event.getGuild();
        MessageChannel channel = event.getChannel();
        
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        ts.deleteMessage();
        
        channel.sendMessageEmbeds(getEmbed(guild, type).build()).addComponents(getButtons(guild, type)).queue(message -> {
            ts.setMessage(new QueueMessage(message, type)); 
        });
    }

    


    public static void sendEmbed(SlashCommandEvent event, EmbedType type) {
        sendEmbed(event, type, ReplyType.REPLY);
    }

    public static void sendEmbed(SlashCommandEvent event, ReplyType replyType) {
        QueueMessage message = PlayerManager.get().getGuildMusicManager(event.getGuild()).getTrackScheduler().getMessage();
        EmbedType type = message != null ? message.getType() : EmbedType.PLAYER;

        sendEmbed(event, type, replyType);
    }

    public static void sendEmbed(SlashCommandEvent event, EmbedType type, ReplyType replyType) {
        Guild guild = event.getGuild();

        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        ts.deleteMessage();

        switch (replyType) {
            case REPLY:
                event.deferReply().addEmbeds(getEmbed(guild, type).build()).setComponents(getButtons(guild, type)).queue(hook -> {
                    ts.setMessage(new QueueMessage(hook, type));
                });
                break;
            case MODIFY:
                event.getHook().editOriginalEmbeds(getEmbed(guild, type).build()).setComponents(getButtons(guild, type)).queue(hook -> {
                    ts.setMessage(new QueueMessage(hook, type));
                });
                break;
            case SEPARATED:
                event.getChannel().sendMessageEmbeds(getEmbed(guild, type).build()).setComponents(getButtons(guild, type)).queue(hook -> {
                    ts.setMessage(new QueueMessage(hook, type));
                });
                break;
            default:
                break;
        }
    }




    public static List<LayoutComponent> getButtons(Guild guild, EmbedType type) {
        switch (type) {
            case PLAYER:
                return getPlayerButtons(guild); 
            case QUEUE:
                return getQueueButtons(guild);
            default:
                throw new IllegalStateException("Unknown type");
        }
    }

    public static EmbedBuilder getEmbed(Guild guild, EmbedType type) {
        switch (type) {
            case PLAYER:
                return getPlayerEmbed(guild); 
            case QUEUE:
                return getQueueEmbed(guild);
            default:
                throw new IllegalStateException("Unknown type");
        }
    }

    public static List<LayoutComponent> getButtons(Guild guild) {
        return getButtons(guild, PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getMessage().getType());
    }
    
    public static EmbedBuilder getEmbed(Guild guild) {
        return getEmbed(guild, PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getMessage().getType());
    }


    public static MessageEmbed getPlaylistEmbed(Member author, AudioPlaylist playlist, String playlistLink) {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Queued by " + author.getEffectiveName(), author.getEffectiveAvatarUrl(), author.getEffectiveAvatarUrl());
        eb.setTitle("Playlist queued (" + playlist.getTracks().size() + " tracks):");
        eb.setDescription("[" + playlist.getName() + "](" + playlistLink + ")");
        eb.setThumbnail(getThumbnail(playlist.getTracks().get(0)));
        eb.setColor(Bot.getColor());

        return eb.build();
    }

    public static MessageEmbed getTrackEmbed(Member author, AudioTrack track) {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Queued by " + author.getEffectiveName(), "https://discord.com/users/" + author.getId(), author.getEffectiveAvatarUrl());
        eb.setTitle("Track queued:");
        eb.setDescription("[" + track.getInfo().title + "](" + track.getInfo().uri + ")");
        eb.setThumbnail(getThumbnail(track));
        eb.setColor(Bot.getColor());

        return eb.build();
    }

    

    public static MessageEmbed getSkipEmbed(Guild guild, Member author) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Skipped by " + author.getEffectiveName(), "https://discord.com/users/" + author.getId(), author.getEffectiveAvatarUrl());
        eb.setTitle("Skipped to:");
        eb.setDescription("[" + ts.getPlayer().getPlayingTrack().getInfo().title + "](" + ts.getPlayer().getPlayingTrack().getInfo().uri + ")");
        eb.setThumbnail(getThumbnail(ts.getPlayer().getPlayingTrack()));
        eb.setColor(Bot.getColor());

        return eb.build();
    }

    public static MessageEmbed getPrevEmbed(Guild guild, Member author) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();

        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Previous by " + author.getEffectiveName(), "https://discord.com/users/" + author.getId(), author.getEffectiveAvatarUrl());
        eb.setTitle("Previous to:");
        eb.setDescription("[" + ts.getPlayer().getPlayingTrack().getInfo().title + "](" + ts.getPlayer().getPlayingTrack().getInfo().uri + ")");
        eb.setThumbnail(getThumbnail(ts.getPlayer().getPlayingTrack()));
        eb.setColor(Bot.getColor());

        return eb.build();
    }

    public static EmbedBuilder getLyricsEmbed(Guild guild) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        eb.setTitle("Lyrics for " + ts.getCurrent().getInfo().title);
        eb.setDescription(ts.getLyrics(ts.getCurrent()));

        return eb;
    }
}
