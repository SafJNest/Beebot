package com.safjnest.commands.settings.levelup;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class LevelUpModifier extends SlashCommand{

    public LevelUpModifier(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        this.options = Arrays.asList(
            new OptionData(OptionType.CHANNEL, "channel", "Channel to change the level up modifier of", true)
                .setChannelTypes(ChannelType.TEXT),
            new OptionData(OptionType.NUMBER, "modifier", "The experience modifier (e.g. 0.6, 1.5, 2).", true)
                .setRequiredRange(0.0, 5.0)
        );

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String channelId = event.getOption("channel").getAsChannel().getId();
        double modifier = event.getOption("modifier").getAsDouble();

        String guildId = event.getGuild().getId();

        if(!GuildCache.getGuild(guildId).getChannelData(channelId).setExperienceModifier(modifier)) {
            event.deferReply(true).addContent("Something went wrong.").queue();
            return;
        }

        event.deferReply(false).addContent("Exp gain set to " + modifier + " times the normal amount in " + event.getGuild().getTextChannelById(channelId).getAsMention()).queue();
    }
}