package com.safjnest.SlashCommands.ManageGuild;

import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.SQL;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;

/**
 * @author <a href="https://github.com/NeuntronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class SetWelcomeSlash extends SlashCommand {
    private SQL sql;

    public SetWelcomeSlash(SQL sql) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        //this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "msg", "Welcome message", true),
            new OptionData(OptionType.CHANNEL, "channel", "User to get the information about", false),
            new OptionData(OptionType.ROLE, "role", "User to get the information about", false));
        this.sql = sql;
    }
    @Override
    protected void execute(SlashCommandEvent event) {
        String channel = null;
        String message = event.getOption("msg").getAsString();
        
        String discordId = event.getGuild().getId();
        String query = "INSERT INTO welcome_message(discord_id, channel_id, message_text)"
                            + "VALUES('" + discordId + "','" + channel +"','" + message + "');";
        sql.runQuery(query);
        query = "INSERT INTO welcome_roles(role_id, discord_id)"
                            + "VALUES('" + event.getOption("role").getAsRole().getId() + "','" + discordId +"');";
        sql.runQuery(query);
        
        event.deferReply(true).addContent("All set correctly").queue();
    }
}