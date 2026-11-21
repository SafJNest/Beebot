package com.safjnest.bot.commands.audio.sound;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.bot.cache.managers.GuildCache;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;

public class SoundOverlap extends SlashCommand {
    public SoundOverlap(String father) {
        BotCommand commandData = CommandsLoader.getCommand(father).getChild("overlap");

        this.name = commandData.getName();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.options = commandData.getOptions();

        this.userPermissions = new Permission[]{Permission.MANAGE_SERVER};
        this.forceUserPermissions = true;

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Use this command in a server.").setEphemeral(true).queue();
            return;
        }

        boolean enabled = event.getOption("enabled").getAsBoolean();

        event.deferReply(true).queue(hook -> Thread.startVirtualThread(() -> {
            boolean saved = GuildCache.withGuild(guild.getId(), data -> data.setOverlapSound(enabled));

            if (!saved) {
                hook.editOriginal("Could not save the sound overlap setting.").queue();
                return;
            }

            hook.editOriginal(enabled ? "Sound overlap enabled." : "Sound overlap disabled.").queue();
        }));
    }
}
