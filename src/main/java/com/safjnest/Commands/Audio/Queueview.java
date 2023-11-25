package com.safjnest.Commands.Audio;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.Utilities.SafJNest;
import com.safjnest.Utilities.TableHandler;
import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.Audio.PlayerPool;
import com.safjnest.Utilities.Audio.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;


import net.dv8tion.jda.api.entities.Guild;


import java.util.LinkedList;

public class Queueview extends Command{
    
    public Queueview() {
        this.name = this.getClass().getSimpleName().toLowerCase();
        this.aliases = new String[]{"q"};
    }

  
    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();

        PlayerManager pm = PlayerPool.contains(event.getSelfUser().getId(), guild.getId()) ? PlayerPool.get(event.getSelfUser().getId(), guild.getId()) : PlayerPool.createPlayer(event.getSelfUser().getId(), guild.getId());
        TrackScheduler ts = pm.getTrackScheduler();
        
        int index = ts.getIndex();

        LinkedList<AudioTrack> queue = ts.getQueue();

        if(queue.isEmpty()) {
            event.reply("```Queue is empty```");
            return;
        }

        /*
         * 0: position
         * 1: title
         * 2: duration
         */
        String[][] data = new String[queue.size()][3];

        int i = 0;
        for(AudioTrack track : queue) {
            data[i][0] = (i + 1) + "";

            if(i == index) {
                data[i][0] = "-> " + data[i][0];
            }     
            
            
            
            data[i][1] = track.getInfo().title;
                        
            data[i][2] = SafJNest.getFormattedDuration(track.getInfo().length);
            i++;
        }

        String[] headers = new String[]{"Position", "Title", "Duration"};
        
        TableHandler.replaceIdsWithNames(data, event.getJDA());
        String table = TableHandler.constructTable(data, headers);


        String[] splitTable = TableHandler.splitTable(table);

        event.reply(event.getGuild().getName() + " current queue:");
        for(i = 0; i < splitTable.length; i++)
            event.reply("```" + splitTable[i] + "```");
    }
}
