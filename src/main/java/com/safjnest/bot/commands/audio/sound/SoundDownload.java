package com.safjnest.bot.commands.audio.sound;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.sound.model.Sound;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

public class SoundDownload extends SlashCommand{

    public SoundDownload(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to download", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String lookup = event.getOption("sound").getAsString();
        String guildId = event.getGuild().getId();
        List<Sound> matches = SoundCache.findSoundMatches(lookup, guildId, event.getUser().getId());
        Sound sound = SoundCache.chooseSound(matches, guildId);
        if (sound == null) {
            event.deferReply(true).addContent("Couldn't find a sound with that name/id.").queue();
            return;
        }

        String description = "**" + sound.getName() + " (ID: " + sound.getId() + ")**";
        String message;
        if (matches.size() == 1)
            message = "Downloaded sound " + description;
        else if (guildId.equals(sound.getGuildId()))
            message = "Downloaded sound " + description + " from this guild.";
        else
            message = "Downloaded a random sound named " + description;

        event.deferReply(false).queue(hook -> Thread.startVirtualThread(
            () -> sendFile(hook, message, sound)));
    }

    /* -------------------------------------------------------------------------- */

    private void sendFile(InteractionHook hook, String message, Sound sound) {
        try {
            Path file = SoundFiles.getInstance().resolve(sound);
            hook.editOriginal(message).setFiles(FileUpload.fromData(file.toFile())).queue();
        } catch (IOException | IllegalArgumentException exception) {
            hook.editOriginal("Sound file is unavailable.").queue();
        }
    }
}
