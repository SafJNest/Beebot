package com.safjnest.commands.settings.welcome;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import com.safjnest.core.cache.managers.GuilddataCache;

public class WelcomeToggle extends SlashCommand{

    public WelcomeToggle(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "toggle", "On or off.", true)
                .addChoice("on", "on")
                .addChoice("off", "off")
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        boolean toggle = event.getOption("toggle").getAsString().equalsIgnoreCase("on") ? true : false;

        String guildId = event.getGuild().getId();

        GuildData gs = GuilddataCache.getGuild(guildId);

        AlertData welcome = gs.getAlert(AlertType.WELCOME);

        if(welcome == null) {
            event.deferReply(true).addContent("This guild doesn't have a welcome message.").queue();
            return;
        }

        if(!welcome.setEnabled(toggle)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("Toggled welcome message " + (toggle ? "on" : "off") + ".").queue();
    }
}
