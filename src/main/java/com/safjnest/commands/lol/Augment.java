package com.safjnest.commands.lol;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.utils.BotCommand;
import com.safjnest.utils.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @since 1.3
 */
public class Augment extends SlashCommand {

    public Augment(){
        this.name = this.getClass().getSimpleName().toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.STRING, "augment", "Augment name or ID", true).setAutoComplete(true)
        );

        commandData.setThings(this);
    }

    @Override
	protected void execute(SlashCommandEvent event) {
        String aug = event.getOption("augment").getAsString();
        com.safjnest.lol.model.Augment augment = null;
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Bot.getColor());
        
        for(com.safjnest.lol.model.Augment a : LeagueHandler.getAugments()){
            if(a.id().equalsIgnoreCase(aug)){
                    augment = a;
                    break;
            }
        }
        
        RichCustomEmoji emoji = CustomEmojiHandler.getRichEmoji("a"+augment.id());
        eb.setTitle(augment.name().toUpperCase() + " (" + augment.id() + ")");
        eb.setDescription(augment.formattedTooltip());
        eb.setThumbnail(emoji.getImageUrl());
        event.replyEmbeds(eb.build()).queue();
    }
}