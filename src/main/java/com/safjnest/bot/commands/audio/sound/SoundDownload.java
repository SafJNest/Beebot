package com.safjnest.bot.commands.audio.sound;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
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
        String fileName = event.getOption("sound").getAsString();
        String message = null;

        List<QueryRecord> sounds = fileName.matches("\\d+")
                           ? BotDB.getSoundsById(fileName, event.getGuild().getId(), event.getMember().getId()) 
                           : BotDB.getSoundsByName(fileName, event.getGuild().getId(), event.getMember().getId());

        if(sounds.isEmpty()) {
            event.deferReply(true).addContent("Couldn't find a sound with that name/id.");
            return;
        }

        QueryRecord toDownload = null;

        if(sounds.size() == 1) {
            toDownload = sounds.get(0);
            message = "Downloaded sound **" + toDownload.get("name") + " (ID: " + toDownload.get("id") + ")**";
        }
        else {
            for(QueryRecord sound : sounds) {
                if(sound.get("guild_id").equals(event.getGuild().getId())) {
                    toDownload = sound;
                    message = "Downloaded sound **" + toDownload.get("name") + " (ID: " + toDownload.get("id") + ")** from this guild.";
                }
            }
        }
        if(toDownload == null) {
            toDownload = sounds.get((int)(Math.random() * sounds.size()));
            message = "Downloaded a random sound named **" + toDownload.get("name") + " (ID: " + toDownload.get("id") + ")** ";
        }

        String id = toDownload.get("id");
        String extension = toDownload.get("extension");
        String response = message;

        event.deferReply(false).queue(hook -> Thread.startVirtualThread(() -> sendFile(hook, response, id, extension)));
    }

    /* -------------------------------------------------------------------------- */

    private void sendFile(InteractionHook hook, String message, String id, String extension) {
        try {
            Path file = SoundFiles.getInstance().resolve(id, extension);
            hook.editOriginal(message).setFiles(FileUpload.fromData(file.toFile())).queue();
        } catch (IOException | IllegalArgumentException exception) {
            hook.editOriginal("Sound file is unavailable.").queue();
        }
    }
}
