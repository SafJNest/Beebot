package com.safjnest.commands.misc.spotify;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.SettingsLoader;

import net.dv8tion.jda.api.interactions.InteractionContextType;

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
        String[] scopes = {"user-top-read", "user-read-recently-played"};

        String userId = event.getUser().getId();
        String randomString = String.valueOf(System.currentTimeMillis());
        String state = userId + ":" + randomString;

        String link = "https://accounts.spotify.com/authorize" + 
                        "?client_id=" + clientId + 
                        "&response_type=code" + 
                        "&redirect_uri=" + redirectUri + 
                        "&scope=" + String.join("+", scopes) + 
                        "&state=" + state; 

        event.deferReply(false).setEphemeral(true).setContent("To link your Spotify account, please visit [this link](" + link + ").")
            .queue();
    }
}
