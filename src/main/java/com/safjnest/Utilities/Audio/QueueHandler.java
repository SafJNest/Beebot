package com.safjnest.Utilities.Audio;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Bot;
import com.safjnest.Utilities.PermissionHandler;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.LOL.RiotHandler;
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

    public static EmbedBuilder getQueueEmbed(Guild guild) {
        return getQueueEmbed(guild, PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getIndex());
    }

    public static EmbedBuilder getQueueEmbed(Guild guild, int startIndex) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.decode(Bot.getColor()));
        eb.setAuthor("Queue");

        LinkedList<AudioTrack> queue = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getQueue();

        AudioTrack playingNow = null;

        int index = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getIndex();

        if(index != -1 && index == startIndex) {
            playingNow = queue.get(index);
            startIndex = index + 1;
        }
        
        String queues = "";
        for(int i = startIndex, cont = 0; i < queue.size() && cont < 10 && i != index; i++, cont ++) {
            queues += formatTrack(i, queue.get(i)) + "\n";
        }

        if (playingNow != null) {
            eb.setTitle(formatTrack(index, playingNow));
            eb.setDescription(playingNow.getInfo().author);
        } 
        else {
            eb.setTitle("There is no song playing right now.");
        } 
        
        eb.addField(RiotHandler.getFormattedEmoji("playlist") + " Songs in queue ("  + (queue.size() - index - 1) + ")", queues, false);
        return eb;
    }

    public static List<LayoutComponent> getQueueButtons(Guild guild) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        int currentIndex = ts.getIndex();

        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Button repeat = Button.secondary("queue-repeat", " ").withEmoji(RiotHandler.getRichEmoji("repeat"));
        Button previous = Button.primary("queue-previous" , " ").withEmoji(RiotHandler.getRichEmoji("previous"));
        Button play = Button.primary("queue-pause", " ").withEmoji(RiotHandler.getRichEmoji("pause"));
        Button next = Button.primary("queue-next", " ").withEmoji(RiotHandler.getRichEmoji("next"));
        Button shurima = Button.secondary("queue-shurima", " ").withEmoji(RiotHandler.getRichEmoji( "shuffle"));
        
        if(ts.isRepeat())
            repeat = repeat.withStyle(ButtonStyle.DANGER);
        
        if(ts.isShuffled())
            shurima = shurima.withStyle(ButtonStyle.DANGER);

        play = ts.isPaused() ? Button.primary("queue-play", " ").withEmoji(RiotHandler.getRichEmoji("play")) 
                             : Button.primary("queue-pause", " ").withEmoji(RiotHandler.getRichEmoji("pause"));

        buttonRows.add(ActionRow.of(
            repeat,
            previous,
            play,
            next,
            shurima
        ));

        Button previousPage = Button.secondary("queue-previouspage-", " ").withEmoji(RiotHandler.getRichEmoji("leftarrow"));
        Button nextPage = Button.secondary("queue-nextpage-", " ").withEmoji(RiotHandler.getRichEmoji("rightarrow"));
 
        int previousIndex = ts.getIndex() - 11;
        if(previousIndex < 0) 
            previousIndex = 0;

        int nextIndex = ts.getIndex() + 11;
        if(nextIndex > ts.getQueue().size())
            nextIndex = ts.getQueue().size() - 1;

        if(currentIndex > ts.getQueue().size())
            nextPage = nextPage.asDisabled();

        if(previousIndex < 0)
            previousPage = previousPage.asDisabled();

        nextPage = nextPage.withId("queue-nextpage-" + nextIndex);
        previousPage = previousPage.withId("queue-previouspage-" + previousIndex);

        buttonRows.add(ActionRow.of(
            Button.secondary("queue-blank", " ").asDisabled().withEmoji(RiotHandler.getRichEmoji("blank")),
            previousPage,
            Button.secondary("queue-blank1", " ").asDisabled().withEmoji(RiotHandler.getRichEmoji("blank")),
            nextPage,
            Button.secondary("queue-clear", " ").withEmoji(RiotHandler.getRichEmoji("bin"))
        ));

        return buttonRows;
    }

    public static void sendQueueEmbed(CommandEvent event) {
        Guild guild = event.getGuild();
        MessageChannel channel = event.getChannel();
        
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        ts.deleteMessage();
        
        channel.sendMessageEmbeds(getQueueEmbed(guild, ts.getIndex()).build()).addComponents(getQueueButtons(guild)).queue(message -> {
            ts.setMessage(new QueueMessage(message));
        });
    }

    public static void sendQueueEmbed(SlashCommandEvent event, boolean sendSeparate) {
        Guild guild = event.getGuild();

        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        ts.deleteMessage();

        if(sendSeparate) {
            event.getChannel().sendMessageEmbeds(getQueueEmbed(guild, ts.getIndex()).build()).setComponents(getQueueButtons(guild)).queue(thresh -> {
                ts.setMessage(new QueueMessage(thresh));
            });
        }
        else {
            event.deferReply().addEmbeds(getQueueEmbed(guild, ts.getIndex()).build()).setComponents(getQueueButtons(guild)).queue(thresh -> {
                ts.setMessage(new QueueMessage(thresh));
            });
        }
    }

    public static void sendQueueEmbed(SlashCommandEvent event) {
        sendQueueEmbed(event, false);
    }


    public static MessageEmbed getPlaylistEmbed(Member author, AudioPlaylist playlist, String playlistLink) {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Queued by " + author.getEffectiveName(), author.getEffectiveAvatarUrl(), author.getEffectiveAvatarUrl());
        eb.setTitle("Playlist queued (" + playlist.getTracks().size() + " tracks):");
        eb.setDescription("[" + playlist.getName() + "](" + playlistLink + ")");
        eb.setThumbnail("https://img.youtube.com/vi/" + playlist.getTracks().get(0).getIdentifier() + "/hqdefault.jpg");
        eb.setColor(Color.decode(Bot.getColor()));

        return eb.build();
    }

    public static MessageEmbed getTrackEmbed(Member author, AudioTrack track) {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Queued by " + author.getEffectiveName(), "https://discord.com/users/" + author.getId(), author.getEffectiveAvatarUrl());
        eb.setTitle("Track queued:");
        eb.setDescription("[" + track.getInfo().title + "](" + track.getInfo().uri + ")");
        eb.addField("Lenght", SafJNest.getFormattedDuration(track.getInfo().length) , true);
        eb.setThumbnail("https://img.youtube.com/vi/" + track.getIdentifier() + "/hqdefault.jpg");
        eb.setColor(Color.decode(Bot.getColor()));

        return eb.build();
    }

    public static MessageEmbed getSkipEmbed(CommandEvent event) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(event.getGuild()).getTrackScheduler();

        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Skipped by " + event.getMember().getEffectiveName(), "https://discord.com/users/" + event.getMember().getId(), event.getMember().getEffectiveAvatarUrl());
        eb.setTitle("Skipped Song:");
        eb.setDescription("[" + ts.getPlayer().getPlayingTrack().getInfo().title + "](" + ts.getPlayer().getPlayingTrack().getInfo().uri + ")");
        eb.setThumbnail("https://img.youtube.com/vi/" + ts.getPlayer().getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
        eb.setColor(Color.decode(Bot.getColor()));

        return eb.build();
    }

    public static MessageEmbed getSkipEmbed(SlashCommandEvent event) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(event.getGuild()).getTrackScheduler();

        EmbedBuilder eb = new EmbedBuilder();

        eb.setAuthor("Skipped by " + event.getMember().getEffectiveName(), "https://discord.com/users/" + event.getMember().getId(), event.getMember().getEffectiveAvatarUrl());
        eb.setTitle("Skipped Song:");
        eb.setDescription("[" + ts.getPlayer().getPlayingTrack().getInfo().title + "](" + ts.getPlayer().getPlayingTrack().getInfo().uri + ")");
        eb.setThumbnail("https://img.youtube.com/vi/" + ts.getPlayer().getPlayingTrack().getIdentifier() + "/hqdefault.jpg");
        eb.setColor(Color.decode(Bot.getColor()));

        return eb.build();
    }
}
