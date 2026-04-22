package com.safjnest.commands.lol;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.model.guild.GuildData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class Region extends SlashCommand {

    public Region(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            LeagueShardUtils.getAsOptions(true),
            new OptionData(OptionType.CHANNEL, "channel", "If empty the region will be use in the guild, select to override for the channel",false)
                .setChannelTypes(ChannelType.TEXT));

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        event.deferReply(false).queue();

        GuildData gs = GuildCache.getGuildOrPut(event.getGuild().getId());
        LeagueShard shard = LeagueShard.valueOf(event.getOption("region").getAsString());

        TextChannel channel = event.getOption("channel") != null ? event.getOption("channel").getAsChannel().asTextChannel() : null;

        if (channel != null) {
            if (!gs.getChannelData(channel.getId()).setLeagueShard(shard)) {
                event.getHook().sendMessage("Something went wrong.").queue();
                return;
            }
            event.getHook().sendMessage("Region set for " + channel.getAsMention() + " to " + LeagueShardUtils.getRegionFlag(shard) + shard.getRealmValue().toUpperCase()).queue();
            return;
        }
        else if(!gs.setLeagueShard(shard)) {
            event.getHook().sendMessage("Something went wrong.").queue();
            return;
        }

        event.getHook().sendMessage("Region set to " + LeagueShardUtils.getRegionFlag(shard) + shard.getRealmValue().toUpperCase()).queue();
	}

}
