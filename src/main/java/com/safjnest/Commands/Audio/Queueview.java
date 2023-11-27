package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.TableHandler;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.safjnest.Utilities.LOL.RiotHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import java.util.ArrayList;
import java.util.LinkedList;

public class Queueview extends Command{
    
    public Queueview() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"q"};
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

        String[][] data = new String[queue.size()][3];

        int i = 0;
        for(AudioTrack track : queue) {
            data[i][0] = (i + 1) + "";
            if(i == index) {
                data[i][0] = "-> " + data[i][0];
            }      
            System.out.println("i: " + i + " index: " + index);      
            data[i][1] = track.getInfo().title;                     
            data[i][2] = SafJNest.getFormattedDuration(track.getInfo().length);
            i++;
        }

        String[] headers = new String[]{"Position", "Title", "Duration"};
        
        TableHandler.replaceIdsWithNames(data, event.getJDA());
        String table = TableHandler.constructTable(data, headers);


        event.reply(event.getGuild().getName() + " current queue:");

        java.util.List<LayoutComponent> rows = new ArrayList<>();

        /*
         * rows.add(ActionRow.of(
            Button.primary("queue-repeat", ":repeat_one:"),
            Button.primary("queue-previous", ":track_previous:"),
            Button.primary("queue-stop", ":arrow_forward:"),
            Button.primary("queue-next", ":track_next:"),
            Button.primary("queue-shurima", ":twisted_rightwards_arrows:")
        ));
         */
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


        rows.add(ActionRow.of(
            repeat,
            previous,
            play,
            next,
            shurima
        ));

        event.getChannel().sendMessage("```" + table + "```").addComponents(rows).queue();
    }
}
