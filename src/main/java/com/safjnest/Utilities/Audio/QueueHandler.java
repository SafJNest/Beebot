package com.safjnest.Utilities.Audio;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Bot;
import com.safjnest.Utilities.PermissionHandler;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
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

    public static EmbedBuilder getEmbed(Guild guild) {
        return getEmbed(guild, PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().getIndex());
    }

    public static EmbedBuilder getEmbed(Guild guild, int startIndex) {
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
        
        if(ts.isRepeat()) {
            repeat = repeat.withStyle(ButtonStyle.DANGER);
        }
            
        
        if(ts.isShuffled()) {
            shurima = shurima.withStyle(ButtonStyle.DANGER);
        }

        if(!ts.isPaused()) {
            play = Button.primary("queue-pause", " ").withEmoji(RiotHandler.getRichEmoji("pause"));
        } else {
            play = Button.primary("queue-play", " ").withEmoji(RiotHandler.getRichEmoji("play"));
        }


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

        if(currentIndex > ts.getQueue().size()) {
            nextPage = nextPage.asDisabled();
        }

        if(previousIndex < 0) {
            previousPage = previousPage.asDisabled();
        }

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

    public static void sendQueueEmbed(Guild guild, MessageChannel channel) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        ts.deleteMessage();
        
        channel.sendMessageEmbeds(getEmbed(guild, ts.getIndex()).build()).addComponents(getQueueButtons(guild)).queue(message -> {
            ts.setMessage(new QueueMessage(message));
        });
    }

    public static void sendQueueEmbed(SlashCommandEvent event, Guild guild) {
        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler();
        ts.deleteMessage();
        
        event.deferReply().addEmbeds(getEmbed(guild, ts.getIndex()).build()).setComponents(getQueueButtons(guild)).queue(thresh -> {
            ts.setMessage(new QueueMessage(thresh));
        });
    }
}
