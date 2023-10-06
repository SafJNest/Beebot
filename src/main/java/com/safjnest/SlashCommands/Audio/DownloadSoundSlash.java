package com.safjnest.SlashCommands.Audio;

import java.io.File;
import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;



public class DownloadSoundSlash extends SlashCommand{
    String path = "rsc" + File.separator + "SoundBoard" + File.separator;

    public DownloadSoundSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();
        this.aliases = new CommandsLoader().getArray(this.name, "alias");
        this.help = new CommandsLoader().getString(this.name, "help");
        this.cooldown = new CommandsLoader().getCooldown(this.name);
        this.category = new Category(new CommandsLoader().getString(this.name, "category"));
        this.arguments = new CommandsLoader().getString(this.name, "arguments");
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "sound", "Sound to download", true)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String fileName = event.getOption("sound").getAsString();

        QueryResult sounds = fileName.matches("[0123456789]*") 
                           ? DatabaseHandler.getSoundsById(fileName, event.getGuild().getId(), event.getMember().getId()) 
                           : DatabaseHandler.getSoundsByName(fileName, event.getGuild().getId(), event.getMember().getId());

        if(sounds.isEmpty()){
            event.reply("Couldn't find a sound with that name/id.");
            return;
        }

        ResultRow toDownload = null;
        for(ResultRow sound : sounds) {
            if(sound.get("guild_id") == event.getGuild().getId()) {
                toDownload = sound;
            }
        }
        
        if(toDownload == null){
            toDownload = sounds.get((int)(Math.random() * sounds.size()));

            fileName = path + toDownload.get("id") + "." + toDownload.get("extension");

            event.deferReply(false).addContent("Couldn't find a sound with this name on your guild so i downloaded a random sound with this name.")
                .setFiles(FileUpload.fromData(new File(fileName)))
                .queue();
        }
        else{
            fileName = path + toDownload.get("id") + "." + toDownload.get("extension");
            event.deferReply(false).addContent("Downloading the sound with this name from your guild.")
                .setFiles(FileUpload.fromData(new File(fileName)))
                .queue();
        }
    }
}