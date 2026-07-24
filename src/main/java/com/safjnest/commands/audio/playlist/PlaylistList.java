package com.safjnest.commands.audio.playlist;

import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;


public class PlaylistList extends SlashCommand{
    public PlaylistList(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Member member = event.getMember();

        List<QueryRecord> playlists = BotDB.getPlaylists(member.getId());

        EmbedBuilder eb = new EmbedBuilder();

        eb.setColor(Bot.getColor());
        eb.setAuthor(member.getNickname(), member.getEffectiveAvatarUrl(), member.getEffectiveAvatarUrl());
        eb.setTitle("Your playlists");

        if(playlists.isEmpty()) {
            eb.setDescription("No playlists found.");
        } else {
            int i = 1;
            for(QueryRecord playlist : playlists) {
                String playlistName = playlist.get("name");
                eb.appendDescription(i + " - " + playlistName + "\n");
                i++;
            }
        }

        event.replyEmbeds(eb.build()).setEphemeral(false).queue();
    }
}
