package com.safjnest.commands.audio;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;


public class Stop extends SlashCommand {

    public Stop(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().stop();
        event.deferReply(false).addContent("Playing stopped").queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        Guild guild = event.getGuild();
        PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().stop();
        event.reply("Playing stopped");
    }
}
