package com.safjnest.commands.misc.spotify;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.type.SpotifyMessageType;
import com.safjnest.util.spotify.type.SpotifyTimeRange;

import net.dv8tion.jda.api.interactions.InteractionContextType;

public class SpotifyAlbums extends SlashCommand {

    public SpotifyAlbums(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String userId = event.getUser().getId();
        SpotifyTimeRange timeRange = SpotifyTimeRange.FULL_TERM;

        event.deferReply(false).queue();
        SpotifyMessage.send(event.getHook(), userId, SpotifyMessageType.ALBUMS, 0, timeRange);
    }

}
