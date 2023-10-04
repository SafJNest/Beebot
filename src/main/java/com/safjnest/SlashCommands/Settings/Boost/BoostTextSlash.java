package com.safjnest.SlashCommands.Settings.Boost;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SQL.DatabaseHandler;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BoostTextSlash extends SlashCommand {

    public BoostTextSlash(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "msg", "Boost message", true),
            new OptionData(OptionType.CHANNEL, "channel", "Boost channel (leave out to use the guild's system channel).", false)
                .setChannelTypes(ChannelType.TEXT)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String channel = null;
        if (event.getOption("channel") == null) {
            try {
                channel = event.getGuild().getSystemChannel().getId();
            } catch (Exception e) {
                event.deferReply(true).addContent("There isn't a system channel in this guild (check your guild settings).").queue();
                return;
            }
        } else {
            channel = event.getOption("channel").getAsChannel().getId();
        }

        String message = event.getOption("msg").getAsString();
        message = message.replace("'", "''");

        String discordId = event.getGuild().getId();
        String query = "INSERT INTO boost_message(guild_id, channel_id, message_text, bot_id)"
                + "VALUES('" + discordId + "','" + channel + "','" + message + "','"
                + event.getJDA().getSelfUser().getId() + "');";
        DatabaseHandler.getSql().runQuery(query);
        event.deferReply(false).addContent("Boost message set").queue();
    }

}