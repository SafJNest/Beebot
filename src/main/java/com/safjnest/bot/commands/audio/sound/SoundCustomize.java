package com.safjnest.bot.commands.audio.sound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.audio.SoundEmbed;
import com.safjnest.bot.Bot;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.sound.model.Sound;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * The command lets you modify one of your sounds.
 * 
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * 
 * @since 1.3
 */
public class SoundCustomize extends SlashCommand {

    public SoundCustomize(String father) {
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "user_sound", "Sound to modify (name or id)", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String lookup = event.getOption("user_sound").getAsString();
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        List<Sound> owned = new ArrayList<>();
        for (Sound match : SoundCache.findSoundMatches(lookup, guildId, userId)) {
            if (userId.equals(match.getUserId()))
                owned.add(match);
        }

        Sound sound = SoundCache.chooseSound(owned, guildId);
        if (sound == null) {
            event.reply("Couldn't find a sound with that name/id (you can only change one of your sounds).").queue();
            return;
        }

        EmbedBuilder eb = getEmbed(event.getUser(), sound);

        event.replyEmbeds(eb.build()).setComponents(SoundEmbed.getSoundButton(sound.getId())).queue();
        
    }

    public static void saveEdit(IMessageEditCallback event, String id, Predicate<Sound> change) {
        event.deferEdit().queue(hook -> Thread.startVirtualThread(() -> {
            try {
                Sound sound = SoundCache.withSound(id, current -> {
                    Sound draft = current.deferEdit();
                    if (change.test(draft) && draft.update(event.getUser().getId()))
                        return current;

                    return null;
                });

                if (sound != null) {
                    hook.editOriginalEmbeds(getEmbed(event.getUser(), sound).build())
                        .setComponents(SoundEmbed.getSoundButton(id)).queue();
                    return;
                }
            } catch (com.safjnest.exception.Exception | NoSuchElementException exception) {
                hook.sendMessage(exception.getMessage()).setEphemeral(true).queue();
                return;
            }

            hook.sendMessage("Could not save the sound.").setEphemeral(true).queue();
        }));
    }

    public static EmbedBuilder getEmbed(User user, Sound sound) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor(user.getName(), "https://github.com/SafJNest", user.getAvatarUrl());
        eb.setTitle("Customize sound");
        eb.setDescription("```" + sound.getName() + " (ID: " + sound.getId() + ") "  + "```");
        eb.setColor(Bot.getColor());
        eb.setThumbnail(Bot.getJDA().getSelfUser().getAvatarUrl());

        eb.addField("Creation time", 
            "<t:" + sound.getTimestampSecond() + ":f>"  + " | <t:" + sound.getTimestampSecond() + ":R>",
        false);

        return eb;
    }
}
