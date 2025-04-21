package com.safjnest.commands.misc.twitch;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.twitch.*;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import com.safjnest.core.Bot;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.model.guild.alert.TwitchData;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class TwitchMenu extends SlashCommand{

    public static List<LayoutComponent> getTwitchButtons(String guildId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();

        List<String> subs = new ArrayList<>();
        for (TwitchData twitch : GuildCache.getGuildOrPut(guildId).getTwitchDatas().values()) {
            subs.add(twitch.getStreamer());
        }
        List<com.github.twitch4j.helix.domain.User> streamers = TwitchClient.getStreamersById(subs);
        Collections.sort(streamers, Comparator.comparing(com.github.twitch4j.helix.domain.User::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        //if there are more than 24 streamers, 25 and up just dont show up :/ | todo: premium = streamcord + 1
        for (com.github.twitch4j.helix.domain.User streamer : streamers) {
            Button subButton = Button.primary("twitch-streamerId-" + streamer.getId(), streamer.getDisplayName());
            buttons.add(subButton);
            if(buttonRows.size() == 4 && buttons.size() == 4) {
                System.out.println("Someone has 24 or more streamers, its time to do pages :)");
                break;
            }
            else if(buttons.size() == 5) {
                buttonRows.add(ActionRow.of(buttons));
                buttons.clear();
            }
        }

        Button addSubButton = Button.success("twitch-addSub", "+");
        buttons.add(addSubButton);

        buttonRows.add(ActionRow.of(buttons));

        return buttonRows;
    }

    public static List<LayoutComponent> getTwitchStreamerButtons(String streamedId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Button goBack = Button.primary("twitch-back-" + streamedId, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button changeMessage = Button.primary("twitch-changeMessage-" + streamedId, "message").withEmoji(CustomEmojiHandler.getRichEmoji("message"));
        Button changeChannel = Button.primary("twitch-changeChannel-" + streamedId, "channel").withEmoji(CustomEmojiHandler.getRichEmoji("hashtag"));
        Button changeRole = Button.primary("twitch-changeRole-" + streamedId, "role").withEmoji(CustomEmojiHandler.getRichEmoji("user"));
        Button delete = Button.danger("twitch-delete-"+ streamedId, " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));

        buttonRows.add(ActionRow.of(goBack, changeMessage, changeChannel, changeRole, delete));

        return buttonRows;
    }

    public static EmbedBuilder getTwitchEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle("Twitch menu");
        eb.setDescription("Here you can **add** new subs to your server or **modify** existing ones.");

        eb.setFooter("In the message you can write #streamer to get the name of the streamer.\n" +
                          "Because of discord limitations you cant ping a role or a channel directly " +
                          "from the form, so for channels you can right click -> copy link and paste " +
                          "that in the form, but if you want to ping a role in the message i would " +
                          "suggest using the twitch link command (specifiyng an already linked streamer " + 
                          "in the link command will update the message/channel).");

        eb.setThumbnail("https://static-00.iconduck.com/assets.00/twitch-icon-512x512-ws2eyit3.png");
        eb.setColor(Bot.getColor());

        return eb;
    }

    public static EmbedBuilder getTwitchStreamerEmbed(String StreamerId, String guildId) {
        TwitchData twitch = GuildCache.getGuildOrPut(guildId).getTwitchdata(StreamerId);
        com.github.twitch4j.helix.domain.User streamer = TwitchClient.getStreamerById(StreamerId);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        eb.setTitle(streamer.getDisplayName(), "https://twitch.tv/" + streamer.getLogin());
        eb.setThumbnail(streamer.getProfileImageUrl());
        eb.setDescription("Here you can **add** new subs to your server or **modify** existing ones.");

        if (!twitch.getMessage().isBlank() && twitch.hasPrivateMessage()) {
            eb.addField("Message", twitch.getMessage(), false);
            eb.addField("Private Message", twitch.getPrivateMessage(), false);
        } else {
            eb.addField("Message", twitch.getMessage(), false);
        }
        eb.addField("Channel", "https://discord.com/channels/" + guildId + "/" + twitch.getChannelId(), true);
        eb.addField("Role", twitch.getStreamerRole() == null ? "None" : "<@&" + twitch.getStreamerRole() + ">", true);

        eb.setFooter("In the message you can write #streamer to get the name of the streamer.\n" +
                     "Because of discord limitations you cant ping a role or a channel directly " +
                     "from the form, so for channels you can right click -> copy link and paste " +
                     "that in the form, but if you want to ping a role in the message i would " +
                     "suggest using the twitch link command (specifiyng an already linked streamer " + 
                     "in the link command will update the message/channel).");
        
        return eb;
    }

    public TwitchMenu(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.replyEmbeds(getTwitchEmbed().build()).setComponents(getTwitchButtons(event.getGuild().getId())).queue();
    }
}
