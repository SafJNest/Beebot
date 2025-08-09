package com.safjnest.commands.misc.spotify;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.spotify.SpotifyMessage;
import com.safjnest.util.spotify.SpotifyMessageType;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SpotifyLink extends SlashCommand{
    public SpotifyLink(String father) {
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
        String clientId = SettingsLoader.getSettings().getJsonSettings().getSpotifyApi().getClientId();
        //String redirectUri = "http://127.0.0.1:3001/spotify/callback";
        String redirectUri = "https://safjnest.com/spotify/callback";
        String scope = "user-top-read";

        String userId = event.getUser().getId();
        String randomString = String.valueOf(System.currentTimeMillis());
        String state = userId + ":" + randomString;

        String link = "https://accounts.spotify.com/authorize?client_id=" + clientId + 
                        "&response_type=code&redirect_uri=" + redirectUri + 
                        "&scope=" + scope + 
                        "&state=" + state;

        event.deferReply(false).setEphemeral(true).setContent("To link your Spotify account, please visit [this link](" + link + ").")
            .queue();
    }
}
