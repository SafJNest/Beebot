package com.safjnest.commands.Settings.slash.Reward;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.guild.GuildData;
import com.safjnest.model.guild.alert.RewardData;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

public class RewardPreviewSlash extends SlashCommand{

    public RewardPreviewSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();
        this.help = new CommandsLoader().getString(name, "help", father.toLowerCase());
        this.cooldown = new CommandsLoader().getCooldown(this.name, father.toLowerCase());
        this.category = new Category(new CommandsLoader().getString(father.toLowerCase(), "category"));
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        String guildId = event.getGuild().getId();

        GuildData gs = Bot.getGuildData(guildId);
        
        RewardData lowerReward = (RewardData) gs.getHigherReward(0);


        if(lowerReward == null) {
            event.deferReply(true).addContent("This Guild has zero reward.").queue();
            return;
        }


        Button left = Button.danger("reward-left", "<-");
        left = left.asDisabled();

        Button right = Button.primary("reward-right", "->");

        Button center = Button.primary("reward-center", "Level: " + lowerReward.getLevel());
        center = center.withStyle(ButtonStyle.SUCCESS);
        center = center.asDisabled();

        event.deferReply(false).addEmbeds(lowerReward.getSampleEmbed(event.getGuild()).build()).addActionRow(left, center, right).queue();
    }
    
}
