package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.safjnest.Utilities.Bot.BotSettingsHandler;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.util.ArrayList;
import java.util.LinkedList;

import java.awt.Color;

public class Queueview extends Command{
    
    public Queueview() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"q"};
    }

    private String formatTrack(int index, AudioTrack track) {
        return "**[" + (index + 1) + "]** " + "`-`" + "[" + track.getInfo().title + "](" + track.getInfo().uri + ") - " + "`" + SafJNest.formatDuration(track.getInfo().length) +  "`";
    }
    
    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        User self = event.getSelfUser();

        TrackScheduler ts = PlayerManager.get().getGuildMusicManager(guild, self).getTrackScheduler();
        
        int index = ts.getIndex();
        LinkedList<AudioTrack> queue = ts.getQueue();

        if(queue.isEmpty()) {
            event.reply("```Queue is empty```");
            return;
        }


        AudioTrack playingNow = null;



        if(index != -1 ) {
            playingNow = queue.get(index);
        }

        String queues = "";
        for(int i = index+1; i < queue.size() && (queue.size() - i - 1) < 10 ; i++) {
            queues += formatTrack(i, queue.get(i)) + "\n";
        }

        EmbedBuilder eb = new EmbedBuilder();
        //eb.setAuthor(event.getAuthor().getName(), "https://github.com/SafJNest", event.getAuthor().getAvatarUrl());
        eb.setTitle(guild.getName() + " current queue");
        eb.setColor(Color.decode(BotSettingsHandler.map.get(event.getJDA().getSelfUser().getId()).color));
        eb.setThumbnail(guild.getIconUrl());

        if(playingNow != null)
            eb.addField(RiotHandler.getFormattedEmoji(event.getJDA(), "audio") + " Now playing", formatTrack(index, playingNow), false);

        eb.addField(RiotHandler.getFormattedEmoji(event.getJDA(), "playlist") + " Songs in queue ("  + (queue.size() - index - 1) + ")", queues, false);

        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Button repeat = Button.primary("queue-repeat", " ").withEmoji(RiotHandler.getRichEmoji(event.getJDA(), "repeat"));
        Button previous = Button.primary("queue-previous", " ").withEmoji(RiotHandler.getRichEmoji(event.getJDA(), "previous"));
        Button play = Button.primary("queue-pause", " ").withEmoji(RiotHandler.getRichEmoji(event.getJDA(), "pause"));
        Button next = Button.primary("queue-next", " ").withEmoji(RiotHandler.getRichEmoji(event.getJDA(), "next"));
        Button shurima = Button.primary("queue-shurima", " ").withEmoji(RiotHandler.getRichEmoji(event.getJDA(), "shuffle"));
        
        if(ts.isRepeat()) {
            repeat = repeat.withStyle(ButtonStyle.DANGER);
            repeat = repeat.asDisabled();
        }
            
        
        if(ts.isShuffled()) {
            shurima = shurima.withStyle(ButtonStyle.SUCCESS);
            shurima = shurima.asDisabled();
        }

        if(ts.getIndex() == 0) {
            previous = previous.withStyle(ButtonStyle.DANGER);
            previous = previous.asDisabled();
        }

        if(ts.getIndex() == ts.getQueue().size() - 1) {
            next = next.withStyle(ButtonStyle.DANGER);
            next = next.asDisabled();
        }

        if(ts.isPlaying()) {
            play = play.withStyle(ButtonStyle.SUCCESS);
        }


        buttonRows.add(ActionRow.of(
            repeat,
            previous,
            play,
            next,
            shurima
        ));

        event.getChannel().sendMessageEmbeds(eb.build()).addComponents(buttonRows).queue();
    }
}
