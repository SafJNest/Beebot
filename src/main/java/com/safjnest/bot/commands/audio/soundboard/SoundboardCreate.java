package com.safjnest.bot.commands.audio.soundboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundEmbed;
import com.safjnest.bot.audio.SoundboardThumbnails;
import com.safjnest.bot.audio.SoundboardThumbnails.Image;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.sound.model.Sound;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class SoundboardCreate extends SlashCommand{
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SoundboardCreate.class);

    public SoundboardCreate(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = new ArrayList<>();

        this.options.add(new OptionData(OptionType.STRING, "name", "Leave blank to not save the soundboard.", false));
        this.options.add(new OptionData(OptionType.ATTACHMENT, "thumbnail", "Thumbnail for the soundboard.", false));

        int soundOptions = Math.min(BotDB.MAX_SOUNDBOARD_SOUNDS, CommandData.MAX_OPTIONS - this.options.size());
        for (int i = 1; i <= soundOptions; i++)
            this.options.add(new OptionData(OptionType.STRING, "sound-" + i, "Sound " + i, false).setAutoComplete(true));
        
        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Set<String> soundIds = new HashSet<>();
        for (OptionMapping option : event.getOptions()) {
            if (option.getName().startsWith("sound-"))
                soundIds.add(option.getAsString());
        }

        if (soundIds.isEmpty()) {
            event.deferReply(true).addContent("You need to insert at least a sound.").queue();
            return;
        }

        String[] ids = soundIds.toArray(String[]::new);
        OptionMapping nameOption = event.getOption("name");
        if (nameOption == null) {
            List<Sound> sounds = SoundCache.getSoundsByIds(ids);
            SoundEmbed.composeSoundboard(event, sounds).queue();
            return;
        }

        String name = nameOption.getAsString();
        String guildId = event.getGuild().getId();
        if (BotDB.soundboardExists(name, guildId)) {
            event.deferReply(true).addContent("A soundboard with that name in this guild already exists.").queue();
            return;
        }

        OptionMapping thumbnailOption = event.getOption("thumbnail");
        Attachment attachment = thumbnailOption == null ? null : thumbnailOption.getAsAttachment();
        String userId = event.getUser().getId();

        event.deferReply().queue(hook -> Thread.startVirtualThread(
            () -> create(hook, name, guildId, userId, attachment, ids)));
    }

    /* -------------------------------------------------------------------------- */

    private void create(InteractionHook hook, String name, String guildId, String userId, Attachment attachment, String[] soundIds) {
        try {
            Function<String, String> upload = null;
            if (attachment != null) {
                Image image = SoundboardThumbnails.download(attachment);
                SoundboardThumbnails thumbnails = SoundboardThumbnails.configured();
                upload = id -> thumbnails.upload(id, image);
            }

            String id = BotDB.get().insertSoundBoard(name, guildId, userId, upload, soundIds);
            List<Sound> sounds = SoundCache.getSoundsByIds(soundIds);
            SoundEmbed.showCreatedSoundboard(hook, id, sounds);
        } catch (IllegalArgumentException exception) {
            hook.editOriginal("Invalid thumbnail.").queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hook.editOriginal("Could not download the thumbnail.").queue();
        } catch (IOException exception) {
            hook.editOriginal("Could not download the thumbnail.").queue();
        } catch (RuntimeException exception) {
            LOG.warn("Could not create soundboard in guild {}", guildId, exception);
            hook.editOriginal("Could not create the soundboard.").queue();
        }
    }
}
