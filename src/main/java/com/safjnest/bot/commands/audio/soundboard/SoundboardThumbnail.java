package com.safjnest.bot.commands.audio.soundboard;

import java.io.IOException;
import java.util.ArrayList;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundboardThumbnails;
import com.safjnest.bot.audio.SoundboardThumbnails.Image;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;
import com.safjnest.utils.ValidationUtils;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 3.0
 */
public class SoundboardThumbnail extends SlashCommand{

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SoundboardThumbnail.class);

    public SoundboardThumbnail(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = new ArrayList<>();
        this.options.add(new OptionData(OptionType.STRING, "soundboard_name", "Soundboard to add the sound(s) to.", true).setAutoComplete(true));
        this.options.add(new OptionData(OptionType.ATTACHMENT, "thumbnail", "Thumbnail for the soundboard.", true));

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String id = event.getOption("soundboard_name").getAsString();
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();

        if (!ValidationUtils.isDecimalId(id) || !BotDB.soundboardExists(id, guildId, userId)) {
            event.deferReply(true).addContent("Soundboard does not exist or you dont have permission to play the selected one.").queue();
            return;
        }

        Attachment attachment = event.getOption("thumbnail").getAsAttachment();
        event.deferReply(false).queue(hook -> Thread.startVirtualThread(
            () -> update(hook, id, guildId, userId, attachment)));
    }

    /* -------------------------------------------------------------------------- */

    private void update(InteractionHook hook, String id, String guildId, String userId, Attachment attachment) {
        try {
            Image image = SoundboardThumbnails.download(attachment);
            String key = SoundboardThumbnails.configured().upload(id, image);
            boolean saved = BotDB.get().updateSoundboardThumbnail(id, guildId, userId, key);

            hook.editOriginal(saved ? "Thumbnail updated correctly." : "Error updating thumbnail.").queue();
        } catch (IllegalArgumentException exception) {
            hook.editOriginal("Invalid thumbnail.").queue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            hook.editOriginal("Error updating thumbnail.").queue();
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Could not update thumbnail for soundboard {}", id, exception);
            hook.editOriginal("Error updating thumbnail.").queue();
        }
    }
}
