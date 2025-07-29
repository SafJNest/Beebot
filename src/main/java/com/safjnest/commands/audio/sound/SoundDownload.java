package com.safjnest.commands.audio.sound;

import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryCollection;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;

public class SoundDownload extends SlashCommand{
    String path = "rsc" + File.separator + "sounds" + File.separator;

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

        QueryCollection sounds = fileName.matches("\\d+") 
                           ? DatabaseHandler.getSoundsById(fileName, event.getGuild().getId(), event.getMember().getId()) 
                           : DatabaseHandler.getSoundsByName(fileName, event.getGuild().getId(), event.getMember().getId());

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

        fileName = path + toDownload.get("id") + "." + toDownload.get("extension");

        event.deferReply(false).addContent(message)
            .setFiles(FileUpload.fromData(new File(fileName)))
            .queue();
    }
}