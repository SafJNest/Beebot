package com.safjnest.SlashCommands.Settings.Boost;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.Utilities.CommandsLoader;
import com.safjnest.Utilities.Bot.BotDataHandler;
import com.safjnest.Utilities.Bot.Guild.GuildData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertData;
import com.safjnest.Utilities.Bot.Guild.Alert.AlertType;

public class BoostPreviewSlash extends SlashCommand{

    public BoostPreviewSlash(String father){
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

        AlertData boost = gs.getAlert(AlertType.BOOST);

        if(boost == null || !boost.isValid()) {
            event.deferReply(true).addContent("This guild doesn't have a boost message.").queue();
            return;
        }

        event.deferReply(false).addContent(boost.getFormattedSample(event.getGuild())).queue();
    }
}