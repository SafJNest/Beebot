package com.safjnest.SlashCommands.Settings.LevelUp;

import java.util.ArrayList;
import java.util.HashMap;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Bot.BotDataHandler;
import com.safjnest.Utilities.Bot.Guild.ChannelData;
import com.safjnest.Utilities.Bot.Guild.GuildData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertType;

public class LevelUpPreviewSlash extends SlashCommand{

    public LevelUpPreviewSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String guildId = event.getGuild().getId();
        String botId = event.getJDA().getSelfUser().getId();

        GuildData gs = BotDataHandler.getSettings(botId).getGuildSettings().getServer(guildId);

        AlertData level = gs.getAlert(AlertType.LEVEL_UP);

        if(!gs.isExpSystemEnabled()) {
            event.deferReply(true).addContent("This guild doesn't have the exp system enabled.").queue();
            return;
        }



        if(level == null){
            event.deferReply(true).addContent("No level up message found.").queue();
            return;
        }
        
        String levelupMessage = level.getMessage();

        levelupMessage = levelupMessage.replace("#user", event.getUser().getAsMention());
        levelupMessage = levelupMessage.replace("#level", "117");

        String message = level.getFormattedSample(event.getGuild());
        
        HashMap<Long, ChannelData> channels = gs.getChannels();

        ArrayList<String> expChannels = new ArrayList<>();
        ArrayList<String> noExpChannels = new ArrayList<>();

        for (ChannelData channel : channels.values()) {
            if (!channel.isExpSystemEnabled()) {
                noExpChannels.add(String.valueOf(channel.getRoomId()));
            }
            if (channel.getExpValue() != 1.0) {
                expChannels.add(String.valueOf(channel.getRoomId()));
            }
        }

        if(!expChannels.isEmpty()) {
            message += "Channel with exp Modifier:\n";
            for(String channel_id : expChannels) {
                message += event.getGuild().getTextChannelById(channel_id).getAsMention() + " exp: " + channels.get(Long.parseLong(channel_id)).getExpValue() + "\n";
            }
        }

        if(!noExpChannels.isEmpty()){
            message += "\nChannel with exp system disabled:\n";
            for(String channel_id : noExpChannels){
                message += event.getGuild().getTextChannelById(channel_id).getAsMention() + "\n";
            }
        }

        event.deferReply(false).addContent(message).queue();
    }
}