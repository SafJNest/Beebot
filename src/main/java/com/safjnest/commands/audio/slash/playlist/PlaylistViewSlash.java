package com.safjnest.commands.audio.slash.playlist;

import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PlaylistViewSlash extends SlashCommand{
    private final int pageSize = 10;
    public PlaylistViewSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "playlist-name", "Name of the custom playlist", true)
        );

        commandData.setThings(this);
    }

    public EmbedBuilder getTracksEmbed(ResultRow playlist, Member member, int page) {
        QueryResult tracks = DatabaseHandler.getPlaylistTracks(playlist.getAsInt("id"), pageSize, page);
        List<AudioTrack> decodedTracks = PlayerManager.get().decodeTracks(tracks.arrayColumn("encoded_track"));

        EmbedBuilder eb = new EmbedBuilder();

        eb.setColor(Bot.getColor());
        eb.setAuthor(member.getNickname(), member.getEffectiveAvatarUrl(), member.getEffectiveAvatarUrl());
        eb.setTitle(playlist.get("name") + " (" + playlist.get("size") + " tracks)");

        if(decodedTracks.isEmpty()) {
            eb.setDescription("No tracks in the playlist.");
        } else {
            int i = 1;
            for(AudioTrack track : decodedTracks) {
                eb.appendDescription("`" + i + "` - [" + track.getInfo().title + "](" + track.getInfo().uri + ")" + "\n");
                i++;
            }
        }

        return eb;
    }


    @Override
    protected void execute(SlashCommandEvent event) {
        event.deferReply(true).queue();

        int playlistId;
        try {
            playlistId = event.getOption("playlist-name").getAsInt();
        } catch (Exception e) {
            event.getHook().editOriginal("What the fock are you doing.").queue();
            return;
        }

        Member member = event.getMember();

        ResultRow playlist = DatabaseHandler.getPlaylistByIdWithSize(playlistId);

        if(playlist.isEmpty() || (!playlist.get("user_id").equals(member.getId()) && !PermissionHandler.isUntouchable(member.getId())) ) {
            event.getHook().editOriginal("Playlist not found.").queue();
            return;
        }

        event.getHook().editOriginalEmbeds(getTracksEmbed(playlist, member, 0).build()).queue();
    }
}
