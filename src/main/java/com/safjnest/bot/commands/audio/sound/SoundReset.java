package com.safjnest.bot.commands.audio.sound;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.AudioEffects.Effect;
import com.safjnest.bot.audio.PlayerManager;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;

public class SoundReset extends SlashCommand {
    public SoundReset(String father) {
        BotCommand commandData = CommandsLoader.getCommand(father).getChild("reset");

        this.name = commandData.getName();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Use this command in a server.").setEphemeral(true).queue();
            return;
        }

        PlayerManager.get().getGuildMusicManager(guild).setSoundEffect(Effect.NONE);

        event.reply("Sound effects reset.").setEphemeral(true).queue();
    }
}
