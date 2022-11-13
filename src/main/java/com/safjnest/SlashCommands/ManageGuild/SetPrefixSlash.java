package com.safjnest.SlashCommands.ManageGuild;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsHandler;
import com.safjnest.Utilities.GuildData;
import com.safjnest.Utilities.GuildSettings;
import com.safjnest.Utilities.PostgreSQL;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class SetPrefixSlash extends SlashCommand{
    private PostgreSQL sql;
    
    public SetPrefixSlash(PostgreSQL sql){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsHandler().getArray(this.name, "alias");
        this.help = new CommandsHandler().getString(this.name, "help");
        this.cooldown = new CommandsHandler().getCooldown(this.name);
        this.category = new Category(new CommandsHandler().getString(this.name, "category"));
        this.arguments = new CommandsHandler().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "prefix", "New Prefix", true));
        this.sql = sql;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if(!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.deferReply(true).addContent("Only admins can change the prefix of the guild").queue();
            return;
        }
        
        String query = "INSERT INTO guild_settings(guild_id, bot_id, prefix)" + "VALUES('" + event.getGuild().getId() + "','" + event.getJDA().getSelfUser().getId()  + "','" + event.getJDA().getSelfUser().getId() + "','" + event.getOption("prefix").getAsString() +"') ON CONFLICT(guild_id, bot_id) DO UPDATE SET prefix = '" + event.getOption("prefix").getAsString() + "' RETURNING guild_id;";
        if(sql.runQuery(query))
            event.deferReply(false).addContent("New Prefix is " + event.getOption("prefix").getAsString()).queue();
        else
            event.deferReply(true).addContent("Error").queue();
        
        GuildSettings.saveData(new GuildData(event.getGuild().getIdLong(), event.getOption("prefix").getAsString()));
    }
}
