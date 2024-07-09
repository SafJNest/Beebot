package com.safjnest.commands.Audio.slash;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.model.sound.Tag;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class UploadSlash extends SlashCommand{
    private String soundName;
    
    public UploadSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "name", "Sound name", true),
            new OptionData(OptionType.ATTACHMENT, "file", "Sound file (mp3 or opus)", true),
            new OptionData(OptionType.BOOLEAN, "public", "true or false", false),
            new OptionData(OptionType.STRING, "tag-1", "Tag", false),
            new OptionData(OptionType.STRING, "tag-2", "Tag", false),
            new OptionData(OptionType.STRING, "tag-3", "Tag", false),
            new OptionData(OptionType.STRING, "tag-4", "Tag", false),
            new OptionData(OptionType.STRING, "tag-5", "Tag", false)
        );

        commandData.setThings(this);
    }
    
	@Override
	protected void execute(SlashCommandEvent event) {
        soundName = event.getOption("name").getAsString();
        Attachment attachment = event.getOption("file").getAsAttachment();

        boolean isPublic;
        if(event.getOption("public") != null)
            isPublic = event.getOption("public").getAsBoolean();
        else
            isPublic = true;
        
        if(soundName.matches("[0123456789]*")){
            event.reply("You can't use a name that only contains numbers.");
            return;
        }

        if(!attachment.getFileExtension().equals("mp3") && !attachment.getFileExtension().equals("opus")){
            event.deferReply(true).addContent("Only upload the sound in **mp3** or **opus** format.").queue();
            return;
        }

        QueryResult sounds = DatabaseHandler.getDuplicateSoundsByName(soundName, event.getGuild().getId(), event.getMember().getId());

        if(!sounds.isEmpty()) {
            for(ResultRow sound : sounds) {
                if(sound.get("guild_id").equals(event.getGuild().getId()))
                    event.deferReply(true).addContent("That name is already in use by you.").queue();
                if(sound.get("user_id").equals(event.getMember().getId()))
                    event.deferReply(true).addContent("That name is already in use in this guild.").queue();
            }
            return;
        }

        String id = DatabaseHandler.insertSound(soundName, event.getGuild().getId(), event.getMember().getId(), attachment.getFileExtension(), isPublic);

        if(id == null){
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        List<Tag> tags = new ArrayList<>();
        for(int i = 1; i <= Tag.MAX_TAG_SOUND; i++){
            if(event.getOption("tag-" + i) != null){
                String tagName = event.getOption("tag-" + i) != null ? event.getOption("tag-" + i).getAsString() : "";
                if(tagName.length() > 0){
                    int tagId = DatabaseHandler.insertTag(tagName);
                    tags.add(new Tag(tagId, tagName));
                }
            }
        }
        if (tags.size() > 0) SoundHandler.getSoundById(id).setTags(tags.toArray(new Tag[tags.size()]));

        File saveFile = new File("rsc" + File.separator + "SoundBoard" + File.separator + (id + "." + attachment.getFileExtension()));

        attachment.getProxy().downloadToFile(saveFile);
        event.deferReply(false).addContent("Sound uploaded succesfully").queue();
	}
}