package com.safjnest.commands.audio.list;

import java.util.Arrays;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.safjnest.core.Bot;
import com.safjnest.model.customemoji.CustomEmojiHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.util.BotCommand;
import com.safjnest.util.CommandsLoader;
import com.safjnest.util.PermissionHandler;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * @author <a href="https://github.com/NeutronSun">NeutronSun</a>
 * @author <a href="https://github.com/Leon412">Leon412</a>
 * 
 * @since 2.1
 */
public class ListUser extends SlashCommand{

    public ListUser(String father){
        this.name = this.getClass().getSimpleName().replace("Slash", "").replace(father, "").toLowerCase();

        BotCommand commandData = CommandsLoader.getCommand(father).getChild(this.name);
        
        this.help = commandData.getHelp();
        this.cooldown = commandData.getCooldown();
        this.category = commandData.getCategory();

        this.options = Arrays.asList(
            new OptionData(OptionType.USER, "user", "User to get the list of", true)
        );

        commandData.setThings(this);
    }

    public ListUser(){
        this.name = this.getClass().getSimpleName().toLowerCase().replace("slash", "");

        BotCommand commandData = CommandsLoader.getCommand(this.name);
        
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
                           ? BotDB.getlistUserSounds(mentionedUser.getId()) 
                           : BotDB.getlistUserSounds(mentionedUser.getId(), event.getGuild().getId());

        eb.setDescription("Total Sound: " + sounds.size());

        for(int i = 0; i < sounds.size() && i < 24; i++){
            QueryRecord sound = sounds.get(i);
            String locket = sound.getAsBoolean("public") ? "" : ":lock:";
            eb.addField("**" + sound.get("name") + "**" + locket, "ID: " + sound.get("id"), true);
        }
        
        if(sounds.size() <= 24){
            right = right.withStyle(ButtonStyle.DANGER);
            right = right.asDisabled();
        }

        event.deferReply(false).addEmbeds(eb.build()).addComponents(ActionRow.of(left, center, right, order)).queue();
    }    

    @Override
	protected void execute(CommandEvent event) {
        User mentionedUser;
        if(event.getArgs().equals(""))
            mentionedUser = event.getAuthor();
        else
            mentionedUser = PermissionHandler.getMentionedUser(event, event.getArgs());

        if(mentionedUser == null) {
            event.reply("Couldn't find the specified user.");
            return;
        }

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
                           ? BotDB.getlistUserSounds(mentionedUser.getId()) 
                           : BotDB.getlistUserSounds(mentionedUser.getId(), event.getGuild().getId());

        eb.setDescription("Total Sound: " + sounds.size());

        for(int i = 0; i < sounds.size() && i < 24; i++){
            QueryRecord sound = sounds.get(i);
            String locket = sound.getAsBoolean("public") ? "" : ":lock:";
            eb.addField("**" + sound.get("name") + "**" + locket, "ID: " + sound.get("id"), true);
        }
        
        if(sounds.size() <= 24){
            right = right.withStyle(ButtonStyle.DANGER);
            right = right.asDisabled();
        }

        event.getChannel().sendMessageEmbeds(eb.build()).setComponents(ActionRow.of(left, center, right, order)).queue();
    }
}