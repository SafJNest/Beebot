package com.safjnest.commands.Misc.slash.twitch;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.CommandsLoader;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;

import java.util.List;
import java.util.ArrayList;

import com.safjnest.util.Twitch.*;

public class TwitchMenuSlash extends SlashCommand{

    public static List<LayoutComponent> getTwitchButtons(String guildId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();
        List<Button> buttons = new ArrayList<>();

        QueryResult subs = DatabaseHandler.getTwitchSubscriptionsGuild(guildId);
        List<com.github.twitch4j.helix.domain.User> streamers = TwitchClient.getStreamersById(subs.arrayColumn("streamer_id"));
        for (com.github.twitch4j.helix.domain.User streamer : streamers) {
            Button subButton = Button.primary("twitch-streamerId-" + streamer.getId(), streamer.getDisplayName());
            buttons.add(subButton);
        }

        Button addSubButton = Button.primary("twitch-addSub", "+");
        buttons.add(addSubButton);

        ActionRow row = ActionRow.of(buttons);

        buttonRows.add(row);

        return buttonRows;
    }

    public static List<LayoutComponent> getTwitchStreamerButtons(String streamedId) {
        java.util.List<LayoutComponent> buttonRows = new ArrayList<>();

        Button goBack = Button.primary("twitch-back-" + streamedId, " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        Button changeMessage = Button.primary("twitch-changeMessage-" + streamedId, "message").withEmoji(CustomEmojiHandler.getRichEmoji("message"));
        Button changeChannel = Button.primary("twitch-changeChannel-" + streamedId, "channel").withEmoji(CustomEmojiHandler.getRichEmoji("hashtag"));
        Button delete = Button.danger("twitch-delete-"+ streamedId, " ").withEmoji(CustomEmojiHandler.getRichEmoji("bin"));

        buttonRows.add(ActionRow.of(goBack, changeMessage, changeChannel, delete));

        return buttonRows;
    }

    public static EmbedBuilder getTwitchEmbed() {
        EmbedBuilder eb = new EmbedBuilder();

        eb.setTitle("Twitch menu");
        eb.setDescription("From here you can add new subs to your server or modify existing ones.\n" +
            "On the message you can write #streamer to get the name of the streamer and @role");
        eb.setThumbnail("https://static-00.iconduck.com/assets.00/twitch-icon-512x512-ws2eyit3.png");
        eb.setColor(Bot.getColor());

        return eb;
    }

    public static EmbedBuilder getTwitchStreamerEmbed(String StreamerId, String guildId) {
        ResultRow sub = DatabaseHandler.getTwitchSubscriptionsGuild(StreamerId, guildId);
        com.github.twitch4j.helix.domain.User streamer = TwitchClient.getStreamerById(StreamerId);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        eb.setTitle(streamer.getDisplayName(), "https://twitch.tv/" + streamer.getLogin());
        eb.setThumbnail(streamer.getProfileImageUrl());
        eb.setDescription(sub.get("message"));
        eb.addField("Channel", "https://discord.com/channels/" + guildId + "/" + sub.get("channel_id"), false);
        return eb;
    }

    public TwitchMenuSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.replyEmbeds(getTwitchEmbed().build()).setComponents(getTwitchButtons(event.getGuild().getId())).queue();
    }
}
