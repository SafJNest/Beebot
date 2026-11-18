package com.safjnest.bot.commands.audio.sound;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.sound.model.Sound;
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
        Attachment attachment = event.getOption("file").getAsAttachment();
        String name;
        String format;

        try {
            name = Sound.normalizeName(event.getOption("name").getAsString());
            format = SoundFiles.uploadExtension(attachment.getFileName());
        } catch (com.safjnest.exception.Exception failure) {
            event.deferReply(true).addContent(failure.getMessage()).queue();
            return;
        }

        Set<String> tags = new LinkedHashSet<>();
        for (int i = 1; i <= Tag.MAX_TAG_SOUND; i++) {
            String option = "tag-" + i;
            if (event.getOption(option) == null)
                continue;

            String tag = event.getOption(option).getAsString().trim().toLowerCase(Locale.ROOT);
            if (tag.length() > 255) {
                event.deferReply(true).addContent("Invalid sound tag.").queue();
                return;
            }
            if (!tag.isEmpty())
                tags.add(tag);
        }

        boolean isPublic = event.getOption("public") == null || event.getOption("public").getAsBoolean();
        List<String> tagNames = List.copyOf(tags);

        event.deferReply(false).queue(hook -> Thread.startVirtualThread(
            () -> upload(event, hook, name, attachment, format, isPublic, tagNames)));
    }

    /* -------------------------------------------------------------------------- */

    private void upload(SlashCommandEvent event, InteractionHook hook, String name, Attachment attachment,
            String extension, boolean isPublic, List<String> tags) {
        try (InputStream input = attachment.getProxy().download().get(30, TimeUnit.SECONDS)) {
            SoundFiles.getInstance().create(BotDB.get(), name, event.getGuild().getId(), event.getUser().getId(), extension,
                isPublic, tags, input);

            hook.editOriginal("Sound uploaded succesfully").queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hook.editOriginal("Could not upload the sound.").queue();
        } catch (com.safjnest.exception.Exception exception) {
            if ("duplicate_sound_name".equals(exception.getCode())) {
                hook.editOriginal("That sound name is already in use.").queue();
                return;
            }

            LOG.warn("Sound upload failed", exception);
            hook.editOriginal("Could not upload the sound.").queue();
        } catch (Exception exception) {
            LOG.warn("Sound upload failed", exception);
            hook.editOriginal("Could not upload the sound.").queue();
        }
    }
}
