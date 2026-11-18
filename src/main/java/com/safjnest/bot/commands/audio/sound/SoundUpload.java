package com.safjnest.bot.commands.audio.sound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 1.2.5
 */
public class SoundUpload extends SlashCommand{
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SoundUpload.class);

    public SoundUpload(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

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
        String soundName = event.getOption("name").getAsString();
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

        List<QueryRecord> sounds = BotDB.getDuplicateSoundsByName(soundName, event.getGuild().getId(), event.getMember().getId());

        if(!sounds.isEmpty()) {
            for(QueryRecord sound : sounds) {
                if(sound.get("guild_id").equals(event.getGuild().getId()))
                    event.deferReply(true).addContent("That name is already in use by you.").queue();
                if(sound.get("user_id").equals(event.getMember().getId()))
                    event.deferReply(true).addContent("That name is already in use in this guild.").queue();
            }
            return;
        }

        event.deferReply(false).queue(hook -> Thread.startVirtualThread(
            () -> upload(event, hook, soundName, attachment, isPublic)));
    }

    /* -------------------------------------------------------------------------- */

    private void upload(SlashCommandEvent event, InteractionHook hook, String name, Attachment attachment, boolean isPublic) {
        String extension = attachment.getFileExtension();
        Path staged = null;

        try {
            staged = Files.createTempFile("beebot-upload-", "." + extension);
            try (InputStream input = attachment.getProxy().download().get(30, TimeUnit.SECONDS)) {
                if (Files.copy(input, staged, StandardCopyOption.REPLACE_EXISTING) == 0)
                    throw new IOException("Empty attachment");
            }

            Path source = staged;
            String id = BotDB.get().insertSound(name, event.getGuild().getId(), event.getUser().getId(), extension, isPublic,
                soundId -> SoundFiles.getInstance().upload(soundId, extension, source));

            List<Tag> tags = new ArrayList<>();
            for (int i = 1; i <= Tag.MAX_TAG_SOUND; i++) {
                String option = "tag-" + i;
                if (event.getOption(option) == null)
                    continue;

                String tagName = event.getOption(option).getAsString();
                if (!tagName.isEmpty())
                    tags.add(new Tag(BotDB.insertTag(tagName), tagName));
            }

            if (!tags.isEmpty())
                SoundCache.withSound(id, sound -> sound.setTags(tags));

            hook.editOriginal("Sound uploaded succesfully").queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hook.editOriginal("Could not upload the sound.").queue();
        } catch (Exception exception) {
            LOG.warn("Sound upload failed", exception);
            hook.editOriginal("Could not upload the sound.").queue();
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException exception) {
                    LOG.warn("Could not remove sound upload staging file", exception);
                }
            }
        }
    }
}
