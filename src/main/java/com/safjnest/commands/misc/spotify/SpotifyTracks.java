package com.safjnest.commands.misc.spotify;

import java.io.IOException;
import java.util.List;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.spotify.SpotifyMessage;
import net.dv8tion.jda.api.interactions.InteractionContextType;

public class SpotifyTracks extends SlashCommand{
    public SpotifyTracks(String father) {
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
        try {
            event.deferReply(false).queue();
            event.getHook()
                    .editOriginalComponents(
                            List.of(SpotifyMessage.getMainContent(event.getMember().getId(), "tracks", 0),
                                    SpotifyMessage.getButtonComponents("tracks")))
                    .useComponentsV2()
                    .queue();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
