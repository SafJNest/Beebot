package com.safjnest.SlashCommands.Settings.Welcome;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SQL.DatabaseHandler;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class WelcomeMoveSlash extends SlashCommand{

    public WelcomeMoveSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(this.name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "Change welcome channel (leave out to use the guild's system channel).", false)
                            .setChannelTypes(ChannelType.TEXT));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        SQL sql = DatabaseHandler.getSql();
        String channel = null;
        if(event.getOption("channel") == null){
            try {
                channel = event.getGuild().getSystemChannel().getId();
            } catch (Exception e) {
                event.deferReply(true).addContent("There isn't a system channel in this guild (check your guild settings).").queue();
                return;
            }
        }else{
            channel = event.getOption("channel").getAsChannel().getId();
        }

        String query = "SELECT channel_id FROM welcome_message WHERE guild_id = '" + event.getGuild().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        String id = sql.getString(query, "channel_id");

        if(id == null){
            event.deferReply(false).addContent("St a welcome message first.").queue();
            return;
        }

        if(id.equals(channel)){
            event.deferReply(false).addContent("This channel is already the welcome channel.").queue();
            return;
        }

        query = "UPDATE welcome_message SET channel_id = '" + channel + "' WHERE guild_id = '" + event.getGuild().getId() + "' AND bot_id = '" + event.getJDA().getSelfUser().getId() + "';";
        sql.runQuery(query);
        event.deferReply(false).addContent("Changed welcome channel.").queue();
    }
    
}
