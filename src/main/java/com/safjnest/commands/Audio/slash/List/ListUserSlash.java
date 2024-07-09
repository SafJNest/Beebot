package com.safjnest.commands.Audio.slash.List;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 2.1
 */
public class ListUserSlash extends SlashCommand{

    public ListUserSlash(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.aliases = commandData.getAliases();
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();
        this.arguments = commandData.getArguments();

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User to get the list of", true)
        );

        commandData.setThings(this);
    }

	@Override
	protected void execute(SlashCommandEvent event) {
        User mentionedUser = event.getOption("user").getAsUser();

        Button left = Button.danger("listuser-left", " ").withEmoji(CustomEmojiHandler.getRichEmoji("leftarrow"));
        left = left.asDisabled();
        Button right = Button.primary("listuser-right", " ").withEmoji(CustomEmojiHandler.getRichEmoji("rightarrow"));
        Button center = Button.primary("listuser-center-" + mentionedUser.getId(), "Page: 1");
        center = center.withStyle(ButtonStyle.SUCCESS);
        center = center.asDisabled();

        Button order = Button.secondary("listuser-order", " ").withEmoji(CustomEmojiHandler.getRichEmoji("clock"));

        EmbedBuilder eb = new  EmbedBuilder();
        eb.setAuthor(mentionedUser.getName(), "https://github.com/SafJNest", mentionedUser.getAvatarUrl());
        eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
        eb.setTitle("List of " + mentionedUser.getName());
        eb.setColor(Bot.getColor());

        QueryResult sounds = (mentionedUser.getId().equals(event.getMember().getId())) 
                           ? DatabaseHandler.getlistUserSounds(mentionedUser.getId()) 
                           : DatabaseHandler.getlistUserSounds(mentionedUser.getId(), event.getGuild().getId());

        eb.setDescription("Total Sound: " + sounds.size());

        for(int i = 0; i < sounds.size() && i < 24; i++){
            ResultRow sound = sounds.get(i);
            String locket = sound.getAsBoolean("public") ? "" : ":lock:";
            eb.addField("**" + sound.get("name") + "**" + locket, "ID: " + sound.get("id"), true);
        }
        
        if(sounds.size() <= 24){
            right = right.withStyle(ButtonStyle.DANGER);
            right = right.asDisabled();
        }

        event.deferReply(false).addEmbeds(eb.build()).addActionRow(left, center, right, order).queue();
    }    
}