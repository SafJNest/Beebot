package com.safjnest.commands.League.slash;


import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.LOL.AugmentData;
import com.safjnest.util.LOL.RiotHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AugmentSlash extends SlashCommand {

    public AugmentSlash(){
        this.name = this.getClass().getSimpleName().replace("Slash", "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();
        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "augment", "Augment name", true).setAutoComplete(true));
        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        String aug = event.getOption("augment").getAsString();
        AugmentData augment = null;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        
        for(AugmentData a : RiotHandler.getAugments()){
            if(a.getId().equalsIgnoreCase(aug)){
                    augment = a;
                    break;
            }
        }
        
        RichCustomEmoji emoji = CustomEmojiHandler.getRichEmoji("a"+augment.getId());
        eb.setTitle(augment.getName().toUpperCase() + " (" + augment.getId() + ")");
        eb.setDescription(augment.getFormattedDesc());
        eb.setThumbnail(emoji.getImageUrl());
        event.replyEmbeds(eb.build()).queue();
    }
}
