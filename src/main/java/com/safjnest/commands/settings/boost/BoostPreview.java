package com.safjnest.commands.settings.boost;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.cache.managers.GuildCache;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.AlertData;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

public class BoostPreview extends SlashCommand{

    public BoostPreview(String father){
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

        GuildData gs = GuildCache.getGuild(guildId);

        AlertData boost = gs.getAlert(AlertType.BOOST);

        if(boost == null) {
            event.deferReply(true).addContent("This guild doesn't have a boost message.").queue();
            return;
        }

        event.deferReply(false).addEmbeds(boost.getSampleEmbed(event.getGuild()).build()).queue();
    }
}