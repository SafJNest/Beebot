package com.safjnest.Utilities.Guild.CustomCommand;

import java.io.File;
import java.util.ArrayList;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;

import com.safjnest.Utilities.Audio.PlayerManager;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class Task {
    private final int ID;
    private TaskType type;
    private ArrayList<String> values;

    public Task(int ID, TaskType type) {
        this.ID = ID;
        this.type = type;
        this.values = new ArrayList<>();
    }

    public void addValue(String value) {
        values.add(value);
    }

    public String toString() {
        return "Task{" +
                "ID=" + ID +
                ", type=" + type +
                ", values=" + values +
                '}';
    }

    public void execute(CustomCommand command, SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        User self = event.getJDA().getSelfUser();
        Member theGuy = event.getMember();

        AudioChannelUnion  voiceChannel = theGuy.getVoiceState().getChannel();

        

        switch (type) {
            case SEND_MESSAGE:
                event.getChannel().sendMessage(values.get(0)).queue();
                break;
            case DELETE_CHANNEL:
                for (String value : values) {
                    if (value.startsWith("#")) {
                        command.getOptions().forEach(option -> {
                            if (option.getId().equals(value.substring(1))) {
                                event.getOption(option.getKey()).getAsChannel().delete().queue();
                            }
                        });
                    } else {
                        guild.getGuildChannelById(value).delete().queue();
                    }
                }
                break;
            case PLAY_SOUND:
                QueryResult sound = DatabaseHandler.getSoundsById(values.get(0));
                if(sound.isEmpty())
                    return;
                

                ResultRow soundRow = sound.get(0);

                PlayerManager pm = PlayerManager.get();

                String path = "rsc" + File.separator + "SoundBoard"+ File.separator + soundRow.get("id") + "." + soundRow.get("extension");

                pm.loadItemOrdered(guild, self, path, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        pm.getGuildMusicManager(guild, self).getTrackScheduler().playForce(track);
                        guild.getAudioManager().openAudioConnection(voiceChannel);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {}
                    
                    @Override
                    public void noMatches() {}

                    @Override
                    public void loadFailed(FriendlyException throwable) {
                        System.out.println("error: " + throwable.getMessage());
                    }
                });
                break;        
            default:
                break;
        }
    }
}
