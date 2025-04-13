package com.safjnest.commands.settings.reward;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import com.safjnest.core.cache.managers.GuildCache;

public class RewardPreview extends SlashCommand{

    public RewardPreview(String father){
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
        
        RewardData lowerReward = (RewardData) gs.getHigherReward(0);


        if(lowerReward == null) {
            event.deferReply(true).addContent("This Guild has zero reward.").queue();
            return;
        }


        Button left = Button.danger("reward-left", "<-");
        left = left.asDisabled();

        Button right = Button.primary("reward-right", "->");

        if (gs.getHigherReward(lowerReward.getLevel()) == null) 
            right = right.asDisabled().withStyle(ButtonStyle.DANGER);
        

        Button center = Button.primary("reward-center", "Level: " + lowerReward.getLevel());
        center = center.withStyle(ButtonStyle.SUCCESS);
        center = center.asDisabled();

        event.deferReply(false).addEmbeds(lowerReward.getSampleEmbed(event.getGuild()).build()).addActionRow(left, center, right).queue();
    }
    
}
