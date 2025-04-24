package com.safjnest.commands.settings.levelup;

import java.util.ArrayList;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.ChannelData;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import com.safjnest.core.cache.managers.GuildCache;

public class LevelUpPreview extends SlashCommand{

    public LevelUpPreview(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String guildId = event.getGuild().getId();

        GuildData gs = GuildCache.getGuildOrPut(guildId);

        AlertData level = gs.getAlert(AlertType.LEVEL_UP);

        if(!gs.isExperienceEnabled()) {
            event.deferReply(true).addContent("This guild doesn't have the exp system enabled.").queue();
            return;
        }

        if(level == null){
            event.deferReply(true).addContent("No level up message found.").queue();
            return;
        }
        
        HashMap<String, ChannelData> channels = gs.getChannels();

        ArrayList<String> expChannels = new ArrayList<>();
        ArrayList<String> noExpChannels = new ArrayList<>();

        for (ChannelData channel : channels.values()) {
            if (!channel.isExpSystemEnabled()) {
                noExpChannels.add(String.valueOf(channel.getRoomId()));
            }
            if (channel.getExperienceModifier() != 1.0) {
                expChannels.add(String.valueOf(channel.getRoomId()));
            }
        }

        EmbedBuilder eb = level.getSampleEmbed(event.getGuild());
        if(!expChannels.isEmpty()) {
            String modifiedChannels= "";

            for(String channel_id : expChannels) {
                modifiedChannels += event.getGuild().getTextChannelById(channel_id).getName() + ": " + channels.get(channel_id).getExperienceModifier() + " exp\n";
            }
            eb.addField("Modified Exp Channels", "```" + modifiedChannels + "```", true);
        }

        if(!noExpChannels.isEmpty()){
            String disabledChannels = "";

            for(String channel_id : noExpChannels){
                disabledChannels += event.getGuild().getTextChannelById(channel_id).getName() + " \n";
            }

            eb.addField("Disabled Exp Channels", "```" + disabledChannels + "```", true);
        }

        event.deferReply(false).addEmbeds(eb.build()).queue();
    }
}