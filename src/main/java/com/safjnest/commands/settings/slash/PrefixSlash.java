package com.safjnest.commands.settings.slash;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildDataHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class PrefixSlash extends SlashCommand{

    GuildDataHandler gs;
    public PrefixSlash(GuildDataHandler gs){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.userPermissions = new Permission[]{Permission.ADMINISTRATOR};
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "prefix", "New Prefix", true)
        );

        commandData.setThings(this);

        this.gs = gs;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if(gs.getGuild(event.getGuild().getId()).setPrefix(event.getOption("prefix").getAsString()))
            event.deferReply(false).addContent("The new Prefix is " + event.getOption("prefix").getAsString()).queue();
        else
            event.deferReply(true).addContent("Error").queue();   
    }
}
