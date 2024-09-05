package com.safjnest.commands.audio.playlist;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

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

        QueryResult playlists = DatabaseHandler.getPlaylists(member.getId());

        EmbedBuilder eb = new EmbedBuilder();

        eb.setColor(Bot.getColor());
        eb.setAuthor(member.getNickname(), member.getEffectiveAvatarUrl(), member.getEffectiveAvatarUrl());
        eb.setTitle("Your playlists");

        if(playlists.isEmpty()) {
            eb.setDescription("No playlists found.");
        } else {
            int i = 1;
            for(String playlistName : playlists.arrayColumn("name")) {
                eb.appendDescription(i + " - " + playlistName + "\n");
                i++;
            }
        }

        event.replyEmbeds(eb.build()).setEphemeral(false).queue();
    }
}
