package com.safjnest.bot.commands.audio.sound;

import java.util.Locale;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.AudioEffects.Effect;
import com.safjnest.bot.audio.PlayerManager;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.entities.Guild;

public class SoundEffect extends SlashCommand {
    public SoundEffect(String father) {
        BotCommand commandData = CommandsLoader.getCommand(father).getChild("effect");

        this.name = commandData.getName();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.options = commandData.getOptions();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Use this command in a server.").setEphemeral(true).queue();
            return;
        }

        Effect effect = Effect.valueOf(event.getOption("preset").getAsString());
        PlayerManager.get().getGuildMusicManager(guild).setSoundEffect(effect);

        String name = effect.name().toLowerCase(Locale.ROOT);
        event.reply("Sound effect set to " + name + ".").setEphemeral(true).queue();
    }
}
