package com.safjnest.commands.misc.twitch;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import com.safjnest.core.Bot;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.twitch.TwitchClient;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;

public class TwitchUser extends SlashCommand{

    public TwitchUser(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "streamer", "Streamer's username", true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String streamerUsername = event.getOption("streamer").getAsString();
        User streamer = TwitchClient.getStreamerByName(streamerUsername);
        if(streamer.getId() == null){
            event.reply("Streamer not found").queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        eb.setAuthor(streamer.getDisplayName(), TwitchClient.getStreamerUrl(streamer.getLogin()), streamer.getProfileImageUrl());
        eb.setThumbnail(streamer.getProfileImageUrl());
        eb.setFooter("twitch.tv/" + streamer.getLogin());
        eb.setDescription(streamer.getDescription());
        
        String buttonLabel = null;

        Stream stream = TwitchClient.getStream(streamer.getId());
        if(stream == null) {
            eb.appendDescription("\n\n`⚫OFFLINE`\n");
            if(streamer.getOfflineImageUrl() != null && !streamer.getOfflineImageUrl().isBlank()) {
                eb.setImage(streamer.getOfflineImageUrl());
            }
            buttonLabel = "Visit profile";
        }
        else {
            eb.appendDescription("\n\n`🔴LIVE`\n");
            eb.appendDescription("\n" + stream.getTitle() + "\n");
            eb.setImage(stream.getThumbnailUrl(400, 225));
            eb.addField("Started", "<t:" + stream.getStartedAtInstant().getEpochSecond() + ":R>", true);
            eb.addField("Viewer count", stream.getViewerCount().toString(), true);
            buttonLabel = "Watch stream";
        }

        eb.addField("Channel created", "<t:" + streamer.getCreatedAt().getEpochSecond() + ":R>", false);

        Button streamerButtonLink = Button.link(TwitchClient.getStreamerUrl(streamer.getLogin()), buttonLabel);
        
        event.replyEmbeds(eb.build()).addComponents(ActionRow.of(streamerButtonLink)).queue();
    }
}
