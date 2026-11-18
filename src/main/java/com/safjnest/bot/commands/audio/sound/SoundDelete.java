package com.safjnest.bot.commands.audio.sound;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.sound.model.Sound;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * The command lets you delete a sound from the server.
 * <p>You have to be a server admin to use the command.</p>
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class SoundDelete extends SlashCommand{
    
    public SoundDelete(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "user_sound", "Sound to delete", true)
        );

        commandData.setThings(this);
    }
    
    @Override
    protected void execute(SlashCommandEvent event) {
        String lookup = event.getOption("user_sound").getAsString();
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        Set<String> administrators = event.getMember().hasPermission(Permission.ADMINISTRATOR) ? Set.of(guildId) : Set.of();
        List<Sound> matches = SoundCache.findSoundMatches(lookup, guildId, userId);

        if (matches.isEmpty()) {
            event.reply("Couldn't find a sound with that name/id.").queue();
            return;
        }
        if (matches.size() > 1) {
            StringBuilder message = new StringBuilder("Two or more sounds with that name have been found, please use IDs.\n");
            for (Sound sound : matches.subList(0, Math.min(matches.size(), 10))) {
                Guild guild = event.getJDA().getGuildById(sound.getGuildId());
                String guildName = guild == null ? sound.getGuildId() : guild.getName();
                boolean allowed = canDelete(sound, userId, administrators);
                if (message.length() + sound.getName().length() + guildName.length() + 140 > 2000)
                    break;

                message.append("**Sound:** ").append(sound.getName()).append(" (ID: ").append(sound.getId())
                    .append(") | **Guild:** ").append(guildName).append(" | **Author:** <@").append(sound.getUserId())
                    .append("> | **Can you delete this:** ").append(allowed ? "yes" : "no").append('\n');
            }
            event.deferReply(true).addContent(message.toString()).queue();
            return;
        }

        Sound sound = matches.getFirst();
        if (!canDelete(sound, userId, administrators)) {
            event.deferReply(true).addContent("You don't have permission to delete this sound.").queue();
            return;
        }

        try {
            boolean deleted = SoundCache.deleteSound(sound.getId(), userId, administrators);
            if (deleted) {
                event.deferReply(false).addContent(sound.getName() + " (ID: " + sound.getId() + ") has been deleted.").queue();
                return;
            }
        } catch (com.safjnest.exception.Exception exception) {
            if ("sound_forbidden".equals(exception.getCode())) {
                event.deferReply(true).addContent("You don't have permission to delete this sound.").queue();
                return;
            }
        }

        event.deferReply(true).addContent("Error deleting sound.").queue();
    }

    /* -------------------------------------------------------------------------- */

    private boolean canDelete(Sound sound, String userId, Set<String> administrators) {
        return userId.equals(sound.getUserId()) || administrators.contains(sound.getGuildId());
    }
}
