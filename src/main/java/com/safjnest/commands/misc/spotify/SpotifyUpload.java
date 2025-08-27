package com.safjnest.commands.misc.spotify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.spotify.SpotifyTrackStreaming;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.spotify.SpotifyHandler;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SpotifyUpload extends SlashCommand {

    public SpotifyUpload(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.contexts = new InteractionContextType[]{InteractionContextType.GUILD, InteractionContextType.BOT_DM};

        this.options = new ArrayList<>(Arrays.asList(
            new OptionData(OptionType.ATTACHMENT, "zip", "Send the .zip file", true)
        ));

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if (event.getOption("zip") == null) {
            event.reply("You must provide a .zip file to upload.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue();
        event.getOption("zip").getAsAttachment().getProxy().download()
            .thenAccept(data -> {
              try {
                List<SpotifyTrackStreaming> streamings = SpotifyHandler.readStreamsInfoFromZip(data);
                SpotifyHandler.uploadStreamingsToDB(streamings, event.getUser().getId());

                event.getHook().editOriginal("done!").queue();
              } catch (IOException e) { }
              
            })
            .exceptionally(ex -> {
                ex.printStackTrace();
                event.reply("An error occurred while processing your request.").setEphemeral(true).queue();
                return null;
            });
    }

}
