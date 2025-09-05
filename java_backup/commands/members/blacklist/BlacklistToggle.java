package com.safjnest.commands.members.blacklist;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class BlacklistToggle extends SlashCommand{

    public BlacklistToggle(String father){
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

        if(GuildCache.getGuildOrPut(event.getGuild().getId()).getBlackChannelId() == null) {
            event.deferReply(true).addContent("This guild doesn't have blacklist set.").queue();
            return;
        }

        if(!GuildCache.getGuildOrPut(event.getGuild().getId()).setBlacklistEnabled(toggle)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("Toggled blacklist " + (toggle ? "on" : "off") + ".").queue();
    }
}