package com.safjnest.commands.Audio.slash;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;


public class StopSlash extends SlashCommand {

    public StopSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        PlayerManager.get().getGuildMusicManager(guild).getTrackScheduler().stop();
        event.deferReply(false).addContent("Playing stopped").queue();
    }
}
