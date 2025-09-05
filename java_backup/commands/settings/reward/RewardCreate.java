package com.safjnest.commands.settings.reward;


import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;

import com.safjnest.core.Bot;

public class RewardCreate extends SlashCommand{

    public RewardCreate(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);

        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        
        commandData.setThings(this);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Container container = Container.of(Section.of(
            Button.primary("alert-createReward", "Create"),
            TextDisplay.of("In order to create a new reward, insert the level required to redeem it.")
        ));
        event.deferReply().addComponents(container.withAccentColor(Bot.getColor())).useComponentsV2().queue();
    }
}